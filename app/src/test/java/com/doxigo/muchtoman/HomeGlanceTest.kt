package com.doxigo.muchtoman

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** The Today tab's figures: real data or nothing, never a made-up number. */
class HomeGlanceTest {

    private val aban1 = jalaliDay(1405, 8, 1)
    private val today = aban1 + 5

    @Test
    fun `no history means no change and no curve`() {
        val g = netWorthGlance(emptyMap(), 66_400_000.0, today)
        assertNull(g.changeToman)
        assertNull(g.changePercent)
        assertTrue(g.spark.isEmpty())
        assertEquals(66_400_000.0, g.toman, 0.0)
    }

    @Test
    fun `change is measured from the last snapshot at the month's start`() {
        val history = mapOf(aban1 - 20 to 10.0, aban1 - 1 to 65_000_000.0, aban1 + 2 to 65_500_000.0)
        val g = netWorthGlance(history, 66_365_000.0, today)
        assertEquals(1_365_000.0, g.changeToman!!, 0.001)
        assertEquals(2.1, g.changePercent!!, 0.001)
        // Oldest first, ending at today's figure.
        assertEquals(listOf(10.0, 65_000_000.0, 65_500_000.0, 66_365_000.0), g.spark)
    }

    @Test
    fun `a snapshot older than a week before the month is not passed off as its start`() {
        val g = netWorthGlance(mapOf(aban1 - 20 to 50.0), 60.0, today)
        assertNull(g.changeToman)
    }

    @Test
    fun `safe spend from cash spreads liquid money over the days left`() {
        assertEquals(400_000L, safeSpendFromCash(10_000_000.0, 25))
        assertNull(safeSpendFromCash(0.0, 25))
    }

    @Test
    fun `main sheba is the largest switched-on account that has one`() {
        val a = BankAccount(bank = "SAMAN", balance = 900.0, sheba = "IR1")
        val b = BankAccount(bank = "MELLAT", balance = 500.0, sheba = "IR2")
        val c = BankAccount(bank = "TEJARAT", balance = 9_999.0)
        assertEquals("MELLAT", mainShebaAccount(listOf(a, b, c), setOf("SAMAN"))?.bank)
        assertNull(mainShebaAccount(listOf(c), emptySet()))
    }

    @Test
    fun `month line speaks in the budget's unit`() {
        val unit = budgetUnit(18_000_000.0)
        assertEquals("میلیون", unit.word)
        assertEquals("۶٫۴۸", unit.figure(6_489_000.0))
        assertEquals("۱۸", unit.figure(18_000_000.0))
    }

    @Test
    fun `weekday date and clock read in Tehran time`() {
        val d = faWeekdayDate(today)
        assertTrue(d, d.endsWith("۶ آبان"))
        assertEquals("۱۰:۴۲", faClock(tehranDayStart(today) + (10 * 60 + 42) * 60_000L))
    }
}
