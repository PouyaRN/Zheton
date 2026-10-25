package com.doxigo.muchtoman

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.max
import kotlin.math.min

/**
 * Every palette must be readable, not just selectable.
 *
 * The bug this locks down: the old code hardcoded a dark-tuned green for income, which measured
 * 3:1 on the light page but 5.8:1 on the dark one. Palette coverage tests would not have caught
 * that — contrast is what matters. These assertions run on the real schemes, so a new palette
 * cannot land with white-on-cream body text or an inverted status-bar expectation.
 */
class ThemePaletteTest {

    private fun contrast(a: Color, b: Color): Double {
        val la = a.luminance().toDouble()
        val lb = b.luminance().toDouble()
        val hi = max(la, lb)
        val lo = min(la, lb)
        return (hi + 0.05) / (lo + 0.05)
    }

    private val failures = mutableListOf<String>()

    private fun checkReadable(mode: ThemeMode, label: String, fg: Color, bg: Color) {
        val ratio = contrast(fg, bg)
        // 4.5:1 is WCAG AA for body text. Collect every failure instead of stopping at the
        // first, so one run names every palette that needs its pair adjusted.
        if (ratio < 4.5) failures += "$mode $label = ${"%.2f".format(ratio)}:1"
    }

    @Test
    fun `all palettes are defined and unique`() {
        assertEquals(19, ThemeMode.entries.size)
        val specs = ThemeMode.entries.map { getThemeVisualSpec(it) }
        assertEquals("every mode has a visual spec", ThemeMode.entries.size, specs.size)
        assertEquals("preview specs are unique", specs.size, specs.toSet().size)
    }

    @Test
    fun `every palette has readable body text on its page and surfaces`() {
        for (mode in ThemeMode.entries) {
            val scheme = getColorScheme(mode, systemDark = true)
            checkReadable(mode, "background/onBackground", scheme.onBackground, scheme.background)
            checkReadable(mode, "surface/onSurface", scheme.onSurface, scheme.surface)
            checkReadable(mode, "primary/onPrimary", scheme.onPrimary, scheme.primary)
        }
        assertTrue(
            "palettes below 4.5:1:\n" + failures.joinToString("\n"),
            failures.isEmpty(),
        )
    }

    @Test
    fun `darkness flag agrees with the actual page luminance`() {
        for (mode in ThemeMode.entries) {
            if (mode == ThemeMode.SYSTEM) continue
            val scheme = getColorScheme(mode, systemDark = true)
            val pageIsDark = scheme.background.luminance() < 0.5f
            assertEquals("$mode isDark flag disagrees with its background", mode.isDark, pageIsDark)
        }
    }

    @Test
    fun `system mode follows the phone`() {
        val light = getColorScheme(ThemeMode.SYSTEM, systemDark = false).background.luminance()
        val dark = getColorScheme(ThemeMode.SYSTEM, systemDark = true).background.luminance()
        assertTrue("system light page is light", light > 0.5f)
        assertTrue("system dark page is dark", dark < 0.5f)
    }


    @Test
    fun `hero figures stay readable on the hero field`() {
        val bad = mutableListOf<String>()
        for (mode in ThemeMode.entries) {
            val hero = getHeroPalette(mode, systemDark = false)
            if (contrast(hero.gold, hero.top) < 4.5) {
                bad += "$mode hero gold on hero = ${"%.2f".format(contrast(hero.gold, hero.top))}:1"
            }
            if (contrast(hero.strong, hero.top) < 4.5) {
                bad += "$mode hero strong on hero = ${"%.2f".format(contrast(hero.strong, hero.top))}:1"
            }
            if (contrast(hero.mint, hero.top) < 4.5) {
                bad += "$mode hero mint on hero = ${"%.2f".format(contrast(hero.mint, hero.top))}:1"
            }
            if (contrast(hero.warn, hero.top) < 4.5) {
                bad += "$mode hero warn on hero = ${"%.2f".format(contrast(hero.warn, hero.top))}:1"
            }
        }
        assertTrue("hero pairs below 4.5:1:\n" + bad.joinToString("\n"), bad.isEmpty())
    }


    @Test
    fun `accent text stays readable on the page`() {
        val bad = mutableListOf<String>()
        for (mode in ThemeMode.entries) {
            val scheme = getColorScheme(mode, systemDark = false)
            for ((label, fg) in listOf(
                "primary" to scheme.primary,
                "secondary" to scheme.secondary,
                "tertiary" to scheme.tertiary,
                "error" to scheme.error,
            )) {
                val ratio = contrast(fg, scheme.background)
                if (ratio < 4.5) bad += "$mode $label on background = ${"%.2f".format(ratio)}:1"
            }
        }
        assertTrue("accent text below 4.5:1:\n" + bad.joinToString("\n"), bad.isEmpty())
    }

    @Test
    fun `income colour stays readable on paper and on ink`() {
        // Mirrors incomeColor()'s two branches without a Compose runtime: if either constant is
        // darkened/lightened later, this fails before a phone shows an unreadable figure.
        val paperGreen = Color(0xFF167A44)
        val inkGreen = Color(0xFF5BE39A)
        val paper = getColorScheme(ThemeMode.LIGHT, systemDark = false).background
        val ink = getColorScheme(ThemeMode.DARK, systemDark = true).background
        assertTrue("income on paper ${contrast(paperGreen, paper)} < 4.5", contrast(paperGreen, paper) >= 4.5)
        assertTrue("income on ink ${contrast(inkGreen, ink)} < 4.5", contrast(inkGreen, ink) >= 4.5)
    }
}
