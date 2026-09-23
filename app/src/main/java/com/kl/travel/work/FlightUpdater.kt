package com.kl.travel.work

import android.content.Context
import com.kl.travel.data.Prefs
import com.kl.travel.data.TripItem
import com.kl.travel.net.FlightClient
import com.kl.travel.net.FlightStatus

/** Fetches flight status, caches the last result for offline display, and notifies on changes. */
object FlightUpdater {
    fun cached(prefs: Prefs, id: String): String? = prefs.getStr("fcache_$id")
    /** Plane type from the last live lookup (the sheet's Aircraft column wins over this). */
    fun plane(prefs: Prefs, id: String): String? = prefs.getStr("fplane_$id")?.takeIf { it.isNotBlank() }
    fun cachedAt(prefs: Prefs, id: String): Long = prefs.getLong("flast_$id")

    suspend fun refresh(ctx: Context, prefs: Prefs, item: TripItem, notify: Boolean): Result<FlightStatus?> {
        val r = FlightClient.fetch(prefs, item.flight, item.date)
        r.onSuccess { st ->
            prefs.putLong("flast_${item.id}", System.currentTimeMillis())
            if (st == null) {
                prefs.putStr("fcache_${item.id}", "No live data yet for ${item.flight}\n")
            } else {
                prefs.putStr("fcache_${item.id}", st.headline() + "\n" + st.detailLine())
                st.aircraft?.let { prefs.putStr("fplane_${item.id}", it) }
                val sig = st.signature()
                val old = prefs.getStr("fsig_${item.id}")
                if (notify && old != sig && (old != null || st.isCancelled || st.isDelayed)) {
                    val title = when {
                        st.isCancelled -> "${item.flight} cancelled"
                        st.isDelayed -> "${item.flight} delayed"
                        else -> "${item.flight} update"
                    }
                    Notifier.post(ctx, ("flight_" + item.id).hashCode(), Notifier.CH_FLIGHT, title,
                        listOf(st.headline(), st.detailLine()).filter { it.isNotBlank() }.joinToString("\n"), item.id)
                }
                prefs.putStr("fsig_${item.id}", sig)
            }
        }
        return r
    }
}
