package com.doxigo.muchtoman.features.export

import com.doxigo.muchtoman.Commitment
import com.doxigo.muchtoman.CommitmentKind
import com.doxigo.muchtoman.InstallmentObligation
import com.doxigo.muchtoman.InstallmentPayment
import com.doxigo.muchtoman.JalaliDate
import com.doxigo.muchtoman.LedgerEntry
import com.doxigo.muchtoman.PARSER_VERSION
import com.doxigo.muchtoman.Txn
import com.doxigo.muchtoman.jalaliDay
import com.doxigo.muchtoman.jalaliMonthLength
import com.doxigo.muchtoman.tehranDayStart
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The report is the app's money turned into a document somebody else may act on, so every figure
 * here is checked by hand rather than against a snapshot. The tests also pin the two rules that
 * are easy to break by accident: RTL ordering of the columns and the promise that no raw
 * message, card mask or reference number reaches the file.
 */
class ReportBuilderTest {

    private var sequence = 0

    private fun entry(
        day: Long,
        signed: Long?,
        category: String = "خرید",
        owner: String = "",
        transfer: Boolean = false,
        duplicate: Boolean = false,
        hidden: Boolean = false,
        review: Boolean = false,
        refNo: String = "",
        mask: String = "",
        merchant: String = "",
    ): LedgerEntry {
        sequence++
        val ref = "s:%04d:0".format(sequence)
        return LedgerEntry(
            txn = Txn(
                ref = ref,
                srcHash = ref,
                seq = 0,
                at = tehranDayStart(day),
                day = day,
                bank = "SAMAN",
                accountId = "SAMAN",
                direction = signed?.let { if (it > 0) "in" else "out" },
                amountRial = signed?.let { kotlin.math.abs(it) },
                signedRial = signed,
                balanceRial = null,
                feeRial = null,
                mask = mask,
                instrument = "unknown",
                merchant = merchant,
                merchantNorm = merchant,
                refNo = refNo,
                printedAt = "",
                channel = "unknown",
                unitPrinted = "none",
                inferred = false,
                parserVer = PARSER_VERSION,
                ownerMemberId = owner.ifBlank { "" },
            ),
            categoryId = "cat_x",
            categoryFa = category,
            confidence = if (review) 40 else 95,
            needsReview = review,
            duplicate = duplicate,
            transfer = transfer,
            hidden = hidden,
            ownerName = owner,
        )
    }

    private val year = 1405
    private val month = 5
    private val first = jalaliDay(year, month, 1)
    private val range = monthRange(year, month)

    private fun report(
        entries: List<LedgerEntry>,
        obligations: List<InstallmentObligation> = emptyList(),
        payments: List<InstallmentPayment> = emptyList(),
        commitments: List<Commitment> = emptyList(),
        assets: List<AssetLine> = emptyList(),
    ) = buildReport(
        ReportInputs(
            range = range,
            entries = entries,
            obligations = obligations,
            payments = payments,
            commitments = commitments,
            assets = assets,
            generatedAt = tehranDayStart(first),
        ),
    )

    private fun ReportDocument.section(title: String): ReportSection? =
        sections.firstOrNull { it.titleFa == title }

    private fun ReportSection.row(label: String): ReportRow? =
        rows.firstOrNull { it.cells.firstOrNull()?.text == label }

    private fun ReportRow.money(index: Int): Double? = cells[index].number

    // ─────────────────────────── totals ───────────────────────────

    @Test
    fun `income expense and net are what the range actually moved`() {
        val document = report(
            listOf(
                entry(first, 100_000_000),
                entry(first + 1, -30_000_000),
                entry(first + 2, -20_000_000),
            ),
        )

        val summary = document.section("خلاصهٔ بازه")!!
        // 100_000_000 Rial is 10_000_000 Toman: the report never prints Rial.
        assertEquals(10_000_000.0, summary.row("درآمد بازه")!!.money(1)!!, 0.0001)
        assertEquals(5_000_000.0, summary.row("خرج بازه")!!.money(1)!!, 0.0001)
        assertEquals(5_000_000.0, summary.row("خالص")!!.money(1)!!, 0.0001)
        // A rate, not a Toman figure: a smaller number of Toman is not a smaller amount of money.
        assertEquals(0.5, summary.row("نرخ پس‌انداز")!!.cells[1].number!!, 0.0001)
        assertEquals(3.0, summary.row("تعداد تراکنش")!!.cells[1].number!!, 0.0)
    }

    @Test
    fun `duplicates hidden rows and transfers never become income or spending`() {
        val document = report(
            listOf(
                entry(first, 100_000_000),
                entry(first + 1, -30_000_000),
                entry(first + 2, -5_000_000, duplicate = true),
                entry(first + 3, -7_000_000, hidden = true),
                entry(first + 4, -9_000_000, transfer = true),
                entry(first + 5, 40_000_000, transfer = true),
            ),
        )

        val summary = document.section("خلاصهٔ بازه")!!
        assertEquals(10_000_000.0, summary.row("درآمد بازه")!!.money(1)!!, 0.0001)
        assertEquals(3_000_000.0, summary.row("خرج بازه")!!.money(1)!!, 0.0001)
        assertEquals(2.0, summary.row("تعداد تراکنش")!!.cells[1].number!!, 0.0)
    }

