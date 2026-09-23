package com.kl.travel.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.kl.travel.data.HotelEntity
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.temporal.ChronoUnit

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HotelsScreen(vm: TravelViewModel, pad: PaddingValues) {
    val hotels by vm.hotels.collectAsState()
    Column(Modifier.padding(pad)) {
        TopAppBar(title = { Text("Lodging") })
        if (hotels.isEmpty()) {
            Text("No lodging yet. Fill the Lodging tab in your sheet and sync.", Modifier.padding(24.dp), style = MaterialTheme.typography.bodyMedium)
            return@Column
        }
        LazyColumn(contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            items(hotels) { HotelCard(it) }
        }
    }
}

@Composable
private fun HotelCard(h: HotelEntity) {
    val ctx = LocalContext.current
    val inDate = LocalDate.parse(h.checkInDate); val outDate = LocalDate.parse(h.checkOutDate)
    val inAt = inDate.atTime(LocalTime.parse(h.checkInTime ?: "15:00"))
    val outAt = outDate.atTime(LocalTime.parse(h.checkOutTime ?: "12:00"))
    val now = LocalDateTime.now()
    val nights = ChronoUnit.DAYS.between(inDate, outDate)
    val status = when {
        now.isAfter(outAt) -> "Past stay"
        now.isAfter(inAt) -> "Staying now · checkout ${outAt.format(TIME_FMT)}"
        else -> "Check-in in ${fmtMinutes(java.time.Duration.between(now, inAt).toMinutes())}"
    }
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            HotelPhoto(h.name, h.address)
            Text(h.name, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            Text(status, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.tertiary)
            Text("Check-in: ${inAt.format(DAY_FMT)}, ${inAt.format(TIME_FMT)}" + if (h.checkInTime == null) " (default)" else "")
            Text("Check-out: ${outAt.format(DAY_FMT)}, ${outAt.format(TIME_FMT)}" + if (h.checkOutTime == null) " (default)" else "")
            Text("$nights night" + if (nights == 1L) "" else "s", style = MaterialTheme.typography.bodySmall)
            if (h.confirmation.isNotBlank()) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Reservation #: ")
                    Text(h.confirmation, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)
                    IconButton(onClick = { ctx.copyText("Reservation #", h.confirmation) }) { Icon(Icons.Filled.ContentCopy, "Copy") }
                }
            } else Text("Reservation #: not in sheet yet", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            h.cost?.let { Text("Cost: ${money(it)}") }
            if (h.notes.isNotBlank()) Text(h.notes, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                if (h.address.isNotBlank()) OutlinedButton(onClick = { ctx.navigateTo(h.address, "d") }) { Icon(Icons.Filled.Navigation, null, Modifier.size(18.dp)); Spacer(Modifier.width(4.dp)); Text("Directions") }
                if (h.phone.isNotBlank()) OutlinedButton(onClick = { ctx.dial(h.phone) }) { Icon(Icons.Filled.Call, null, Modifier.size(18.dp)); Spacer(Modifier.width(4.dp)); Text("Call") }
            }
        }
    }
}
