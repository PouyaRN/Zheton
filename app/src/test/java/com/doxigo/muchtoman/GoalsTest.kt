package com.doxigo.muchtoman

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
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

    @Test
    fun `a cap scoped to a merchant tracks only that merchant's expenses`() {
        val targetMerchant = "اسنپ فود"
        val otherMerchant = "دیجی‌کالا"
        val rows = listOf(
            entry(first, -20_000_000, categoryId = "cat_food", merchant = targetMerchant),
            entry(first + 1, -50_000_000, categoryId = "cat_food", merchant = otherMerchant),
        )
        val g = Goal(
            id = "g_merchant",
            nameFa = "اسنپ",
            targetRial = 30_000_000,
            kind = GoalKind.CAP,
            scopeMerchantNorm = targetMerchant,
            period = GoalPeriod.MONTH,
            startsOn = first,
            createdAt = 0,
            updatedAt = 0,
        )
        val p = goalProgress(g, rows, first + 3)
        assertEquals(20_000_000L, p.currentRial)
        assertTrue(p.done)
        assertEquals(20_000_000f / 30_000_000f, p.share, 0.001f)
    }

    @Test
    fun `a cap scoped to a category includes child categories recursively`() {
        val categories = listOf(
            Category(id = "parent", nameFa = "خوراکی", kind = "out", parentId = null),
            Category(id = "child", nameFa = "میوه", kind = "out", parentId = "parent"),
            Category(id = "grandchild", nameFa = "سیب", kind = "out", parentId = "child"),
            Category(id = "unrelated", nameFa = "پوشاک", kind = "out", parentId = null),
        )
        val rows = listOf(
            entry(first, -10_000_000, categoryId = "parent"),
            entry(first + 1, -15_000_000, categoryId = "child"),
            entry(first + 2, -5_000_000, categoryId = "grandchild"),
            entry(first + 3, -100_000_000, categoryId = "unrelated"),
        )
        val g = Goal(
            id = "g_cat",
            nameFa = "خوراکی‌ها",
            targetRial = 50_000_000,
            kind = GoalKind.CAP,
            categoryId = "parent",
            period = GoalPeriod.MONTH,
            startsOn = first,
            createdAt = 0,
            updatedAt = 0,
        )
        val p = goalProgress(g, rows, first + 5, categories)
        assertEquals(30_000_000L, p.currentRial)
        assertTrue(p.done)
    }

    @Test
    fun `pace estimate triggers when cap usage exceeds 80 percent`() {
        // Target is 100M, spent 85M by day 10 -> daily rate 8.5M/day -> remaining 15M / 8.5M ≈ 1.76 days -> paceDay = 10 + 1 = 11
        val day10 = jalaliDay(year, month, 10)
        val rows = listOf(entry(first, -85_000_000, categoryId = "cat_dining"))
        val g = goal(100_000_000, GoalKind.CAP, "cat_dining")
        val p = goalProgress(g, rows, day10)
        assertEquals(85_000_000L, p.currentRial)
        assertEquals(0.85f, p.share, 0.001f)
        assertNotNull("Pace day should be computed at 85% share", p.paceDay)
        assertEquals(11, p.paceDay)

        // When share < 80%, pace estimate is null
        val lowRows = listOf(entry(first, -70_000_000, categoryId = "cat_dining"))
        val lowProgress = goalProgress(g, lowRows, day10)
        assertNull("Pace day should be null under 80% share", lowProgress.paceDay)
    }

    @Test
    fun `cap daysLeft reaches zero on the last day of the jalali month`() {
        val lastDayOfMordad = jalaliDay(year, month, 31)
        val g = goal(50_000_000, GoalKind.CAP, "cat_dining")
        val p = goalProgress(g, emptyList(), lastDayOfMordad)
        assertEquals(0, p.daysLeft)
        assertTrue(p.done)
    }
}
