package com.doxigo.muchtoman.features.reminders

import com.doxigo.muchtoman.DAY_MS
import com.doxigo.muchtoman.TEHRAN_OFFSET_MS

/*
 * Deciding *when* a reminder happens, with no Android in the file.
 *
 * The planner is the part that is easy to get subtly wrong — a day counted from UTC instead of
 * Tehran, a reminder armed for a moment that has already passed, a second notification for the
 * same cheque the next morning. Keeping it as a pure function of (items, now, settings, already
 * fired) is what lets the JUnit tests below cover those cases without a device, an emulator or a
 * clock. WorkManager, channels and intents live in the neighbouring files and none of their
 * decisions leak in here.
 */

/** What sort of obligation a reminder belongs to. The Persian label is what the notification prints. */
enum class ReminderKind(val fa: String) {
    CHECK("چک صیادی"),
    LOAN("وام"),
    BNPL("خرید اعتباری"),
    INSTALLMENT("قسط"),
    OTHER("سررسید"),
}

/**
 * One thing that will be due on one Tehran day.
 *
 * Deliberately a day number rather than a timestamp: a cheque is due "on 12 Mordad", not at
 * 11:47:03. The reminder's hour comes from [ReminderSettings.minuteOfDay], so moving the daily
 * reminder from 9 to 20 moves every lead-day reminder with it — one time to understand instead of
 * three. [id] only has to be stable across runs; the workspace-facing adapter prefixes it with the
 * source ("commitment:", "installment:") so two tables can never collide.
 */
data class ReminderItem(
    val id: String,
    val kind: ReminderKind,
    val dueDay: Long,
    val label: String,
)

/** One quiet window, in local minutes from midnight. The end is exclusive. */
data class QuietHours(
    val enabled: Boolean = true,
    val startMinuteOfDay: Int = 23 * 60,
    val endMinuteOfDay: Int = 8 * 60,
) {
    /** Clamped copy; stored preferences can be edited by an older build or a backup restore. */
    fun normalized(): QuietHours = copy(
        startMinuteOfDay = startMinuteOfDay.coerceIn(0, MINUTES_PER_DAY - 1),
        endMinuteOfDay = endMinuteOfDay.coerceIn(0, MINUTES_PER_DAY - 1),
    )

    /**
     * Whether [minuteOfDay] falls inside the window.
     *
     * A window that wraps midnight ("22:00 to 08:00") is the normal case in Iran, so the
     * wrap is the branch that has to be right. Equal ends mean an empty window rather than a
     * 24-hour one: "from 08:00 to 08:00" as *all day* is a trap, and a user who wants silence
     * all day can switch reminders off.
     */
    fun contains(minuteOfDay: Int): Boolean {
        if (!enabled) return false
        val minute = minuteOfDay.coerceIn(0, MINUTES_PER_DAY - 1)
        if (startMinuteOfDay == endMinuteOfDay) return false
        return if (startMinuteOfDay < endMinuteOfDay) {
            minute >= startMinuteOfDay && minute < endMinuteOfDay
        } else {
            minute >= startMinuteOfDay || minute < endMinuteOfDay
        }
    }

    companion object {
        /** A calm default: nobody wants a cheque reminder at 02:00. */
        val NIGHT = QuietHours()
        val DISABLED = QuietHours(enabled = false)
    }
}

/** Everything the settings screen owns. Defaults are what a fresh install gets. */
data class ReminderSettings(
    val enabled: Boolean = false,
    val leadDays: Int = DEFAULT_LEAD_DAYS,
    val minuteOfDay: Int = DEFAULT_MINUTE_OF_DAY,
    val quietHours: QuietHours = QuietHours.NIGHT,
) {
    /** Clamped copy; see [QuietHours.normalized] for why this exists at all. */
    fun normalized(): ReminderSettings = copy(
        leadDays = leadDays.coerceIn(0, MAX_LEAD_DAYS),
        minuteOfDay = minuteOfDay.coerceIn(0, MINUTES_PER_DAY - 1),
        quietHours = quietHours.normalized(),
    )

    companion object {
        const val DEFAULT_LEAD_DAYS = 3
        const val MAX_LEAD_DAYS = 30
        const val DEFAULT_MINUTE_OF_DAY = 9 * 60

        /** The key every reminder this app has ever fired is remembered under. */
        const val MAX_FIRED_KEYS = 400
    }
}

