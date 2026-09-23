package com.kl.travel.data

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.util.UUID

data class VaultDoc(val id: String, val label: String, val name: String, val mime: String, val addedAt: Long) {
    val isPdf get() = mime == "application/pdf"
}

/**
 * Private document vault for passports, visas, tickets and insurance.
 * Files are copied into the app's own storage (encrypted at rest by Android on modern phones, removed on uninstall,
 * excluded from cloud backup). They never go to the Google Sheet or any server.
 */
class DocStore(ctx: Context) {
    private val resolver = ctx.applicationContext.contentResolver
    private val dir = File(ctx.applicationContext.filesDir, "vault").apply { mkdirs() }
    private val index = File(dir, "index.json")

    fun list(): List<VaultDoc> = runCatching {
        val a = JSONArray(index.readText())
        (0 until a.length()).map {
            val o = a.getJSONObject(it)
            VaultDoc(o.getString("id"), o.getString("label"), o.getString("name"), o.getString("mime"), o.getLong("at"))
        }.sortedByDescending { it.addedAt }
    }.getOrDefault(emptyList())

    fun file(id: String) = File(dir, "$id.bin")

    private fun save(docs: List<VaultDoc>) {
        val a = JSONArray()
        docs.forEach { a.put(JSONObject().put("id", it.id).put("label", it.label).put("name", it.name).put("mime", it.mime).put("at", it.addedAt)) }
        index.writeText(a.toString())
    }

    suspend fun import(uri: Uri, label: String): Result<VaultDoc> = withContext(Dispatchers.IO) {
        runCatching {
            val mime = resolver.getType(uri).orEmpty()
            require(mime == "application/pdf" || mime.startsWith("image/")) { "Only PDF and photo files can be added." }
            val name = resolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use {
                if (it.moveToFirst()) it.getString(0) else null
            } ?: "Document"
            val id = UUID.randomUUID().toString()
            val out = file(id)
            var total = 0L
            resolver.openInputStream(uri)!!.use { input ->
                out.outputStream().use { o ->
                    val buf = ByteArray(64 * 1024)
                    while (true) {
                        val n = input.read(buf); if (n < 0) break
                        total += n
                        if (total > MAX_BYTES) { out.delete(); error("That file is over 25 MB.") }
                        o.write(buf, 0, n)
                    }
                }
            }
            val doc = VaultDoc(id, label, name, mime, System.currentTimeMillis())
            save(list() + doc)
            doc
        }
    }

    fun delete(id: String) {
        file(id).delete()
        save(list().filterNot { it.id == id })
    }

    companion object {
        const val MAX_BYTES = 25L * 1024 * 1024
        val LABELS = listOf("Passport", "Visa", "Ticket", "Insurance", "Other")
    }
}
