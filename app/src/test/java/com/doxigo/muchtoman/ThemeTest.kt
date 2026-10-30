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
                "ZHETON_TERMINAL",
                "LIGHT",
                "DARK",
                "AMOLED",
                "APPLE_TITANIUM",
                "APPLE_SILVER",
                "REVOLUT_OBSIDIAN",
                "NORDIC_FROST",
                "CHAMPAGNE_GOLD",
                "PERSIAN_MINT",
                "ROSE_QUARTZ",
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
        assertEquals("کلاسیک روشن", ThemeMode.LIGHT.fa)
        assertEquals("اپل سیلور", ThemeMode.APPLE_SILVER.fa)
        assertEquals("نوردیک فراست", ThemeMode.NORDIC_FROST.fa)
        assertEquals("شامپاین", ThemeMode.CHAMPAGNE_GOLD.fa)
        assertEquals("فیروزه روشن", ThemeMode.PERSIAN_MINT.fa)
        assertEquals("رز کوارتز", ThemeMode.ROSE_QUARTZ.fa)
        assertEquals("ژتون", ThemeMode.ZHETON_TERMINAL.fa)
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

        // Verify Light/Dark partitioning
        assertEquals(false, ThemeMode.LIGHT.isDark)
        assertEquals(false, ThemeMode.APPLE_SILVER.isDark)
        assertEquals(false, ThemeMode.NORDIC_FROST.isDark)
        assertEquals(false, ThemeMode.CHAMPAGNE_GOLD.isDark)
        assertEquals(false, ThemeMode.PERSIAN_MINT.isDark)
        assertEquals(false, ThemeMode.ROSE_QUARTZ.isDark)

        assertEquals(true, ThemeMode.DARK.isDark)
        assertEquals(true, ThemeMode.AMOLED.isDark)
        assertEquals(true, ThemeMode.APPLE_TITANIUM.isDark)
        assertEquals(true, ThemeMode.REVOLUT_OBSIDIAN.isDark)
        assertEquals(true, ThemeMode.DUBAI_GOLD.isDark)
    }

    @Test
    fun `every ThemeMode has a complete ThemeVisualSpec with valid colors`() {
        for (mode in ThemeMode.entries) {
            val spec = getThemeVisualSpec(mode)
            assertEquals(mode, spec.mode)
            if (mode != ThemeMode.SYSTEM) {
                assertEquals("Mode ${mode.name} dark state matches isDark", mode.isDark, spec.isDark)
            }
            // Ensure color previews are set
            org.junit.Assert.assertNotEquals(androidx.compose.ui.graphics.Color.Unspecified, spec.bgPreview)
            org.junit.Assert.assertNotEquals(androidx.compose.ui.graphics.Color.Unspecified, spec.surfacePreview)
            org.junit.Assert.assertNotEquals(androidx.compose.ui.graphics.Color.Unspecified, spec.accentPreview)
            org.junit.Assert.assertNotEquals(androidx.compose.ui.graphics.Color.Unspecified, spec.heroTopPreview)
            org.junit.Assert.assertNotEquals(androidx.compose.ui.graphics.Color.Unspecified, spec.heroBottomPreview)
        }
    }

    @Test
    fun `light themes have higher luminance backgrounds than dark themes`() {
        val lightModes = ThemeMode.entries.filter { !it.isDark && it != ThemeMode.SYSTEM }
        val darkModes = ThemeMode.entries.filter { it.isDark && it != ThemeMode.SYSTEM }

        assertEquals(6, lightModes.size)
        assertEquals(12, darkModes.size)

        for (light in lightModes) {
            val spec = getThemeVisualSpec(light)
            val lum = spec.bgPreview.red * 0.299f + spec.bgPreview.green * 0.587f + spec.bgPreview.blue * 0.114f
            org.junit.Assert.assertTrue("Light mode ${light.name} background must be bright ($lum)", lum > 0.7f)
        }

        for (dark in darkModes) {
            val spec = getThemeVisualSpec(dark)
            val lum = spec.bgPreview.red * 0.299f + spec.bgPreview.green * 0.587f + spec.bgPreview.blue * 0.114f
            org.junit.Assert.assertTrue("Dark mode ${dark.name} background must be dark ($lum)", lum < 0.2f)
        }
    }

    @Test
    fun `light theme must not use murky pine green as primary or gold accent`() {
        val spec = getThemeVisualSpec(ThemeMode.LIGHT)
        val pineGreen = androidx.compose.ui.graphics.Color(0xFF0A423B)
        org.junit.Assert.assertNotEquals("ThemeMode.LIGHT preview accent must not be pine green", pineGreen, spec.accentPreview)

        val palette = getHeroPalette(ThemeMode.LIGHT, false)
        org.junit.Assert.assertNotEquals("ThemeMode.LIGHT hero gold must not be pine green", pineGreen, palette.gold)

        val scheme = getColorScheme(ThemeMode.LIGHT, false)
        org.junit.Assert.assertNotEquals("LightColors primary must not be pine green", pineGreen, scheme.primary)
    }

    @Test
    fun `ZhetonColors for light themes must provide dark readable text and light surfaces`() {
        val lightModes = ThemeMode.entries.filter { !it.isDark && it != ThemeMode.SYSTEM }
        for (mode in lightModes) {
            val scheme = getColorScheme(mode, false)
            val palette = getHeroPalette(mode, false)
            val zheton = getZhetonColors(scheme, isDark = false, heroPalette = palette)

            val textLum = zheton.textPrimary.red * 0.299f + zheton.textPrimary.green * 0.587f + zheton.textPrimary.blue * 0.114f
            org.junit.Assert.assertTrue("Mode ${mode.name} textPrimary must be dark (lum=$textLum)", textLum < 0.35f)

            val surfLum = zheton.surfacePrimary.red * 0.299f + zheton.surfacePrimary.green * 0.587f + zheton.surfacePrimary.blue * 0.114f
            org.junit.Assert.assertTrue("Mode ${mode.name} surfacePrimary must be light (lum=$surfLum)", surfLum > 0.7f)
        }
    }
}
