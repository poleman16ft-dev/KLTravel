package com.kl.travel.net

/** Where to see a booking's seat map: each airline's own manage-booking page. */
object Airlines {
    data class Airline(val name: String, val url: String)

    private val KNOWN = mapOf(
        "BR" to Airline("EVA Air", "https://booking.evaair.com/flyeva/eva/b2c/manage-your-trip/seat-login.aspx?lang=en-us"),
        "PR" to Airline("Philippine Airlines", "https://www.philippineairlines.com/us/en/manage-booking.html"),
        "DG" to Airline("Cebgo (Cebu Pacific)", "https://www.cebupacificair.com/manage-booking"),
        "5J" to Airline("Cebu Pacific", "https://www.cebupacificair.com/manage-booking"),
    )

    /** Airline name for image searches ("EVA Air", "Cebgo"), or the two-letter code when the airline isn't in the list. */
    private val PHOTO_NAMES = mapOf(
        "BR" to "EVA Air", "PR" to "Philippine Airlines", "DG" to "Cebgo", "5J" to "Cebu Pacific", "AA" to "American Airlines",
        "DL" to "Delta Air Lines", "UA" to "United Airlines", "WN" to "Southwest Airlines", "AS" to "Alaska Airlines", "B6" to "JetBlue",
        "NK" to "Spirit Airlines", "F9" to "Frontier Airlines", "HA" to "Hawaiian Airlines", "AC" to "Air Canada", "NH" to "All Nippon Airways",
        "JL" to "Japan Airlines", "CI" to "China Airlines", "CX" to "Cathay Pacific", "SQ" to "Singapore Airlines", "KE" to "Korean Air",
        "OZ" to "Asiana Airlines", "TG" to "Thai Airways", "MH" to "Malaysia Airlines", "GA" to "Garuda Indonesia", "QF" to "Qantas",
        "EK" to "Emirates", "QR" to "Qatar Airways", "EY" to "Etihad Airways", "TK" to "Turkish Airlines", "LH" to "Lufthansa",
        "BA" to "British Airways", "AF" to "Air France", "KL" to "KLM", "AM" to "Aeromexico", "LA" to "LATAM", "AK" to "AirAsia",
        "Z2" to "AirAsia Philippines", "VJ" to "VietJet Air", "VN" to "Vietnam Airlines", "MU" to "China Eastern", "CA" to "Air China",
        "CZ" to "China Southern", "IT" to "Tigerair Taiwan", "JQ" to "Jetstar", "3K" to "Jetstar Asia", "UO" to "HK Express", "CM" to "Copa Airlines",
        "AV" to "Avianca", "IB" to "Iberia", "AY" to "Finnair", "SK" to "SAS", "LX" to "Swiss", "OS" to "Austrian Airlines", "QP" to "Akasa Air",
    )

    fun photoName(flight: String): String? = code(flight)?.let { PHOTO_NAMES[it] ?: it }

    /** "BR49" -> "BR", "5J521" -> "5J". */
    fun code(flight: String): String? = Regex("^([A-Z0-9]{2})\\d{1,4}[A-Z]?$").find(flight.trim().uppercase())?.groupValues?.get(1)

    fun forFlight(flight: String): Airline {
        val c = code(flight)
        return KNOWN[c] ?: Airline(c ?: "the airline", "https://www.google.com/search?q=" + java.net.URLEncoder.encode("${c ?: flight} airline manage booking seat map", "UTF-8"))
    }

    /** seatmaps.com airline pages and the plane pages that exist on them (checked live). Order matters: longer names first. */
    private class SeatMapsAirline(val slug: String, val planes: List<Pair<String, String>>)

    private val SEATMAPS = mapOf(
        "BR" to SeatMapsAirline("br-eva-air", listOf("777-300" to "boeing-777-300er", "787-10" to "boeing-787-10", "787-9" to "boeing-787-9",
            "a321" to "airbus-a321", "a330-300" to "airbus-a330-300", "a330" to "airbus-a330-300")),
        "PR" to SeatMapsAirline("pr-pal", listOf("777-300" to "boeing-777-300er", "a321neo" to "airbus-a321neo", "a321" to "airbus-a321", "a320" to "airbus-a320",
            "a330" to "airbus-a330-300", "a350-1000" to "airbus-a350-1000", "a350" to "airbus-a350-900", "dash8" to "de-havilland-dash-8-q400", "q400" to "de-havilland-dash-8-q400")),
        "5J" to SeatMapsAirline("5j-cebu-pacific-air", listOf("a320neo" to "airbus-a320neo", "a320" to "airbus-a320", "a321neo" to "airbus-a321neo", "a321" to "airbus-a321",
            "a330-900" to "airbus-a330-900neo", "a330" to "airbus-a330-300", "atr72" to "atr-72-600")),
        "DG" to SeatMapsAirline("dg-cebgo", listOf("atr72" to "atr-72-600")),
    )

    /**
     * Direct seatmaps.com page (no search). Known plane: its own seat map. Unknown plane: that airline's seatmaps.com page,
     * which lists its planes. Unknown airline: the seatmaps.com home page. Addresses were checked on seatmaps.com.
     */
    fun seatMapsUrl(flight: String, aircraft: String): String {
        val home = "https://seatmaps.com"
        val a = SEATMAPS[code(flight)] ?: return "$home/"
        val plane = aircraft.lowercase().replace(Regex("[\\s_]"), "")
        val page = a.planes.firstOrNull { plane.contains(it.first) }?.second
        return "$home/airlines/${a.slug}/" + (page?.let { "$it/" } ?: "")
    }

    /** Name fragments of lounges run by the flight's own airline, so they can be listed first for a business ticket. */
    fun ownLoungeWords(flight: String): List<String> = when (code(flight)) {
        "BR" -> listOf("eva", "infinity", "the star")
        "PR" -> listOf("philippine", "mabuhay")
        "DG", "5J" -> listOf("cebu pacific")
        else -> emptyList()
    }

    /** First booking reference in text like "DQZUUS (Luis) / C2YF3G (Sabrina)". */
    fun firstReference(confirmation: String): String? = Regex("\\b[A-Z0-9]{6}\\b").find(confirmation.uppercase())?.value
}
