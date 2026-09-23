package com.kl.travel.net

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import androidx.core.content.ContextCompat
import com.google.android.gms.location.LocationServices
import com.kl.travel.data.ItemKind
import com.kl.travel.data.TripItem
import kotlin.coroutines.resume
import kotlinx.coroutines.suspendCancellableCoroutine
import java.time.LocalDateTime

data class Origin(val place: Place, val label: String)

object OriginResolver {
    fun hasLocationPermission(ctx: Context) =
        ContextCompat.checkSelfPermission(ctx, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED ||
            ContextCompat.checkSelfPermission(ctx, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED

    private suspend fun lastLocation(ctx: Context): Place.Coord? {
        if (!hasLocationPermission(ctx)) return null
        return suspendCancellableCoroutine { cont ->
            try {
                LocationServices.getFusedLocationProviderClient(ctx).lastLocation
                    .addOnSuccessListener { l -> cont.resume(l?.let { Place.Coord(it.latitude, it.longitude) }) }
                    .addOnFailureListener { cont.resume(null) }
            } catch (_: SecurityException) { cont.resume(null) }
        }
    }

    /** Previous stop (skipping flights, whose address is the departure airport), else the hotel you are checked into. */
    fun previousStop(target: TripItem, all: List<TripItem>): Origin? {
        val before = all.filter { it.id != target.id && it.sortKey < target.sortKey && it.address.isNotBlank() }
        val prevDay = before.lastOrNull { it.date == target.date && !it.isFlight && it.kind != ItemKind.HOTEL_OUT }
        if (prevDay != null) return Origin(Place.Address(prevDay.address), prevDay.location.ifBlank { prevDay.title })
        val hotel = before.lastOrNull { it.kind == ItemKind.HOTEL_IN }
        val checkedOut = hotel != null && before.any { it.kind == ItemKind.HOTEL_OUT && it.id.removeSuffix("_out") == hotel.id.removeSuffix("_in") }
        if (hotel != null && !checkedOut) return Origin(Place.Address(hotel.address), hotel.location)
        return null
    }

    suspend fun resolve(ctx: Context, target: TripItem, all: List<TripItem>, preferMyLocation: Boolean): Origin? {
        if (preferMyLocation) lastLocation(ctx)?.let { return Origin(it, "My location") }
        return previousStop(target, all)
            ?: lastLocation(ctx)?.let { Origin(it, "My location") }
    }
}

/** Shared leave-by math used by the detail screen and the background worker. */
object LeaveBy {
    fun compute(item: TripItem, durationSec: Long, leaveBufferMin: Int, airportBufferMin: Int): LocalDateTime? {
        val start = item.start ?: return null
        val buffer = if (item.isFlight) airportBufferMin else leaveBufferMin
        return start.minusSeconds(durationSec).minusMinutes(buffer.toLong())
    }
}
