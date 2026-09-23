package com.kl.travel.data

import java.time.Duration
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.format.DateTimeFormatter

enum class LayoverLevel { OK, SHORT, LONG, STAY, MISSED, UNKNOWN }

/**
 * A connection between two flights that meet at the same airport. Times are local to that airport.
 * [arrives] is null when the first flight has no arrival time anywhere (End Time or an "arrives 5:25 AM" note).
 */
data class Layover(
    val from: TripItem,
    val to: TripItem,
    val airport: String,
    val arrives: LocalDateTime?,
    val departs: LocalDateTime,
    /** True when live flight status changed the arrival or departure time. */
    val live: Boolean = false,
    /** A hotel check-in sits between the two flights. */
    val hotel: Boolean = false,
) {
    val minutes: Long? get() = arrives?.let { Duration.between(it, departs).toMinutes() }
    val level: LayoverLevel get() {
        val m = minutes ?: return LayoverLevel.UNKNOWN
        return when {
            m <= 0 -> LayoverLevel.MISSED
            m < Layovers.SHORT_MIN -> LayoverLevel.SHORT
            hotel || m >= Layovers.STAY_MIN -> LayoverLevel.STAY
            m > Layovers.LONG_MIN -> LayoverLevel.LONG
            else -> LayoverLevel.OK
        }
    }
    fun durationText() = minutes?.let { Layovers.durationText(it) } ?: "?"
}

/** Works out layovers from consecutive flights. Pure logic, unit-tested. */
object Layovers {
    const val SHORT_MIN = 60L
    const val LONG_MIN = 6L * 60
    /** A gap this long (or any gap with a hotel check-in in it) is an overnight stay, not a wait at the gate. */
    const val STAY_MIN = 12L * 60
    /** Two flights further apart than this are separate trips, not a connection. */
    const val MAX_GAP_MIN = 24L * 60

    private val ROUTE = Regex("\\b([A-Z]{3})\\s*(?:→|➔|➝|⇒|»|->|–|—|>|-|to)\\s*([A-Z]{3})\\b")
    private val LOC_CODE = Regex("\\(([A-Z]{3})\\)")
    private val ARR_LIVE = Regex("Arrives (\\d{1,2}:\\d{2})")
    private val DEP_LIVE = Regex("Now departs (\\d{1,2}:\\d{2})")

    /** "EVA BR49 - DFW > TPE" gives (DFW, TPE). Also accepts arrows, dashes and the word "to". */
    fun route(title: String): Pair<String, String>? =
        ROUTE.find(title)?.let { it.groupValues[1] to it.groupValues[2] }

    fun departureAirport(i: TripItem): String? = route(i.title)?.first ?: LOC_CODE.find(i.location)?.groupValues?.get(1)
    fun arrivalAirport(i: TripItem): String? = route(i.title)?.second

    fun durationText(min: Long): String = when {
        min <= 0 -> "0m"
        min < 60 -> "${min}m"
        min % 60 == 0L -> "${min / 60}h"
        else -> "${min / 60}h ${min % 60}m"
    }

    private fun clock(s: String): LocalTime = DateTimeFormatter.ofPattern("H:mm").let { LocalTime.parse(s, it) }

    /** The date (day before, same day or day after [near]) at which the clock reads [hhmm] that is closest to [near]. */
    internal fun nearest(near: LocalDateTime, hhmm: String): LocalDateTime {
        val t = runCatching { clock(hhmm) }.getOrNull() ?: return near
        return (-1L..1L).map { near.toLocalDate().plusDays(it).atTime(t) }
            .minByOrNull { kotlin.math.abs(Duration.between(near, it).toMinutes()) } ?: near
    }

    private val MONTHS = listOf("jan", "feb", "mar", "apr", "may", "jun", "jul", "aug", "sep", "oct", "nov", "dec")
    private val ARRIVE_NOTE = Regex(
        "arrive[sd]?\\b[^;]*?(\\d{1,2}:\\d{2})\\s*([AaPp][Mm])?(?:\\s+on\\s+([A-Za-z]{3})[a-z]*\\.?\\s+(\\d{1,2}))?",
        RegexOption.IGNORE_CASE,
    )

