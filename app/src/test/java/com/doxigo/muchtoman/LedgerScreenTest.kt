package com.doxigo.muchtoman

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** The ledger screen's figures: the week card, the quick filters and the caption. */
class LedgerScreenTest {

    private var n = 0

    private fun entry(
        at: Long,
        signed: Long,
        categoryId: String = "cat_dining",
        transfer: Boolean = false,
        hidden: Boolean = false,
        merchant: String = "",
    ): LedgerEntry {
        n++
        val ref = "s:%04d:0".format(n)
        return LedgerEntry(
            txn = Txn(
                ref = ref, srcHash = ref, seq = 0, at = at, day = tehranDay(at),
                bank = "SAMAN", accountId = "SAMAN",
                direction = if (signed > 0) "in" else "out",
                amountRial = kotlin.math.abs(signed), signedRial = signed,
                balanceRial = null, feeRial = null, mask = "", instrument = "unknown",
                merchant = merchant, merchantNorm = "", refNo = "", printedAt = "",
                channel = "unknown", unitPrinted = "none", inferred = false,
                parserVer = PARSER_VERSION,
            ),
            categoryId = categoryId, categoryFa = "کافه", confidence = 95,
            needsReview = false, duplicate = false, transfer = transfer, hidden = hidden,
        )
    }

    private val aban1 = jalaliDay(1405, 8, 1) // Friday 23 Oct 2026
    private val tuesday = aban1 + 4
    private fun at(day: Long, hour: Int) = tehranDayStart(day) + hour * 3_600_000L
    private val now = at(tuesday, 12)

    @Test
    fun `weekdays run Saturday to Friday`() {
        assertEquals(6, persianWeekday(aban1))
        assertEquals(0, persianWeekday(aban1 + 1))
        assertEquals(3, persianWeekday(tuesday))
    }

    @Test
    fun `the week adds outflows only, by day, Saturday first`() {
        val week = ledgerWeek(
            listOf(
                entry(at(tuesday - 3, 9), -4_200_000),
                entry(at(tuesday - 2, 9), -1_800_000),
                entry(at(tuesday, 10), -2_770_000),
                entry(at(tuesday, 7), 12_000_000),               // a deposit is not spending
                entry(at(tuesday, 8), -5_000_000, transfer = true), // nor is her own money moving
                entry(at(tuesday, 9), -9_000_000, hidden = true),
            ),
            now,
        )
        assertEquals(listOf(4_200_000L, 1_800_000L, 0L, 2_770_000L, 0L, 0L, 0L), week.daysRial)
        assertEquals(3, week.todayIndex)
        assertEquals(8_770_000L, week.totalRial)
        assertFalse(week.hasBaseline)
        assertNull(week.change)
    }

    @Test
    fun `last week is counted only up to this same moment`() {
        val week = ledgerWeek(
            listOf(
                entry(at(tuesday - 7, 10), -1_000_000),  // last Tuesday morning: counts
                entry(at(tuesday - 7, 18), -50_000_000), // last Tuesday evening: not yet
                entry(at(tuesday, 9), -800_000),
            ),
            now,
        )
        assertEquals(1_000_000L, week.lastWeekSoFarRial)
        assertTrue(week.hasBaseline)
        assertEquals(-0.2, week.change!!, 1e-9)
    }

    @Test
    fun `quick filters pick uncategorised, deposits and installments`() {
        val unc = entry(now, -10, categoryId = CAT_UNCATEGORISED)
        val dep = entry(now, 10)
        val inst = entry(now, -10, merchant = "پرداخت قسط وام")
        val linked = entry(now, -10)
        val plain = entry(now, -10)
        val refs = setOf(linked.txn.ref)
        val all = listOf(unc, dep, inst, linked, plain)
        fun pick(f: LedgerQuickFilter) = all.filter { matchesQuickFilter(it, f, refs) }
        assertEquals(all, pick(LedgerQuickFilter.ALL))
        assertEquals(listOf(unc), pick(LedgerQuickFilter.UNCATEGORISED))
        assertEquals(listOf(dep), pick(LedgerQuickFilter.DEPOSITS))
        assertEquals(listOf(inst, linked), pick(LedgerQuickFilter.INSTALLMENTS))
    }

    @Test
    fun `caption names the month and counts its transactions`() {
        val entries = listOf(entry(at(aban1, 9), -10), entry(at(tuesday, 9), -10), entry(at(aban1 - 1, 9), -10))
        assertEquals("آبان ۱۴۰۵ · ۲ تراکنش", ledgerCaption(entries, tuesday))
    }

    @Test
    fun `clock and signed figures read in Persian`() {
        assertEquals("۰۷:۰۳", faClockOf(tehranDayStart(tuesday) + (7 * 60 + 3) * 60_000L))
        assertEquals(ltrFigure("−۱۸۵٬۰۰۰"), faSignedToman(-1_850_000))
        assertEquals(ltrFigure("+۱٬۲۰۰٬۰۰۰"), faSignedToman(12_000_000))
    }
}
