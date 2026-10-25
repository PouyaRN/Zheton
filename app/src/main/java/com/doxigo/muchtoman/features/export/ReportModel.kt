package com.doxigo.muchtoman.features.export

import com.doxigo.muchtoman.JalaliDate
import com.doxigo.muchtoman.MONTHS
import com.doxigo.muchtoman.faDecimal
import com.doxigo.muchtoman.faNumber
import com.doxigo.muchtoman.jalaliDay
import com.doxigo.muchtoman.jalaliMonthLength
import com.doxigo.muchtoman.jalaliOf
import com.doxigo.muchtoman.ltrFigure
import com.doxigo.muchtoman.previousMonth
import com.doxigo.muchtoman.tomanOf

/**
 * The report's own vocabulary: a window of days, the rows that go in it, and the strings a
 * person actually reads.
 *
 * This file is deliberately free of `android.*` so the arithmetic and the Persian wording can be
 * checked by a JVM test on a laptop. The parts that need a device — the PDF canvas and the
 * activity — live beside it and consume only these plain values.
 *
 * Two money rules are copied from the app rather than re-invented, because a report that
 * disagrees with the screen is worse than no report:
 *
 *  - **Everything is Toman on the way out.** Rial is the storage unit; the ledger screens already
 *    divide by ten, so a report that printed Rial would show figures ten times the ones she
 *    just checked.
 *  - **No figure is invented.** Every number below comes from a transaction, an installment row
 *    or a stored holding. Nothing is estimated, rounded up, or filled in from a rate that was
 *    not already cached.
 */

/** A half-open range of Tehran days, `[from, until)`. Half-open because a month boundary is otherwise counted twice. */
data class DayRange(val from: Long, val until: Long) {
    init {
        require(until > from) { "empty day range: $from..$until" }
    }

    val dayCount: Int get() = (until - from).toInt()

    operator fun contains(day: Long): Boolean = day in from until until
}

/** The windows the export screen offers. Persian labels, resolved against a Tehran "today". */
enum class ReportWindow(val fa: String) {
    THIS_MONTH("این ماه"),
    LAST_MONTH("ماه قبل"),
    LAST_30_DAYS("۳۰ روز اخیر"),
    CUSTOM("بازهٔ دلخواه"),

    ;

    /**
     * The days this window covers. [CUSTOM] cannot be resolved from a clock alone — the caller
     * already has the two dates she typed — so asking is a programming error rather than a
     * silently empty report.
     */
    fun resolve(today: Long): DayRange = when (this) {
        THIS_MONTH -> jalaliOf(today).let { monthRange(it.year, it.month) }
        LAST_MONTH -> jalaliOf(today).let { previousMonth(it.year, it.month) }.let { (y, m) -> monthRange(y, m) }
        LAST_30_DAYS -> DayRange(today - 29, today + 1)
        CUSTOM -> error("CUSTOM needs explicit dates; use customRange(from, to)")
    }
}

/** One whole Jalali month, `[first day, first day of next month)`. */
fun monthRange(year: Int, month: Int): DayRange {
    require(month in 1..12) { "month out of range: $month" }
    val from = jalaliDay(year, month, 1)
    return DayRange(from, from + jalaliMonthLength(year, month))
}

/**
 * A range she typed in, inclusive of both ends.
 *
 * Reversed input is normalised rather than rejected: on a phone, typing the later date first is
 * a slip, not an intent, and an export that refuses to run over it helps nobody.
 */
fun customRange(from: JalaliDate, to: JalaliDate): DayRange {
    val start = jalaliDay(from.year, from.month, from.day)
    val end = jalaliDay(to.year, to.month, to.day)
    return DayRange(minOf(start, end), maxOf(start, end) + 1)
}

/**
 * Persian digits with a two-digit month/day, e.g. «۱۴۰۵/۰۵/۰۳».
 *
 * Years are not run through [faNumber]: its Persian grouping would print «۱٬۴۰۵», and a
 * thousands separator inside a year is a date nobody can read.
 */
fun faDate(day: Long): String = jalaliOf(day).let { d ->
    "${faYear(d.year)}/${faTwo(d.month)}/${faTwo(d.day)}"
}

private fun faYear(year: Int): String = faYearDigits(year.toString())

private fun faTwo(n: Int): String = faYearDigits(n.toString().padStart(2, '0'))

private fun faYearDigits(digits: String): String =
    digits.map { if (it in '0'..'9') '۰' + (it - '0') else it }.joinToString("")