    /** Arrival written in Notes, like "arrives Taipei 5:25 AM on Feb 23" or "depart 1:35 PM, arrive 3:20 PM". Date is null when not given. */
    internal fun arrivalFromNotes(notes: String, flightDate: LocalDate): Pair<LocalTime, LocalDate?>? {
        val m = ARRIVE_NOTE.find(notes) ?: return null
        val raw = m.groupValues[1] + (m.groupValues[2].takeIf { it.isNotBlank() }?.let { " " + it.uppercase() } ?: "")
        val t = runCatching {
            if (m.groupValues[2].isBlank()) LocalTime.parse(raw, DateTimeFormatter.ofPattern("H:mm"))
            else LocalTime.parse(raw, DateTimeFormatter.ofPattern("h:mm a", java.util.Locale.US))
        }.getOrNull() ?: return null
        val mon = MONTHS.indexOf(m.groupValues[3].lowercase()) + 1
        val day = m.groupValues[4].toIntOrNull()
        val date = if (mon > 0 && day != null) runCatching {
            var d = LocalDate.of(flightDate.year, mon, day)
            if (d.isBefore(flightDate.minusDays(1))) d = d.plusYears(1)
            d
        }.getOrNull() else null
        return t to date
    }

    /**
     * Layovers between consecutive flights. [cached] returns the saved live-status text for a flight id
     * (first line status, second line details such as "Arrives 14:35") and is used to move times when a flight is late.
     * Needs the airport codes in the flight titles ("BR49 - DFW > TPE"). The arrival comes from End Time, or from Notes
     * ("arrives 5:25 AM on Feb 23"); without either the card asks for it. [items] is the whole trip, so hotel stays in between are noticed.
     */
    fun find(items: List<TripItem>, cached: (String) -> String? = { null }): List<Layover> {
        val flights = items.filter { it.isFlight && it.start != null }.sortedBy { it.sortKey }
        val out = mutableListOf<Layover>()
        for (k in 0 until flights.size - 1) {
            val a = flights[k]; val b = flights[k + 1]
            val hub = arrivalAirport(a) ?: continue
            if (hub != departureAirport(b)) continue
            val bDep = b.start ?: continue
            val aStart = a.start ?: continue
            val note = if (a.end == null) arrivalFromNotes(a.notes, a.date) else null
            val arrClock = a.end?.toLocalTime() ?: note?.first
            if (arrClock == null) {
                // Arrival unknown: only flag it when the next flight is close enough to be a connection.
                if (java.time.temporal.ChronoUnit.DAYS.between(a.date, b.date) in 0..2) out += Layover(a, b, hub, null, bDep)
                continue
            }
            val base: LocalDateTime = note?.second?.atTime(arrClock)?.takeIf { Duration.between(it, bDep).toMinutes() in 0..MAX_GAP_MIN }
                // The sheet has no arrival date. If the arrival clock is later than the departure clock it lands the same day;
                // if it is earlier it lands the next day or two (long haul), or the same day across the date line. Keep the arrival
                // that fits the flight (between 14 hours before and 34 hours after it leaves, local clocks) and sits just before the next departure.
                ?: (if (arrClock >= aStart.toLocalTime()) 0L..0L else 0L..2L).map { a.date.plusDays(it).atTime(arrClock) }
                    .filter { Duration.between(aStart, it).toMinutes() in -14 * 60..34 * 60 }
                    .filter { Duration.between(it, bDep).toMinutes() in 0..MAX_GAP_MIN }
                    .maxOrNull() ?: continue
            var arr = base; var dep = bDep; var live = false
            cached(a.id)?.let { c -> ARR_LIVE.find(c)?.let { m -> nearest(base, m.groupValues[1]).let { if (it != arr) { arr = it; live = true } } } }
            cached(b.id)?.let { c -> DEP_LIVE.find(c)?.let { m -> nearest(bDep, m.groupValues[1]).let { if (it != dep) { dep = it; live = true } } } }
            val hotel = items.any { it.kind == ItemKind.HOTEL_IN && !it.date.isBefore(base.toLocalDate()) && !it.date.isAfter(bDep.toLocalDate()) }
            out += Layover(a, b, hub, arr, dep, live, hotel)
        }
        return out
    }
}
