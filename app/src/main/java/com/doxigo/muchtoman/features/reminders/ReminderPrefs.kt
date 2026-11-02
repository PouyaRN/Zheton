package com.doxigo.muchtoman.features.reminders

import android.content.Context

/**
 * The reminder preferences, in the same `muchtoman` preferences file the rest of the app uses.
 *
 * A separate class rather than more fields on `Store` because reminders are their own feature
 * with their own defaults and their own schema — and because `Store` is the app's shared state,
 * where a new key is a decision for whoever owns it, not a detail one feature can append.
 *
 * Two kinds of data live here:
 *
 *  * **Settings** — one key each, readable and clamped on the way out, so a restore from an older
 *    build (or a hand-edited backup) cannot produce a 400-day lead time.
 *  * **The dedupe ledger** — the keys of reminders already shown. This is deliberately *not* in
 *    the database: it is a cache of "I already told her", losing it costs one repeated
 *    notification, and keeping it out of the durable store keeps the ledger the only thing there
 *    that must never be rebuilt. It is a newline-joined list capped at
 *    [ReminderSettings.MAX_FIRED_KEYS]; a `StringSet` would have made the cap and the order
 *    impossible to reason about, and the cap is what stops a phone that has been running for
 *    years from carrying a megabyte of notification history.
 */
class ReminderPrefs(context: Context) {

    private val prefs =
        context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    /** Whether reminders exist at all. Off until she says otherwise — no notification on install. */
    var enabled: Boolean
        get() = prefs.getBoolean(KEY_ENABLED, false)
        set(value) = prefs.edit().putBoolean(KEY_ENABLED, value).apply()

    /** Days between the reminder and the due date. 0 means "on the day itself". */
    var leadDays: Int
        get() = prefs.getInt(KEY_LEAD_DAYS, ReminderSettings.DEFAULT_LEAD_DAYS)
            .coerceIn(0, ReminderSettings.MAX_LEAD_DAYS)
        set(value) = prefs.edit()
            .putInt(KEY_LEAD_DAYS, value.coerceIn(0, ReminderSettings.MAX_LEAD_DAYS))
            .apply()

    /** Local time of day for every reminder, in minutes from midnight. */
    var minuteOfDay: Int
        get() = prefs.getInt(KEY_MINUTE_OF_DAY, ReminderSettings.DEFAULT_MINUTE_OF_DAY)
            .coerceIn(0, MINUTES_PER_DAY - 1)
        set(value) = prefs.edit()
            .putInt(KEY_MINUTE_OF_DAY, value.coerceIn(0, MINUTES_PER_DAY - 1))
            .apply()

    var quietEnabled: Boolean
        get() = prefs.getBoolean(KEY_QUIET_ENABLED, true)
        set(value) = prefs.edit().putBoolean(KEY_QUIET_ENABLED, value).apply()

    var quietStartMinute: Int
        get() = prefs.getInt(KEY_QUIET_START, 23 * 60).coerceIn(0, MINUTES_PER_DAY - 1)
        set(value) = prefs.edit()
            .putInt(KEY_QUIET_START, value.coerceIn(0, MINUTES_PER_DAY - 1))
            .apply()

    var quietEndMinute: Int
        get() = prefs.getInt(KEY_QUIET_END, 8 * 60).coerceIn(0, MINUTES_PER_DAY - 1)
        set(value) = prefs.edit()
            .putInt(KEY_QUIET_END, value.coerceIn(0, MINUTES_PER_DAY - 1))
            .apply()

    /**
     * Whether the system permission dialog has ever been shown.
     *
     * Android stops showing that dialog after two denials without telling the app that it will,
     * and the only observable difference is that a launch returns "denied" without a dialog.
     * Remembering that we asked is what lets the screen offer the system settings page instead of
     * a button that does nothing.
     */
    var notificationPermissionAsked: Boolean
        get() = prefs.getBoolean(KEY_PERMISSION_ASKED, false)
        set(value) = prefs.edit().putBoolean(KEY_PERMISSION_ASKED, value).apply()

    /** The settings as one value, for the planner. */
    fun settings(): ReminderSettings = ReminderSettings(
        enabled = enabled,
        leadDays = leadDays,
        minuteOfDay = minuteOfDay,
        quietHours = QuietHours(
            enabled = quietEnabled,
            startMinuteOfDay = quietStartMinute,
            endMinuteOfDay = quietEndMinute,
        ),
    )

