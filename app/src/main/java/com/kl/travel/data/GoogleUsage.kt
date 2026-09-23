package com.kl.travel.data

import java.time.YearMonth

/** The three kinds of Google call the app makes with your Maps key. */
enum class GoogleApi(val id: String, val label: String, val defaultCap: Int, val freeNote: String) {
    ROUTES("routes", "Routes (travel time, leave-by)", 3000, "Google's free allowance is 5,000 a month with live traffic, 10,000 without"),
    PLACES("places", "Places (lounges, lodging photos)", 1000, "free allowance is 5,000 a month"),
    MAPS("maps", "Map loads (route and lounge maps)", 3000, "free allowance is 10,000 a month"),
}

/**
 * Counts the app's own Google calls per month and stops them at a limit you set, so a bug or a lot of tapping
 * can't run up a bill. It only sees this app's calls; Google's console is the final word (see Settings > Keep Google free).
 */
object GoogleUsage {
    class LimitReached(val api: GoogleApi, val cap: Int) :
        Exception("Monthly ${api.label.substringBefore(" (")} limit reached ($cap). Raise it in Settings > Google usage if you want more.")

    private fun monthKey(api: GoogleApi) = "gu_${api.id}_month"
    private fun countKey(api: GoogleApi) = "gu_${api.id}_n"
    private fun capKey(api: GoogleApi) = "gu_${api.id}_cap"

    fun used(p: Prefs, api: GoogleApi, month: YearMonth = YearMonth.now()): Int =
        if (p.getStr(monthKey(api)) == month.toString()) p.getInt(countKey(api), 0) else 0

    fun cap(p: Prefs, api: GoogleApi): Int = p.getInt(capKey(api), api.defaultCap).coerceAtLeast(0)
    fun setCap(p: Prefs, api: GoogleApi, v: Int) = p.putInt(capKey(api), v.coerceAtLeast(0))

    /** Call right before a real Google request. Counts it and returns true, or returns false (and counts nothing) if the limit is used up. */
    fun tryUse(p: Prefs, api: GoogleApi, month: YearMonth = YearMonth.now()): Boolean {
        val n = used(p, api, month)
        if (n >= cap(p, api)) return false
        p.putStr(monthKey(api), month.toString()); p.putInt(countKey(api), n + 1)
        return true
    }

    /** Same, but throws [LimitReached] (inside a runCatching this becomes the error message shown to you). */
    fun require(p: Prefs, api: GoogleApi) { if (!tryUse(p, api)) throw LimitReached(api, cap(p, api)) }
}

/** How often the background alert check may re-ask Google for an event's travel time. Far-off events barely change, so they wait longer. */
object RouteThrottle {
    fun intervalMin(minsToStart: Long): Int = when {
        minsToStart > 180 -> 60
        minsToStart > 90 -> 30
        else -> 15
    }
    fun due(minsToStart: Long, lastCheckMs: Long, nowMs: Long): Boolean =
        lastCheckMs <= 0 || nowMs - lastCheckMs >= intervalMin(minsToStart) * 60_000L - 60_000L   // 1 min slack: the worker fires a little early or late
}