/**
 * One notification the app intends to show.
 *
 * [key] is the whole duplicate-suppression story: it names the due date, not the firing moment,
 * so re-planning every time the app opens — or once a day while the lead window is open — still
 * produces the same key and the scheduler can tell "already told her" from "tell her now".
 * [immediate] means the preferred moment has already passed and this should be shown as soon as
 * WorkManager lets the worker run (which is what makes a phone that was off at 09:00 still get
 * the reminder when it comes back on).
 */
data class ReminderFire(
    val key: String,
    val atMillis: Long,
    val item: ReminderItem?,
    val daysUntilDue: Int?,
    val overdue: Boolean,
    val daily: Boolean,
    val immediate: Boolean,
)

/**
 * The next stretch of schedule, as data.
 *
 * [oneShots] is ordered and bounded; the scheduler arms the earliest and re-plans when it fires.
 * [daily] is the next daily reminder (or null when the settings make one impossible), and
 * [dailyDue] is the one boolean the scheduler actually needs: "the daily moment has arrived and
 * has not been shown yet". [dueSoonCount] feeds the honest summary line in the daily notification
 * instead of making the notification builder re-read the database.
 */
data class ReminderPlan(
    val oneShots: List<ReminderFire>,
    val daily: ReminderFire?,
    val dailyDue: Boolean,
    val nowMillis: Long,
    val dueSoonCount: Int,
) {
    /** The next item reminder, which is what the one-shot worker gets armed for. */
    val nextOneShot: ReminderFire? get() = oneShots.firstOrNull()

    /** The nearest of the two kinds of reminder, for the "next reminder" line in settings. */
    val nextAny: ReminderFire? get() =
        listOfNotNull(nextOneShot, daily).minByOrNull { it.atMillis }

    val isEmpty: Boolean get() = oneShots.isEmpty() && daily == null
}

/**
 * Pure planning. No clock, no context, no database: the caller supplies all four.
 *
 * [zoneOffsetMs] exists because the app is Iran-only but the arithmetic should not assume it:
 * tests use it to prove that the hour is a *local* hour and not a UTC one. Real callers keep the
 * default, which is the same fixed Tehran offset the rest of the app uses.
 */
object ReminderPlanner {

    /**
     * How far ahead the app is willing to arm a one-shot.
     *
     * WorkManager keeps delayed work in its own database, so a long delay is not expensive — but
     * it is also not a promise, and there is no reason to hold work for a cheque two months out.
     * The daily periodic tick re-plans as things get closer, so this is a horizon, not a wall.
     */
    const val HORIZON_DAYS = 45

    /**
     * An overdue item is only worth one nudge, and only while it is still news. A commitment left
     * unhandled for a month belongs on the commitments screen, not in the notification tray, and
     * reminding about it every day is how people turn reminders off.
     */
    const val OVERDUE_GRACE_DAYS = 7

    /** At most this many item reminders are armed at once; the rest follow as these fire. */
    const val MAX_ONE_SHOTS = 8

    /** A nudge for a moment that already passed is shown this far in the future, not "now". */
    const val SOON_DELAY_MS = 5_000L

    /** Days of lookahead for the daily reminder, which is enough to step over a quiet window. */
    private const val DAILY_LOOKAHEAD_DAYS = 2

    /** What "near" means in the daily reminder's summary line. */
    private const val DUE_SOON_DAYS = 7

