package com.doxigo.muchtoman

import kotlin.math.abs
import kotlin.math.roundToLong

/**
 * The Report screen's month, set beside the month before it.
 *
 * Data only: the screen renders [MonthOverMonth] and computes nothing. Every figure comes from
 * the ledger through the same rules as [monthReport] — transfers and duplicates are neither
 * income nor spending — and, as on the existing reports tab, rows she hid are left out too.
 *
 * "Month" honours her cycle setting. Under [CycleMode.ANCHOR] the month labelled مهر is the cycle
 * that starts on her anchor day in مهر, which is how [buildStory] already dates a cycle.
 */

/** A month the report can be switched to, named by the Jalali month its cycle starts in. */
data class ReportMonth(val year: Int, val month: Int) {
    init {
        require(month in 1..12) { "month out of range: $month" }
    }

    /** «مهر» */
    val nameFa: String get() = MONTHS[month - 1]

    fun previous(): ReportMonth = previousMonth(year, month).let { ReportMonth(it.first, it.second) }

    fun next(): ReportMonth = if (month == 12) ReportMonth(year + 1, 1) else ReportMonth(year, month + 1)

    /** The half-open day window this month spans under [spec]. */
    fun window(spec: CycleSpec): CycleWindow {
        if (spec.mode == CycleMode.JALALI_MONTH) return cycleWindow(jalaliDay(year, month, 1), spec)
        // The anchor is snapped into short months exactly as Cycle.kt does, so the window found
        // here is the one that starts in this month and never the one before it.
        val anchor = spec.anchorDay.coerceIn(1, jalaliMonthLength(year, month))
        return cycleWindow(jalaliDay(year, month, anchor), spec)
    }
}

/** The month that contains [today] under [spec]: what the report opens on. */
fun reportMonthOf(today: Long, spec: CycleSpec): ReportMonth =
    jalaliOf(cycleWindow(today, spec).startDay).let { ReportMonth(it.year, it.month) }

/**
 * One category's spending this month and how it moved.
 *
 * [change] is `(now − before) / before`, so −0.31 reads «−۳۱٪ از شهریور». It is null when the
 * previous month had nothing in this category: a change from nothing is not a percentage, and
 * the row then shows no change at all.
 */
data class CategoryChange(
    val categoryId: String,
    val nameFa: String,
    val spentRial: Long,
    val previousRial: Long,
    /** This category's share of the month's spending, 0..1. */
    val share: Double,
    val change: Double?,
)

/** How a highlighted phrase in the summary is lit: bright for good news, amber for a warning. */
enum class SummaryTone { GOOD, WARNING }

/** One run of the summary sentence. Plain runs render dim; highlighted runs render bright. */
sealed interface SummaryPart {
    val text: String

    data class Plain(override val text: String) : SummaryPart

    data class Highlight(override val text: String, val tone: SummaryTone) : SummaryPart
}

/** Everything the Report screen shows for one month. */
data class MonthOverMonth(
    val month: ReportMonth,
    val previousMonth: ReportMonth,
    val window: CycleWindow,
    val previousWindow: CycleWindow,
    val incomeRial: Long,
    val spentRial: Long,
    val previousIncomeRial: Long,
    val previousSpentRial: Long,
    /**
     * Running total of spending, one value per day of the month from its first day. For the
     * month in progress it stops at today; for a month that has not started it is empty.
     */
    val cumulativeSpend: List<Long>,
    /** The same running total over the whole previous month. Its last value is [previousSpentRial]. */
    val previousCumulativeSpend: List<Long>,
    /** This month's spending by category, largest first. */
    val categories: List<CategoryChange>,
    /** The sentence at the top, in runs. Joining every [SummaryPart.text] gives the plain sentence. */
    val summary: List<SummaryPart>,
    /** True while today is inside this month: the figures are so far, not final. */
    val inProgress: Boolean,
) {
    /** Income minus spending; negative when she spent more than came in. */
    val savedRial: Long get() = incomeRial - spentRial

    /** The share of income that stayed; null with no income, as in [MonthReport.savingsRate]. */
    val savingsRate: Double? get() = if (incomeRial > 0) savedRial.toDouble() / incomeRial else null

    /** The summary as one string, for accessibility and sharing. */
    val summaryText: String get() = summary.joinToString("") { it.text }
}

