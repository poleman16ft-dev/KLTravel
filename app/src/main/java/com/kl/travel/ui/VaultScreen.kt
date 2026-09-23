package com.kl.travel.ui

import android.app.Activity
import android.app.KeyguardManager
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.pdf.PdfRenderer
import android.os.ParcelFileDescriptor
import android.view.WindowManager
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.kl.travel.data.DocStore
import com.kl.travel.data.VaultDoc
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

private fun Context.keyguard() = getSystemService(Context.KEYGUARD_SERVICE) as KeyguardManager

/** Passport / visa / ticket vault. Needs the phone's screen lock (PIN, pattern, password or fingerprint) to open. */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun VaultScreen(vm: TravelViewModel, pad: PaddingValues, onClose: () -> Unit) {
    val ctx = LocalContext.current
    var viewing by remember { mutableStateOf<VaultDoc?>(null) }
    var label by remember { mutableStateOf(DocStore.LABELS.first()) }
    var confirmDelete by remember { mutableStateOf<VaultDoc?>(null) }
    val secure = remember { ctx.keyguard().isDeviceSecure }

    // Block screenshots and the recent-apps thumbnail while documents are on screen.
    DisposableEffect(vm.vaultUnlocked) {
        val w = (ctx as? Activity)?.window
        if (vm.vaultUnlocked) w?.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
        onDispose { w?.clearFlags(WindowManager.LayoutParams.FLAG_SECURE) }
    }

    val unlock = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { r ->
        if (r.resultCode == Activity.RESULT_OK) vm.unlockVault()
    }
    fun askUnlock() {
        @Suppress("DEPRECATION")
        val i = ctx.keyguard().createConfirmDeviceCredentialIntent("Unlock documents", "Confirm your screen lock to open your travel documents.")
        if (i != null) unlock.launch(i)
    }
    LaunchedEffect(secure) { if (secure && !vm.vaultUnlocked) askUnlock() }

    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) vm.importDoc(uri, label)
    }

    BackHandler { if (viewing != null) viewing = null else onClose() }

    Column(Modifier.padding(pad)) {
        TopAppBar(
            title = { Text(viewing?.let { "${it.label}: ${it.name}" } ?: "Documents") },
            navigationIcon = { IconButton(onClick = { if (viewing != null) viewing = null else onClose() }) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") } },
        )
        when {
            !secure -> Message("Set a screen lock first",
                "Documents are only shown after you confirm your phone's PIN, pattern, password or fingerprint. Set one in Android Settings > Security, then come back.")
            !vm.vaultUnlocked -> Column(Modifier.fillMaxSize().padding(32.dp), verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally) {
                Icon(Icons.Filled.Lock, null, Modifier.size(48.dp), tint = MaterialTheme.colorScheme.primary)
                Spacer(Modifier.height(12.dp))
                Text("Documents are locked", style = MaterialTheme.typography.titleMedium)
                Spacer(Modifier.height(12.dp))
                Button(onClick = ::askUnlock) { Text("Unlock") }
            }
            viewing != null -> DocViewer(vm.vault, viewing!!)
            else -> Column(Modifier.verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("Stored only on this phone. Not synced, not in cloud backups, never sent to your Google Sheet.",
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    DocStore.LABELS.forEach { l -> FilterChip(selected = label == l, onClick = { label = l }, label = { Text(l) }) }
                }
                Button(onClick = { picker.launch(arrayOf("application/pdf", "image/*")) }) { Text("Add $label (PDF or photo)") }
                HorizontalDivider()
                if (vm.vaultDocs.isEmpty()) Text("Nothing here yet.", style = MaterialTheme.typography.bodyMedium)
                vm.vaultDocs.forEach { d ->
                    val added = Instant.ofEpochMilli(d.addedAt).atZone(ZoneId.systemDefault()).format(DateTimeFormatter.ofPattern("MMM d, yyyy", Locale.US))
                    Card(onClick = { viewing = d }, modifier = Modifier.fillMaxWidth()) {
                        Row(Modifier.padding(start = 16.dp, top = 8.dp, bottom = 8.dp, end = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) {
                                Text(d.label, style = MaterialTheme.typography.titleSmall)
                                Text("${d.name} · added $added", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            IconButton(onClick = { confirmDelete = d }) { Icon(Icons.Filled.Delete, "Delete") }
                        }
                    }
                }
            }
        }
    }

    confirmDelete?.let { d ->
        AlertDialog(
            onDismissRequest = { confirmDelete = null },
            title = { Text("Delete ${d.label}?") },
            text = { Text("This removes \"${d.name}\" from the phone. It can't be undone.") },
            confirmButton = { TextButton(onClick = { vm.deleteDoc(d.id); confirmDelete = null }) { Text("Delete") } },
            dismissButton = { TextButton(onClick = { confirmDelete = null }) { Text("Cancel") } },
        )
    }
}

@Composable
private fun Message(title: String, body: String) {
    Column(Modifier.fillMaxSize().padding(32.dp), verticalArrangement = Arrangement.Center) {
        Text(title, style = MaterialTheme.typography.titleMedium)
        Spacer(Modifier.height(8.dp))
        Text(body, style = MaterialTheme.typography.bodyMedium)
    }
}

@Composable
private fun DocViewer(store: DocStore, doc: VaultDoc) {
    val pages by produceState<List<Bitmap>?>(null, doc.id) {
        value = withContext(Dispatchers.IO) { runCatching { render(store, doc) }.getOrDefault(emptyList()) }
    }
    when {
        pages == null -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
        pages!!.isEmpty() -> Message("Couldn't open this file", "It may be damaged or password protected.")
        else -> LazyColumn(contentPadding = PaddingValues(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            items(pages!!) { b -> Image(b.asImageBitmap(), doc.name, Modifier.fillMaxWidth(), contentScale = ContentScale.FillWidth) }
        }
    }
}

private const val MAX_PAGES = 12
private const val TARGET_W = 1200

private fun render(store: DocStore, doc: VaultDoc): List<Bitmap> {
    val f = store.file(doc.id)
    if (!doc.isPdf) {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(f.path, bounds)
        var sample = 1
        while (bounds.outWidth / (sample * 2) >= TARGET_W) sample *= 2
        return listOfNotNull(BitmapFactory.decodeFile(f.path, BitmapFactory.Options().apply { inSampleSize = sample }))
    }
    return ParcelFileDescriptor.open(f, ParcelFileDescriptor.MODE_READ_ONLY).use { fd ->
        PdfRenderer(fd).use { r ->
            (0 until minOf(r.pageCount, MAX_PAGES)).map { i ->
                r.openPage(i).use { p ->
                    val h = (TARGET_W.toFloat() * p.height / p.width).toInt()
                    Bitmap.createBitmap(TARGET_W, h, Bitmap.Config.ARGB_8888).also {
                        it.eraseColor(android.graphics.Color.WHITE)
                        p.render(it, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                    }
                }
            }
        }
    }
}
