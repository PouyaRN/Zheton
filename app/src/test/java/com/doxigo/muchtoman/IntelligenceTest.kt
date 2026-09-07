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

    // ── Ticket 08: Market Fluctuation Engine & Chart Series Tests ──

    @Test
    fun `MarketFluctuationEngine computes accurate series ending at current price`() {
        val currentTether = 92_500.0
        val series = MarketFluctuationEngine.computeSeries(
            assetId = "usdt",
            currentPriceToman = currentTether,
            interval = FluctuationInterval.DAY_1,
        )

        assertEquals("usdt", series.assetId)
        assertEquals(FluctuationInterval.DAY_1, series.interval)
        assertEquals(FluctuationInterval.DAY_1.pointCount, series.points.size)
        assertEquals(currentTether, series.points.last().priceToman, 0.001)
        assertEquals(currentTether, series.currentPriceToman, 0.001)
        assertTrue(series.highPriceToman >= series.lowPriceToman)
        assertTrue(series.startPriceToman > 0.0)
    }

    @Test
    fun `MarketFluctuationEngine safely handles zero or negative price`() {
        val series = MarketFluctuationEngine.computeSeries(
            assetId = "unknown",
            currentPriceToman = 0.0,
            interval = FluctuationInterval.MONTH_1,
        )

        assertEquals(0.0, series.currentPriceToman, 0.0)
        assertEquals(1, series.points.size)
        assertEquals(0.0, series.points[0].priceToman, 0.0)
    }

    @Test
    fun `MarketFluctuationEngine generateSparkline returns 7 normalized points`() {
        val sparkline = MarketFluctuationEngine.generateSparkline(
            assetId = "coin_emami",
            currentPrice = 48_000_000.0,
        )

        assertEquals(7, sparkline.size)
        for (point in sparkline) {
            assertTrue(point in 0.0..1.0)
        }
    }

    // ── MarketRateHistoryStore Tests ─────────────────────────────────

    @Test
    fun `MarketRateHistoryStore records and retrieves points in chronological order`() {
        val store = MarketRateHistoryStore()
        val now = 1_000_000L

        store.recordRate("usd", 70_000.0, timestampMs = now)
        store.recordRate("usd", 70_500.0, timestampMs = now + 60_000L)
        store.recordRate("usd", 71_000.0, timestampMs = now + 120_000L)

        val history = store.getHistory("usd")
        assertEquals(3, history.size)
        assertEquals(70_000.0, history[0].priceToman, 0.001)
        assertEquals(71_000.0, history[2].priceToman, 0.001)

        val series = MarketFluctuationEngine.computeSeries(
            assetId = "usd",
            currentPriceToman = 71_000.0,
            interval = FluctuationInterval.DAY_1,
            historyStore = store,
        )
        assertNotNull(series)
        assertEquals(FluctuationInterval.DAY_1.pointCount, series.points.size)
        assertEquals(71_000.0, series.points.last().priceToman, 0.001)
    }

    @Test
    fun `MarketRateHistoryStore recordAllSnapshot records non-zero valid rates`() {
        val store = MarketRateHistoryStore()
        val rates = mapOf(
            "usd" to 70_000.0,
            "usdt" to 70_200.0,
            "gold18" to 0.0, // Should be ignored
        )

        store.recordAllSnapshot(rates)
        assertEquals(1, store.getHistory("usd").size)
        assertEquals(1, store.getHistory("usdt").size)
        assertEquals(0, store.getHistory("gold18").size)
    }

    // ── Timeline Spending Calculation Tests ─────────────────────────

    @Test
    fun `computeDailyOutflowPoints correctly aggregates 14-day spending in Toman`() {
        val today = 20_000L
        fun makeTxn(ref: String, day: Long, amount: Long, dir: String = "out", dup: Boolean = false) = LedgerEntry(
            txn = Txn(
                ref = ref, srcHash = "h", seq = 0, at = day * 86400000L, day = day,
                bank = "MELLAT", accountId = "acc", direction = dir, amountRial = amount,
                signedRial = if (dir == "in") amount else -amount, balanceRial = null, feeRial = null, mask = "6104",
                instrument = "card", merchant = "خرید", merchantNorm = "خرید", refNo = "", printedAt = "",
                channel = "pos", unitPrinted = "none", inferred = false, parserVer = PARSER_VERSION,
            ),
            categoryId = "cat_other", categoryFa = "سایر", confidence = 100, needsReview = false, duplicate = dup, transfer = false,
        )

        val entry1 = makeTxn("tx1", day = today, amount = 500_000L)
        val entry2 = makeTxn("tx2", day = today - 1, amount = 1_000_000L)
        val entryDup = makeTxn("tx3", day = today, amount = 900_000L, dup = true)

        val points = computeDailyOutflowPoints(listOf(entry1, entry2, entryDup), today, 14)
        assertEquals(14, points.size)
        assertEquals(today, points.last().day)
        assertEquals(50_000.0, points.last().toman, 0.001)
        assertEquals(today - 1, points[12].day)
        assertEquals(100_000.0, points[12].toman, 0.001)
        assertEquals(0.0, points[0].toman, 0.001)
    }

    @Test
    fun `PurchasingOptimizationEngine directs micro budget to Gold ETF without bubble`() {
        val advice = PurchasingOptimizationEngine.computeBestBuyAdvice(
            budgetToman = 10_000_000L,
            halfCoinBubblePercent = 28.0,
            quarterCoinBubblePercent = 38.0,
        )
        assertEquals(AssetClass.GOLD_ETF, advice.primaryRecommendation)
        assertEquals(2, advice.allocations.size)
        assertEquals(AssetClass.GOLD_ETF, advice.allocations[0].asset)
        assertEquals(70, advice.allocations[0].percentage)
        assertEquals(7_000_000L, advice.allocations[0].amountToman)
        assertEquals(AssetClass.USDT_STABLECOIN, advice.allocations[1].asset)
        assertEquals(30, advice.allocations[1].percentage)
        assertEquals(3_000_000L, advice.allocations[1].amountToman)
        assertEquals(2, advice.warningsFa.size)
    }

    @Test
    fun `PurchasingOptimizationEngine directs medium budget to Bahar Azadi coin and Gold ETF`() {
        val advice = PurchasingOptimizationEngine.computeBestBuyAdvice(
            budgetToman = 100_000_000L,
            halfCoinBubblePercent = 5.0,
            quarterCoinBubblePercent = 8.0,
        )
        assertEquals(AssetClass.GOLD_COIN_BAHAR, advice.primaryRecommendation)
        assertEquals(3, advice.allocations.size)
        assertEquals(AssetClass.GOLD_COIN_BAHAR, advice.allocations[0].asset)
        assertEquals(60, advice.allocations[0].percentage)
        assertEquals(60_000_000L, advice.allocations[0].amountToman)
        assertEquals(0, advice.warningsFa.size)
    }

    @Test
    fun `PurchasingOptimizationEngine directs macro budget to physical property`() {
        val advice = PurchasingOptimizationEngine.computeBestBuyAdvice(
            budgetToman = 2_000_000_000L,
        )
        assertEquals(AssetClass.PHYSICAL_PROPERTY, advice.primaryRecommendation)
        assertEquals(3, advice.allocations.size)
        assertEquals(AssetClass.PHYSICAL_PROPERTY, advice.allocations[0].asset)
        assertEquals(70, advice.allocations[0].percentage)
        assertEquals(1_400_000_000L, advice.allocations[0].amountToman)
    }

    @Test
    fun `PurchasingOptimizationEngine computes wealth defense index and daily bleed`() {
        // Fully hedged
        val protected = PurchasingOptimizationEngine.computeWealthDefenseIndex(
            unhedgedToman = 0L,
            hedgedToman = 500_000_000L,
            annualInflationRate = 0.45,
        )
        assertEquals(100, protected.score)
        assertEquals(0L, protected.dailyBleedToman)
        assertEquals(0L, protected.annualLossToman)

        // Fully unhedged (sitting in bank Rial account)
        val vulnerable = PurchasingOptimizationEngine.computeWealthDefenseIndex(
            unhedgedToman = 100_000_000L,
            hedgedToman = 0L,
            annualInflationRate = 0.45,
        )
        assertEquals(0, vulnerable.score)
        org.junit.Assert.assertTrue(vulnerable.dailyBleedToman > 0L)
        assertEquals(45_000_000L, vulnerable.annualLossToman)

        // Balanced 60% hedged
        val balanced = PurchasingOptimizationEngine.computeWealthDefenseIndex(
            unhedgedToman = 40_000_000L,
            hedgedToman = 60_000_000L,
            annualInflationRate = 0.45,
        )
        assertEquals(60, balanced.score)
        assertEquals(0.6, balanced.hedgedRatio, 0.001)
    }
}