/**
 * What the period is called at the top of the page.
 *
 * A range that is exactly one Jalali month is named the way she says it — «مرداد ۱۴۰۵» — and
 * anything else falls back to the two dates. Naming an arbitrary range by its first month would
 * be the kind of small lie that gets a report sent back.
 */
fun rangeLabelFa(range: DayRange): String {
    val start = jalaliOf(range.from)
    val end = jalaliOf(range.until - 1)
    val isWholeMonth = start.day == 1 && end.month == start.month && end.year == start.year &&
        end.day == jalaliMonthLength(start.year, start.month)
    return if (isWholeMonth) "${MONTHS[start.month - 1]} ${faYear(start.year)}"
    else "${faDate(range.from)} تا ${faDate(range.until - 1)}"
}

/**
 * Toman for print, keeping the tenth.
 *
 * The screen rounds to whole Toman because scans better, but an accountant reconciling the
 * report against a bank statement needs the 500-Rial tail. Persian grouping and the Persian
 * decimal separator come from the same locale formatter the rest of the app uses, so the
 * figures look like the app's own.
 */
fun faToman(toman: Double): String = faDecimal(toman, 1)

/** The same figure, with a typographic minus that survives an RTL line. */
fun faTomanSigned(toman: Double): String =
    if (toman < 0) ltrFigure("−" + faToman(-toman)) else faToman(toman)

/** Percent for print: «۲۳٫۴٪», never «۲۳.۴%». */
fun faPercentText(fraction: Double): String = "${faDecimal(fraction * 100.0, 1)}٪"

/**
 * How a cell is read by both renderers. Text is what a human sees in the PDF; [number] is what
 * Excel gets, so a spreadsheet can still sum the column.
 */
enum class CellKind { TEXT, MONEY, PERCENT, COUNT }

/** One cell, carrying its display string and — for figures — the raw number behind it. */
data class ReportCell(
    val text: String,
    val kind: CellKind = CellKind.TEXT,
    val number: Double? = null,
) {
    init {
        // A numeric cell with no number is how a report quietly loses a column in Excel.
        if (kind != CellKind.TEXT) require(number != null) { "numeric cell without a number: $text" }
    }
}

fun textCell(text: String): ReportCell = ReportCell(text)

/** Money cells are built from Rial and carry Toman, because the display rule is Toman everywhere. */
fun moneyCell(rial: Long): ReportCell = moneyCellToman(tomanOf(rial))

fun moneyCellToman(toman: Double): ReportCell =
    ReportCell(text = faTomanSigned(toman), kind = CellKind.MONEY, number = toman)

fun percentCell(fraction: Double): ReportCell =
    ReportCell(text = faPercentText(fraction), kind = CellKind.PERCENT, number = fraction)

/**
 * A rate that can be negative — a month that spent more than it earned.
 *
 * The number stays numeric so Excel can still read it; only the printed string gets the
 * left-to-right isolation, because without it the minus drifts to the wrong end of a
 * right-to-left line and «−۲۹٪» starts reading as a dash between two words.
 */
fun signedPercentCell(fraction: Double): ReportCell = ReportCell(
    text = if (fraction < 0) ltrFigure("−" + faPercentText(-fraction)) else faPercentText(fraction),
    kind = CellKind.PERCENT,
    number = fraction,
)

fun countCell(count: Int): ReportCell =
    ReportCell(text = faNumber(count), kind = CellKind.COUNT, number = count.toDouble())

/** A column: its Persian header and, for layout, how much of the width it wants. */
data class ReportColumn(val headerFa: String, val kind: CellKind, val widthChars: Int)

/** One table row. [emphasis] is a total row: bold, ruled above, never zebra-striped. */
data class ReportRow(val cells: List<ReportCell>, val emphasis: Boolean = false)

/** A titled table. [noteFa] is where a section explains what it left out. */
data class ReportSection(
    val titleFa: String,
    val columns: List<ReportColumn>,
    val rows: List<ReportRow>,
    val noteFa: String = "",
)

/**
 * Everything the two renderers need, and nothing a device must supply.
 *
 * [footnoteFa] is printed once, at the end, and is the report's promise about its own contents:
 * no message bodies, no card numbers, no account numbers. It is a promise the builder keeps by
 * never copying those fields — see `ReportBuilderTest` for the guard.
 */
data class ReportDocument(
    val titleFa: String,
    val periodFa: String,
    val metaFa: String,
    val footnoteFa: String,
    val range: DayRange,
    val sections: List<ReportSection>,
    val generatedAt: Long,
)