    /** Persist a whole settings value at once, e.g. after "reset" or from a restore. */
    fun save(settings: ReminderSettings) {
        val s = settings.normalized()
        enabled = s.enabled
        leadDays = s.leadDays
        minuteOfDay = s.minuteOfDay
        quietEnabled = s.quietHours.enabled
        quietStartMinute = s.quietHours.startMinuteOfDay
        quietEndMinute = s.quietHours.endMinuteOfDay
    }

    /** Keys already shown. The planner filters these out, so they can never fire twice. */
    fun firedKeys(): Set<String> = synchronized(LOCK) { readLines(KEY_FIRED).toSet() }

    fun rememberFired(key: String) {
        if (key.isBlank()) return
        synchronized(LOCK) {
            val keys = readLines(KEY_FIRED)
            if (key in keys) return
            writeLines(KEY_FIRED, (keys + key).takeLast(ReminderSettings.MAX_FIRED_KEYS))
        }
    }

    /**
     * Record that she tapped «رسیدگی شد» on a notification.
     *
     * The key is also marked as fired: the reminder was shown and answered, and no later
     * replanning may resurrect it. The event is queued as well, because the receiver can run in a
     * process where the app's UI callback is not registered; [ReminderActions.drainPending] is
     * what the app calls on its next start to do the actual work (mark the cheque settled, mark
     * the installment paid).
     */
    fun rememberHandled(key: String, itemId: String?) {
        if (key.isBlank()) return
        synchronized(LOCK) {
            val fired = readLines(KEY_FIRED)
            if (key !in fired) {
                writeLines(KEY_FIRED, (fired + key).takeLast(ReminderSettings.MAX_FIRED_KEYS))
            }
            val pending = readLines(KEY_PENDING_HANDLED)
                .filterNot { it.substringBefore('\t') == key }
            val event = key + "\t" + itemId.orEmpty()
            writeLines(KEY_PENDING_HANDLED, (pending + event).takeLast(MAX_PENDING_HANDLED))
        }
    }

    /**
     * Drop one queued event after the live callback has taken it.
     *
     * The receiver queues first and dispatches second, so a live app must remove the entry it
     * just handled — otherwise the same tap would be handled a second time on the next start.
     */
    fun consumeHandled(key: String) {
        synchronized(LOCK) {
            val remaining = readLines(KEY_PENDING_HANDLED)
                .filterNot { it.substringBefore('\t') == key }
            writeLines(KEY_PENDING_HANDLED, remaining)
        }
    }

    /** Read and clear the handled events the UI has not consumed yet. */
    fun drainHandled(): List<HandledReminder> = synchronized(LOCK) {
        val events = readLines(KEY_PENDING_HANDLED).map { line ->
            HandledReminder(
                key = line.substringBefore('\t'),
                itemId = line.substringAfter('\t', "").ifBlank { null },
            )
        }
        if (events.isNotEmpty()) writeLines(KEY_PENDING_HANDLED, emptyList())
        events
    }

    /** Forget what was shown. Exposed for a "test the reminder again" style reset, not the UI. */
    fun clearFired() {
        synchronized(LOCK) { writeLines(KEY_FIRED, emptyList()) }
    }

    private fun readLines(key: String): List<String> =
        prefs.getString(key, "").orEmpty()
            .split('\n')
            .filter { it.isNotBlank() }

    private fun writeLines(key: String, lines: List<String>) {
        prefs.edit().putString(key, lines.joinToString("\n")).apply()
    }

    private companion object {
        /**
         * The same file `Store` uses, on purpose: one backup, one clear-data, one place a person
         * would look. The keys are all prefixed so they cannot collide with the ledger's.
         */
        const val PREFS_NAME = "zheton"

        const val KEY_ENABLED = "remindersEnabled"
        const val KEY_LEAD_DAYS = "remindersLeadDays"
        const val KEY_MINUTE_OF_DAY = "remindersMinuteOfDay"
        const val KEY_QUIET_ENABLED = "remindersQuietEnabled"
        const val KEY_QUIET_START = "remindersQuietStart"
        const val KEY_QUIET_END = "remindersQuietEnd"
        const val KEY_PERMISSION_ASKED = "remindersPermissionAsked"
        const val KEY_FIRED = "remindersFiredKeys"
        const val KEY_PENDING_HANDLED = "remindersPendingHandled"

        /** A tap queue this size means the app has not been opened in months; drop the oldest. */
        const val MAX_PENDING_HANDLED = 100

        /** Worker, receiver and UI all mutate the ledger; a JVM-wide lock is enough for one process. */
        val LOCK = Any()
    }
}
