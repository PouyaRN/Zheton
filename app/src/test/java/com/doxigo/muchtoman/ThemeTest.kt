package com.doxigo.muchtoman

import org.junit.Assert.assertEquals
import org.junit.Test

class ThemeTest {

    @Test
    fun `ThemeMode values include SYSTEM, LIGHT, DARK, and AMOLED`() {
        val names = ThemeMode.entries.map { it.name }
        assertEquals(listOf("SYSTEM", "LIGHT", "DARK", "AMOLED"), names)
        assertEquals("آمولد", ThemeMode.AMOLED.fa)
    }

    @Test
    fun `ThemeMode parses safely and falls back on invalid values`() {
        val parsedAmoled = runCatching { ThemeMode.valueOf("AMOLED") }.getOrDefault(ThemeMode.SYSTEM)
        assertEquals(ThemeMode.AMOLED, parsedAmoled)

        val parsedInvalid = runCatching { ThemeMode.valueOf("UNKNOWN_THEME") }.getOrDefault(ThemeMode.SYSTEM)
        assertEquals(ThemeMode.SYSTEM, parsedInvalid)
    }
}
