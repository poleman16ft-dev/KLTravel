package com.kl.travel.data

/** One traveler's seat from the Seat column. [who] is blank when the sheet gives only the seat. */
data class SeatEntry(val who: String, val seat: String)

object Seats {
    private val SEAT = Regex("\\b(\\d{1,3}[A-Za-z])\\b")

    /**
     * "Luis 3C / Sabrina 3D", "Luis: 3C; Sabrina: 3D", "3C, 3D" or "6K" -> one entry per seat, in the order written.
     * Parts are split on / ; , & and the word "and". A part with no seat-like token (e.g. "TBD") is kept as-is.
     */
    fun parse(text: String): List<SeatEntry> =
        text.split(Regex("[/;,&\\n]|\\band\\b", RegexOption.IGNORE_CASE))
            .map { it.trim() }.filter { it.isNotEmpty() }
            .map { part ->
                val m = SEAT.find(part)
                if (m == null) SeatEntry("", part)
                else SeatEntry(part.removeRange(m.range).trim().trim(':', '-', '(', ')', '–', '—', ' ').trim(), m.groupValues[1].uppercase())
            }

    /** Short one-line form for lists: "Luis 3C, Sabrina 3D". */
    fun summary(text: String): String = parse(text).joinToString(", ") { (if (it.who.isBlank()) "" else it.who + " ") + it.seat }
}
