package com.doxigo.muchtoman.domain.shetab

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ShetabKitTest {

    @Test
    fun `toAsciiDigits converts both Persian and Arabic numerals to ASCII 0-9`() {
        val mixed = "۰۱۲۳۴۵۶۷۸۹ و ٠١٢٣٤٥٦٧٨٩"
        val expected = "0123456789 و 0123456789"
        assertEquals(expected, ShetabKit.toAsciiDigits(mixed))
    }

    @Test
    fun `isValidLuhn validates valid Shetab 16-digit card and rejects invalid ones`() {
        assertTrue(ShetabKit.isValidLuhn("6037991827364518"))
        assertTrue(ShetabKit.isValidLuhn("۶۰۳۷۹۹۱۸۲۷۳۶۴۵۱۸")) // Persian numerals
        assertFalse(ShetabKit.isValidLuhn("6037991827364512")) // wrong check digit
        assertFalse(ShetabKit.isValidLuhn("603799182736")) // incomplete PAN
        assertFalse(ShetabKit.isValidLuhn(""))
    }

    @Test
    fun `cleanSheba normalizes uppercase ASCII without spaces or hyphens`() {
        val raw = "ir82 0170-0000 0012 3456 7890 12"
        assertEquals("IR820170000000123456789012", ShetabKit.cleanSheba(raw))
    }

    @Test
    fun `isValidSheba performs ISO 7064 Mod 97-10 checksum validation`() {
        val acc = "0170000000123456789012"
        var rem = 0
        for (c in (acc + "182700")) {
            rem = (rem * 10 + (c - '0')) % 97
        }
        val check = (98 - rem).toString().padStart(2, '0')
        val validSheba = "IR$check$acc"

        assertTrue(ShetabKit.isValidSheba(validSheba))
        assertFalse(ShetabKit.isValidSheba("IR00$acc"))
        assertFalse(ShetabKit.isValidSheba("IR12345"))
    }

    @Test
    fun `formatSheba groups 26-char SHEBA into canonical 4-digit clusters`() {
        val raw = "ir82 0170-0000 0012 3456 7890 12"
        assertEquals("IR82  0170  0000  0012  3456  7890  12", ShetabKit.formatSheba(raw))
    }

    @Test
    fun `formatCardPan converts to Persian numerals in 4-digit blocks`() {
        val raw = "5022291012345678"
        assertEquals("۵۰۲۲  ۲۹۱۰  ۱۲۳۴  ۵۶۷۸", ShetabKit.formatCardPan(raw))
    }

    @Test
    fun `formatCardPanLtr wraps PAN in Unicode isolate markers`() {
        val ltr = ShetabKit.formatCardPanLtr("6037991827364518")
        assertTrue(ltr.startsWith("\u2066"))
        assertTrue(ltr.endsWith("\u2069"))
        assertTrue(ltr.contains("۶۰۳۷  ۹۹۱۸  ۲۷۳۶  ۴۵۱۸"))
    }
}
