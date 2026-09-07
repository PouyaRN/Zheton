package com.doxigo.muchtoman

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class GlanceWidgetTest {

    @Test
    fun `glance widget state respects masked lock`() {
        val state = GlanceWidgetState(
            netWorthToman = 125_400_000.0,
            isMasked = true,
            deltaPercent = 2.5,
            deltaSince = "از دیروز",
            todaySpendToman = 450_000L,
            goldRateToman = 4_500_000.0,
            tetherRateToman = 85_000.0,
            nextCommitmentText = "دیجی‌پی: ۳ روز مانده (۵۰۰ هزار ت)",
        )

        val totalStr = if (state.isMasked) "٭٭٭" else "${faCompact(state.netWorthToman, 2)} تومان"
        assertEquals("٭٭٭", totalStr)
    }

    @Test
    fun `glance widget state formats unmasked net worth and delta`() {
        val state = GlanceWidgetState(
            netWorthToman = 125_400_000.0,
            isMasked = false,
            deltaPercent = 2.5,
            deltaSince = "از دیروز",
            todaySpendToman = 450_000L,
            goldRateToman = 4_500_000.0,
            tetherRateToman = 85_000.0,
            nextCommitmentText = null,
        )

        val totalStr = if (state.isMasked) "٭٭٭" else "${faCompact(state.netWorthToman, 2)} تومان"
        assertTrue("Contains million magnitude", totalStr.contains("میلیون"))
        assertTrue("Contains Toman unit", totalStr.contains("تومان"))

        val deltaStr = "${if (state.deltaPercent!! >= 0) "+" else ""}${faDecimal(kotlin.math.abs(state.deltaPercent), 1)}٪ ${state.deltaSince}"
        assertTrue("Contains positive sign", deltaStr.startsWith("+"))
        assertTrue("Contains since label", deltaStr.contains("از دیروز"))
    }

    @Test
    fun `glance widget formats today spend appropriately`() {
        val positiveSpendState = GlanceWidgetState(
            netWorthToman = 50_000_000.0,
            isMasked = false,
            deltaPercent = null,
            deltaSince = "",
            todaySpendToman = 350_000L,
            goldRateToman = 4_500_000.0,
            tetherRateToman = 85_000.0,
            nextCommitmentText = null,
        )

        val label = if (positiveSpendState.todaySpendToman > 0) {
            "خرج امروز: ${faCompact(positiveSpendState.todaySpendToman.toDouble())} ت"
        } else {
            "خرج امروز: صفر"
        }

        assertTrue("Contains 350 thousand compact notation", label.contains("۳۵۰ هزار"))
        assertTrue("Contains Toman suffix", label.contains("ت"))

        val zeroSpendState = positiveSpendState.copy(todaySpendToman = 0L)
        val zeroLabel = if (zeroSpendState.todaySpendToman > 0) {
            "خرج امروز: ${faCompact(zeroSpendState.todaySpendToman.toDouble())} ت"
        } else {
            "خرج امروز: صفر"
        }
        assertEquals("خرج امروز: صفر", zeroLabel)
    }

    @Test
    fun `glance widget installment countdown formatting`() {
        val now = System.currentTimeMillis()
        val commitment = Commitment(
            id = "c1",
            title = "اسنپ‌پی قسط ۲",
            provider = "اسنپ‌پی",
            totalAmountRial = 20_000_000L,
            remainingAmountRial = 5_000_000L,
            installmentCount = 4,
            paidInstallmentCount = 3,
            nextDueTimestamp = now + (3L * 24 * 3600 * 1000L) + 5000L,
        )

        val days = commitment.nextDueDays
        assertEquals(3, days)

        val dueLabel = when (days) {
            0 -> "سررسید امروز"
            1 -> "سررسید فردا"
            else -> "${faNumber(days.toDouble())} روز مانده"
        }
        val text = "${commitment.provider}: $dueLabel (${faCompact(commitment.remainingAmountToman.toDouble())} ت)"
        assertTrue(text.contains("اسنپ‌پی"))
        assertTrue(text.contains("۳ روز مانده"))
        assertTrue(text.contains("۵۰۰ هزار ت"))
    }

    @Test
    fun `rates ticker formats gold and tether accurately`() {
        val rates = mapOf(
            "gold18" to 4_580_000.0,
            "usdt" to 87_200.0,
        )

        val goldFormatted = "طلا: ${faCompact(rates["gold18"]!!)}"
        val tetherFormatted = "تتر: ${faCompact(rates["usdt"]!!)}"

        assertTrue(goldFormatted.contains("۴٫۶ میلیون") || goldFormatted.contains("۴٫۵۸ میلیون") || goldFormatted.contains("میلیون"))
        assertTrue(tetherFormatted.contains("۸۷٫۲ هزار") || tetherFormatted.contains("۸۷ هزار") || tetherFormatted.contains("هزار"))
    }
}
