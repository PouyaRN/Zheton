package com.doxigo.muchtoman.features.reminders

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import kotlinx.coroutines.CancellationException

/**
 * The worker behind both kinds of reminder.
 *
 * It is written as a **re-planner**, not as a payload carrier: the input data only says which
 * dedupe key it was armed for, and the worker reads the current items and settings and asks the
 * planner again. That costs one extra read and buys two things that matter on a phone:
 *
 *  * A cheque that was edited, settled or deleted after the work was armed is respected. The
 *    notification that would have described yesterday's data is simply not shown.
 *  * A tap on «رسیدگی شد» is final. The key is in the fired ledger before the next run, so the
 *    work cannot resurrect a reminder she already answered.
 *
 * The daily input kind is also the heartbeat: even when the daily line has nothing to say, running
 * it once a day re-arms the one-shots that have drifted out of the 45-day horizon.
 */
class ReminderWorker(
    context: Context,
    params: WorkerParameters,
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val app = applicationContext
        return try {
            ReminderNotifications.ensureChannel(app)
            val prefs = ReminderPrefs(app)
            // Switched off while this work was queued: there is nothing to aim at, and
            // `cancelAll` is deliberately not called from inside a worker by this feature.
            if (!prefs.enabled) return Result.success()

            val now = System.currentTimeMillis()
            val settings = prefs.settings()
            val items = loadReminderItems(app, now)
            val plan = ReminderPlanner.plan(items, now, settings, prefs.firedKeys())

            val key = inputData.getString(KEY_KEY)
            val isDaily = inputData.getString(KEY_KIND) == KIND_DAILY
            val fire = if (isDaily) {
                plan.daily
            } else {
                plan.oneShots.firstOrNull { it.key == key }
            }

            // The slack absorbs the milliseconds between WorkManager waking us and this line.
            // WorkManager never runs before `initialDelay`, so a fire that is still ahead of us
            // means a stale input row, not an early alarm — and arming again below is enough.
            if (fire != null && fire.atMillis <= now + POST_SLACK_MS) {
                if (ReminderNotifications.post(app, fire, plan.dueSoonCount)) {
                    prefs.rememberFired(fire.key)
                }
            }

            // Re-plan with the ledger as it now stands, then arm only what is missing. `arm`,
            // not `reschedule`: the latter cancels by tag and would cancel this very worker.
            val remaining = ReminderPlanner.plan(items, now, settings, prefs.firedKeys())
            ReminderScheduler.arm(app, remaining, now)
            Result.success()
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            // A corrupt preferences file or a Room read that cannot succeed will not start
            // succeeding on a retry, and a retry storm is worse than a missed reminder. The daily
            // tick tries again tomorrow.
            Result.success()
        }
    }

    companion object {
        const val KEY_KIND = "kind"
        const val KEY_KEY = "key"

        const val KIND_ITEM = "item"
        const val KIND_DAILY = "daily"

        /**
         * How late a fire may be and still be posted by this run.
         *
         * A planner nudge for a moment that has already passed is placed 5 seconds ahead, so this
         * also has to cover that gap; 10 seconds does, without ever posting something a whole
         * interval early.
         */
        private const val POST_SLACK_MS = 10_000L
    }
}
