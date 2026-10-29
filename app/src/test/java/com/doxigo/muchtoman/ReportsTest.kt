package com.doxigo.muchtoman

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The reports, which are the only place the ledger becomes a claim about her money — so every
 * one of them has to be arithmetic that can be checked by hand.
 */
class ReportsTest {

    private var n = 0

    private fun entry(
        day: Long,
        signed: Long?,
        category: String = "خرید",
        transfer: Boolean = false,
        duplicate: Boolean = false,
        review: Boolean = false,
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
            categoryId = "cat_x", categoryFa = category, confidence = if (review) 40 else 95,
            needsReview = review, duplicate = duplicate, transfer = transfer,
        )
    }

    private val year = 1405
    private val month = 5
    private val first = jalaliDay(year, month, 1)

    @Test
    fun `income and spending are what the month actually moved`() {
        val rows = listOf(
            entry(first, 100_000_000),
            entry(first + 1, -30_000_000),
            entry(first + 2, -20_000_000),
        )
        val r = monthReport(rows, year, month)
        assertEquals(100_000_000L, r.incomeRial)
        assertEquals(50_000_000L, r.spentRial)
        assertEquals(50_000_000L, r.netRial)
        assertEquals(0.5, r.savingsRate!!, 0.0001)
    }

    @Test
    fun `a transfer between her own accounts is neither income nor spending`() {
        // Without this, moving money between her own accounts reads as earning it and spending
        // it in the same month — the same quietly-wrong total this app exists to avoid.
        val rows = listOf(
            entry(first, 100_000_000),
            entry(first + 1, -500_000_000, transfer = true),
            entry(first + 1, 500_000_000, transfer = true),
        )
        val r = monthReport(rows, year, month)
        assertEquals(100_000_000L, r.incomeRial)
        assertEquals(0L, r.spentRial)
        assertEquals(1, r.transactions)
    }

    @Test
    fun `the later leg of a duplicate is not counted twice`() {
        val rows = listOf(
            entry(first, -5_000_000),
            entry(first, -5_000_000, duplicate = true),
        )
        assertEquals(5_000_000L, monthReport(rows, year, month).spentRial)
    }

    @Test
    fun `a month with no income has no savings rate rather than a bad one`() {
        // Zero income is not a hard month, it is no information. A rate here would be a
        // division by zero wearing a percentage sign.
        val r = monthReport(listOf(entry(first, -5_000_000)), year, month)
        assertNull(r.savingsRate)
    }

    @Test
    fun `nothing from another month leaks in`() {
        val rows = listOf(
            entry(first - 1, -9_000_000),
            entry(first, -1_000_000),
            entry(first + jalaliMonthLength(year, month), -8_000_000),
        )
        assertEquals(1_000_000L, monthReport(rows, year, month).spentRial)
    }

    @Test
    fun `the month before فروردین is اسفند of the year before`() {
        assertEquals(1404 to 12, previousMonth(1405, 1))
        assertEquals(1405 to 4, previousMonth(1405, 5))
    }

    @Test
    fun `runway uses the median day so one big repair does not shorten it`() {
        // Twenty ordinary days and one enormous one. A mean would report roughly half the
        // runway the median does, and she would plan around the wrong number.
        val rows = (0 until 20).map { entry(first + it, -1_000_000) } +
            entry(first + 20, -500_000_000)
        val days = bufferDays(rows, liquidRial = 100_000_000, today = first + 25)
        assertEquals(100, days)
    }

    @Test
    fun `too little history says nothing at all`() {
        val rows = (0 until 5).map { entry(first + it, -1_000_000) }
        assertNull(bufferDays(rows, liquidRial = 100_000_000, today = first + 10))
    }

    @Test
    fun `the automatic share is the number the app is judged on`() {
        val rows = listOf(
            entry(first, -1_000_000),
            entry(first, -1_000_000),
            entry(first, -1_000_000, review = true),
            entry(first, -1_000_000),
        )
        assertEquals(0.75, monthReport(rows, year, month).automaticShare!!, 0.0001)
    }

    @Test
    fun `every insight carries the transactions it came from`() {
        val rows = listOf(entry(first, 100_000_000), entry(first + 1, -40_000_000))
        val now = monthReport(rows, year, month)
        val insights = narrate(now, null, rows, bufferDays = 45)
        assertTrue(insights.isNotEmpty())
        // «چرا این را می‌بینم؟» has to be answerable for every line on the screen.
        for (insight in insights) {
            assertTrue("«${insight.text}» explains nothing", insight.why.isNotBlank())
        }
        val savings = insights.first { it.text.contains("درآمدت") }
        assertTrue("a claim about the month must name its transactions", savings.refs.isNotEmpty())
    }

    @Test
    fun `nothing is celebrated for being fewer toman`() {
        // Inflation makes a nominal fall meaningless. Every comparison the narrative draws has
        // to be a rate, a share, or a number of days — never an amount.
        val (py, pm) = previousMonth(year, month)
        val before = monthReport(
            listOf(entry(first - 31, 100_000_000), entry(first - 30, -90_000_000)),
            py, pm,
        )
        val now = monthReport(
            listOf(entry(first, 40_000_000), entry(first + 1, -38_000_000)),
            year, month,
        )
        // Spending fell in Toman (90m to 38m) while the savings rate got worse (10% to 5%).
        assertTrue(now.spentRial < before.spentRial)
        assertTrue(now.savingsRate!! < before.savingsRate!!)
        val text = narrate(now, before, emptyList(), null).joinToString(" ") { it.text }
        assertTrue("a worse month must not be reported as an improvement", !text.contains("بهتر"))
        assertTrue(quietWins(now, before, null).isEmpty())
    }

    @Test
    fun `spending more than came in is said plainly, not as a negative percentage`() {
        // "minus twenty-nine percent of your income remained" is arithmetic read aloud.
        // Nothing remained; she spent more than came in, and that is what the line has to say.
        val rows = listOf(entry(first, 100_000_000), entry(first + 1, -129_000_000))
        val now = monthReport(rows, year, month)
        assertTrue(now.savingsRate!! < 0)
        val text = narrate(now, null, rows, null).first { it.text.contains("درآمد") }.text
        assertTrue("«$text» should not report a negative remainder", !text.contains("−"))
        assertTrue("«$text» should not report a negative remainder", !text.contains("-"))
        assertTrue("«$text» should say she overspent", text.contains("بیشتر از درآمدت"))
    }

    @Test
    fun `a win is a real outcome, never an app-opening`() {
        val (py, pm) = previousMonth(year, month)
        val before = monthReport(listOf(entry(first - 31, -5_000_000)), py, pm)
        val now = monthReport(
            listOf(entry(first, 100_000_000), entry(first + 1, -10_000_000)),
            year, month,
        )
        val wins = quietWins(now, before, bufferDays = 45)
        assertTrue(wins.any { it.text.contains("اولین ماهی") })
        assertTrue(wins.any { it.text.contains("یک ماه خرج") })
        assertTrue(wins.all { it.tone == Insight.Tone.GOOD })
    }

    @Test
    fun `changeOver finds the closest base day within grace window`() {
        val today = 1000L
        val history = mapOf(
            970L to 100_000.0, // target is 1000 - 30 = 970
            900L to 50_000.0,
        )
        val c = changeOver(history, today, 30, 150_000.0)
        assertNotNull(c)
        assertEquals(50_000.0, c!!.delta, 0.001)
        assertEquals(50.0, c.percent!!, 0.001)
        assertEquals(970L, c.sinceDay)

        // When exact target is missing but within grace window (target + 1..3)
        val graceHistory = mapOf(
            972L to 100_000.0,
        )
        val cGrace = changeOver(graceHistory, today, 30, 120_000.0)
        assertNotNull(cGrace)
        assertEquals(972L, cGrace!!.sinceDay)

        // When data is too fresh (e.g. only 10 days of history for 30-day window) -> null
        val shortHistory = mapOf(990L to 100_000.0)
        assertNull(changeOver(shortHistory, today, 30, 120_000.0))
    }

    @Test
    fun `jalaliMonthsBack correctly traverses back across Persian year boundary`() {
        // Day in Farvardin 1405 (month 1, day 15)
        val dayInFarvardin = jalaliDay(1405, 1, 15)
        // 3 months back: Dey 1404 (month 10)
        val threeMonthsBack = jalaliMonthsBack(dayInFarvardin, 3)
        val j3 = jalaliOf(threeMonthsBack)
        assertEquals(1404, j3.year)
        assertEquals(10, j3.month)
        assertEquals(1, j3.day)

        // 12 months back: Farvardin 1404
        val twelveMonthsBack = jalaliMonthsBack(dayInFarvardin, 12)
        val j12 = jalaliOf(twelveMonthsBack)
        assertEquals(1404, j12.year)
        assertEquals(1, j12.month)
        assertEquals(1, j12.day)
    }

    @Test
    fun `findClosestPointIndex returns correct index for various touch positions`() {
        val points = listOf(
            100L to 10.0,
            101L to 20.0,
            102L to 30.0,
            103L to 40.0,
            104L to 50.0,
        )
        val width = 400f
        // Indices 0 to 4 map to x = 0, 100, 200, 300, 400

        // At x = 0 or negative -> index 0
        assertEquals(0, findClosestPointIndex(0f, width, points))
        assertEquals(0, findClosestPointIndex(-50f, width, points))

        // At x = 400 or greater -> index 4
        assertEquals(4, findClosestPointIndex(400f, width, points))
        assertEquals(4, findClosestPointIndex(500f, width, points))

        // Intermediate positions
        assertEquals(1, findClosestPointIndex(110f, width, points))
        assertEquals(2, findClosestPointIndex(190f, width, points))
        assertEquals(3, findClosestPointIndex(310f, width, points))

        // Empty list or single point
        assertNull(findClosestPointIndex(100f, width, emptyList()))
        assertEquals(0, findClosestPointIndex(100f, width, listOf(1L to 1.0)))
    }
}
