package com.kl.travel.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.kl.travel.net.DayWeather
import java.time.LocalDate
import java.util.Locale

fun fmtAmount(v: Double): String = String.format(Locale.US, "%,.2f", v)

/** Converts an amount at the given rate; blank or invalid input gives null. */
fun convertAmount(text: String, rate: Double?): Double? {
    val amount = text.replace(",", "").trim().toDoubleOrNull() ?: return null
    return rate?.let { amount * it }
}

fun sourceLabel(w: DayWeather) = when (w.source) {
    "forecast" -> "forecast"
    "actual" -> "recorded"
    else -> "typical, from last year"
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun ToolsScreen(vm: TravelViewModel, pad: PaddingValues) {
    var vaultOpen by rememberSaveable { mutableStateOf(false) }
    if (vaultOpen) {
        VaultScreen(vm, pad, onClose = { vaultOpen = false; vm.lockVault() })
        return
    }

    Column(Modifier.padding(pad)) {
        TopAppBar(title = { Text("Tools") })
        Column(Modifier.verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            CurrencyCard(vm)
            WeatherCard(vm)
            Section("Documents") {
                Text("Keep passport, visa and ticket copies on the phone, behind your screen lock.", style = MaterialTheme.typography.bodyMedium)
                Button(onClick = { vaultOpen = true }) { Text("Open documents") }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun CurrencyCard(vm: TravelViewModel) {
    var amount by rememberSaveable { mutableStateOf("100") }
    var from by remember(vm.fxFrom) { mutableStateOf(vm.fxFrom) }
    var to by remember(vm.fxTo) { mutableStateOf(vm.fxTo) }
    val rate = vm.fxRate
    val out = convertAmount(amount, rate?.rate)

    Section("Currency converter") {
        OutlinedTextField(amount, { amount = it }, label = { Text("Amount") }, singleLine = true, modifier = Modifier.fillMaxWidth(),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal))
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedTextField(from, { from = it.uppercase().take(3); if (from.length == 3) vm.setFxPair(from, to) }, label = { Text("From") },
                singleLine = true, modifier = Modifier.weight(1f))
            IconButton(onClick = { vm.swapFx() }) { Icon(Icons.Filled.SwapHoriz, "Swap") }
            OutlinedTextField(to, { to = it.uppercase().take(3); if (to.length == 3) vm.setFxPair(from, to) }, label = { Text("To") },
                singleLine = true, modifier = Modifier.weight(1f))
        }
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf("PHP", "JPY", "TWD", "USD", "EUR").filter { it != vm.fxTo }.forEach { c ->
                AssistChip(onClick = { vm.setFxPair(vm.fxFrom, c) }, label = { Text("to $c") })
            }
        }
        when {
            vm.fxLoading && rate == null -> LinearProgressIndicator(Modifier.fillMaxWidth())
            out != null -> {
                Text("${fmtAmount(amount.replace(",", "").toDouble())} ${vm.fxFrom} = ${fmtAmount(out)} ${vm.fxTo}",
                    style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                Text("1 ${vm.fxFrom} = ${String.format(Locale.US, "%.4f", rate!!.rate)} ${vm.fxTo}" +
                    (if (rate.date.isNotBlank()) " · rates from ${rate.date}" else "") +
                    (if (rate.stale) " · saved rate, you're offline" else ""),
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            vm.fxError != null -> Text(vm.fxError!!, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyMedium)
        }
        Text("Market rate for planning. Your card or the cash counter will add fees.", style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun WeatherCard(vm: TravelViewModel) {
    val today = LocalDate.now()
    val days = vm.weather.values.filter { !it.date.isBefore(today) }.sortedBy { it.date }
    Section("Weather along the trip") {
        when {
            days.isEmpty() && vm.weatherLoading -> LinearProgressIndicator(Modifier.fillMaxWidth())
            days.isEmpty() -> Text(vm.weatherError ?: "Weather appears once your trip has dated events with a place name.", style = MaterialTheme.typography.bodyMedium)
            else -> days.forEach { w ->
                Row(Modifier.fillMaxWidth().padding(vertical = 2.dp)) {
                    Text(w.date.format(DAY_FMT), Modifier.width(96.dp), style = MaterialTheme.typography.labelMedium)
                    Column(Modifier.weight(1f)) {
                        Text("${w.place}: ${w.text}, ${w.short()}", style = MaterialTheme.typography.bodyMedium)
                        Text(sourceLabel(w), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
        }
        Text("Forecasts cover the next 16 days. Further out, the same dates last year are shown. Data: Open-Meteo.com.",
            style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}
