package com.kl.travel.net

import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import com.google.android.gms.maps.model.LatLng
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId

enum class TravelMode(val api: String, val label: String) {
    DRIVE("DRIVE", "Drive"),
    RIDESHARE("DRIVE", "Rideshare"),
    TRANSIT("TRANSIT", "Transit"),
    WALK("WALK", "Walk");

    companion object {
        fun from(s: String) = entries.firstOrNull { it.name == s } ?: DRIVE

        /** Maps the sheet's free-text "Transport" column to a default mode (null = no hint). */
        fun hint(t: String): TravelMode? {
            val x = t.lowercase()
            return when {
                x.isBlank() -> null
                listOf("grab", "taxi", "uber", "lyft", "rideshare", "cab").any { it in x } -> RIDESHARE
                "walk" in x -> WALK
                listOf("bus", "train", "metro", "subway", "transit", "jeepney").any { it in x } -> TRANSIT
                listOf("drive", "car", "van", "rental").any { it in x } -> DRIVE
                else -> null
            }
        }
    }
}

sealed class Place {
    data class Address(val text: String) : Place()
    data class Coord(val lat: Double, val lng: Double) : Place()
}

data class RouteResult(
    val durationSec: Long,
    val staticDurationSec: Long?,
    val distanceMeters: Int,
    val path: List<LatLng>,
    val start: LatLng?,
    val end: LatLng?,
) {
    val trafficDelayMin: Int get() = staticDurationSec?.let { ((durationSec - it) / 60).toInt().coerceAtLeast(0) } ?: 0
}

/** Remembers recent routes for a few minutes so reopening a screen or flipping back to a mode doesn't call Google again. */
object RouteCache {
    const val TTL_MS = 10 * 60 * 1000L
    private class Entry(val at: Long, val route: RouteResult)
    private val map = LinkedHashMap<String, Entry>()

    private fun p(p: Place) = when (p) {
        is Place.Address -> "a:" + p.text.trim().lowercase()
        is Place.Coord -> "c:%.3f,%.3f".format(java.util.Locale.US, p.lat, p.lng)   // about 100 m, so standing still hits the cache
    }
    fun key(o: Place, d: Place, mode: TravelMode, live: Boolean) = "${p(o)}>${p(d)}|${mode.api}|$live"

    @Synchronized fun get(key: String, now: Long = System.currentTimeMillis()): RouteResult? =
        map[key]?.takeIf { now - it.at < TTL_MS }?.route
    @Synchronized fun put(key: String, r: RouteResult, now: Long = System.currentTimeMillis()) {
        map[key] = Entry(now, r)
        if (map.size > 40) map.remove(map.keys.first())
    }
    @Synchronized fun clear() = map.clear()
}

/** Google Routes API (the replacement for the legacy Directions API). */
object RoutesClient {
    private const val URL_ = "https://routes.googleapis.com/directions/v2:computeRoutes"
    private const val MASK = "routes.duration,routes.staticDuration,routes.distanceMeters," +
        "routes.polyline.encodedPolyline,routes.legs.startLocation,routes.legs.endLocation"

