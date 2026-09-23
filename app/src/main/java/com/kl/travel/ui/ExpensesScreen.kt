package com.kl.travel.ui

import android.graphics.BitmapFactory
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.PhotoCamera
import androidx.compose.material.icons.filled.Receipt
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.kl.travel.data.ExpenseEntity
import com.kl.travel.receipt.ReceiptFiles
import java.io.File
import java.time.LocalDate
import java.util.Locale

private val CATEGORIES = listOf("Food", "Transport", "Lodging", "Activity", "Shopping", "Other")

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun ExpensesScreen(vm: TravelViewModel, pad: PaddingValues) {
    val ctx = LocalContext.current
    val expenses by vm.expenses.collectAsState()
    val planned by vm.plannedTotal.collectAsState()
    var showAdd by remember { mutableStateOf(false) }
    var pickSource by remember { mutableStateOf(false) }
    var viewing by remember { mutableStateOf<ExpenseEntity?>(null) }
    val spent = expenses.sumOf { it.amount }
    val today = LocalDate.now().toString()
    val spentToday = expenses.filter { it.date == today }.sumOf { it.amount }

    // Camera writes into a file inside the app; the gallery picker hands back a photo we copy in.
    var pending by remember { mutableStateOf<File?>(null) }
    val camera = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { ok ->
        val f = pending; pending = null
        if (ok && f != null) vm.scanReceipt(f) else f?.delete()
    }
    val gallery = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri -> if (uri != null) vm.scanFromGallery(uri) }

    Column(Modifier.padding(pad)) {
        TopAppBar(title = { Text("Expenses") }, actions = {
            IconButton(onClick = { pickSource = true }) { Icon(Icons.Filled.PhotoCamera, "Scan a receipt") }
            IconButton(onClick = { showAdd = true }) { Icon(Icons.Filled.Add, "Add expense") }
        })
        if (vm.scanning) LinearProgressIndicator(Modifier.fillMaxWidth())
        LazyColumn(contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            item {
                Card(Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        SummaryLine("Planned (from sheet)", money(planned))
                        SummaryLine("Spent so far", money(spent))
                        SummaryLine("Spent today", money(spentToday))
                        HorizontalDivider()
                        val left = planned - spent
                        SummaryLine(if (left >= 0) "Left in plan" else "Over plan", money(kotlin.math.abs(left)), bold = true)
                    }
                }
            }
            if (expenses.isNotEmpty()) item {
                Card(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text("Spent by category", fontWeight = FontWeight.Bold)
                        expenses.groupBy { it.category }.map { (c, l) -> Triple(c, l.sumOf { it.amount }, l.size) }
                            .sortedByDescending { it.second }
                            .forEach { (c, sum, n) -> SummaryLine("$c ($n)", money(sum)) }
                        val receipts = expenses.count { it.receiptPath.isNotBlank() }
                        if (receipts > 0) Text("$receipts receipt photo${if (receipts == 1) "" else "s"} saved on this phone",
                            style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
            if (expenses.isEmpty()) item {
                Text("Nothing logged yet. Tap the camera to scan a receipt, or + to type an expense.", Modifier.padding(top = 16.dp), style = MaterialTheme.typography.bodyMedium)
            }
            items(expenses) { e ->
                Row(Modifier.fillMaxWidth().clickable(enabled = e.receiptPath.isNotBlank()) { viewing = e }, verticalAlignment = Alignment.CenterVertically) {
                    if (e.receiptPath.isNotBlank()) Icon(Icons.Filled.Receipt, "Has receipt photo", Modifier.padding(end = 8.dp), tint = MaterialTheme.colorScheme.primary)
                    Column(Modifier.weight(1f)) {
                        Text("${e.category}${if (e.note.isNotBlank()) " · ${e.note}" else ""}", fontWeight = FontWeight.Medium)
                        val orig = if (e.currency != "USD" && e.originalAmount > 0) " · ${e.currency} ${String.format(Locale.US, "%,.2f", e.originalAmount)}" else ""
                        Text(LocalDate.parse(e.date).format(DAY_FMT) + orig, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Text(money(e.amount), fontWeight = FontWeight.Bold)
                    IconButton(onClick = { vm.deleteExpense(e) }) { Icon(Icons.Filled.Delete, "Delete") }
                }
                HorizontalDivider(color = MaterialTheme.colorScheme.surfaceVariant)
            }
        }
    }

    if (pickSource) AlertDialog(
        onDismissRequest = { pickSource = false },
        title = { Text("Scan a receipt") },
        text = { Text("Take a photo of the receipt, or pick one from your gallery. The app reads the merchant, date and total on your phone, then you check it and save. The photo stays on this phone.") },
        confirmButton = {
            TextButton(onClick = {
                pickSource = false
                val f = ReceiptFiles.newFile(ctx); pending = f
                camera.launch(ReceiptFiles.uri(ctx, f))
            }) { Text("Take photo") }
        },
        dismissButton = { TextButton(onClick = { pickSource = false; gallery.launch("image/*") }) { Text("From gallery") } },
    )

    vm.receiptDraft?.let { ReceiptReview(vm, it) }

    viewing?.let { e ->
        AlertDialog(
            onDismissRequest = { viewing = null },
            title = { Text(if (e.note.isNotBlank()) e.note else e.category) },
            text = {
                Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("${e.category} · ${LocalDate.parse(e.date).format(DAY_FMT)} · ${money(e.amount)}" +
                        if (e.currency != "USD" && e.originalAmount > 0) " (${e.currency} ${String.format(Locale.US, "%,.2f", e.originalAmount)})" else "")
                    ReceiptImage(e.receiptPath, Modifier.fillMaxWidth(), fit = true)
                }
            },
            confirmButton = { TextButton(onClick = { viewing = null }) { Text("Close") } },
        )
    }

    if (showAdd) {
        var amount by remember { mutableStateOf("") }
        var note by remember { mutableStateOf("") }
        var cat by remember { mutableStateOf(CATEGORIES[0]) }
        AlertDialog(
            onDismissRequest = { showAdd = false },
            title = { Text("Add expense") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(amount, { amount = it }, label = { Text("Amount (USD)") }, singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal))
                    OutlinedTextField(note, { note = it }, label = { Text("Note (optional)") }, singleLine = true)
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        CATEGORIES.forEach { c -> FilterChip(selected = cat == c, onClick = { cat = c }, label = { Text(c) }) }
                    }
                }
            },
            confirmButton = {
                TextButton(enabled = amount.toDoubleOrNull() != null, onClick = {
                    vm.addExpense(cat, amount.toDouble(), note.trim()); showAdd = false
                }) { Text("Save") }
            },
            dismissButton = { TextButton(onClick = { showAdd = false }) { Text("Cancel") } },
        )
    }
}

