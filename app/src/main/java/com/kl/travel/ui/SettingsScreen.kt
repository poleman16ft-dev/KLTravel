package com.kl.travel.ui

import android.Manifest
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import com.kl.travel.data.LoungeProgram
import com.kl.travel.net.MapsKey
import com.kl.travel.net.OriginResolver
import com.kl.travel.net.TravelMode
import com.kl.travel.net.UserPhotos
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

@Composable
fun SettingsScreen(vm: TravelViewModel, pad: PaddingValues) {
    // A restore bumps restoreTick, which rebuilds every field from the restored values.
    key(vm.restoreTick) { SettingsBody(vm, pad) }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
private fun SettingsBody(vm: TravelViewModel, pad: PaddingValues) {
    val p = vm.prefs
    val ctx = LocalContext.current
    var url by remember(vm.activeTrip?.id) { mutableStateOf(p.sheetUrl) }
    var provider by remember { mutableStateOf(p.flightProvider) }
    var key by remember { mutableStateOf(p.flightKey) }
    var mapsKeyText by remember { mutableStateOf(p.mapsKey) }
    val scope = rememberCoroutineScope()
    var vehicle by remember { mutableStateOf(p.myVehicle) }
    var vehiclePhotoTick by remember { mutableIntStateOf(0) }
    val vehiclePicker = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) scope.launch { if (UserPhotos.save(ctx, uri, UserPhotos.vehicleFile(ctx))) vehiclePhotoTick++ }
    }
    var mapsKeyMsg by remember { mutableStateOf<String?>(null) }
    var lounges by remember { mutableStateOf(p.loungePrograms) }
    var loungeNotes by remember { mutableStateOf(p.loungeNotes) }
    var cap by remember { mutableStateOf(p.flightMonthlyCap.toString()) }
    var alerts by remember { mutableStateOf(p.alertsEnabled) }
    var lead by remember { mutableStateOf(p.alertLeadMin.toString()) }
    var buf by remember { mutableStateOf(p.leaveBufferMin.toString()) }
    var airBuf by remember { mutableStateOf(p.airportBufferMin.toString()) }
    var defMode by remember { mutableStateOf(TravelMode.from(p.defaultMode)) }
    var hasLoc by remember { mutableStateOf(OriginResolver.hasLocationPermission(ctx)) }
    val locLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
        hasLoc = OriginResolver.hasLocationPermission(ctx)
    }

    Column(Modifier.padding(pad)) {
        TopAppBar(title = { Text("Settings") })
        Column(Modifier.verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {

            Section("Google Sheet") {
                Text("Trip: ${vm.activeTrip?.name ?: "none yet"}", fontWeight = FontWeight.Bold)
                OutlinedButton(onClick = { vm.openTrips() }) { Text("Switch or add a trip") }
                OutlinedTextField(url, { url = it }, label = { Text("Sheet link") }, modifier = Modifier.fillMaxWidth(),
                    supportingText = { Text("Share it as \"Anyone with the link: Viewer\". Tabs: Events and Lodging.") })
                var showTemplate by rememberSaveable { mutableStateOf(false) }
                Button(onClick = { showTemplate = true }) { Text("New trip: get a blank sheet") }
                if (showTemplate) AlertDialog(
                    onDismissRequest = { showTemplate = false },
                    title = { Text("Blank trip sheet (template v${com.kl.travel.data.Versions.TEMPLATE})") },
                    text = {
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text("An empty copy with the Events and Lodging tabs, seat, aircraft and everything else this app reads. No data in it.", style = MaterialTheme.typography.bodySmall)
                            TEMPLATE_STEPS.forEachIndexed { i, (t, b) ->
                                Column { Text("${i + 1}. $t", fontWeight = FontWeight.Bold); Text(b, style = MaterialTheme.typography.bodySmall) }
                            }
                        }
                    },
                    confirmButton = { Button(onClick = { ctx.copyTemplateToSheets() }) { Text("Open in Google Sheets") } },
                    dismissButton = {
                        Row { TextButton(onClick = { ctx.openTemplate() }) { Text("Use the file") }; TextButton(onClick = { showTemplate = false }) { Text("Close") } }
                    },
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Button(enabled = !vm.syncing, onClick = { p.sheetUrl = url; vm.sync() }) {
                        if (vm.syncing) CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp) else Text("Save & sync")
                    }
                    Spacer(Modifier.width(12.dp))
                    val last = if (vm.lastSync > 0) LocalDateTime.ofInstant(Instant.ofEpochMilli(vm.lastSync), ZoneId.systemDefault())
                        .format(DateTimeFormatter.ofPattern("MMM d, h:mm a", Locale.US)) else "never"
                    Text("Last sync: $last", style = MaterialTheme.typography.bodySmall)
                }
            }

            Section("Google Maps key") {
                OutlinedTextField(mapsKeyText, { mapsKeyText = it; mapsKeyMsg = null }, label = { Text("Maps API key (starts with AIza)") },
                    singleLine = true, visualTransformation = PasswordVisualTransformation(), modifier = Modifier.fillMaxWidth(),
                    supportingText = { Text("Your own key from Google Cloud. Enable Routes API, Maps JavaScript API and Places API (New) on it.") })
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(onClick = {
                        val k = mapsKeyText.trim()
                        if (k.isEmpty()) { p.mapsKey = ""; mapsKeyMsg = "Cleared." }
                        else if (!MapsKey.isWellFormed(k)) mapsKeyMsg = "That doesn't look like a Maps key (letters, numbers, - and _ only, 20+ characters)."
                        else { p.mapsKey = k; mapsKeyMsg = "Saved. Open any event to try it." }
                    }) { Text("Save key") }
                    OutlinedButton(onClick = { mapsKeyText = ""; p.mapsKey = ""; mapsKeyMsg = "Cleared." }) { Text("Clear") }
                }
                mapsKeyMsg?.let { Text(it, style = MaterialTheme.typography.bodySmall) }
                var howTo by rememberSaveable { mutableStateOf(false) }
                TextButton(onClick = { howTo = !howTo }) { Text(if (howTo) "Hide: how to get a key" else "How to get a key") }
                if (howTo) {
                    MAPS_KEY_STEPS.forEachIndexed { i, step -> KeyStepCard(i + 1, step) }
                    Text("Google gives a monthly free allowance, but billing must be on. Keep the key private.", style = MaterialTheme.typography.bodySmall)
                }
                var blocked by rememberSaveable { mutableStateOf(false) }
                TextButton(onClick = { blocked = !blocked }) { Text(if (blocked) "Hide: fix the API blocked error" else "Fix: API is blocked error") }
                if (blocked) {
                    Text("If lounges, photos or routes say Requests to this API are blocked, your key isn't allowed to use that API yet.", style = MaterialTheme.typography.bodySmall)
                    BLOCKED_API_STEPS.forEachIndexed { i, step -> KeyStepCard(i + 1, step) }
                }
                var free by rememberSaveable { mutableStateOf(false) }
                TextButton(onClick = { free = !free }) { Text(if (free) "Hide: keep Google free (usage caps)" else "Keep Google free (usage caps)") }
                if (free) {
                    Text("Google has no single monthly cap switch. You cap each API per day instead: a daily limit times 30 is your monthly limit. Budgets only email you, they do not stop charges.", style = MaterialTheme.typography.bodySmall)
                    FREE_CAP_STEPS.forEachIndexed { i, step -> KeyStepCard(i + 1, step) }
                }
                val src = when {
                    MapsKey.userKey(ctx) != null -> "using the key you entered"
                    MapsKey.buildKey() != null -> "using the key built into this app"
                    else -> "MISSING. Paste a key above."
                }
                Text("Status: $src", style = MaterialTheme.typography.bodySmall)
            }

            Section("Google usage") {
                Text("The app counts its own Google calls each month and stops at the limit you set, so nothing runs up a bill. Recent routes are reused for 10 minutes, and background checks wait longer when a trip is far off.",
                    style = MaterialTheme.typography.bodySmall)
                com.kl.travel.data.GoogleApi.entries.forEach { api ->
                    var capText by remember(vm.restoreTick) { mutableStateOf(com.kl.travel.data.GoogleUsage.cap(p, api).toString()) }
                    NumberField("${api.label}: monthly limit", capText) {
                        capText = it.filter(Char::isDigit).take(6)
                        capText.toIntOrNull()?.let { n -> com.kl.travel.data.GoogleUsage.setCap(p, api, n) }
                    }
                    Text("Used this month: ${com.kl.travel.data.GoogleUsage.used(p, api)} of ${com.kl.travel.data.GoogleUsage.cap(p, api)}. Google's ${api.freeNote}.",
                        style = MaterialTheme.typography.labelSmall)
                }
                var live by remember { mutableStateOf(p.liveTraffic) }
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Column(Modifier.weight(1f)) {
                        Text("Live traffic for driving", fontWeight = FontWeight.Bold)
                        Text("Off uses plain travel time: Google's cheaper tier with twice the free allowance. Drive times may run a little short in rush hour.",
                            style = MaterialTheme.typography.bodySmall)
                    }
                    Switch(live, { live = it; p.liveTraffic = it })
                }
                Text("This only counts what this app does. If you use the same key elsewhere, or want a hard stop at Google, use Keep Google free (usage caps) under the Google Maps key section.",
                    style = MaterialTheme.typography.labelSmall)
            }

            Section("My vehicle") {
                Text("For rows where you drive yourself, the trip detail shows your own vehicle. Type what you drive, add a photo, or both. A photo wins over the typed name.",
                    style = MaterialTheme.typography.bodySmall)
                OutlinedTextField(vehicle, { vehicle = it; p.myVehicle = it }, label = { Text("What you drive") }, singleLine = true,
                    modifier = Modifier.fillMaxWidth(), supportingText = { Text("For example: 2019 Toyota RAV4, or Ford Transit camper van.") })
                val vf = remember(vehiclePhotoTick) { UserPhotos.vehicleFile(ctx) }
                val hasPhoto = remember(vehiclePhotoTick) { UserPhotos.has(vf) }
                val bmp = remember(vehiclePhotoTick) { if (hasPhoto) decodeScaled(vf.path)?.asImageBitmap() else null }
                if (bmp != null) androidx.compose.foundation.Image(bmp, "My vehicle", Modifier.fillMaxWidth().height(160.dp)
                    .clip(androidx.compose.foundation.shape.RoundedCornerShape(12.dp)), contentScale = androidx.compose.ui.layout.ContentScale.Crop)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = { vehiclePicker.launch(androidx.activity.result.PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) }) {
                        Text(if (hasPhoto) "Change photo" else "Choose photo")
                    }
                    if (hasPhoto) TextButton(onClick = { UserPhotos.delete(vf); vehiclePhotoTick++ }) { Text("Remove photo") }
                }
                Text("Photos stay on this phone and are not part of the backup.", style = MaterialTheme.typography.labelSmall)
            }

            Section("Lounge access") {
                Text("Tick what you have. The flight screen then shows which airport lounges you may get into.", style = MaterialTheme.typography.bodySmall)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    LoungeProgram.entries.filter { it != LoungeProgram.BUSINESS_TICKET }.forEach { prog ->
                        FilterChip(selected = prog in lounges, onClick = {
                            lounges = if (prog in lounges) lounges - prog else lounges + prog
                            p.loungePrograms = lounges
                        }, label = { Text(prog.label) })
                    }
                }
                OutlinedTextField(loungeNotes, { loungeNotes = it; p.loungeNotes = it }, label = { Text("Other access or notes") },
                    modifier = Modifier.fillMaxWidth(), minLines = 2,
                    supportingText = { Text("For example: EVA Diamond, Priority Pass 2 guests, AAdvantage Executive Platinum.") })
                Text("Matches are best guesses from lounge names. Always confirm access rules, guest limits and hours with the lounge or your card issuer.", style = MaterialTheme.typography.labelSmall)
            }

            Section("Flight updates") {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(provider == "aerodatabox", { provider = "aerodatabox"; p.flightProvider = provider }, { Text("AeroDataBox (RapidAPI)") })
                    FilterChip(provider == "aviationstack", { provider = "aviationstack"; p.flightProvider = provider }, { Text("aviationstack") })
                }
                OutlinedTextField(key, { key = it; p.flightKey = it }, label = { Text("API key") }, singleLine = true,
                    visualTransformation = PasswordVisualTransformation(), modifier = Modifier.fillMaxWidth())
                FlightKeyHelp(provider)
                NumberField("Monthly lookup limit (protects your free quota)", cap) { cap = it; it.toIntOrNull()?.let { n -> p.flightMonthlyCap = n } }
                Text("Used this month: ${p.flightCallsThisMonth}", style = MaterialTheme.typography.bodySmall)
            }

            Section("Alerts") {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Leave-by, traffic and flight alerts", Modifier.weight(1f))
                    Switch(alerts, { alerts = it; p.alertsEnabled = it })
                }
                NumberField("Alert me this many minutes before leave time", lead) { lead = it; it.toIntOrNull()?.let { n -> p.alertLeadMin = n } }
                NumberField("Buffer for normal events (min)", buf) { buf = it; it.toIntOrNull()?.let { n -> p.leaveBufferMin = n } }
                NumberField("Buffer for flights (min before departure)", airBuf) { airBuf = it; it.toIntOrNull()?.let { n -> p.airportBufferMin = n } }
                Text("Default travel mode", style = MaterialTheme.typography.labelMedium)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    TravelMode.entries.forEach { m ->
                        FilterChip(defMode == m, { defMode = m; p.defaultMode = m.name }, { Text(m.label) })
                    }
                }
                OutlinedButton(onClick = { vm.checkAlertsNow(); ctx.toast("Checking now…") }) { Text("Check alerts now") }
                Text("Alerts run about every 15 minutes in the background.", style = MaterialTheme.typography.bodySmall)
            }

            Section("Permissions") {
                Text(if (hasLoc) "Location: allowed" else "Location: not allowed (routes start from your previous stop or lodging instead)")
                if (!hasLoc) Button(onClick = {
                    locLauncher.launch(arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION))
                }) { Text("Allow location") }
                Text("Background alerts can't read your live location on newer Android, so they measure from your previous stop or lodging.",
                    style = MaterialTheme.typography.bodySmall)
            }

            Section("Backup and restore") {
                Text("Saves your keys, lounge memberships, alert settings, trips and expenses to one file. Restore it after a reinstall or on a new phone so you don't type anything again. The file contains your API keys, so keep it private.",
                    style = MaterialTheme.typography.bodySmall)
                val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
                    if (uri != null) {
                        val text = runCatching { ctx.contentResolver.openInputStream(uri)?.bufferedReader()?.use { it.readText() } }.getOrNull()
                        if (text.isNullOrBlank()) vm.syncMessage = "Couldn't read that file." else vm.restoreBackup(text)
                    }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(onClick = { scope.launch { runCatching { ctx.shareBackup(vm.backupText()) }.onFailure { vm.syncMessage = "Couldn't make the backup." } } }) { Text("Back up") }
                    OutlinedButton(onClick = { picker.launch(arrayOf("application/json", "text/plain", "application/octet-stream", "*/*")) }) { Text("Restore") }
                }
                Text("Back up: choose Drive, Files or email to save it. Restore: pick the KLTravel_backup file.", style = MaterialTheme.typography.labelSmall)
            }

            Section("About") {
                OutlinedButton(onClick = { vm.startSetup() }) { Text("Run the setup guide again") }
                Text("Google Maps key: " + if (MapsKey.effective(ctx) != null) "set" else "MISSING (add it in the Google Maps key section above)")
                Text("KL Travel v${com.kl.travel.BuildConfig.VERSION_NAME} · sheet template v${com.kl.travel.data.Versions.TEMPLATE}", style = MaterialTheme.typography.bodySmall)
            }
            Spacer(Modifier.height(24.dp))
        }
    }
}