    suspend fun compute(
        ctx: Context, origin: Place, dest: Place, mode: TravelMode, eventStart: LocalDateTime?,
    ): Result<RouteResult> = withContext(Dispatchers.IO) {
        runCatching {
            val prefs = com.kl.travel.data.Prefs(ctx)
            val live = prefs.liveTraffic
            val cacheKey = RouteCache.key(origin, dest, mode, live)
            RouteCache.get(cacheKey)?.let { return@runCatching it }
            val key = MapsKey.effective(ctx) ?: error("No Google Maps key yet. Add one in Settings.")
            com.kl.travel.data.GoogleUsage.require(prefs, com.kl.travel.data.GoogleApi.ROUTES)

            val body = JSONObject().apply {
                put("origin", placeJson(origin))
                put("destination", placeJson(dest))
                put("travelMode", mode.api)
                put("units", "IMPERIAL")
                val now = Instant.now()
                if (mode.api == "DRIVE" && !live) {
                    put("routingPreference", "TRAFFIC_UNAWARE")
                } else if (mode.api == "DRIVE") {
                    put("routingPreference", "TRAFFIC_AWARE")
                    // Traffic prediction needs a departure time that is now or in the future.
                    val est = eventStart?.atZone(ZoneId.systemDefault())?.toInstant()?.minusSeconds(45 * 60)
                    val dep = if (est != null && est.isAfter(now.plusSeconds(120))) est else now.plusSeconds(30)
                    put("departureTime", dep.toString())
                } else if (mode == TravelMode.TRANSIT && eventStart != null) {
                    val arr = eventStart.atZone(ZoneId.systemDefault()).toInstant()
                    if (arr.isAfter(now.plusSeconds(300))) put("arrivalTime", arr.toString())
                }
            }

            val conn = (URL(URL_).openConnection() as HttpURLConnection).apply {
                requestMethod = "POST"; doOutput = true
                connectTimeout = 15000; readTimeout = 20000
                setRequestProperty("Content-Type", "application/json")
                setRequestProperty("X-Goog-Api-Key", key)
                setRequestProperty("X-Goog-FieldMask", MASK)
                // Lets you lock the key to this app (Application restrictions) in Google Cloud.
                setRequestProperty("X-Android-Package", ctx.packageName)
                signatureSha1(ctx)?.let { setRequestProperty("X-Android-Cert", it) }
            }
            try {
                conn.outputStream.use { it.write(body.toString().toByteArray()) }
                val code = conn.responseCode
                val text = (if (code in 200..299) conn.inputStream else conn.errorStream)
                    ?.bufferedReader()?.use { it.readText() }.orEmpty()
                if (code !in 200..299) {
                    val msg = runCatching { JSONObject(text).getJSONObject("error").getString("message") }.getOrNull()
                    error(msg ?: "Routes API error (HTTP $code)")
                }
                val routes = JSONObject(text).optJSONArray("routes")
                if (routes == null || routes.length() == 0) error("No route found for this mode.")
                val r = routes.getJSONObject(0)
                val leg = r.optJSONArray("legs")?.optJSONObject(0)
                val result = RouteResult(
                    durationSec = parseSec(r.optString("duration")) ?: error("No duration in response"),
                    staticDurationSec = parseSec(r.optString("staticDuration")),
                    distanceMeters = r.optInt("distanceMeters"),
                    path = decode(r.getJSONObject("polyline").getString("encodedPolyline")),
                    start = leg?.optJSONObject("startLocation")?.let(::latLng),
                    end = leg?.optJSONObject("endLocation")?.let(::latLng),
                )
                RouteCache.put(cacheKey, result)
                result
            } finally { conn.disconnect() }
        }
    }

    private fun placeJson(p: Place) = when (p) {
        is Place.Address -> JSONObject().put("address", p.text)
        is Place.Coord -> JSONObject().put("location", JSONObject().put("latLng",
            JSONObject().put("latitude", p.lat).put("longitude", p.lng)))
    }

    private fun latLng(o: JSONObject): LatLng? =
        o.optJSONObject("latLng")?.let { LatLng(it.getDouble("latitude"), it.getDouble("longitude")) }

    private fun parseSec(s: String?): Long? = s?.removeSuffix("s")?.toDoubleOrNull()?.toLong()

    @Suppress("DEPRECATION")
    internal fun signatureSha1(ctx: Context): String? = runCatching {
        val pm = ctx.packageManager
        val sig = if (Build.VERSION.SDK_INT >= 28) {
            pm.getPackageInfo(ctx.packageName, PackageManager.GET_SIGNING_CERTIFICATES)
                .signingInfo?.apkContentsSigners?.firstOrNull()
        } else {
            pm.getPackageInfo(ctx.packageName, PackageManager.GET_SIGNATURES).signatures?.firstOrNull()
        } ?: return@runCatching null
        MessageDigest.getInstance("SHA-1").digest(sig.toByteArray()).joinToString("") { "%02X".format(it) }
    }.getOrNull()

    /** Standard Google encoded-polyline decoder. */
    fun decode(enc: String): List<LatLng> {
        val out = ArrayList<LatLng>()
        var i = 0; var lat = 0; var lng = 0
        while (i < enc.length) {
            var shift = 0; var result = 0; var b: Int
            do { b = enc[i++].code - 63; result = result or ((b and 0x1f) shl shift); shift += 5 } while (b >= 0x20)
            lat += if (result and 1 != 0) (result shr 1).inv() else result shr 1
            shift = 0; result = 0
            do { b = enc[i++].code - 63; result = result or ((b and 0x1f) shl shift); shift += 5 } while (b >= 0x20)
            lng += if (result and 1 != 0) (result shr 1).inv() else result shr 1
            out += LatLng(lat / 1e5, lng / 1e5)
        }
        return out
    }
}
