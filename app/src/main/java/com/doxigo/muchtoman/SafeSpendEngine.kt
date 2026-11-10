package com.doxigo.muchtoman

import kotlin.math.abs
import kotlin.math.max

/**
 * Pure Kotlin Financial Decision Engine for ChandToman.
 *
 * Implements:
 * 1. Non-linear Safe Daily Spend (خرج امن روزانه) with Iranian work-week weighting and committed liability isolation.
 * 2. Real-time Burn Velocity Index and projected cash exhaustion date (روز اتمام بودجه).
 * 3. Robust period-over-period category variance attribution (ریشه انحراف بودجه) using positive expansion weights.
 *
 * Completely independent of Android UI and Compose lifecycle.
 */
object SafeSpendEngine {

    /**
     * Converts a Tehran unix day count into Iranian day-of-week index (1=Shanbeh .. 7=Jom'eh).
     */
    fun iranianDayOfWeek(tehranDay: Long): Int = ((((tehranDay - 2) % 7 + 7) % 7).toInt()) + 1

    /**
     * Day-of-week spending weight for Iranian financial week:
     * Saturday = 1 ... Friday = 7
     * Peak discretionary surges occur on Thursday (1.40) and Friday (1.25).
     */
    fun dayOfWeekWeight(dayOfWeek1to7: Int): Double = when (dayOfWeek1to7) {
        1, 2, 3, 4 -> 1.00  // Shanbeh to Seshanbeh (Sat - Tue)
        5 -> 1.15           // Chaharshanbeh (Wed)
        6 -> 1.40           // Panjshanbeh (Thu)
        7 -> 1.25           // Jom'eh (Fri)
        else -> 1.00
    }

    enum class PaceStatus(val labelFa: String, val shortLabelFa: String, val isWarning: Boolean) {
        CONSERVATIVE("کنترل عالی (زیر سقف بودجه)", "کنترل عالی", false),
        ON_TRACK("در محدوده امن بودجه", "در بودجه امن", false),
        ELEVATED_VELOCITY("شتاب بالای خرج‌کرد", "شتاب بالا", true),
        DEFICIT_RISK("خطر کسری قبل از حقوق", "خطر کسری", true),
    }

    data class PacingDecision(
        val liquidCashToman: Long,
        val committedLiabilitiesToman: Long,
        val discretionaryBufferToman: Long,
        val safeSpendTodayToman: Long,
        val velocityIndex: Double,
        val paceStatus: PaceStatus,
        val exhaustionCycleDay: Int?,
        val daysUntilExhaustion: Int?,
        val cycleDay: Int,
        val totalCycleDays: Int,
        val remainingDays: Int,
    )

    data class VarianceInsight(
        val isSignificantIncrease: Boolean,
        val isSignificantDecrease: Boolean,
        val percentageDelta: Int,
        val topDriverCategoryName: String?,
        val topDriverAttributionPct: Int?,
        val secondDriverCategoryName: String?,
        val secondDriverAttributionPct: Int?,
        val diagnosticTextFa: String,
    )

