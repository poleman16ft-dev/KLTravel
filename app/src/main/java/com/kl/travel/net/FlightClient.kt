package com.kl.travel.net

import com.kl.travel.data.Prefs
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.time.LocalDate
import java.time.OffsetDateTime
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit

data class FlightStatus(
    val flight: String,
    val status: String,
    val depAirport: String?, val arrAirport: String?,
    val depTerminal: String?, val depGate: String?,
    val arrTerminal: String?, val arrGate: String?,
    val depScheduled: String?, val depEstimated: String?,   // "HH:mm" local
    val arrScheduled: String?, val arrEstimated: String?,
    val delayMin: Int?,
    val aircraft: String? = null,          // e.g. "Airbus A330-300"; not part of signature() so it never triggers alerts
) {
    val isCancelled get() = status.contains("cancel", true)
    val isDelayed get() = (delayMin ?: 0) >= 15 || status.contains("delay", true)

    /** Anything that should trigger a notification if it changes. */
    fun signature() = listOf(status, depTerminal, depGate, arrTerminal, arrGate, depEstimated, arrEstimated, delayMin)
        .joinToString("|") { it?.toString() ?: "" }

    fun headline(): String = buildString {
        append(status.replaceFirstChar { it.uppercase() })
        if ((delayMin ?: 0) > 0) append(" · +${delayMin} min")
    }

    fun detailLine(): String = buildList {
        if (depTerminal != null || depGate != null) add("Depart T${depTerminal ?: "-"} Gate ${depGate ?: "-"}")
        if (depEstimated != null && depEstimated != depScheduled) add("Now departs $depEstimated (was $depScheduled)")
        if (arrEstimated != null && arrEstimated != arrScheduled) add("Arrives $arrEstimated")
        if (arrTerminal != null || arrGate != null) add("Arrive T${arrTerminal ?: "-"} Gate ${arrGate ?: "-"}")
    }.joinToString(" · ")
}

/** Flight status from AeroDataBox (via RapidAPI) or aviationstack. Provider + key are set in Settings. */
object FlightClient {

    suspend fun fetch(prefs: Prefs, flightNumber: String, date: LocalDate): Result<FlightStatus?> =
        withContext(Dispatchers.IO) {
            runCatching {
                if (prefs.flightKey.isBlank()) error("Add a flight API key in Settings.")
                if (prefs.flightCallsThisMonth >= prefs.flightMonthlyCap) {
                    error("Monthly flight-lookup limit reached (${prefs.flightMonthlyCap}). Raise it in Settings if your plan allows.")
                }
                val fn = flightNumber.replace(" ", "").uppercase()
                prefs.countFlightCall()
                if (prefs.flightProvider == "aviationstack") aviationStack(prefs.flightKey, fn, date)
                else aeroDataBox(prefs.flightKey, fn, date)
            }
        }

    private fun get(url: String, headers: Map<String, String> = emptyMap()): String {
        val c = (URL(url).openConnection() as HttpURLConnection).apply {
            connectTimeout = 15000; readTimeout = 20000
            headers.forEach { (k, v) -> setRequestProperty(k, v) }
        }
        try {
            val code = c.responseCode
            val text = (if (code in 200..299) c.inputStream else c.errorStream)?.bufferedReader()?.use { it.readText() }.orEmpty()
            if (code == 204) return "[]"
            if (code == 401 || code == 403) error("Flight API rejected the key (HTTP $code).")
            if (code == 429) error("Flight API rate limit hit (HTTP 429).")
            if (code !in 200..299) error("Flight API error (HTTP $code).")
            return text
        } finally { c.disconnect() }
    }

