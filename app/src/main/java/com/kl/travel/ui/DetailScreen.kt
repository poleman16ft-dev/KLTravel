package com.kl.travel.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.clickable
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.model.LatLngBounds
import com.google.maps.android.compose.*
import com.kl.travel.net.MapsKey
import com.kl.travel.data.ItemKind
import com.kl.travel.data.TripItem
import com.kl.travel.net.TravelMode
import java.time.Duration
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DetailScreen(vm: TravelViewModel, item: TripItem, onBack: () -> Unit) {
    BackHandler(onBack = onBack)
    LaunchedEffect(item.id) { vm.onDetailShown(item) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(item.title, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") } },
            )
        },
    ) { pad ->
        Column(
            Modifier.padding(pad).verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            if ((item.kind == ItemKind.HOTEL_IN || item.kind == ItemKind.HOTEL_OUT) && item.location.isNotBlank())
                HotelPhoto(item.location, item.address)
            InfoCard(item)
            if (item.isFlight) {
                val all by vm.items.collectAsState()
                val connections = remember(all, item.id) { com.kl.travel.data.Layovers.find(all) { vm.flightCache(it) } }
                connections.firstOrNull { it.to.id == item.id }?.let { l ->
                    LayoverRow(l, hint = "Connects from ${l.from.flight.ifBlank { "your previous flight" }}. Tap to open it.") { vm.open(l.from.id) }
                }
                connections.firstOrNull { it.from.id == item.id }?.let { l ->
                    LayoverRow(l, hint = "Connects to ${l.to.flight.ifBlank { "your next flight" }}. Tap to open it.") { vm.open(l.to.id) }
                }
            }
            if (item.isFlight || com.kl.travel.data.Categories.isTransport(item)) TransportPhotoCard(vm, item)
            if (item.isFlight && item.flight.isNotBlank()) FlightCard(vm, item)
            if (item.isFlight) LoungeCard(item, com.kl.travel.net.LoungeClient.airportCodes(item.location, item.title), item.address, vm.prefs)
            when {
                item.needsTravel -> TravelCard(vm, item)
                item.kind != ItemKind.HOTEL_OUT -> Text(
                    "No address on this row, so no route. Add one in the Address column of your sheet.",
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Spacer(Modifier.height(24.dp))
        }
    }
}

@Composable
private fun InfoCard(item: TripItem) {
    val ctx = LocalContext.current
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(iconFor(item), null, tint = MaterialTheme.colorScheme.primary)
                Spacer(Modifier.width(8.dp))
                Text(item.type.uppercase(Locale.US), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
            }
            Text(item.title, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            val time = when {
                item.start != null && item.end != null -> "${item.start.format(TIME_FMT)} – ${item.end.format(TIME_FMT)}"
                item.start != null -> item.start.format(TIME_FMT)
                else -> "All day"
            }
            Text("${item.date.format(DAY_LONG_FMT)} · $time", style = MaterialTheme.typography.bodyMedium)
            if (item.address.isNotBlank()) {
                Row(Modifier.clickable { ctx.openInMaps(item.address) }, verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Filled.Place, null, Modifier.size(18.dp), tint = MaterialTheme.colorScheme.secondary)
                    Spacer(Modifier.width(6.dp))
                    Text(item.address, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.secondary)
                }
            } else if (item.location.isNotBlank()) {
                Text(item.location, style = MaterialTheme.typography.bodyMedium)
            }
            if (item.confirmation.isNotBlank()) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Confirmation: ", style = MaterialTheme.typography.bodyMedium)
                    Text(item.confirmation, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    IconButton(onClick = { ctx.copyText("Confirmation #", item.confirmation) }) { Icon(Icons.Filled.ContentCopy, "Copy") }
                }
            }
            if (item.phone.isNotBlank()) {
                TextButton(onClick = { ctx.dial(item.phone) }) { Icon(Icons.Filled.Call, null); Spacer(Modifier.width(6.dp)); Text(item.phone) }
            }
            item.cost?.takeIf { it > 0 }?.let { Text("Planned cost: ${money(it)}", style = MaterialTheme.typography.bodyMedium) }
            if (item.transport.isNotBlank()) Text("Planned transport: ${item.transport}", style = MaterialTheme.typography.bodyMedium)
            if (item.notes.isNotBlank()) Text(item.notes, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun FlightCard(vm: TravelViewModel, item: TripItem) {
    val f = vm.flight
    val p = vm.prefs
    Card(Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text("Flight ${item.flight}", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            val ctx = LocalContext.current
            val plane = item.aircraft.ifBlank { com.kl.travel.work.FlightUpdater.plane(p, item.id).orEmpty() }
            val dim = MaterialTheme.colorScheme.onSurfaceVariant
            val seats = com.kl.travel.data.Seats.parse(item.seat)
            if (seats.size > 1) {
                Text("Seats (${seats.size})", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                seats.forEach { e ->
                    Text(if (e.who.isBlank()) e.seat else "${e.who}: ${e.seat}", style = MaterialTheme.typography.titleMedium)
                }
            } else if (seats.size == 1) {
                val e = seats.first()
                Text(if (e.who.isBlank()) "Seat: ${e.seat}" else "Seat (${e.who}): ${e.seat}", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            } else Text("Seat: not in your sheet yet (add it in the Seat column, e.g. Luis 3C / Sabrina 3D)", style = MaterialTheme.typography.bodySmall, color = dim)
            if (plane.isNotBlank()) Text("Plane: $plane", style = MaterialTheme.typography.bodyMedium)
            else Text("Plane: appears after the live flight lookup, or add it in the Aircraft column", style = MaterialTheme.typography.bodySmall, color = dim)
            val airline = com.kl.travel.net.Airlines.forFlight(item.flight)
            Button(onClick = {
                ctx.startActivity(android.content.Intent(android.content.Intent.ACTION_VIEW,
                    android.net.Uri.parse(com.kl.travel.net.Airlines.seatMapsUrl(item.flight, plane))))
            }) { Text("Seat map on SeatMaps") }
            OutlinedButton(onClick = {
                com.kl.travel.net.Airlines.firstReference(item.confirmation)?.let { ctx.copyText("Booking code", it, toast = false) }
                ctx.startActivity(android.content.Intent(android.content.Intent.ACTION_VIEW, android.net.Uri.parse(airline.url)))
            }) { Text("Change seat on ${airline.name}") }
            if (com.kl.travel.net.Airlines.firstReference(item.confirmation) != null)
                Text("Your booking code is copied when you tap. Paste it on their page with your last name.", style = MaterialTheme.typography.labelSmall, color = dim)
            val lines = f.text?.lines().orEmpty()
            if (lines.isNotEmpty() && lines[0].isNotBlank()) {
                Text(lines[0], style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.tertiary)
                lines.drop(1).filter { it.isNotBlank() }.forEach { Text(it, style = MaterialTheme.typography.bodyMedium) }
            } else {
                Text("No status yet. Live data usually appears within about 24 hours of departure.",
                    style = MaterialTheme.typography.bodyMedium)
            }
            f.error?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall) }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Button(onClick = { vm.refreshFlight(item) }, enabled = !f.loading) {
                    if (f.loading) CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp) else Text("Refresh")
                }
                Spacer(Modifier.width(12.dp))
                val at = if (f.checkedAt > 0) "checked " + LocalDateTime.ofInstant(Instant.ofEpochMilli(f.checkedAt), ZoneId.systemDefault()).format(TIME_FMT) + " · " else ""
                Text("$at${p.flightCallsThisMonth}/${p.flightMonthlyCap} lookups this month", style = MaterialTheme.typography.labelSmall)
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun TravelCard(vm: TravelViewModel, item: TripItem) {
    val ctx = LocalContext.current
    val rs = vm.routeState
    val mode = vm.mode
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text("Getting there", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)

            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                TravelMode.entries.forEach { m ->
                    FilterChip(
                        selected = mode == m, onClick = { vm.setMode(item, m) }, label = { Text(m.label) },
                        leadingIcon = { Icon(modeIcon(m), null, Modifier.size(18.dp)) },
                    )
                }
            }
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Start from:", Modifier.align(Alignment.CenterVertically), style = MaterialTheme.typography.labelMedium)
                FilterChip(selected = vm.useMyLocation, onClick = { vm.setUseMyLocation(item, true) }, label = { Text("My location") })
                FilterChip(selected = !vm.useMyLocation, onClick = { vm.setUseMyLocation(item, false) }, label = { Text("Previous stop / lodging") })
            }

            val userKey = MapsKey.userKey(ctx)
            val hasKey = MapsKey.effective(ctx) != null
            if (!hasKey) {
                Text("Maps key missing: add your Google Maps key in Settings.", color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
            }
            if (rs.loading) LinearProgressIndicator(Modifier.fillMaxWidth())
            rs.error?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall) }

            rs.route?.let { r ->
                val durMin = r.durationSec / 60
                val miles = r.distanceMeters / 1609.344
                Text("${fmtMinutes(durMin)} · ${"%.1f".format(Locale.US, miles)} mi (${"%.1f".format(Locale.US, r.distanceMeters / 1000.0)} km)",
                    style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                val sub = buildList {
                    rs.originLabel?.let { add("from $it") }
                    if (mode.api == "DRIVE" && vm.prefs.liveTraffic) add(if (r.trafficDelayMin >= 3) "+${r.trafficDelayMin} min traffic" else "traffic is light")
                }.joinToString(" · ")
                if (sub.isNotBlank()) Text(sub, style = MaterialTheme.typography.bodyMedium)
                rs.leaveBy?.let { lb ->
                    val mins = Duration.between(LocalDateTime.now(), lb).toMinutes()
                    val buf = if (item.isFlight) vm.prefs.airportBufferMin else vm.prefs.leaveBufferMin
                    val txt = if (mins <= 0) "Leave now (target was ${lb.format(TIME_FMT)})" else "Leave by ${lb.format(TIME_FMT)} · in ${fmtMinutes(mins)}"
                    Text(txt, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
                    Text("Includes a $buf min buffer.", style = MaterialTheme.typography.labelSmall)
                }
            }

            when (mode) {
                TravelMode.DRIVE -> Button(onClick = { ctx.navigateTo(item.address, "d") }) { Icon(Icons.Filled.Navigation, null); Spacer(Modifier.width(6.dp)); Text("Start navigation") }
                TravelMode.WALK -> Button(onClick = { ctx.navigateTo(item.address, "w") }) { Icon(Icons.Filled.DirectionsWalk, null); Spacer(Modifier.width(6.dp)); Text("Walking directions") }
                TravelMode.TRANSIT -> Button(onClick = { ctx.transitTo(item.address) }) { Icon(Icons.Filled.DirectionsTransit, null); Spacer(Modifier.width(6.dp)); Text("Transit directions") }
                TravelMode.RIDESHARE -> {
                    Text("Ride time is the driving estimate. Fares are only shown inside the ride apps.", style = MaterialTheme.typography.bodySmall)
                    val end = rs.route?.end
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(onClick = { ctx.openGrab(item.address) }) { Text("Grab") }
                        OutlinedButton(onClick = { ctx.openUber(end?.latitude, end?.longitude, item.location.ifBlank { item.title }, item.address) }) { Text("Uber") }
                        OutlinedButton(onClick = { ctx.openLyft(end?.latitude, end?.longitude) }) { Text("Lyft") }
                    }
                }
            }

            when {
                // Only once there is a route: a map with no line on it is a wasted (billed) load.
                userKey != null && MapsKey.isWellFormed(userKey) && rs.route != null ->
                    WebRouteMap(userKey, rs.route?.path.orEmpty(), rs.route?.start, rs.route?.end)
                userKey == null && MapsKey.buildKey() != null -> RouteMap(rs.route)
            }
        }
    }
}