    /**
     * Calculates the dynamic daily safe spend and cycle runway pacing.
     *
     * @param liquidCashToman Total aggregate liquid balance across active bank accounts.
     * @param upcomingLiabilitiesToman Sum of commitments/checks/installments maturing within the remaining cycle days.
     * @param cycleDay Current day index within cycle (1..totalDays).
     * @param totalCycleDays Total days in active cycle.
     * @param currentDayOfWeek 1 (Sat) to 7 (Fri).
     * @param actualSpentSoFarToman Discretionary spend so far in this cycle.
     * @param targetMonthlyBudgetToman Income or expected cycle expenditure.
     * @param recentDailyBurnToman Trailing short-term daily spend (e.g. 3-5 day average).
     */
    fun computePacing(
        liquidCashToman: Long,
        upcomingLiabilitiesToman: Long,
        cycleDay: Int,
        totalCycleDays: Int,
        currentDayOfWeek: Int,
        actualSpentSoFarToman: Long,
        targetMonthlyBudgetToman: Long,
        recentDailyBurnToman: Long,
    ): PacingDecision {
        val safeCycleDay = cycleDay.coerceIn(1, totalCycleDays)
        val remainingDays = max(1, totalCycleDays - safeCycleDay + 1)
        val discretionaryBuffer = max(0L, liquidCashToman - upcomingLiabilitiesToman)

        // 1. Calculate weighted remaining days
        var totalRemainingWeight = 0.0
        var todayWeight = dayOfWeekWeight(currentDayOfWeek)

        for (offset in 0 until remainingDays) {
            val dow = ((currentDayOfWeek - 1 + offset) % 7) + 1
            val w = dayOfWeekWeight(dow)
            if (offset == 0) todayWeight = w
            totalRemainingWeight += w
        }

        // 2. Compute dynamic Safe Daily Spend for today
        val safeToday = if (totalRemainingWeight > 0.0 && discretionaryBuffer > 0L) {
            (discretionaryBuffer * (todayWeight / totalRemainingWeight)).toLong()
        } else {
            0L
        }

        // 3. Compute Pace Velocity Index V(t)
        var totalCycleWeight = 0.0
        var weightUpToToday = 0.0
        val cycleStartDayOfWeek = ((currentDayOfWeek - 1 - (safeCycleDay - 1)) % 7 + 7) % 7 + 1

        for (d in 0 until totalCycleDays) {
            val dow = ((cycleStartDayOfWeek - 1 + d) % 7) + 1
            val w = dayOfWeekWeight(dow)
            totalCycleWeight += w
            if (d < safeCycleDay) {
                weightUpToToday += w
            }
        }

        val expectedSpendSoFar = if (totalCycleWeight > 0.0 && targetMonthlyBudgetToman > 0L) {
            targetMonthlyBudgetToman.toDouble() * (weightUpToToday / totalCycleWeight)
        } else {
            targetMonthlyBudgetToman.toDouble()
        }

        val velocity = if (expectedSpendSoFar >= 50_000.0) {
            actualSpentSoFarToman.toDouble() / expectedSpendSoFar
        } else {
            1.0
        }

        val status = when {
            velocity <= 0.85 -> PaceStatus.CONSERVATIVE
            velocity <= 1.10 -> PaceStatus.ON_TRACK
            velocity <= 1.30 -> PaceStatus.ELEVATED_VELOCITY
            else -> PaceStatus.DEFICIT_RISK
        }

        // 4. Compute projected balance exhaustion day
        var exhaustionDay: Int? = null
        var daysLeft: Int? = null

        val effectiveBurnRate = if (recentDailyBurnToman > 0L) {
            recentDailyBurnToman
        } else if (safeCycleDay > 1 && actualSpentSoFarToman > 0L) {
            actualSpentSoFarToman / (safeCycleDay - 1)
        } else {
            0L
        }

        if (effectiveBurnRate > 0L && discretionaryBuffer > 0L) {
            var simBalance = discretionaryBuffer.toDouble()
            for (step in 1..remainingDays) {
                val dow = ((currentDayOfWeek - 1 + (step - 1)) % 7) + 1
                val dailyBurn = effectiveBurnRate * (dayOfWeekWeight(dow) / 1.114)
                simBalance -= dailyBurn
                if (simBalance <= 0) {
                    exhaustionDay = safeCycleDay + step - 1
                    daysLeft = step
                    break
                }
            }
        } else if (discretionaryBuffer <= 0L && upcomingLiabilitiesToman > 0L) {
            // Immediate deficit: today is exhaustion day
            exhaustionDay = safeCycleDay
            daysLeft = 0
        }

        return PacingDecision(
            liquidCashToman = liquidCashToman,
            committedLiabilitiesToman = upcomingLiabilitiesToman,
            discretionaryBufferToman = discretionaryBuffer,
            safeSpendTodayToman = safeToday,
            velocityIndex = velocity,
            paceStatus = status,
            exhaustionCycleDay = exhaustionDay,
            daysUntilExhaustion = daysLeft,
            cycleDay = safeCycleDay,
            totalCycleDays = totalCycleDays,
            remainingDays = remainingDays,
        )
    }

