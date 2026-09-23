package com.kl.travel.data

import java.time.LocalDate
import java.time.LocalTime
import java.time.format.DateTimeFormatterBuilder
import java.util.Locale

/** Pure parsing of the Events / Lodging (formerly Hotels) tabs (no Android dependencies, unit-tested). */
object SheetParser {
    private class Table(rows: List<List<String>>) {
        val header: Map<String, Int>
        val data: List<List<String>>
        init {
            val h = rows.firstOrNull().orEmpty()
            header = h.mapIndexed { i, s -> s.lowercase(Locale.US).filter { c -> c.isLetterOrDigit() || c == '#' } to i }.toMap()
            data = rows.drop(1)
        }
        fun has(vararg names: String) = names.any { header.containsKey(it) }
        fun get(row: List<String>, vararg names: String): String {
            for (n in names) header[n]?.let { i -> if (i < row.size) return row[i].trim() }
            return ""
        }
    }

    fun parseEvents(rows: List<List<String>>, warnings: MutableList<String>): List<EventEntity> {
        val t = Table(rows)
        if (!t.has("date") || !t.has("title")) {
            error("Events tab not found or headers changed (need Date and Title columns).")
        }
        val out = mutableListOf<EventEntity>()
        val seen = HashSet<String>()
        t.data.forEachIndexed { idx, r ->
            val title = t.get(r, "title")
            val dateStr = t.get(r, "date")
            if (title.isBlank() && dateStr.isBlank()) return@forEachIndexed
            val date = parseDate(dateStr)
            if (date == null) { warnings += "Events row ${idx + 2} skipped: bad date \"$dateStr\""; return@forEachIndexed }
            val start = parseTime(t.get(r, "starttime", "time"))
            val end = parseTime(t.get(r, "endtime"))
            val flight = t.get(r, "flight#", "flight", "flightnumber").replace(" ", "").uppercase(Locale.US)
            var id = "e" + Integer.toHexString("$date|$start|$title".hashCode())
            var n = 1
            while (!seen.add(id)) id = id.substringBefore('_') + "_" + n++
            out += EventEntity(
                id = id, date = date.toString(), startTime = start?.toString(), endTime = end?.toString(),
                type = t.get(r, "type").ifBlank { if (flight.isNotBlank()) "Flight" else "Event" },
                title = title.ifBlank { "(untitled)" }, location = t.get(r, "location"),
                address = t.get(r, "address"), flight = flight,
                confirmation = t.get(r, "confirmation#", "confirmation"),
                cost = parseMoney(t.get(r, "cost")), transport = t.get(r, "transport"), notes = t.get(r, "notes"),
                seat = t.get(r, "seat", "seats", "seatnumber", "seatno"),
                aircraft = t.get(r, "aircraft", "plane", "planetype", "aircrafttype"),
                cabin = t.get(r, "cabin", "class", "cabinclass", "travelclass"),
            )
        }
        return out
    }

    fun parseHotels(rows: List<List<String>>, warnings: MutableList<String>): List<HotelEntity> {
        val t = Table(rows)
        if (!t.has("lodgingname", "lodging", "hotelname", "name") || !t.has("checkindate")) error("Lodging tab headers not found.")
        val out = mutableListOf<HotelEntity>()
        val seen = HashSet<String>()
        t.data.forEachIndexed { idx, r ->
            val name = t.get(r, "lodgingname", "lodging", "hotelname", "name")
            if (name.isBlank()) return@forEachIndexed
            val inD = parseDate(t.get(r, "checkindate"))
            val outD = parseDate(t.get(r, "checkoutdate"))
            if (inD == null || outD == null) { warnings += "Lodging row ${idx + 2} skipped: bad check-in/out date"; return@forEachIndexed }
            var id = "h" + Integer.toHexString("$name|$inD".hashCode())
            var n = 1
            while (!seen.add(id)) id = id.substringBefore('_') + "_" + n++
            out += HotelEntity(
                id = id, name = name, address = t.get(r, "address"),
                checkInDate = inD.toString(), checkInTime = parseTime(t.get(r, "checkintime"))?.toString(),
                checkOutDate = outD.toString(), checkOutTime = parseTime(t.get(r, "checkouttime"))?.toString(),
                confirmation = t.get(r, "confirmation#", "confirmation"), phone = t.get(r, "phone"),
                cost = parseMoney(t.get(r, "cost")), notes = t.get(r, "notes"),
            )
        }
        return out
    }

    private val dateFormats = listOf(
        "yyyy-MM-dd", "M/d/yyyy", "M/d/yy", "MM/dd/yyyy", "MMM d, yyyy", "MMMM d, yyyy",
        "d-MMM-yyyy", "d MMM yyyy", "EEE, MMM d, yyyy", "EEEE, MMMM d, yyyy",
    ).map { DateTimeFormatterBuilder().parseCaseInsensitive().appendPattern(it).toFormatter(Locale.US) }

    private val timeFormats = listOf("h:mm a", "h:mm:ss a", "H:mm", "H:mm:ss", "h a", "ha", "h:mma")
        .map { DateTimeFormatterBuilder().parseCaseInsensitive().appendPattern(it).toFormatter(Locale.US) }

    fun parseDate(s: String): LocalDate? {
        val v = s.trim()
        if (v.isEmpty()) return null
        for (f in dateFormats) try { return LocalDate.parse(v, f) } catch (_: Exception) {}
        return null
    }

    fun parseTime(s: String): LocalTime? {
        val v = s.trim().replace(Regex("(?i)([ap])\\.?m\\.?$"), "$1m")
            .replace(Regex("(?i)(\\d)([ap]m)$"), "$1 $2")
        if (v.isEmpty()) return null
        for (f in timeFormats) try { return LocalTime.parse(v, f) } catch (_: Exception) {}
        return null
    }

    private fun parseMoney(s: String): Double? =
        s.replace(Regex("[^0-9.\\-]"), "").toDoubleOrNull()
}
