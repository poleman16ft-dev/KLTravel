package com.kl.travel.data

import android.content.Context
import android.content.SharedPreferences
import java.time.YearMonth

class Prefs(ctx: Context) {
    private val sp: SharedPreferences = ctx.applicationContext.getSharedPreferences("kltravel", Context.MODE_PRIVATE)

    // ---- trips (several saved trips, one active) ----
    fun trips(): List<Trip> {
        sp.getString("trips_v1", null)?.let { return runCatching { Trips.decode(it) }.getOrDefault(emptyList()) }
        // First run of a version with trips: turn the old single sheet link into "My trip".
        val legacy = Trips.legacy(sp.getString("sheetUrl", "") ?: "", sp.getLong("lastSync", 0L))
        saveTrips(legacy)
        if (legacy.isNotEmpty()) sp.edit().putLong("activeTrip", legacy[0].id).apply()
        return legacy
    }

    private fun saveTrips(l: List<Trip>) = sp.edit().putString("trips_v1", Trips.encode(l)).apply()

    var activeTripId: Long
        get() {
            val id = sp.getLong("activeTrip", 0L); val t = trips()
            return if (t.any { it.id == id }) id else t.firstOrNull()?.id ?: 0L
        }
        set(v) = sp.edit().putLong("activeTrip", v).apply()

    fun activeTrip(): Trip? = trips().firstOrNull { it.id == activeTripId }

    fun addTrip(name: String, url: String): Trip {
        val list = trips()
        val t = Trip(Trips.nextId(list), Trips.cleanName(name, list), url.trim())
        saveTrips(list + t); return t
    }

    fun updateTrip(t: Trip) = saveTrips(trips().map { if (it.id == t.id) t else it })
    fun deleteTrip(id: Long) = saveTrips(trips().filter { it.id != id })
    fun setTripSynced(id: Long, at: Long) { trips().firstOrNull { it.id == id }?.let { updateTrip(it.copy(lastSync = at)) } }

    /** The active trip's sheet link (older code and Settings read and write it here). */
    var sheetUrl: String
        get() = activeTrip()?.sheetUrl ?: ""
        set(v) {
            val t = activeTrip()
            if (t != null) updateTrip(t.copy(sheetUrl = v.trim()))
            else if (v.isNotBlank()) activeTripId = addTrip("My trip", v).id
        }

    val lastSync: Long get() = activeTrip()?.lastSync ?: 0L

    /** Per-trip cache keys (the legacy trip keeps the old key names). */
    fun tripKey(base: String): String = activeTripId.let { if (it == Trips.LEGACY_ID) base else "${base}_t$it" }

    /** Google Maps key typed into Settings (overrides the key baked in at build time). */
    var mapsKey: String
        get() = sp.getString("mapsKey", "") ?: ""
        set(v) = sp.edit().putString("mapsKey", v.trim()).apply()

    /** What you drive on your own, e.g. "2019 Toyota RAV4". Used to show a picture on Drive rows. */
    var myVehicle: String
        get() = sp.getString("myVehicle", "") ?: ""
        set(v) = sp.edit().putString("myVehicle", v.trim()).apply()

    /** Drive routes use live traffic (Google's higher-priced tier, smaller free allowance). Off = plain travel time. */
    var liveTraffic: Boolean
        get() = sp.getBoolean("liveTraffic", true)
        set(v) = sp.edit().putBoolean("liveTraffic", v).apply()

    // ---- lounge access ----
    var loungePrograms: Set<LoungeProgram>
        get() = LoungeProgram.decode(sp.getString("loungePrograms", "") ?: "")
        set(v) = sp.edit().putString("loungePrograms", LoungeProgram.encode(v)).apply()
    var loungeNotes: String
        get() = sp.getString("loungeNotes", "") ?: ""
        set(v) = sp.edit().putString("loungeNotes", v.trim()).apply()

