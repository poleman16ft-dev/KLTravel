package com.kl.travel.data

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

data class SyncResult(val events: Int, val hotels: Int, val warnings: List<String>)

class SheetRepository(ctx: Context) {
    private val dao = TravelDb.get(ctx).dao()
    private val prefs = Prefs(ctx)

    /** Downloads the Events + Lodging tabs (a tab still called Hotels also works), parses them and replaces the offline cache. */
    suspend fun sync(tripId: Long = prefs.activeTripId): Result<SyncResult> = withContext(Dispatchers.IO) {
        runCatching {
            val trip = prefs.trips().firstOrNull { it.id == tripId } ?: error("Pick or add a trip first.")
            val id = extractSheetId(trip.sheetUrl) ?: error("Paste this trip's Google Sheets link first (Settings, or the trips screen).")
            val warnings = mutableListOf<String>()

            val gids = discoverGids(id)
            val eventsCsv = fetchTab(id, "Events", gids["Events"])
            val events = SheetParser.parseEvents(eventsCsv, warnings)

            val hotels = runCatching { SheetParser.parseHotels(fetchLodging(id, gids), warnings) }
                .getOrElse { warnings += "Lodging tab: ${it.message}"; emptyList() }

            dao.replaceAll(
                tripId,
                events.map { it.copy(tripId = tripId, id = Trips.scopedId(tripId, it.id)) },
                hotels.map { it.copy(tripId = tripId, id = Trips.scopedId(tripId, it.id)) },
            )
            prefs.setTripSynced(tripId, System.currentTimeMillis())
            SyncResult(events.size, hotels.size, warnings)
        }
    }

    // ------------------------------------------------------------------ fetch

    fun extractSheetId(input: String): String? {
        val s = input.trim()
        if (s.isEmpty()) return null
        Regex("/d/([a-zA-Z0-9_-]+)").find(s)?.let { return it.groupValues[1] }
        return if (Regex("^[a-zA-Z0-9_-]{20,}$").matches(s)) s else null
    }

    /**
     * The gviz endpoint blanks out cells that don't match a column's dominant type (e.g. the
     * hotel confirmation "K2E7Y1" in a column of all-numeric ones). The plain CSV export keeps
     * every cell as displayed, but needs the tab's gid, which we read from the sheet's page.
     * Anything goes wrong here -> empty map -> fetchTab falls back to gviz.
     */
    internal fun discoverGids(id: String): Map<String, String> = runCatching {
        val html = httpGet("https://docs.google.com/spreadsheets/d/$id/edit")
        parseGids(html)
    }.getOrDefault(emptyMap())

    /** The Lodging tab, or the older Hotels tab. */
    private fun fetchLodging(id: String, gids: Map<String, String>): List<List<String>> {
        pickLodgingTab(gids)?.let { return fetchTab(id, it, gids[it]) }
        for (tab in LODGING_TABS) {
            val rows = runCatching { fetchTab(id, tab, null) }.getOrNull() ?: continue
            if (rows.firstOrNull().orEmpty().any { it.contains("check", ignoreCase = true) }) return rows
        }
        error("No Lodging tab found (also looked for Hotels).")
    }

    private fun fetchTab(id: String, tab: String, gid: String?): List<List<String>> {
        if (gid != null) {
            val exported = runCatching {
                httpGet("https://docs.google.com/spreadsheets/d/$id/export?format=csv&gid=$gid")
            }.getOrNull()
            if (exported != null && !exported.trimStart().startsWith("<")) return Csv.parse(exported)
        }
        return Csv.parse(httpGet("https://docs.google.com/spreadsheets/d/$id/gviz/tq?tqx=out:csv&sheet=${URLEncoder.encode(tab, "UTF-8")}"))
    }

    private fun httpGet(url: String): String {
        val conn = (URL(url).openConnection() as HttpURLConnection).apply {
            connectTimeout = 15000; readTimeout = 20000; instanceFollowRedirects = true
        }
        try {
            val code = conn.responseCode
            if (code == 401 || code == 403 || code == 404) {
                error("Can't open the sheet (HTTP $code). Share it as \"Anyone with the link: Viewer\".")
            }
            if (code !in 200..299) error("Sheet request failed (HTTP $code).")
            val text = conn.inputStream.bufferedReader().use { it.readText() }
            if (text.trimStart().startsWith("<") && url.contains("gviz")) {
                error("Google returned a web page instead of data. Share the sheet as \"Anyone with the link: Viewer\".")
            }
            return text
        } finally {
            conn.disconnect()
        }
    }

    companion object {
        private val LODGING_TABS = listOf("Lodging", "Hotels")

        /** Prefers a tab named Lodging, else Hotels (case-insensitive); null if neither is in [gids]. */
        fun pickLodgingTab(gids: Map<String, String>): String? =
            LODGING_TABS.firstNotNullOfOrNull { want -> gids.keys.firstOrNull { it.equals(want, ignoreCase = true) } }

        private val GID_RE = Regex("""\[\d,0,\\"(\d+)\\",\[\{\\"1\\":\[\[0,0,\\"([^"\\]+)\\"""")
        /** Extracts {tab name -> gid} from the sheet's /edit page. */
        fun parseGids(html: String): Map<String, String> =
            GID_RE.findAll(html).associate { it.groupValues[2] to it.groupValues[1] }
    }
}
