package com.kl.travel.ui

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.kl.travel.data.TripItem
import com.kl.travel.net.TransportImages
import com.kl.travel.net.TransportPhotoClient
import com.kl.travel.net.UserPhotos
import com.kl.travel.work.FlightUpdater
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

internal const val TP_IMAGE = "transportPhoto"
internal const val TP_PICK = "transportPhotoPick"
internal const val TP_RESET = "transportPhotoReset"
internal const val TP_HINT = "transportPhotoHint"

/** What the picture area shows. */
sealed interface TransportShot {
    data object Loading : TransportShot
    /** Nothing to show (a taxi, say). The card only offers "add my photo". */
    data object Empty : TransportShot
    data class Hint(val text: String) : TransportShot
    data class Failed(val msg: String) : TransportShot
    data class Ready(val bmp: ImageBitmap, val caption: String, val credit: String?, val mine: Boolean) : TransportShot
}

/** Stateless: easy to test. [onPick] opens the photo picker; [onReset] appears only when your own photo is showing. */
@Composable
fun TransportPhotoView(shot: TransportShot, onPick: (() -> Unit)?, onReset: (() -> Unit)?, modifier: Modifier = Modifier) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(2.dp)) {
        when (shot) {
            is TransportShot.Ready -> {
                Image(shot.bmp, shot.caption, Modifier.fillMaxWidth().height(190.dp).clip(RoundedCornerShape(12.dp)).testTag(TP_IMAGE),
                    contentScale = ContentScale.Crop)
                Text(shot.caption, style = MaterialTheme.typography.labelMedium)
                Text(
                    if (shot.mine) "Your own photo" else "Example photo, your actual vehicle may differ. Photo: ${shot.credit.orEmpty()}",
                    style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            is TransportShot.Hint -> Text(shot.text, Modifier.testTag(TP_HINT), style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
            is TransportShot.Failed -> Text(shot.msg, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            TransportShot.Loading, TransportShot.Empty -> Unit
        }
        if (shot !is TransportShot.Loading) Row {
            if (onPick != null) TextButton(onClick = onPick, modifier = Modifier.testTag(TP_PICK)) {
                Text(if (shot is TransportShot.Ready) "Use a different photo" else "Add my own photo")
            }
            if (onReset != null) TextButton(onClick = onReset, modifier = Modifier.testTag(TP_RESET)) { Text("Reset") }
        }
    }
}

/** Picture of the plane, ferry, train or your own vehicle for a transport row. Your own photo (per row) always wins. */
@Composable
fun TransportPhotoCard(vm: TravelViewModel, item: TripItem, modifier: Modifier = Modifier) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    var tick by remember { mutableIntStateOf(0) }
    val livePlane = remember(item.id, tick) { FlightUpdater.plane(vm.prefs, item.id) }
    val vehicle = vm.prefs.myVehicle
    val source = remember(item, livePlane) { TransportImages.forItem(item, livePlane) }
    var shot by remember(item.id) { mutableStateOf<TransportShot>(TransportShot.Loading) }

    val itemFile = remember(item.id) { UserPhotos.itemFile(ctx, item.id) }
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) scope.launch { if (UserPhotos.save(ctx, uri, itemFile)) tick++ }
    }

    LaunchedEffect(item.id, source, vehicle, tick) {
        shot = withContext(Dispatchers.IO) { resolve(ctx, itemFile, source, vehicle) }
    }

    TransportPhotoView(
        shot,
        onPick = { picker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) },
        onReset = if (UserPhotos.has(itemFile)) ({ UserPhotos.delete(itemFile); tick++ }) else null,
        modifier = modifier,
    )
}

private suspend fun resolve(ctx: android.content.Context, itemFile: java.io.File, source: TransportImages.Source, vehicle: String): TransportShot {
    if (UserPhotos.has(itemFile)) decodeScaled(itemFile.path)?.let { return TransportShot.Ready(it.asImageBitmap(), "Your photo", null, mine = true) }
    return when (source) {
        TransportImages.Source.None -> TransportShot.Empty
        TransportImages.Source.MyVehicle -> {
            val mine = UserPhotos.vehicleFile(ctx)
            val plan = TransportImages.vehiclePlan(vehicle)
            when {
                UserPhotos.has(mine) -> decodeScaled(mine.path)?.let { TransportShot.Ready(it.asImageBitmap(), vehicle.ifBlank { "My vehicle" }, null, mine = true) }
                    ?: TransportShot.Empty
                plan != null -> fromPlan(ctx, plan)
                else -> TransportShot.Hint("Driving yourself? Add your vehicle (type or photo) in Settings > My vehicle to see it here.")
            }
        }
        is TransportImages.Source.Search -> fromPlan(ctx, source.plan)
    }
}

private suspend fun fromPlan(ctx: android.content.Context, plan: TransportImages.Plan): TransportShot =
    TransportPhotoClient.fetch(ctx, plan).fold(
        onSuccess = { p ->
            val bmp = p?.let { decodeScaled(it.file.path) }
            if (p != null && bmp != null) TransportShot.Ready(bmp.asImageBitmap(), p.label, p.credit, mine = false) else TransportShot.Empty
        },
        onFailure = { TransportShot.Failed("Couldn't load a photo (no connection?). It will be saved once it loads.") },
    )

internal fun decodeScaled(path: String): Bitmap? {
    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    BitmapFactory.decodeFile(path, bounds)
    var sample = 1
    while (bounds.outWidth / sample > 1600) sample *= 2
    return BitmapFactory.decodeFile(path, BitmapFactory.Options().apply { inSampleSize = sample })
}
