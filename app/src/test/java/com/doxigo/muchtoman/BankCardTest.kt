package com.doxigo.muchtoman

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class BankCardTest {

    @Test
    fun `BankCardThemes resolves correct theme per Iranian bank name or BIN prefix`() {
        assertEquals(BankCardThemes.MELLI, BankCardThemes.resolve("بانک ملی ایران"))
        assertEquals(BankCardThemes.MELLI, BankCardThemes.resolve("نامشخص", "6037991827364512"))
        assertEquals(BankCardThemes.MELLAT, BankCardThemes.resolve("بانک ملت"))
        assertEquals(BankCardThemes.MELLAT, BankCardThemes.resolve("نامشخص", "6104331234567890"))
        assertEquals(BankCardThemes.SAMAN, BankCardThemes.resolve("بانک سامان"))
        assertEquals(BankCardThemes.SAMAN, BankCardThemes.resolve("نامشخص", "6219861234567890"))
        assertEquals(BankCardThemes.PASARGAD, BankCardThemes.resolve("بانک پاسارگاد"))
        assertEquals(BankCardThemes.PASARGAD, BankCardThemes.resolve("نامشخص", "5022291234567890"))
        assertEquals(BankCardThemes.BLU, BankCardThemes.resolve("بلو بانک"))
        assertEquals(BankCardThemes.BLU, BankCardThemes.resolve("نامشخص", "6221061234567890"))
        assertEquals(BankCardThemes.TEJARAT, BankCardThemes.resolve("بانک تجارت"))
        assertEquals(BankCardThemes.TEJARAT, BankCardThemes.resolve("نامشخص", "5859831234567890"))
        assertEquals(BankCardThemes.RESALAT, BankCardThemes.resolve("بانک رسالت"))
        assertEquals(BankCardThemes.SEPAH, BankCardThemes.resolve("بانک سپه"))
        assertEquals(BankCardThemes.SEPAH, BankCardThemes.resolve("نامشخص", "5892101234567890"))
        assertEquals(BankCardThemes.KESHAVARZI, BankCardThemes.resolve("بانک کشاورزی"))
        assertEquals(BankCardThemes.KESHAVARZI, BankCardThemes.resolve("نامشخص", "6037701234567890"))
        assertEquals(BankCardThemes.SADERAT, BankCardThemes.resolve("بانک صادرات"))
        assertEquals(BankCardThemes.REFAH, BankCardThemes.resolve("بانک رفاه"))
        assertEquals(BankCardThemes.DEFAULT, BankCardThemes.resolve("بانک نامشخص"))
    }

    @Test
    fun `formatCardPan formats 16 digits into 4 spaced blocks with Persian numbers`() {
        val pan = "6037991827364512"
        val formatted = formatCardPan(pan)
        assertEquals("۶۰۳۷  ۹۹۱۸  ۲۷۳۶  ۴۵۱۲", formatted)
    }

    @Test
    fun `formatCardPan converts masked card patterns into 4-digit groups`() {
        val maskContinuous = "603799••••••5421"
        val formattedContinuous = formatCardPan(maskContinuous)
        assertEquals("۶۰۳۷  ۹۹••  ••••  ۵۴۲۱", formattedContinuous)

        val maskSpaced = "6037 •••• •••• 1234"
        val formatted = formatCardPan(maskSpaced)
        assertEquals("۶۰۳۷ •••• •••• ۱۲۳۴", formatted)
    }

    @Test
    fun `toAsciiDigits properly converts Persian and Arabic numerals to ASCII digits`() {
        assertEquals("0123456789", toAsciiDigits("۰۱۲۳۴۵۶۷۸۹"))
        assertEquals("0123456789", toAsciiDigits("٠١٢٣٤٥٦٧٨٩"))
        assertEquals("6037997512345678", toAsciiDigits("۶۰۳۷۹۹۷۵۱۲۳۴۵۶۷۸"))
        assertEquals("Card: 50222910", toAsciiDigits("Card: ۵۰۲۲۲۹۱۰"))
    }

    @Test
    fun `dormant account detection flags accounts inactive for more than 45 days`() {
        val now = 1725000000000L
        val activeAccount = BankAccount(
            bank = "MELLAT",
            mask = "610433••••••1234",
            balance = 15_000_000.0,
            updatedAt = now - (10L * 24L * 3600L * 1000L), // 10 days ago
        )
        val dormantAccount = BankAccount(
            bank = "MELLI",
            mask = "603799••••••5678",
            balance = 2_000_000.0,
            updatedAt = now - (50L * 24L * 3600L * 1000L), // 50 days ago (>45 days)
        )
        val unanchoredAccount = BankAccount(
            bank = "SAMAN",
            mask = "621986••••••9012",
            balance = 0.0,
            updatedAt = 0L,
        )

        org.junit.Assert.assertFalse(activeAccount.isDormant(now))
        org.junit.Assert.assertTrue(dormantAccount.isDormant(now))
        org.junit.Assert.assertFalse(unanchoredAccount.isDormant(now))

        // Sorting check: active accounts precede dormant accounts
        val accounts = listOf(dormantAccount, activeAccount)
        val sorted = accounts.sortedBy { if (it.isDormant(now)) 1 else 0 }
        assertEquals(activeAccount, sorted[0])
        assertEquals(dormantAccount, sorted[1])
    }
}
