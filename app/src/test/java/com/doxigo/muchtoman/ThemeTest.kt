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
                "ANDIS_TERMINAL",
                "LIGHT",
                "DARK",
                "AMOLED",
                "APPLE_TITANIUM",
                "REVOLUT_OBSIDIAN",
                "CASH_APP_NEON",
                "MONZO_OCEANIC",
                "DUBAI_GOLD",
                "OBSIDIAN_EMERALD",
                "ROYAL_AMETHYST",
                "NORDIC_SLATE",
                "CYBER_GOLD",
            ),
            names,
        )
        assertEquals("اندیس", ThemeMode.ANDIS_TERMINAL.fa)
        assertEquals("آمولد", ThemeMode.AMOLED.fa)
        assertEquals("اپل تیتانیوم", ThemeMode.APPLE_TITANIUM.fa)
        assertEquals("رولوت ابسیدین", ThemeMode.REVOLUT_OBSIDIAN.fa)
        assertEquals("کش‌اپ نئون", ThemeMode.CASH_APP_NEON.fa)
        assertEquals("مانزو اقیانوسی", ThemeMode.MONZO_OCEANIC.fa)
        assertEquals("دبی گلد", ThemeMode.DUBAI_GOLD.fa)
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
