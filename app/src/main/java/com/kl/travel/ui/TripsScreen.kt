package com.kl.travel.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.kl.travel.data.Trip
import com.kl.travel.data.TripSpan
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

private val SPAN_FMT = DateTimeFormatter.ofPattern("MMM d, yyyy", Locale.US)

/** "Feb 21, 2027 to Mar 21, 2027 · 113 events", or a hint when the trip has not synced yet. */
internal fun spanText(s: TripSpan?, hasLink: Boolean): String {
    if (s == null || s.n == 0 || s.firstDate == null || s.lastDate == null)
        return if (hasLink) "Not synced yet" else "No sheet link yet"
    val a = LocalDate.parse(s.firstDate).format(SPAN_FMT); val b = LocalDate.parse(s.lastDate).format(SPAN_FMT)
    return (if (a == b) a else "$a to $b") + " · ${s.n} event" + if (s.n == 1) "" else "s"
}

/** Pick which trip you are on, add a new one, rename it, change its sheet, or delete it. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TripsScreen(vm: TravelViewModel) {
    val ctx = LocalContext.current
    val canClose = vm.activeTrip != null
    if (canClose) BackHandler { vm.showTrips = false }
    LaunchedEffect(Unit) { vm.refreshTrips() }

    var adding by rememberSaveable { mutableStateOf(vm.trips.isEmpty()) }
    var editId by rememberSaveable { mutableStateOf<Long?>(null) }
    var deleteId by rememberSaveable { mutableStateOf<Long?>(null) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Your trips") },
                navigationIcon = { if (canClose) IconButton(onClick = { vm.showTrips = false }) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") } },
            )
        },
        floatingActionButton = {
            ExtendedFloatingActionButton(onClick = { vm.tripError = null; adding = true }, icon = { Icon(Icons.Filled.Add, null) }, text = { Text("Add a trip") })
        },
    ) { pad ->
        if (vm.trips.isEmpty()) {
            Column(Modifier.padding(pad).fillMaxSize().padding(32.dp), verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally) {
                Text("No trips yet", style = MaterialTheme.typography.titleLarge)
                Spacer(Modifier.height(8.dp))
                Text("Add a trip with its Google Sheet link. You can keep as many trips as you like and switch between them here.", style = MaterialTheme.typography.bodyMedium)
                Spacer(Modifier.height(12.dp))
                OutlinedButton(onClick = { ctx.copyTemplateToSheets() }) { Text("Get a blank sheet (template)") }
            }
        } else {
            LazyColumn(Modifier.padding(pad), contentPadding = PaddingValues(16.dp, 16.dp, 16.dp, 96.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                items(vm.trips, key = { it.id }) { t ->
                    TripCard(t, vm.spans[t.id], active = t.id == vm.activeTrip?.id,
                        onOpen = { vm.selectTrip(t.id) }, onEdit = { vm.tripError = null; editId = t.id }, onDelete = { deleteId = t.id })
                }
                item { Text("Tap a trip to open it. Each trip keeps its own timeline, lodging and expenses.", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
            }
        }
    }

    if (adding) TripDialog(title = "Add a trip", initial = null, error = vm.tripError, onDismiss = { adding = false; vm.tripError = null },
        onSave = { name, link -> if (vm.addTrip(name, link)) adding = false })

    editId?.let { id ->
        vm.trips.firstOrNull { it.id == id }?.let { t ->
            TripDialog(title = "Edit trip", initial = t, error = vm.tripError, onDismiss = { editId = null; vm.tripError = null },
                onSave = { name, link -> vm.renameTrip(id, name); if (vm.setTripLink(id, link)) editId = null })
        }
    }

    deleteId?.let { id ->
        val t = vm.trips.firstOrNull { it.id == id }
        if (t != null) AlertDialog(
            onDismissRequest = { deleteId = null },
            title = { Text("Delete ${t.name}?") },
            text = { Text("This removes the trip's saved timeline, lodging and expenses from this phone. Your Google Sheet is not touched.") },
            confirmButton = { TextButton(onClick = { vm.deleteTrip(id); deleteId = null }) { Text("Delete") } },
            dismissButton = { TextButton(onClick = { deleteId = null }) { Text("Cancel") } },
        )
    }
}

@Composable
private fun TripCard(t: Trip, span: TripSpan?, active: Boolean, onOpen: () -> Unit, onEdit: () -> Unit, onDelete: () -> Unit) {
    val colors = if (active) CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer) else CardDefaults.cardColors()
    Card(Modifier.fillMaxWidth().clickable(onClick = onOpen), colors = colors) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(t.name, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
                if (active) AssistChip(onClick = {}, label = { Text("Current") })
            }
            Text(spanText(span, t.sheetUrl.isNotBlank()), style = MaterialTheme.typography.bodyMedium)
            val synced = if (t.lastSync > 0) "Synced " + LocalDateTime.ofInstant(Instant.ofEpochMilli(t.lastSync), ZoneId.systemDefault())
                .format(DateTimeFormatter.ofPattern("MMM d, h:mm a", Locale.US)) else null
            synced?.let { Text(it, style = MaterialTheme.typography.labelSmall) }
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                Button(onClick = onOpen) { Text(if (active) "Open" else "Switch to this trip") }
                TextButton(onClick = onEdit) { Text("Edit") }
                TextButton(onClick = onDelete) { Text("Delete") }
            }
        }
    }
}

@Composable
private fun TripDialog(title: String, initial: Trip?, error: String?, onDismiss: () -> Unit, onSave: (String, String) -> Unit) {
    var name by rememberSaveable(initial?.id) { mutableStateOf(initial?.name ?: "") }
    var link by rememberSaveable(initial?.id) { mutableStateOf(initial?.sheetUrl ?: "") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(name, { name = it }, label = { Text("Trip name (e.g. Philippines 2027)") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(link, { link = it }, label = { Text("Google Sheet link") }, modifier = Modifier.fillMaxWidth(),
                    supportingText = { Text("Shared as \"Anyone with the link: Viewer\". Leave empty for now if you don't have one.") })
                error?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall) }
            }
        },
        confirmButton = { Button(onClick = { onSave(name, link) }) { Text("Save") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}
