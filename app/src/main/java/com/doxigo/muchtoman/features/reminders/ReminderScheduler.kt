package com.doxigo.muchtoman.features.reminders

import android.content.Context
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.workDataOf
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.util.concurrent.TimeUnit

/**
 * How WorkManager is asked to do the reminding.
 *
 * Three deliberate choices:
 *
 *  * **WorkManager, not `AlarmManager`.** There is no `SCHEDULE_EXACT_ALARM` permission in the
 *    manifest, no foreground service and no battery-optimisation exemption dance. WorkManager
 *    keeps its queue in its own database, survives reboots, and runs when the phone next gets a
 *    chance — which is the honest trade-off for a reminder that is allowed to arrive a few
 *    minutes late. Exact alarms are for alarms; a cheque due in three days is not one.
 *  * **Unique names derived from the dedupe key.** Re-scheduling is idempotent by construction:
 *    opening the app five times creates one work per reminder, not five. This is also why the
 *    worker uses [arm] and the app uses [reschedule] — replacing a unique work while that work is
 *    the one currently running cancels it mid-flight.
 *  * **A daily tick as the heartbeat.** One periodic work runs once a day and re-plans everything.
 *    Without it, a phone whose user never opens the app would only have the work armed on the day
 *    reminders were switched on. It also gives the schedule a second way to recover if a one-shot
 *    is ever lost.
 *
 * No network constraint anywhere: reminders are computed from local data, and a phone with no
 * signal still has a cheque due.
 */
object ReminderScheduler {

    /** Tag on every work this feature creates, so disabling reminders can cancel exactly these. */
    const val TAG = "reminders"

    /** Unique-work prefix; the dedupe key follows it, so the name is stable across replans. */
    const val WORK_ONE_SHOT_PREFIX = "reminder-due:"

    /** The daily heartbeat's unique name. */
    const val WORK_DAILY = "reminder-daily"

    /**
     * Serialises full rebuilds. Two quick taps on the lead-days stepper launch two rebuilds; without
     * this they can interleave cancel/enqueue and leave a work for the value in between.
     */
    private val rescheduleLock = Mutex()

    /**
     * Cancel everything, then build the schedule again from current data.
     *
     * Call this from anything that changes the input: app start, a commitment saved or deleted, a
     * settings change, a handled tap. It is `suspend` because the item list comes from Room, and
     * it is safe to call when reminders are off — it just leaves nothing behind.
     */
    suspend fun reschedule(context: Context, nowMillis: Long = System.currentTimeMillis()) {
        val app = context.applicationContext
        rescheduleLock.withLock {
            withContext(Dispatchers.IO) {
                val workManager = WorkManager.getInstance(app)
                // Cancelling first is what removes work for a reminder that was deleted, settled,
                // or moved; enqueueing alone would leave the stale one armed under its old key.
                workManager.cancelAllWorkByTag(TAG)

                val prefs = ReminderPrefs(app)
                val settings = prefs.settings()
                if (!settings.enabled) return@withContext

                ReminderNotifications.ensureChannel(app)
                val items = loadReminderItems(app, nowMillis)
                val plan = ReminderPlanner.plan(
                    items = items,
                    nowMillis = nowMillis,
                    settings = settings,
                    firedKeys = prefs.firedKeys(),
                )
                armInternal(workManager, plan, nowMillis, replace = true)
            }
        }
    }

    /**
     * Add whatever the plan says is missing, without cancelling anything.
     *
     * This is the worker's entry point. [ExistingWorkPolicy.KEEP] makes it a no-op for work that
     * is already armed — including the work currently executing — so a reminder that has just
     * fired can safely arm the next one from inside its own `doWork`.
     */
    fun arm(context: Context, plan: ReminderPlan, nowMillis: Long = System.currentTimeMillis()) {
        armInternal(WorkManager.getInstance(context), plan, nowMillis, replace = false)
    }

    /** Stop all reminder work. Called when she switches reminders off. */
    fun cancelAll(context: Context) {
        WorkManager.getInstance(context.applicationContext).cancelAllWorkByTag(TAG)
    }

    private fun armInternal(
        workManager: WorkManager,
        plan: ReminderPlan,
        nowMillis: Long,
        replace: Boolean,
    ) {
        val oneShotPolicy = if (replace) ExistingWorkPolicy.REPLACE else ExistingWorkPolicy.KEEP
        plan.oneShots.forEach { fire ->
            val delay = (fire.atMillis - nowMillis).coerceAtLeast(0L)
            val request = OneTimeWorkRequestBuilder<ReminderWorker>()
                .setInitialDelay(delay, TimeUnit.MILLISECONDS)
                .setInputData(
                    workDataOf(
                        ReminderWorker.KEY_KIND to ReminderWorker.KIND_ITEM,
                        ReminderWorker.KEY_KEY to fire.key,
                    ),
                )
                .addTag(TAG)
                .build()
            workManager.enqueueUniqueWork(WORK_ONE_SHOT_PREFIX + fire.key, oneShotPolicy, request)
        }

        // There is always a daily tick while reminders are on, even when the daily line itself
        // has nothing to say: it is the heartbeat that re-plans the item schedule.
        val dailyDelay = plan.daily?.let { (it.atMillis - nowMillis).coerceAtLeast(0L) }
            ?: TimeUnit.DAYS.toMillis(1)
        val dailyRequest = PeriodicWorkRequestBuilder<ReminderWorker>(24, TimeUnit.HOURS)
            .setInitialDelay(dailyDelay, TimeUnit.MILLISECONDS)
            .setInputData(workDataOf(ReminderWorker.KEY_KIND to ReminderWorker.KIND_DAILY))
            .addTag(TAG)
            .build()
        val dailyPolicy = if (replace) {
            // Re-applies the chosen time when she changes it, rather than keeping the old delay
            // for the life of the install.
            ExistingPeriodicWorkPolicy.CANCEL_AND_REENQUEUE
        } else {
            ExistingPeriodicWorkPolicy.KEEP
        }
        workManager.enqueueUniquePeriodicWork(WORK_DAILY, dailyPolicy, dailyRequest)
    }
}