private fun modeIcon(m: TravelMode) = when (m) {
    TravelMode.DRIVE -> Icons.Filled.DirectionsCar
    TravelMode.RIDESHARE -> Icons.Filled.LocalTaxi
    TravelMode.TRANSIT -> Icons.Filled.DirectionsTransit
    TravelMode.WALK -> Icons.Filled.DirectionsWalk
}

@Composable
private fun RouteMap(route: com.kl.travel.net.RouteResult?) {
    val camera = rememberCameraPositionState()
    var loaded by remember { mutableStateOf(false) }
    val lineColor = MaterialTheme.colorScheme.secondary

    GoogleMap(
        modifier = Modifier.fillMaxWidth().height(280.dp).clip(RoundedCornerShape(12.dp)),
        cameraPositionState = camera,
        onMapLoaded = { loaded = true },
        uiSettings = MapUiSettings(zoomControlsEnabled = false, mapToolbarEnabled = false),
    ) {
        if (route != null && route.path.isNotEmpty()) {
            Polyline(points = route.path, color = lineColor, width = 12f)
            route.start?.let { Marker(state = rememberMarkerState(position = it), title = "Start") }
            route.end?.let { Marker(state = rememberMarkerState(position = it), title = "Destination") }
        }
    }
    LaunchedEffect(route, loaded) {
        if (loaded && route != null && route.path.isNotEmpty()) {
            val b = LatLngBounds.builder()
            route.path.forEach { b.include(it) }
            runCatching { camera.animate(CameraUpdateFactory.newLatLngBounds(b.build(), 100)) }
        }
    }
}
