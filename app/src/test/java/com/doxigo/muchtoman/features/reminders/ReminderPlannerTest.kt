package com.doxigo.muchtoman.features.reminders

import com.doxigo.muchtoman.DAY_MS
import com.doxigo.muchtoman.JalaliDate
import com.doxigo.muchtoman.TEHRAN_OFFSET_MS
import com.doxigo.muchtoman.jalaliDay
import com.doxigo.muchtoman.jalaliMonthLength
import com.doxigo.muchtoman.jalaliOf
import com.doxigo.muchtoman.tehranDayStart
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The planner's contract, checked against the repo's own Jalali conversion.
 *
 * These tests run on a plain JVM: no Robolectric, no clock injection, no device. That is the
 * point of [ReminderPlanner] being pure. The dates are real Jalali dates converted by the same
 * [jalaliDay] the app uses, so a month-end bug cannot hide behind a hand-written epoch number,
 * and Nowruz and Esfand are covered because those are where "subtract three days" stops being
 * arithmetic and becomes a calendar.
 */
class ReminderPlannerTest {

    private val minute = 60_000L
    private val hour = 60 * minute

    @Test
    fun `an overdue item is nudged once and soon, not silently dropped`() {
        val today = jalaliDay(1405, 5, 10)
        val now = at(today, 10 * 60)
        val item = item(dueDay = today - 1)

        val plan = ReminderPlanner.plan(listOf(item), now, settings())

        val fire = plan.oneShots.single()
        assertTrue(fire.overdue)
        assertEquals(-1, fire.daysUntilDue!!)
        assertEquals("due:commitment:c1:${today - 1}", fire.key)
        assertEquals(now + ReminderPlanner.SOON_DELAY_MS, fire.atMillis)
        assertTrue(fire.immediate)
    }

    @Test
    fun `a cheque due today fires at the chosen hour, not at midnight`() {
        val today = jalaliDay(1405, 5, 10)
        val now = at(today, 8 * 60)
        val item = item(dueDay = today)

        val plan = ReminderPlanner.plan(listOf(item), now, settings(leadDays = 0))

        val fire = plan.oneShots.single()
        assertEquals(0, fire.daysUntilDue!!)
        assertFalse(fire.overdue)
        assertFalse(fire.immediate)
        assertEquals(at(today, 9 * 60), fire.atMillis)
    }

    @Test
    fun `a reminder for tomorrow arrives a day early when the lead is one`() {
        val today = jalaliDay(1405, 5, 10)
        val now = at(today, 8 * 60)

        val plan = ReminderPlanner.plan(
            listOf(item(dueDay = today + 1)),
            now,
            settings(leadDays = 1),
        )

        val fire = plan.oneShots.single()
        assertEquals(1, fire.daysUntilDue!!)
        assertEquals(at(today, 9 * 60), fire.atMillis)
    }

    // ─────────────────────────── lead window ───────────────────────────

    @Test
    fun `an item outside the lead window is still armed for its day, not shown early`() {
        val today = jalaliDay(1405, 5, 10)
        val now = at(today, 8 * 60)

        val plan = ReminderPlanner.plan(
            listOf(item(dueDay = today + 10)),
            now,
            settings(leadDays = 3),
        )

        val fire = plan.oneShots.single()
        assertEquals(10, fire.daysUntilDue!!)
        assertEquals(at(today + 7, 9 * 60), fire.atMillis)
        assertFalse(fire.immediate)
    }

    @Test
    fun `an item already inside the lead window fires now, even if the hour passed`() {
        val today = jalaliDay(1405, 5, 10)
        val now = at(today, 10 * 60)

        val plan = ReminderPlanner.plan(
            listOf(item(dueDay = today + 3)),
            now,
            settings(leadDays = 3),
        )

        val fire = plan.oneShots.single()
        assertTrue(fire.immediate)
        assertEquals(now + ReminderPlanner.SOON_DELAY_MS, fire.atMillis)
        assertFalse(fire.overdue)
    }

    @Test
    fun `nothing beyond the horizon is armed, so work is not held for months`() {
        val today = jalaliDay(1405, 5, 10)
        val now = at(today, 8 * 60)

        val plan = ReminderPlanner.plan(
            listOf(item(dueDay = today + ReminderPlanner.HORIZON_DAYS + 1)),
            now,
            settings(),
        )

        assertTrue(plan.oneShots.isEmpty())
    }

    @Test
    fun `an item overdue for longer than the grace window is not a reminder`() {
        val today = jalaliDay(1405, 5, 10)
        val now = at(today, 10 * 60)

        val plan = ReminderPlanner.plan(
            listOf(item(dueDay = today - ReminderPlanner.OVERDUE_GRACE_DAYS - 1)),
            now,
            settings(),
        )

        assertTrue(plan.oneShots.isEmpty())
    }

