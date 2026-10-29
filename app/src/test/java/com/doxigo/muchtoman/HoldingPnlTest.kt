package com.doxigo.muchtoman

import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class HoldingPnlTest {

    @get:Rule
    val tmp = TemporaryFolder()

    private fun buy(amount: Double, price: Double, at: Long) = HoldingLot("b$at", LotSide.BUY, amount, price, at)
    private fun sell(amount: Double, price: Double, at: Long) = HoldingLot("s$at", LotSide.SELL, amount, price, at)

    // ── Cost basis ──────────────────────────────────────────────────────────

    @Test
    fun `average cost weighs each buy by its amount`() {
        val s = summarizeLots(listOf(buy(2.0, 100.0, 1), buy(1.0, 400.0, 2)))
        assertEquals(3.0, s.trackedAmount, 1e-9)
        assertEquals(600.0, s.trackedCostToman, 1e-9)
        assertEquals(200.0, s.averageCostToman!!, 1e-9)
    }

    @Test
    fun `a sell keeps the average and books realised profit`() {
        // Recorded out of order on purpose: lots are folded by date.
        val s = summarizeLots(listOf(sell(1.0, 300.0, 3), buy(2.0, 100.0, 1), buy(2.0, 300.0, 2)))
        assertEquals(3.0, s.trackedAmount, 1e-9)
        assertEquals(200.0, s.averageCostToman!!, 1e-9)
        assertEquals(100.0, s.realizedPnlToman, 1e-9)
    }

    @Test
    fun `selling everything leaves no average`() {
        val s = summarizeLots(listOf(buy(2.0, 100.0, 1), sell(2.0, 150.0, 2)))
        assertNull(s.averageCostToman)
        assertEquals(100.0, s.realizedPnlToman, 1e-9)
    }

    @Test
    fun `a sell beyond tracked units only removes what was tracked`() {
        val s = summarizeLots(listOf(buy(1.0, 100.0, 1), sell(3.0, 200.0, 2)))
        assertEquals(0.0, s.trackedAmount, 1e-9)
        assertEquals(100.0, s.realizedPnlToman, 1e-9)
    }

    @Test
    fun `invalid lots are ignored`() {
        assertFalse(isValidLot(0.0, 100.0))
        assertFalse(isValidLot(1.0, Double.NaN))
        val s = summarizeLots(listOf(buy(1.0, -5.0, 1), buy(1.0, 10.0, 2)))
        assertEquals(10.0, s.averageCostToman!!, 1e-9)
    }

    // ── Position ────────────────────────────────────────────────────────────

    @Test
    fun `fully covered holding has cost basis and profit`() {
        val p = positionOf(Holding("gold18", 3.0, id = "g"), listOf(buy(2.0, 100.0, 1), buy(1.0, 400.0, 2)), 250.0)
        assertEquals("g", p.key)
        assertEquals(750.0, p.valueToman!!, 1e-9)
        assertEquals(600.0, p.costBasisToman!!, 1e-9)
        assertEquals(150.0, p.pnlToman!!, 1e-9)
        assertEquals(25.0, p.pnlPercent!!, 1e-9)
        assertEquals(0.0, p.uncoveredAmount, 0.0)
        assertTrue(p.costKnown)
    }

    @Test
    fun `holding from before lots has unknown cost, not zero`() {
        val p = positionOf(Holding("usd", 10.0), emptyList(), 70_000.0)
        assertEquals(700_000.0, p.valueToman!!, 1e-9)
        assertNull(p.averageCostToman)
        assertNull(p.costBasisToman)
        assertNull(p.pnlToman)
        assertNull(p.pnlPercent)
        assertEquals(10.0, p.uncoveredAmount, 1e-9)
        assertFalse(p.costKnown)
    }

    @Test
    fun `partly covered holding knows its average but not its profit`() {
        // 10 owned before lots existed, then a recorded buy of 2 (amount went to 12).
        val p = positionOf(Holding("usd", 12.0), listOf(buy(2.0, 60_000.0, 1)), 70_000.0)
        assertEquals(60_000.0, p.averageCostToman!!, 1e-9)
        assertEquals(10.0, p.uncoveredAmount, 1e-9)
        assertNull(p.costBasisToman)
        assertNull(p.pnlPercent)
    }

    @Test
    fun `amount typed below the lots is priced at the average`() {
        val p = positionOf(Holding("usd", 1.0), listOf(buy(2.0, 50.0, 1)), 100.0)
        assertEquals(50.0, p.costBasisToman!!, 1e-9)
        assertEquals(100.0, p.pnlPercent!!, 1e-9)
    }

    @Test
    fun `no rate means no value and no profit`() {
        val p = positionOf(Holding("btc", 1.0), listOf(buy(1.0, 10.0, 1)), null)
        assertNull(p.valueToman)
        assertNull(p.pnlToman)
        assertEquals(10.0, p.costBasisToman!!, 1e-9)
    }

    @Test
    fun `lots survive a json round trip`() {
        val lots = mapOf("g" to listOf(buy(1.5, 2_000_000.0, 42)))
        val json = Json.encodeToString(lots)
        assertEquals(lots, Json.decodeFromString<Map<String, List<HoldingLot>>>(json))
    }

    // ── Daily change and series ─────────────────────────────────────────────

    @Test
    fun `day change compares with the last earlier close`() {
        val c = dayChange(mapOf(98L to 90.0, 99L to 100.0, 100L to 104.0), today = 100, currentPriceToman = 110.0)!!
        assertEquals(99L, c.baseDay)
        assertEquals(10.0, c.percent, 1e-9)
        // Without a live price, today's stored close is the current one.
        assertEquals(4.0, dayChange(mapOf(99L to 100.0, 100L to 104.0), 100)!!.percent, 1e-9)
    }

    @Test
    fun `day change is unknown without a recent earlier close`() {
        assertNull(dayChange(mapOf(100L to 104.0), 100, 105.0))
        assertNull(dayChange(mapOf(90L to 100.0), 100, 105.0))
        assertNull(dayChange(emptyMap(), 100, null))
    }

    @Test
    fun `series starts at the period boundary when history covers it`() {
        val closes = (80L..100L).associateWith { 100.0 + it }
        val s = rateSeries("usd", closes, RatePeriod.WEEK, today = 100, livePriceToman = 250.0)
        assertTrue(s.complete)
        assertEquals(93L, s.first!!.day)
        assertEquals(250.0, s.last!!.priceToman, 0.0)
        assertEquals(8, s.points.size)
        assertEquals((250.0 - 193.0) / 193.0 * 100.0, s.changePercent!!, 1e-9)
    }

    @Test
    fun `short history returns what exists and says so`() {
        val closes = mapOf(95L to 10.0, 97L to 11.0, 100L to 12.0)
        val s = rateSeries("usd", closes, RatePeriod.YEAR, today = 100)
        assertFalse(s.complete)
        assertEquals(5, s.availableDays)
        assertEquals(listOf(95L, 97L, 100L), s.points.map { it.day })
        assertEquals(20.0, s.changePercent!!, 1e-9)
    }

    @Test
    fun `empty history is an empty, incomplete series with no change`() {
        val s = rateSeries("usd", emptyMap(), RatePeriod.MONTH, today = 100)
        assertTrue(s.isEmpty)
        assertFalse(s.complete)
        assertNull(s.changePercent)
        val live = rateSeries("usd", emptyMap(), RatePeriod.MONTH, today = 100, livePriceToman = 5.0)
        assertEquals(1, live.points.size)
        assertNull(live.changePercent)
    }

    @Test
    fun `series ignores future and invalid closes`() {
        val s = rateSeries("usd", mapOf(99L to 0.0, 100L to 5.0, 101L to 9.0), RatePeriod.WEEK, 100)
        assertEquals(listOf(100L), s.points.map { it.day })
    }

    @Test
    fun `record close prunes beyond five years`() {
        val old = mapOf(0L to 1.0, 10L to 2.0)
        val next = recordClose(old, DAILY_RATE_KEEP_DAYS + 5L, 3.0)
        assertFalse(0L in next)
        assertTrue(10L in next)
        assertEquals(old, recordClose(old, 20, Double.NaN))
    }

    @Test
    fun `observations collapse to the last price of each tehran day`() {
        val day = 20_000L
        val start = tehranDayStart(day)
        val closes = closesFromObservations(
            listOf(
                RateObservation(start + 3_600_000L, 10.0),
                RateObservation(start + 7_200_000L, 12.0),
                RateObservation(start + DAY_MS + 1, 13.0),
            ),
        )
        assertEquals(mapOf(day to 12.0, day + 1 to 13.0), closes)
    }

    // ── File store ──────────────────────────────────────────────────────────

    @Test
    fun `daily rate files record only allowed assets and reload`() {
        val files = DailyRateFiles(tmp.newFolder("rates"))
        val changed = files.record(mapOf("usd" to 70_000.0, "tse_x" to 5.0, "eur" to 0.0), 100, setOf("usd", "eur"))
        assertEquals(setOf("usd"), changed.keys)
        files.record(mapOf("usd" to 71_000.0), 101, setOf("usd"))
        // Same day again: the last fetch of the day is the close.
        files.record(mapOf("usd" to 72_000.0), 101, setOf("usd"))
        // Unchanged price writes nothing.
        assertTrue(files.record(mapOf("usd" to 72_000.0), 101, setOf("usd")).isEmpty())

        val reloaded = DailyRateFiles(tmp.root.resolve("rates")).loadAll()
        assertEquals(mapOf("usd" to mapOf(100L to 70_000.0, 101L to 72_000.0)), reloaded)
    }

    @Test
    fun `seeding imports old observations once and never overwrites`() {
        val dir = tmp.newFolder("seed")
        val files = DailyRateFiles(dir)
        val day = 20_000L
        files.record(mapOf("usd" to 99.0), day, setOf("usd"))
        val obs = mapOf(
            "usd" to listOf(RateObservation(tehranDayStart(day - 1) + 1, 90.0), RateObservation(tehranDayStart(day) + 1, 50.0)),
            TOMAN_ID to listOf(RateObservation(tehranDayStart(day) + 1, 1.0)),
        )
        files.seedOnce(obs)
        assertEquals(mapOf(day - 1 to 90.0, day to 99.0), files.load("usd"))
        assertTrue(files.loadAll().keys == setOf("usd"))

        // Second call is a no-op even with new data.
        files.seedOnce(mapOf("eur" to listOf(RateObservation(tehranDayStart(day) + 1, 80.0))))
        assertTrue(files.load("eur").isEmpty())
    }

    @Test
    fun `asset ids with odd characters round trip through file names`() {
        val files = DailyRateFiles(tmp.newFolder("odd"))
        files.record(mapOf("tse_فولاد/1" to 5.0), 1, setOf("tse_فولاد/1"))
        assertEquals(setOf("tse_فولاد/1"), files.loadAll().keys)
    }

    @Test
    fun `daily ids cover the catalogue and held coins, never toman or bank`() {
        val ids = dailyRateIds(listOf(Holding("btc", 1.0), Holding(BANK_ID, 5.0), Holding(TOMAN_ID, 3.0)))
        assertTrue("btc" in ids)
        assertFalse(TOMAN_ID in ids)
        assertFalse(BANK_ID in ids)
        assertTrue(STATIC_CATALOG.filter { it.id != TOMAN_ID }.all { it.id in ids })
    }

    // ── UiState read side ──────────────────────────────────────────────────

    @Test
    fun `ui state exposes positions and series from stored data`() {
        val now = tehranDayStart(20_000L) + 3_600_000L
        val state = UiState(
            holdings = listOf(Holding("usd", 2.0, id = "u"), Holding("eur", 1.0, id = "e")),
            rates = Rates(updatedAt = now, toman = mapOf("usd" to 110.0, "eur" to 50.0)),
            holdingLots = mapOf("u" to listOf(buy(2.0, 100.0, 1))),
            dailyRates = mapOf("usd" to mapOf(19_999L to 100.0)),
        )
        val u = state.positionOf("u", now)!!
        assertEquals(10.0, u.pnlPercent!!, 1e-9)
        assertEquals(10.0, u.dayChangePercent!!, 1e-9)
        val e = state.positionOf("e", now)!!
        assertNull(e.costBasisToman)
        assertNull(e.dayChange)
        assertNull(state.positionOf("missing", now))

        val series = state.rateSeriesOf("usd", RatePeriod.MONTH, now)
        assertFalse(series.complete)
        assertEquals(listOf(100.0, 110.0), series.points.map { it.priceToman })
        assertEquals(listOf(100.0, 110.0), state.sparklineOf("usd", now))
        assertNotNull(state.positions(now).firstOrNull { it.key == "u" })
    }
}
