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
    fun `calculateGoldSpendBenchmark converts expense to grams of 18k gold`() {
        val goldRateToman = 4_500_000.0 // 4.5M Toman per gram
        val expenseToman = 9_000_000L // Exactly 2 grams

        val benchmark = calculateGoldSpendBenchmark(expenseToman, goldRateToman)
        assertEquals(2.0, benchmark.grams18k, 0.001)
        assertEquals(goldRateToman, benchmark.goldRateToman, 0.001)
        assertTrue(benchmark.formattedGramsFa.contains("گرم طلا ۱۸ عیار"))

        // Fractional grams test
        val smallExpenseToman = 450_000L // 0.1 grams
        val fractionalBenchmark = calculateGoldSpendBenchmark(smallExpenseToman, goldRateToman)
        assertEquals(0.1, fractionalBenchmark.grams18k, 0.001)

        // Zero expense test
        val zeroBenchmark = calculateGoldSpendBenchmark(0L, goldRateToman)
        assertEquals(0.0, zeroBenchmark.grams18k, 0.001)

        // Fallback rate when null or non-positive
        val fallbackBenchmark = calculateGoldSpendBenchmark(9_000_000L, null)
        assertTrue(fallbackBenchmark.goldRateToman > 0.0)
        assertTrue(fallbackBenchmark.grams18k > 0.0)
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

    // ── Ticket 08: 1-Year Historical Chart & Bank Card Deck Tests ───

    @Test
    fun `MarketFluctuationEngine generates realistic 1-year historical cycle series when history is short`() {
        val nowMs = 1715000000000L
        val currentPrice = 100_000.0

        val series = MarketFluctuationEngine.computeSeries(
            assetId = "usd",
            currentPriceToman = currentPrice,
            interval = FluctuationInterval.YEAR_1,
            nowMs = nowMs,
        )

        assertEquals(FluctuationInterval.YEAR_1.pointCount, series.points.size)
        assertEquals(currentPrice, series.points.last().priceToman, 0.001)
        assertTrue("Start price should reflect 1Y macro inflation baseline", series.startPriceToman < currentPrice)
        assertTrue("Change percent should be positive over 1 year", series.changePercent > 0.0)
        assertTrue("High price should be at least current price", series.highPriceToman >= currentPrice)
        assertTrue("Low price should be positive", series.lowPriceToman > 0.0)
        assertEquals("این ماه", series.points.last().labelFa)
        assertTrue("Early points should have months ago label", series.points.first().labelFa.contains("ماه قبل"))
    }

    @Test
    fun `MarketFluctuationEngine integrates historicalSnapshots into series`() {
        val nowMs = 1715000000000L
        val today = (nowMs / (24 * 3600_000L)).toInt()
        val currentPrice = 90_000.0

        // Snapshot 100 days ago
        val pastDay = (today - 100).toLong()
        val pastPrice = 65_000.0
        val snapshots = mapOf(pastDay to pastPrice)

        val series = MarketFluctuationEngine.computeSeries(
            assetId = "usd",
            currentPriceToman = currentPrice,
            interval = FluctuationInterval.YEAR_1,
            historicalSnapshots = snapshots,
            nowMs = nowMs,
        )

        assertEquals(FluctuationInterval.YEAR_1.pointCount, series.points.size)
        assertEquals(currentPrice, series.points.last().priceToman, 0.001)
        assertTrue(series.points.any { it.priceToman in 60_000.0..70_000.0 })
    }

    @Test
    fun `formatCardPan converts card numbers to 4-block Persian groups`() {
        val pan = "6037991823456789"
        val formatted = formatCardPan(pan)
        assertEquals("۶۰۳۷  ۹۹۱۸  ۲۳۴۵  ۶۷۸۹", formatted)

        val mask = "6037 •••• •••• 1234"
        val formattedMask = formatCardPan(mask)
        assertTrue(formattedMask.contains("۶۰۳۷"))
        assertTrue(formattedMask.contains("۱۲۳۴"))
    }

    // ── Ticket 06: 50/30/20 Classification & Idle Cash Sweep Tests ──

    @Test
    fun `NecessityType classifies standard categories into Need, Want, and Savings`() {
        assertEquals(NecessityType.NEED, NecessityType.classifyCategory("cat_groceries"))
        assertEquals(NecessityType.NEED, NecessityType.classifyCategory("cat_bills"))
        assertEquals(NecessityType.NEED, NecessityType.classifyCategory("cat_transport"))
        assertEquals(NecessityType.NEED, NecessityType.classifyCategory("cat_health"))
        assertEquals(NecessityType.NEED, NecessityType.classifyCategory("cat_home"))

        assertEquals(NecessityType.WANT, NecessityType.classifyCategory("cat_dining"))
        assertEquals(NecessityType.WANT, NecessityType.classifyCategory("cat_shopping"))
        assertEquals(NecessityType.WANT, NecessityType.classifyCategory("cat_travel"))
        assertEquals(NecessityType.WANT, NecessityType.classifyCategory("cat_entertainment"))
        assertEquals(NecessityType.WANT, NecessityType.classifyCategory(null))

        assertEquals(NecessityType.SAVINGS, NecessityType.classifyCategory("cat_savings"))
        assertEquals(NecessityType.SAVINGS, NecessityType.classifyCategory("cat_loan"))
    }

    @Test
    fun `BudgetRule503020Summary computes structural expense percentages correctly`() {
        fun makeExpense(catId: String, amountRial: Long): LedgerEntry {
            return LedgerEntry(
                txn = Txn(
                    ref = "r:$catId:$amountRial",
                    srcHash = "h:$catId",
                    seq = 0,
                    at = 1000L,
                    day = 100L,
                    bank = "MELLAT",
                    accountId = "acc1",
                    direction = "out",
                    amountRial = amountRial,
                    signedRial = -amountRial,
                    balanceRial = null,
                    feeRial = null,
                    mask = "",
                    instrument = "pos",
                    merchant = "",
                    merchantNorm = "",
                    refNo = "",
                    printedAt = "",
                    channel = "pos",
                    unitPrinted = "none",
                    inferred = false,
                    parserVer = PARSER_VERSION,
                ),
                categoryId = catId,
                categoryFa = "تست",
                confidence = 100,
                needsReview = false,
                duplicate = false,
                transfer = false,
            )
        }

        val entries = listOf(
            makeExpense("cat_groceries", 50_000_000L),  // 5M Toman Need (50%)
            makeExpense("cat_dining", 30_000_000L),     // 3M Toman Want (30%)
            makeExpense("cat_savings", 20_000_000L),    // 2M Toman Savings (20%)
        )

        val summary = BudgetRule503020Summary.compute(entries)

        assertEquals(100_000_000L, summary.totalExpenseRial)
        assertEquals(10_000_000L, summary.totalExpenseToman)
        assertEquals(50_000_000L, summary.needRial)
        assertEquals(5_000_000L, summary.needToman)
        assertEquals(30_000_000L, summary.wantRial)
        assertEquals(3_000_000L, summary.wantToman)
        assertEquals(20_000_000L, summary.savingsRial)
        assertEquals(2_000_000L, summary.savingsToman)

        assertEquals(50, summary.needPercent)
        assertEquals(30, summary.wantPercent)
        assertEquals(20, summary.savingsPercent)
    }

    @Test
    fun `IdleCashAnalyzer detects excessive checking account balances and recommends sweep`() {
        val monthlyBurnToman = 20_000_000L // 20 Million monthly burn
        // 1.5x monthly burn = 30M Toman operational need
        // 60M Toman checking balance => 30M Toman excess (>5M threshold)
        val highBalanceToman = 60_000_000L
        val warning = IdleCashAnalyzer.evaluate(
            checkingBalanceToman = highBalanceToman,
            monthlyExpenseToman = monthlyBurnToman,
            annualInflationRate = 0.45,
        )

        assertTrue(warning.hasIdleCash)
        assertEquals(30_000_000L, warning.operationalNeedToman)
        assertEquals(30_000_000L, warning.excessCashToman)
        // 30M * 0.45 = 13.5M Toman annual opportunity loss
        assertEquals(13_500_000L, warning.annualOpportunityLossToman)
        assertTrue(warning.recommendationFa.contains("هشدار نقدینگی مازاد"))
        assertEquals(AssetClass.GOLD_ETF, warning.targetAsset)

        // Safe balance scenario: checking balance under operational need
        val safeBalanceToman = 25_000_000L
        val safeWarning = IdleCashAnalyzer.evaluate(
            checkingBalanceToman = safeBalanceToman,
            monthlyExpenseToman = monthlyBurnToman,
        )
        org.junit.Assert.assertFalse(safeWarning.hasIdleCash)
        assertEquals(0L, safeWarning.excessCashToman)
        assertEquals(0L, safeWarning.annualOpportunityLossToman)
    }

    // ── Ticket 14: Micro-Leak Detector & Reverse Opportunity Simulator Tests ─

    private fun makeLeakEntry(
        ref: String,
        merchant: String,
        amountRial: Long,
        direction: String = "out",
        transfer: Boolean = false,
    ) = LedgerEntry(
        txn = Txn(
            ref = ref, srcHash = "h", seq = 0, at = 19800L * 86400000L, day = 19800L,
            bank = "MELLAT", accountId = "acc", direction = direction, amountRial = amountRial,
            signedRial = if (direction == "out") -amountRial else amountRial,
            balanceRial = null, feeRial = null, mask = "", instrument = "card",
            merchant = merchant, merchantNorm = merchant, refNo = "", printedAt = "",
            channel = "pos", unitPrinted = "none", inferred = false, parserVer = PARSER_VERSION,
        ),
        categoryId = "cat_test",
        categoryFa = "آزمایشی",
        confidence = 100,
        needsReview = false,
        duplicate = false,
        transfer = transfer,
    )

    @Test
    fun `MicroLeakDetector detects micro-expenses under threshold and aggregates correctly`() {
        val entries = listOf(
            // Micro-leak 1: 25,000 Tomans = 250,000 Rial (snack)
            makeLeakEntry("l1", "سوپرمارکت", 250_000L),
            // Micro-leak 2: 12,000 Tomans = 120,000 Rial (card fee / fee)
            makeLeakEntry("l2", "کارمزد بانکی", 120_000L),
            // Micro-leak 3: 20,000 Tomans = 200,000 Rial (coffee / quick drink)
            makeLeakEntry("l3", "کافه", 200_000L),
            // Micro-leak 4: 15,000 Tomans = 150,000 Rial (micro-transit)
            makeLeakEntry("l4", "تاکسی", 150_000L),
            // Normal expense > 50,000 Tomans: 300,000 Tomans = 3,000,000 Rial
            makeLeakEntry("l5", "فروشگاه بزرگ", 3_000_000L),
            // Inbound transaction: salary (ignored)
            makeLeakEntry("l6", "واریز", 10_000_000L, direction = "in"),
            // Transfer transaction (ignored)
            makeLeakEntry("l7", "انتقال بین حسابی", 30_000L, transfer = true),
        )

        val summary = MicroLeakDetector.analyze(entries, thresholdToman = 50_000L)

        assertTrue(summary.hasLeaks)
        assertEquals(4, summary.leakCount)
        assertEquals(72_000L, summary.totalLeakToman) // 25k + 12k + 20k + 15k
        assertEquals(18_000L, summary.averageAmountToman)
        assertTrue(summary.headlineFa.contains("تراکنش خرد"))
        assertTrue(summary.recommendationFa.contains("تومان"))
    }

    @Test
    fun `MicroLeakDetector returns false when no micro-leaks exist`() {
        val entries = listOf(
            makeLeakEntry("l1", "رستوران", 1_000_000L),
        )

        val summary = MicroLeakDetector.analyze(entries, thresholdToman = 50_000L)
        assertFalse(summary.hasLeaks)
        assertEquals(0, summary.leakCount)
        assertEquals(0L, summary.totalLeakToman)
    }

    @Test
    fun `OpportunityCostSimulator calculates historical gold and usdt compound gains accurately`() {
        val initialCash = 10_000_000L // 10M Toman
        val months = 12 // 1 full year

        // Gold: 55% annual, USD: 45% annual
        val result = OpportunityCostSimulator.simulate(
            amountToman = initialCash,
            monthsAgo = months,
            goldAnnualRate = 0.55,
            usdAnnualRate = 0.45,
        )

        assertEquals(initialCash, result.initialAmountToman)
        assertEquals(months, result.monthsAgo)
        // 10M * 1.55 = 15.5M Toman gold value
        assertEquals(15_500_000L, result.goldValueToman)
        assertEquals(5_500_000L, result.goldGainToman)
        assertEquals(55.0, result.goldGainPercent, 0.01)

        // 10M * 1.45 = 14.5M Toman tether value
        assertEquals(14_500_000L, result.tetherValueToman)
        assertEquals(4_500_000L, result.tetherGainToman)
        assertEquals(45.0, result.tetherGainPercent, 0.01)

        assertTrue(result.narrativeFa.contains("طلا"))
    }

    @Test
    fun `calculateAccountRunwayDays returns correct days of liquidity`() {
        // Checking cash = 15M Toman, daily burn = 1M Toman/day => 15 days
        val runway = calculateAccountRunwayDays(
            checkingBalanceToman = 15_000_000L,
            averageDailyBurnToman = 1_000_000L,
        )
        assertEquals(15, runway)

        // Zero burn defaults to null
        val zeroBurnRunway = calculateAccountRunwayDays(
            checkingBalanceToman = 15_000_000L,
            averageDailyBurnToman = 0L,
        )
        assertNull(zeroBurnRunway)
    }

    @Test
    fun `calculateNoSpendStreak counts consecutive zero outflow days and monthly no-spend totals`() {
        val todayEpochDay = 20000L
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

        // Case 1: Active 3-day streak (no debits on today=20000, 19999, 19998; debit on 19997)
        val entriesStreak3 = listOf(
            makeTxn("tx1", day = 19997L, amount = 100_000L),
            makeTxn("tx2", day = 19995L, amount = 200_000L),
        )
        val streakResult3 = calculateNoSpendStreak(entriesStreak3, todayEpochDay)
        assertEquals(3, streakResult3.currentStreakDays)
        assertTrue(streakResult3.isTodayZeroSpend)
        assertEquals(28, streakResult3.totalNoSpendDaysPast30)
        assertTrue(streakResult3.motivationFa.contains("۳ روز"))

        // Case 2: Spent today (todayEpochDay has an outflow debit)
        val entriesSpentToday = listOf(
            makeTxn("txToday", day = todayEpochDay, amount = 50_000L, dir = "out"),
        )
        val streakResult0 = calculateNoSpendStreak(entriesSpentToday, todayEpochDay)
        assertEquals(0, streakResult0.currentStreakDays)
        assertFalse(streakResult0.isTodayZeroSpend)
        assertEquals(29, streakResult0.totalNoSpendDaysPast30)
    }

    @Test
    fun `calculateLifeEnergy uses custom hourly wage when provided`() {
        val duration = calculateLifeEnergy(
            amountToman = 600_000L,
            customHourlyWageToman = 200_000L,
        )
        assertEquals(3, duration.hours)
        assertEquals(0, duration.minutes)
        assertTrue(duration.formattedFa.contains("۳ ساعت"))

        val durationWithMinutes = calculateLifeEnergy(
            amountToman = 300_000L,
            customHourlyWageToman = 200_000L,
        )
        assertEquals(1, durationWithMinutes.hours)
        assertEquals(30, durationWithMinutes.minutes)
        assertTrue(durationWithMinutes.formattedFa.contains("۱ ساعت") && durationWithMinutes.formattedFa.contains("۳۰ دقیقه"))
    }

    @Test
    fun `computeAsset24hChange generates deterministic percentage changes matching asset type`() {
        val fixedNowMs = 1715000000000L
        val btcChange = MarketFluctuationEngine.computeAsset24hChange("btc", 65_000_000_000.0, null, fixedNowMs)
        val usdChange = MarketFluctuationEngine.computeAsset24hChange("usd", 60_000.0, null, fixedNowMs)
        val coinChange = MarketFluctuationEngine.computeAsset24hChange("coin_emami", 42_000_000.0, null, fixedNowMs)

        assertTrue(btcChange in -15.0..15.0)
        assertTrue(usdChange in -15.0..15.0)
        assertTrue(coinChange in -15.0..15.0)

        val btcChangeRepeat = MarketFluctuationEngine.computeAsset24hChange("btc", 65_000_000_000.0, null, fixedNowMs)
        assertEquals(btcChange, btcChangeRepeat, 0.001)

        val sparkline = MarketFluctuationEngine.generateSparkline("btc", 65_000_000_000.0, btcChange)
        assertEquals(7, sparkline.size)
        assertTrue(sparkline.all { it in 0.0..1.0 })
    }
}
