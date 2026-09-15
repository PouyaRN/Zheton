package com.doxigo.muchtoman

import org.junit.Assert.*
import org.junit.Test
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToLong

/**
 * Verification of the Non-Linear Safe Daily Spend & Cycle Runway Engine.
 */
class SafeDailySpendAlgorithmTest {

    // ─────────────────────────────────────────────────────────────────────────────
    // Domain Models & Math Engine for Safe Daily Spend
    // ─────────────────────────────────────────────────────────────────────────────

    enum class IranianDayOfWeek(val index: Int, val faName: String, val baseWeight: Double) {
        SATURDAY(0, "شنبه", 0.80),
        SUNDAY(1, "یکشنبه", 0.75),
        MONDAY(2, "دوشنبه", 0.75),
        TUESDAY(3, "سه‌شنبه", 0.80),
        WEDNESDAY(4, "چهارشنبه", 1.05),
        THURSDAY(5, "پنج‌شنبه", 1.40),
        FRIDAY(6, "جمعه", 1.45);

        companion object {
            fun fromTehranDay(day: Long): IranianDayOfWeek {
                val dowIndex = (((day - 2) % 7 + 7) % 7).toInt()
                return entries[dowIndex]
            }
        }
    }

    data class DayOfWeekWeights(
        val weights: DoubleArray = doubleArrayOf(0.80, 0.75, 0.75, 0.80, 1.05, 1.40, 1.45)
    ) {
        init {
            require(weights.size == 7) { "Weights must have exactly 7 elements" }
            val sum = weights.sum()
            require(abs(sum - 7.0) < 1e-4) { "Weights sum must equal 7.0 (average 1.0), got $sum" }
        }

        fun weightFor(day: Long): Double {
            val dow = IranianDayOfWeek.fromTehranDay(day)
            return weights[dow.index]
        }

        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (javaClass != other?.javaClass) return false
            other as DayOfWeekWeights
            return weights.contentEquals(other.weights)
        }

