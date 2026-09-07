package com.doxigo.muchtoman

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class IntelligenceTest {

    // ── Ticket 01: Currency Lens Tests ──────────────────────────────

    @Test
    fun `formatInDenominator converts to gold, usdt, and coin correctly`() {
        val rates = mapOf(
            "gold18" to 3_500_000.0,     // 3.5M Toman per gram
            "usd" to 60_000.0,           // 60k Toman per USD/USDT
            "coin_emami" to 42_000_000.0 // 42M Toman per Emami coin
        )

        val amountToman = 210_000_000.0 // 210 Million Toman

        // Toman
        val (tomanText, tomanUnit) = formatInDenominator(amountToman, CurrencyDenominator.TOMAN, rates)
        assertEquals("تومان", tomanUnit)
        assertTrue(tomanText.contains("۲۱۰"))

        // Gold 18k: 210M / 3.5M = 60 grams
        val (goldText, goldUnit) = formatInDenominator(amountToman, CurrencyDenominator.GOLD_18K, rates)
        assertEquals("گرم", goldUnit)
        assertTrue(goldText.contains("۶۰"))

        // USDT: 210M / 60k = 3500 USDT
        val (usdText, usdUnit) = formatInDenominator(amountToman, CurrencyDenominator.USD_TETHER, rates)
        assertEquals("دلار", usdUnit)
        assertTrue(usdText.contains("۳٫۵") || usdText.contains("۳٬۵۰۰"))

        // Emami Coin: 210M / 42M = 5 coins
        val (coinText, coinUnit) = formatInDenominator(amountToman, CurrencyDenominator.EMAMI_COIN, rates)
        assertEquals("سکه", coinUnit)
        assertTrue(coinText.contains("۵"))

        // Missing rate falls back gracefully to Toman
        val (fallbackText, fallbackUnit) = formatInDenominator(amountToman, CurrencyDenominator.GOLD_18K, emptyMap())
        assertEquals("تومان", fallbackUnit)
        assertTrue(fallbackText.contains("۲۱۰"))
    }

    // ── Ticket 02: Daily Cash Burn & Safe-to-Spend Tests ─────────────

    @Test
    fun `calculateDailyCashBurn calculates daily inflation drag accurately`() {
        val liquidCash = 36_500_000L // 36.5M Toman
        val result = calculateDailyCashBurn(liquidCash, annualInflationPercent = 40.0)

        // 36,500,000 * (0.40 / 365) = 40,000 Toman/day
        assertEquals(40_000L, result.dailyLossToman)
        assertTrue(result.monthlyLossToman > 0L)

        // Zero cash returns zero loss
        val zeroResult = calculateDailyCashBurn(0L, 45.0)
        assertEquals(0L, zeroResult.dailyLossToman)
    }

    @Test
    fun `calculateSafeToSpend deducts obligations and computes daily allowance`() {
        // Fixed date in Ordibehesht (month length = 31 days)
        val epochMs = 1715000000000L // ~1403/02/17
        val liquidCash = 20_000_000L
        val obligations = 5_000_000L
        val reservations = 3_000_000L

        val result = calculateSafeToSpend(
            liquidCashToman = liquidCash,
            upcomingObligationsToman = obligations,
            reservationsToman = reservations,
            todayEpochMs = epochMs,
        )

        assertEquals(12_000_000L, result.safeToSpendToman)
        assertEquals(8_000_000L, result.committedObligationsToman)
        assertFalse(result.isOverdrawn)
        assertTrue(result.daysRemaining in 1..31)
        assertTrue(result.dailyAllowanceToman > 0L)

        // Overdrawn test
        val overdrawnResult = calculateSafeToSpend(
            liquidCashToman = 5_000_000L,
            upcomingObligationsToman = 10_000_000L,
            todayEpochMs = epochMs,
        )
        assertTrue(overdrawnResult.isOverdrawn)
        assertEquals(-5_000_000L, overdrawnResult.safeToSpendToman)
        assertEquals(0L, overdrawnResult.dailyAllowanceToman)
    }

    // ── Ticket 03: Gold Coin Bubble Tests ────────────────────────────

    @Test
    fun `evaluateCoinBubble calculates intrinsic gold value and bubble percentage`() {
        val spot18k = 3_500_000.0 // 3.5M Toman/g
        // Emami fine gold: 7.3197g * (3.5M / 0.75) * 1.02 ≈ 34.8M Toman
        val intrinsic = calculateCoinIntrinsicToman(GoldCoinType.EMAMI, spot18k)
        assertTrue("Intrinsic must be around 34.8M Toman", intrinsic in 34_000_000L..36_000_000L)

        // Market price = 42,000,000 Toman
        val bubble = evaluateCoinBubble(GoldCoinType.EMAMI, 42_000_000L, spot18k)
        assertTrue("Bubble amount should be positive", bubble.bubbleAmountToman > 0L)
        assertTrue("Bubble percent should be around 16-20%", bubble.bubblePercent in 15.0..22.0)
    }

    // ── Ticket 04: Card BIN Intelligence & Recurring Tests ───────────

    @Test
    fun `lookupBankByCardBin identifies major Iranian banks correctly`() {
        val mellat = lookupBankByCardBin("6104331234567890")
        assertNotNull(mellat)
        assertEquals(Bank.MELLAT, mellat?.bank)
        assertEquals("بانک ملت", mellat?.bankNameFa)

        val melli = lookupBankByCardBin("603799******1122")
        assertNotNull(melli)
        assertEquals(Bank.MELLI, melli?.bank)

        val saman = lookupBankByCardBin("621986")
        assertNotNull(saman)
        assertEquals(Bank.SAMAN, saman?.bank)

        val pasargad = lookupBankByCardBin("5022290000000000")
        assertNotNull(pasargad)
        assertEquals(Bank.PASARGAD, pasargad?.bank)

        // Short or unknown
        assertNull(lookupBankByCardBin("123"))
        assertNull(lookupBankByCardBin("999999123456"))
    }

    @Test
    fun `detectRecurringExpenses identifies monthly recurring bills`() {
        fun makeOut(ref: String, merchant: String, amountRial: Long, day: Long) = LedgerEntry(
            txn = Txn(
                ref = ref, srcHash = "h", seq = 0, at = day * 86400000L, day = day,
                bank = "MELLAT", accountId = "acc", direction = "out", amountRial = amountRial,
                signedRial = -amountRial, balanceRial = null, feeRial = null, mask = "", instrument = "card",
                merchant = merchant, merchantNorm = merchant, refNo = "", printedAt = "",
                channel = "pos", unitPrinted = "none", inferred = false, parserVer = PARSER_VERSION,
            ),
            categoryId = "cat_internet",
            categoryFa = "اینترنت",
            confidence = 100,
            needsReview = false,
            duplicate = false,
            transfer = false,
        )

        val entries = listOf(
            // Irancell monthly package: 3 payments ~30 days apart with exact same amount
            makeOut("tx1", "ایرانسل", 1_500_000L, day = 100L),
            makeOut("tx2", "ایرانسل", 1_500_000L, day = 130L),
            makeOut("tx3", "ایرانسل", 1_500_000L, day = 160L),
            // One-off purchase: only 1 entry
            makeOut("tx4", "کتابفروشی افق", 2_000_000L, day = 110L),
        )

        val recurring = detectRecurringExpenses(entries)
        assertEquals(1, recurring.size)
        assertEquals("ایرانسل", recurring.first().merchantKey)
        assertEquals(1_500_000L, recurring.first().averageAmountRial)
        assertEquals(3, recurring.first().count)
    }

    // ── Ticket 05: Failed POS Watchdog Tests ─────────────────────────

    @Test
    fun `reconcileFailedWatchdogs tracks and resolves failed debit alerts`() {
        val now = 1720000000000L
        fun makeTxn(ref: String, dir: String, merchant: String, amount: Long, at: Long, mask: String = "6104") = LedgerEntry(
            txn = Txn(
                ref = ref, srcHash = "h", seq = 0, at = at, day = at / 86400000L,
                bank = "MELLAT", accountId = "acc", direction = dir, amountRial = amount,
                signedRial = if (dir == "in") amount else -amount, balanceRial = null, feeRial = null, mask = mask,
                instrument = "card", merchant = merchant, merchantNorm = merchant, refNo = "", printedAt = "",
                channel = "pos", unitPrinted = "none", inferred = false, parserVer = PARSER_VERSION,
            ),
            categoryId = "cat_other", categoryFa = "سایر", confidence = 100, needsReview = false, duplicate = false, transfer = false,
        )

        // Case 1: Failed debit that was refunded 2 hours later
        val failed1 = makeTxn("fail1", "out", "تراکنش ناموفق خرید", 500_000L, at = now - 10 * 3600 * 1000L)
        val refund1 = makeTxn("ref1", "in", "اصلاحیه واریز", 500_000L, at = now - 8 * 3600 * 1000L)

        // Case 2: Failed debit 20 hours ago, still awaiting refund (within 72h)
        val failed2 = makeTxn("fail2", "out", "مغایرت شتابی", 800_000L, at = now - 20 * 3600 * 1000L)

        // Case 3: Failed debit 80 hours ago, overdue!
        val failed3 = makeTxn("fail3", "out", "خطا در تراکنش", 1_200_000L, at = now - 80 * 3600 * 1000L)

        val watchdogs = reconcileFailedWatchdogs(
            failedEntries = listOf(failed1, failed2, failed3),
            allEntries = listOf(failed1, refund1, failed2, failed3),
            nowEpochMs = now,
        )

        assertEquals(3, watchdogs.size)
        assertEquals(WatchdogStatus.REFUNDED, watchdogs[0].status)
        assertEquals("ref1", watchdogs[0].refundRef)

        assertEquals(WatchdogStatus.PENDING, watchdogs[1].status)
        assertTrue("Hours remaining should be ~52h", watchdogs[1].hoursRemaining in 51..53)

        assertEquals(WatchdogStatus.OVERDUE, watchdogs[2].status)
        assertEquals(0, watchdogs[2].hoursRemaining)
    }

    // ── Ticket 06: Life Energy & Wishlist Tests ──────────────────────

    @Test
    fun `calculateLifeEnergy converts expense into active labor hours`() {
        val monthlyWageToman = 17_600_000L // 17.6M Toman
        val monthlyHours = 176 // 100,000 Toman per hour

        val purchaseToman = 450_000L // 4.5 hours
        val energy = calculateLifeEnergy(purchaseToman, monthlyWageToman, monthlyHours)

        assertEquals(4, energy.hours)
        assertEquals(30, energy.minutes)
        assertTrue(energy.formattedFa.contains("۴ ساعت و ۳۰ دقیقه"))
    }

    @Test
    fun `WishlistItem tracks cooling period remaining hours`() {
        val now = 1720000000000L
        val itemRecent = WishlistItem(
            title = "هدفون بی‌سیم",
            amountToman = 3_000_000L,
            addedAtEpochMs = now - 10 * 3600 * 1000L, // 10 hours ago
        )
        assertFalse(itemRecent.isCoolingPeriodElapsed(now))
        assertEquals(38, itemRecent.hoursRemaining(now))

        val itemExpired = WishlistItem(
            title = "کفش ورزشی",
            amountToman = 2_000_000L,
            addedAtEpochMs = now - 50 * 3600 * 1000L, // 50 hours ago
        )
        assertTrue(itemExpired.isCoolingPeriodElapsed(now))
        assertEquals(0, itemExpired.hoursRemaining(now))
    }

    // ── Ticket 07: Dynamic Wealth Defense & Purchasing Optimization ──

    @Test
    fun `PurchasingOptimizationEngine recommends Gold ETF for micro budget`() {
        val microBudget = 5_000_000L // 5 Million Toman
        val advice = PurchasingOptimizationEngine.computeBestBuy(
            budgetToman = microBudget,
            usdtToman = 65_000.0,
            gold18Toman = 3_500_000.0,
            emamiCoinToman = 45_000_000.0,
            robCoinToman = 18_000_000.0,
        )

        assertEquals(AssetClass.GOLD_ETF, advice.primaryRecommendation)
        assertEquals(2, advice.allocations.size)
        assertEquals(AssetClass.GOLD_ETF, advice.allocations[0].assetClass)
        assertEquals(70, advice.allocations[0].percentage)
        assertEquals(AssetClass.USDT_STABLECOIN, advice.allocations[1].assetClass)
        assertEquals(30, advice.allocations[1].percentage)
        assertTrue(advice.warningsFa.isNotEmpty()) // Detects elevated coin bubbles
    }

    @Test
    fun `PurchasingOptimizationEngine recommends Property for macro budget`() {
        val macroBudget = 2_000_000_000L // 2 Billion Toman
        val advice = PurchasingOptimizationEngine.computeBestBuy(
            budgetToman = macroBudget,
            usdtToman = 65_000.0,
            gold18Toman = 3_500_000.0,
            emamiCoinToman = 45_000_000.0,
            robCoinToman = 18_000_000.0,
        )

        assertEquals(AssetClass.PHYSICAL_PROPERTY, advice.primaryRecommendation)
        assertEquals(3, advice.allocations.size)
        assertEquals(AssetClass.PHYSICAL_PROPERTY, advice.allocations[0].assetClass)
        assertEquals(70, advice.allocations[0].percentage)
        assertNotNull(advice.realEstateContextFa)
    }
}
