package com.kl.travel.ui

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.core.content.FileProvider
import java.io.File

private const val MIME = "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"

/** Copies the bundled template out of the APK so other apps (Google Sheets, Drive) can read it. */
internal fun Context.templateUri(): Uri {
    val dir = File(cacheDir, "shared").also { it.mkdirs() }
    val f = File(dir, "KLTravel_Template_v${com.kl.travel.data.Versions.TEMPLATE}.xlsx")
    assets.open("KLTravel_Template.xlsx").use { i -> f.outputStream().use { o -> i.copyTo(o) } }
    return FileProvider.getUriForFile(this, "$packageName.files", f)
}

/**
 * The blank template as a Google Sheet (view-only for anyone with the link, no trip data in it).
 * Opening its /copy address makes Google offer "Make a copy", which lands a fresh blank sheet in the person's own Drive.
 * Keep it in step with Versions.TEMPLATE: when the template changes, update this hosted sheet too.
 */
internal const val TEMPLATE_SHEET_ID = "1MJ4WKVJwA5wH8YEtU49SzjKEl8cdId8kQz94jEKgKLo"
internal const val TEMPLATE_COPY_URL = "https://docs.google.com/spreadsheets/d/$TEMPLATE_SHEET_ID/copy"

/** Opens the blank template's "Make a copy" page in Google Sheets (or the browser). */
fun Context.copyTemplateToSheets() {
    runCatching {
        startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(TEMPLATE_COPY_URL)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }
}

/** Opens the template in Google Sheets (or whatever spreadsheet app the person picks). */
fun Context.openTemplate() {
    val i = Intent(Intent.ACTION_VIEW).setDataAndType(templateUri(), MIME).addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    startActivity(Intent.createChooser(i, "Open template with").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
}

/** Sends the file to Drive, email, etc. */
fun Context.shareTemplate() {
    val i = Intent(Intent.ACTION_SEND).setType(MIME).putExtra(Intent.EXTRA_STREAM, templateUri()).addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    startActivity(Intent.createChooser(i, "Save template to").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
}

internal val TEMPLATE_STEPS = listOf(
    "Make a copy" to "Tap Open in Google Sheets. Google asks you to sign in and tap Make a copy. The blank sheet (no trip data) lands in your own Drive. Rename it to your trip.",
    "Fill it in" to "Add your flights, stays and plans under the headers. The How to use tab explains every column.",
    "Share it" to "Tap Share > General access > Anyone with the link > Viewer. Copy the link.",
    "Paste it here" to "Come back to this screen, paste the link into Sheet link, and tap Save & sync.",
)

/** Writes the backup JSON to a shareable file and opens the share sheet (Drive, Files, email...). */
fun Context.shareBackup(json: String) {
    val dir = File(cacheDir, "shared").also { it.mkdirs() }
    val stamp = java.time.LocalDate.now().toString()
    val f = File(dir, "KLTravel_backup_$stamp.json")
    f.writeText(json)
    val uri = FileProvider.getUriForFile(this, "$packageName.files", f)
    val i = Intent(Intent.ACTION_SEND).setType("application/json").putExtra(Intent.EXTRA_STREAM, uri).addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    startActivity(Intent.createChooser(i, "Save backup to").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
}
