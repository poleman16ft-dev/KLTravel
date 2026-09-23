package com.kl.travel.data

import org.json.JSONArray
import org.json.JSONObject

/**
 * One-file backup of everything you typed into the app: Maps and flight keys, lounge memberships, alert settings,
 * your trips (names and sheet links), saved travel modes and expenses. Caches (weather, lounges, flight status,
 * photos) are not saved; they refill by themselves after a sync.
 */
object Backup {
    const val FORMAT = 1
    private val KEYS = setOf(
        "mapsKey", "loungePrograms", "loungeNotes", "flightProvider", "flightKey", "flightCap",
        "alertsEnabled", "leaveBuffer", "airportBuffer", "alertLead", "defaultMode", "useMyLocation",
        "trips_v1", "activeTrip", "myVehicle", "liveTraffic",
        "gu_routes_cap", "gu_places_cap", "gu_maps_cap",
    )
    private val PREFIXES = listOf("mode_")

    fun wanted(key: String) = key in KEYS || PREFIXES.any { key.startsWith(it) }

    class Parsed(val prefs: Map<String, Any>, val expenses: List<ExpenseEntity>)

    fun encode(all: Map<String, Any?>, expenses: List<ExpenseEntity>, appVersion: String): String {
        val p = JSONObject()
        for ((k, v) in all) {
            if (!wanted(k)) continue
            val e = when (v) {
                is String -> JSONObject().put("t", "s").put("v", v)
                is Int -> JSONObject().put("t", "i").put("v", v)
                is Long -> JSONObject().put("t", "l").put("v", v)
                is Boolean -> JSONObject().put("t", "b").put("v", v)
                else -> continue
            }
            p.put(k, e)
        }
        val ex = JSONArray()
        expenses.forEach {
            ex.put(JSONObject().put("date", it.date).put("category", it.category).put("amount", it.amount)
                .put("note", it.note).put("tripId", it.tripId).put("currency", it.currency).put("originalAmount", it.originalAmount))
        }
        return JSONObject().put("app", "KLTravel").put("format", FORMAT).put("appVersion", appVersion)
            .put("prefs", p).put("expenses", ex).toString(2)
    }

    /** Throws IllegalArgumentException with a readable message when the file isn't a KL Travel backup. */
    fun decode(text: String): Parsed {
        val root = try { JSONObject(text) } catch (e: Exception) { throw IllegalArgumentException("That file isn't a KL Travel backup.") }
        require(root.optString("app") == "KLTravel") { "That file isn't a KL Travel backup." }
        require(root.optInt("format", 0) in 1..FORMAT) { "This backup is from a newer version of the app. Update the app first." }
        val prefs = mutableMapOf<String, Any>()
        val p = root.optJSONObject("prefs") ?: JSONObject()
        for (k in p.keys()) {
            if (!wanted(k)) continue
            val e = p.getJSONObject(k)
            prefs[k] = when (e.getString("t")) {
                "s" -> e.getString("v")
                "i" -> e.getInt("v")
                "l" -> e.getLong("v")
                "b" -> e.getBoolean("v")
                else -> continue
            }
        }
        val ex = mutableListOf<ExpenseEntity>()
        val arr = root.optJSONArray("expenses") ?: JSONArray()
        for (i in 0 until arr.length()) {
            val o = arr.getJSONObject(i)
            ex += ExpenseEntity(date = o.getString("date"), category = o.optString("category"), amount = o.getDouble("amount"),
                note = o.optString("note"), tripId = o.optLong("tripId", 1L),
                currency = o.optString("currency", "USD").ifBlank { "USD" }, originalAmount = o.optDouble("originalAmount", 0.0))
        }
        return Parsed(prefs, ex)
    }
}