/** One numbered how-to card; [links] are tappable buttons that open the exact Google Cloud page. */
internal data class KeyStep(val title: String, val body: String, val links: List<Pair<String, String>> = emptyList())

private const val GC = "https://console.cloud.google.com"
private val LINK_PROJECT = "Create project" to "$GC/projectcreate"
private val LINK_BILLING = "Open billing" to "$GC/billing"
private val LINK_ROUTES = "Routes API" to "$GC/apis/library/routes.googleapis.com"
private val LINK_MAPS_JS = "Maps JavaScript API" to "$GC/apis/library/maps-backend.googleapis.com"
private val LINK_PLACES = "Places API (New)" to "$GC/apis/library/places.googleapis.com"
private val LINK_KEYS = "Open my keys" to "$GC/apis/credentials"

internal val MAPS_KEY_STEPS = listOf(
    KeyStep("Create a project", "Sign in at console.cloud.google.com. Tap the project picker, then New Project. Name it KL Travel and Create.", listOf(LINK_PROJECT)),
    KeyStep("Turn on billing", "Menu > Billing. Link a billing account (a card) to the project. Google's free monthly usage usually covers a trip app.", listOf(LINK_BILLING)),
    KeyStep("Enable three APIs", "Tap each button below, then Enable. Places API (New) is used for lodging photos and airport lounges. Choose the one with \"(New)\", not the plain \"Places API\".", listOf(LINK_ROUTES, LINK_MAPS_JS, LINK_PLACES)),
    KeyStep("Create the key", "Credentials > Create credentials > API key. Copy the key (starts with AIza).", listOf(LINK_KEYS)),
    KeyStep("Restrict it", "Open the key. Under API restrictions choose Restrict key and tick Routes API, Maps JavaScript API and Places API (New). Leave application restrictions off. Save.", listOf(LINK_KEYS)),
    KeyStep("Paste it here", "Paste the key in the box above and tap Save key. Open any event to check the route and map show up."),
)

