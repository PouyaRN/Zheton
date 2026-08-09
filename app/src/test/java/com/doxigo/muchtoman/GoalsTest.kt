package com.doxigo.muchtoman

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** Goals and «آیا ارزشش را داشت؟» — the entire reward system, and all of it arithmetic. */
class GoalsTest {

    private var n = 0
    private val year = 1405
    private val month = 5
    private val first = jalaliDay(year, month, 1)

    private fun entry(
        day: Long,
        signed: Long,
        categoryId: String = "cat_shopping",
        review: Boolean = false,
        merchant: String = "جایی",
    ): LedgerEntry {
        n++
        val ref = "s:%04d:0".format(n)
        return LedgerEntry(
            txn = Txn(
                ref = ref, srcHash = ref, seq = 0, at = tehranDayStart(day), day = day,
                bank = "SAMAN", accountId = "SAMAN",
                direction = if (signed > 0) "in" else "out",
                amountRial = kotlin.math.abs(signed), signedRial = signed,
                balanceRial = null, feeRial = null, mask = "", instrument = "unknown",
                merchant = merchant, merchantNorm = merchant, refNo = "", printedAt = "",
                channel = "unknown", unitPrinted = "none", inferred = false,
                parserVer = PARSER_VERSION,
            ),
            categoryId = categoryId, categoryFa = "خرید", confidence = 95,
            needsReview = review, duplicate = false, transfer = false,
        )
    }

    private fun goal(target: Long, kind: String = GoalKind.SAVE, category: String? = null) = Goal(
        id = "g1", nameFa = "سفر", targetRial = target, kind = kind, categoryId = category,
        period = GoalPeriod.ONCE, startsOn = first, createdAt = 0, updatedAt = 0,
    )

    @Test
    fun `a savings goal counts what was kept, not what came in`() {
        val rows = listOf(entry(first, 100_000_000), entry(first + 1, -40_000_000))
        val p = goalProgress(goal(60_000_000), rows, first + 5)
        assertEquals(60_000_000L, p.currentRial)
        assertTrue(p.done)
        assertEquals(1f, p.share, 0.001f)
    }

    @Test
    fun `a transfer between her own accounts moves no goal`() {
        val rows = listOf(
            entry(first, 100_000_000),
            entry(first + 1, -500_000_000).copy(transfer = true),
        )
        assertEquals(100_000_000L, goalProgress(goal(50_000_000), rows, first + 5).currentRial)
    }

    @Test
    fun `a cap is met by not reaching it`() {
        // Easy to get backwards, and getting it backwards congratulates her for overspending.
        val rows = listOf(entry(first, -30_000_000, categoryId = "cat_dining"))
        val under = goalProgress(goal(50_000_000, GoalKind.CAP, "cat_dining"), rows, first + 5)
        assertEquals(30_000_000L, under.currentRial)
        assertTrue("under the cap is met", under.done)

        val over = goalProgress(goal(20_000_000, GoalKind.CAP, "cat_dining"), rows, first + 5)
        assertTrue("over the cap is not", !over.done)
    }

    @Test
    fun `a goal under water shows nothing set aside, never a negative figure`() {
        // "−45 million of 50 million" is arithmetic read aloud, and it lands as a scolding.
        // Nothing was set aside; that is what the screen has to say.
        val rows = listOf(entry(first, 10_000_000), entry(first + 1, -55_000_000))
        val p = goalProgress(goal(50_000_000), rows, first + 5)
        assertTrue(p.underWater)
        assertEquals(0L, p.currentRial)
        assertEquals(0f, p.share, 0.001f)
        assertTrue(!p.done)
    }

    @Test
    fun `progress never runs past its own bar`() {
        val rows = listOf(entry(first, 900_000_000))
        assertEquals(1f, goalProgress(goal(10_000_000), rows, first + 5).share, 0.001f)
    }
}
