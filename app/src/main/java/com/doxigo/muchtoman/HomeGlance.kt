package com.doxigo.muchtoman

import kotlin.math.truncate

/*
 * The figures behind the Today tab (HomeScreen.kt), kept pure so they can be tested without
 * Compose. Nothing here invents a number: every value is read from the ledger, the accounts or
 * the daily net-worth snapshots, and a value that cannot be read honestly is null.
 */

/** Net worth for the Home card: the real total, its real change since the month began, and the curve. */
data class NetWorthGlance(
    val toman: Double,
    /** Change since the start of the Jalali month, or null when no snapshot is that old. */
    val changeToman: Double?,
    val changePercent: Double?,
    /** Oldest first, ending at [toman]; empty when there are fewer than two real points. */
    val spark: List<Double>,
)

/**
 * [history] is the daily snapshot map (epoch day → Toman). The baseline is the newest snapshot
 * on or before the month's first day, at most a week before it, or failing that one from the
 * month's first three days: anything else would pass a different window off as «از اول ماه».
 */
fun netWorthGlance(history: Map<Long, Double>, currentToman: Double, today: Long): NetWorthGlance {
    val monthStart = jalaliMonthStart(today)
    val baseDay = history.keys.filter { it in (monthStart - 7)..monthStart }.maxOrNull()
        ?: history.keys.filter { it in (monthStart + 1)..(monthStart + 3) && it < today }.minOrNull()
    val base = baseDay?.let { history.getValue(it) }
    val changeToman = base?.let { currentToman - it }
    val changePercent = base?.takeIf { it > 0.0 }?.let { (currentToman - it) / it * 100.0 }

    val window = history.filterKeys { it in (today - 30) until today }.toSortedMap().values.toList()
    val spark = if (window.isEmpty()) emptyList() else window + currentToman
    return NetWorthGlance(currentToman, changeToman, changePercent, spark)
}

/** Today's rows, newest first: not hidden, not the struck-through leg of a duplicate. */
fun todaysEntries(entries: List<LedgerEntry>, today: Long): List<LedgerEntry> =
    entries.filter { it.txn.day == today && !it.hidden && !it.duplicate }.sortedByDescending { it.txn.at }

/** The account whose SHEBA «کپی شبا» copies: the largest switched-on balance that has one. */
fun mainShebaAccount(accounts: List<BankAccount>, disabled: Set<String>): BankAccount? =
    accounts.filter { it.bank !in disabled && it.sheba.isNotBlank() }.maxByOrNull { it.balance }

/**
 * With no budget set, the answer falls back to the safe-spend the old dial showed: liquid cash
 * across the switched-on accounts, spread over the days left in the cycle (today included).
 * Null when there is no positive balance to spread.
 */
fun safeSpendFromCash(liquidToman: Double, daysLeft: Int): Long? =
    if (liquidToman > 0.0) (liquidToman / daysLeft.coerceAtLeast(1)).toLong() else null

/** «سه‌شنبه ۶ آبان» for a Tehran day. */
fun faWeekdayDate(day: Long): String {
    val j = jalaliOf(day)
    val weekday = WEEKDAYS_FA[SafeSpendEngine.iranianDayOfWeek(day) - 1]
    return "$weekday ${faNumber(j.day)} ${MONTHS[j.month - 1]}"
}

private val WEEKDAYS_FA = listOf("شنبه", "یک‌شنبه", "دوشنبه", "سه‌شنبه", "چهارشنبه", "پنج‌شنبه", "جمعه")

/** «۱۰:۴۲», Tehran wall-clock time of an epoch instant. */
fun faClock(epochMillis: Long): String {
    val minutes = Math.floorMod(epochMillis + TEHRAN_OFFSET_MS, DAY_MS) / 60_000L
    val h = (minutes / 60).toInt()
    val m = (minutes % 60).toInt()
    return "${faNumber(h)}:${if (m < 10) "۰" else ""}${faNumber(m)}"
}

/**
 * The unit a budget is spoken in, so the month line reads «۶٫۴۸ از بودجهٔ ۱۸ میلیونی آبان»:
 * every figure on that line is in the budget's own unit.
 */
data class BudgetUnit(val word: String, val divisor: Double) {
    /** Truncated to two places, trailing zeros dropped: «۶٫۴۸», «۱۸». */
    fun figure(toman: Double): String = faDecimal(truncate(toman / divisor * 100.0) / 100.0, 2)
}

fun budgetUnit(budgetToman: Double): BudgetUnit = when {
    budgetToman >= 1_000_000_000.0 -> BudgetUnit("میلیارد", 1_000_000_000.0)
    budgetToman >= 1_000_000.0 -> BudgetUnit("میلیون", 1_000_000.0)
    budgetToman >= 1_000.0 -> BudgetUnit("هزار", 1_000.0)
    else -> BudgetUnit("تومن", 1.0)
}