        override fun hashCode(): Int = weights.contentHashCode()
    }

    enum class LiabilityType(val faName: String) {
        BANK_LOAN("وام بانکی"),
        BNPL("الان بخر، بعداً پرداخت کن (BNPL)"),
        SAYAD_CHECK("چک صیادی"),
        GOAL_RESERVATION("رزرو هدف و پس‌انداز"),
        FIXED_BILL("قبوض و هزینه‌های ثابت ماهانه");
    }

    data class ScheduledLiability(
        val id: String,
        val title: String,
        val type: LiabilityType,
        val amountToman: Long,
        val dueDay: Long,
        val isSettled: Boolean = false,
    )

    enum class PaceRegime(val faLabel: String) {
        FRUGAL("صرفه‌جویانه"),
        BALANCED("متعادل و روی خط"),
        ELEVATED("شتاب مصرف (هشدار)"),
        CRITICAL("مصرف بحرانی (خطر کسری)");
    }

    data class SafeSpendSnapshot(
        val safeSpendTodayToman: Long,
        val liquidCashToman: Long,
        val committedReservationsToman: Long,
        val discretionaryPoolToman: Long,
        val daysRemainingInCycle: Int,
        val sumRemainingWeights: Double,
        val paceVelocityIndex: Double,
        val paceRegime: PaceRegime,
        val emaDailyBurnToman: Long,
        val projectedExhaustionDay: Long,
        val daysToExhaustion: Int,
        val runwayGapDays: Int,
        val isDeficit: Boolean,
        val deficitShortfallToman: Long,
        val firstDefaultLiability: ScheduledLiability? = null,
    )

    object SafeDailySpendEngine {

        fun calculateSnapshot(
            currentDay: Long,
            liquidCashToman: Long,
            cycleSpec: CycleSpec,
            liabilities: List<ScheduledLiability>,
            discretionarySpendHistory: Map<Long, Long>, // Day -> Discretionary Spend in Toman
            initialDiscretionaryBudgetToman: Long? = null,
            weights: DayOfWeekWeights = DayOfWeekWeights(),
            emaSmoothingAlpha: Double = 0.333,
        ): SafeSpendSnapshot {
            val window = cycleWindow(currentDay, cycleSpec)
            val cycleStart = window.startDay
            val cycleEnd = window.endDay // Exclusive boundary

            require(currentDay in cycleStart until cycleEnd) {
                "Current day $currentDay must be within cycle [$cycleStart, $cycleEnd)"
            }

            // 1. Committed liabilities maturing strictly within the active cycle window [currentDay, cycleEnd)
            val activeLiabilitiesInCycle = liabilities.filter { liability ->
                !liability.isSettled && liability.dueDay in currentDay until cycleEnd
            }
            val committedTotalToman = activeLiabilitiesInCycle.sumOf { it.amountToman }

            // 2. Discretionary Pool
            val discretionaryPoolToman = liquidCashToman - committedTotalToman
            val isDeficit = discretionaryPoolToman < 0L
            val deficitShortfallToman = if (isDeficit) -discretionaryPoolToman else 0L

            // 3. Detect first defaulting liability if in deficit
            var firstDefault: ScheduledLiability? = null
            if (isDeficit) {
                var runningCash = liquidCashToman
                val sortedLiabilities = activeLiabilitiesInCycle.sortedBy { it.dueDay }
                for (item in sortedLiabilities) {
                    if (runningCash < item.amountToman) {
                        firstDefault = item
                        break
                    }
                    runningCash -= item.amountToman
                }
            }

            // 4. Day-of-week remaining weights
            var sumRemainingWeights = 0.0
            for (d in currentDay until cycleEnd) {
                sumRemainingWeights += weights.weightFor(d)
            }
            val daysRemaining = (cycleEnd - currentDay).toInt()
            val todayWeight = weights.weightFor(currentDay)

            // 5. Dynamic Daily Safe Spend
            val safeSpendTodayToman = if (isDeficit || sumRemainingWeights <= 0.0) {
                0L
            } else {
                // Month-end clustering damping: if daysRemaining <= 2, ensure non-zero division and smooth transition
                val rawSafeSpend = (discretionaryPoolToman.toDouble() / sumRemainingWeights) * todayWeight
                max(0L, rawSafeSpend.roundToLong())
            }

            // 6. Real-Time Pace Velocity Index V(t)
            // Cumulative weights from cycleStart to cycleEnd
            var totalCycleWeights = 0.0
            var elapsedWeights = 0.0
            for (d in cycleStart until cycleEnd) {
                val w = weights.weightFor(d)
                totalCycleWeights += w
                if (d <= currentDay) {
                    elapsedWeights += w
                }
            }

            val actualCumulativeSpend = discretionarySpendHistory
                .filter { it.key in cycleStart..currentDay }
                .values
                .sum()

            val baselineBudget = initialDiscretionaryBudgetToman
                ?: (discretionaryPoolToman + actualCumulativeSpend).coerceAtLeast(1L)

            val expectedCumulativeSpend = if (totalCycleWeights > 0.0) {
                baselineBudget * (elapsedWeights / totalCycleWeights)
            } else {
                baselineBudget.toDouble()
            }

            val velocityIndex = if (expectedCumulativeSpend > 0.0) {
                actualCumulativeSpend.toDouble() / expectedCumulativeSpend
            } else {
                1.0
            }

            val paceRegime = when {
                velocityIndex <= 0.85 -> PaceRegime.FRUGAL
                velocityIndex <= 1.10 -> PaceRegime.BALANCED
                velocityIndex <= 1.30 -> PaceRegime.ELEVATED
                else -> PaceRegime.CRITICAL
            }

            // 7. Projected Balance Exhaustion Day via EMA-smoothed burn rate
            val baselineDailyBurn = if (totalCycleWeights > 0.0) {
                baselineBudget / totalCycleWeights
            } else {
                1.0
            }

            var currentEma = baselineDailyBurn
            for (d in cycleStart..currentDay) {
                val spend = discretionarySpendHistory[d]
                if (spend != null) {
                    val w = weights.weightFor(d)
                    val normalizedSpend = spend.toDouble() / max(0.1, w)
                    currentEma = emaSmoothingAlpha * normalizedSpend + (1.0 - emaSmoothingAlpha) * currentEma
                }
            }
            val emaBurnToman = max(1L, currentEma.roundToLong())

            val daysToExhaustion = if (isDeficit || emaBurnToman <= 0L) {
                0
            } else {
                (discretionaryPoolToman.toDouble() / emaBurnToman.toDouble()).toInt()
            }
            val projectedExhaustionDay = currentDay + daysToExhaustion
            val runwayGapDays = (projectedExhaustionDay - cycleEnd).toInt()

            return SafeSpendSnapshot(
                safeSpendTodayToman = safeSpendTodayToman,
                liquidCashToman = liquidCashToman,
                committedReservationsToman = committedTotalToman,
                discretionaryPoolToman = discretionaryPoolToman,
                daysRemainingInCycle = daysRemaining,
                sumRemainingWeights = sumRemainingWeights,
                paceVelocityIndex = velocityIndex,
                paceRegime = paceRegime,
                emaDailyBurnToman = emaBurnToman,
                projectedExhaustionDay = projectedExhaustionDay,
                daysToExhaustion = daysToExhaustion,
                runwayGapDays = runwayGapDays,
                isDeficit = isDeficit,
                deficitShortfallToman = deficitShortfallToman,
                firstDefaultLiability = firstDefault,
            )
        }
    }

    // ─────────────────────────────────────────────────────────────────────────────
    // Algorithmic Tests & Mathematical Proofs
    // ─────────────────────────────────────────────────────────────────────────────

    @Test
    fun `mathematical conservation proof - spending exactly safe spend clears budget at cycle end`() {
        // Test a full 30-day cycle
        val cycleSpec = CycleSpec(CycleMode.JALALI_MONTH)
        // Day 1403/07/01 (Mehr 1st, 30-day month)
        val startDay = jalaliDay(1403, 7, 1)
        val endDay = jalaliDay(1403, 8, 1) // 30 days total
        assertEquals(30L, endDay - startDay)

        val initialCash = 30_000_000L // 30M Toman
        val weights = DayOfWeekWeights()

        var currentCash = initialCash
        val spendHistory = mutableMapOf<Long, Long>()

        for (day in startDay until endDay) {
            val snapshot = SafeDailySpendEngine.calculateSnapshot(
                currentDay = day,
                liquidCashToman = currentCash,
                cycleSpec = cycleSpec,
                liabilities = emptyList(),
                discretionarySpendHistory = spendHistory,
                initialDiscretionaryBudgetToman = initialCash,
                weights = weights,
            )

            val safeSpend = snapshot.safeSpendTodayToman
            assertTrue("Safe spend must be positive", safeSpend > 0L)

            // User spends exactly safeSpend
            spendHistory[day] = safeSpend
            currentCash -= safeSpend
        }

        // After 30 days of spending safeSpend, remaining cash should be negligible (0 due to integer rounding)
        assertTrue("Final cash must be nearly 0 (integer rounding within 30 Toman)", abs(currentCash) < 50L)
    }

    @Test
    fun `weekend surge weighting yields higher allowance on Thursday and Friday`() {
        val cycleSpec = CycleSpec(CycleMode.JALALI_MONTH)
        // Jalali 1403/07/01: Let's find day of week
        val startDay = jalaliDay(1403, 7, 1)
        val weights = DayOfWeekWeights()

        // Scan 7 days of the week
        val dailyAllowances = mutableMapOf<IranianDayOfWeek, Long>()
        val dummyCash = 70_000_000L

        for (offset in 0 until 7) {
            val day = startDay + offset
            val dow = IranianDayOfWeek.fromTehranDay(day)
            val snapshot = SafeDailySpendEngine.calculateSnapshot(
                currentDay = day,
                liquidCashToman = dummyCash,
                cycleSpec = cycleSpec,
                liabilities = emptyList(),
                discretionarySpendHistory = emptyMap(),
                weights = weights,
            )
            dailyAllowances[dow] = snapshot.safeSpendTodayToman
        }

        val thuSpend = dailyAllowances[IranianDayOfWeek.THURSDAY]!!
        val friSpend = dailyAllowances[IranianDayOfWeek.FRIDAY]!!
        val sunSpend = dailyAllowances[IranianDayOfWeek.SUNDAY]!!

        // Friday spend should be ~1.9x Sunday spend
        val ratioFriSun = friSpend.toDouble() / sunSpend.toDouble()
        assertTrue("Friday spend must be substantially greater than Sunday spend", ratioFriSun > 1.8)
        assertTrue("Thursday spend must also be elevated", thuSpend > sunSpend)
    }

    @Test
    fun `committed liabilities deduction prevents phantom liquidity`() {
        val currentDay = jalaliDay(1403, 7, 10)
        val cycleSpec = CycleSpec(CycleMode.JALALI_MONTH)

        val liquidCash = 25_000_000L // 25M Toman

        // Scheduled BNPL (SnappPay) due on 7/25: 4M
        // Sayad check due on 7/20: 10M
        // Bank loan installment due on 7/15: 3M
        // Liability outside cycle (due next month 8/05): 6M (MUST NOT BE DEDUCTED)
        val liabilities = listOf(
            ScheduledLiability("1", "اسنپ‌پی", LiabilityType.BNPL, 4_000_000L, jalaliDay(1403, 7, 25)),
            ScheduledLiability("2", "چک صیادی شرکت", LiabilityType.SAYAD_CHECK, 10_000_000L, jalaliDay(1403, 7, 20)),
            ScheduledLiability("3", "وام مسکن", LiabilityType.BANK_LOAN, 3_000_000L, jalaliDay(1403, 7, 15)),
            ScheduledLiability("4", "قسط ماه بعد", LiabilityType.BANK_LOAN, 6_000_000L, jalaliDay(1403, 8, 5)),
        )

        val snapshot = SafeDailySpendEngine.calculateSnapshot(
            currentDay = currentDay,
            liquidCashToman = liquidCash,
            cycleSpec = cycleSpec,
            liabilities = liabilities,
            discretionarySpendHistory = emptyMap(),
        )

        // Committed should be 4M + 10M + 3M = 17M (excluding the 6M outside cycle)
        assertEquals(17_000_000L, snapshot.committedReservationsToman)
        // Discretionary pool = 25M - 17M = 8M
        assertEquals(8_000_000L, snapshot.discretionaryPoolToman)
        assertFalse(snapshot.isDeficit)
        assertTrue(snapshot.safeSpendTodayToman > 0L)
    }

    @Test
    fun `deficit triage correctly detects first defaulting liability`() {
        val currentDay = jalaliDay(1403, 7, 5)
        val cycleSpec = CycleSpec(CycleMode.JALALI_MONTH)

        val liquidCash = 8_000_000L // Only 8M in bank

        // Liabilities:
        // 1. SnappPay 5M on 7/10 -> Covered (8M - 5M = 3M left)
        // 2. Sayad check 10M on 7/15 -> DEFAULTS! (3M < 10M)
        val liabilities = listOf(
            ScheduledLiability("bnpl-1", "اسنپ‌پی", LiabilityType.BNPL, 5_000_000L, jalaliDay(1403, 7, 10)),
            ScheduledLiability("check-1", "چک اجاره صیادی", LiabilityType.SAYAD_CHECK, 10_000_000L, jalaliDay(1403, 7, 15)),
        )

        val snapshot = SafeDailySpendEngine.calculateSnapshot(
            currentDay = currentDay,
            liquidCashToman = liquidCash,
            cycleSpec = cycleSpec,
            liabilities = liabilities,
            discretionarySpendHistory = emptyMap(),
        )

        assertTrue(snapshot.isDeficit)
        assertEquals(7_000_000L, snapshot.deficitShortfallToman) // 15M committed - 8M cash = 7M deficit
        assertEquals(0L, snapshot.safeSpendTodayToman)
        assertNotNull(snapshot.firstDefaultLiability)
        assertEquals("check-1", snapshot.firstDefaultLiability?.id)
        assertEquals("چک اجاره صیادی", snapshot.firstDefaultLiability?.title)
    }

    @Test
    fun `front-loaded lump-sum rent payment does not distort discretionary velocity`() {
        val cycleSpec = CycleSpec(CycleMode.JALALI_MONTH)
        val day1 = jalaliDay(1403, 7, 1)

        val initialCash = 50_000_000L // 50M total
        val rentObligation = ScheduledLiability("rent", "اجاره خانه", LiabilityType.FIXED_BILL, 30_000_000L, day1)

        // Snapshot BEFORE paying rent on Day 1:
        val snapBefore = SafeDailySpendEngine.calculateSnapshot(
            currentDay = day1,
            liquidCashToman = initialCash,
            cycleSpec = cycleSpec,
            liabilities = listOf(rentObligation),
            discretionarySpendHistory = emptyMap(),
            initialDiscretionaryBudgetToman = 20_000_000L,
        )

        assertEquals(20_000_000L, snapBefore.discretionaryPoolToman)
        val safeDay1 = snapBefore.safeSpendTodayToman

        // User pays 30M rent (settled obligation, bank cash decreases by 30M, discretionary spend is 0)
        val cashAfterRent = 20_000_000L
        val settledRent = rentObligation.copy(isSettled = true)

        val snapAfter = SafeDailySpendEngine.calculateSnapshot(
            currentDay = day1,
            liquidCashToman = cashAfterRent,
            cycleSpec = cycleSpec,
            liabilities = listOf(settledRent),
            discretionarySpendHistory = emptyMap(),
            initialDiscretionaryBudgetToman = 20_000_000L,
        )

        // Discretionary pool must remain identical (20M)!
        assertEquals(20_000_000L, snapAfter.discretionaryPoolToman)
        assertEquals(safeDay1, snapAfter.safeSpendTodayToman)
        // Pacing velocity should remain 0 or balanced, not 1500% over-budget!
        assertEquals(0.0, snapAfter.paceVelocityIndex, 0.01)
        assertEquals(PaceRegime.FRUGAL, snapAfter.paceRegime)
    }

    @Test
    fun `pace velocity regimes transition accurately based on spend burn`() {
        val cycleSpec = CycleSpec(CycleMode.JALALI_MONTH)
        val startDay = jalaliDay(1403, 7, 1)
        val midDay = jalaliDay(1403, 7, 15) // Day 15 (halfway)

        val budget = 30_000_000L

        // Halfway expected spend is approx 50% of budget = 15M
        // Case 1: Frugal (spend only 8M)
        val snapFrugal = SafeDailySpendEngine.calculateSnapshot(
            currentDay = midDay,
            liquidCashToman = budget - 8_000_000L,
            cycleSpec = cycleSpec,
            liabilities = emptyList(),
            discretionarySpendHistory = mapOf(midDay to 8_000_000L),
            initialDiscretionaryBudgetToman = budget,
        )
        assertTrue(snapFrugal.paceVelocityIndex < 0.85)
        assertEquals(PaceRegime.FRUGAL, snapFrugal.paceRegime)

        // Case 2: Balanced (spend 15M)
        val snapBalanced = SafeDailySpendEngine.calculateSnapshot(
            currentDay = midDay,
            liquidCashToman = budget - 15_000_000L,
            cycleSpec = cycleSpec,
            liabilities = emptyList(),
            discretionarySpendHistory = mapOf(midDay to 15_000_000L),
            initialDiscretionaryBudgetToman = budget,
        )
        assertTrue(snapBalanced.paceVelocityIndex in 0.85..1.10)
        assertEquals(PaceRegime.BALANCED, snapBalanced.paceRegime)

        // Case 3: Elevated (spend 18M)
        val snapElevated = SafeDailySpendEngine.calculateSnapshot(
            currentDay = midDay,
            liquidCashToman = budget - 18_000_000L,
            cycleSpec = cycleSpec,
            liabilities = emptyList(),
            discretionarySpendHistory = mapOf(midDay to 18_000_000L),
            initialDiscretionaryBudgetToman = budget,
        )
        assertTrue(snapElevated.paceVelocityIndex in 1.10..1.30)
        assertEquals(PaceRegime.ELEVATED, snapElevated.paceRegime)

        // Case 4: Critical (spend 24M halfway through month!)
        val snapCritical = SafeDailySpendEngine.calculateSnapshot(
            currentDay = midDay,
            liquidCashToman = budget - 24_000_000L,
            cycleSpec = cycleSpec,
            liabilities = emptyList(),
            discretionarySpendHistory = mapOf(midDay to 24_000_000L),
            initialDiscretionaryBudgetToman = budget,
        )
        assertTrue(snapCritical.paceVelocityIndex > 1.30)
        assertEquals(PaceRegime.CRITICAL, snapCritical.paceRegime)
        assertTrue("Runway gap must be negative (exhausts before month end)", snapCritical.runwayGapDays < 0)
    }
}