    /**
     * Decomposes expenditure divergence between current cycle and prior cycle using
     * Positive Expansion Attribution:
     * A_i = Delta_C_i / sum_{k: Delta_C_k > 0} Delta_C_k
     */
    fun analyzeVariance(
        currentCatSpend: Map<String, Long>,
        priorCatSpend: Map<String, Long>,
        categoryNames: Map<String, String>,
    ): VarianceInsight {
        val currentTotal = currentCatSpend.values.sum()
        val priorTotal = priorCatSpend.values.sum()

        if (priorTotal <= 0L && currentTotal <= 0L) {
            return VarianceInsight(
                isSignificantIncrease = false,
                isSignificantDecrease = false,
                percentageDelta = 0,
                topDriverCategoryName = null,
                topDriverAttributionPct = null,
                secondDriverCategoryName = null,
                secondDriverAttributionPct = null,
                diagnosticTextFa = "مخارج در محدوده پایدار و بدون تغییر ثبت شده است.",
            )
        }

        val totalDelta = currentTotal - priorTotal
        val pctDelta = if (priorTotal > 0L) {
            ((totalDelta.toDouble() / priorTotal.toDouble()) * 100).toInt()
        } else {
            100
        }

        // Category-level deltas
        val allCatKeys = currentCatSpend.keys + priorCatSpend.keys
        val positiveDeltas = mutableListOf<Pair<String, Long>>()
        val negativeDeltas = mutableListOf<Pair<String, Long>>()

        for (catId in allCatKeys) {
            val c = currentCatSpend[catId] ?: 0L
            val p = priorCatSpend[catId] ?: 0L
            val diff = c - p
            if (diff > 0) {
                positiveDeltas.add(catId to diff)
            } else if (diff < 0) {
                negativeDeltas.add(catId to abs(diff))
            }
        }

        val sumPositiveDelta = positiveDeltas.sumOf { it.second }
        val sumNegativeDelta = negativeDeltas.sumOf { it.second }

        positiveDeltas.sortByDescending { it.second }
        negativeDeltas.sortByDescending { it.second }

        val isIncrease = pctDelta >= 15
        val isDecrease = pctDelta <= -15

        if (isIncrease && sumPositiveDelta > 0L) {
            val top = positiveDeltas.first()
            val topName = categoryNames[top.first] ?: "سایر"
            val topPct = ((top.second.toDouble() / sumPositiveDelta.toDouble()) * 100).toInt().coerceIn(1, 100)

            val second = positiveDeltas.getOrNull(1)
            val secondName = second?.let { categoryNames[it.first] ?: "سایر" }
            val secondPct = second?.let { ((it.second.toDouble() / sumPositiveDelta.toDouble()) * 100).toInt().coerceIn(1, 100) }

            val diagnostic = if (topPct >= 50 || second == null) {
                "رشد ٪${faNumber(pctDelta.toDouble())} مخارج نسبت به دوره قبل؛ ٪${faNumber(topPct.toDouble())} این انحراف ناشی از «$topName» است."
            } else {
                "رشد ٪${faNumber(pctDelta.toDouble())} مخارج؛ عوامل اصلی: «$topName» (٪${faNumber(topPct.toDouble())}) و «$secondName» (٪${faNumber(secondPct?.toDouble() ?: 0.0)})."
            }

            return VarianceInsight(
                isSignificantIncrease = true,
                isSignificantDecrease = false,
                percentageDelta = pctDelta,
                topDriverCategoryName = topName,
                topDriverAttributionPct = topPct,
                secondDriverCategoryName = secondName,
                secondDriverAttributionPct = secondPct,
                diagnosticTextFa = diagnostic,
            )
        } else if (isDecrease && sumNegativeDelta > 0L) {
            val topSaving = negativeDeltas.first()
            val topSavingName = categoryNames[topSaving.first] ?: "سایر"
            val savingPct = abs(pctDelta)

            return VarianceInsight(
                isSignificantIncrease = false,
                isSignificantDecrease = true,
                percentageDelta = pctDelta,
                topDriverCategoryName = topSavingName,
                topDriverAttributionPct = null,
                secondDriverCategoryName = null,
                secondDriverAttributionPct = null,
                diagnosticTextFa = "٪${faNumber(savingPct.toDouble())} صرفه‌جویی نسبت به دوره قبل؛ بیشترین کاهش هزینه در «$topSavingName» ثبت شد.",
            )
        } else {
            return VarianceInsight(
                isSignificantIncrease = false,
                isSignificantDecrease = false,
                percentageDelta = pctDelta,
                topDriverCategoryName = null,
                topDriverAttributionPct = null,
                secondDriverCategoryName = null,
                secondDriverAttributionPct = null,
                diagnosticTextFa = "مخارج در محدوده پایدار و منطبق بر برنامه ماهانه است.",
            )
        }
    }
}