/** For the error "Requests to this API ... are blocked": the key isn't allowed to use that API. */
internal val BLOCKED_API_STEPS = listOf(
    KeyStep("Enable the API", "Tap each button, then Enable if it isn't enabled yet.", listOf(LINK_PLACES, LINK_ROUTES, LINK_MAPS_JS)),
    KeyStep("Allow it on your key", "Open your key. Under API restrictions, tick Places API (New), Routes API and Maps JavaScript API. Save.", listOf(LINK_KEYS)),
    KeyStep("Check billing", "The project must have a billing account linked, or Google still refuses the calls.", listOf(LINK_BILLING)),
    KeyStep("Wait, then retry", "Google takes 2 to 5 minutes to apply the change. Then reopen the flight or lodging page and try again."),
)

private val LINK_Q_PLACES = "Places quotas" to "$GC/apis/api/places.googleapis.com/quotas"
private val LINK_Q_ROUTES = "Routes quotas" to "$GC/apis/api/routes.googleapis.com/quotas"
private val LINK_Q_MAPS = "Maps JS quotas" to "$GC/apis/api/maps-backend.googleapis.com/quotas"
private val LINK_BUDGETS = "Open budgets" to "$GC/billing/budgets"

/** Keeping the key inside Google's free monthly allowance. Google only offers per-day / per-minute quotas, so monthly = daily x 30. */
internal val FREE_CAP_STEPS = listOf(
    KeyStep("Know the free monthly limits", "Map loads 10,000. Routes 10,000. Places Text Search Pro 5,000. Place Details 10,000. Stay under these and nothing is charged."),
    KeyStep("Cap Places at 100 a day", "Open Places quotas. Type Text Search in the filter box, tick the row that says per day, tap Edit quotas, enter 100, Submit request. 100 x 30 = 3,000 a month, under the 5,000 free.", listOf(LINK_Q_PLACES)),
    KeyStep("Cap Routes at 150 a day", "Open Routes quotas. Tick the Compute Routes row that says per day, Edit quotas, enter 150, Submit. 150 x 30 = 4,500 a month, under the 10,000 free.", listOf(LINK_Q_ROUTES)),
    KeyStep("Cap map loads at 200 a day", "Open Maps JS quotas. Tick the Map loads row that says per day, Edit quotas, enter 200, Submit. 200 x 30 = 6,000 a month, under the 10,000 free.", listOf(LINK_Q_MAPS)),
    KeyStep("If there is no per day row", "Lower the per minute row to 5 instead. Nobody can run up a big bill at 5 a minute in a trip app.", emptyList()),
    KeyStep("Add a $1 budget alert", "Create budget, amount 1, alerts at 50% and 100%. It emails you; it does not stop anything, so keep the caps too.", listOf(LINK_BUDGETS)),
    KeyStep("Leave application restrictions off", "Do not restrict the key to Android apps. The map and lounge screens load inside the app and would stop working. The API restrictions from the key steps are enough.", listOf(LINK_KEYS)),
    KeyStep("What a cap does", "Once a day's cap is hit, that feature shows an error until the next day. That is the point: it stops charges. Raise the number if you travel a lot."),
)

