package com.kl.travel.data

/**
 * Lounge memberships the traveller has, and a best-guess match against a lounge's name.
 * Access rules change often (guest limits, same-day-flight rules, capacity), so a match is only ever
 * shown as "may get in", never as a guarantee.
 */
enum class LoungeProgram(val label: String, private val keywords: List<String>) {
    PRIORITY_PASS("Priority Pass", listOf("plaza premium", "escape lounge", "club aspire", "airport dimensions", "no1 lounge", "minute suites", "priority pass")),
    LOUNGEKEY("LoungeKey", listOf("plaza premium", "escape lounge", "club aspire", "airport dimensions", "loungekey")),
    DRAGONPASS("DragonPass", listOf("plaza premium", "club aspire", "dragonpass")),
    PLAZA_PREMIUM("Plaza Premium membership", listOf("plaza premium")),
    AMEX("Amex Platinum / Centurion", listOf("centurion", "sky club", "escape lounge")),
    CAPITAL_ONE("Capital One Venture X", listOf("capital one lounge", "plaza premium", "priority pass")),
    CHASE("Chase Sapphire Reserve", listOf("sapphire lounge", "priority pass", "plaza premium")),
    DINERS("Diners Club", listOf("diners club", "priority pass")),
    AIRLINE_STATUS("Airline status (Star Alliance Gold, oneworld Emerald...)", emptyList()),
    /** Not a saved setting: switched on per flight, automatically when the flight's Cabin is business or first. */
    BUSINESS_TICKET("Business / first class ticket", emptyList());

    fun mayAccept(loungeName: String): Boolean = keywords.any { loungeName.contains(it, ignoreCase = true) }

    companion object {
        private val AIRLINE_WORDS = listOf("eva", "infinity", "the star", "philippine airlines", "mabuhay", "cebu pacific", "american", "admirals",
            "delta", "united", "star alliance", "oneworld", "skyteam", "korean air", "china airlines", "singapore", "cathay", "emirates", "qatar", "ana ", "jal ", "sakura")

        fun isAirlineLounge(name: String) = AIRLINE_WORDS.any { name.contains(it, ignoreCase = true) }

        /** Programs from [mine] that commonly get into this lounge (empty = unknown or not covered). */
        fun matches(loungeName: String, mine: Set<LoungeProgram>): List<LoungeProgram> =
            mine.filter { it != AIRLINE_STATUS && it != BUSINESS_TICKET && it.mayAccept(loungeName) } +
                listOf(AIRLINE_STATUS, BUSINESS_TICKET).filter { it in mine && isAirlineLounge(loungeName) }

        /** True for Cabin text like "Business", "First", "Royal Laurel"; not for "Premium Economy" or "Economy". */
        fun isBusinessCabin(cabin: String): Boolean {
            val c = cabin.lowercase()
            if (c.isBlank() || "premium" in c || "economy" in c) return false
            return listOf("business", "first", "royal laurel", "upper class", "club world", "suite").any { it in c }
        }

        /** Settings are saved by name; the old single "AIRLINE" chip becomes Airline status. */
        fun decode(csv: String): Set<LoungeProgram> = csv.split(",").mapNotNull { n ->
            val name = n.trim().let { if (it == "AIRLINE") "AIRLINE_STATUS" else it }
            entries.firstOrNull { it.name == name && it != BUSINESS_TICKET }
        }.toSet()
        fun encode(s: Set<LoungeProgram>) = s.filter { it != BUSINESS_TICKET }.joinToString(",") { it.name }
    }
}