    // ─────────────────────────── categories ───────────────────────────

    @Test
    fun `each category carries its share of the table's own total`() {
        val document = report(
            listOf(
                entry(first, -75_000_000, category = "خرید"),
                entry(first + 1, -25_000_000, category = "حمل‌ونقل"),
                entry(first + 2, 200_000_000, category = "حقوق"),
            ),
        )

        val spending = document.section("خرج به تفکیک دسته")!!
        // Largest first: the expense table is read from the top.
        assertEquals("خرید", spending.rows[0].cells[0].text)
        assertEquals(7_500_000.0, spending.rows[0].money(1)!!, 0.0001)
        assertEquals(0.75, spending.rows[0].cells[2].number!!, 0.0001)
        assertEquals("حمل‌ونقل", spending.rows[1].cells[0].text)

        val total = spending.rows.last()
        assertTrue("total row is emphasised", total.emphasis)
        assertEquals(10_000_000.0, total.money(1)!!, 0.0001)

        val income = document.section("درآمد به تفکیک دسته")!!
        assertEquals("حقوق", income.rows[0].cells[0].text)
        assertEquals(20_000_000.0, income.rows[0].money(1)!!, 0.0001)
    }

    @Test
    fun `an empty range is a valid report and says so`() {
        val document = report(listOf(entry(first - 40, -1_000_000)))

        assertEquals(1, document.sections.size)
        val summary = document.sections.single()
        assertEquals("خلاصهٔ بازه", summary.titleFa)
        assertEquals(0.0, summary.row("درآمد بازه")!!.money(1)!!, 0.0)
        assertEquals(0.0, summary.row("خرج بازه")!!.money(1)!!, 0.0)
        assertEquals(0.0, summary.row("تعداد تراکنش")!!.cells[1].number!!, 0.0)
        // No income means no savings rate; a printed 0% would claim she saved nothing.
        assertEquals("—", summary.row("نرخ پس‌انداز")!!.cells[1].text)
        assertTrue(summary.noteFa.contains("تراکنشی ثبت نشده"))
        assertNull(document.section("خرج به تفکیک دسته"))
    }

    // ─────────────────────────── household ───────────────────────────

    @Test
    fun `family summary appears only when owners are named`() {
        val members = report(
            listOf(
                entry(first, 60_000_000, owner = "سارا"),
                entry(first + 1, -20_000_000, owner = "سارا"),
                entry(first + 2, 30_000_000, owner = "رضا"),
            ),
        )
        val section = members.section("خانواده")!!
        assertEquals(3, section.rows.size) // two people plus the total
        assertEquals("سارا", section.rows[0].cells[0].text)
        assertEquals(4_000_000.0, section.rows[0].cells[3].number!!, 0.0001)
        assertTrue(section.rows.last().emphasis)

        // No names — a solo ledger — must not print an internal member id as a person.
        val solo = report(listOf(entry(first, 60_000_000)))
        assertNull(solo.section("خانواده"))
    }

    // ─────────────────────────── due dates ───────────────────────────

    @Test
    fun `only unpaid installments and commitments due inside the range are listed`() {
        val obligation = InstallmentObligation(
            id = "o1",
            provider = "دیجی‌پی",
            totalRial = 30_000_000,
            downPaymentRial = 0,
            installmentCount = 3,
            installmentRial = 10_000_000,
            startDate = tehranDayStart(first),
            createdAt = 0,
            updatedAt = 0,
        )
        val payments = listOf(
            // in range, unpaid
            InstallmentPayment("p1", "o1", 1, tehranDayStart(first + 3), 10_000_000),
            // in range, already paid: it is spending, not a due date
            InstallmentPayment("p2", "o1", 2, tehranDayStart(first + 4), 10_000_000, paidDate = 1L),
            // outside the range
            InstallmentPayment("p3", "o1", 3, tehranDayStart(first + 40), 10_000_000),
        )
        val commitments = listOf(
            Commitment(
                id = "c1",
                title = "چک صیادی",
                provider = "بانک ملت",
                kind = CommitmentKind.CHECK,
                totalAmountRial = 90_000_000,
                remainingAmountRial = 30_000_000,
                nextDueTimestamp = tehranDayStart(first + 1),
            ),
            Commitment(
                id = "c2",
                title = "وام",
                provider = "بانک ملت",
                kind = CommitmentKind.LOAN,
                totalAmountRial = 1,
                remainingAmountRial = 1,
                nextDueTimestamp = tehranDayStart(first + 60),
            ),
        )

        val document = report(
            entries = listOf(entry(first, -1_000_000)),
            obligations = listOf(obligation),
            payments = payments,
            commitments = commitments,
        )
        val section = document.section("قسط‌ها و تعهدهای سررسید")!!

        // Sorted by due date, not by kind: the table answers «چه چیزی اول می‌رسد؟».
        assertEquals("چک صیادی — بانک ملت", section.rows[0].cells[0].text)
        assertEquals(faDate(first + 1), section.rows[0].cells[2].text)
        // The kind column says what the line is; the description names the provider and which
        // installment of the schedule this month pays.
        assertEquals("قسط", section.rows[1].cells[1].text)
        assertTrue(section.rows[1].cells[0].text.contains("دیجی‌پی"))
        assertTrue(section.rows[1].cells[0].text.contains("قسط ۱"))
        assertEquals(2, section.rows.size - 1) // two due lines, one total
        assertEquals(4_000_000.0, section.rows.last().money(3)!!, 0.0001)
    }