/**
 * Builds the report for [month] from the whole ledger (pass `LedgerView.allEntries`).
 *
 * [today] decides whether the month is still running, which trims its spend line at today and
 * words the sentence as "so far" rather than "you closed".
 */
fun monthOverMonth(
    entries: List<LedgerEntry>,
    month: ReportMonth,
    spec: CycleSpec,
    today: Long,
): MonthOverMonth {
    val prevMonth = month.previous()
    val window = month.window(spec)
    val prevWindow = prevMonth.window(spec)
    val counted = entries.filterNot { it.duplicate || it.transfer || it.hidden }
    val now = counted.inWindow(window)
    val before = counted.inWindow(prevWindow)

    val income = now.income()
    val spent = now.spent()
    val prevSpent = before.spent()

    val lastShownDay = minOf(window.endDay - 1, today)
    val series = if (lastShownDay < window.startDay) emptyList() else cumulative(now, window.startDay, lastShownDay)
    val prevSeries = cumulative(before, prevWindow.startDay, prevWindow.endDay - 1)

    val nowByCat = now.byCategory()
    val beforeByCat = before.byCategory()
    val categories = nowByCat.map { (id, pair) ->
        val (name, rial) = pair
        val prev = beforeByCat[id]?.second ?: 0L
        CategoryChange(
            categoryId = id,
            nameFa = name,
            spentRial = rial,
            previousRial = prev,
            share = if (spent > 0) rial.toDouble() / spent else 0.0,
            change = if (prev > 0) (rial - prev).toDouble() / prev else null,
        )
    }.sortedByDescending { it.spentRial }

    val inProgress = today >= window.startDay && today < window.endDay
    return MonthOverMonth(
        month = month,
        previousMonth = prevMonth,
        window = window,
        previousWindow = prevWindow,
        incomeRial = income,
        spentRial = spent,
        previousIncomeRial = before.income(),
        previousSpentRial = prevSpent,
        cumulativeSpend = series,
        previousCumulativeSpend = prevSeries,
        categories = categories,
        summary = summarize(
            month = month,
            hasActivity = now.any { it.txn.signedRial != null },
            income = income,
            spent = spent,
            inProgress = inProgress,
            nowByCat = nowByCat,
            beforeByCat = beforeByCat,
        ),
        inProgress = inProgress,
    )
}

// ─────────────────────────── the sentence ───────────────────────────

/** A category must carry at least this share of a month's spending to be named in the sentence. */
internal const val SUMMARY_MIN_SHARE = 0.05

/** A move smaller than this, either way, is not worth a sentence. */
internal const val SUMMARY_MIN_CHANGE = 0.10

/**
 * Up to three clauses, each a template over figures computed above — never prose that has to be
 * trusted. See issue #14 for the rules in words.
 */
