package com.kl.travel.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.listSaver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.kl.travel.data.TripItem
import kotlinx.coroutines.delay
import java.time.LocalDateTime

/** A filtered, day-grouped list of the trip: the Transportation and Activities tabs. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CategoryScreen(
    vm: TravelViewModel, pad: PaddingValues, title: String, emptyText: String,
    filter: (TripItem) -> Boolean, extra: (TripItem) -> String = { "" },
) {
    val all by vm.items.collectAsState()
    val list = remember(all) { all.filter(filter) }
    val now by produceState(LocalDateTime.now()) { while (true) { delay(30_000); value = LocalDateTime.now() } }
    val today = now.toLocalDate()

    Column(Modifier.padding(pad)) {
        TopAppBar(title = { Text(title) })
        if (list.isEmpty()) {
            Column(Modifier.fillMaxSize().padding(32.dp), verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally) {
                Text(emptyText, style = MaterialTheme.typography.bodyMedium)
            }
            return@Column
        }
        Text("${list.size} on this trip", style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(horizontal = 16.dp))
        val minuteKey = now.hour * 60 + now.minute
        val layovers = remember(list, minuteKey) { com.kl.travel.data.Layovers.find(list) { vm.flightCache(it) } }
        // Past days start collapsed into a "N items" dropdown; a date the user taps open stays open until they tap it shut again.
        val expandedPastSaver = remember { listSaver<Set<String>, String>(save = { it.toList() }, restore = { it.toSet() }) }
        var expandedPast by rememberSaveable(title, stateSaver = expandedPastSaver) { mutableStateOf(emptySet()) }
        val collapsedDates = remember(list, today, expandedPast) {
            list.map { it.date }.filter { it.isBefore(today) && it.toString() !in expandedPast }.toSet()
        }
        val rows = remember(list, layovers, collapsedDates) { buildRows(list, layovers, collapsedDates) }
        val listState = rememberLazyListState()
        var scrolled by rememberSaveable(title) { mutableStateOf(false) }
        LaunchedEffect(rows.size) {
            if (!scrolled && rows.isNotEmpty()) {
                listState.scrollToItem(rows.indexOfFirst { it is Row.Header && !it.date.isBefore(today) }.coerceAtLeast(0))
                scrolled = true
            }
        }
        LazyColumn(state = listState, contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp)) {
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
                            Text(
                                dayLabel(row.date, today), style = MaterialTheme.typography.titleSmall,
                                color = if (isPast) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.primary,
                                fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f),
                            )
                            if (isPast) {
                                val count = list.count { it.date == row.date }
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
                    is Row.Entry -> EntryRow(vm, row.item, past = row.item.sortKey < now, extra = extra(row.item)) { vm.open(row.item.id) }
                }
            }
            item { Spacer(Modifier.height(24.dp)) }
        }
    }
}

/** "BR49 · Seat 6K · Boeing 787-9" for flights, otherwise the confirmation code if there is one. */
fun transportExtra(i: TripItem): String =
    if (i.isFlight) listOf(i.flight, i.seat.takeIf { it.isNotBlank() }?.let { "Seat " + com.kl.travel.data.Seats.summary(it) }, i.aircraft.takeIf { it.isNotBlank() }, i.cabin.takeIf { it.isNotBlank() })
        .filterNotNull().filter { it.isNotBlank() }.joinToString(" · ")
    else i.confirmation.takeIf { it.isNotBlank() }?.let { "Booking $it" }.orEmpty()