    // ─────────────────────────── duplicate suppression ───────────────────────────

    @Test
    fun `the key is the due date, so a second morning in the window cannot notify again`() {
        val today = jalaliDay(1405, 5, 10)
        val item = item(dueDay = today + 2)

        val first = ReminderPlanner.plan(
            listOf(item),
            at(today, 10 * 60),
            settings(leadDays = 3),
        )
        val key = first.oneShots.single().key

        // Same item, next day, same lead window — the planner proposes the identical key...
        val tomorrow = ReminderPlanner.plan(
            listOf(item),
            at(today + 1, 10 * 60),
            settings(leadDays = 3),
        )
        assertEquals(key, tomorrow.oneShots.single().key)

        // ...and the ledger is what turns "proposes" into "does not show it again".
        val suppressed = ReminderPlanner.plan(
            listOf(item),
            at(today + 1, 10 * 60),
            settings(leadDays = 3),
            firedKeys = setOf(key),
        )
        assertTrue(suppressed.oneShots.isEmpty())
    }

    @Test
    fun `two rows for the same due date produce one key and one notification`() {
        val today = jalaliDay(1405, 5, 10)
        val item = item(dueDay = today + 1)

        val plan = ReminderPlanner.plan(
            listOf(item, item.copy()),
            at(today, 8 * 60),
            settings(leadDays = 1),
        )

        assertEquals(1, plan.oneShots.size)
    }

    // ─────────────────────────── Jalali / month-end edges ───────────────────────────

    @Test
    fun `three days before Nowruz lands in the previous Jalali year`() {
        val nextYear = jalaliDay(1405, 1, 1)
        val now = at(jalaliDay(1404, 12, 27), 8 * 60)

        val fire = ReminderPlanner.plan(
            listOf(item(dueDay = nextYear)),
            now,
            settings(leadDays = 3),
        ).oneShots.single()

        assertEquals(at(jalaliDay(1404, 12, 27), 9 * 60), fire.atMillis)
        assertEquals(JalaliDate(1404, 12, 27), jalaliOf(ReminderPlanner.localDay(fire.atMillis)))
    }

    @Test
    fun `Esfand has thirty days in a leap year and the thirtieth keeps its own day`() {
        assertEquals(30, jalaliMonthLength(1403, 12))
        val due = jalaliDay(1403, 12, 30)
        val now = at(jalaliDay(1403, 12, 26), 8 * 60)

        val fire = ReminderPlanner.plan(
            listOf(item(dueDay = due)),
            now,
            settings(leadDays = 3),
        ).oneShots.single()

        assertEquals(JalaliDate(1403, 12, 27), jalaliOf(ReminderPlanner.localDay(fire.atMillis)))
        assertEquals(4, fire.daysUntilDue!!)
    }

    @Test
    fun `a twenty-nine day Esfand does not shift the twenty-eighth`() {
        assertEquals(29, jalaliMonthLength(1404, 12))
        val due = jalaliDay(1404, 12, 29)
        val now = at(jalaliDay(1404, 12, 27), 8 * 60)

        val fire = ReminderPlanner.plan(
            listOf(item(dueDay = due)),
            now,
            settings(leadDays = 1),
        ).oneShots.single()

        assertEquals(JalaliDate(1404, 12, 28), jalaliOf(ReminderPlanner.localDay(fire.atMillis)))
        assertEquals(2, fire.daysUntilDue!!)
    }

    @Test
    fun `a month boundary is walked day by day, not by tidy month arithmetic`() {
        // Shahrivar has 31 days, so 1 Mehr - 2 days is 30 Shahrivar (and -1 is the 31st).
        assertEquals(31, jalaliMonthLength(1405, 6))
        val due = jalaliDay(1405, 7, 1)
        val now = at(jalaliDay(1405, 6, 20), 8 * 60)

        val fire = ReminderPlanner.plan(
            listOf(item(dueDay = due)),
            now,
            settings(leadDays = 2),
        ).oneShots.single()

        assertEquals(JalaliDate(1405, 6, 30), jalaliOf(ReminderPlanner.localDay(fire.atMillis)))
    }

    // ─────────────────────────── quiet hours ───────────────────────────

    @Test
    fun `a daily reminder inside the quiet window moves to its end, on the next day`() {
        val today = jalaliDay(1405, 5, 10)
        val now = at(today, 20 * 60)
        val quiet = QuietHours(enabled = true, startMinuteOfDay = 23 * 60, endMinuteOfDay = 8 * 60)

        val plan = ReminderPlanner.plan(
            items = emptyList(),
            nowMillis = now,
            settings = settings(minuteOfDay = 23 * 60 + 30, quiet = quiet),
        )

        val daily = plan.daily!!
        assertEquals(at(today + 1, 8 * 60), daily.atMillis)
        assertEquals(ReminderPlanner.dailyKey(today + 1), daily.key)
        assertFalse(daily.immediate)
        assertFalse(plan.dailyDue)
    }

