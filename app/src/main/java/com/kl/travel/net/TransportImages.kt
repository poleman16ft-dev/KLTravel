package com.kl.travel.net

import com.kl.travel.data.Categories
import com.kl.travel.data.TripItem
import org.json.JSONObject

/**
 * Works out what picture to show for a flight, ferry, train and so on, and picks the right file from a Wikimedia Commons search.
 * Pure logic (no Android), unit-tested. The network part is TransportPhotoClient.
 */
object TransportImages {
    /** One search to try. A file is accepted only if its name contains every word in [must] (spaces and punctuation ignored). */
    data class Step(val query: String, val must: List<String>, val label: String)
    data class Plan(val key: String, val steps: List<Step>)

    sealed interface Source {
        data object None : Source
        /** A row you drive yourself: your own vehicle from Settings. */
        data object MyVehicle : Source
        data class Search(val plan: Plan) : Source
    }

    class Candidate(
        val title: String, val thumbUrl: String, val width: Int, val height: Int, val mime: String,
        val artist: String?, val license: String?, val pageUrl: String?, val index: Int,
    )

    private val NEGATIVE = setOf(
        "interior", "cabin", "seat", "seats", "lounge", "cockpit", "logo", "logos", "map", "maps", "route", "routes", "diagram", "drawing",
        "crash", "accident", "wreck", "fire", "blueprint", "schematic", "poster", "ticket", "timetable", "menu", "meal", "crew", "uniform",
        "model", "toy", "cutaway", "painting", "screenshot", "flag", "stamp", "badge", "emblem", "livery", "sketch", "engine", "tail",
    )
    private val IMAGE_MIME = setOf("image/jpeg", "image/png", "image/webp")
    private val MAKERS = setOf("boeing", "airbus", "embraer", "bombardier", "de", "havilland", "canada", "canadair", "mcdonnell", "douglas")

    fun norm(s: String): String = s.lowercase().filter { it.isLetterOrDigit() }
    private fun words(s: String): List<String> = s.lowercase().split(Regex("[^a-z0-9]+")).filter { it.isNotEmpty() }

    // ------------------------------------------------------------------ what to show

    /** "Boeing 787-9" -> "7879", "Airbus A330-300" -> "a330300", "De Havilland Dash 8" -> "dash8". */
    internal fun planeKey(aircraft: String): String = words(aircraft).filter { it !in MAKERS }.joinToString("")
    /** "7879" style key trimmed to the family: "787", "a330", "atr72". */
    internal fun planeFamily(aircraft: String): String {
        val first = aircraft.lowercase().split(Regex("\\s+")).filter { it.replace(Regex("[^a-z0-9-]"), "") !in MAKERS }
            .joinToString("") { it }.replace(Regex("[^a-z0-9-]"), "")
        val fam = Regex("^(?:a\\d{3}|\\d{3}|atr\\d{2}|dash\\d+|dhc\\d+|[eq]\\d{3}|crj\\d+)").find(first)?.value
        return fam ?: first.substringBefore('-')
    }

    private val FERRY_WORDS = setOf("ferry", "boat", "ship", "catamaran", "speedboat", "cruise", "ferries", "fastcraft", "seajet", "ocean")
    private val TRAIN_WORDS = setOf("train", "rail", "railway", "metro", "subway", "shinkansen", "tram", "mrt", "lrt", "skytrain", "amtrak")
    private val BUS_WORDS = setOf("bus", "coach")
    private val LOCAL_WORDS = mapOf("tricycle" to "Tricycle", "jeepney" to "Jeepney", "tuk" to "Tuk-tuk", "rickshaw" to "Rickshaw")
    private val DRIVE_WORDS = setOf("drive", "driving", "car", "rental", "selfdrive")
    private val SKIP_WORDS = setOf("taxi", "grab", "uber", "lyft", "rideshare", "ride", "cab", "walk", "van", "shuttle", "transfer")
    private val KNOWN_OPERATORS = listOf("OceanJet", "2GO", "Cokaliong", "Weesam Express", "Montenegro Lines", "Lite Ferries", "Starlite", "Fast Cat", "SuperCat", "Bluewater")

    private fun operatorOf(item: TripItem): String? {
        KNOWN_OPERATORS.firstOrNull { item.title.contains(it, ignoreCase = true) || item.transport.contains(it, ignoreCase = true) }?.let { return it }
        val tail = item.title.substringAfterLast(',', "").trim()
        return tail.takeIf { it.isNotBlank() && it.split(" ").size <= 3 && !it.contains("→") && !it.contains(">") }
    }

