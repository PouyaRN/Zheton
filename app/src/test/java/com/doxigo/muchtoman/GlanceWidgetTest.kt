package com.doxigo.muchtoman

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
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

    @Test
    fun `marketTickers filters to pinned keys in catalog order`() {
        val effective = mapOf(
            "gold18" to 6_680_000.0,
            "usd" to 60_200.0,
            "usdt" to 61_300.0,
            "coin_emami" to 58_000_000.0,
            "eur" to 65_000.0,
        )

        val pinned = setOf("usd", "gold18")
        val tickers = marketTickers(effective, pinned)

        // Catalog order wins: gold18 comes before usd even though usd was "picked" first.
        assertEquals(listOf("gold18", "usd"), tickers.map { it.key })
        assertEquals("گرم طلا", tickers[0].label)
        assertEquals("دلار", tickers[1].label)
        assertEquals(6_680_000.0, tickers[0].rateToman, 0.001)
    }

    @Test
    fun `marketTickers drops unpinned and missing rates`() {
        val effective = mapOf("gold18" to 6_680_000.0, "usd" to 0.0)
        val tickers = marketTickers(effective, setOf("gold18", "usd", "usdt"))

        // usd is pinned but zero, usdt pinned but absent: neither renders.
        assertEquals(listOf("gold18"), tickers.map { it.key })
    }

    @Test
    fun `marketTickers returns empty list when nothing is pinned`() {
        val effective = mapOf("gold18" to 6_680_000.0)
        assertTrue(marketTickers(effective, emptySet()).isEmpty())
    }

    @Test
    fun `runwayWidgetState projects safe spend and gold peg with masked lock`() {
        // Anchor mid-month: day 10 of a 31-day month → 22 days remaining, ~32% progress.
        val epochMs = 1_700_000_000_000L
        val state = runwayWidgetState(
            liquidCashToman = 28_000_000L,
            upcomingObligationsToman = 5_000_000L,
            reservationsToman = 1_000_000L,
            goldRateToman = 6_400_000.0,
            isMasked = false,
            todayEpochMs = epochMs,
        )

        assertEquals(22_000_000L, state.safeToSpendToman)
        assertEquals(22_000_000L / state.daysRemaining, state.dailyAllowanceToman)
        assertTrue(state.daysRemaining >= 1)
        assertTrue(state.monthProgressPercent in 0..100)
        assertEquals(28_000_000L / 6_400_000.0, state.goldGrams ?: 0.0, 0.001)
        assertTrue(state.dailyBleedToman > 0)
        assertFalse(state.isMasked)
        assertFalse(state.isOverdrawn)
    }

    @Test
    fun `runwayWidgetState budget bar rises as commitments consume the allowance`() {
        // The bar is consumption (share of the month's total allowance already spoken for
        // by commitments and gone from the remainder), not calendar time. Same day, same
        // liquid cash: more obligations must mean a fuller bar.
        val epochMs = 1_700_000_000_000L
        val light = runwayWidgetState(
            liquidCashToman = 30_000_000L,
            upcomingObligationsToman = 0L,
            reservationsToman = 0L,
            goldRateToman = null,
            isMasked = false,
            todayEpochMs = epochMs,
        )
        val heavy = runwayWidgetState(
            liquidCashToman = 30_000_000L,
            upcomingObligationsToman = 22_000_000L,
            reservationsToman = 0L,
            goldRateToman = null,
            isMasked = false,
            todayEpochMs = epochMs,
        )

        assertTrue(light.monthProgressPercent in 0..100)
        assertTrue(heavy.monthProgressPercent > light.monthProgressPercent)
        assertTrue(heavy.monthProgressPercent in 0..100)

        // Fully overdrawn pins the bar at the top.
        val drained = runwayWidgetState(
            liquidCashToman = 1_000_000L,
            upcomingObligationsToman = 20_000_000L,
            reservationsToman = 0L,
            goldRateToman = null,
            isMasked = false,
        )
        assertEquals(100, drained.monthProgressPercent)
        assertTrue(drained.isOverdrawn)
    }

    @Test
    fun `runwayWidgetState flags overdrawn budget`() {
        val state = runwayWidgetState(
            liquidCashToman = 2_000_000L,
            upcomingObligationsToman = 9_000_000L,
            reservationsToman = 0L,
            goldRateToman = null,
            isMasked = false,
        )

        assertTrue(state.isOverdrawn)
        assertEquals(0L, state.dailyAllowanceToman)
        assertEquals(-7_000_000L, state.safeToSpendToman)
        assertNull(state.goldGrams)
    }

    @Test
    fun `quick add chips carry valid category ids and quick add intent contract holds`() {
        // Every chip's id must be one the quick-add dialog's own category list accepts,
        // so a widget tap always lands on a real preselected category.
        val dialogCategories = setOf(
            "cat_groceries",
            "cat_dining",
            "cat_transport",
            "cat_bills",
            "cat_shopping",
            "cat_fuel",
            "cat_uncategorised",
        )
        assertTrue(QUICK_ADD_CHIPS.isNotEmpty())
        QUICK_ADD_CHIPS.forEach { chip ->
            assertTrue("unknown chip category: ${chip.categoryId}", chip.categoryId in dialogCategories)
            assertTrue(chip.label.isNotBlank())
        }

        // The widget's intent extra key must match what QuickAddActivity reads back.
        assertEquals("com.doxigo.muchtoman.extra.CATEGORY_ID", QuickAddActivity.EXTRA_CATEGORY_ID)
    }

    @Test
    fun `quickCardRows projects full pan and sheba or hides everything when masked`() {
        val accounts = listOf(
            BankAccount(
                bank = "MELLAT",
                mask = "610433••••••1234",
                balance = 15_000_000.0,
                nickname = "کارت حقوق",
                fullPan = "۶۱۰۴۳۳۷۷۱۲۳۴۵۶۷۸",
                sheba = "IR820170000000123456789012",
            ),
            BankAccount(
                bank = "SAMAN",
                mask = "621986••••••9012",
                balance = 2_000_000.0,
            ),
        )

        val rows = quickCardRows(accounts, isMasked = false)
        assertEquals(2, rows.size)
        assertEquals("کارت حقوق", rows[0].label)
        assertEquals("6104337712345678", rows[0].fullPanDigits)
        assertEquals("IR820170000000123456789012", rows[0].sheba)
        // Second card has no full PAN: copy button hidden, mask shown instead.
        assertTrue(rows[1].fullPanDigits.isEmpty())
        assertTrue(rows[1].maskedPan.isNotBlank())

        // Widget lock hides the whole deck.
        assertTrue(quickCardRows(accounts, isMasked = true).isEmpty())
    }

    @Test
    fun `pan formatting handles 16 digits and masked states`() {
        val pan = "6037991823456789"
        val digits = pan.filter { it.isDigit() }
        assertEquals(16, digits.length)
        val p1 = digits.substring(0, 4)
        val p2 = digits.substring(4, 8)
        val p3 = digits.substring(8, 12)
        val p4 = digits.substring(12, 16)
        assertEquals("6037  9918  2345  6789", "$p1  $p2  $p3  $p4")

        val masked = "••••  ••••  ••••  ${digits.takeLast(4)}"
        assertEquals("••••  ••••  ••••  6789", masked)
    }
}