private val LINK_ADB_SIGNUP = "Sign up (RapidAPI)" to "https://rapidapi.com/auth/sign-up"
private val LINK_ADB_PLAN = "Free plan page" to "https://rapidapi.com/aedbx-aedbx/api/aerodatabox/pricing"
/** The Flight status endpoint page. Once you are signed in and subscribed its Headers tab and code snippet hold your x-rapidapi-key. */
private val LINK_ADB_KEY = "Open the key page" to "https://rapidapi.com/aedbx-aedbx/api/aerodatabox/playground/apiendpoint_37f1f719-ef9e-4596-abf6-b3f882435e4e"
private val LINK_ADB_APPS = "Open My Apps" to "https://rapidapi.com/developer"
private val LINK_AVS_SIGNUP = "Sign up (free)" to "https://aviationstack.com/signup/free"
private val LINK_AVS_KEY = "Open my key (dashboard)" to "https://aviationstack.com/dashboard"

/** The page a tap on "Get my free key" opens for each provider. */
internal fun flightKeyUrl(provider: String) =
    if (provider == "aviationstack") "https://aviationstack.com/signup/free" else "https://rapidapi.com/aedbx-aedbx/api/aerodatabox/pricing"

internal val AERODATABOX_STEPS = listOf(
    KeyStep("Make a RapidAPI account", "AeroDataBox is sold through RapidAPI. Sign up (Google or GitHub sign-in works), then sign in.", listOf(LINK_ADB_SIGNUP)),
    KeyStep("Subscribe to the free plan", "On the pricing page tap Subscribe under Basic ($0). It allows 400 units a month, and a flight lookup costs 2, so about 200 lookups. The app's monthly limit below keeps you under that.", listOf(LINK_ADB_PLAN)),
    KeyStep("Easiest: get it from My Apps", "Open My Apps below. You'll see a row called default-application. Tap the shield icon under Authorization. The Application Key shown there is your key. Copy it and paste it above.", listOf(LINK_ADB_APPS)),
    KeyStep("Or: use the code snippet", "Open the key page while signed in. On a phone it looks cramped: tap the </> icon on the right edge (Code snippets). The line that says x-rapidapi-key is your key. Press and hold it to copy, then paste it above. Tip: in Chrome tap the three dots > Desktop site to see it all at once.", listOf(LINK_ADB_KEY)),
)

