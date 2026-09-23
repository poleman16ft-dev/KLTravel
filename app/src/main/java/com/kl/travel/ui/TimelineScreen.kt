package com.kl.travel.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.listSaver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.kl.travel.data.Layover
import com.kl.travel.data.LayoverLevel
import com.kl.travel.data.Layovers
import com.kl.travel.data.TripItem
import com.kl.travel.work.FlightUpdater
import kotlinx.coroutines.delay
import java.time.LocalDate
import java.time.LocalDateTime

internal sealed interface Row {
    data class Header(val date: LocalDate) : Row
    data class Entry(val item: TripItem) : Row
    data class Connection(val layover: Layover) : Row
}

/**
 * [collapsed] days still get their Header row (so the day is visible and can be expanded again),
 * but their Entry/Connection rows are left out of the list.
 */
internal fun buildRows(items: List<TripItem>, layovers: List<Layover> = emptyList(), collapsed: Set<LocalDate> = emptySet()): List<Row> {
    val out = mutableListOf<Row>()
    var last: LocalDate? = null
    val byNextFlight = layovers.associateBy { it.to.id }
    for (i in items) {
        if (i.date != last) { out += Row.Header(i.date); last = i.date }
        if (i.date in collapsed) continue
        byNextFlight[i.id]?.takeIf { l -> items.any { it.id == l.from.id } }?.let { out += Row.Connection(it) }
        out += Row.Entry(i)
    }
    return out
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TimelineScreen(vm: TravelViewModel, pad: PaddingValues, onOpenSettings: () -> Unit) {
    val trip by vm.items.collectAsState()
    val now by produceState(LocalDateTime.now()) { while (true) { delay(30_000); value = LocalDateTime.now() } }
    val today = now.toLocalDate()

    Column(Modifier.padding(pad)) {
        TopAppBar(
            title = {
                Row(Modifier.clickable { vm.openTrips() }, verticalAlignment = Alignment.CenterVertically) {
                    Text(vm.activeTrip?.name ?: "KL Travel", maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f, fill = false))
                    Icon(Icons.Filled.ArrowDropDown, "Switch trip")
                }
            },
            actions = {
                if (vm.syncing) CircularProgressIndicator(Modifier.size(24.dp).padding(end = 4.dp), strokeWidth = 2.dp)
                else IconButton(onClick = { vm.sync() }) { Icon(Icons.Filled.Sync, "Sync sheet") }
            },
        )
        if (trip.isEmpty()) {
            Column(Modifier.fillMaxSize().padding(32.dp), verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally) {
                Text("No trip loaded yet", style = MaterialTheme.typography.titleLarge)
                Spacer(Modifier.height(8.dp))
                Text("Paste your Google Sheets link in Settings, then tap sync.", style = MaterialTheme.typography.bodyMedium)
                Spacer(Modifier.height(16.dp))
                Button(onClick = onOpenSettings) { Text("Open Settings") }
            }
            return@Column
        }

        val minuteKey = now.hour * 60 + now.minute
        val layovers = remember(trip, minuteKey) { Layovers.find(trip) { vm.flightCache(it) } }
        // Past days start collapsed into a "N items" dropdown; a date the user taps open stays open until they tap it shut again.
        val expandedPastSaver = remember { listSaver<Set<String>, String>(save = { it.toList() }, restore = { it.toSet() }) }
        var expandedPast by rememberSaveable(stateSaver = expandedPastSaver) { mutableStateOf(emptySet()) }
        val collapsedDates = remember(trip, today, expandedPast) {
            trip.map { it.date }.filter { it.isBefore(today) && it.toString() !in expandedPast }.toSet()
        }
        val rows = remember(trip, layovers, collapsedDates) { buildRows(trip, layovers, collapsedDates) }
        val next = trip.firstOrNull { it.sortKey >= now }
        val offset = if (next != null) 1 else 0
        val listState = rememberLazyListState()
        var scrolled by rememberSaveable { mutableStateOf(false) }
        LaunchedEffect(rows.size) {
            if (!scrolled && rows.isNotEmpty()) {
                val idx = rows.indexOfFirst { it is Row.Header && !it.date.isBefore(today) }.coerceAtLeast(0)
                listState.scrollToItem(idx + offset)
                scrolled = true
            }
        }

        LazyColumn(state = listState, contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp)) {
            if (next != null) item { NextUpCard(vm, next, now) { vm.open(next.id) }; Spacer(Modifier.height(8.dp)) }
            itemsIndexed(rows) { idx, row ->
                when (row) {
                    is Row.Header -> {
                        // A visible break between one day and the next, not just extra padding.
                        if (idx != 0) HorizontalDivider(Modifier.padding(top = 12.dp), thickness = 2.dp, color = MaterialTheme.colorScheme.outlineVariant)
                        val isPast = row.date.isBefore(today)
                        val dateKey = row.date.toString()
                        val expanded = !isPast || dateKey in expandedPast
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .padding(top = if (idx == 0) 8.dp else 16.dp, bottom = 4.dp)
                                .let { if (isPast) it.clickable {
                                    expandedPast = if (expanded) expandedPast - dateKey else expandedPast + dateKey
                                } else it },
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Column(Modifier.weight(1f)) {
                                Text(
                                    dayLabel(row.date, today), style = MaterialTheme.typography.titleSmall,
                                    color = if (isPast) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.primary,
                                    fontWeight = FontWeight.Bold,
                                )
                                if (!isPast) vm.weather[row.date]?.let { w ->
                                    Text("${w.text} · ${w.short()}" + if (w.source == "forecast") "" else " · ${sourceLabel(w)}",
                                        style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                            }
                            if (isPast) {
                                val count = trip.count { it.date == row.date }
                                Text(
                                    if (expanded) "Hide" else "$count item" + if (count != 1) "s" else "",
                                    style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                                Icon(
                                    if (expanded) Icons.Filled.ExpandLess else Icons.Filled.ExpandMore,
                                    if (expanded) "Hide this past day" else "Show this past day",
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                    }
                    is Row.Connection -> LayoverRow(row.layover) { vm.open(row.layover.to.id) }
                    is Row.Entry -> EntryRow(vm, row.item, past = row.item.sortKey < now) { vm.open(row.item.id) }
                }
            }
            item { Spacer(Modifier.height(24.dp)) }
        }
    }
}

@Composable
private fun NextUpCard(vm: TravelViewModel, item: TripItem, now: LocalDateTime, onClick: () -> Unit) {
    Card(
        onClick = onClick, modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text("NEXT UP", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onPrimaryContainer)
            Text(item.title, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onPrimaryContainer)
            val whenText = listOfNotNull(countdown(now, item.start), item.start?.format(TIME_FMT), item.date.format(DAY_FMT)).joinToString(" · ")
            Text(whenText, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onPrimaryContainer)
            if (item.location.isNotBlank()) Text(item.location, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onPrimaryContainer)
            val leave = vm.prefs.getStr("leave_${item.id}")?.let { runCatching { LocalDateTime.parse(it) }.getOrNull() }
            val dur = vm.prefs.getInt("dur_${item.id}")
            if (leave != null && leave.isAfter(now.minusMinutes(30))) {
                Text("Leave by ${leave.format(TIME_FMT)}" + if (dur > 0) " · $dur min trip" else "",
                    style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.onPrimaryContainer)
            }
            if (item.isFlight) FlightUpdater.cached(vm.prefs, item.id)?.lineSequence()?.firstOrNull()?.takeIf { it.isNotBlank() }?.let {
                Text("Status: $it", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onPrimaryContainer)
            }
        }
    }
}

@Composable
internal fun EntryRow(vm: TravelViewModel, item: TripItem, past: Boolean, extra: String = "", onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().alpha(if (past) 0.55f else 1f).clickable(onClick = onClick).padding(vertical = 8.dp),
        verticalAlignment = Alignment.Top,
    ) {
        Text(item.start?.format(TIME_FMT) ?: "All day", style = MaterialTheme.typography.labelMedium,
            modifier = Modifier.width(68.dp).padding(top = 2.dp))
        Icon(iconFor(item), null, Modifier.size(20.dp).padding(top = 2.dp), tint = MaterialTheme.colorScheme.primary)
        Spacer(Modifier.width(10.dp))
        Column(Modifier.weight(1f)) {
            Text(item.title, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Medium, maxLines = 2, overflow = TextOverflow.Ellipsis)
            val sub = listOf(item.location, item.transport).filter { it.isNotBlank() }.joinToString(" · ")
            if (sub.isNotBlank()) Text(sub, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
            if (extra.isNotBlank()) Text(extra, style = MaterialTheme.typography.bodySmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
            if (item.isFlight) FlightUpdater.cached(vm.prefs, item.id)?.lineSequence()?.firstOrNull()?.takeIf { it.isNotBlank() }?.let {
                Text(it, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.tertiary)
            }
        }
        item.cost?.takeIf { it > 0 }?.let {
            Text(money(it), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
    HorizontalDivider(color = MaterialTheme.colorScheme.surfaceVariant)
}


/** "Layover in TPE: 2h 15m" between two connecting flights. Tap it to open the next flight (its lounge list starts at this airport). */
@Composable
internal fun LayoverRow(l: Layover, hint: String = "Tap for airport lounges", onClick: () -> Unit) {
    val cs = MaterialTheme.colorScheme
    val (bg, fg) = when (l.level) {
        LayoverLevel.MISSED, LayoverLevel.SHORT -> cs.errorContainer to cs.onErrorContainer
        LayoverLevel.LONG, LayoverLevel.UNKNOWN -> cs.tertiaryContainer to cs.onTertiaryContainer
        LayoverLevel.STAY, LayoverLevel.OK -> cs.secondaryContainer to cs.onSecondaryContainer
    }
    val note = when (l.level) {
        LayoverLevel.MISSED -> "Delays may make you miss this connection. Check with the airline."
        LayoverLevel.SHORT -> "Short connection: head straight to the next gate."
        LayoverLevel.LONG -> "Long layover: a lounge could make the wait easier."
        LayoverLevel.STAY -> if (l.hotel) "Overnight stopover with a hotel stay in between." else "Overnight stopover."
        LayoverLevel.UNKNOWN -> "Add the arrival time for ${l.from.flight.ifBlank { "the first flight" }} (End Time, or \"arrives 5:25 AM\" in Notes) to see the layover time."
        LayoverLevel.OK -> ""
    }
    Card(onClick = onClick, modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp), colors = CardDefaults.cardColors(containerColor = bg)) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            val head = when (l.level) {
                LayoverLevel.MISSED -> "Connection at ${l.airport} at risk"
                LayoverLevel.UNKNOWN -> "Connection at ${l.airport}: arrival time missing"
                LayoverLevel.STAY -> "Stopover in ${l.airport}: ${l.durationText()}"
                else -> "Layover in ${l.airport}: ${l.durationText()}"
            }
            Text(head, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold, color = fg)
            if (l.arrives != null) Text("Lands ${l.arrives.format(TIME_FMT)} · ${l.to.flight.ifBlank { "next flight" }} leaves ${l.departs.format(TIME_FMT)}" +
                if (l.live) " · live times" else "", style = MaterialTheme.typography.bodySmall, color = fg)
            else Text("${l.to.flight.ifBlank { "Next flight" }} leaves ${l.departs.format(TIME_FMT)}", style = MaterialTheme.typography.bodySmall, color = fg)
            if (note.isNotBlank()) Text(note, style = MaterialTheme.typography.bodySmall, color = fg)
            if (hint.isNotBlank()) Text(hint, style = MaterialTheme.typography.labelSmall, color = fg)
        }
    }
}