    // ─────────────────────────── assets ───────────────────────────

    @Test
    fun `asset total keeps excluded holdings out and never values an unpriced one at zero`() {
        val document = report(
            entries = listOf(entry(first, -1_000_000)),
            assets = listOf(
                AssetLine("طلا", "۱۰ گرم", 50_000_000.0, inTotal = true),
                AssetLine("تتر", "۵۰۰", 20_000_000.0, inTotal = false),
                AssetLine("سهام", "۱۰۰", null, inTotal = true),
            ),
        )
        val section = document.section("دارایی‌ها")!!

        val total = section.rows.last()
        assertEquals(50_000_000.0, total.money(2)!!, 0.0001)
        assertTrue(section.rows.any { it.cells[0].text.contains("(خارج از جمع)") })
        assertTrue(section.rows.any { it.cells[2].text == "بدون قیمت" })
        assertTrue(section.noteFa.contains("بدون") || section.noteFa.contains("قیمت"))
    }

    // ─────────────────────────── privacy ───────────────────────────

    @Test
    fun `no raw message card mask or reference number reaches any cell`() {
        val document = report(
            listOf(
                entry(
                    first,
                    -42_000_000,
                    refNo = "1234567890123456",
                    mask = "6037-****-4321",
                    merchant = "فروشگاه نمونه",
                ),
            ),
        )
        val allText = document.sections
            .flatMap { section ->
                section.rows.flatMap { row -> row.cells.map { it.text } } +
                    section.noteFa
            }
            .joinToString(" | ")

        assertFalse(allText.contains("1234567890123456"))
        assertFalse(allText.contains("6037"))
        assertFalse(allText.contains("4321"))
        assertFalse(allText.contains("فروشگاه نمونه"))
    }

    // ─────────────────────────── windows and RTL text ───────────────────────────

    @Test
    fun `windows resolve to the Jalali month boundaries`() {
        val today = jalaliDay(year, month, 15)

        val thisMonth = ReportWindow.THIS_MONTH.resolve(today)
        assertEquals(first, thisMonth.from)
        assertEquals(first + jalaliMonthLength(year, month), thisMonth.until)

        val lastMonth = ReportWindow.LAST_MONTH.resolve(today)
        assertEquals(jalaliDay(year, 4, 1), lastMonth.from)

        val last30 = ReportWindow.LAST_30_DAYS.resolve(today)
        assertEquals(30, last30.dayCount)
        assertEquals(today, last30.until - 1)
    }

    @Test
    fun `a reversed custom range is normalised rather than rejected`() {
        val from = JalaliDate(year, month, 20)
        val to = JalaliDate(year, month, 2)
        val normalised = customRange(from, to)
        assertEquals(jalaliDay(year, month, 2), normalised.from)
        assertEquals(jalaliDay(year, month, 20) + 1, normalised.until)
    }

    @Test
    fun `the printed calendar and money keep Persian digits and RTL order`() {
        // Persian digits, Jalali order: year/month/day, regardless of the device locale.
        assertEquals("۱۴۰۵/۰۵/۰۱", faDate(first))
        // Grouped Persian digits with the Arabic thousands separator, exact tenth when present.
        assertEquals("۱٬۲۳۴٬۵۶۷", faToman(1_234_567.0))
        assertEquals("۵۰۰٫۵", faToman(500.5))
        assertEquals("۲۳٫۴٪", faPercentText(0.234))
        // A whole month is named the way it is spoken; a partial one falls back to two dates.
        assertEquals("مرداد ۱۴۰۵", rangeLabelFa(range))
        assertTrue(rangeLabelFa(DayRange(first, first + 10)).contains(" تا "))
        // The first column in every table is the label, which in RTL is the rightmost one.
        val document = report(listOf(entry(first, -1_000_000, category = "خرید")))
        assertEquals("دسته", document.section("خرج به تفکیک دسته")!!.columns[0].headerFa)
        assertEquals("شرح", document.section("خلاصهٔ بازه")!!.columns[0].headerFa)
    }
}
