package com.doxigo.muchtoman

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class FilterTest {

    private fun row(
        merchant: String = "اسنپ",
        merchantNorm: String = "اسنپ",
        categoryId: String = "cat_food",
        categoryFa: String = "غذا",
        bank: String = "TEJARAT",
        direction: String = "out",
        amountRial: Long = 10_000_000,
        day: Long = 500,
    ) = LedgerEntry(
        txn = Txn(
            ref = "s:x:0", srcHash = "x", seq = 0, at = day, day = day,
            bank = bank, accountId = bank,
            direction = direction, amountRial = amountRial, signedRial = if (direction == "in") amountRial else -amountRial,
            balanceRial = null, feeRial = null, mask = "", instrument = "unknown",
            merchant = merchant, merchantNorm = merchantNorm, refNo = "", printedAt = "",
            channel = "unknown", unitPrinted = "none", inferred = false,
            parserVer = PARSER_VERSION,
        ),
        categoryId = categoryId,
        categoryFa = categoryFa,
        confidence = 100,
        needsReview = false,
        duplicate = false,
        transfer = false,
    )

    @Test
    fun `an empty filter keeps everything`() {
        assertTrue(matchesFilter(row(), emptyList()))
    }

    @Test
    fun `merchant contains narrows to the rows that have it`() {
        val tokens = listOf(FilterToken(FilterField.MERCHANT, FilterOp.CONTAINS, "اسنپ"))
        assertTrue(matchesFilter(row(merchant = "اسنپ اکسپرس"), tokens))
        assertTrue(matchesFilter(row(merchant = "پرداخت اسنپ"), tokens))
        assertFalse(matchesFilter(row(merchant = "اسنفود"), tokens))
    }

    @Test
    fun `raw regex matches merchant text only`() {
        val re = listOf(FilterToken(FilterField.MERCHANT, FilterOp.REGEX, "اسن[پب]"))
        assertTrue(matchesFilter(row(merchant = "اسنپ"), re))
        assertTrue(matchesFilter(row(merchant = "اسنب"), re))
        assertFalse(matchesFilter(row(merchant = "اسنا"), re))
    }

    @Test
    fun `a broken regex matches nothing instead of widening`() {
        val re = listOf(FilterToken(FilterField.MERCHANT, FilterOp.REGEX, "اس[پ"))
        assertFalse(matchesFilter(row(merchant = "اسنپ"), re))
    }

    @Test
    fun `merchant not-contains excludes a substring`() {
        val tokens = listOf(FilterToken(FilterField.MERCHANT, FilterOp.NOT_CONTAINS, "اسنپ"))
        assertTrue(matchesFilter(row(merchant = "تپسی"), tokens))
        assertFalse(matchesFilter(row(merchant = "اسنپ"), tokens))
    }

    @Test
    fun `category equals only matches that category`() {
        val tokens = listOf(FilterToken(FilterField.CATEGORY, FilterOp.EQUALS, "cat_transport"))
        assertTrue(matchesFilter(row(categoryId = "cat_transport"), tokens))
        assertFalse(matchesFilter(row(categoryId = "cat_food"), tokens))
    }

    @Test
    fun `bank equals matches by internal bank id`() {
        val tokens = listOf(FilterToken(FilterField.BANK, FilterOp.EQUALS, "MELLI"))
        assertTrue(matchesFilter(row(bank = "MELLI"), tokens))
        assertFalse(matchesFilter(row(bank = "TEJARAT"), tokens))
    }

    @Test
    fun `direction only accepts in or out`() {
        val inTok = listOf(FilterToken(FilterField.DIRECTION, FilterOp.EQUALS, "in"))
        val outTok = listOf(FilterToken(FilterField.DIRECTION, FilterOp.EQUALS, "out"))
        assertTrue(matchesFilter(row(direction = "in"), inTok))
        assertFalse(matchesFilter(row(direction = "out"), inTok))
        assertTrue(matchesFilter(row(direction = "out"), outTok))
    }

    @Test
    fun `amount range uses rial millis quota`() {
        val gte = listOf(FilterToken(FilterField.AMOUNT, FilterOp.GTE, "10,000,000"))
        assertTrue(matchesFilter(row(amountRial = 12_000_000), gte))
        assertTrue(matchesFilter(row(amountRial = 10_000_000), gte))
        assertFalse(matchesFilter(row(amountRial = 9_000_000), gte))
    }

    @Test
    fun `all tokens are ANDed`() {
        val tokens = listOf(
            FilterToken(FilterField.MERCHANT, FilterOp.CONTAINS, "اسنپ"),
            FilterToken(FilterField.BANK, FilterOp.EQUALS, "MELLI"),
            FilterToken(FilterField.DIRECTION, FilterOp.EQUALS, "out"),
        )
        assertTrue(matchesFilter(
            row(merchant = "اسنپ", bank = "MELLI", direction = "out"), tokens,
        ))
        assertFalse(matchesFilter(
            row(merchant = "اسنپ", bank = "TEJARAT", direction = "out"), tokens,
        ))
        assertFalse(matchesFilter(
            row(merchant = "تپسی", bank = "MELLI", direction = "out"), tokens,
        ))
    }
}