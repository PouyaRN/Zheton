package com.doxigo.muchtoman

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AssetDetailTest {

    private val today = 20_000L

    private fun series(period: RatePeriod, vararg points: Pair<Long, Double>, complete: Boolean = true) =
        RateSeries("gold18", period, points.map { RatePoint(it.first, it.second) }, complete, 0)

    @Test
    fun shortHistoryOccupiesOnlyItsOwnStretch() {
        // Two days of a month: the line sits at the right edge, not stretched across.
        val s = series(RatePeriod.MONTH, today - 1 to 100.0, today to 110.0, complete = false)
        val g = chartGeometry(s, today, null, width = 300f, top = 0f, bottom = 100f)
        assertEquals(300f, g.xs.last(), 0.01f)
        assertEquals(290f, g.xs.first(), 0.01f)
        assertNull(g.costY)
    }

    @Test
    fun basePointBeforeWindowIsPinnedToLeftEdge() {
        val s = series(RatePeriod.WEEK, today - 9 to 100.0, today to 120.0)
        val g = chartGeometry(s, today, null, width = 70f, top = 0f, bottom = 100f)
        assertEquals(0f, g.xs.first(), 0.01f)
        assertEquals(100f, g.ys.first(), 0.01f) // lowest at the bottom
        assertEquals(0f, g.ys.last(), 0.01f)    // highest at the top
    }

    @Test
    fun averageCostIsAlwaysInsideTheChart() {
        val s = series(RatePeriod.WEEK, today - 3 to 200.0, today to 210.0)
        val g = chartGeometry(s, today, averageCost = 100.0, width = 70f, top = 10f, bottom = 110f)
        assertNotNull(g.costY)
        assertEquals(110f, g.costY!!, 0.01f)
    }

    @Test
    fun emptySeriesDrawsNothing() {
        val g = chartGeometry(series(RatePeriod.YEAR), today, 5.0, 300f, 0f, 100f)
        assertTrue(g.xs.isEmpty())
        assertNull(g.costY)
    }

    @Test
    fun spanSaysWhereHistoryStartsWhenShort() {
        val short = series(RatePeriod.YEAR, today - 10 to 1.0, today to 2.0, complete = false)
        assertTrue(periodSpan(short, today).startsWith("تاریخچه از "))
        val full = series(RatePeriod.MONTH, today - 31 to 1.0, today to 2.0)
        assertEquals("در این ماه", periodSpan(full, today))
    }

    @Test
    fun deltaIsDashWithoutTwoPoints() {
        assertEquals("—", periodDelta(null, null))
        assertTrue(periodDelta(100_000.0, 4.1).contains("+۱۰۰٬۰۰۰"))
        assertTrue(periodDelta(100_000.0, 4.1).contains("(۴٫۱٪)"))
        assertTrue(periodDelta(-5.0, -1.0).contains("−۵"))
    }

    @Test
    fun markUsesTheFigureInTheName() {
        val gold = STATIC_CATALOG.first { it.id == "gold18" }
        assertEquals("۱۸", assetDetailMark(gold))
        assertEquals("هر گرم · بازار آزاد", assetUnitLine(gold))
        assertEquals("طلای تو", holdingLabel(gold))
    }

    @Test
    fun nearestIndexFollowsTheFinger() {
        assertEquals(1, nearestIndex(listOf(0f, 50f, 100f), 60f))
        assertNull(nearestIndex(emptyList(), 10f))
    }
}
