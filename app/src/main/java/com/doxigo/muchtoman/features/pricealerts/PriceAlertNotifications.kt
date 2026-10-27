package com.doxigo.muchtoman.features.pricealerts

import android.annotation.SuppressLint
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.doxigo.muchtoman.MainActivity
import com.doxigo.muchtoman.R
import com.doxigo.muchtoman.features.reminders.NotificationPermission

/**
 * The one notification this feature posts. Its own channel, so she can mute price alerts without
 * muting due-date reminders, and gated on the same [NotificationPermission] the reminders use —
 * this feature never asks for the permission on its own schedule; the sheet asks when she adds
 * an alert.
 */
object PriceAlertNotifications {

    const val CHANNEL_ID = "price_alerts"
    const val CHANNEL_NAME = "هشدار قیمت"
    const val EXTRA_ALERT_ID = "com.doxigo.muchtoman.extra.PRICE_ALERT_ID"
    const val EXTRA_ASSET_ID = "com.doxigo.muchtoman.extra.PRICE_ALERT_ASSET_ID"

    fun ensureChannel(context: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager
            ?: return
        if (manager.getNotificationChannel(CHANNEL_ID) != null) return
        manager.createNotificationChannel(
            NotificationChannel(CHANNEL_ID, CHANNEL_NAME, NotificationManager.IMPORTANCE_DEFAULT).apply {
                description = "وقتی قیمت یک دارایی به عددی که گذاشتی برسد؛ هر هشدار فقط یک بار."
                setShowBadge(true)
            },
        )
    }

    /** Whether a price alert posted now would reach the shade. */
    fun canPost(context: Context): Boolean {
        if (!NotificationPermission.isGranted(context)) return false
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager
            val channel = manager?.getNotificationChannel(CHANNEL_ID)
            if (channel != null && channel.importance == NotificationManager.IMPORTANCE_NONE) return false
        }
        return true
    }

    @SuppressLint("MissingPermission") // guarded by canPost
    fun post(context: Context, alert: PriceAlert): Boolean {
        ensureChannel(context)
        if (!canPost(context)) return false
        val text = PriceAlertRules.message(alert)
        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_toman)
            .setContentTitle(CHANNEL_NAME)
            .setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setContentIntent(openIntent(context, alert))
            .setAutoCancel(true)
            .setCategory(NotificationCompat.CATEGORY_STATUS)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            // A price is not private the way a cheque is, but what she watches says what she
            // holds; same lock-screen rule as the reminders.
            .setVisibility(NotificationCompat.VISIBILITY_PRIVATE)
            .build()
        return runCatching {
            NotificationManagerCompat.from(context).notify(notificationId(alert.id), notification)
        }.isSuccess
    }

    private fun openIntent(context: Context, alert: PriceAlert): PendingIntent {
        val launch = context.packageManager.getLaunchIntentForPackage(context.packageName)
            ?: Intent(context, MainActivity::class.java)
        launch.putExtra(EXTRA_ALERT_ID, alert.id)
        launch.putExtra(EXTRA_ASSET_ID, alert.assetId)
        launch.addFlags(
            Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP,
        )
        return PendingIntent.getActivity(
            context,
            notificationId(alert.id),
            launch,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
    }

    private fun notificationId(id: String): Int = ("price:$id").hashCode() and 0x7fffffff
}
