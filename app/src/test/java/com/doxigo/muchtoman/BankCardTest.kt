package com.doxigo.muchtoman

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class BankCardTest {

    @Test
    fun `BankCardThemes resolves correct theme per Iranian bank name`() {
        assertEquals(BankCardThemes.MELLI, BankCardThemes.resolve("بانک ملی ایران"))
        assertEquals(BankCardThemes.MELLAT, BankCardThemes.resolve("بانک ملت"))
        assertEquals(BankCardThemes.SAMAN, BankCardThemes.resolve("بانک سامان"))
        assertEquals(BankCardThemes.PASARGAD, BankCardThemes.resolve("بانک پاسارگاد"))
        assertEquals(BankCardThemes.BLU, BankCardThemes.resolve("بلو بانک"))
        assertEquals(BankCardThemes.TEJARAT, BankCardThemes.resolve("بانک تجارت"))
        assertEquals(BankCardThemes.RESALAT, BankCardThemes.resolve("بانک رسالت"))
        assertEquals(BankCardThemes.DEFAULT, BankCardThemes.resolve("بانک نامشخص"))
    }

    @Test
    fun `formatCardPan formats 16 digits into 4 spaced blocks with Persian numbers`() {
        val pan = "6037991827364512"
        val formatted = formatCardPan(pan)
        assertEquals("۶۰۳۷  ۹۹۱۸  ۲۷۳۶  ۴۵۱۲", formatted)
    }

    @Test
    fun `formatCardPan converts masked card patterns to Persian digits`() {
        val mask = "6037 •••• •••• 1234"
        val formatted = formatCardPan(mask)
        assertEquals("۶۰۳۷ •••• •••• ۱۲۳۴", formatted)
    }

    @Test
    fun `toAsciiDigits properly converts Persian and Arabic numerals to ASCII digits`() {
        assertEquals("0123456789", toAsciiDigits("۰۱۲۳۴۵۶۷۸۹"))
        assertEquals("0123456789", toAsciiDigits("٠١٢٣٤٥٦٧٨٩"))
        assertEquals("6037997512345678", toAsciiDigits("۶۰۳۷۹۹۷۵۱۲۳۴۵۶۷۸"))
        assertEquals("Card: 50222910", toAsciiDigits("Card: ۵۰۲۲۲۹۱۰"))
    }
}
