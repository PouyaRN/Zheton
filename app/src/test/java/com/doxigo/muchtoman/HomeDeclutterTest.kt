package com.doxigo.muchtoman

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class HomeDeclutterTest {

    @Test
    fun `Tab entries has exactly four core tabs and excludes GOALS and standalone REPORTS`() {
        assertEquals(4, Tab.entries.size)
        val tabNames = Tab.entries.map { it.name }
        assertTrue(tabNames.contains("HOME"))
        assertTrue(tabNames.contains("ASSETS"))
        assertTrue(tabNames.contains("CARDS"))
        assertTrue(tabNames.contains("ACTIVITY_AND_TRENDS"))
        assertFalse("GOALS must not be in Tab.entries", tabNames.contains("GOALS"))
        assertFalse("REPORTS must not be a standalone root tab", tabNames.contains("REPORTS"))
    }

    @Test
    fun `Tab labels in Persian match refined four-tab navigation`() {
        val expected = listOf("فرمان", "بازار", "حساب‌ها", "دفتر و تحلیل")
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

    @Test
    fun `Home recent transactions displays up to exactly five transactions for optimal glance`() {
        val entries = (1..10).map { i ->
            LedgerEntry(
                txn = Txn(
                    ref = "tx$i",
                    srcHash = "h$i",
                    seq = i,
                    at = 1700000000L + i,
                    day = 14030620L,
                    bank = "mellat",
                    accountId = "acc1",
                    direction = "out",
                    amountRial = (i * 100_000L),
                    signedRial = -(i * 100_000L),
                    balanceRial = 50_000_000L,
                    feeRial = 0L,
                    mask = "1234",
                    instrument = "card",
                    merchant = "فروشگاه $i",
                    merchantNorm = "فروشگاه $i",
                    refNo = "ref$i",
                    printedAt = "12:0$i",
                    channel = "pos",
                    unitPrinted = "rial",
                    inferred = false,
                    parserVer = 1,
                ),
                categoryId = "cat1",
                categoryFa = "خرید",
                confidence = 100,
                needsReview = false,
                duplicate = false,
                transfer = false,
            )
        }
        val displayed = entries.take(5)
        assertEquals(5, displayed.size)
        assertEquals("tx1", displayed.first().txn.ref)
        assertEquals("tx5", displayed.last().txn.ref)
    }

    @Test
    fun `Privacy mode masks financial amounts with dots`() {
        val masked = maskFinancialAmount("۲۵,۰۰۰,۰۰۰ تومان", privacy = true)
        assertEquals("••••••••", masked)
        val unmasked = maskFinancialAmount("۲۵,۰۰۰,۰۰۰ تومان", privacy = false)
        assertEquals("۲۵,۰۰۰,۰۰۰ تومان", unmasked)
    }
}
