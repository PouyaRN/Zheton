package com.doxigo.muchtoman.features.reminders

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * «رسیدگی شد» from the notification shade.
 *
 * A receiver is the only component that can hear a notification action while the app is not on
 * screen. Two things happen here and both are required:
 *
 *  1. The tap is written to the durable-ish prefs queue before anything else can fail. If the
 *     process dies after this line, the next app start still learns about it.
 *  2. The schedule is rebuilt, because the handled reminder must not come back and the next one
 *     may now be the earliest.
 *
 * The callback seam ([ReminderActions.onHandled]) is optional: when the app is alive it handles
 * the change immediately; when it is not, the persisted event is drained on next launch. That is
 * the whole reason this is a receiver and not a `PendingIntent.getActivity` with an extra — a tap
 * on a notification must work even when the app has never been opened this boot.
 *
 * `goAsync` is the supported way to do a few hundred milliseconds of work from a receiver; the
 * schedule rebuild is a preferences read and a WorkManager enqueue, comfortably inside that.
 */
class ReminderActionReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != ReminderNotifications.ACTION_MARK_HANDLED) return
        val key = intent.getStringExtra(ReminderNotifications.EXTRA_KEY) ?: return
        val itemId = intent.getStringExtra(ReminderNotifications.EXTRA_REMINDER_ITEM_ID)

        val pendingResult = goAsync()
        // A fresh scope per event rather than a long-lived one: the process may live for exactly
        // this broadcast, and a shared scope would leak it.
        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            try {
                val prefs = ReminderPrefs(context)
                prefs.rememberHandled(key, itemId)
                ReminderNotifications.cancel(context, key)
                // A live app consumes the event through the callback; a cold process leaves the
                // queued copy for the next start. Either way the tap is handled exactly once.
                if (ReminderActions.dispatch(HandledReminder(key, itemId))) {
                    prefs.consumeHandled(key)
                }
                ReminderScheduler.reschedule(context)
            } catch (_: Exception) {
                // The queue entry (already written first) is what the app drains; a failure to
                // re-plan is recoverable by the next app start or the daily tick.
            } finally {
                pendingResult.finish()
            }
        }
    }
}
