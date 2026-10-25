package com.doxigo.muchtoman.features.export

import com.doxigo.muchtoman.AssetType
import com.doxigo.muchtoman.Holding
import com.doxigo.muchtoman.Kind
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The assets snapshot is small but it has three ways to be quietly wrong: valuing an excluded
 * holding, valuing an unpriced one at zero, and losing an asset the catalogue no longer knows.
 * Each has a test.
 */
class ReportAssetsTest {

    private val gold = AssetType("gold", "طلا", Kind.GOLD, "گرم", dec = 2)
    private val toman = AssetType("toman", "پول نقد", Kind.CASH, "تومان", dec = 0)
    private val tether = AssetType("usdt", "تتر", Kind.CRYPTO, "USDT", dec = 2)
    private val silver = AssetType("silver", "نقره", Kind.SILVER, "گرم", dec = 2)

    private val rates = mapOf(
        "gold" to 5_000_000.0,
        "toman" to 1.0,
        "silver" to 30_000.0,
        // no "usdt" price: the map deliberately leaves it unpriced
    )

    private val types = listOf(gold, toman, tether, silver)

    @Test
    fun `holdings are priced from the rate map and sorted by value`() {
        val lines = assetLines(
            holdings = listOf(
                Holding(typeId = "usdt", amount = 500.0),
                Holding(typeId = "toman", amount = 2_000_000.0),
                Holding(typeId = "gold", amount = 10.0),
            ),
            types = types,
            ratesToman = rates,
        )

        assertEquals(3, lines.size)
        assertEquals("طلا", lines[0].nameFa)
        assertEquals(50_000_000.0, lines[0].toman!!, 0.001)
        assertEquals("پول نقد", lines[1].nameFa)
        // 2,000,000 Toman at a rate of 1.0 stays 2,000,000 — no double conversion.
        assertEquals(2_000_000.0, lines[1].toman!!, 0.001)
        // Unpriced is null, never zero: zero is a figure somebody would believe.
        assertNull(lines[2].toman)
        assertTrue(lines[2].inTotal)
    }

    @Test
    fun `excluded holdings stay visible but out of the total`() {
        val lines = assetLines(
            holdings = listOf(
                Holding(typeId = "gold", amount = 1.0),
                Holding(typeId = "silver", amount = 100.0, excluded = true),
            ),
            types = types,
            ratesToman = rates,
        )

        val silverLine = lines.first { it.nameFa == "نقره" }
        assertFalse(silverLine.inTotal)
        assertTrue("priced even though excluded", silverLine.toman!! > 0.0)
    }

    @Test
    fun `archived holdings are not owned and do not appear`() {
        val lines = assetLines(
            holdings = listOf(
                Holding(typeId = "gold", amount = 1.0),
                Holding(typeId = "silver", amount = 100.0, archived = true),
            ),
            types = types,
            ratesToman = rates,
        )
        assertEquals(1, lines.size)
        assertEquals("طلا", lines.single().nameFa)
    }

    @Test
    fun `an asset the catalogue no longer knows still renders`() {
        val lines = assetLines(
            holdings = listOf(Holding(typeId = "doge", amount = 5.0)),
            types = types,
            ratesToman = mapOf("doge" to 1_000.0),
        )
        assertEquals(1, lines.size)
        assertEquals("DOGE", lines.single().nameFa)
        assertEquals(5_000.0, lines.single().toman!!, 0.001)
        assertTrue(lines.single().amountFa.contains("DOGE"))
    }

    @Test
    fun `zero and negative amounts are treated as unpriced rather than negative wealth`() {
        val lines = assetLines(
            holdings = listOf(Holding(typeId = "gold", amount = 0.0)),
            types = types,
            ratesToman = rates,
        )
        assertNull(lines.single().toman)
    }
}
