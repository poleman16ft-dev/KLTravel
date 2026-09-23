package com.kl.travel.data

import org.json.JSONArray
import org.json.JSONObject

/** One saved trip: a name and the Google Sheet it is read from. Events, lodging and expenses are stored per trip id. */
data class Trip(val id: Long, val name: String, val sheetUrl: String, val lastSync: Long = 0L)

/** Pure helpers for the list of trips (unit-tested; Prefs stores the JSON). */
object Trips {
    /** The trip that owns data saved before multiple trips existed (Room's default tripId). */
    const val LEGACY_ID = 1L

    fun encode(list: List<Trip>): String = JSONArray().apply {
        list.forEach { put(JSONObject().put("id", it.id).put("name", it.name).put("url", it.sheetUrl).put("sync", it.lastSync)) }
    }.toString()

    fun decode(json: String): List<Trip> {
        val a = JSONArray(json)
        return (0 until a.length()).map { a.getJSONObject(it) }
            .map { Trip(it.getLong("id"), it.getString("name"), it.optString("url"), it.optLong("sync")) }
    }

    fun nextId(list: List<Trip>): Long = (list.maxOfOrNull { it.id } ?: 0L) + 1L

    /** Ids of rows that belong to the legacy trip stay as they were so saved per-item settings keep working. */
    fun scopedId(tripId: Long, id: String): String = if (tripId == LEGACY_ID) id else "t${tripId}_$id"

    /** A trip made from the single sheet link an older version saved. */
    fun legacy(url: String, lastSync: Long): List<Trip> =
        if (url.isBlank()) emptyList() else listOf(Trip(LEGACY_ID, "My trip", url.trim(), lastSync))

    fun cleanName(name: String, existing: List<Trip>): String = name.trim().ifBlank { "Trip ${existing.size + 1}" }.take(40)
}

/** First and last event date of a trip, for the trip list. */
data class TripSpan(val firstDate: String?, val lastDate: String?, val n: Int)
