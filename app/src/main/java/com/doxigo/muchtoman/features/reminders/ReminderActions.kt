package com.doxigo.muchtoman.features.reminders

import android.content.Context

/**
 * One "رسیدگی شد" tap, on its way from the notification shade to whatever owns the obligation.
 *
 * [key] is the planner's dedupe key (`due:<itemId>:<day>` or `daily:<day>`), which is also what
 * the notification was filed under. [itemId] is the [ReminderItem.id] when the reminder was about
 * an item and null for the daily reminder.
 */
data class HandledReminder(
    val key: String,
    val itemId: String?,
) {
    /** The source-prefixed item id, split for callers that would rather have the raw id. */
    val source: String? get() = itemId?.substringBefore(':')
    val rawId: String? get() = itemId?.let { it.substringAfter(':', it) }
}

/**
 * The seam between the notification receiver and the app's own state.
 *
 * The receiver is the only place that learns about a tap while the UI may not exist: Android can
 * start the process just for the broadcast, run it, and kill it again. So there are two paths,
 * and both matter:
 *
 *  * [onHandled] is the **injectable callback**. The app sets it while it is alive (from the
 *    activity/view-model that can change the cheque's state, mark the installment paid, and
 *    refresh the screen); it fires immediately in that case.
 *  * The tap is also **persisted** by [ReminderPrefs.rememberHandled]. If the callback is null
 *    (cold process, no UI), [drainPending] returns the events on the next app start, so a tap is
 *    never silently lost. That is the difference between an action button and decoration.
 *
 * No telemetry, no accounts: this is a local callback, and the queue never leaves the phone.
 */
object ReminderActions {

    /**
     * Set by the app while it is running. Not required for correctness — the pending queue is —
     * and deliberately a plain field: a reminder tap is a single event with no ordering story.
     */
    @Volatile
    var onHandled: ((HandledReminder) -> Unit)? = null

    /**
     * Called by [ReminderActionReceiver]; kept public so a test or a host can drive the seam.
     *
     * Returns whether a live callback took the event. When it did, the receiver drops the queued
     * copy: handling the same tap twice — once now and once on the next app start — would mark an
     * installment paid twice in a UI that trusts the event.
     */
    fun dispatch(event: HandledReminder): Boolean {
        val callback = onHandled ?: return false
        callback(event)
        return true
    }

    /** Handled taps not yet consumed by the UI. Clears the queue as it reads. */
    fun drainPending(context: Context): List<HandledReminder> =
        ReminderPrefs(context).drainHandled()
}
