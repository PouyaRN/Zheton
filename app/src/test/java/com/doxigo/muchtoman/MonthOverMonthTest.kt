package com.doxigo.muchtoman

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class MonthOverMonthTest {

    private var n = 0

    private fun entry(
        day: Long,
        signed: Long?,
        categoryId: String = "groceries",
        category: String = "خواربار",
        transfer: Boolean = false,
        duplicate: Boolean = false,
        hidden: Boolean = false,
    ): LedgerEntry {
        n++
        val ref = "s:%04d:0".format(n)
        return LedgerEntry(
            txn = Txn(
                ref = ref, srcHash = ref, seq = 0, at = tehranDayStart(day), day = day,
                bank = "SAMAN", accountId = "SAMAN",
                direction = signed?.let { if (it > 0) "in" else "out" },
                amountRial = signed?.let { kotlin.math.abs(it) }, signedRial = signed,
                balanceRial = null, feeRial = null, mask = "", instrument = "unknown",
                merchant = "", merchantNorm = "", refNo = "", printedAt = "",
                channel = "unknown", unitPrinted = "none", inferred = false,
                parserVer = PARSER_VERSION,
            ),
            categoryId = categoryId, categoryFa = category, confidence = 95,
            needsReview = false, duplicate = duplicate, transfer = transfer, hidden = hidden,
        )
    }

    private val mehr = ReportMonth(1405, 7)
    private val mehr1 = jalaliDay(1405, 7, 1)
    private val shahrivar1 = jalaliDay(1405, 6, 1)
    private val later = jalaliDay(1405, 9, 1)
    private val calendar = CycleSpec()

    private fun food(day: Long, rial: Long) = entry(day, -rial, "food", "رستوران و کافه")
    private fun grocery(day: Long, rial: Long) = entry(day, -rial)
    private fun home(day: Long, rial: Long) = entry(day, -rial, "home", "خانه و قبوض")

    /** The canvas month: saved, restaurants down 31%, groceries up. */
    private val canvas = listOf(
        entry(shahrivar1, 200_000_000),
        food(shahrivar1 + 2, 45_000_000),
        grocery(shahrivar1 + 3, 45_000_000),
        home(shahrivar1 + 4, 60_000_000),
        entry(mehr1, 240_000_000),
        food(mehr1 + 1, 31_000_000),
        grocery(mehr1 + 5, 52_000_000),
        home(mehr1 + 5, 60_000_000),
        entry(mehr1 + 6, -500_000_000, transfer = true),
        grocery(mehr1 + 7, 90_000_000).copy(duplicate = true),
        grocery(mehr1 + 8, 70_000_000).copy(hidden = true),
    )

    @Test
    fun `income spend saved and rate leave out transfers duplicates and hidden rows`() {
        val r = monthOverMonth(canvas, mehr, calendar, later)
        assertEquals(240_000_000L, r.incomeRial)
        assertEquals(143_000_000L, r.spentRial)
        assertEquals(97_000_000L, r.savedRial)
        assertEquals(97.0 / 240, r.savingsRate!!, 1e-9)
        assertEquals(200_000_000L, r.previousIncomeRial)
        assertEquals(150_000_000L, r.previousSpentRial)
        assertEquals(ReportMonth(1405, 6), r.previousMonth)
        assertFalse(r.inProgress)
    }

    @Test
    fun `savings rate is null without income`() {
        val r = monthOverMonth(listOf(grocery(mehr1, 1_000)), mehr, calendar, later)
        assertNull(r.savingsRate)
    }

    @Test
    fun `cumulative spend covers every day and ends at the month total`() {
        val r = monthOverMonth(canvas, mehr, calendar, later)
        assertEquals(30, r.cumulativeSpend.size) // مهر has 30 days
        assertEquals(31, r.previousCumulativeSpend.size) // شهریور has 31
        assertEquals(0L, r.cumulativeSpend[0])
        assertEquals(31_000_000L, r.cumulativeSpend[1])
        assertEquals(143_000_000L, r.cumulativeSpend[5])
        assertEquals(r.spentRial, r.cumulativeSpend.last())
        assertEquals(r.previousSpentRial, r.previousCumulativeSpend.last())
        assertTrue(r.cumulativeSpend.zipWithNext().all { (a, b) -> b >= a })
    }

    @Test
    fun `the month in progress stops its line at today and a future month has none`() {
        val r = monthOverMonth(canvas, mehr, calendar, mehr1 + 3)
        assertEquals(4, r.cumulativeSpend.size)
        assertTrue(r.inProgress)
        assertEquals(31, r.previousCumulativeSpend.size)
        val future = monthOverMonth(canvas, mehr.next(), calendar, mehr1 + 3)
        assertTrue(future.cumulativeSpend.isEmpty())
    }

    @Test
    fun `categories carry change against last month and none when last month had none`() {
        val rows = canvas + entry(mehr1 + 9, -5_000_000, "fun", "تفریح")
        val r = monthOverMonth(rows, mehr, calendar, later)
        assertEquals(listOf("home", "groceries", "food", "fun"), r.categories.map { it.categoryId })
        val food = r.categories.first { it.categoryId == "food" }
        assertEquals("رستوران و کافه", food.nameFa)
        assertEquals(-14.0 / 45, food.change!!, 1e-9)
        assertEquals(0.0, r.categories.first { it.categoryId == "home" }.change!!, 1e-9)
        assertNull(r.categories.first { it.categoryId == "fun" }.change)
        assertEquals(1.0, r.categories.sumOf { it.share }, 1e-9)
    }

    @Test
    fun `the canvas sentence comes out with its highlights and tones`() {
        val r = monthOverMonth(canvas, mehr, calendar, later)
        assertEquals(
            "مهر رو با ۹٫۷ میلیون پس‌انداز بستی. خرج رستوران و کافه ۳۱٪ کم شد، ولی خواربار داره بالا می‌ره.",
            r.summaryText,
        )
        val lit = r.summary.filterIsInstance<SummaryPart.Highlight>()
        assertEquals(
            listOf(
                "۹٫۷ میلیون پس‌انداز" to SummaryTone.GOOD,
                "رستوران و کافه ۳۱٪ کم" to SummaryTone.GOOD,
                "خواربار داره بالا می‌ره." to SummaryTone.WARNING,
            ),
            lit.map { it.text to it.tone },
        )
    }

    @Test
    fun `overspending is a warning and the running month says so far`() {
        val rows = listOf(entry(mehr1, 10_000_000), grocery(mehr1 + 1, 30_000_000))
        val closed = monthOverMonth(rows, mehr, calendar, later)
        assertEquals("مهر رو با ۲ میلیون بیشتر از درآمدت خرج بستی.", closed.summaryText)
        assertEquals(SummaryTone.WARNING, closed.summary.filterIsInstance<SummaryPart.Highlight>().single().tone)
        val running = monthOverMonth(rows, mehr, calendar, mehr1 + 2)
        assertEquals("تا امروز تو مهر ۲ میلیون بیشتر از درآمدت خرج کردی.", running.summaryText)
    }

    @Test
    fun `small moves and small categories stay out of the sentence`() {
        val rows = listOf(
            entry(shahrivar1, 200_000_000),
            grocery(shahrivar1, 100_000_000),
            food(shahrivar1, 2_000_000),
            entry(mehr1, 200_000_000),
            grocery(mehr1, 105_000_000), // +5%: too small a move
            food(mehr1, 500_000), // −75% of a category under 5% of spend
        )
        val r = monthOverMonth(rows, mehr, calendar, later)
        assertEquals("مهر رو با ۹٫۴ میلیون پس‌انداز بستی.", r.summaryText)
    }

    @Test
    fun `a rise alone is worded without but`() {
        val rows = listOf(grocery(shahrivar1, 10_000_000), entry(mehr1, 50_000_000), grocery(mehr1, 20_000_000))
        val r = monthOverMonth(rows, mehr, calendar, later)
        assertEquals("مهر رو با ۳ میلیون پس‌انداز بستی. خرج خواربار داره بالا می‌ره.", r.summaryText)
    }

    @Test
    fun `an empty month says nothing was recorded`() {
        val r = monthOverMonth(emptyList(), mehr, calendar, later)
        assertEquals(listOf(SummaryPart.Plain("برای مهر هنوز تراکنشی ثبت نشده.")), r.summary)
        assertTrue(r.categories.isEmpty())
    }

    @Test
    fun `an anchored cycle runs from the anchor day in this month to the one in the next`() {
        val spec = CycleSpec(CycleMode.ANCHOR, 25)
        val w = mehr.window(spec)
        assertEquals(jalaliDay(1405, 7, 25), w.startDay)
        assertEquals(jalaliDay(1405, 8, 25), w.endDay)
        val rows = listOf(
            entry(jalaliDay(1405, 7, 24), 99_000_000), // belongs to شهریور's cycle
            entry(jalaliDay(1405, 7, 25), 50_000_000),
            grocery(jalaliDay(1405, 8, 10), 10_000_000),
        )
        val r = monthOverMonth(rows, mehr, spec, later)
        assertEquals(50_000_000L, r.incomeRial)
        assertEquals(99_000_000L, r.previousIncomeRial)
        assertEquals(30, r.cumulativeSpend.size)
        assertEquals(ReportMonth(1405, 7), reportMonthOf(jalaliDay(1405, 8, 10), spec))
        assertEquals(ReportMonth(1405, 8), reportMonthOf(jalaliDay(1405, 8, 25), spec))
    }

    @Test
    fun `month switching rolls the year`() {
        assertEquals(ReportMonth(1404, 12), ReportMonth(1405, 1).previous())
        assertEquals(ReportMonth(1406, 1), ReportMonth(1405, 12).next())
        assertEquals(ReportMonth(1405, 7), reportMonthOf(mehr1 + 10, calendar))
    }
}
