package com.kl.travel.net

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.net.Uri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.security.MessageDigest

/** A transport photo on disk, with what to show under it. */
data class TransportPhoto(val file: File, val label: String, val credit: String, val pageUrl: String?)

/**
 * Finds a free photo of the plane, ferry, train... on Wikimedia Commons (no key needed) and keeps it on disk,
 * so each kind of vehicle is downloaded once. "Nothing found" is remembered for 3 days so we don't keep searching.
 */
object TransportPhotoClient {
    private const val API = "https://commons.wikimedia.org/w/api.php"
    private const val UA = "KLTravel/1.22 (Android trip app)"
    private const val NEGATIVE_MS = 3L * 24 * 3600 * 1000

    internal fun searchUrl(query: String): String =
        API + "?action=query&format=json&generator=search&gsrnamespace=6&gsrlimit=15" +
            "&gsrsearch=" + URLEncoder.encode("$query filetype:bitmap", "UTF-8") +
            "&prop=imageinfo&iiprop=" + URLEncoder.encode("url|size|mime|extmetadata", "UTF-8") +
            "&iiextmetadatafilter=" + URLEncoder.encode("Artist|LicenseShortName", "UTF-8") + "&iiurlwidth=1000"

    internal fun cacheName(key: String): String =
        MessageDigest.getInstance("SHA-1").digest(key.trim().lowercase().toByteArray()).joinToString("") { "%02x".format(it) }.take(24)

    private fun dir(ctx: Context) = File(ctx.filesDir, "transport_photos").also { it.mkdirs() }

    fun cached(ctx: Context, key: String): TransportPhoto? {
        val base = cacheName(key)
        val img = File(dir(ctx), "$base.jpg"); val meta = File(dir(ctx), "$base.meta")
        if (!img.exists() || img.length() == 0L || !meta.exists()) return null
        return runCatching {
            val j = JSONObject(meta.readText())
            TransportPhoto(img, j.getString("label"), j.getString("credit"), j.optString("url").takeIf { it.startsWith("https://") })
        }.getOrNull()
    }

    private fun recentlyEmpty(ctx: Context, key: String): Boolean {
        val f = File(dir(ctx), cacheName(key) + ".none")
        return f.exists() && System.currentTimeMillis() - f.lastModified() < NEGATIVE_MS
    }

    /** null (not an error) when Commons has nothing suitable; a failure means the network was the problem. */
    suspend fun fetch(ctx: Context, plan: TransportImages.Plan): Result<TransportPhoto?> = withContext(Dispatchers.IO) {
        runCatching {
            cached(ctx, plan.key)?.let { return@runCatching it }
            if (recentlyEmpty(ctx, plan.key)) return@runCatching null
            for (step in plan.steps) {
                val cands = TransportImages.parseResults(text(searchUrl(step.query)))
                val c = TransportImages.pick(cands, step) ?: continue
                val bytes = bytes(c.thumbUrl)
                val bmp = BitmapFactory.decodeByteArray(bytes, 0, bytes.size) ?: continue
                val base = cacheName(plan.key)
                File(dir(ctx), "$base.jpg").outputStream().use { bmp.compress(Bitmap.CompressFormat.JPEG, 88, it) }
                File(dir(ctx), "$base.meta").writeText(
                    JSONObject().put("label", step.label).put("credit", TransportImages.credit(c)).put("url", c.pageUrl ?: "").toString())
                File(dir(ctx), "$base.none").delete()
                return@runCatching cached(ctx, plan.key)
            }
            File(dir(ctx), cacheName(plan.key) + ".none").writeText(System.currentTimeMillis().toString())
            null
        }
    }

    private fun open(url: String) = (URL(url).openConnection() as HttpURLConnection).apply {
        connectTimeout = 15000; readTimeout = 30000; setRequestProperty("User-Agent", UA)
    }

    private fun text(url: String): String {
        val c = open(url)
        try {
            if (c.responseCode !in 200..299) error("Photo search failed (HTTP ${c.responseCode}).")
            return c.inputStream.bufferedReader().use { it.readText() }
        } finally { c.disconnect() }
    }

    private fun bytes(url: String): ByteArray {
        val c = open(url)
        try {
            if (c.responseCode !in 200..299) error("Photo download failed (HTTP ${c.responseCode}).")
            return c.inputStream.use { it.readBytes() }
        } finally { c.disconnect() }
    }
}

/** Photos you choose yourself: one per trip item, and one for "my vehicle" (Settings). Copied into the app so they survive the picker. */
object UserPhotos {
    private const val MAX_PX = 1400

    private fun dir(ctx: Context) = File(ctx.filesDir, "user_photos").also { it.mkdirs() }
    fun itemFile(ctx: Context, itemId: String): File =
        File(dir(ctx), "item_" + TransportPhotoClient.cacheName(itemId) + ".jpg")
    fun vehicleFile(ctx: Context): File = File(dir(ctx), "my_vehicle.jpg")

    fun has(f: File) = f.exists() && f.length() > 0

    /** Reads the picked image, turns it upright, scales it down and saves it as [dest]. Returns false if it couldn't be read. */
    suspend fun save(ctx: Context, src: Uri, dest: File): Boolean = withContext(Dispatchers.IO) {
        runCatching {
            val cr = ctx.contentResolver
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            cr.openInputStream(src)?.use { BitmapFactory.decodeStream(it, null, bounds) }
            if (bounds.outWidth <= 0) return@runCatching false
            var sample = 1
            while (maxOf(bounds.outWidth, bounds.outHeight) / (sample * 2) >= MAX_PX) sample *= 2
            var bmp = cr.openInputStream(src)?.use { BitmapFactory.decodeStream(it, null, BitmapFactory.Options().apply { inSampleSize = sample }) }
                ?: return@runCatching false
            val rot = cr.openInputStream(src)?.use { rotationOf(it) } ?: 0
            val longSide = maxOf(bmp.width, bmp.height)
            val m = Matrix()
            if (longSide > MAX_PX) m.postScale(MAX_PX.toFloat() / longSide, MAX_PX.toFloat() / longSide)
            if (rot != 0) m.postRotate(rot.toFloat())
            if (!m.isIdentity) bmp = Bitmap.createBitmap(bmp, 0, 0, bmp.width, bmp.height, m, true)
            val tmp = File(dest.parentFile, dest.name + ".tmp")
            tmp.outputStream().use { bmp.compress(Bitmap.CompressFormat.JPEG, 88, it) }
            dest.delete(); tmp.renameTo(dest)
        }.getOrDefault(false)
    }

    fun delete(f: File) { f.delete() }

    private fun rotationOf(input: java.io.InputStream): Int =
        runCatching {
            when (android.media.ExifInterface(input).getAttributeInt(android.media.ExifInterface.TAG_ORIENTATION, 1)) {
                android.media.ExifInterface.ORIENTATION_ROTATE_90 -> 90
                android.media.ExifInterface.ORIENTATION_ROTATE_180 -> 180
                android.media.ExifInterface.ORIENTATION_ROTATE_270 -> 270
                else -> 0
            }
        }.getOrDefault(0)
}
