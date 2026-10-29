package com.doxigo.muchtoman

/**
 * The monthly budget and the daily share it leaves («سهم امروزت از بودجه»).
 *
 * The month is the user's own cycle ([cycleWindow] over [CycleSpec]), so a payday-anchored
 * budget runs from payday to payday. Spending is the same figure the home story reads:
 * [spendable] outflows (no transfers, no duplicates) inside the window.
 *
 * Every figure is whole Rial and they add up by construction:
 * `spent + remaining == budget`, `dailyShare == max(0, remaining) / daysLeft` (floored),
 * `planToDate == budget * dayOfCycle / totalDays` (floored).
 */
sealed interface MonthlyBudget {
    /** The cycle being measured, present whether or not a budget is set. */
    val cycle: BudgetCycle

    /** No budget is set: the UI keeps showing safe-spend from liquid cash instead. */
    data class Unset(override val cycle: BudgetCycle) : MonthlyBudget

    data class Active(
        override val cycle: BudgetCycle,
        val budgetRial: Long,
        val spentRial: Long,
        /** What an even share of the budget allows through the end of today. */
        val planToDateRial: Long,
        /** `budget - spent`; negative once the budget is overspent. */
        val remainingRial: Long,
        /** The budget left for each remaining day, today included. Never negative. */
        val dailyShareRial: Long,
        /** `spent - planToDate`: positive when spending runs ahead of plan. */
        val gapRial: Long,
        val pace: BudgetPaceStatus,
        /** The one expense or category that accounts for being ahead, when one does. */
        val driver: BudgetDriver?,
        /** The sentence under the bar, e.g. «روز ۶ از ۳۰ آبان. ۵ میلیونش اجاره بوده و بقیه طبق برنامه‌ست.» */
        val explanationFa: String,
    ) : MonthlyBudget {
        /** Spent as a share of the budget; above 1 when overspent. */
        val spentFraction: Float get() = if (budgetRial > 0) spentRial.toFloat() / budgetRial else 0f
        val spentPercent: Int get() = (spentFraction * 100).toInt()
        val elapsedFraction: Float get() = cycle.elapsedFraction
        val elapsedPercent: Int get() = cycle.elapsedPercent
        /** Plan-to-date as a share of the budget; equals [elapsedFraction] up to rounding. */
        val planFraction: Float get() = if (budgetRial > 0) planToDateRial.toFloat() / budgetRial else 0f
    }
}

/** Where today sits in the cycle; drives the 30-day strip. */
data class BudgetCycle(
    val window: CycleWindow,
    val today: Long,
    /** 1-based: the first day of the cycle is day 1. */
    val dayOfCycle: Int,
    val totalDays: Int,
    /** Days left including today, so it is never zero inside the cycle. */
    val daysLeft: Int,
    /** The Jalali month the cycle mostly covers, e.g. «آبان». */
    val monthNameFa: String,
    /** Spending so far in the cycle, also known when no budget is set. */
    val spentRial: Long,
) {
    val elapsedFraction: Float get() = dayOfCycle.toFloat() / totalDays
    val elapsedPercent: Int get() = dayOfCycle * 100 / totalDays
}

enum class BudgetPaceStatus { AHEAD, ON_PLAN, UNDER, OVER_BUDGET }

/** A single expense or a whole category that explains spending ahead of plan. */
data class BudgetDriver(val labelFa: String, val rial: Long, val kind: Kind, val ref: String? = null) {
    enum class Kind { EXPENSE, CATEGORY }
}

/**
 * The budget picture for the cycle containing [today].
 *
 * [budgetToman] of zero or less means unset and yields [MonthlyBudget.Unset]. Hidden entries,
 * transfers and duplicates never count as spending.
 */
