package com.doxigo.muchtoman

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TimelineUiFeaturesTest {

    private fun dummyEntry(
        ref: String = "sms:mellat:12345",
        direction: String = "out",
        amountRial: Long = 1500000L,
        merchant: String = "اسنپ",
        categoryFa: String = "حمل و نقل",
        bank: String = "MELLAT",
        mask: String = "۶۱۰۴***۱۲۳۴",
        refNo: String = "987654321",
        printedAt: String = "۱۴۰۳/۰۴/۱۵ ۱۲:۳۰",
    ) = LedgerEntry(
        txn = Txn(
            ref = ref,
            srcHash = "hash123",
            seq = 0,
            at = 1720000000000L,
            day = 500L,
            bank = bank,
            accountId = "acc1",
            direction = direction,
            amountRial = amountRial,
            signedRial = if (direction == "in") amountRial else -amountRial,
            balanceRial = 5000000L,
            feeRial = null,
            mask = mask,
            instrument = "card",
            merchant = merchant,
            merchantNorm = merchant,
            refNo = refNo,
            printedAt = printedAt,
            channel = "pos",
            unitPrinted = "none",
            inferred = false,
            parserVer = PARSER_VERSION,
        ),
        categoryId = "cat_transport",
        categoryFa = categoryFa,
        confidence = 100,
        needsReview = false,
        duplicate = false,
        transfer = false,
    )

    // ── Ticket 11: formatReceipt Tests ──────────────────────────────

    @Test
    fun `formatReceipt formats full Persian receipt accurately`() {
        val entry = dummyEntry()
        val receipt = formatReceipt(entry, "کرایه تاکسی")

        assertTrue(receipt.contains("رسید تراکنش (برداشت)"))
        assertTrue(receipt.contains("مبلغ: ۱۵۰ هزار تومان"))
        assertTrue(receipt.contains("طرف حساب: اسنپ"))
        assertTrue(receipt.contains("دسته‌بندی: حمل و نقل"))
        assertTrue(receipt.contains("بانک: بانک ملت"))
        assertTrue(receipt.contains("کارت/حساب: ۶۱۰۴***۱۲۳۴"))
        assertTrue(receipt.contains("شماره پیگیری: 987654321"))
        assertTrue(receipt.contains("تاریخ: ۱۴۰۳/۰۴/۱۵ ۱۲:۳۰"))
        assertTrue(receipt.contains("یادداشت: کرایه تاکسی"))
    }

    @Test
    fun `formatReceipt handles incoming transfer and empty notes`() {
        val entry = dummyEntry(
            direction = "in",
            amountRial = 100_000_000L,
            merchant = "حقوق شرکت",
            categoryFa = "حقوق",
            refNo = "",
        )
        val receipt = formatReceipt(entry, "")

        assertTrue(receipt.contains("رسید تراکنش (واریز)"))
        assertTrue(receipt.contains("مبلغ: ۱۰ میلیون تومان"))
        assertTrue(receipt.contains("طرف حساب: حقوق شرکت"))
        assertTrue(receipt.contains("دسته‌بندی: حقوق"))
        assertFalse(receipt.contains("شماره پیگیری"))
        assertFalse(receipt.contains("یادداشت"))
    }

    // ── Ticket 07: matchesTimelineQuery Tests ───────────────────────

    @Test
    fun `matchesTimelineQuery matches various transaction fields`() {
        val entry = dummyEntry(
            ref = "sms:melli:9988",
            merchant = "سوپرمارکت افق",
            categoryFa = "خواربار",
            amountRial = 2_500_000L, // 250,000 Toman
        )
        val note = "خرید هفتگی لبنیات"

        // Blank query matches everything
        assertTrue(matchesTimelineQuery(entry, note, ""))
        assertTrue(matchesTimelineQuery(entry, note, "   "))

        // Merchant match
        assertTrue(matchesTimelineQuery(entry, note, "افق"))
        // Category match
        assertTrue(matchesTimelineQuery(entry, note, "خواربار"))
        // Note match
        assertTrue(matchesTimelineQuery(entry, note, "لبنیات"))
        // Reference ID match
        assertTrue(matchesTimelineQuery(entry, note, "9988"))
        // Amount match in Persian or Latin
        assertTrue(matchesTimelineQuery(entry, note, "250000"))
        assertTrue(matchesTimelineQuery(entry, note, "۲۵۰"))

        // Non-matching query
        assertFalse(matchesTimelineQuery(entry, note, "پیتزا"))
    }

    // ── Ticket 10: matchesSearch (Asset Picker) Tests ───────────────

    @Test
    fun `matchesSearch finds asset by Persian, Latin, or ticker symbol`() {
        val bitcoin = AssetType(
            id = "btc",
            fa = "بیت‌کوین",
            en = "Bitcoin",
            kind = Kind.CRYPTO,
            unitFa = "واحد",
        )

        // Persian name match
        assertTrue(matchesSearch(bitcoin, "بیت"))
        // Latin name match
        assertTrue(matchesSearch(bitcoin, "bitcoin"))
        assertTrue(matchesSearch(bitcoin, "bit"))
        // Ticker / ID match
        assertTrue(matchesSearch(bitcoin, "btc"))
        assertTrue(matchesSearch(bitcoin, "BTC"))

        // Non-match
        assertFalse(matchesSearch(bitcoin, "eth"))
    }
}
