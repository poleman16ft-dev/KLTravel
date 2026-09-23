package com.kl.travel.ui

import android.Manifest
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import com.kl.travel.data.LoungeProgram
import com.kl.travel.net.MapsKey
import com.kl.travel.net.OriginResolver

private val STEP_TITLES = listOf("Welcome", "Your trip sheet", "Google Maps key", "Flight updates", "Lounge access", "Alerts and permissions", "All set")

/**
 * First-run guide: sheet link, Maps key, flight key, lounges, alerts. Every step can be skipped and so can the whole guide
 * (top right). Nothing here is required; everything can be changed later in Settings.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun SetupWizard(vm: TravelViewModel) {
    val p = vm.prefs
    val ctx = LocalContext.current
    var step by rememberSaveable { mutableIntStateOf(0) }
    val last = STEP_TITLES.lastIndex

    var url by rememberSaveable { mutableStateOf(p.sheetUrl) }
    var mapsKey by rememberSaveable { mutableStateOf(p.mapsKey) }
    var sheetMsg by remember { mutableStateOf<String?>(null) }
    var mapsMsg by remember { mutableStateOf<String?>(null) }
    var showFree by rememberSaveable { mutableStateOf(false) }
    var provider by rememberSaveable { mutableStateOf(p.flightProvider) }
    var flightKey by rememberSaveable { mutableStateOf(p.flightKey) }
    var lounges by remember { mutableStateOf(p.loungePrograms) }
    var alerts by rememberSaveable { mutableStateOf(p.alertsEnabled) }
    var hasLoc by remember { mutableStateOf(OriginResolver.hasLocationPermission(ctx)) }
    val locLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { hasLoc = OriginResolver.hasLocationPermission(ctx) }
    val notifLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {}
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            val text = runCatching { ctx.contentResolver.openInputStream(uri)?.bufferedReader()?.use { it.readText() } }.getOrNull()
            if (text.isNullOrBlank()) ctx.toast("Couldn't read that file.") else { vm.restoreBackup(text); vm.finishSetup() }
        }
    }

    /** Saves what this step collected (only what was filled in), then moves on. */
    fun saveStep() {
        when (step) {
            1 -> if (url.isNotBlank() && url.trim() != p.sheetUrl) {
                val t = vm.activeTrip
                val ok = if (t == null) vm.addTrip("My trip", url) else vm.setTripLink(t.id, url)
                if (!ok) { sheetMsg = "That doesn't look like a Google Sheets link. Fix it, or tap Skip this step."; return }
            }
            2 -> { val k = mapsKey.trim(); if (k.isNotEmpty()) { if (MapsKey.isWellFormed(k)) p.mapsKey = k else { mapsMsg = "That doesn't look like a Maps key. Fix it, or tap Skip this step."; return } } }
            3 -> if (flightKey.isNotBlank()) { p.flightProvider = provider; p.flightKey = flightKey.trim() }
            5 -> p.alertsEnabled = alerts
        }
        if (step < last) step++
    }

    Column(Modifier.fillMaxSize().systemBarsPadding()) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(if (step in 1 until last) "Step $step of ${last - 1}" else "KL Travel", style = MaterialTheme.typography.labelLarge, modifier = Modifier.weight(1f))
            if (step < last) TextButton(onClick = { vm.finishSetup() }) { Text("Skip setup") }
        }
        LinearProgressIndicator(progress = { step / last.toFloat() }, modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp))

        Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(STEP_TITLES[step], style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
            when (step) {
                0 -> {
                    Text("A few quick steps to connect your trip sheet and Google keys. It takes about 3 minutes.")
                    Text("Nothing here is required. You can skip any step, or skip the whole guide, and change everything later in Settings.", style = MaterialTheme.typography.bodySmall)
                    OutlinedButton(onClick = { picker.launch(arrayOf("application/json", "text/plain", "application/octet-stream", "*/*")) }) { Text("I have a backup file to restore") }
                }
                1 -> {
                    Text("The app reads your trip from a Google Sheet with Events and Lodging tabs. Share it as \"Anyone with the link: Viewer\", then paste the link.")
                    OutlinedTextField(url, { url = it; sheetMsg = null }, label = { Text("Sheet link") }, modifier = Modifier.fillMaxWidth())
                    sheetMsg?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall) }
                    Button(onClick = { ctx.copyTemplateToSheets() }) { Text("Don't have one? Get a blank sheet") }
                    Text("Opens Google Sheets: tap Make a copy, fill it in, then share it as Anyone with the link: Viewer and paste that link here.", style = MaterialTheme.typography.bodySmall)
                    Text("You can add more trips later from Settings.", style = MaterialTheme.typography.bodySmall)
                }
                2 -> {
                    Text("A Google Maps key powers routes, maps, lodging photos and airport lounges. Google's free monthly allowance covers a trip app, but billing must be on. Follow the steps, then paste the key.")
                    MAPS_KEY_STEPS.take(5).forEachIndexed { i, s -> KeyStepCard(i + 1, s) }
                    OutlinedTextField(mapsKey, { mapsKey = it; mapsMsg = null }, label = { Text("Maps API key (starts with AIza)") }, singleLine = true,
                        visualTransformation = PasswordVisualTransformation(), modifier = Modifier.fillMaxWidth())
                    mapsMsg?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall) }
                    TextButton(onClick = { showFree = !showFree }) { Text(if (showFree) "Hide: keep Google free" else "Recommended: keep Google free (usage caps)") }
                    if (showFree) FREE_CAP_STEPS.forEachIndexed { i, s -> KeyStepCard(i + 1, s) }
                }
                3 -> {
                    Text("Optional. A flight-data key gives live gate, delay and plane info. Skip it if you don't want live flight updates.")
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        FilterChip(provider == "aerodatabox", { provider = "aerodatabox" }, { Text("AeroDataBox (RapidAPI)") })
                        FilterChip(provider == "aviationstack", { provider = "aviationstack" }, { Text("aviationstack") })
                    }
                    OutlinedTextField(flightKey, { flightKey = it }, label = { Text("Flight API key") }, singleLine = true,
                        visualTransformation = PasswordVisualTransformation(), modifier = Modifier.fillMaxWidth())
                    FlightKeyHelp(provider)
                    Text("Both have free plans. Sign up on their site, copy the key, paste it here. Lookups are limited each month to protect the free quota.", style = MaterialTheme.typography.bodySmall)
                }
                4 -> {
                    Text("Optional. Tick the lounge memberships and cards you have, and the flight screen shows the airport lounges you may get into.")
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        LoungeProgram.entries.filter { it != LoungeProgram.BUSINESS_TICKET }.forEach { prog ->
                            FilterChip(selected = prog in lounges, onClick = {
                                lounges = if (prog in lounges) lounges - prog else lounges + prog
                                p.loungePrograms = lounges
                            }, label = { Text(prog.label) })
                        }
                    }
                    Text("Access rules change, so always confirm with the lounge.", style = MaterialTheme.typography.bodySmall)
                }
                5 -> {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("Leave-by, traffic and flight alerts", Modifier.weight(1f)); Switch(alerts, { alerts = it })
                    }
                    if (Build.VERSION.SDK_INT >= 33) OutlinedButton(onClick = { notifLauncher.launch(Manifest.permission.POST_NOTIFICATIONS) }) { Text("Allow notifications") }
                    Text(if (hasLoc) "Location: allowed" else "Location: not allowed. Routes then start from your previous stop or lodging.")
                    if (!hasLoc) OutlinedButton(onClick = { locLauncher.launch(arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION)) }) { Text("Allow location") }
                    Text("Alert timing and travel mode can be tuned in Settings > Alerts.", style = MaterialTheme.typography.bodySmall)
                }
                else -> {
                    fun mark(ok: Boolean) = if (ok) "Done" else "Skipped (add later in Settings)"
                    Text("Sheet link: ${mark(p.sheetUrl.isNotBlank())}")
                    Text("Google Maps key: ${mark(MapsKey.effective(ctx) != null)}")
                    Text("Flight updates key: ${mark(p.flightKey.isNotBlank())}")
                    Text("Lounge memberships: ${if (p.loungePrograms.isEmpty()) "none ticked" else p.loungePrograms.joinToString { it.label }}")
                    Text("Anything skipped is in Settings. You can run this guide again from Settings > About.", style = MaterialTheme.typography.bodySmall)
                }
            }
        }

        Row(Modifier.fillMaxWidth().padding(16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            if (step in 1 until last) TextButton(onClick = { step-- }) { Text("Back") }
            Spacer(Modifier.weight(1f))
            when {
                step == 0 -> Button(onClick = { step = 1 }) { Text("Start setup") }
                step < last -> {
                    TextButton(onClick = { step++ }) { Text("Skip this step") }
                    Button(onClick = { saveStep() }) { Text("Next") }
                }
                else -> Button(onClick = { vm.finishSetup() }) { Text("Finish") }
            }
        }
    }
}
