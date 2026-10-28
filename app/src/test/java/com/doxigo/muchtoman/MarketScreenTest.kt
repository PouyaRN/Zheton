package com.doxigo.muchtoman

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class MarketScreenTest {
    private val gold = resolveType("gold18", emptyList())
    private val coin = resolveType("coin_emami", emptyList())
    private val usd = resolveType("usd", emptyList())
    private val cash = resolveType(TOMAN_ID, emptyList())

    @Test
    fun `allocation groups by kind, largest first, unpriced left out`() {
        val slices = allocationByKind(listOf(gold to 30.0, coin to 10.0, usd to null, gold to 20.0))
        assertEquals(listOf(Kind.GOLD, Kind.COIN), slices.map { it.kind })
        assertEquals(50.0 / 60.0, slices[0].share, 1e-9)
        assertEquals("طلا", slices[0].label)
    }

    @Test
    fun `allocation names a lone dollar as the canvas does`() {
        assertEquals("دلار", allocationByKind(listOf(usd to 5.0)).single().label)
    }

    @Test
    fun `day change is hidden when any priced holding has no stored close`() {
        val up = DayChange(10.0, 10.0, 0L, 100.0)
        assertNull(portfolioDayChangePercent(listOf(Triple(gold, 110.0, up), Triple(coin, 50.0, null))))
    }

    @Test
    fun `day change weighs each holding and treats cash as flat`() {
        val up = DayChange(10.0, 10.0, 0L, 100.0)
        val pct = portfolioDayChangePercent(listOf(Triple(gold, 110.0, up), Triple(cash, 100.0, null)))!!
        assertEquals(10.0 / 200.0 * 100.0, pct, 1e-9)
    }

    @Test
    fun `day change is hidden when only cash is priced`() {
        assertNull(portfolioDayChangePercent(listOf(Triple(cash, 100.0, null), Triple(gold, null, null))))
    }

    @Test
    fun `ring figure truncates to one decimal and names its unit`() {
        assertEquals("۵۱٫۹" to "میلیون تومان", ringFigure(51_990_000.0))
        assertEquals("۱٫۲" to "میلیارد تومان", ringFigure(1_299_000_000.0))
    }

    @Test
    fun `signed percent keeps a sign only when it is not zero`() {
        assertEquals("۰٫۰٪", faSignedPercent(0.01))
        assertEquals(ltrFigure("+۴٫۱٪"), faSignedPercent(4.1))
        assertEquals(ltrFigure("−۰٫۸٪"), faSignedPercent(-0.8))
    }
}
