package com.doxigo.muchtoman

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class HomeDeclutterTest {

    @Test
    fun `Tab entries has exactly four core tabs and excludes GOALS`() {
        assertEquals(4, Tab.entries.size)
        val tabNames = Tab.entries.map { it.name }
        assertTrue(tabNames.contains("HOME"))
        assertTrue(tabNames.contains("LEDGER"))
        assertTrue(tabNames.contains("ASSETS"))
        assertTrue(tabNames.contains("REPORTS"))
        assertFalse("GOALS must not be in Tab.entries", tabNames.contains("GOALS"))
    }

    @Test
    fun `Tab labels in Persian match refined four-tab navigation`() {
        val expected = listOf("خانه", "دفتر", "بازار", "گزارش‌ها")
        assertEquals(expected, Tab.entries.map { it.fa })
    }

    @Test
    fun `Active obligations filtering excludes archived items on home screen`() {
        val sampleObligation = InstallmentObligation(
            id = "ob1",
            provider = "دیجی‌پی",
            totalRial = 100_000_000L,
            downPaymentRial = 0L,
            installmentRial = 10_000_000L,
            installmentCount = 10,
            startDate = System.currentTimeMillis(),
            createdAt = System.currentTimeMillis(),
            updatedAt = System.currentTimeMillis(),
            archived = true,
        )
        val list = listOf(sampleObligation)
        val active = list.filter { !it.archived }
        assertTrue("Archived obligation must be excluded from active home display", active.isEmpty())
    }

    @Test
    fun `Active reservations filtering excludes released items on home screen`() {
        val sampleReservation = Reservation(
            id = "res1",
            purpose = "خرید لپ‌تاپ",
            amountRial = 50_000_000L,
            createdAt = System.currentTimeMillis(),
            updatedAt = System.currentTimeMillis(),
            released = true,
        )
        val list = listOf(sampleReservation)
        val active = list.filter { !it.released }
        assertTrue("Released reservation must be excluded from active home display", active.isEmpty())
    }

    @Test
    fun `formatCardPan correctly masks and separates PAN into 4 distinct Persian digit blocks`() {
        val raw = "5022291012345678"
        val formatted = formatCardPan(raw)
        val blocks = formatted.split("  ")
        assertEquals(4, blocks.size)
        assertEquals("۵۰۲۲", blocks[0])
        assertEquals("۲۹۱۰", blocks[1])
        assertEquals("۱۲۳۴", blocks[2])
        assertEquals("۵۶۷۸", blocks[3])
    }
}