internal val AVIATIONSTACK_STEPS = listOf(
    KeyStep("Sign up for the free plan", "Fill in the form (it asks for a billing address, not a card) and choose Free. The free plan allows 100 lookups a month.", listOf(LINK_AVS_SIGNUP)),
    KeyStep("Copy the access key", "Sign in. The dashboard shows your API access key. Copy it and paste it above.", listOf(LINK_AVS_KEY)),
)

/** Big "go get the key" button plus numbered steps for the chosen flight-data provider. */
@OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
@Composable
internal fun FlightKeyHelp(provider: String) {
    val ctx = LocalContext.current
    val name = if (provider == "aviationstack") "aviationstack" else "AeroDataBox"
    Button(onClick = {
        runCatching { ctx.startActivity(android.content.Intent(android.content.Intent.ACTION_VIEW, android.net.Uri.parse(flightKeyUrl(provider)))) }
    }, modifier = Modifier.fillMaxWidth()) { Text("Get my free $name key") }
    val steps = if (provider == "aviationstack") AVIATIONSTACK_STEPS else AERODATABOX_STEPS
    steps.forEachIndexed { i, s -> KeyStepCard(i + 1, s) }
}

@OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
@Composable
internal fun KeyStepCard(n: Int, step: KeyStep) {
    val ctx = LocalContext.current
    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text("$n. ${step.title}", fontWeight = FontWeight.Bold)
            Text(step.body, style = MaterialTheme.typography.bodySmall)
            if (step.links.isNotEmpty()) FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                step.links.forEach { (label, url) ->
                    FilledTonalButton(onClick = {
                        runCatching { ctx.startActivity(android.content.Intent(android.content.Intent.ACTION_VIEW, android.net.Uri.parse(url))) }
                    }) { Text(label) }
                }
            }
        }
    }
}

@Composable
fun Section(title: String, content: @Composable ColumnScope.() -> Unit) {
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            content()
        }
    }
}

@Composable
private fun NumberField(label: String, value: String, onChange: (String) -> Unit) {
    OutlinedTextField(value, onChange, label = { Text(label) }, singleLine = true, modifier = Modifier.fillMaxWidth(),
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number))
}