    // ---- flight provider ----
    var flightProvider: String   // "aerodatabox" | "aviationstack"
        get() = sp.getString("flightProvider", "aerodatabox") ?: "aerodatabox"
        set(v) = sp.edit().putString("flightProvider", v).apply()

    var flightKey: String
        get() = sp.getString("flightKey", "") ?: ""
        set(v) = sp.edit().putString("flightKey", v.trim()).apply()

    var flightMonthlyCap: Int
        get() = sp.getInt("flightCap", if (flightProvider == "aviationstack") 90 else 150)
        set(v) = sp.edit().putInt("flightCap", v).apply()

    val flightCallsThisMonth: Int
        get() = if (sp.getString("flightCallsMonth", "") == YearMonth.now().toString()) sp.getInt("flightCalls", 0) else 0

    fun countFlightCall() {
        val m = YearMonth.now().toString()
        val n = if (sp.getString("flightCallsMonth", "") == m) sp.getInt("flightCalls", 0) else 0
        sp.edit().putString("flightCallsMonth", m).putInt("flightCalls", n + 1).apply()
    }

    // ---- alert settings ----
    var alertsEnabled: Boolean
        get() = sp.getBoolean("alertsEnabled", true)
        set(v) = sp.edit().putBoolean("alertsEnabled", v).apply()
    var leaveBufferMin: Int
        get() = sp.getInt("leaveBuffer", 10)
        set(v) = sp.edit().putInt("leaveBuffer", v).apply()
    var airportBufferMin: Int
        get() = sp.getInt("airportBuffer", 120)
        set(v) = sp.edit().putInt("airportBuffer", v).apply()
    var alertLeadMin: Int
        get() = sp.getInt("alertLead", 20)
        set(v) = sp.edit().putInt("alertLead", v).apply()
    var defaultMode: String
        get() = sp.getString("defaultMode", "DRIVE") ?: "DRIVE"
        set(v) = sp.edit().putString("defaultMode", v).apply()
    var useMyLocation: Boolean
        get() = sp.getBoolean("useMyLocation", true)
        set(v) = sp.edit().putBoolean("useMyLocation", v).apply()

    /** True once the first-run setup has been finished or skipped. */
    var setupDone: Boolean
        get() = sp.getBoolean("setupDone", false)
        set(v) = sp.edit().putBoolean("setupDone", v).apply()

    /** Someone who already had trips or a key before the setup existed shouldn't be walked through it. */
    fun isFreshInstall(): Boolean = !sp.contains("setupDone") && trips().none { it.sheetUrl.isNotBlank() } &&
        mapsKey.isBlank() && flightKey.isBlank()

    // ---- per-item state ----
    fun savedMode(id: String): String? = sp.getString("mode_$id", null)
    fun setMode(id: String, mode: String) = sp.edit().putString("mode_$id", mode).apply()

    fun getStr(key: String): String? = sp.getString(key, null)
    fun putStr(key: String, v: String) = sp.edit().putString(key, v).apply()
    fun getLong(key: String): Long = sp.getLong(key, 0L)
    fun putLong(key: String, v: Long) = sp.edit().putLong(key, v).apply()
    fun getInt(key: String, def: Int = -1): Int = sp.getInt(key, def)
    fun putInt(key: String, v: Int) = sp.edit().putInt(key, v).apply()
    fun has(key: String) = sp.contains(key)

    // ---- backup / restore ----
    fun exportAll(): Map<String, Any?> = sp.all

    /** Replaces everything the backup covers with the values in [m]; caches and other keys are untouched. */
    fun importAll(m: Map<String, Any>) {
        val e = sp.edit()
        sp.all.keys.filter { Backup.wanted(it) }.forEach { e.remove(it) }
        for ((k, v) in m) when (v) {
            is String -> e.putString(k, v)
            is Int -> e.putInt(k, v)
            is Long -> e.putLong(k, v)
            is Boolean -> e.putBoolean(k, v)
        }
        e.commit()
    }
}
