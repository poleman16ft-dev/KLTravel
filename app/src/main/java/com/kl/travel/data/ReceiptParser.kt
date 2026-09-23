package com.kl.travel.data

import java.time.LocalDate

data class ParsedReceipt(val merchant: String, val date: LocalDate?, val total: Double?, val currency: String, val category: String)

/** Pulls merchant, date, total, currency and a category guess out of receipt text read by OCR. Everything is editable in the app afterwards. */
object ReceiptParser {
    fun parse(text: String): ParsedReceipt {
        val lines = text.lines().map { it.trim() }.filter { it.isNotEmpty() }
        val currency = detectCurrency(text)
        return ParsedReceipt(
            merchant = merchant(lines),
            date = date(text, currency),
            total = total(lines),
            currency = currency,
            category = category(text),
        )
    }

    // ---------------------------------------------------------------- amounts

    /** "1,250.00", "1.250,50", "3,480", "12.5" -> number. A separator followed by exactly 3 digits is a thousands separator. */
    fun toAmount(token: String): Double? {
        val s = token.trim().trimEnd('.', ',')
        if (s.isEmpty()) return null
        val dec = maxOf(s.lastIndexOf('.'), s.lastIndexOf(','))
        if (dec < 0) return s.toDoubleOrNull()
        val after = s.length - dec - 1
        val cleaned = if (after in 1..2) s.substring(0, dec).replace(Regex("[.,]"), "") + "." + s.substring(dec + 1)
        else s.replace(Regex("[.,]"), "")
        return cleaned.toDoubleOrNull()
    }

    private val NUMBER = Regex("""\d[\d.,]*""")

    /** Amounts on a line, ignoring things glued to letters (like B12). */
    private fun amountsIn(line: String): List<Pair<String, Double>> =
        NUMBER.findAll(line).mapNotNull { m ->
            val before = line.getOrNull(m.range.first - 1)
            if (before != null && before.isLetter()) return@mapNotNull null
            toAmount(m.value)?.let { m.value.trimEnd('.', ',') to it }
        }.toList()

    private fun hasTwoDecimals(token: String) = Regex("""[.,]\d{2}$""").containsMatchIn(token)

    private val STRONG = listOf("grand total", "total due", "amount due", "balance due", "total amount", "amount payable", "net total", "total payable", "total to pay")
    private val WEAK = listOf("total", "amount", "balance", "合計", "合计", "總計", "总计")
    private val NOT_TOTAL = listOf("subtotal", "sub total", "sub-total", "change", "cash", "tender", "tip", "discount", "total items", "total qty", "item")

    private fun pick(list: List<Pair<String, Double>>): Double? {
        if (list.isEmpty()) return null
        val decimals = list.filter { hasTwoDecimals(it.first) }
        return (decimals.ifEmpty { list }).maxOf { it.second }
    }

    fun total(lines: List<String>): Double? {
        fun scan(words: List<String>): Double? {
            var found: Double? = null
            lines.forEachIndexed { i, l ->
                val lc = l.lowercase()
                if (words.none { lc.contains(it) } || NOT_TOTAL.any { lc.contains(it) }) return@forEachIndexed
                val here = pick(amountsIn(l)) ?: lines.getOrNull(i + 1)?.let { pick(amountsIn(it)) }
                if (here != null && here > 0) found = here
            }
            return found
        }
        scan(STRONG)?.let { return it }
        scan(WEAK)?.let { return it }
        return pick(lines.flatMap { amountsIn(it) }.filter { hasTwoDecimals(it.first) })
    }

    // ---------------------------------------------------------------- currency

    fun detectCurrency(text: String): String {
        val t = text.uppercase()
        fun has(vararg k: String) = k.any { t.contains(it) }
        return when {
            has("NT$", "TWD", "NTD") -> "TWD"
            has("PHP", "₱", "PESO") -> "PHP"
            has("JPY", "¥", "円", "YEN") -> "JPY"
            has("CNY", "RMB", "人民币") -> "CNY"
            has("KRW", "₩") -> "KRW"
            has("EUR", "€") -> "EUR"
            has("GBP", "£") -> "GBP"
            has("THB", "฿") -> "THB"
            has("SGD", "S$") -> "SGD"
            has("HKD", "HK$") -> "HKD"
            has("USD", "$") -> "USD"
            else -> ""
        }
    }

