package com.kl.travel.work

import android.content.Context
import androidx.work.*
import com.kl.travel.data.*
import com.kl.travel.net.*
import com.kl.travel.widget.NextUpWidget
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit
import java.util.Locale
import java.util.concurrent.TimeUnit
import kotlin.math.abs

object AlertScheduler {
    private val net = Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build()

    fun schedule(ctx: Context) {
        val req = PeriodicWorkRequestBuilder<AlertWorker>(15, TimeUnit.MINUTES).setConstraints(net).build()
        WorkManager.getInstance(ctx).enqueueUniquePeriodicWork("kl_alerts", ExistingPeriodicWorkPolicy.KEEP, req)
    }

    fun runNow(ctx: Context) {
        WorkManager.getInstance(ctx).enqueue(OneTimeWorkRequestBuilder<AlertWorker>().setConstraints(net).build())
    }
}

/** Runs about every 15 minutes: refreshes the sheet, checks flights near departure, and sends leave-by / traffic alerts. */
class AlertWorker(private val ctx: Context, params: WorkerParameters) : CoroutineWorker(ctx, params) {
    private val timeFmt = DateTimeFormatter.ofPattern("h:mm a", Locale.US)

    override suspend fun doWork(): Result {
        val prefs = Prefs(ctx)
        if (prefs.sheetUrl.isNotBlank()) SheetRepository(ctx).sync()   // pick up sheet edits; failures are fine (offline)
        if (!prefs.alertsEnabled) { NextUpWidget.refreshAll(ctx); return Result.success() }

        val dao = TravelDb.get(ctx).dao()
        val items = TripBuilder.build(dao.eventsOnce(prefs.activeTripId), dao.hotelsOnce(prefs.activeTripId))
        val now = LocalDateTime.now()
        checkFlights(prefs, items, now)
        checkLeaveBy(prefs, items, now)
        NextUpWidget.refreshAll(ctx)      // keep the home-screen widget current
        return Result.success()
    }

    private suspend fun checkFlights(prefs: Prefs, items: List<TripItem>, now: LocalDateTime) {
        if (prefs.flightKey.isBlank()) return
        for (item in items) {
            val start = item.start ?: continue
            if (!item.isFlight || item.flight.isBlank()) continue
            val mins = ChronoUnit.MINUTES.between(now, start)
            if (mins < -240 || mins > 24 * 60) continue
            // Poll less often when the flight is far away: saves your free API quota.
            val everyMin = when { abs(mins) <= 180 -> 15; mins <= 720 -> 60; else -> 180 }
            val last = FlightUpdater.cachedAt(prefs, item.id)
            if (System.currentTimeMillis() - last < everyMin * 60_000L) continue
            FlightUpdater.refresh(ctx, prefs, item, notify = true)
        }
    }

    private suspend fun checkLeaveBy(prefs: Prefs, items: List<TripItem>, now: LocalDateTime) {
        for (item in items) {
            val start = item.start ?: continue
            if (!item.needsTravel) continue
            val minsToStart = ChronoUnit.MINUTES.between(now, start)
            if (minsToStart < -15 || minsToStart > 240) continue

            // Saves Google calls: far-off events are only re-checked every 30 to 60 minutes.
            val nowMs = System.currentTimeMillis()
            if (!com.kl.travel.data.RouteThrottle.due(minsToStart, prefs.getLong("rcheck_${item.id}"), nowMs)) continue

            val mode = TravelMode.from(prefs.savedMode(item.id) ?: TravelMode.hint(item.transport)?.name ?: prefs.defaultMode)
            val origin = OriginResolver.resolve(ctx, item, items, prefs.useMyLocation) ?: continue
            val route = RoutesClient.compute(ctx, origin.place, Place.Address(item.address), mode, start).getOrNull() ?: continue
            prefs.putLong("rcheck_${item.id}", nowMs)

            val leave = LeaveBy.compute(item, route.durationSec, prefs.leaveBufferMin, prefs.airportBufferMin) ?: continue
            val durMin = (route.durationSec / 60).toInt()
            prefs.putStr("leave_${item.id}", leave.toString())
            prefs.putInt("dur_${item.id}", durMin)

            val minsToLeave = ChronoUnit.MINUTES.between(now, leave)
            val stage = when { minsToLeave <= 5 -> 2; minsToLeave <= prefs.alertLeadMin -> 1; else -> 0 }
            val sent = prefs.getInt("stage_${item.id}", 0)
            val traffic = if (route.trafficDelayMin >= 5) ", +${route.trafficDelayMin} min traffic" else ""
            val body = "${item.title}: ${durMin} min ${mode.label.lowercase()}$traffic from ${origin.label}."

            if (stage > sent) {
                val title = if (stage == 2) "Leave now for ${item.title}" else "Leave by ${leave.format(timeFmt)}"
                Notifier.post(ctx, ("leave_" + item.id).hashCode(), Notifier.CH_LEAVE, title, body, item.id)
                prefs.putInt("stage_${item.id}", stage)
                prefs.putInt("durn_${item.id}", durMin)
            } else if (sent >= 1 && minsToLeave > 0) {
                val lastDur = prefs.getInt("durn_${item.id}", durMin)
                if (abs(durMin - lastDur) >= 10) {
                    val dir = if (durMin > lastDur) "longer" else "shorter"
                    Notifier.post(ctx, ("traffic_" + item.id).hashCode(), Notifier.CH_LEAVE,
                        "Trip now $dir: leave by ${leave.format(timeFmt)}", body, item.id)
                    prefs.putInt("durn_${item.id}", durMin)
                }
            }
        }
    }
}