    /**
     * Plan the next notifications.
     *
     * [firedKeys] is the dedupe ledger owned by [ReminderPrefs]; passing it in keeps this
     * function total and testable. Keys in it are never returned again, which is why replanning
     * tomorrow cannot produce today's notification a second time.
     */
    fun plan(
        items: List<ReminderItem>,
        nowMillis: Long,
        settings: ReminderSettings,
        firedKeys: Set<String> = emptySet(),
        zoneOffsetMs: Long = TEHRAN_OFFSET_MS,
    ): ReminderPlan {
        val s = settings.normalized()
        if (!s.enabled) {
            return ReminderPlan(emptyList(), null, false, nowMillis, 0)
        }
        val today = localDay(nowMillis, zoneOffsetMs)
        val oneShots = items
            // One row per (id, due date): a list assembled from two sources can mention the same
            // cheque twice, and two identical keys would mean two identical notifications.
            .distinctBy { it.id to it.dueDay }
            .mapNotNull { itemFire(it, today, nowMillis, s, firedKeys, zoneOffsetMs) }
            .sortedWith(compareBy({ it.atMillis }, { it.key }))
            .take(MAX_ONE_SHOTS)

        val daily = dailyFire(items, today, nowMillis, s, firedKeys, zoneOffsetMs)
        val dueSoonCount = items.count { it.dueDay in today..(today + DUE_SOON_DAYS) }
        return ReminderPlan(
            oneShots = oneShots,
            daily = daily,
            dailyDue = daily?.immediate == true,
            nowMillis = nowMillis,
            dueSoonCount = dueSoonCount,
        )
    }

    /** The stable dedupe key for an item's current due date. */
    fun dueKey(item: ReminderItem): String = "due:${item.id}:${item.dueDay}"

    /** The stable dedupe key for the daily reminder of one local day. */
    fun dailyKey(day: Long): String = "daily:$day"

    /** The local day an instant falls on, for the caller's offset. */
    fun localDay(epochMillis: Long, zoneOffsetMs: Long = TEHRAN_OFFSET_MS): Long =
        floorDiv(epochMillis + zoneOffsetMs, DAY_MS)

    /** Local minutes from midnight, 0..1439. */
    fun localMinuteOfDay(epochMillis: Long, zoneOffsetMs: Long = TEHRAN_OFFSET_MS): Int {
        val sinceMidnight = floorMod(epochMillis + zoneOffsetMs, DAY_MS)
        return (sinceMidnight / 60_000L).toInt()
    }

    /** The first instant of a local day, back in epoch milliseconds. */
    fun dayStart(day: Long, zoneOffsetMs: Long = TEHRAN_OFFSET_MS): Long =
        day * DAY_MS - zoneOffsetMs

    /**
     * Move an instant out of the quiet window, forward to the window's end.
     *
     * Forward, never backward: a reminder that would have happened at 23:30 and is moved to
     * 08:00 the next morning is still a reminder; one moved to 22:00 the same evening is a
     * surprise before the quiet hours even start. If the end has already passed because of the
     * wrap, the modulo lands on the next day's end and that is the intended one.
     */
    fun shiftOutOfQuiet(
        epochMillis: Long,
        quietHours: QuietHours,
        zoneOffsetMs: Long = TEHRAN_OFFSET_MS,
    ): Long {
        val quiet = quietHours.normalized()
        if (!quiet.enabled) return epochMillis
        val minute = localMinuteOfDay(epochMillis, zoneOffsetMs)
        if (!quiet.contains(minute)) return epochMillis
        val untilEnd = floorMod(quiet.endMinuteOfDay - minute, MINUTES_PER_DAY)
        val deltaMinutes = if (untilEnd == 0) MINUTES_PER_DAY else untilEnd
        return epochMillis + deltaMinutes * 60_000L
    }

    // ─────────────────────────── item reminders ───────────────────────────

