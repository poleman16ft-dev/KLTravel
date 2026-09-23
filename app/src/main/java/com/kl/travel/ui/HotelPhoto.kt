package com.kl.travel.ui

import android.graphics.BitmapFactory
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.kl.travel.net.HotelPhotoClient
import com.kl.travel.net.MapsKey
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

private sealed interface PhotoState {
    data object Loading : PhotoState
    data object None : PhotoState
    data class Ready(val bmp: ImageBitmap, val credit: String?) : PhotoState
    data class Failed(val msg: String) : PhotoState
}

/** Photo of the hotel so it's easy to spot from the street. Cached after the first load. */
@Composable
fun HotelPhoto(name: String, address: String, modifier: Modifier = Modifier) {
    val ctx = LocalContext.current
    val hasKey = MapsKey.effective(ctx) != null
    var state by remember(name, address, hasKey) { mutableStateOf<PhotoState>(PhotoState.Loading) }

    LaunchedEffect(name, address, hasKey) {
        if (!hasKey) { state = PhotoState.None; return@LaunchedEffect }
        state = withContext(Dispatchers.IO) {
            HotelPhotoClient.fetch(ctx, name, address).fold(
                onSuccess = { p ->
                    val bmp = p?.let { decode(it.file.path) }
                    if (p != null && bmp != null) PhotoState.Ready(bmp.asImageBitmap(), p.credit) else PhotoState.None
                },
                onFailure = { PhotoState.Failed(it.message ?: "Couldn't load the lodging photo.") },
            )
        }
    }

    when (val s = state) {
        is PhotoState.Ready -> Column(modifier, verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Image(s.bmp, "Photo of $name", Modifier.fillMaxWidth().height(190.dp).clip(RoundedCornerShape(12.dp)), contentScale = ContentScale.Crop)
            Text("Photo: ${s.credit ?: "Google Maps"}", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        is PhotoState.Failed -> Text(if (s.msg.contains("limit reached")) s.msg else s.msg + " (Lodging photos need Places API (New) enabled on your key.)", modifier,
            style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.error)
        PhotoState.Loading, PhotoState.None -> Unit
    }
}

private fun decode(path: String): android.graphics.Bitmap? {
    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    BitmapFactory.decodeFile(path, bounds)
    var sample = 1
    while (bounds.outWidth / sample > 1600) sample *= 2
    return BitmapFactory.decodeFile(path, BitmapFactory.Options().apply { inSampleSize = sample })
}
