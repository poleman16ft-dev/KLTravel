package com.kl.travel.net

import com.kl.travel.data.Prefs
import org.json.JSONArray
import org.json.JSONObject
import java.time.Instant
import java.time.ZoneOffset

data class FxRate(val from: String, val to: String, val rate: Double, val date: String, val fetchedAt: Long, val stale: Boolean = false)

/** Exchange rates with no API key: Frankfurter (central-bank data) first, ExchangeRate-API open endpoint as backup. Cached for offline use. */
object CurrencyClient {
    private const val FRESH_MS = 6 * 60 * 60 * 1000L

    fun valid(code: String) = code.length == 3 && code.all { it.isLetter() }

    suspend fun rate(prefs: Prefs, fromIn: String, toIn: String): Result<FxRate> = runCatching {
        val from = fromIn.uppercase(); val to = toIn.uppercase()
        require(valid(from) && valid(to)) { "Use 3-letter currency codes like USD or PHP." }
        if (from == to) return@runCatching FxRate(from, to, 1.0, "", System.currentTimeMillis())

        cached(prefs, from, to)?.takeIf { System.currentTimeMillis() - it.fetchedAt < FRESH_MS }?.let { return@runCatching it }

        val fetched = runCatching { frankfurter(from, to) }.recoverCatching { erApi(from, to) }
        fetched.onSuccess { save(prefs, it) }
        fetched.getOrNull() ?: (cached(prefs, from, to) ?: reverse(prefs, from, to))?.copy(stale = true)
            ?: error("Can't reach the rate service and nothing is saved for $from to $to yet.")
    }

    private suspend fun frankfurter(from: String, to: String): FxRate {
        val text = Http.get("https://api.frankfurter.dev/v2/rates?base=$from&quotes=$to")
        val now = System.currentTimeMillis()
        if (text.trimStart().startsWith("[")) {
            val a = JSONArray(text)
            for (i in 0 until a.length()) {
                val o = a.getJSONObject(i)
                if (o.optString("quote").equals(to, true)) return FxRate(from, to, o.getDouble("rate"), o.optString("date"), now)
            }
        } else {
            val o = JSONObject(text)                                   // older response shape
            o.optJSONObject("rates")?.let { if (it.has(to)) return FxRate(from, to, it.getDouble(to), o.optString("date"), now) }
        }
        error("Rate for $to not found")
    }

    private suspend fun erApi(from: String, to: String): FxRate {
        val o = JSONObject(Http.get("https://open.er-api.com/v6/latest/$from"))
        val r = o.getJSONObject("rates")
        if (!r.has(to)) error("Rate for $to not found")
        val date = Instant.ofEpochSecond(o.optLong("time_last_update_unix")).atZone(ZoneOffset.UTC).toLocalDate().toString()
        return FxRate(from, to, r.getDouble(to), date, System.currentTimeMillis())
    }

    private fun save(p: Prefs, r: FxRate) = p.putStr("fx_${r.from}_${r.to}", "${r.rate}|${r.date}|${r.fetchedAt}")

    private fun cached(p: Prefs, from: String, to: String): FxRate? = p.getStr("fx_${from}_$to")?.split("|")?.let {
        if (it.size == 3) FxRate(from, to, it[0].toDoubleOrNull() ?: return null, it[1], it[2].toLongOrNull() ?: 0L) else null
    }

    private fun reverse(p: Prefs, from: String, to: String): FxRate? =
        cached(p, to, from)?.let { FxRate(from, to, 1.0 / it.rate, it.date, it.fetchedAt) }
}
