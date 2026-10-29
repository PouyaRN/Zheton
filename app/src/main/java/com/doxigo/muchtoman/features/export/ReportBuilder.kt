package com.doxigo.muchtoman.features.export

import com.doxigo.muchtoman.Commitment
import com.doxigo.muchtoman.InstallmentObligation
import com.doxigo.muchtoman.InstallmentPayment
import com.doxigo.muchtoman.LedgerEntry
import com.doxigo.muchtoman.faNumber
import com.doxigo.muchtoman.spendable
import com.doxigo.muchtoman.tehranDay

/**
 * Turns a window and a set of already-read rows into the sections a person (or their accountant)
 * will read.
 *
 * **Why it takes data rather than a database.** The whole thing is a function of its arguments:
 * no clock unless one is passed, no `Context`, no I/O. That is what makes it possible to assert
 * a total by hand on a desktop JVM, and it is the only reason the PDF and the spreadsheet can be
 * trusted to agree — both consume this one document.
 *
 * **What is left out, and why.** Duplicates, transfer legs and rows she hid are excluded from
 * every figure, exactly as the Reports tab does it. A transfer between her own accounts is
 * neither income nor spending; counting it would double a month's income and make the report
 * useless for the one conversation it exists for.
 */

/** One priced holding, as it will appear in the assets snapshot. */
data class AssetLine(
    val nameFa: String,
    val amountFa: String,
    /** Value in Toman, or null when no cached rate could price it. */
    val toman: Double?,
    /** False for a holding she deliberately keeps out of the total (`excluded`). */
    val inTotal: Boolean,
)

/**
 * Everything the builder is allowed to see.
 *
 * [payments] may be a superset of what is due — the builder filters by [range] itself, so the
 * caller can pass every unpaid installment without re-deriving the window.
 */
data class ReportInputs(
    val range: DayRange,
    val entries: List<LedgerEntry>,
    val obligations: List<InstallmentObligation> = emptyList(),
    val payments: List<InstallmentPayment> = emptyList(),
    val commitments: List<Commitment> = emptyList(),
    val assets: List<AssetLine> = emptyList(),
    val generatedAt: Long = System.currentTimeMillis(),
)

private const val TITLE_FA = "گزارش مالی"

private const val FOOTNOTE_FA =
    "فقط از تراکنش‌های ثبت‌شده ساخته شده؛ متن پیامک، شمارهٔ کارت و شمارهٔ حساب در آن نیست. ارقام به تومان است."

private val SUMMARY_COLUMNS = listOf(
    ReportColumn("شرح", CellKind.TEXT, 26),
    ReportColumn("مقدار", CellKind.MONEY, 18),
)

private val CATEGORY_COLUMNS = listOf(
    ReportColumn("دسته", CellKind.TEXT, 30),
    ReportColumn("مبلغ (تومان)", CellKind.MONEY, 18),
    ReportColumn("سهم", CellKind.PERCENT, 10),
)

private val MEMBER_COLUMNS = listOf(
    ReportColumn("عضو", CellKind.TEXT, 24),
    ReportColumn("درآمد", CellKind.MONEY, 16),
    ReportColumn("خرج", CellKind.MONEY, 16),
    ReportColumn("خالص", CellKind.MONEY, 16),
)

private val DUE_COLUMNS = listOf(
    ReportColumn("شرح", CellKind.TEXT, 32),
    ReportColumn("نوع", CellKind.TEXT, 18),
    ReportColumn("سررسید", CellKind.TEXT, 13),
    ReportColumn("مبلغ (تومان)", CellKind.MONEY, 16),
)

private val ASSET_COLUMNS = listOf(
    ReportColumn("دارایی", CellKind.TEXT, 28),
    ReportColumn("مقدار", CellKind.TEXT, 16),
    ReportColumn("ارزش (تومان)", CellKind.MONEY, 18),
)

/**
 * The one public entry point.
 *
 * Deliberately not `suspend` and deliberately without an overload that reads a database: the
 * device-facing [ReportSource] does that, and keeping the two apart is what stops report logic
 * from slowly accumulating Android calls again.
 */
fun buildReport(inputs: ReportInputs): ReportDocument {
    val inRange = spendable(inputs.entries)
        .filterNot { it.hidden }
        .filter { it.txn.day in inputs.range }

    val incomeRial = inRange.sumOf { (it.txn.signedRial ?: 0L).coerceAtLeast(0L) }
    val expenseRial = inRange.sumOf { (-(it.txn.signedRial ?: 0L)).coerceAtLeast(0L) }
    val netRial = incomeRial - expenseRial

    val sections = mutableListOf<ReportSection>()

    sections += summarySection(inRange, incomeRial, expenseRial, netRial)
    categorySections(inRange, expenseRial, incomeRial).forEach { sections += it }
    memberSection(inRange)?.let { sections += it }
    dueSection(inputs)?.let { sections += it }
    assetSection(inputs.assets)?.let { sections += it }

    val today = tehranDay(inputs.generatedAt)
    return ReportDocument(
        titleFa = TITLE_FA,
        periodFa = "${rangeLabelFa(inputs.range)} · ${faNumber(inputs.range.dayCount)} روز",
        metaFa = "ساخته‌شده در ${faDate(today)} · ${faNumber(inRange.size)} تراکنش در بازه",
        footnoteFa = FOOTNOTE_FA,
        range = inputs.range,
        sections = sections,
        generatedAt = inputs.generatedAt,
    )
}

