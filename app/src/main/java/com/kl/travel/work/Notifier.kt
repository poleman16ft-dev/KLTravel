package com.kl.travel.work

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.kl.travel.MainActivity
import com.kl.travel.R

object Notifier {
    const val CH_FLIGHT = "flights"
    const val CH_LEAVE = "leave"
    const val EXTRA_ITEM = "itemId"

    fun createChannels(ctx: Context) {
        if (Build.VERSION.SDK_INT < 26) return
        val nm = ctx.getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(NotificationChannel(CH_FLIGHT, "Flight updates", NotificationManager.IMPORTANCE_HIGH)
            .apply { description = "Delays, cancellations, gate and terminal changes" })
        nm.createNotificationChannel(NotificationChannel(CH_LEAVE, "Leave-by alerts", NotificationManager.IMPORTANCE_HIGH)
            .apply { description = "When to leave for your next event, based on live traffic" })
    }

    fun post(ctx: Context, id: Int, channel: String, title: String, text: String, itemId: String?) {
        if (Build.VERSION.SDK_INT >= 33 &&
            ContextCompat.checkSelfPermission(ctx, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) return
        val open = Intent(ctx, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
            itemId?.let { putExtra(EXTRA_ITEM, it) }
        }
        val pi = PendingIntent.getActivity(ctx, id, open, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        val n = NotificationCompat.Builder(ctx, channel)
            .setSmallIcon(R.drawable.ic_stat)
            .setContentTitle(title).setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true).setContentIntent(pi).build()
        try { NotificationManagerCompat.from(ctx).notify(id, n) } catch (_: SecurityException) {}
    }
}