    // ------------------------------------------------------------ AeroDataBox
    private fun aeroDataBox(key: String, fn: String, date: LocalDate): FlightStatus? {
        val text = get(
            "https://aerodatabox.p.rapidapi.com/flights/number/${URLEncoder.encode(fn, "UTF-8")}/$date?dateLocalRole=Departure",
            mapOf("X-RapidAPI-Key" to key, "X-RapidAPI-Host" to "aerodatabox.p.rapidapi.com"),
        )
        val arr = JSONArray(text)
        if (arr.length() == 0) return null
        val f = arr.getJSONObject(0)
        val dep = f.optJSONObject("departure"); val arrv = f.optJSONObject("arrival")
        fun hm(o: JSONObject?, k: String) = o?.optJSONObject(k)?.optString("local")?.takeIf { it.length >= 16 }?.substring(11, 16)
        fun s(o: JSONObject?, k: String) = o?.optString(k)?.takeIf { it.isNotBlank() }
        val depSched = hm(dep, "scheduledTime")
        val depRev = hm(dep, "revisedTime") ?: hm(dep, "predictedTime")
        val arrSched = hm(arrv, "scheduledTime")
        val arrRev = hm(arrv, "revisedTime") ?: hm(arrv, "predictedTime")
        val delay = runCatching {
            val fmt = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mmX")
            val a = OffsetDateTime.parse(dep!!.getJSONObject("scheduledTime").getString("utc"), fmt)
            val r = OffsetDateTime.parse((dep.optJSONObject("revisedTime") ?: dep.getJSONObject("predictedTime")).getString("utc"), fmt)
            ChronoUnit.MINUTES.between(a, r).toInt()
        }.getOrNull()
        return FlightStatus(
            flight = fn, status = f.optString("status", "Unknown"),
            depAirport = dep?.optJSONObject("airport")?.optString("iata")?.ifBlank { null },
            arrAirport = arrv?.optJSONObject("airport")?.optString("iata")?.ifBlank { null },
            depTerminal = s(dep, "terminal"), depGate = s(dep, "gate"),
            arrTerminal = s(arrv, "terminal"), arrGate = s(arrv, "gate"),
            depScheduled = depSched, depEstimated = depRev,
            arrScheduled = arrSched, arrEstimated = arrRev, delayMin = delay,
            aircraft = f.optJSONObject("aircraft")?.optString("model")?.takeIf { it.isNotBlank() && it != "null" },
        )
    }

    // ------------------------------------------------------------ aviationstack
    private fun aviationStack(key: String, fn: String, date: LocalDate): FlightStatus? {
        val text = get("https://api.aviationstack.com/v1/flights?access_key=${URLEncoder.encode(key, "UTF-8")}&flight_iata=${URLEncoder.encode(fn, "UTF-8")}")
        val root = JSONObject(text)
        root.optJSONObject("error")?.let { error(it.optString("message", "aviationstack error")) }
        val data = root.optJSONArray("data") ?: return null
        var pick: JSONObject? = null
        for (i in 0 until data.length()) {
            val o = data.getJSONObject(i)
            if (o.optString("flight_date") == date.toString()) { pick = o; break }
        }
        val f = pick ?: return null
        val dep = f.optJSONObject("departure"); val arr = f.optJSONObject("arrival")
        // aviationstack labels local times as +00:00; show the clock time as given.
        fun hm(o: JSONObject?, k: String) = o?.optString(k)?.takeIf { it.length >= 16 && it != "null" }?.substring(11, 16)
        fun s(o: JSONObject?, k: String) = o?.optString(k)?.takeIf { it.isNotBlank() && it != "null" }
        val delay = dep?.takeIf { !it.isNull("delay") }?.optInt("delay")
        return FlightStatus(
            flight = fn, status = f.optString("flight_status", "unknown"),
            depAirport = s(dep, "iata"), arrAirport = s(arr, "iata"),
            depTerminal = s(dep, "terminal"), depGate = s(dep, "gate"),
            arrTerminal = s(arr, "terminal"), arrGate = s(arr, "gate"),
            depScheduled = hm(dep, "scheduled"), depEstimated = hm(dep, "estimated"),
            arrScheduled = hm(arr, "scheduled"), arrEstimated = hm(arr, "estimated"),
            delayMin = delay,
            aircraft = f.optJSONObject("aircraft")?.let { a -> s(a, "iata") ?: s(a, "icao") },
        )
    }
}