/**
 * The page a reader scans first: what came in, what went out, and whether the month held.
 *
 * The savings rate is a rate, not a Toman figure. With Iranian inflation a smaller number of
 * Toman is not a smaller amount of money, so the report never invites that comparison.
 */
private fun summarySection(
    inRange: List<LedgerEntry>,
    incomeRial: Long,
    expenseRial: Long,
    netRial: Long,
): ReportSection {
    val rows = mutableListOf(
        ReportRow(listOf(textCell("درآمد بازه"), moneyCell(incomeRial))),
        ReportRow(listOf(textCell("خرج بازه"), moneyCell(expenseRial))),
        ReportRow(listOf(textCell("خالص"), moneyCell(netRial)), emphasis = true),
    )
    rows += if (incomeRial > 0) {
        ReportRow(
            listOf(
                textCell("نرخ پس‌انداز"),
                signedPercentCell((incomeRial - expenseRial).toDouble() / incomeRial),
            ),
        )
    } else {
        // No income means no denominator. Printing 0% would claim she saved nothing, which is
        // not what an empty month says.
        ReportRow(listOf(textCell("نرخ پس‌انداز"), textCell("—")))
    }
    rows += ReportRow(listOf(textCell("تعداد تراکنش"), countCell(inRange.size)))
    rows += ReportRow(
        listOf(
            textCell("دسته‌بندی‌شده به‌صورت خودکار"),
            countCell(inRange.count { !it.needsReview }),
        ),
    )

    return ReportSection(
        titleFa = "خلاصهٔ بازه",
        columns = SUMMARY_COLUMNS,
        rows = rows,
        noteFa = if (inRange.isEmpty()) "در این بازه تراکنشی ثبت نشده است." else "",
    )
}

/**
 * Spending and income split the way she files them.
 *
 * Two tables rather than one with a direction column: a mixed column sorts to nonsense, and the
 * expense table is the one that gets read. Both carry their own total so the page can be checked
 * against the summary without scrolling.
 */
private fun categorySections(
    inRange: List<LedgerEntry>,
    expenseRial: Long,
    incomeRial: Long,
): List<ReportSection> {
    val expense = linkedMapOf<String, Long>()
    val income = linkedMapOf<String, Long>()
    for (entry in inRange) {
        val signed = entry.txn.signedRial ?: continue
        if (signed > 0) income[entry.categoryFa] = (income[entry.categoryFa] ?: 0L) + signed
        else if (signed < 0) expense[entry.categoryFa] = (expense[entry.categoryFa] ?: 0L) + -signed
    }

    // Amount descending, name as the tie-break: two categories of exactly equal spend must not
    // swap places between two builds of the same report.
    fun rowsOf(map: Map<String, Long>, totalRial: Long): List<ReportRow> =
        map.entries
            .sortedWith(compareByDescending<Map.Entry<String, Long>> { it.value }.thenBy { it.key })
            .map { (name, rial) ->
                ReportRow(
                    listOf(
                        textCell(name),
                        moneyCell(rial),
                        percentCell(if (totalRial > 0) rial.toDouble() / totalRial else 0.0),
                    ),
                )
            }

    val sections = mutableListOf<ReportSection>()

    val expenseRows = rowsOf(expense, expenseRial)
    if (expenseRows.isNotEmpty()) {
        sections += ReportSection(
            titleFa = "خرج به تفکیک دسته",
            columns = CATEGORY_COLUMNS,
            rows = expenseRows + ReportRow(
                listOf(textCell("جمع"), moneyCell(expenseRial), percentCell(1.0)),
                emphasis = true,
            ),
        )
    }

    val incomeRows = rowsOf(income, incomeRial)
    if (incomeRows.isNotEmpty()) {
        sections += ReportSection(
            titleFa = "درآمد به تفکیک دسته",
            columns = CATEGORY_COLUMNS,
            rows = incomeRows + ReportRow(
                listOf(textCell("جمع"), moneyCell(incomeRial), percentCell(1.0)),
                emphasis = true,
            ),
        )
    }

    return sections
}

/**
 * The household view, when the ledger actually names people.
 *
 * A solo user's rows have no member name — the ledger stores the id of whoever this device
 * belongs to — and printing that id as if it were a person would be noise dressed as data. So
 * the section exists only when at least one in-range row carries a real name.
 */
