package com.doxigo.muchtoman

import org.junit.Assert.assertEquals
import org.junit.Test

class ThemeTest {

    @Test
    fun `ThemeMode values include SYSTEM, LIGHT, DARK, and AMOLED`() {
        val names = ThemeMode.entries.map { it.name }
        assertEquals(
            listOf(
                "SYSTEM",
                "LIGHT",
                "DARK",
                "AMOLED",
                "OBSIDIAN_EMERALD",
                "ROYAL_AMETHYST",
                "NORDIC_SLATE",
                "CYBER_GOLD",
            ),
            names,
        )
        assertEquals("آمولد", ThemeMode.AMOLED.fa)
        assertEquals("زمرد", ThemeMode.OBSIDIAN_EMERALD.fa)
        assertEquals("ارغوانی", ThemeMode.ROYAL_AMETHYST.fa)
        assertEquals("نوردیک", ThemeMode.NORDIC_SLATE.fa)
        assertEquals("سایبرگلد", ThemeMode.CYBER_GOLD.fa)
    }

    @Test
    fun `ThemeMode parses safely and falls back on invalid values`() {
        val parsedAmoled = runCatching { ThemeMode.valueOf("AMOLED") }.getOrDefault(ThemeMode.SYSTEM)
        assertEquals(ThemeMode.AMOLED, parsedAmoled)

        val parsedInvalid = runCatching { ThemeMode.valueOf("UNKNOWN_THEME") }.getOrDefault(ThemeMode.SYSTEM)
        assertEquals(ThemeMode.SYSTEM, parsedInvalid)
    }
}
