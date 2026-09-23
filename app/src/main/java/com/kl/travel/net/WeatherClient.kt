package com.kl.travel.net

import com.kl.travel.data.ItemKind
import com.kl.travel.data.TripItem
import com.kl.travel.data.Prefs
import org.json.JSONArray
import org.json.JSONObject
import java.net.URLEncoder
import java.time.LocalDate

data class DayWeather(
    val date: LocalDate, val place: String,
    val hiC: Double, val loC: Double, val code: Int,
    val rainPct: Int?, val rainMm: Double?,
    val source: String,            // "forecast" | "last year" | "actual"
) {
    val hiF get() = hiC * 9 / 5 + 32
    val loF get() = loC * 9 / 5 + 32
    val text get() = WeatherClient.describe(code)
    fun short() = "%d°/%d°F".format(Math.round(hiF), Math.round(loF)) +
        (rainPct?.takeIf { it >= 30 }?.let { " · rain $it%" } ?: "")
    fun toJson(): JSONObject = JSONObject().put("d", date.toString()).put("p", place).put("hi", hiC).put("lo", loC)
        .put("c", code).put("rp", rainPct ?: JSONObject.NULL).put("rm", rainMm ?: JSONObject.NULL).put("s", source)

    companion object {
        fun fromJson(o: JSONObject) = DayWeather(
            LocalDate.parse(o.getString("d")), o.getString("p"), o.getDouble("hi"), o.getDouble("lo"), o.getInt("c"),
            if (o.isNull("rp")) null else o.getInt("rp"), if (o.isNull("rm")) null else o.getDouble("rm"), o.getString("s"),
        )
    }
}

/** Open-Meteo (free, no key; data CC BY 4.0). Forecast up to 16 days out, otherwise the same date last year. */
object WeatherClient {
    private fun enc(s: String) = URLEncoder.encode(s, "UTF-8")

    /**
     * One place per trip day: the most common event location that day. A day with no located events borrows the
     * previous day's place, unless it has a flight (you are probably somewhere else by the end of it).
     */
    fun planDays(items: List<TripItem>): List<Pair<LocalDate, String>> {
        val out = mutableListOf<Pair<LocalDate, String>>()
        var prev: String? = null
        for ((d, list) in items.groupBy { it.date }.toSortedMap()) {
            val own = list.filter { it.kind == ItemKind.EVENT && it.location.isNotBlank() }
                .groupingBy { it.location.trim() }.eachCount().maxByOrNull { it.value }?.key
            val place = own ?: if (list.any { it.isFlight }) null else prev
            if (place != null) { out += d to place; prev = place } else prev = null
        }
        return out
    }

    fun describe(code: Int) = when (code) {
        0 -> "Clear"; 1 -> "Mostly clear"; 2 -> "Partly cloudy"; 3 -> "Overcast"
        45, 48 -> "Fog"
        51, 53, 55, 56, 57 -> "Drizzle"
        61, 63, 65, 66, 67 -> "Rain"
        71, 73, 75, 77 -> "Snow"
        80, 81, 82 -> "Rain showers"
        85, 86 -> "Snow showers"
        95 -> "Thunderstorm"; 96, 99 -> "Thunderstorm, hail"
        else -> "Mixed"
    }

    private suspend fun geocode(prefs: Prefs, place: String, country: String?): Pair<Double, Double>? {
        val key = "geo_${place.lowercase()}_${country?.lowercase()}"
        prefs.getStr(key)?.split(",")?.let { if (it.size == 2) return it[0].toDouble() to it[1].toDouble() }
        val arr = JSONObject(Http.get("https://geocoding-api.open-meteo.com/v1/search?name=${enc(place)}&count=10&language=en&format=json"))
            .optJSONArray("results") ?: return null
        var pick: JSONObject? = null
        for (i in 0 until arr.length()) {
            val o = arr.getJSONObject(i)
            if (country == null || o.optString("country").equals(country, true)) { pick = o; break }
        }
        val o = pick ?: return null
        prefs.putStr(key, "${o.getDouble("latitude")},${o.getDouble("longitude")}")
        return o.getDouble("latitude") to o.getDouble("longitude")
    }

    private data class Raw(val hi: Double, val lo: Double, val code: Int, val rainPct: Int?, val rainMm: Double?)

    private suspend fun fetchDaily(url: String, rainKey: String): Map<LocalDate, Raw> {
        val d = JSONObject(Http.get(url)).getJSONObject("daily")
        val t = d.getJSONArray("time")
        val hi = d.getJSONArray("temperature_2m_max"); val lo = d.getJSONArray("temperature_2m_min")
        val code = d.getJSONArray("weather_code"); val rain = d.optJSONArray(rainKey)
        val out = HashMap<LocalDate, Raw>()
        for (i in 0 until t.length()) {
            if (hi.isNull(i) || lo.isNull(i)) continue
            val r = rain?.takeIf { !it.isNull(i) }?.getDouble(i)
            out[LocalDate.parse(t.getString(i))] = Raw(
                hi.getDouble(i), lo.getDouble(i), if (code.isNull(i)) -1 else code.getInt(i),
                if (rainKey == "precipitation_probability_max") r?.toInt() else null,
                if (rainKey == "precipitation_sum") r else null,
            )
        }
        return out
    }

    suspend fun load(prefs: Prefs, days: List<Pair<LocalDate, String>>, country: String?, today: LocalDate = LocalDate.now()): List<DayWeather> {
        val result = mutableListOf<DayWeather>()
        for ((place, list) in days.groupBy({ it.second }, { it.first })) {
            val (lat, lng) = geocode(prefs, place, country) ?: continue
            val base = "latitude=$lat&longitude=$lng&timezone=auto"
            val fc = list.filter { !it.isBefore(today) && !it.isAfter(today.plusDays(15)) }
            val past = list.filter { it.isBefore(today) && !it.isAfter(today.minusDays(6)) }       // archive lags ~5 days
            val far = list.filter { it.isAfter(today.plusDays(15)) }

            if (fc.isNotEmpty()) {
                val m = fetchDaily("https://api.open-meteo.com/v1/forecast?$base&daily=weather_code,temperature_2m_max,temperature_2m_min,precipitation_probability_max&start_date=${fc.min()}&end_date=${fc.max()}", "precipitation_probability_max")
                fc.forEach { d -> m[d]?.let { result += DayWeather(d, place, it.hi, it.lo, it.code, it.rainPct, null, "forecast") } }
            }
            if (past.isNotEmpty()) {
                val m = fetchDaily("https://archive-api.open-meteo.com/v1/archive?$base&daily=weather_code,temperature_2m_max,temperature_2m_min,precipitation_sum&start_date=${past.min()}&end_date=${past.max()}", "precipitation_sum")
                past.forEach { d -> m[d]?.let { result += DayWeather(d, place, it.hi, it.lo, it.code, null, it.rainMm, "actual") } }
            }
            if (far.isNotEmpty()) {
                val ly = far.associateWith { it.minusYears(1) }
                val m = fetchDaily("https://archive-api.open-meteo.com/v1/archive?$base&daily=weather_code,temperature_2m_max,temperature_2m_min,precipitation_sum&start_date=${ly.values.min()}&end_date=${ly.values.max()}", "precipitation_sum")
                ly.forEach { (d, last) -> m[last]?.let { result += DayWeather(d, place, it.hi, it.lo, it.code, null, it.rainMm, "last year") } }
            }
        }
        return result.sortedBy { it.date }
    }
}