private fun memberSection(inRange: List<LedgerEntry>): ReportSection? {
    val named = inRange.filter { it.ownerName.isNotBlank() }
    if (named.isEmpty()) return null

    data class Totals(var income: Long = 0L, var spent: Long = 0L)

    val byMember = linkedMapOf<String, Totals>()
    for (entry in named) {
        val signed = entry.txn.signedRial ?: continue
        val totals = byMember.getOrPut(entry.ownerName) { Totals() }
        if (signed > 0) totals.income += signed else if (signed < 0) totals.spent += -signed
    }

    val rows = byMember.entries
        .sortedByDescending { it.value.spent }
        .map { (name, t) ->
            ReportRow(
                listOf(
                    textCell(name),
                    moneyCell(t.income),
                    moneyCell(t.spent),
                    moneyCell(t.income - t.spent),
                ),
            )
        }
        .toMutableList()
    rows += ReportRow(
        listOf(
            textCell("جمع"),
            moneyCell(byMember.values.sumOf { it.income }),
            moneyCell(byMember.values.sumOf { it.spent }),
            moneyCell(byMember.values.sumOf { it.income - it.spent }),
        ),
        emphasis = true,
    )

    return ReportSection("خانواده", MEMBER_COLUMNS, rows)
}

/** One due line, before it becomes a table row. */
private data class DueLine(
    val dueDay: Long,
    val titleFa: String,
    val kindFa: String,
    val rial: Long,
)

/**
 * What is owed and falls due inside the window.
 *
 * Unpaid installments and manual commitments are merged into one table because from her side
 * they are the same question — «این ماه چقدر باید کنار بگذارم؟». Paid installments are not here:
 * they already left the account and are in the spending table.
 */
private fun dueSection(inputs: ReportInputs): ReportSection? {
    val obligationsById = inputs.obligations.associateBy { it.id }
    val lines = mutableListOf<DueLine>()

    for (payment in inputs.payments) {
        if (payment.paidDate != null) continue
        val obligation = obligationsById[payment.obligationId] ?: continue
        val dueDay = tehranDay(payment.dueDate)
        if (dueDay !in inputs.range) continue
        lines += DueLine(
            dueDay = dueDay,
            // One row per installment, not one per obligation: a report for a given month has to
            // say which installment that month is paying.
            titleFa = "${obligation.provider} — قسط ${faNumber(payment.installmentNumber)}",
            kindFa = "قسط",
            rial = payment.amountRial,
        )
    }

    for (commitment in inputs.commitments) {
        if (commitment.nextDueTimestamp <= 0L) continue
        val dueDay = tehranDay(commitment.nextDueTimestamp)
        if (dueDay !in inputs.range) continue
        val title = commitment.title.ifBlank { commitment.provider }
        val withProvider = if (commitment.provider.isNotBlank() && commitment.provider != title) {
            "$title — ${commitment.provider}"
        } else {
            title
        }
        lines += DueLine(
            dueDay = dueDay,
            titleFa = withProvider,
            kindFa = commitment.kind.fa,
            rial = commitment.remainingAmountRial,
        )
    }

    if (lines.isEmpty()) return null

    val rows = lines.sortedBy { it.dueDay }.map { line ->
        ReportRow(
            listOf(
                textCell(line.titleFa),
                textCell(line.kindFa),
                textCell(faDate(line.dueDay)),
                moneyCell(line.rial),
            ),
        )
    }.toMutableList()
    rows += ReportRow(
        listOf(
            textCell("جمع"),
            textCell(""),
            textCell(""),
            moneyCell(lines.sumOf { it.rial }),
        ),
        emphasis = true,
    )

    return ReportSection(
        titleFa = "قسط‌ها و تعهدهای سررسید",
        columns = DUE_COLUMNS,
        rows = rows,
    )
}

/**
 * A snapshot of what she holds, priced only from rates the app already had.
 *
 * No fetch happens here, and none should: an export that blocks on the network is an export that
 * fails on a plane. A holding with no cached rate is shown as «بدون قیمت» and kept out of the
 * total rather than valued at zero, because zero is a number that would be wrong.
 */
private fun assetSection(assets: List<AssetLine>): ReportSection? {
    if (assets.isEmpty()) return null

    val rows = assets.map { asset ->
        ReportRow(
            listOf(
                textCell(if (asset.inTotal) asset.nameFa else "${asset.nameFa} (خارج از جمع)"),
                textCell(asset.amountFa),
                asset.toman?.let { moneyCellToman(it) } ?: textCell("بدون قیمت"),
            ),
        )
    }
    val total = assets.filter { it.inTotal }.sumOf { it.toman ?: 0.0 }
    val unpriced = assets.count { it.inTotal && it.toman == null }

    val withTotal = rows.toMutableList()
    withTotal += ReportRow(
        listOf(textCell("جمع دارایی‌ها"), textCell(""), moneyCellToman(total)),
        emphasis = true,
    )

    val notes = mutableListOf(
        // An asset snapshot is "now", not "at the end of the window". Saying so is the
        // difference between a reader understanding the number and trusting it wrongly.
        "ارزش‌ها با آخرین قیمت‌های ذخیره‌شده در برنامه است، نه قیمت پایان بازه.",
    )
    if (unpriced > 0) {
        notes += "برای $unpriced دارایی قیمت تازه‌ای در برنامه نبود؛ در جمع حساب نشده است."
    }

    return ReportSection(
        titleFa = "دارایی‌ها",
        columns = ASSET_COLUMNS,
        rows = withTotal,
        noteFa = notes.joinToString(" "),
    )
}
