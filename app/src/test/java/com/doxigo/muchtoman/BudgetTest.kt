package com.doxigo.muchtoman

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** The monthly budget and daily share: every figure checkable by hand, and they add up. */
class BudgetTest {

    private var n = 0

    private fun entry(
        day: Long,
        signed: Long,
        category: String = "خوراک",
        transfer: Boolean = false,
        hidden: Boolean = false,
    ): LedgerEntry {
        n++
        val ref = "s:%04d:0".format(n)
        return LedgerEntry(
            txn = Txn(
                ref = ref, srcHash = ref, seq = 0, at = tehranDayStart(day), day = day,
                bank = "SAMAN", accountId = "SAMAN",
                direction = if (signed > 0) "in" else "out",
                amountRial = kotlin.math.abs(signed), signedRial = signed,
                balanceRial = null, feeRial = null, mask = "", instrument = "unknown",
                merchant = "", merchantNorm = "", refNo = "", printedAt = "",
                channel = "unknown", unitPrinted = "none", inferred = false,
                parserVer = PARSER_VERSION,
            ),
            categoryId = "cat_x", categoryFa = category, confidence = 95,
            needsReview = false, duplicate = false, transfer = transfer, hidden = hidden,
        )
    }

    private val aban1 = jalaliDay(1405, 8, 1)
    private val today = aban1 + 5 // روز ۶ آبان
    private val calendar = CycleSpec()

    private fun active(entries: List<LedgerEntry>, budgetToman: Long = 24_000_000, day: Long = today, spec: CycleSpec = calendar) =
        monthlyBudget(entries, budgetToman, day, spec) as MonthlyBudget.Active

    private fun assertAddsUp(b: MonthlyBudget.Active) {
        assertEquals(b.budgetRial, b.spentRial + b.remainingRial)
        assertEquals(b.remainingRial.coerceAtLeast(0) / b.cycle.daysLeft, b.dailyShareRial)
        assertEquals(b.budgetRial * b.cycle.dayOfCycle / b.cycle.totalDays, b.planToDateRial)
        assertEquals(b.spentRial - b.planToDateRial, b.gapRial)
        assertEquals(b.cycle.totalDays, b.cycle.dayOfCycle + b.cycle.daysLeft - 1)
    }

    @Test
    fun `no budget says so and still knows the cycle`() {
        val b = monthlyBudget(listOf(entry(aban1, -10_000_000)), 0, today, calendar)
        assertTrue(b is MonthlyBudget.Unset)
        assertEquals(6, b.cycle.dayOfCycle)
        assertEquals(30, b.cycle.totalDays)
        assertEquals(25, b.cycle.daysLeft)
        assertEquals("آبان", b.cycle.monthNameFa)
        assertEquals(10_000_000L, b.cycle.spentRial)
    }

    @Test
    fun `rent explains being ahead of plan`() {
        val rows = listOf(
            entry(aban1, -50_000_000, category = "اجاره"),
            entry(aban1 + 2, -20_000_000),
            entry(aban1 + 4, -20_000_000),
        )
        val b = active(rows)
        assertAddsUp(b)
        assertEquals(240_000_000L, b.budgetRial)
        assertEquals(90_000_000L, b.spentRial)
        assertEquals(48_000_000L, b.planToDateRial)
        assertEquals(150_000_000L, b.remainingRial)
        assertEquals(6_000_000L, b.dailyShareRial) // ۶۰۰ هزار تومان
        assertEquals(37, b.spentPercent)
        assertEquals(20, b.elapsedPercent)
        assertEquals(BudgetPaceStatus.AHEAD, b.pace)
        assertEquals(BudgetDriver("اجاره", 50_000_000, BudgetDriver.Kind.EXPENSE, b.driver?.ref), b.driver)
        assertEquals("روز ۶ از ۳۰ آبان. ۵ میلیونش اجاره بوده و بقیه طبق برنامه‌ست.", b.explanationFa)
    }

    @Test
    fun `a category explains the gap when no single expense does`() {
        // Twelve 600-thousand meals: no single one covers a 2.4 million gap, the category does.
        val rows = (0 until 12).map { entry(aban1 + it % 6, -6_000_000, category = "رستوران") }
        val b = active(rows)
        assertAddsUp(b)
        assertEquals(BudgetPaceStatus.AHEAD, b.pace)
        assertEquals(BudgetDriver.Kind.CATEGORY, b.driver?.kind)
        assertEquals("رستوران", b.driver?.labelFa)
        assertEquals("روز ۶ از ۳۰ آبان. ${faCompact(7_200_000L)}ش خرج رستوران بوده و بقیه طبق برنامه‌ست.", b.explanationFa)
    }

    @Test
    fun `ahead with no single explanation names the gap`() {
        val cats = listOf("الف", "ب", "پ", "ت", "ث", "ج")
        val rows = cats.mapIndexed { i, c -> entry(aban1 + i, -15_000_000, category = c) }
        val b = active(rows)
        assertAddsUp(b)
        assertEquals(BudgetPaceStatus.AHEAD, b.pace)
        assertNull(b.driver)
        assertEquals(42_000_000L, b.gapRial)
        assertEquals("روز ۶ از ۳۰ آبان. ${faCompact(4_200_000L)} جلوتر از برنامه خرج کردی.", b.explanationFa)
    }

    @Test
    fun `on plan and under plan`() {
        val onPlan = active(listOf(entry(aban1 + 3, -46_000_000)))
        assertAddsUp(onPlan)
        assertEquals(BudgetPaceStatus.ON_PLAN, onPlan.pace)
        assertEquals("روز ۶ از ۳۰ آبان. خرجت طبق برنامه‌ست.", onPlan.explanationFa)

        val under = active(listOf(entry(aban1, -8_000_000)))
        assertAddsUp(under)
        assertEquals(BudgetPaceStatus.UNDER, under.pace)
        assertEquals("روز ۶ از ۳۰ آبان. ۴ میلیون کمتر از برنامه خرج کردی.", under.explanationFa)
    }

    @Test
    fun `overspent budget never gives a negative share`() {
        val b = active(listOf(entry(aban1, -250_000_000, category = "اجاره")))
        assertAddsUp(b)
        assertEquals(-10_000_000L, b.remainingRial)
        assertEquals(0L, b.dailyShareRial)
        assertEquals(BudgetPaceStatus.OVER_BUDGET, b.pace)
        assertTrue(b.explanationFa.contains("بیشتر از بودجه"))
    }

    @Test
    fun `income transfers hidden rows and other months do not count`() {
        val rows = listOf(
            entry(aban1, 500_000_000),
            entry(aban1, -30_000_000, transfer = true),
            entry(aban1, -30_000_000, hidden = true),
            entry(aban1 - 1, -30_000_000),
            entry(today + 1, -30_000_000),
            entry(today, -10_000_000),
        )
        assertEquals(10_000_000L, active(rows).spentRial)
    }

    @Test
    fun `payday cycle sets the window and the last day keeps one day left`() {
        val spec = CycleSpec(CycleMode.ANCHOR, 25)
        val day = jalaliDay(1405, 8, 24) // last day of the cycle that began on 25 Mehr
        val b = active(listOf(entry(jalaliDay(1405, 7, 25), -10_000_000)), day = day, spec = spec)
        assertAddsUp(b)
        assertEquals(30, b.cycle.totalDays)
        assertEquals(30, b.cycle.dayOfCycle)
        assertEquals(1, b.cycle.daysLeft)
        assertEquals(230_000_000L, b.dailyShareRial)
        assertEquals("آبان", b.cycle.monthNameFa)
    }
}
