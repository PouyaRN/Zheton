package com.doxigo.muchtoman.features.reminders

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
import com.doxigo.muchtoman.faNumber
import com.doxigo.muchtoman.formatShamsiDate
import com.doxigo.muchtoman.tehranDayStart

/**
 * Building and showing the two notifications this feature has: a due reminder and the daily one.
 *
 * Two rules shape everything in this file:
 *
 *  * **The content is financial.** The channel is private and every notification is
 *    `VISIBILITY_PRIVATE`, so the lock screen shows the app instead of the sentence. The phone is
 *    shared in real households, and a cheque's amount or provider is not something to learn from
 *    a glance at the table.
 *  * **Tapping must go somewhere useful.** Every notification carries an explicit «باز کردن»
 *    action and a due reminder also carries «رسیدگی شد»; the second one is the difference
 *    between a notification and a to-do that keeps coming back.
 *
 * Nothing here schedules anything. WorkManager decides when, [ReminderPlanner] decides what, and
 * this file only turns a [ReminderFire] into pixels.
 */
object ReminderNotifications {

    /** One channel for everything: a second channel would only be noise in the system settings. */
    const val CHANNEL_ID = "reminders"

    const val CHANNEL_NAME = "یادآورها"

    /**
     * The dedupe key of the reminder this notification opened.
     *
     * The app can read it in `onCreate`/`onNewIntent` to scroll to the right cheque. Not required
     * for the notification to work, which is why it is only an extra on the launch intent.
     */
    const val EXTRA_REMINDER_KEY = "com.doxigo.muchtoman.extra.REMINDER_KEY"

    /** The [ReminderItem.id] behind a due reminder, understood by the receiver and the app. */
    const val EXTRA_REMINDER_ITEM_ID = "com.doxigo.muchtoman.extra.REMINDER_ITEM_ID"

    /** Broadcast action for «رسیدگی شد». The receiver is registered in the manifest. */
    const val ACTION_MARK_HANDLED = "com.doxigo.muchtoman.action.REMINDER_HANDLED"

    const val EXTRA_KEY = "com.doxigo.muchtoman.extra.REMINDER_DEDUPE_KEY"

    /** A test notification has no dedupe key and is never recorded as fired. */
    const val TEST_KEY = "test"

    private const val TEST_NOTIFICATION_ID = 1_060_001

    /**
     * Create the channel if it is missing.
     *
     * The existence check is deliberate: calling `createNotificationChannel` again is harmless on
     * paper, but the check documents that the user's own channel settings are the authority here
     * and this method must never reset them.
     */
    fun ensureChannel(context: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager
            ?: return
        if (manager.getNotificationChannel(CHANNEL_ID) != null) return
        val channel = NotificationChannel(
            CHANNEL_ID,
            CHANNEL_NAME,
            // DEFAULT, not HIGH: a due date three days away is not an emergency, and a
            // heads-up popup over someone's banking app is exactly the behaviour that makes
            // people revoke the permission.
            NotificationManager.IMPORTANCE_DEFAULT,
        ).apply {
            description = "سررسید اقساط، چک‌ها و وام‌ها؛ فقط روی همین گوشی ساخته می‌شود."
            setShowBadge(true)
            enableVibration(true)
        }
        manager.createNotificationChannel(channel)
    }