    @Test
    fun `a due reminder that is immediate while quiet waits for the window to end`() {
        val today = jalaliDay(1405, 5, 10)
        val now = at(today, 23 * 60 + 10)
        val quiet = QuietHours(enabled = true, startMinuteOfDay = 23 * 60, endMinuteOfDay = 8 * 60)

        val fire = ReminderPlanner.plan(
            listOf(item(dueDay = today)),
            now,
            settings(leadDays = 0, quiet = quiet),
        ).oneShots.single()

        // Still today's obligation, but it must not ring at 23:10. The nudge's own five-second
        // lead survives the move, so it lands five seconds after the window ends.
        assertEquals(at(today + 1, 8 * 60) + ReminderPlanner.SOON_DELAY_MS, fire.atMillis)
        assertEquals(0, fire.daysUntilDue!!)
        assertFalse(fire.immediate)
    }

    @Test
    fun `quiet hours switched off leave the chosen hour alone`() {
        val today = jalaliDay(1405, 5, 10)
        val now = at(today, 20 * 60)

        val daily = ReminderPlanner.plan(
            emptyList(),
            now,
            settings(minuteOfDay = 23 * 60 + 30, quiet = QuietHours.DISABLED),
        ).daily!!

        assertEquals(at(today, 23 * 60 + 30), daily.atMillis)
    }

    @Test
    fun `the quiet window wraps midnight and excludes its own end`() {
        val quiet = QuietHours(enabled = true, startMinuteOfDay = 23 * 60, endMinuteOfDay = 8 * 60)
        assertTrue(quiet.contains(23 * 60 + 30))
        assertTrue(quiet.contains(0))
        assertTrue(quiet.contains(7 * 60 + 59))
        assertFalse(quiet.contains(8 * 60))
        assertFalse(quiet.contains(22 * 60 + 59))
    }

    @Test
    fun `equal quiet ends mean an empty window, not a silent day`() {
        val quiet = QuietHours(enabled = true, startMinuteOfDay = 8 * 60, endMinuteOfDay = 8 * 60)
        assertFalse(quiet.contains(8 * 60))
        assertFalse(quiet.contains(12 * 60))
    }

    // ─────────────────────────── daily reminder ───────────────────────────

    @Test
    fun `the daily reminder is due when its hour has passed and nothing was shown`() {
        val today = jalaliDay(1405, 5, 10)
        val now = at(today, 10 * 60)

        val plan = ReminderPlanner.plan(emptyList(), now, settings(minuteOfDay = 9 * 60))

        assertTrue(plan.dailyDue)
        assertEquals(now + ReminderPlanner.SOON_DELAY_MS, plan.daily!!.atMillis)
        assertEquals(ReminderPlanner.dailyKey(today), plan.daily!!.key)
        assertEquals(plan.daily, plan.nextAny)
    }

    @Test
    fun `a daily reminder already shown today waits for tomorrow's hour`() {
        val today = jalaliDay(1405, 5, 10)
        val now = at(today, 10 * 60)
        val fired = setOf(ReminderPlanner.dailyKey(today))

        val plan = ReminderPlanner.plan(
            emptyList(),
            now,
            settings(minuteOfDay = 9 * 60),
            firedKeys = fired,
        )

        assertFalse(plan.dailyDue)
        assertEquals(at(today + 1, 9 * 60), plan.daily!!.atMillis)
        assertEquals(ReminderPlanner.dailyKey(today + 1), plan.daily!!.key)
    }

    @Test
    fun `the daily line carries the nearest due date for its sentence`() {
        val today = jalaliDay(1405, 5, 10)
        val now = at(today, 8 * 60)
        val nearest = item(dueDay = today + 2, label = "وام مسکن")
        val later = item(id = "commitment:c2", dueDay = today + 9, label = "چک")

        val plan = ReminderPlanner.plan(
            listOf(later, nearest),
            now,
            settings(minuteOfDay = 9 * 60),
        )

        assertEquals("وام مسکن", plan.daily!!.item!!.label)
        assertEquals(2, plan.daily!!.daysUntilDue!!)
        assertEquals(1, plan.dueSoonCount)
    }

    // ─────────────────────────── disabled settings ───────────────────────────

    @Test
    fun `disabled settings plan nothing at all`() {
        val today = jalaliDay(1405, 5, 10)
        val now = at(today, 8 * 60)

        val plan = ReminderPlanner.plan(
            listOf(item(dueDay = today)),
            now,
            settings(enabled = false),
        )

        assertTrue(plan.isEmpty)
        assertTrue(plan.oneShots.isEmpty())
        assertNull(plan.daily)
        assertFalse(plan.dailyDue)
        assertEquals(0, plan.dueSoonCount)
    }

