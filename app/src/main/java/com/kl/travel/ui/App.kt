package com.kl.travel.ui

import android.Manifest
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Modifier
import kotlinx.coroutines.launch
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.ui.unit.dp
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable

@Composable
fun KLRoot(vm: TravelViewModel) {
    val items by vm.items.collectAsState()
    val selected = vm.selectedId?.let { id -> items.firstOrNull { it.id == id } }
    // Bottom tabs are pages: swipe left/right or tap. The pager is the single source of truth for which tab is showing.
    val pager = androidx.compose.foundation.pager.rememberPagerState { 5 }
    val scope = rememberCoroutineScope()
    val tab = pager.currentPage
    fun goTab(i: Int) { scope.launch { pager.animateScrollToPage(i) } }
    // Inside "More": 0 = the menu, 1 = Expenses, 2 = Tools, 3 = Settings
    var more by rememberSaveable { mutableIntStateOf(0) }
    androidx.activity.compose.BackHandler(enabled = tab == 4 && more != 0) { more = 0 }
    val snack = remember { SnackbarHostState() }

    LaunchedEffect(vm.syncMessage) {
        vm.syncMessage?.let { snack.showSnackbar(it); vm.syncMessage = null }
    }
    val notifPerm = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {}
    LaunchedEffect(vm.showSetup) {
        // During first-run setup the alerts step asks for this instead.
        if (!vm.showSetup && Build.VERSION.SDK_INT >= 33) notifPerm.launch(Manifest.permission.POST_NOTIFICATIONS)
    }

    if (vm.showSetup) {
        SetupWizard(vm)
        return
    }

    if (vm.showTrips) {
        TripsScreen(vm)
        return
    }

    if (selected != null) {
        DetailScreen(vm, selected, onBack = vm::close)
        return
    }

    Scaffold(
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        snackbarHost = { SnackbarHost(snack) },
        bottomBar = {
            NavigationBar {
                val tabs = listOf(
                    "Timeline" to Icons.Filled.Schedule, "Transport" to Icons.Filled.Commute, "Activities" to Icons.Filled.Attractions,
                    "Lodging" to Icons.Filled.Hotel, "More" to Icons.Filled.MoreHoriz,
                )
                tabs.forEachIndexed { i, (label, icon) ->
                    NavigationBarItem(selected = tab == i, onClick = { if (i == 4) more = 0; goTab(i) },
                        icon = { Icon(icon, label) }, label = { Text(label) })
                }
            }
        },
    ) { pad ->
        androidx.compose.foundation.pager.HorizontalPager(pager, Modifier.fillMaxSize()) { page ->
        when (page) {
            0 -> TimelineScreen(vm, pad, onOpenSettings = { more = 3; goTab(4) })
            1 -> CategoryScreen(vm, pad, "Transportation", "No flights, boats, drives or trains yet. Set Type to Flight, Transport, Boat, Ferry, Train, Bus or Drive in your sheet.",
                filter = { com.kl.travel.data.Categories.isTransport(it) }, extra = ::transportExtra)
            2 -> CategoryScreen(vm, pad, "Activities", "No activities yet. Rows with Type Activity, Tour, Wedding and similar show up here.",
                filter = { com.kl.travel.data.Categories.isActivity(it) })
            3 -> HotelsScreen(vm, pad)
            else -> when (more) {
                1 -> ExpensesScreen(vm, pad)
                2 -> ToolsScreen(vm, pad)
                3 -> SettingsScreen(vm, pad)
                else -> MoreMenu(pad) { more = it }
            }
        }
        }
    }
}

@Composable
private fun MoreMenu(pad: androidx.compose.foundation.layout.PaddingValues, onPick: (Int) -> Unit) {
    androidx.compose.foundation.layout.Column(
        androidx.compose.ui.Modifier.padding(pad).padding(16.dp),
        verticalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(12.dp),
    ) {
        Text("More", style = MaterialTheme.typography.headlineSmall)
        listOf(
            Triple(1, "Expenses", Icons.Filled.Payments), Triple(2, "Tools", Icons.Filled.Build), Triple(3, "Settings", Icons.Filled.Settings),
        ).forEach { (id, label, icon) ->
            Card(onClick = { onPick(id) }, modifier = androidx.compose.ui.Modifier.fillMaxWidth()) {
                androidx.compose.foundation.layout.Row(
                    androidx.compose.ui.Modifier.padding(16.dp),
                    verticalAlignment = androidx.compose.ui.Alignment.CenterVertically,
                    horizontalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(16.dp),
                ) {
                    Icon(icon, null)
                    Text(label, style = MaterialTheme.typography.titleMedium)
                }
            }
        }
    }
}