    /**
     * Show one reminder. Returns whether it was actually handed to the notification manager, which
     * is what decides whether the dedupe key is recorded — a notification the system refused must
     * be retried later, not marked as delivered.
     */
    fun post(context: Context, fire: ReminderFire, dueSoonCount: Int = 0): Boolean {
        if (!NotificationPermission.isGranted(context)) return false
        ensureChannel(context)
        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_toman)
            .setContentTitle(title(fire))
            .setContentText(body(fire, dueSoonCount))
            .setStyle(NotificationCompat.BigTextStyle().bigText(body(fire, dueSoonCount)))
            .setContentIntent(openIntent(context, fire.key))
            .setAutoCancel(true)
            .setOnlyAlertOnce(true)
            .setCategory(NotificationCompat.CATEGORY_REMINDER)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            // Financial data on a shared phone: the lock screen names the app, not the cheque.
            .setVisibility(NotificationCompat.VISIBILITY_PRIVATE)
            .setWhen(fire.atMillis)
            .setShowWhen(true)
            .addAction(0, "باز کردن", openIntent(context, fire.key))
            .apply {
                if (!fire.daily) {
                    addAction(0, "رسیدگی شد", handledIntent(context, fire))
                }
            }
            .build()
        return runCatching {
            NotificationManagerCompat.from(context).notify(notificationId(fire.key), notification)
        }.isSuccess
    }

    /**
     * A notification that proves the plumbing without waiting for a due date.
     *
     * It deliberately ignores quiet hours: the test is a button she pressed, not a reminder the
     * app decided to show her. It does not ignore a switched-off permission or a muted channel —
     * pretending it worked would be worse than saying it did not.
     */
    fun postTest(context: Context): Boolean {
        if (!NotificationPermission.isGranted(context)) return false
        ensureChannel(context)
        val text = "اگر این را می‌بینی، یادآورها کار می‌کنند. " +
            "یادآورها فقط روی همین گوشی ساخته می‌شوند و جایی فرستاده نمی‌شوند."
        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_toman)
            .setContentTitle("اعلان آزمایشی")
            .setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setContentIntent(openIntent(context, TEST_KEY))
            .setAutoCancel(true)
            .setCategory(NotificationCompat.CATEGORY_REMINDER)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setVisibility(NotificationCompat.VISIBILITY_PRIVATE)
            .build()
        return runCatching {
            NotificationManagerCompat.from(context).notify(TEST_NOTIFICATION_ID, notification)
        }.isSuccess
    }

    /** Remove the notification for a reminder that was handled from the shade. */
    fun cancel(context: Context, key: String) {
        runCatching {
            NotificationManagerCompat.from(context).cancel(notificationId(key))
        }
    }

    // ─────────────────────────── copy ───────────────────────────

    private fun title(fire: ReminderFire): String =
        if (fire.daily) {
            "یادآور روزانه"
        } else {
            "یادآوری ${fire.item?.kind?.fa ?: ReminderKind.OTHER.fa}"
        }

    /**
     * The one sentence the notification exists for.
     *
     * The daily line is not a second copy of the due-date reminders: it is the app's daily
     * check-in — log today's transactions and see what is coming — which is why it still appears
     * on a day with nothing due. It names the nearest due date when there is one and otherwise
     * says so plainly, because "no news" is also worth a sentence in an app about money.
     */
    private fun body(fire: ReminderFire, dueSoonCount: Int): String {
        if (fire.daily) {
            val item = fire.item
                ?: return "تراکنش‌های امروزت را ثبت کن. امروز سررسیدی نداری."
            val date = formatShamsiDate(tehranDayStart(item.dueDay))
            val days = fire.daysUntilDue ?: 0
            val relative = when {
                days > 0 -> "${faNumber(days)} روز دیگر"
                days == 0 -> "امروز"
                else -> "${faNumber(-days)} روز گذشته"
            }
            val due = if (date.isBlank()) relative else "$relative ($date)"
            // Only mention a count when there really is more than one; "۱ سررسید نزدیک داری"
            // when there is exactly one reads like a machine wrote it.
            val tail = if (dueSoonCount > 1) {
                " · ${faNumber(dueSoonCount)} سررسید در ۷ روز آینده"
            } else {
                ""
            }
            return "تراکنش‌های امروزت را ثبت کن. نزدیک‌ترین سررسید: ${item.label} — $due$tail."
        }

        val item = fire.item ?: return "سررسید نزدیک است."
        val date = formatShamsiDate(tehranDayStart(item.dueDay))
        val days = fire.daysUntilDue ?: 0
        val relative = when {
            days > 0 -> "${faNumber(days)} روز تا سررسید"
            days == 0 -> "امروز سررسید است"
            else -> "${faNumber(-days)} روز از سررسید گذشته است"
        }
        val duePart = if (date.isBlank()) relative else "$relative ($date)"
        return "${item.label} — $duePart"
    }

    // ─────────────────────────── intents ───────────────────────────

    /**
     * Opens the app. `getLaunchIntentForPackage` is preferred over naming [MainActivity] so the
     * launcher activity stays the manifest's decision, not this file's; the explicit fallback is
     * only there because the call is nullable and a reminder whose tap does nothing is worse than
     * one that fails at build time.
     */
    private fun openIntent(context: Context, key: String): PendingIntent {
        val launch = context.packageManager.getLaunchIntentForPackage(context.packageName)
            ?: Intent(context, MainActivity::class.java)
        launch.putExtra(EXTRA_REMINDER_KEY, key)
        launch.addFlags(
            Intent.FLAG_ACTIVITY_NEW_TASK or
                Intent.FLAG_ACTIVITY_SINGLE_TOP or
                Intent.FLAG_ACTIVITY_CLEAR_TOP,
        )
        return PendingIntent.getActivity(
            context,
            notificationId(key),
            launch,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
    }

    /** The broadcast the receiver listens for; see [ReminderActionReceiver]. */
    private fun handledIntent(context: Context, fire: ReminderFire): PendingIntent {
        val intent = Intent(context, ReminderActionReceiver::class.java)
            .setAction(ACTION_MARK_HANDLED)
            .putExtra(EXTRA_KEY, fire.key)
            .putExtra(EXTRA_REMINDER_ITEM_ID, fire.item?.id)
        return PendingIntent.getBroadcast(
            context,
            notificationId(fire.key) + 1,
            intent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
    }

    /** Stable, non-negative id per dedupe key, so replanning replaces rather than stacks. */
    private fun notificationId(key: String): Int = key.hashCode() and 0x7fffffff
}
