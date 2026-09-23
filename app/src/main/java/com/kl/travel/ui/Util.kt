package com.kl.travel.ui

import android.content.ActivityNotFoundException
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.ui.graphics.vector.ImageVector
import com.kl.travel.data.ItemKind
import com.kl.travel.data.TripItem
import java.net.URLEncoder
import java.time.Duration
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale

val DAY_FMT: DateTimeFormatter = DateTimeFormatter.ofPattern("EEE, MMM d", Locale.US)
val DAY_LONG_FMT: DateTimeFormatter = DateTimeFormatter.ofPattern("EEEE, MMMM d, yyyy", Locale.US)
val TIME_FMT: DateTimeFormatter = DateTimeFormatter.ofPattern("h:mm a", Locale.US)

fun money(v: Double) = String.format(Locale.US, "$%,.2f", v)

fun fmtMinutes(min: Long): String {
    val m = kotlin.math.abs(min)
    return when {
        m < 60 -> "${m} min"
        m < 60 * 24 -> "${m / 60}h ${"%02d".format(m % 60)}m"
        else -> "${m / (60 * 24)} d ${(m % (60 * 24)) / 60}h"
    }
}

fun countdown(now: LocalDateTime, start: LocalDateTime?): String? {
    start ?: return null
    val mins = Duration.between(now, start).toMinutes()
    return if (mins >= 0) "in ${fmtMinutes(mins)}" else if (mins > -180) "${fmtMinutes(mins)} ago" else null
}

fun dayLabel(d: LocalDate, today: LocalDate): String = when (d) {
    today -> "Today · ${d.format(DAY_FMT)}"
    today.plusDays(1) -> "Tomorrow · ${d.format(DAY_FMT)}"
    else -> d.format(DAY_LONG_FMT)
}

fun iconFor(item: TripItem): ImageVector = when {
    item.kind == ItemKind.HOTEL_IN || item.kind == ItemKind.HOTEL_OUT -> Icons.Filled.Hotel
    item.isFlight -> Icons.Filled.Flight
    else -> when (item.type.lowercase(Locale.US)) {
        "hotel", "lodging" -> Icons.Filled.Hotel
        "meal", "food" -> Icons.Filled.Restaurant
        "wedding" -> Icons.Filled.Favorite
        "boat", "ferry", "cruise" -> Icons.Filled.DirectionsBoat
        "train", "metro", "subway", "rail" -> Icons.Filled.Train
        "bus", "shuttle", "van", "jeepney" -> Icons.Filled.DirectionsBus
        "transport", "travel", "drive", "driving", "car", "taxi", "ride", "rideshare", "grab", "transfer" -> Icons.Filled.DirectionsCar
        "meeting" -> Icons.Filled.Groups
        "activity" -> Icons.Filled.Attractions
        else -> Icons.Filled.Event
    }
}

// ------------------------------------------------------------------ intents

private fun Context.launch(intent: Intent, fallback: Intent? = null) {
    try { startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
    catch (_: ActivityNotFoundException) {
        try { fallback?.let { startActivity(it.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) } }
        catch (_: ActivityNotFoundException) { Toast.makeText(this, "No app found for that.", Toast.LENGTH_SHORT).show() }
    }
}

private fun enc(s: String) = URLEncoder.encode(s, "UTF-8")

fun Context.openInMaps(address: String) =
    launch(Intent(Intent.ACTION_VIEW, Uri.parse("geo:0,0?q=${enc(address)}")),
        Intent(Intent.ACTION_VIEW, Uri.parse("https://www.google.com/maps/search/?api=1&query=${enc(address)}")))

/** mode: d = driving, w = walking */
fun Context.navigateTo(address: String, mode: String = "d") =
    launch(Intent(Intent.ACTION_VIEW, Uri.parse("google.navigation:q=${enc(address)}&mode=$mode")),
        Intent(Intent.ACTION_VIEW, Uri.parse("https://www.google.com/maps/dir/?api=1&destination=${enc(address)}")))

fun Context.transitTo(address: String) =
    launch(Intent(Intent.ACTION_VIEW, Uri.parse("https://www.google.com/maps/dir/?api=1&destination=${enc(address)}&travelmode=transit")))

fun Context.openUber(lat: Double?, lng: Double?, name: String, address: String) {
    val dest = if (lat != null && lng != null)
        "dropoff[latitude]=$lat&dropoff[longitude]=$lng&dropoff[nickname]=${enc(name)}"
    else "dropoff[formatted_address]=${enc(address)}"
    launch(Intent(Intent.ACTION_VIEW, Uri.parse("uber://?action=setPickup&pickup=my_location&$dest")),
        Intent(Intent.ACTION_VIEW, Uri.parse("https://m.uber.com/ul/?action=setPickup&pickup=my_location&$dest")))
}

fun Context.openLyft(lat: Double?, lng: Double?) {
    if (lat == null || lng == null) { toast("Load the route first so I know where to send Lyft."); return }
    launch(Intent(Intent.ACTION_VIEW, Uri.parse("lyft://ridetype?id=lyft&destination[latitude]=$lat&destination[longitude]=$lng")),
        Intent(Intent.ACTION_VIEW, Uri.parse("https://lyft.com/ride?id=lyft&destination[latitude]=$lat&destination[longitude]=$lng")))
}

/** Grab has no public deep link for a destination, so open the app and put the address on the clipboard to paste. */
fun Context.openGrab(address: String) {
    copyText("Destination", address, toast = false)
    val i = packageManager.getLaunchIntentForPackage("com.grabtaxi.passenger")
    if (i != null) { toast("Address copied. Paste it into Grab."); launch(i) }
    else launch(Intent(Intent.ACTION_VIEW, Uri.parse("https://play.google.com/store/apps/details?id=com.grabtaxi.passenger")))
}

fun Context.dial(phone: String) = launch(Intent(Intent.ACTION_DIAL, Uri.parse("tel:${phone.filter { it.isDigit() || it == '+' }}")))

fun Context.toast(msg: String) = Toast.makeText(this, msg, Toast.LENGTH_SHORT).show()

fun Context.copyText(label: String, text: String, toast: Boolean = true) {
    (getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager).setPrimaryClip(ClipData.newPlainText(label, text))
    if (toast) toast("$label copied")
}