/** Check what was read from the photo, fix anything, then save. Foreign amounts are converted to USD (you can override). */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ReceiptReview(vm: TravelViewModel, d: TravelViewModel.ReceiptDraft) {
    val p = d.parsed
    var merchant by remember(d) { mutableStateOf(p.merchant) }
    var date by remember(d) { mutableStateOf((p.date ?: LocalDate.now()).toString()) }
    var cat by remember(d) { mutableStateOf(p.category.takeIf { it in CATEGORIES } ?: "Other") }
    var amount by remember(d) { mutableStateOf(p.total?.let { String.format(Locale.US, "%.2f", it) } ?: "") }
    var currency by remember(d) { mutableStateOf(p.currency.ifBlank { "USD" }) }
    var usd by remember(d) { mutableStateOf("") }
    var usdEdited by remember(d) { mutableStateOf(false) }
    var fxNote by remember(d) { mutableStateOf<String?>(null) }

    val amt = amount.replace(",", "").toDoubleOrNull()
    val cur = currency.trim().uppercase()
    LaunchedEffect(amt, cur) {
        if (usdEdited) return@LaunchedEffect
        if (amt == null || cur.length != 3) { usd = ""; return@LaunchedEffect }
        if (cur == "USD") { usd = String.format(Locale.US, "%.2f", amt); fxNote = null; return@LaunchedEffect }
        fxNote = "Converting…"
        vm.toUsd(amt, cur).fold(
            { usd = String.format(Locale.US, "%.2f", it); fxNote = "Converted at today's rate. Change it if your card charged something different." },
            { usd = ""; fxNote = "Couldn't get a rate: type the USD amount yourself." },
        )
    }
    val usdValue = usd.replace(",", "").toDoubleOrNull()
    val dateValue = runCatching { LocalDate.parse(date.trim()) }.getOrNull()

    AlertDialog(
        onDismissRequest = { vm.discardReceipt() },
        title = { Text("Check this receipt") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                if (!d.readOk) Text("Couldn't read any text on this photo. The photo is still saved: fill in the details below.", color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                else if (p.total == null) Text("Read the receipt but couldn't find the total. Please type it.", color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                ReceiptImage(d.path, Modifier.fillMaxWidth().height(160.dp), fit = false)
                OutlinedTextField(merchant, { merchant = it }, label = { Text("Merchant / note") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(date, { date = it }, label = { Text("Date (YYYY-MM-DD)") }, singleLine = true, modifier = Modifier.fillMaxWidth(),
                    isError = dateValue == null)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(amount, { amount = it; usdEdited = false }, label = { Text("Total on receipt") }, singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), modifier = Modifier.weight(1f))
                    OutlinedTextField(currency, { currency = it.take(3); usdEdited = false }, label = { Text("Currency") }, singleLine = true, modifier = Modifier.width(96.dp))
                }
                OutlinedTextField(usd, { usd = it; usdEdited = true }, label = { Text("Amount in USD") }, singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), modifier = Modifier.fillMaxWidth())
                fxNote?.let { Text(it, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    CATEGORIES.forEach { c -> FilterChip(selected = cat == c, onClick = { cat = c }, label = { Text(c) }) }
                }
            }
        },
        confirmButton = {
            TextButton(enabled = usdValue != null && dateValue != null, onClick = {
                vm.saveReceipt(cat, usdValue!!, merchant.trim(), dateValue!!, if (cur.length == 3) cur else "USD", if (cur == "USD") 0.0 else (amt ?: 0.0))
            }) { Text("Save") }
        },
        dismissButton = { TextButton(onClick = { vm.discardReceipt() }) { Text("Discard") } },
    )
}

@Composable
private fun ReceiptImage(path: String, modifier: Modifier, fit: Boolean) {
    val bmp = remember(path) { runCatching { BitmapFactory.decodeFile(path) }.getOrNull() }
    if (bmp == null) Text("Photo not found on this phone.", style = MaterialTheme.typography.bodySmall)
    else Image(bmp.asImageBitmap(), "Receipt photo", modifier, contentScale = if (fit) ContentScale.FillWidth else ContentScale.Fit)
}

@Composable
private fun SummaryLine(label: String, value: String, bold: Boolean = false) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(label, fontWeight = if (bold) FontWeight.Bold else FontWeight.Normal)
        Text(value, fontWeight = if (bold) FontWeight.Bold else FontWeight.Medium)
    }
}