    // ---------------------------------------------------------------- date

    private val MONTHS = listOf("jan", "feb", "mar", "apr", "may", "jun", "jul", "aug", "sep", "oct", "nov", "dec")
    private fun monthOf(name: String): Int? = MONTHS.indexOf(name.lowercase().take(3)).takeIf { it >= 0 }?.plus(1)
    private fun ymd(y: Int, m: Int, d: Int): LocalDate? = runCatching { LocalDate.of(if (y < 100) 2000 + y else y, m, d) }.getOrNull()

    fun date(text: String, currency: String): LocalDate? {
        Regex("""\b(\d{4})[-/.](\d{1,2})[-/.](\d{1,2})\b""").findAll(text).forEach { m ->
            ymd(m.groupValues[1].toInt(), m.groupValues[2].toInt(), m.groupValues[3].toInt())?.let { return it }
        }
        Regex("""\b(\d{1,2})[-/.](\d{1,2})[-/.](\d{2,4})\b""").findAll(text).forEach { m ->
            val a = m.groupValues[1].toInt(); val b = m.groupValues[2].toInt(); val y = m.groupValues[3].toInt()
            val monthFirst = when { a > 12 -> false; b > 12 -> true; else -> currency == "USD" || currency == "" }
            (if (monthFirst) ymd(y, a, b) else ymd(y, b, a))?.let { return it }
        }
        Regex("""\b([A-Za-z]{3,9})\.?\s+(\d{1,2})(?:st|nd|rd|th)?,?\s+(\d{4})\b""").findAll(text).forEach { m ->
            monthOf(m.groupValues[1])?.let { mo -> ymd(m.groupValues[3].toInt(), mo, m.groupValues[2].toInt())?.let { return it } }
        }
        Regex("""\b(\d{1,2})\s+([A-Za-z]{3,9})\.?,?\s+(\d{4})\b""").findAll(text).forEach { m ->
            monthOf(m.groupValues[2])?.let { mo -> ymd(m.groupValues[3].toInt(), mo, m.groupValues[1].toInt())?.let { return it } }
        }
        return null
    }

    // ---------------------------------------------------------------- merchant + category

    private val SKIP = Regex("""(?i)(receipt|invoice|\btel\b|phone|www\.|http|@|\bdate\b|order|table|server|cashier|welcome|thank|tin[:\s]|vat reg)""")

    fun merchant(lines: List<String>): String {
        val line = lines.take(8).firstOrNull { l ->
            val letters = l.count { it.isLetter() }
            letters >= 3 && letters * 2 >= l.length && !SKIP.containsMatchIn(l)
        } ?: return ""
        val t = line.take(60).trim()
        return if (t.any { it.isLowerCase() }) t else t.lowercase().split(' ').joinToString(" ") { w -> w.replaceFirstChar { it.uppercase() } }
    }

    private val CATEGORY_WORDS = listOf(
        "Lodging" to listOf("hotel", "resort", "hostel", "lodge", "inn ", "guest house", "homestay", "airbnb", "room charge"),
        "Transport" to listOf("taxi", "grab", "uber", "bolt", "ferry", "train", "railway", "metro", "bus ", "fuel", "gasoline", "petrol", "parking", "toll", "airline", "airways", "airport", "car rental", "tricycle"),
        "Food" to listOf("restaurant", "cafe", "café", "coffee", "bar ", "grill", "kitchen", "bakery", "pizza", "burger", "dining", "bistro", "eatery", "noodle", "ramen", "sushi", "tea", "jollibee", "mcdonald", "starbucks", "food", "dine-in", "dine in", "menu"),
        "Activity" to listOf("tour", "admission", "entrance", "museum", "national park", "spa", "massage", "ticket", "excursion", "snorkel", "diving"),
        "Shopping" to listOf("mart", "mall", "store", "shop", "supermarket", "market", "7-eleven", "pharmacy", "drugstore", "boutique", "souvenir"),
    )

    fun category(text: String): String {
        val t = " " + text.lowercase() + " "
        return CATEGORY_WORDS.firstOrNull { (_, words) -> words.any { t.contains(it) } }?.first ?: "Other"
    }
}
