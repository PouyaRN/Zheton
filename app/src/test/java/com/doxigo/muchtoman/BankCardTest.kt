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
        assertEquals(BankCardThemes.BLU, BankCardThemes.resolve("نامشخص", "8600121234567890"))
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

        // Non-standard PAN length (e.g. 13 digits) segmented into 4-character clusters
        val nonStandardPan = "0235375275786"
        assertEquals("۰۲۳۵  ۳۷۵۲  ۷۵۷۸  ۶", formatCardPan(nonStandardPan))

        // Hyphenated non-standard PAN
        val hyphenatedPan = "0235-3752-7578-6"
        assertEquals("۰۲۳۵  ۳۷۵۲  ۷۵۷۸  ۶", formatCardPan(hyphenatedPan))
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

    @Test
    fun `BankCardThemes resolve respects explicit theme preset or custom gradient stops`() {
        // Explicit preset override
        val customTheme = BankCardThemes.resolve("بانک ملی ایران", theme = "royal")
        val royalPreset = BankCardThemes.PRESETS.first { it.first == "royal" }.second
        assertEquals(royalPreset.accentColor, customTheme.accentColor)

        // Custom two-stop hex gradient
        val hexTheme = BankCardThemes.resolve("بانک ملی ایران", theme = "#1A1A24,#E5A93C")
        assertEquals(androidx.compose.ui.graphics.Color(0xFFE5A93C), hexTheme.accentColor)

        // Bank fallback when theme is blank
        val fallbackTheme = BankCardThemes.resolve("بانک ملی ایران", theme = "")
        assertEquals(BankCardThemes.MELLI.accentColor, fallbackTheme.accentColor)
    }

    @Test
    fun `BankAccount preserves card nickname, expiry, sheba, and custom theme`() {
        val account = BankAccount(
            bank = "بانک ملت",
            mask = "610433••••••1234",
            balance = 5_000_000.0,
            nickname = "کارت حقوق",
            fullPan = "6104337712345678",
            expiry = "06/07",
            sheba = "IR820170000000123456789012",
            theme = "nordic",
        )
        assertEquals("کارت حقوق", account.nickname)
        assertEquals("6104337712345678", account.fullPan)
        assertEquals("06/07", account.expiry)
        assertEquals("IR820170000000123456789012", account.sheba)
        assertEquals("nordic", account.theme)
    }

    @Test
    fun `isValidLuhn accurately validates Iranian Shetab cards`() {
        org.junit.Assert.assertTrue(isValidLuhn("6037991827364518"))
        org.junit.Assert.assertTrue(isValidLuhn("۶۰۳۷۹۹۱۸۲۷۳۶۴۵۱۸")) // Persian numerals
        org.junit.Assert.assertFalse(isValidLuhn("6037991827364512")) // wrong check digit
        org.junit.Assert.assertFalse(isValidLuhn("603799182736")) // incomplete PAN
        org.junit.Assert.assertFalse(isValidLuhn(""))
    }

    @Test
    fun `SHEBA utilities clean, format and validate properly`() {
        val rawSheba = "ir82 0170-0000 0012 3456 7890 12"
        val cleaned = cleanSheba(rawSheba)
        assertEquals("IR820170000000123456789012", cleaned)

        val formatted = formatSheba(rawSheba)
        assertEquals("IR82  0170  0000  0012  3456  7890  12", formatted)

        // Generate a valid SHEBA using ISO 7064 check digits
        // Let account number be "0170000000123456789012"
        val acc = "0170000000123456789012"
        // (acc + "182700") % 97:
        var rem = 0
        for (c in (acc + "182700")) {
            rem = (rem * 10 + (c - '0')) % 97
        }
        val check = 98 - rem
        val checkStr = check.toString().padStart(2, '0')
        val validSheba = "IR$checkStr$acc"

        org.junit.Assert.assertTrue(isValidSheba(validSheba))
        org.junit.Assert.assertFalse(isValidSheba("IR00$acc"))
        org.junit.Assert.assertFalse(isValidSheba("IR12345")) // too short
    }

    @Test
    fun `BankCardThemes resolves all expanded Iranian banks and luxury presets`() {
        // Expanded banks
        assertEquals(BankCardThemes.MASKAN, BankCardThemes.resolve("نامشخص", "6280231234567890"))
        assertEquals(BankCardThemes.AYANDEH, BankCardThemes.resolve("نامشخص", "6362141234567890"))
        assertEquals(BankCardThemes.SHAHR, BankCardThemes.resolve("نامشخص", "5028061234567890"))
        assertEquals(BankCardThemes.MEHR_IRAN, BankCardThemes.resolve("نامشخص", "6063731234567890"))
        assertEquals(BankCardThemes.PARSIAN, BankCardThemes.resolve("نامشخص", "6221061234567890"))
        assertEquals(BankCardThemes.EGHTESAD_NOVIN, BankCardThemes.resolve("نامشخص", "6274121234567890"))
        assertEquals(BankCardThemes.POST_BANK, BankCardThemes.resolve("نامشخص", "6277601234567890"))
        assertEquals(BankCardThemes.KHAVARMIANEH, BankCardThemes.resolve("نامشخص", "5859471234567890"))
        assertEquals(BankCardThemes.MELAL, BankCardThemes.resolve("نامشخص", "6062561234567890"))

        // Luxury material presets
        val goldTheme = BankCardThemes.resolve("بانک ملی", theme = "gold")
        val obsidianTheme = BankCardThemes.resolve("بانک ملی", theme = "obsidian")
        val hologramTheme = BankCardThemes.resolve("بانک ملی", theme = "hologram")
        val titaniumTheme = BankCardThemes.resolve("بانک ملی", theme = "titanium")

        assertEquals(androidx.compose.ui.graphics.Color(0xFFFFDF73), goldTheme.accentColor)
        assertEquals(androidx.compose.ui.graphics.Color(0xFFFFD54F), obsidianTheme.accentColor)
        assertEquals(androidx.compose.ui.graphics.Color(0xFF00FFFF), hologramTheme.accentColor)
        assertEquals(androidx.compose.ui.graphics.Color(0xFF81D4FA), titaniumTheme.accentColor)
    }

    @Test
    fun `formatCardPanLtr isolates text in LTR Unicode markers`() {
        val ltrPan = formatCardPanLtr("6037991827364512")
        org.junit.Assert.assertTrue(ltrPan.startsWith("\u2066"))
        org.junit.Assert.assertTrue(ltrPan.endsWith("\u2069"))
        org.junit.Assert.assertTrue(ltrPan.contains("۶۰۳۷  ۹۹۱۸  ۲۷۳۶  ۴۵۱۲"))
    }

    @Test
    fun `computeTotals excludes archived holdings from total wealth calculation`() {
        val activeHolding = Holding(typeId = "usd", amount = 100.0, archived = false)
        val archivedHolding = Holding(typeId = "eur", amount = 50.0, archived = true)
        val rates = mapOf("usd" to 100_000.0, "eur" to 110_000.0)

        val totals = computeTotals(listOf(activeHolding, archivedHolding), rates)
        // Only active holding (100 * 100_000 = 10_000_000) should be counted
        assertEquals(10_000_000.0, totals.toman, 0.001)
        assertTrue(totals.missing.isEmpty())
    }
}
