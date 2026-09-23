package com.kl.travel.receipt

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.net.Uri
import androidx.core.content.FileProvider
import androidx.exifinterface.media.ExifInterface
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import kotlinx.coroutines.suspendCancellableCoroutine
import java.io.File
import kotlin.coroutines.resume

/** Receipt photos live inside the app (never uploaded anywhere); text is read on the phone. */
object ReceiptFiles {
    fun dir(ctx: Context) = File(ctx.filesDir, "receipts").also { it.mkdirs() }
    fun newFile(ctx: Context) = File(dir(ctx), "rcpt_${System.currentTimeMillis()}.jpg")
    fun uri(ctx: Context, f: File): Uri = FileProvider.getUriForFile(ctx, "${ctx.packageName}.files", f)

    fun copyFrom(ctx: Context, src: Uri, dest: File): Boolean = runCatching {
        ctx.contentResolver.openInputStream(src)!!.use { i -> dest.outputStream().use { o -> i.copyTo(o) } }
        true
    }.getOrDefault(false)

    /** Downsizes to at most [maxSide] pixels and bakes in the rotation, so a photo takes ~300 KB instead of ~4 MB. */
    fun shrink(f: File, maxSide: Int = 1800) {
        runCatching {
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeFile(f.path, bounds)
            var sample = 1
            while (maxOf(bounds.outWidth, bounds.outHeight) / sample > maxSide * 2) sample *= 2
            val bmp = BitmapFactory.decodeFile(f.path, BitmapFactory.Options().apply { inSampleSize = sample }) ?: return
            val rot = when (ExifInterface(f.path).getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL)) {
                ExifInterface.ORIENTATION_ROTATE_90 -> 90f
                ExifInterface.ORIENTATION_ROTATE_180 -> 180f
                ExifInterface.ORIENTATION_ROTATE_270 -> 270f
                else -> 0f
            }
            val scale = minOf(1f, maxSide.toFloat() / maxOf(bmp.width, bmp.height))
            val out = Bitmap.createBitmap(bmp, 0, 0, bmp.width, bmp.height, Matrix().apply { postRotate(rot); postScale(scale, scale) }, true)
            f.outputStream().use { out.compress(Bitmap.CompressFormat.JPEG, 85, it) }
        }
    }

    fun delete(path: String) { if (path.isNotBlank()) runCatching { File(path).delete() } }
}

object ReceiptOcr {
    /** Reads all the text on the photo. Works offline (the model ships inside the app). */
    suspend fun recognize(ctx: Context, file: File): Result<String> = suspendCancellableCoroutine { cont ->
        val recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
        try {
            val image = InputImage.fromFilePath(ctx, Uri.fromFile(file))
            recognizer.process(image)
                .addOnSuccessListener { if (cont.isActive) cont.resume(Result.success(it.text)); recognizer.close() }
                .addOnFailureListener { if (cont.isActive) cont.resume(Result.failure(it)); recognizer.close() }
        } catch (e: Exception) {
            recognizer.close()
            if (cont.isActive) cont.resume(Result.failure(e))
        }
    }
}