    @Test
    fun `an out-of-range lead time is clamped rather than trusted`() {
        val today = jalaliDay(1405, 5, 10)
        val now = at(today, 10 * 60)

        val fire = ReminderPlanner.plan(
            listOf(item(dueDay = today + 20)),
            now,
            settings(leadDays = 999),
        ).oneShots.single()

        // 30 days before a date 20 days out is ten days ago, so the nudge is immediate.
        assertTrue(fire.immediate)
    }

    // ─────────────────────────── timezone ───────────────────────────

    @Test
    fun `the hour is a local hour and the offset is the only difference`() {
        val day = jalaliDay(1405, 5, 10)
        val item = item(dueDay = day)

        val tehran = ReminderPlanner.plan(
            listOf(item),
            at(day, 8 * 60),
            settings(leadDays = 0),
            zoneOffsetMs = TEHRAN_OFFSET_MS,
        ).oneShots.single()
        // The same wall clock in UTC.
        val utc = ReminderPlanner.plan(
            listOf(item),
            day * DAY_MS + 8 * hour,
            settings(leadDays = 0),
            zoneOffsetMs = 0L,
        ).oneShots.single()

        assertEquals(9 * 60, ReminderPlanner.localMinuteOfDay(tehran.atMillis, TEHRAN_OFFSET_MS))
        assertEquals(9 * 60, ReminderPlanner.localMinuteOfDay(utc.atMillis, 0L))
        assertEquals(TEHRAN_OFFSET_MS, utc.atMillis - tehran.atMillis)
    }

    @Test
    fun `a shift out of quiet is forward and lands exactly on the end`() {
        val day = jalaliDay(1405, 5, 10)
        val quiet = QuietHours(enabled = true, startMinuteOfDay = 22 * 60, endMinuteOfDay = 7 * 60)

        val shifted = ReminderPlanner.shiftOutOfQuiet(at(day, 23 * 60), quiet)

        assertEquals(at(day + 1, 7 * 60), shifted)
        assertEquals(7 * 60, ReminderPlanner.localMinuteOfDay(shifted))
    }

    @Test
    fun `an already-quiet-free instant is left untouched`() {
        val day = jalaliDay(1405, 5, 10)
        val quiet = QuietHours(enabled = true, startMinuteOfDay = 23 * 60, endMinuteOfDay = 8 * 60)
        val instant = at(day, 9 * 60)

        assertEquals(instant, ReminderPlanner.shiftOutOfQuiet(instant, quiet))
    }

    @Test
    fun `fired key for one item does not suppress another`() {
        val today = jalaliDay(1405, 5, 10)
        val now = at(today, 10 * 60)
        val first = item(id = "commitment:a", dueDay = today + 1)
        val second = item(id = "commitment:b", dueDay = today + 1)

        val plan = ReminderPlanner.plan(
            listOf(first, second),
            now,
            settings(leadDays = 3),
            firedKeys = setOf(ReminderPlanner.dueKey(first)),
        )

        assertEquals(1, plan.oneShots.size)
        assertEquals(ReminderPlanner.dueKey(second), plan.oneShots.single().key)
    }

    @Test
    fun `the plan is sorted so the earliest reminder is the one armed`() {
        val today = jalaliDay(1405, 5, 10)
        val now = at(today, 8 * 60)
        val late = item(id = "commitment:late", dueDay = today + 12)
        val earlier = item(id = "commitment:early", dueDay = today + 5)

        val plan = ReminderPlanner.plan(
            listOf(late, earlier),
            now,
            settings(leadDays = 2),
        )

        assertEquals(ReminderPlanner.dueKey(earlier), plan.nextOneShot!!.key)
        assertNotNull(plan.nextAny)
    }

    // ─────────────────────────── helpers ───────────────────────────

    private fun at(day: Long, minuteOfDay: Int): Long =
        tehranDayStart(day) + minuteOfDay * minute

    private fun settings(
        enabled: Boolean = true,
        leadDays: Int = 0,
        minuteOfDay: Int = 9 * 60,
        quiet: QuietHours = QuietHours.DISABLED,
    ): ReminderSettings = ReminderSettings(
        enabled = enabled,
        leadDays = leadDays,
        minuteOfDay = minuteOfDay,
        quietHours = quiet,
    )

    private fun item(
        id: String = "commitment:c1",
        kind: ReminderKind = ReminderKind.CHECK,
        dueDay: Long,
        label: String = "چک بانک ملت",
    ): ReminderItem = ReminderItem(
        id = id,
        kind = kind,
        dueDay = dueDay,
        label = label,
    )
}