    /** What a Transport-tab row should show, given the plane type (sheet Aircraft column, else live lookup). */
    fun forItem(item: TripItem, livePlane: String? = null): Source {
        if (item.isFlight) return flightPlan(item, item.aircraft.ifBlank { livePlane.orEmpty() })
        if (!Categories.isTransport(item)) return Source.None
        val w = words(item.type + " " + item.title + " " + item.transport)
        val set = w.toSet()
        if (set.any { it in setOf("drive", "driving", "selfdrive") } || (set.any { it in DRIVE_WORDS } && set.none { it in SKIP_WORDS })) return Source.MyVehicle
        if (set.any { it in setOf("taxi", "grab", "uber", "lyft", "rideshare", "cab") }) return Source.None
        val op = operatorOf(item)
        if (set.any { it in FERRY_WORDS }) {
            val steps = mutableListOf<Step>()
            if (op != null) {
                steps += Step("$op ferry", listOf(norm(op)), "$op ferry")
                steps += Step(op, listOf(norm(op)), "$op ferry")
            }
            steps += Step("ferry boat", listOf("ferry"), "Ferry (example)")
            return Source.Search(Plan("ferry|${op.orEmpty().lowercase()}", steps))
        }
        if (set.any { it in TRAIN_WORDS }) {
            val name = words(item.title + " " + item.transport).firstOrNull { it in setOf("shinkansen", "amtrak", "skytrain", "mrt", "lrt", "metro", "subway", "tram") }
            val steps = mutableListOf<Step>()
            if (name != null) steps += Step(name, listOf(name), "$name (example)")
            steps += Step("passenger train", listOf("train"), "Train (example)")
            return Source.Search(Plan("train|${name.orEmpty()}", steps))
        }
        if (set.any { it in BUS_WORDS }) return Source.Search(Plan("bus", listOf(Step("intercity bus", listOf("bus"), "Bus (example)"))))
        LOCAL_WORDS.entries.firstOrNull { it.key in set }?.let { (k, label) ->
            return Source.Search(Plan("local|$k", listOf(Step(label, listOf(k), "$label (example)"))))
        }
        return Source.None
    }

    private fun flightPlan(item: TripItem, plane: String): Source {
        val airline = Airlines.photoName(item.flight)?.takeIf { it.length > 2 } ?: return if (plane.isBlank()) Source.None else planePlan(plane)
        if (plane.isBlank()) return Source.Search(Plan("air|${airline.lowercase()}", listOf(Step("$airline aircraft", listOf(norm(airline)), "$airline aircraft (example)"))))
        val key = planeKey(plane); val fam = planeFamily(plane)
        val steps = mutableListOf(Step("$airline $plane", listOf(norm(airline), key), "$airline $plane"))
        if (fam != key && fam.length >= 3) steps += Step("$airline $plane".replace(plane, fam), listOf(norm(airline), fam), "$airline $plane")
        steps += Step(plane, listOf(key), "$plane (example)")
        steps += Step("$airline aircraft", listOf(norm(airline)), "$airline aircraft (example)")
        return Source.Search(Plan("air|${airline.lowercase()}|$key", steps))
    }

    private fun planePlan(plane: String) =
        Source.Search(Plan("plane|${planeKey(plane)}", listOf(Step(plane, listOf(planeKey(plane)), "$plane (example)"))))

    /** Search plan for the vehicle typed in Settings, e.g. "2019 Toyota RAV4 XLE". */
    fun vehiclePlan(text: String): Plan? {
        val w = words(text)
        if (w.isEmpty()) return null
        val short = w.filter { !Regex("(19|20)\\d\\d").matches(it) }.take(2)
        val steps = mutableListOf(Step(text.trim(), w, text.trim()))
        if (short.isNotEmpty() && short != w) steps += Step(short.joinToString(" "), short, text.trim())
        return Plan("veh|" + w.joinToString(""), steps)
    }

    // ------------------------------------------------------------------ picking from Commons

    /** Reads a Commons generator=search & prop=imageinfo response into candidates, best search rank first. */
    fun parseResults(json: String): List<Candidate> {
        val pages = JSONObject(json).optJSONObject("query")?.optJSONObject("pages") ?: return emptyList()
        val out = mutableListOf<Candidate>()
        for (k in pages.keys()) {
            val p = pages.getJSONObject(k)
            val ii = p.optJSONArray("imageinfo")?.optJSONObject(0) ?: continue
            val thumb = ii.optString("thumburl").ifBlank { ii.optString("url") }
            if (!thumb.startsWith("https://")) continue
            val meta = ii.optJSONObject("extmetadata")
            fun m(name: String) = meta?.optJSONObject(name)?.optString("value")?.replace(Regex("<[^>]*>"), "")?.trim()?.takeIf { it.isNotBlank() }
            out += Candidate(
                title = p.optString("title").removePrefix("File:"), thumbUrl = thumb,
                width = ii.optInt("width"), height = ii.optInt("height"), mime = ii.optString("mime"),
                artist = m("Artist"), license = m("LicenseShortName"), pageUrl = ii.optString("descriptionurl").takeIf { it.startsWith("https://") },
                index = p.optInt("index", 999),
            )
        }
        return out.sortedBy { it.index }
    }

    /** First good file for [step]: a real photo (not a PDF, logo, seat or cabin shot), big enough, and named for what we want. */
    fun pick(all: List<Candidate>, step: Step): Candidate? =
        all.filter { it.mime in IMAGE_MIME && it.width >= 600 }
            .filter { c -> words(c.title).none { it in NEGATIVE } }
            .filter { c -> val t = norm(c.title); step.must.all { it in t } }
            .sortedWith(compareBy<Candidate> { if (it.width >= it.height) 0 else 1 }.thenBy { it.index })
            .firstOrNull()

    /** "Photo: KCS · CC BY-SA 4.0 · Wikimedia Commons" */
    fun credit(c: Candidate): String = listOfNotNull(c.artist?.take(60), c.license, "Wikimedia Commons").joinToString(" · ")
}