    private fun itemFire(
        item: ReminderItem,
        today: Long,
        nowMillis: Long,
        settings: ReminderSettings,
        firedKeys: Set<String>,
        zoneOffsetMs: Long,
    ): ReminderFire? {
        // Far past is not a reminder, and far future is not this worker's business yet.
        if (item.dueDay < today - OVERDUE_GRACE_DAYS) return null
        if (item.dueDay > today + HORIZON_DAYS) return null

        val key = dueKey(item)
        if (key in firedKeys) return null

        val preferredDay = item.dueDay - settings.leadDays
        val preferred = dayStart(preferredDay, zoneOffsetMs) + settings.minuteOfDay * 60_000L
        val planned = shiftOutOfQuiet(preferred, settings.quietHours, zoneOffsetMs)
        // Missed the planned moment (phone off, app installed late, permission granted today):
        // show it as soon as possible rather than silently dropping it. If right now is quiet,
        // "as soon as possible" is the end of the quiet window, which is what the second shift
        // is for.
        val at = if (planned <= nowMillis) {
            shiftOutOfQuiet(nowMillis + SOON_DELAY_MS, settings.quietHours, zoneOffsetMs)
        } else {
            planned
        }

        val daysUntilDue = (item.dueDay - today).toInt()
        return ReminderFire(
            key = key,
            atMillis = at,
            item = item,
            daysUntilDue = daysUntilDue,
            overdue = daysUntilDue < 0,
            daily = false,
            immediate = at <= nowMillis + SOON_DELAY_MS,
        )
    }

    // ─────────────────────────── daily reminder ───────────────────────────

    private fun dailyFire(
        items: List<ReminderItem>,
        today: Long,
        nowMillis: Long,
        settings: ReminderSettings,
        firedKeys: Set<String>,
        zoneOffsetMs: Long,
    ): ReminderFire? {
        // The nearest upcoming due date is what makes the daily line worth reading; overdue
        // items have their own notification and are not repeated here.
        val nearest = items.filter { it.dueDay >= today }.minByOrNull { it.dueDay }
        for (offsetDay in 0..DAILY_LOOKAHEAD_DAYS) {
            val day = today + offsetDay
            val preferred = dayStart(day, zoneOffsetMs) + settings.minuteOfDay * 60_000L
            val planned = shiftOutOfQuiet(preferred, settings.quietHours, zoneOffsetMs)

            val at = when {
                planned > nowMillis -> planned
                // Today's moment has passed and has not been shown: show it now, unless now is
                // quiet, in which case the quiet window wins and the reminder moves to its end.
                localDay(planned, zoneOffsetMs) == today ->
                    shiftOutOfQuiet(nowMillis + SOON_DELAY_MS, settings.quietHours, zoneOffsetMs)
                // Yesterday's daily is stale; a daily reminder is not a debt.
                else -> continue
            }

            // The key follows the *day it will actually arrive on*, not the day it was aimed
            // at. A 23:30 daily with a 23:00–08:00 quiet window becomes an 08:00 reminder, and
            // keying it by tomorrow stops tomorrow evening's attempt from firing twice.
            val key = dailyKey(localDay(at, zoneOffsetMs))
            if (key in firedKeys) continue

            val daysUntilDue = nearest?.let { (it.dueDay - today).toInt() }
            return ReminderFire(
                key = key,
                atMillis = at,
                item = nearest,
                daysUntilDue = daysUntilDue,
                overdue = false,
                daily = true,
                immediate = at <= nowMillis + SOON_DELAY_MS,
            )
        }
        return null
    }

    // ─────────────────────────── local-day arithmetic ───────────────────────────
    // java.time is unavailable (minSdk 24, no desugaring), and Jalali.kt keeps its flooring
    // helpers private, so the two operations this file needs are spelled out here.

    private fun floorDiv(a: Long, b: Long): Long {
        val q = a / b
        return if (a % b != 0L && (a xor b) < 0) q - 1 else q
    }

    private fun floorMod(a: Long, b: Long): Long = a - floorDiv(a, b) * b

    private fun floorMod(a: Int, b: Int): Int {
        val rem = a % b
        return if (rem < 0) rem + b else rem
    }
}

/** Minutes in a local day. One name, used by settings, quiet hours and tests. */
const val MINUTES_PER_DAY = 24 * 60