fun monthlyBudget(entries: List<LedgerEntry>, budgetToman: Long, today: Long, spec: CycleSpec): MonthlyBudget {
    val window = cycleWindow(today, spec)
    val outflows = spendable(entries)
        .filter { !it.hidden && it.txn.day >= window.startDay && it.txn.day <= today && (it.txn.signedRial ?: 0L) < 0L }
    val spent = outflows.sumOf { -(it.txn.signedRial ?: 0L) }
    val total = (window.endDay - window.startDay).toInt().coerceAtLeast(1)
    val dayOfCycle = (today - window.startDay + 1).toInt().coerceIn(1, total)
    val mid = jalaliOf(window.startDay + (total - 1) / 2)
    val cycle = BudgetCycle(
        window = window,
        today = today,
        dayOfCycle = dayOfCycle,
        totalDays = total,
        daysLeft = total - dayOfCycle + 1,
        monthNameFa = MONTHS.getOrElse(mid.month - 1) { "" },
        spentRial = spent,
    )
    if (budgetToman <= 0L) return MonthlyBudget.Unset(cycle)

    val budget = budgetToman * 10L
    val plan = budget * dayOfCycle / total
    val remaining = budget - spent
    val dailyShare = remaining.coerceAtLeast(0L) / cycle.daysLeft
    val gap = spent - plan
    // Within one day's even share of the plan counts as on plan: a single coffee should not
    // flip the sentence back and forth.
    val tolerance = budget / total
    val pace = when {
        remaining < 0L -> BudgetPaceStatus.OVER_BUDGET
        gap > tolerance -> BudgetPaceStatus.AHEAD
        gap < -tolerance -> BudgetPaceStatus.UNDER
        else -> BudgetPaceStatus.ON_PLAN
    }
    val driver = if (pace == BudgetPaceStatus.AHEAD || pace == BudgetPaceStatus.OVER_BUDGET) {
        budgetDriver(outflows, gap - tolerance)
    } else null

    val lead = "روز ${faNumber(dayOfCycle)} از ${faNumber(total)} ${cycle.monthNameFa}."
    val body = when (pace) {
        BudgetPaceStatus.ON_PLAN -> "خرجت طبق برنامه‌ست."
        BudgetPaceStatus.UNDER -> "${faCompact(tomanOf(-gap))} کمتر از برنامه خرج کردی."
        BudgetPaceStatus.AHEAD -> if (driver != null) {
            "${driverPhrase(driver)} و بقیه طبق برنامه‌ست."
        } else {
            "${faCompact(tomanOf(gap))} جلوتر از برنامه خرج کردی."
        }
        BudgetPaceStatus.OVER_BUDGET -> {
            val over = "${faCompact(tomanOf(-remaining))} بیشتر از بودجه خرج کردی."
            if (driver != null) "$over ${driverPhrase(driver)}." else over
        }
    }
    return MonthlyBudget.Active(
        cycle = cycle,
        budgetRial = budget,
        spentRial = spent,
        planToDateRial = plan,
        remainingRial = remaining,
        dailyShareRial = dailyShare,
        gapRial = gap,
        pace = pace,
        driver = driver,
        explanationFa = "$lead $body",
    )
}

/** «۵ میلیونش اجاره بوده» / «۵ میلیونش خرج خوراک بوده». */
private fun driverPhrase(driver: BudgetDriver): String = when (driver.kind) {
    BudgetDriver.Kind.EXPENSE -> "${faCompact(tomanOf(driver.rial))}ش ${driver.labelFa} بوده"
    BudgetDriver.Kind.CATEGORY -> "${faCompact(tomanOf(driver.rial))}ش خرج ${driver.labelFa} بوده"
}

/**
 * The single largest expense if it alone covers [mustCover], else the largest category if it
 * does, else null. A single expense is preferred: «اجاره» is a clearer reason than a category sum.
 */
internal fun budgetDriver(outflows: List<LedgerEntry>, mustCover: Long): BudgetDriver? {
    if (outflows.isEmpty()) return null
    val biggest = outflows.maxBy { -(it.txn.signedRial ?: 0L) }
    val biggestRial = -(biggest.txn.signedRial ?: 0L)
    if (biggestRial >= mustCover) {
        val label = biggest.categoryFa.ifBlank { biggest.txn.merchant }.ifBlank { "یک خرج" }
        return BudgetDriver(label, biggestRial, BudgetDriver.Kind.EXPENSE, biggest.txn.ref)
    }
    val (category, categoryRial) = outflows
        .filter { it.categoryFa.isNotBlank() }
        .groupBy { it.categoryFa }
        .mapValues { (_, rows) -> rows.sumOf { -(it.txn.signedRial ?: 0L) } }
        .maxByOrNull { it.value }
        ?.toPair() ?: return null
    return if (categoryRial >= mustCover) BudgetDriver(category, categoryRial, BudgetDriver.Kind.CATEGORY) else null
}
