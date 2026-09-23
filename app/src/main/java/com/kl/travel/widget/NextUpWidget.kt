package com.kl.travel.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.view.View
import android.widget.RemoteViews
import com.kl.travel.MainActivity
import com.kl.travel.R
import com.kl.travel.data.*
import com.kl.travel.ui.DAY_FMT
import com.kl.travel.ui.TIME_FMT
import com.kl.travel.ui.countdown
import com.kl.travel.work.FlightUpdater
import com.kl.travel.work.Notifier
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import java.time.LocalDateTime

/** Home-screen "Next up" widget: the next event, when to leave, and flight status. Reads only the saved trip, so it works offline. */
class NextUpWidget : AppWidgetProvider() {

    override fun onUpdate(context: Context, manager: AppWidgetManager, ids: IntArray) {
        val pending = goAsync()
        scope.launch { try { update(context, manager, ids) } finally { pending.finish() } }
    }

    companion object {
        private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

        fun refreshAll(ctx: Context) {
            val app = ctx.applicationContext
            val mgr = AppWidgetManager.getInstance(app)
            val ids = mgr.getAppWidgetIds(ComponentName(app, NextUpWidget::class.java))
            if (ids.isEmpty()) return
            scope.launch { update(app, mgr, ids) }
        }

        /** Pure text for the widget, kept separate so it can be unit tested. */
        data class Lines(val title: String, val whenLine: String, val place: String, val extra: String)

        fun lines(item: TripItem?, now: LocalDateTime, leave: LocalDateTime?, durMin: Int, flightLine: String?): Lines {
            if (item == null) return Lines("Nothing coming up", "", "", "")
            val whenLine = listOfNotNull(
                countdown(now, item.start),
                item.start?.format(TIME_FMT),
                item.date.takeIf { it != now.toLocalDate() }?.format(DAY_FMT),
            ).joinToString(" · ")
            val extra = when {
                leave != null && leave.isAfter(now.minusMinutes(30)) ->
                    "Leave by ${leave.format(TIME_FMT)}" + if (durMin > 0) " · $durMin min" else ""
                !flightLine.isNullOrBlank() -> flightLine
                else -> ""
            }
            return Lines(item.title, whenLine, item.location, extra)
        }

        private suspend fun update(ctx: Context, mgr: AppWidgetManager, ids: IntArray) {
            val dao = TravelDb.get(ctx).dao()
            val prefs = Prefs(ctx)
            val items = TripBuilder.build(dao.eventsOnce(prefs.activeTripId), dao.hotelsOnce(prefs.activeTripId))
            val now = LocalDateTime.now()
            val next = items.firstOrNull { it.sortKey >= now }
            val leave = next?.let { prefs.getStr("leave_${it.id}") }?.let { runCatching { LocalDateTime.parse(it) }.getOrNull() }
            val dur = next?.let { prefs.getInt("dur_${it.id}") } ?: -1
            val flight = next?.takeIf { it.isFlight }?.let { FlightUpdater.cached(prefs, it.id)?.lineSequence()?.firstOrNull() }
            val l = lines(next, now, leave, dur, flight)

            for (id in ids) {
                val v = RemoteViews(ctx.packageName, R.layout.widget_next)
                v.setTextViewText(R.id.w_title, l.title)
                v.setTextViewText(R.id.w_when, l.whenLine)
                v.setTextViewText(R.id.w_place, l.place)
                v.setTextViewText(R.id.w_extra, l.extra)
                v.setViewVisibility(R.id.w_when, if (l.whenLine.isBlank()) View.GONE else View.VISIBLE)
                v.setViewVisibility(R.id.w_place, if (l.place.isBlank()) View.GONE else View.VISIBLE)
                v.setViewVisibility(R.id.w_extra, if (l.extra.isBlank()) View.GONE else View.VISIBLE)
                val open = Intent(ctx, MainActivity::class.java).apply {
                    flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
                    next?.let { putExtra(Notifier.EXTRA_ITEM, it.id) }
                }
                v.setOnClickPendingIntent(R.id.w_root,
                    PendingIntent.getActivity(ctx, 7, open, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE))
                mgr.updateAppWidget(id, v)
            }
        }
    }
}
