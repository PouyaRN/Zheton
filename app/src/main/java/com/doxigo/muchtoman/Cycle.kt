package com.doxigo.muchtoman

/**
 * Which half-open day window one "financial month" spans.
 *
 * [JALALI_MONTH] is the plain calendar month, [ANCHOR] is the salary cycle: it starts on
 * [CycleSpec.anchorDay] of each Jalali month and runs to the day before the next anchor. A
 * salary paid on the 29th then never leaves the calendar month after it reading as a deficit.
 */
enum class CycleMode { JALALI_MONTH, ANCHOR }

/** The user's chosen cycle. [anchorDay] is ignored unless [CycleMode.ANCHOR] is selected. */
data class CycleSpec(val mode: CycleMode = CycleMode.JALALI_MONTH, val anchorDay: Int = 1)

/** One cycle as day numbers: `[startDay, endDay)` — [endDay] is the *next* cycle's start. */
data class CycleWindow(val startDay: Long, val endDay: Long)

/**
 * The first day of the cycle containing [day].
 *
 * When the month has no [CycleSpec.anchorDay] (an anchor of 30 in Esfand, an anchor of 31 in a
 * 30-day month), the anchor falls back to the month's last day — a payday is never skipped.
 * Scanning backwards over real month lengths keeps start and end symmetric, so no window can
 * come out inverted or empty the way hand-written leap arithmetic tends to.
 */
fun cycleStartOfDay(day: Long, spec: CycleSpec): Long {
    if (spec.mode == CycleMode.JALALI_MONTH || spec.anchorDay <= 1) return jalaliMonthStart(day)
    val anchor = spec.anchorDay.coerceIn(2, 31)
    var candidate = previousDayAnchor(day, anchor)
    // The scan above is defined by subtraction and can land one anchor short when [day] sits in
    // the tail of a long month; step once more when the candidate failed to reach [day].
    val next = nextDayAnchor(candidate, anchor)
    if (next <= day) candidate = next
    return candidate
}

/** The last day of the cycle containing [day]; exclusive, i.e. the next cycle's first day. */
fun cycleEndOfDay(day: Long, spec: CycleSpec): Long = nextDayAnchor(cycleStartOfDay(day, spec), spec.anchorDay)

/** The cycle containing [day], as a half-open `[start, end)` window of day numbers. */
fun cycleWindow(day: Long, spec: CycleSpec): CycleWindow {
    val start = cycleStartOfDay(day, spec)
    return CycleWindow(start, nextDayAnchor(start, spec.anchorDay))
}

/** The cycle containing [day], as a half-open `[from, until)` pair for [monthReport]. */
fun reportWindowForCycle(day: Long, spec: CycleSpec): Pair<Long, Long> =
    cycleWindow(day, spec).let { it.startDay to it.endDay }

/** The day one Jalali month after the same day-of-month position, snapped into that month. */
private fun addJalaliMonth(date: JalaliDate): JalaliDate {
    var month = date.month + 1
    var year = date.year
    if (month > 12) {
        month = 1
        year += 1
    }
    return JalaliDate(year, month, date.day.coerceAtMost(jalaliMonthLength(year, month)))
}

private fun monthAnchorDay(year: Int, month: Int, anchor: Int): Long =
    jalaliDay(year, month, anchor.coerceAtMost(jalaliMonthLength(year, month)))

/** The most recent anchor occurrence at or before [day]. */
private fun previousDayAnchor(day: Long, anchor: Int): Long {
    var here = jalaliOf(day)
    var candidate = monthAnchorDay(here.year, here.month, anchor)
    while (candidate > day) {
        here = jalaliOf(jalaliMonthStart(jalaliDay(here.year, here.month, 1)) - 1)
        candidate = monthAnchorDay(here.year, here.month, anchor)
    }
    return candidate
}

/** The first anchor occurrence strictly after [day]. */
private fun nextDayAnchor(day: Long, anchor: Int): Long {
    var here = jalaliOf(day)
    var candidate = monthAnchorDay(here.year, here.month, anchor)
    while (candidate <= day) {
        here = addJalaliMonth(JalaliDate(here.year, here.month, 1))
        candidate = monthAnchorDay(here.year, here.month, anchor)
    }
    return candidate
}