internal fun summarize(
    month: ReportMonth,
    hasActivity: Boolean,
    income: Long,
    spent: Long,
    inProgress: Boolean,
    nowByCat: Map<String, Pair<String, Long>>,
    beforeByCat: Map<String, Pair<String, Long>>,
): List<SummaryPart> {
    val name = month.nameFa
    if (!hasActivity) {
        return listOf(SummaryPart.Plain("برای $name هنوز تراکنشی ثبت نشده."))
    }
    val out = mutableListOf<SummaryPart>()
    val saved = income - spent

    // 1. What stayed.
    when {
        income <= 0 -> {
            out += SummaryPart.Plain(if (inProgress) "تا امروز تو $name " else "تو $name ")
            out += SummaryPart.Highlight("${money(spent)} خرج", SummaryTone.GOOD)
            out += SummaryPart.Plain(" کردی و درآمدی ثبت نشده.")
        }
        saved >= 0 && inProgress -> {
            out += SummaryPart.Plain("تا امروز تو $name ")
            out += SummaryPart.Highlight("${money(saved)} پس‌انداز", SummaryTone.GOOD)
            out += SummaryPart.Plain(" کردی.")
        }
        saved >= 0 -> {
            out += SummaryPart.Plain("$name رو با ")
            out += SummaryPart.Highlight("${money(saved)} پس‌انداز", SummaryTone.GOOD)
            out += SummaryPart.Plain(" بستی.")
        }
        inProgress -> {
            out += SummaryPart.Plain("تا امروز تو $name ")
            out += SummaryPart.Highlight("${money(-saved)} بیشتر از درآمدت", SummaryTone.WARNING)
            out += SummaryPart.Plain(" خرج کردی.")
        }
        else -> {
            out += SummaryPart.Plain("$name رو با ")
            out += SummaryPart.Highlight("${money(-saved)} بیشتر از درآمدت", SummaryTone.WARNING)
            out += SummaryPart.Plain(" خرج بستی.")
        }
    }

    // 2 and 3. The category that fell most and the one that rose most, in money moved.
    val beforeTotal = beforeByCat.values.sumOf { it.second }
    if (beforeTotal <= 0) return out
    val scale = maxOf(spent, beforeTotal)
    val moves = (nowByCat.keys + beforeByCat.keys).mapNotNull { id ->
        val nowRial = nowByCat[id]?.second ?: 0L
        val prevRial = beforeByCat[id]?.second ?: 0L
        if (prevRial <= 0) return@mapNotNull null
        if (maxOf(nowRial, prevRial).toDouble() / scale < SUMMARY_MIN_SHARE) return@mapNotNull null
        val change = (nowRial - prevRial).toDouble() / prevRial
        if (abs(change) < SUMMARY_MIN_CHANGE) return@mapNotNull null
        Triple(nowByCat[id]?.first ?: beforeByCat.getValue(id).first, nowRial - prevRial, change)
    }
    val fell = moves.filter { it.second < 0 }.minByOrNull { it.second }
    val rose = moves.filter { it.second > 0 }.maxByOrNull { it.second }

    if (fell != null) {
        out += SummaryPart.Plain(" خرج ")
        out += SummaryPart.Highlight("${fell.first} ${percent(-fell.third)}٪ کم", SummaryTone.GOOD)
        out += SummaryPart.Plain(if (rose != null) " شد،" else " شد.")
    }
    if (rose != null) {
        out += SummaryPart.Plain(if (fell != null) " ولی " else " خرج ")
        out += SummaryPart.Highlight("${rose.first} داره بالا می‌ره.", SummaryTone.WARNING)
    }
    return out
}

private fun money(rial: Long): String = faCompact(tomanOf(rial))

private fun percent(ratio: Double): String = faNumber((ratio * 100).roundToLong())

// ─────────────────────────── arithmetic ───────────────────────────

private fun List<LedgerEntry>.inWindow(w: CycleWindow) = filter { it.txn.day >= w.startDay && it.txn.day < w.endDay }

private fun List<LedgerEntry>.income(): Long = sumOf { (it.txn.signedRial ?: 0L).coerceAtLeast(0L) }

private fun List<LedgerEntry>.spent(): Long = sumOf { -(it.txn.signedRial ?: 0L).coerceAtMost(0L) }

/** Spending by category id, carrying the category's Persian name. */
private fun List<LedgerEntry>.byCategory(): Map<String, Pair<String, Long>> {
    val out = LinkedHashMap<String, Pair<String, Long>>()
    for (e in this) {
        val signed = e.txn.signedRial ?: continue
        if (signed >= 0) continue
        val had = out[e.categoryId]?.second ?: 0L
        out[e.categoryId] = e.categoryFa to had - signed
    }
    return out
}

/** Running spend for each day in `[from, through]`. */
private fun cumulative(rows: List<LedgerEntry>, from: Long, through: Long): List<Long> {
    if (through < from) return emptyList()
    val daily = LongArray((through - from + 1).toInt())
    for (e in rows) {
        val signed = e.txn.signedRial ?: continue
        if (signed >= 0 || e.txn.day < from || e.txn.day > through) continue
        daily[(e.txn.day - from).toInt()] += -signed
    }
    var run = 0L
    return daily.map { run += it; run }
}
