package com.doxigo.muchtoman

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SafeSpendEngineTest {

    @Test
    fun `dayOfWeekWeight allocates highest weights to Iranian weekend`() {
        assertEquals(1.00, SafeSpendEngine.dayOfWeekWeight(1), 0.001) // Shanbeh (Sat)
        assertEquals(1.00, SafeSpendEngine.dayOfWeekWeight(2), 0.001) // Yekshanbeh (Sun)
        assertEquals(1.00, SafeSpendEngine.dayOfWeekWeight(3), 0.001) // Doshanbeh (Mon)
        assertEquals(1.00, SafeSpendEngine.dayOfWeekWeight(4), 0.001) // Seshanbeh (Tue)
        assertEquals(1.15, SafeSpendEngine.dayOfWeekWeight(5), 0.001) // Chaharshanbeh (Wed)
        assertEquals(1.40, SafeSpendEngine.dayOfWeekWeight(6), 0.001) // Panjshanbeh (Thu)
        assertEquals(1.25, SafeSpendEngine.dayOfWeekWeight(7), 0.001) // Jom'eh (Fri)
    }

    @Test
    fun `computePacing isolates upcoming liabilities from safe spend`() {
        val liquidCash = 10_000_000L
        val upcomingLiabilities = 4_000_000L // loan installment
        val cycleDay = 15
        val totalDays = 30
        val currentDOW = 1 // Saturday

        val result = SafeSpendEngine.computePacing(
            liquidCashToman = liquidCash,
            upcomingLiabilitiesToman = upcomingLiabilities,
            cycleDay = cycleDay,
            totalCycleDays = totalDays,
            currentDayOfWeek = currentDOW,
            actualSpentSoFarToman = 3_000_000L,
            targetMonthlyBudgetToman = 8_000_000L,
            recentDailyBurnToman = 200_000L,
        )

        // Discretionary buffer must be exactly 6,000,000 Toman
        assertEquals(6_000_000L, result.discretionaryBufferToman)
        assertEquals(upcomingLiabilities, result.committedLiabilitiesToman)
        assertTrue("Safe spend today must be greater than 0", result.safeSpendTodayToman > 0L)
        // With 16 days remaining and 6M budget, daily spend is ~375k
        assertTrue("Safe spend should be around 300k-400k", result.safeSpendTodayToman in 300_000L..420_000L)
    }

    @Test
    fun `computePacing handles zero or deficit runway safely`() {
        val liquidCash = 2_000_000L
        val upcomingLiabilities = 5_000_000L // deficit!

        val result = SafeSpendEngine.computePacing(
            liquidCashToman = liquidCash,
            upcomingLiabilitiesToman = upcomingLiabilities,
            cycleDay = 10,
            totalCycleDays = 30,
            currentDayOfWeek = 1,
            actualSpentSoFarToman = 4_000_000L,
            targetMonthlyBudgetToman = 6_000_000L,
            recentDailyBurnToman = 300_000L,
        )

        assertEquals(0L, result.discretionaryBufferToman)
        assertEquals(0L, result.safeSpendTodayToman)
        assertEquals(SafeSpendEngine.PaceStatus.DEFICIT_RISK, result.paceStatus)
        assertEquals(10, result.exhaustionCycleDay)
        assertEquals(0, result.daysUntilExhaustion)
    }

    @Test
    fun `computePacing simulates projected exhaustion day correctly`() {
        val liquidCash = 5_000_000L
        val liabilities = 0L
        val cycleDay = 10
        val totalDays = 30
        val currentDOW = 1 // Saturday
        val heavyBurnDaily = 1_000_000L // 1M/day burn with 5M remaining -> runs out in ~5 days

        val result = SafeSpendEngine.computePacing(
            liquidCashToman = liquidCash,
            upcomingLiabilitiesToman = liabilities,
            cycleDay = cycleDay,
            totalCycleDays = totalDays,
            currentDayOfWeek = currentDOW,
            actualSpentSoFarToman = 8_000_000L,
            targetMonthlyBudgetToman = 10_000_000L,
            recentDailyBurnToman = heavyBurnDaily,
        )

        assertNotNull(result.exhaustionCycleDay)
        // Should run out around day 14 or 15 (before day 30)
        assertTrue("Exhaustion day should be before month end", result.exhaustionCycleDay!! < 30)
        assertTrue("Days until exhaustion should be around 4-6 days", result.daysUntilExhaustion in 4..6)
    }

    @Test
    fun `analyzeVariance identifies single dominant overspent category`() {
        val currentCatSpend = mapOf(
            "cat_food" to 6_000_000L,
            "cat_transport" to 1_000_000L,
        )
        val priorCatSpend = mapOf(
            "cat_food" to 2_000_000L, // +4,000,000 increase!
            "cat_transport" to 1_000_000L,
        )
        val names = mapOf(
            "cat_food" to "رستوران و کافه",
            "cat_transport" to "حمل و نقل",
        )

        val insight = SafeSpendEngine.analyzeVariance(currentCatSpend, priorCatSpend, names)

        assertTrue(insight.isSignificantIncrease)
        assertEquals(133, insight.percentageDelta) // (7M - 3M) / 3M = +133%
        assertEquals("رستوران و کافه", insight.topDriverCategoryName)
        assertEquals(100, insight.topDriverAttributionPct)
        assertTrue(insight.diagnosticTextFa.contains("رستوران و کافه"))
        assertTrue(insight.diagnosticTextFa.contains("رشد"))
    }

    @Test
    fun `analyzeVariance identifies quiet win when expenses drop`() {
        val currentCatSpend = mapOf(
            "cat_shopping" to 1_000_000L,
        )
        val priorCatSpend = mapOf(
            "cat_shopping" to 3_000_000L,
        )
        val names = mapOf(
            "cat_shopping" to "خرید و پوشاک",
        )

        val insight = SafeSpendEngine.analyzeVariance(currentCatSpend, priorCatSpend, names)

        assertTrue(insight.isSignificantDecrease)
        assertEquals(-66, insight.percentageDelta)
        assertEquals("خرید و پوشاک", insight.topDriverCategoryName)
        assertTrue(insight.diagnosticTextFa.contains("صرفه‌جویی"))
        assertTrue(insight.diagnosticTextFa.contains("خرید و پوشاک"))
    }
}
