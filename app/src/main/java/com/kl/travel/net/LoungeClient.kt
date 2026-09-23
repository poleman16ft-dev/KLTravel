package com.kl.travel.net

import android.content.Context
import com.kl.travel.data.Prefs
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import kotlin.math.*

data class Lounge(val id: String, val name: String, val address: String, val lat: Double, val lng: Double, val mapsUri: String?)

data class AirportLounges(val code: String, val centerLat: Double, val centerLng: Double, val lounges: List<Lounge>, val fetchedAt: Long) {
    fun toJson(): String = JSONObject().put("code", code).put("lat", centerLat).put("lng", centerLng).put("at", fetchedAt)
        .put("lounges", JSONArray().apply {
            lounges.forEach { put(JSONObject().put("id", it.id).put("name", it.name).put("addr", it.address).put("lat", it.lat).put("lng", it.lng).put("uri", it.mapsUri ?: "")) }
        }).toString()

    companion object {
        fun fromJson(s: String): AirportLounges = JSONObject(s).let { o ->
            val a = o.getJSONArray("lounges")
            AirportLounges(o.getString("code"), o.getDouble("lat"), o.getDouble("lng"),
                (0 until a.length()).map { a.getJSONObject(it) }.map { Lounge(it.getString("id"), it.getString("name"), it.optString("addr"), it.getDouble("lat"), it.getDouble("lng"), it.optString("uri").ifBlank { null }) },
                o.getLong("at"))
        }
    }
}

/**
 * Finds lounges at an airport with Google Places API (New): locate the airport, then search for lounges
 * around it. Results are cached for 30 days per airport, so each airport costs two requests once.
 */
object LoungeClient {
    private const val SEARCH = "https://places.googleapis.com/v1/places:searchText"
    private const val FRESH_MS = 30L * 24 * 3600 * 1000
    private const val RADIUS_M = 4000.0

    private val LOUNGE_NAME = Regex("lounge|club|centurion|priority pass|plaza premium|escape|the wing|infinity|mabuhay|suite|salon|sala vip|vip room", RegexOption.IGNORE_CASE)

    /** IATA codes for a flight row: departure first (from the Location like "Taipei Taoyuan (TPE) T2"), then both from the title "TPE → NRT". */
    fun airportCodes(location: String, title: String): List<String> {
        val out = LinkedHashSet<String>()
        Regex("\\(([A-Z]{3})\\)").find(location)?.let { out += it.groupValues[1] }
        com.kl.travel.data.Layovers.route(title)?.let { out += it.first; out += it.second }
        return out.toList()
    }

    internal fun distanceM(lat1: Double, lng1: Double, lat2: Double, lng2: Double): Double {
        val r = 6371000.0
        val dLat = Math.toRadians(lat2 - lat1); val dLng = Math.toRadians(lng2 - lng1)
        val a = sin(dLat / 2).pow(2) + cos(Math.toRadians(lat1)) * cos(Math.toRadians(lat2)) * sin(dLng / 2).pow(2)
        return 2 * r * asin(min(1.0, sqrt(a)))
    }

    private fun place(o: JSONObject): Lounge? {
        val loc = o.optJSONObject("location") ?: return null
        val name = o.optJSONObject("displayName")?.optString("text")?.takeIf { it.isNotBlank() } ?: return null
        return Lounge(o.optString("id"), name, o.optString("formattedAddress"), loc.getDouble("latitude"), loc.getDouble("longitude"),
            o.optString("googleMapsUri").ifBlank { null })
    }

    internal fun parsePlaces(json: String): List<Lounge> {
        val a = JSONObject(json).optJSONArray("places") ?: return emptyList()
        return (0 until a.length()).mapNotNull { place(a.getJSONObject(it)) }
    }

    /** Keeps things that look like lounges and sit near the airport; nearest first, no duplicates. */
    internal fun filterLounges(all: List<Lounge>, centerLat: Double, centerLng: Double): List<Lounge> =
        all.filter { LOUNGE_NAME.containsMatchIn(it.name) && distanceM(centerLat, centerLng, it.lat, it.lng) <= RADIUS_M }
            .distinctBy { it.id.ifBlank { it.name } }
            .sortedBy { distanceM(centerLat, centerLng, it.lat, it.lng) }

    fun cached(prefs: Prefs, code: String): AirportLounges? =
        prefs.getStr("lounges_$code")?.let { runCatching { AirportLounges.fromJson(it) }.getOrNull() }

    suspend fun load(ctx: Context, code: String, addressHint: String, force: Boolean = false): Result<AirportLounges> = withContext(Dispatchers.IO) {
        val prefs = Prefs(ctx)
        val old = cached(prefs, code)
        if (!force && old != null && System.currentTimeMillis() - old.fetchedAt < FRESH_MS) return@withContext Result.success(old)
        runCatching {
            val key = MapsKey.effective(ctx) ?: error("Add your Google Maps key in Settings to see lounges.")
            val airportName = addressHint.substringBefore(",").trim().takeIf { it.contains("airport", true) } ?: "$code airport"
            val airport = parsePlaces(post(ctx, key, "places.location,places.displayName",
                JSONObject().put("textQuery", "$airportName ($code)").put("maxResultCount", 1).toString())).firstOrNull()
                ?: error("Couldn't locate airport $code.")
            val body = JSONObject().put("textQuery", "airport lounge").put("maxResultCount", 20).put("locationBias",
                JSONObject().put("circle", JSONObject().put("center", JSONObject().put("latitude", airport.lat).put("longitude", airport.lng)).put("radius", RADIUS_M))).toString()
            val found = filterLounges(parsePlaces(post(ctx, key, "places.id,places.displayName,places.formattedAddress,places.location,places.googleMapsUri", body)), airport.lat, airport.lng)
            AirportLounges(code, airport.lat, airport.lng, found, System.currentTimeMillis()).also { prefs.putStr("lounges_$code", it.toJson()) }
        }.recoverCatching { e -> old ?: throw e }
    }

    private fun post(ctx: Context, key: String, mask: String, body: String): String {
        com.kl.travel.data.GoogleUsage.require(Prefs(ctx), com.kl.travel.data.GoogleApi.PLACES)
        val c = (URL(SEARCH).openConnection() as HttpURLConnection).apply {
            requestMethod = "POST"; doOutput = true; connectTimeout = 15000; readTimeout = 20000
            setRequestProperty("Content-Type", "application/json")
            setRequestProperty("X-Goog-Api-Key", key)
            setRequestProperty("X-Goog-FieldMask", mask)
            setRequestProperty("X-Android-Package", ctx.packageName)
            RoutesClient.signatureSha1(ctx)?.let { setRequestProperty("X-Android-Cert", it) }
        }
        try {
            c.outputStream.use { it.write(body.toByteArray()) }
            val code = c.responseCode
            val text = (if (code in 200..299) c.inputStream else c.errorStream)?.bufferedReader()?.use { it.readText() }.orEmpty()
            if (code !in 200..299) error(runCatching { JSONObject(text).getJSONObject("error").getString("message") }.getOrNull() ?: "Places API error (HTTP $code)")
            return text
        } finally { c.disconnect() }
    }
}
