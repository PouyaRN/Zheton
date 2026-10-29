package com.doxigo.muchtoman

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The billing cycle is a half-open window `[start, nextStart)`, never a Jalali month and never
 * a leap-table arithmetic exercise. The window is discovered by scanning day anchors, so a 29th
 * that does not exist in some month can never trap the math.
 */
class CycleTest {

    private val salary29 = CycleSpec(mode = CycleMode.ANCHOR, anchorDay = 29)

    @Test
    fun `payday anchors never emit empty cycles`() {
        // Exactly the trap the feature request was filed against: a 29th-of-Jalali salary with
        // Esfand short. Day-anchored starts and month-anchored ends must line up 1:1 or the
        // window becomes inverted and every report reads zero.
        var day = jalaliDay(1404, 8, 1)
        repeat(14) {
            val cycle = cycleWindow(day, salary29)
            assertTrue(cycle.endDay > cycle.startDay)
            day += jalaliMonthLength(jalaliOf(day).year, jalaliOf(day).month)
        }
    }

    @Test
    fun `window scan finds the current cycle and its boundaries`() {
        // The 25th of Aban with a 29th anchor sits in the cycle that began on the 29th of Mehr.
        val day = jalaliDay(1404, 8, 25)
        val window = cycleWindow(day, salary29)
        assertEquals(jalaliDay(1404, 7, 29), window.startDay)
        assertEquals(jalaliDay(1404, 8, 29), window.endDay)
    }

    @Test
    fun `anchor capped at month length keeps every day reachable`() {
        // Anchor 31 seen from Mehr 10th: Mehr has no 31st, and its capped 30th has not arrived,
        // so the cycle still running is the one that began on Shahrivar 31.
        val start = cycleStartOfDay(jalaliDay(1404, 7, 10), CycleSpec(CycleMode.ANCHOR, 31))
        assertEquals(jalaliDay(1404, 6, 31), start)

        // Anchor 31 seen from Mehr 30th: the capped anchor is today, so a new cycle starts now.
        assertEquals(jalaliDay(1404, 7, 30), cycleStartOfDay(jalaliDay(1404, 7, 30), CycleSpec(CycleMode.ANCHOR, 31)))

        // Anchor 30 in Esfand of a common year caps to the 29th, and the 29th itself starts it.
        val spec30 = CycleSpec(CycleMode.ANCHOR, 30)
        assertEquals(jalaliDay(1404, 11, 30), cycleStartOfDay(jalaliDay(1404, 12, 5), spec30))
        assertEquals(jalaliDay(1404, 12, 29), cycleStartOfDay(jalaliDay(1404, 12, 29), spec30))
    }

    @Test
    fun `entries on the anchor day belong to the new cycle`() {
        // The salary lands on the 29th and must count toward the cycle that starts there, not
        // the one that just ended — otherwise income never meets the month it pays for.
        val anchor = jalaliDay(1404, 8, 29)
        val before = anchor - 1
        assertTrue(cycleWindow(anchor, salary29).startDay == anchor)
        assertTrue(cycleWindow(before, salary29).endDay == anchor)
    }

    @Test
    fun `month mode is the plain Jalali month`() {
        val day = jalaliDay(1404, 8, 25)
        val window = cycleWindow(day, CycleSpec(CycleMode.JALALI_MONTH))
        assertEquals(jalaliDay(1404, 8, 1), window.startDay)
        // Exclusive end, in the same convention monthReport uses: first day of the next month.
        assertEquals(jalaliDay(1404, 8, 1) + jalaliMonthLength(1404, 8), window.endDay)
    }

    @Test
    fun `long anchors scroll back across more than one month`() {
        // Anchor 29 seen from the 2nd of the month resolves to last month's 29th.
        val start = cycleStartOfDay(jalaliDay(1404, 8, 2), salary29)
        assertEquals(jalaliDay(1404, 7, 29), start)
    }

    @Test
    fun `reportWindowForCycle matches monthReport windowing`() {
        val today = jalaliDay(1404, 8, 25)
        val window = cycleWindow(today, salary29)
        val report = reportWindowForCycle(today, salary29)
        assertEquals(window.startDay, report.first)
        assertEquals(window.endDay, report.second)
    }

    @Test
    fun `anchor 1 is indistinguishable from the calendar month`() {
        val day = jalaliDay(1404, 8, 25)
        val anchored = cycleWindow(day, CycleSpec(CycleMode.ANCHOR, 1))
        val calendar = cycleWindow(day, CycleSpec(CycleMode.JALALI_MONTH))
        assertEquals(calendar.startDay, anchored.startDay)
        assertEquals(calendar.endDay, anchored.endDay)
    }
}
