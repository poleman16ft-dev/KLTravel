package com.kl.travel.net

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest

data class HotelPhoto(val file: File, val credit: String?)

/**
 * Finds a photo of a hotel with Google Places API (New): text search -> first photo -> image.
 * Uses the same Maps key as routes (Settings). Each hotel is fetched once and cached on disk,
 * so it costs a couple of API calls per hotel, not per screen view.
 */
object HotelPhotoClient {
    private const val SEARCH = "https://places.googleapis.com/v1/places:searchText"

    internal data class Found(val photoName: String, val credit: String?)

    /** Pulls the first photo (and its author, which Google asks apps to show) from a searchText response. */
    internal fun parseSearch(json: String): Found? {
        val places = JSONObject(json).optJSONArray("places") ?: return null
        for (i in 0 until places.length()) {
            val photos = places.getJSONObject(i).optJSONArray("photos") ?: continue
            if (photos.length() == 0) continue
            val p = photos.getJSONObject(0)
            val name = p.optString("name").takeIf { it.isNotBlank() } ?: continue
            val credit = p.optJSONArray("authorAttributions")?.optJSONObject(0)?.optString("displayName")?.takeIf { it.isNotBlank() }
            return Found(name, credit)
        }
        return null
    }

    internal fun parsePhotoUri(json: String): String? =
        JSONObject(json).optString("photoUri").takeIf { it.startsWith("https://") }

    internal fun cacheName(name: String, address: String): String {
        val d = MessageDigest.getInstance("SHA-1").digest("${name.trim().lowercase()}|${address.trim().lowercase()}".toByteArray())
        return d.joinToString("") { "%02x".format(it) }.take(24)
    }

    fun cached(ctx: Context, name: String, address: String): HotelPhoto? {
        val base = cacheName(name, address)
        val img = File(dir(ctx), "$base.jpg")
        if (!img.exists() || img.length() == 0L) return null
        return HotelPhoto(img, File(dir(ctx), "$base.credit").takeIf { it.exists() }?.readText()?.takeIf { it.isNotBlank() })
    }

    private fun dir(ctx: Context) = File(ctx.filesDir, "hotel_photos").also { it.mkdirs() }

    /** Returns null (not an error) when Google has no photo for this hotel. */
    suspend fun fetch(ctx: Context, name: String, address: String): Result<HotelPhoto?> = withContext(Dispatchers.IO) {
        runCatching {
            cached(ctx, name, address)?.let { return@runCatching it }
            val key = MapsKey.effective(ctx) ?: error("Add your Google Maps key in Settings to see lodging photos.")

            val query = listOf(name, address).filter { it.isNotBlank() }.distinct().joinToString(", ")
            val body = JSONObject().put("textQuery", query).put("maxResultCount", 1).toString()
            val found = parseSearch(post(ctx, SEARCH, key, "places.photos", body)) ?: return@runCatching null

            val media = "https://places.googleapis.com/v1/${found.photoName}/media?maxWidthPx=900&skipHttpRedirect=true"
            val uri = parsePhotoUri(get(ctx, media, key)) ?: return@runCatching null

            val conn = (URL(uri).openConnection() as HttpURLConnection).apply { connectTimeout = 15000; readTimeout = 30000 }
            val bytes = try {
                if (conn.responseCode !in 200..299) error("Photo download failed (HTTP ${conn.responseCode}).")
                conn.inputStream.use { it.readBytes() }
            } finally { conn.disconnect() }

            val base = cacheName(name, address)
            val img = File(dir(ctx), "$base.jpg")
            img.writeBytes(bytes)
            found.credit?.let { File(dir(ctx), "$base.credit").writeText(it) }
            HotelPhoto(img, found.credit)
        }
    }

    private fun open(ctx: Context, url: String, key: String, method: String, mask: String?): HttpURLConnection {
        com.kl.travel.data.GoogleUsage.require(com.kl.travel.data.Prefs(ctx), com.kl.travel.data.GoogleApi.PLACES)
        return (URL(url).openConnection() as HttpURLConnection).apply {
            requestMethod = method; connectTimeout = 15000; readTimeout = 20000
            setRequestProperty("X-Goog-Api-Key", key)
            mask?.let { setRequestProperty("X-Goog-FieldMask", it) }
            // Same app-identity headers as the Routes call, in case the key is locked to this app.
            setRequestProperty("X-Android-Package", ctx.packageName)
            RoutesClient.signatureSha1(ctx)?.let { setRequestProperty("X-Android-Cert", it) }
        }
    }

    private fun read(conn: HttpURLConnection): String {
        try {
            val code = conn.responseCode
            val text = (if (code in 200..299) conn.inputStream else conn.errorStream)?.bufferedReader()?.use { it.readText() }.orEmpty()
            if (code !in 200..299) {
                val msg = runCatching { JSONObject(text).getJSONObject("error").getString("message") }.getOrNull()
                error(msg ?: "Places API error (HTTP $code)")
            }
            return text
        } finally { conn.disconnect() }
    }

    private fun post(ctx: Context, url: String, key: String, mask: String, body: String): String {
        val c = open(ctx, url, key, "POST", mask).apply { doOutput = true; setRequestProperty("Content-Type", "application/json") }
        c.outputStream.use { it.write(body.toByteArray()) }
        return read(c)
    }

    private fun get(ctx: Context, url: String, key: String): String = read(open(ctx, url, key, "GET", null))
}
