package com.kl.travel.ui

import android.app.Application
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.kl.travel.data.*
import com.kl.travel.net.*
import com.kl.travel.receipt.ReceiptFiles
import com.kl.travel.receipt.ReceiptOcr
import java.io.File
import com.kl.travel.work.AlertScheduler
import com.kl.travel.work.FlightUpdater
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import com.kl.travel.widget.NextUpWidget
import org.json.JSONArray
import java.time.LocalDate
import java.time.LocalDateTime

data class RouteState(
    val loading: Boolean = false,
    val route: RouteResult? = null,
    val error: String? = null,
    val originLabel: String? = null,
    val leaveBy: LocalDateTime? = null,
)

data class FlightUi(val loading: Boolean = false, val text: String? = null, val error: String? = null, val checkedAt: Long = 0L)

@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
class TravelViewModel(app: Application) : AndroidViewModel(app) {
    private val ctx = app.applicationContext
    val prefs = Prefs(ctx)
    private val dao = TravelDb.get(ctx).dao()
    private val repo = SheetRepository(ctx)

    /** Which trip's data every screen shows. */
    private val tripId = MutableStateFlow(prefs.activeTripId)

    val items: StateFlow<List<TripItem>> = tripId.flatMapLatest { id -> combine(dao.events(id), dao.hotels(id)) { e, h -> TripBuilder.build(e, h) } }
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())
    val hotels: StateFlow<List<HotelEntity>> = tripId.flatMapLatest { dao.hotels(it) }.stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())
    val expenses: StateFlow<List<ExpenseEntity>> = tripId.flatMapLatest { dao.expenses(it) }.stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())
    val plannedTotal: StateFlow<Double> = tripId.flatMapLatest { id ->
        combine(dao.events(id), dao.hotels(id)) { e, h -> e.sumOf { it.cost ?: 0.0 } + h.sumOf { it.cost ?: 0.0 } }
    }.stateIn(viewModelScope, SharingStarted.Eagerly, 0.0)

    // ---- trips ----
    var trips by mutableStateOf(prefs.trips()); private set
    var activeTrip by mutableStateOf(prefs.activeTrip()); private set
    /** First-run setup (keys and settings). Only a fresh install sees it; it can always be skipped or re-run from Settings. */
    var showSetup by mutableStateOf(prefs.isFreshInstall())
    fun finishSetup() { prefs.setupDone = true; showSetup = false; showTrips = prefs.activeTrip() == null }
    fun startSetup() { showSetup = true }

    /** The "pick your trip" screen; opens by itself when there are no trips yet. */
    var showTrips by mutableStateOf(prefs.activeTrip() == null)
    var spans by mutableStateOf<Map<Long, TripSpan>>(emptyMap()); private set
    var tripError by mutableStateOf<String?>(null)

    fun refreshTrips() {
        trips = prefs.trips(); activeTrip = prefs.activeTrip()
        viewModelScope.launch { spans = trips.associate { it.id to dao.span(it.id) } }
    }
    fun openTrips() { tripError = null; refreshTrips(); showTrips = true }

    fun selectTrip(id: Long) {
        if (prefs.trips().none { it.id == id }) return
        prefs.activeTripId = id; tripId.value = id
        selectedId = null; routeJob?.cancel(); routeState = RouteState(); flight = FlightUi()
        lastSync = prefs.lastSync; weather = loadCachedWeather(); weatherError = null
        lastAutoSync = 0L; showTrips = false; tripError = null
        refreshTrips(); syncOnOpen(); NextUpWidget.refreshAll(ctx)
    }

    private fun linkProblem(link: String): String? =
        if (link.isNotBlank() && repo.extractSheetId(link) == null) "That doesn't look like a Google Sheets link." else null

    /** Returns true when added. New trips open right away and sync. */
    fun addTrip(name: String, link: String): Boolean {
        linkProblem(link.trim())?.let { tripError = it; return false }
        val t = prefs.addTrip(name, link)
        selectTrip(t.id); return true
    }

    fun renameTrip(id: Long, name: String) {
        prefs.trips().firstOrNull { it.id == id }?.let { prefs.updateTrip(it.copy(name = Trips.cleanName(name, prefs.trips().filter { o -> o.id != id }))) }
        refreshTrips()
    }

    fun setTripLink(id: Long, link: String): Boolean {
        linkProblem(link.trim())?.let { tripError = it; return false }
        prefs.trips().firstOrNull { it.id == id }?.let { prefs.updateTrip(it.copy(sheetUrl = link.trim())) }
        tripError = null; refreshTrips()
        if (id == prefs.activeTripId) sync()
        return true
    }

    fun deleteTrip(id: Long) {
        val wasActive = id == prefs.activeTripId
        prefs.deleteTrip(id)
        viewModelScope.launch { dao.deleteTrip(id); refreshTrips() }
        if (wasActive) {
            val next = prefs.trips().firstOrNull()
            prefs.activeTripId = next?.id ?: 0L; tripId.value = prefs.activeTripId
            selectedId = null; routeState = RouteState(); flight = FlightUi()
            lastSync = prefs.lastSync; weather = loadCachedWeather()
        }
        refreshTrips()
    }

    // ---- backup / restore ----
    /** Bumped after a restore so Settings rebuilds its fields from the restored values. */
    var restoreTick by mutableStateOf(0); private set

    suspend fun backupText(): String = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
        Backup.encode(prefs.exportAll(), dao.allExpenses(), com.kl.travel.BuildConfig.VERSION_NAME)
    }

    fun restoreBackup(text: String) {
        viewModelScope.launch {
            val r = runCatching {
                val b = Backup.decode(text)
                prefs.importAll(b.prefs)
                // Photos aren't in the backup; keep the ones already on this phone attached to the same expenses.
                val have = dao.allExpenses()
                dao.replaceExpenses(b.expenses.map { n ->
                    n.copy(receiptPath = have.firstOrNull { o -> o.receiptPath.isNotBlank() && o.date == n.date && o.amount == n.amount && o.note == n.note && o.tripId == n.tripId }?.receiptPath ?: "")
                })
                b
            }
            r.fold({ b ->
                val id = prefs.activeTripId
                tripId.value = id; selectedId = null; routeState = RouteState(); flight = FlightUi()
                lastSync = prefs.lastSync; weather = loadCachedWeather(); weatherError = null
                lastAutoSync = 0L; showTrips = prefs.activeTrip() == null
                refreshTrips(); restoreTick++
                syncMessage = "Restored ${b.prefs.size} settings and ${b.expenses.size} expenses."
                if (prefs.sheetUrl.isNotBlank()) sync(silent = true)
                NextUpWidget.refreshAll(ctx)
            }, { syncMessage = "Couldn't restore: ${it.message ?: "unreadable file"}" })
        }
    }

    // ---- sync ----
    var syncing by mutableStateOf(false); private set
    var syncMessage by mutableStateOf<String?>(null)
    var lastSync by mutableLongStateOf(prefs.lastSync); private set

    private var lastAutoSync = 0L

    /** Called every time the app comes to the foreground. Quiet unless something is actually wrong. */
    fun syncOnOpen() {
        if (prefs.sheetUrl.isBlank()) return
        val now = System.currentTimeMillis()
        if (now - lastAutoSync < 20_000) return        // avoid double-syncing on quick app switches
        lastAutoSync = now
        sync(silent = true)
    }

    fun sync(silent: Boolean = false) {
        if (syncing) return
        viewModelScope.launch {
            syncing = true
            val syncedTrip = tripId.value
            val r = repo.sync(syncedTrip)
            if (syncedTrip == tripId.value) lastSync = prefs.lastSync
            r.fold(
                { s ->
                    if (!silent) syncMessage = "Synced ${s.events} events, ${s.hotels} lodging" +
                        if (s.warnings.isNotEmpty()) " (${s.warnings.size} warnings: ${s.warnings.first()})" else ""
                },
                { e ->
                    val offline = e is java.net.UnknownHostException || e is java.net.SocketTimeoutException ||
                        e is java.net.ConnectException
                    // Offline on open is normal while travelling: keep showing the saved trip without nagging.
                    if (!silent || !offline) syncMessage = e.message ?: "Sync failed"
                },
            )
            syncing = false
            NextUpWidget.refreshAll(ctx)
            refreshWeather()
        }
    }

    // ---- navigation ----
    var selectedId by mutableStateOf<String?>(null); private set
    fun open(id: String) { selectedId = id }
    /** Saved live-status text for a flight row (used to move layover times when a flight runs late). */
    fun flightCache(id: String): String? = FlightUpdater.cached(prefs, id)
    fun close() { selectedId = null; routeJob?.cancel() }

    // ---- route for the open item ----
    var mode by mutableStateOf(TravelMode.DRIVE); private set
    var useMyLocation by mutableStateOf(prefs.useMyLocation); private set
    var routeState by mutableStateOf(RouteState()); private set
    private var routeJob: Job? = null

    fun effectiveMode(item: TripItem): TravelMode =
        TravelMode.from(prefs.savedMode(item.id) ?: TravelMode.hint(item.transport)?.name ?: prefs.defaultMode)

    fun onDetailShown(item: TripItem) {
        mode = effectiveMode(item)
        loadRoute(item)
        flight = FlightUi(text = FlightUpdater.cached(prefs, item.id), checkedAt = FlightUpdater.cachedAt(prefs, item.id))
    }

    fun setMode(item: TripItem, m: TravelMode) {
        mode = m; prefs.setMode(item.id, m.name); loadRoute(item)
    }

    fun setUseMyLocation(item: TripItem, v: Boolean) {
        useMyLocation = v; prefs.useMyLocation = v; loadRoute(item)
    }

    fun loadRoute(item: TripItem) {
        routeJob?.cancel()
        if (!item.needsTravel) { routeState = RouteState(); return }
        routeState = RouteState(loading = true)
        routeJob = viewModelScope.launch {
            val origin = OriginResolver.resolve(ctx, item, items.value, useMyLocation)
            if (origin == null) {
                routeState = RouteState(error = "No starting point yet. Allow location in Settings, or add an earlier stop or lodging to the sheet.")
                return@launch
            }
            RoutesClient.compute(ctx, origin.place, Place.Address(item.address), mode, item.start).fold(
                { r ->
                    val leave = LeaveBy.compute(item, r.durationSec, prefs.leaveBufferMin, prefs.airportBufferMin)
                    routeState = RouteState(route = r, originLabel = origin.label, leaveBy = leave)
                },
                { routeState = RouteState(error = it.message ?: "Route failed", originLabel = origin.label) },
            )
        }
    }

    // ---- flight ----
    var flight by mutableStateOf(FlightUi()); private set

    fun refreshFlight(item: TripItem) {
        if (item.flight.isBlank() || flight.loading) return
        flight = flight.copy(loading = true, error = null)
        viewModelScope.launch {
            val r = FlightUpdater.refresh(ctx, prefs, item, notify = false)
            flight = r.fold(
                { FlightUi(text = FlightUpdater.cached(prefs, item.id), checkedAt = FlightUpdater.cachedAt(prefs, item.id)) },
                { FlightUi(text = FlightUpdater.cached(prefs, item.id), error = it.message ?: "Lookup failed", checkedAt = FlightUpdater.cachedAt(prefs, item.id)) },
            )
        }
    }

    // ---- expenses ----
    fun addExpense(category: String, amount: Double, note: String, date: LocalDate = LocalDate.now(),
                   receiptPath: String = "", currency: String = "USD", originalAmount: Double = 0.0) {
        val trip = tripId.value
        viewModelScope.launch {
            dao.addExpense(ExpenseEntity(date = date.toString(), category = category, amount = amount, note = note, tripId = trip,
                receiptPath = receiptPath, currency = currency, originalAmount = originalAmount))
        }
    }
    fun deleteExpense(e: ExpenseEntity) {
        ReceiptFiles.delete(e.receiptPath)
        viewModelScope.launch { dao.deleteExpense(e.id) }
    }

    // ---- receipt scanning ----
    /** A photographed receipt waiting for you to check what was read from it. */
    class ReceiptDraft(val path: String, val parsed: ParsedReceipt, val readOk: Boolean)

    var scanning by mutableStateOf(false); private set
    var receiptDraft by mutableStateOf<ReceiptDraft?>(null); private set

    fun scanReceipt(file: File) {
        scanning = true
        viewModelScope.launch {
            val text = ReceiptOcr.recognize(ctx, file).getOrNull().orEmpty()
            val parsed = if (text.isBlank()) ParsedReceipt("", null, null, "", "Other") else ReceiptParser.parse(text)
            kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) { ReceiptFiles.shrink(file) }
            receiptDraft = ReceiptDraft(file.absolutePath, parsed, readOk = text.isNotBlank())
            scanning = false
        }
    }

    fun scanFromGallery(uri: android.net.Uri) {
        scanning = true
        viewModelScope.launch {
            val f = ReceiptFiles.newFile(ctx)
            val ok = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) { ReceiptFiles.copyFrom(ctx, uri, f) }
            if (ok) scanReceipt(f) else { scanning = false; syncMessage = "Couldn't open that photo." }
        }
    }

    fun discardReceipt() { receiptDraft?.let { ReceiptFiles.delete(it.path) }; receiptDraft = null }

    fun saveReceipt(category: String, usd: Double, note: String, date: LocalDate, currency: String, original: Double) {
        val d = receiptDraft ?: return
        addExpense(category, usd, note, date, receiptPath = d.path, currency = currency, originalAmount = original)
        receiptDraft = null
    }

    /** Receipt amount converted to USD (rate cached for offline use). */
    suspend fun toUsd(amount: Double, currency: String): Result<Double> =
        CurrencyClient.rate(prefs, currency, "USD").map { amount * it.rate }


    // ---- weather (Open-Meteo) ----
    var weather by mutableStateOf<Map<LocalDate, DayWeather>>(loadCachedWeather()); private set
    var weatherLoading by mutableStateOf(false); private set
    var weatherError by mutableStateOf<String?>(null); private set

    private fun loadCachedWeather(): Map<LocalDate, DayWeather> = runCatching {
        val a = JSONArray(prefs.getStr(prefs.tripKey("weather_json")) ?: return emptyMap())
        (0 until a.length()).map { DayWeather.fromJson(a.getJSONObject(it)) }.associateBy { it.date }
    }.getOrDefault(emptyMap())

    /** Refreshes at most every 3 hours, or when the trip's days/places change. */
    fun refreshWeather(force: Boolean = false) {
        val days = WeatherClient.planDays(items.value)
        if (days.isEmpty() || weatherLoading) return
        val key = days.joinToString("|") { "${it.first}@${it.second}" }
        val fresh = System.currentTimeMillis() - prefs.getLong(prefs.tripKey("weather_at")) < 3 * 60 * 60 * 1000L
        if (!force && fresh && key == prefs.getStr(prefs.tripKey("weather_key"))) return
        viewModelScope.launch {
            weatherLoading = true; weatherError = null
            runCatching { WeatherClient.load(prefs, days, null) }.fold(
                { list ->
                    if (list.isNotEmpty()) {
                        weather = list.associateBy { it.date }
                        prefs.putStr(prefs.tripKey("weather_json"), JSONArray(list.map { it.toJson() }).toString())
                        prefs.putStr(prefs.tripKey("weather_key"), key); prefs.putLong(prefs.tripKey("weather_at"), System.currentTimeMillis())
                    }
                },
                { weatherError = if (weather.isEmpty()) "Weather unavailable offline" else null },
            )
            weatherLoading = false
        }
    }

    // ---- currency ----
    var fxFrom by mutableStateOf(prefs.getStr("fx_pair_from") ?: "USD"); private set
    var fxTo by mutableStateOf(prefs.getStr("fx_pair_to") ?: "PHP"); private set
    var fxRate by mutableStateOf<FxRate?>(null); private set
    var fxError by mutableStateOf<String?>(null); private set
    var fxLoading by mutableStateOf(false); private set

    fun setFxPair(from: String, to: String) {
        fxFrom = from.uppercase().take(3); fxTo = to.uppercase().take(3)
        prefs.putStr("fx_pair_from", fxFrom); prefs.putStr("fx_pair_to", fxTo)
        loadRate()
    }
    fun swapFx() = setFxPair(fxTo, fxFrom)

    fun loadRate() {
        if (!CurrencyClient.valid(fxFrom) || !CurrencyClient.valid(fxTo)) {
            fxRate = null; fxError = "Use 3-letter currency codes like USD or PHP."; return
        }
        viewModelScope.launch {
            fxLoading = true
            CurrencyClient.rate(prefs, fxFrom, fxTo).fold({ fxRate = it; fxError = null }, { fxRate = null; fxError = it.message })
            fxLoading = false
        }
    }

    // ---- document vault ----
    val vault = DocStore(ctx)
    var vaultUnlocked by mutableStateOf(false); private set
    var vaultDocs by mutableStateOf(vault.list()); private set
    fun unlockVault() { vaultUnlocked = true; vaultDocs = vault.list() }
    fun lockVault() { vaultUnlocked = false }
    fun importDoc(uri: android.net.Uri, label: String) {
        viewModelScope.launch { vault.import(uri, label).onFailure { syncMessage = it.message ?: "Couldn't add that file" }; vaultDocs = vault.list() }
    }
    fun deleteDoc(id: String) { vault.delete(id); vaultDocs = vault.list() }

    fun checkAlertsNow() = AlertScheduler.runNow(ctx)

    init {
        viewModelScope.launch { items.first { it.isNotEmpty() }; refreshWeather(); loadRate() }
    }
}
