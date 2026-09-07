package com.doxigo.muchtoman

import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import java.util.UUID
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.roundToLong
import kotlin.math.sqrt

// ════════════════════════════════════════════════════════════════════════════════
// 1. CURRENCY LENS & PUBLIC PRIVACY MODE (Ticket 01)
// ════════════════════════════════════════════════════════════════════════════════

enum class CurrencyDenominator(
    val id: String,
    val labelFa: String,
    val unitFa: String,
    val assetKey: String,
    val decimals: Int = 2,
) {
    TOMAN("toman", "تومان", "تومان", TOMAN_ID, 0),
    GOLD_18K("gold18", "گرم طلا", "گرم", "gold18", 2),
    USD_TETHER("usd", "دلار / تتر", "دلار", "usd", 1),
    EMAMI_COIN("coin_emami", "سکه امامی", "سکه", "coin_emami", 2);
}

/**
 * Re-denominates a Toman amount into the selected currency lens.
 * Returns Pair(formattedAmount, unitString).
 */
fun formatInDenominator(
    amountToman: Double,
    denominator: CurrencyDenominator,
    effectiveRates: Map<String, Double>,
): Pair<String, String> {
    if (denominator == CurrencyDenominator.TOMAN) {
        return faCompact(amountToman) to "تومان"
    }

    val rate = when (denominator) {
        CurrencyDenominator.USD_TETHER -> effectiveRates["usdt"] ?: effectiveRates["usd"]
        CurrencyDenominator.GOLD_18K -> effectiveRates["gold18"]
        CurrencyDenominator.EMAMI_COIN -> effectiveRates["coin_emami"]
        CurrencyDenominator.TOMAN -> 1.0
    }

    if (rate == null || rate <= 0.0) {
        // Graceful fallback to Toman when asset rate is not available
        return faCompact(amountToman) to "تومان"
    }

    val converted = amountToman / rate
    val formatted = if (converted >= 1000.0) {
        faCompact(converted, denominator.decimals)
    } else {
        faDecimal(converted, denominator.decimals)
    }
    return formatted to denominator.unitFa
}

/**
 * Privacy blur modifier that applies a soft blur filter and masks accessibility descriptions.
 */
fun Modifier.privacyBlur(enabled: Boolean): Modifier = if (enabled) {
    this
        .blur(14.dp)
        .semantics { contentDescription = "محرمانه" }
} else this

// ════════════════════════════════════════════════════════════════════════════════
// 2. DAILY INFLATION CASH BURN & SAFE-TO-SPEND RUNWAY (Ticket 02)
// ════════════════════════════════════════════════════════════════════════════════

data class CashBurnResult(
    val dailyLossToman: Long,
    val monthlyLossToman: Long,
    val annualInflationRate: Double,
)

/**
 * Calculates continuous purchasing power erosion on uninvested Rial balances.
 * Default annual inflation is 45% (standard baseline for Iranian consumer basket).
 */
fun calculateDailyCashBurn(
    liquidCashToman: Long,
    annualInflationPercent: Double = 45.0,
): CashBurnResult {
    if (liquidCashToman <= 0L || annualInflationPercent <= 0.0) {
        return CashBurnResult(0L, 0L, annualInflationPercent)
    }
    val rate = annualInflationPercent / 100.0
    // Continuous daily compounding erosion
    val dailyLoss = (liquidCashToman * (rate / 365.0)).roundToLong()
    val monthlyLoss = (liquidCashToman * (rate / 12.0)).roundToLong()
    return CashBurnResult(
        dailyLossToman = max(0L, dailyLoss),
        monthlyLossToman = max(0L, monthlyLoss),
        annualInflationRate = annualInflationPercent,
    )
}

data class SafeToSpendResult(
    val safeToSpendToman: Long,
    val dailyAllowanceToman: Long,
    val daysRemaining: Int,
    val committedObligationsToman: Long,
    val isOverdrawn: Boolean,
)

/**
 * Computes the remaining Safe-to-Spend cash after deducting upcoming debt service and reservations.
 */
fun calculateSafeToSpend(
    liquidCashToman: Long,
    upcomingObligationsToman: Long,
    reservationsToman: Long = 0L,
    recurringBillsToman: Long = 0L,
    todayEpochMs: Long = System.currentTimeMillis(),
): SafeToSpendResult {
    val committed = max(0L, upcomingObligationsToman + reservationsToman + recurringBillsToman)
    val safeToSpend = liquidCashToman - committed
    val tehranDay = tehranDay(todayEpochMs)
    val jalali = jalaliOf(tehranDay)
    val monthLength = jalaliMonthLength(jalali.year, jalali.month)
    val daysRemaining = max(1, monthLength - jalali.day + 1)
    val dailyAllowance = if (safeToSpend > 0L) safeToSpend / daysRemaining else 0L

    return SafeToSpendResult(
        safeToSpendToman = safeToSpend,
        dailyAllowanceToman = dailyAllowance,
        daysRemaining = daysRemaining,
        committedObligationsToman = committed,
        isOverdrawn = safeToSpend < 0L,
    )
}

// ════════════════════════════════════════════════════════════════════════════════
// 3. GOLD INDEX & BUBBLE CALCULATOR (Ticket 03)
// ════════════════════════════════════════════════════════════════════════════════

enum class GoldCoinType(
    val id: String,
    val nameFa: String,
    val grossWeightGrams: Double,
    val purity: Double = 0.900, // 21.6 karat
    val mintFactor: Double = 1.02, // Base minting cost allowance
) {
    EMAMI("coin_emami", "تمام سکه امامی", 8.133, 0.900, 1.02),
    BAHAR("coin_bahar", "سکه بهار آزادی", 8.133, 0.900, 1.02),
    NIM("coin_nim", "نیم‌سکه بهار آزادی", 4.0665, 0.900, 1.03),
    ROB("coin_rob", "ربع‌سکه بهار آزادی", 2.03325, 0.900, 1.05),
    GERAMI("coin_gerami", "سکه یک گرمی", 1.0, 0.900, 1.08);

    /** Grams of 24k fine gold content */
    val fineGoldGrams: Double = grossWeightGrams * purity
}

data class GoldBubbleInfo(
    val coinType: GoldCoinType,
    val marketPriceToman: Long,
    val intrinsicPriceToman: Long,
    val bubbleAmountToman: Long,
    val bubblePercent: Double,
)

/**
 * Calculates intrinsic value of a gold coin based on spot 18k gold price in Toman.
 * 18k gold purity is 750/1000 (0.750).
 */
fun calculateCoinIntrinsicToman(
    coin: GoldCoinType,
    gold18RateToman: Double,
): Long {
    if (gold18RateToman <= 0.0) return 0L
    val pureGoldRateToman = gold18RateToman / 0.750
    val rawGoldValue = coin.fineGoldGrams * pureGoldRateToman
    return (rawGoldValue * coin.mintFactor).roundToLong()
}

/**
 * Computes bubble percentage: ((Market - Intrinsic) / Market) * 100
 */
fun calculateBubblePercent(marketPriceToman: Long, intrinsicPriceToman: Long): Double {
    if (marketPriceToman <= 0L) return 0.0
    val diff = (marketPriceToman - intrinsicPriceToman).toDouble()
    return (diff / marketPriceToman.toDouble()) * 100.0
}

fun evaluateCoinBubble(
    coin: GoldCoinType,
    marketPriceToman: Long,
    gold18RateToman: Double,
): GoldBubbleInfo {
    val intrinsic = calculateCoinIntrinsicToman(coin, gold18RateToman)
    val bubbleToman = max(0L, marketPriceToman - intrinsic)
    val bubblePct = calculateBubblePercent(marketPriceToman, intrinsic)
    return GoldBubbleInfo(
        coinType = coin,
        marketPriceToman = marketPriceToman,
        intrinsicPriceToman = intrinsic,
        bubbleAmountToman = bubbleToman,
        bubblePercent = bubblePct,
    )
}

// ════════════════════════════════════════════════════════════════════════════════
// 4. CARD BIN INTELLIGENCE & RECURRING SUBSCRIPTIONS (Ticket 04)
// ════════════════════════════════════════════════════════════════════════════════

data class CardBinInfo(
    val prefix: String,
    val bank: Bank,
    val bankNameFa: String,
    val shortNameFa: String,
)

val CARD_BIN_DIRECTORY: Map<String, CardBinInfo> = listOf(
    CardBinInfo("603799", Bank.MELLI, "بانک ملی ایران", "ملی"),
    CardBinInfo("610433", Bank.MELLAT, "بانک ملت", "ملت"),
    CardBinInfo("621986", Bank.SAMAN, "بانک سامان", "سامان"),
    CardBinInfo("502229", Bank.PASARGAD, "بانک پاسارگاد", "پاسارگاد"),
    CardBinInfo("627412", Bank.EGHTESAD_NOVIN, "بانک اقتصاد نوین", "اقتصاد نوین"),
    CardBinInfo("589210", Bank.SEPAH, "بانک سپه", "سپه"),
    CardBinInfo("603770", Bank.OTHER, "بانک کشاورزی", "کشاورزی"),
    CardBinInfo("628023", Bank.MASKAN, "بانک مسکن", "مسکن"),
    CardBinInfo("627760", Bank.OTHER, "پست بانک ایران", "پست بانک"),
    CardBinInfo("502908", Bank.OTHER, "بانک توسعه تعاون", "توسعه تعاون"),
    CardBinInfo("627353", Bank.TEJARAT, "بانک تجارت", "تجارت"),
    CardBinInfo("585983", Bank.TEJARAT, "بانک تجارت", "تجارت"),
    CardBinInfo("603769", Bank.SADERAT, "بانک صادرات ایران", "صادرات"),
    CardBinInfo("505416", Bank.OTHER, "بانک گردشگری", "گردشگری"),
    CardBinInfo("639607", Bank.OTHER, "بانک سرمایه", "سرمایه"),
    CardBinInfo("636214", Bank.OTHER, "بانک آینده", "آینده"),
    CardBinInfo("505785", Bank.OTHER, "بانک ایران زمین", "ایران زمین"),
    CardBinInfo("627961", Bank.OTHER, "بانک صنعت و معدن", "صنعت و معدن"),
    CardBinInfo("606373", Bank.OTHER, "بانک مهر ایران", "مهر ایران"),
    CardBinInfo("504172", Bank.RESALAT, "بانک رسالت", "رسالت"),
    CardBinInfo("639346", Bank.OTHER, "بانک سینا", "سینا"),
    CardBinInfo("504706", Bank.OTHER, "بانک شهر", "شهر"),
    CardBinInfo("622106", Bank.PARSIAN, "بانک پارسیان", "پارسیان"),
    CardBinInfo("502938", Bank.OTHER, "بانک دی", "دی"),
    CardBinInfo("589463", Bank.REFAH, "بانک رفاه کارگران", "رفاه"),
    CardBinInfo("639599", Bank.OTHER, "بانک قوامین", "قوامین"),
    CardBinInfo("505801", Bank.OTHER, "موسسه کوثر", "کوثر"),
    CardBinInfo("606256", Bank.OTHER, "موسسه اعتباری ملل", "ملل"),
    CardBinInfo("505834", Bank.OTHER, "بانک شهر", "شهر"),
).associateBy { it.prefix }

/**
 * Identifies the issuing Iranian bank from any card PAN or mask containing at least 6 digits.
 */
fun lookupBankByCardBin(cardOrMask: String): CardBinInfo? {
    val digits = cardOrMask.filter { it.isDigit() }
    if (digits.length < 6) return null
    val prefix = digits.take(6)
    return CARD_BIN_DIRECTORY[prefix]
}

data class RecurringExpense(
    val merchantKey: String,
    val merchantDisplayName: String,
    val averageAmountRial: Long,
    val count: Int,
    val lastDay: Long,
    val categoryId: String,
    val categoryFa: String,
)

/**
 * Scans transaction history to automatically cluster recurring monthly bills (internet, telecom, gym, rent).
 * Looks for outbound transactions occurring across 2+ distinct intervals with low amount variance.
 */
fun detectRecurringExpenses(entries: List<LedgerEntry>): List<RecurringExpense> {
    val outbound = entries.filter { it.txn.direction == "out" && !it.hidden && !it.duplicate && (it.txn.amountRial ?: 0L) > 0L }
    val grouped = outbound.groupBy { entry ->
        val m = entry.txn.merchantNorm.trim().ifEmpty { entry.txn.merchant.trim() }
        if (m.isNotEmpty()) m else entry.categoryFa
    }

    val result = mutableListOf<RecurringExpense>()

    for ((key, group) in grouped) {
        if (group.size < 2 || key.isBlank()) continue
        val sorted = group.sortedByDescending { it.txn.day }
        val amounts = sorted.mapNotNull { it.txn.amountRial?.toDouble() }
        if (amounts.size < 2) continue

        val mean = amounts.average()
        if (mean <= 0.0) continue
        val variance = amounts.map { (it - mean) * (it - mean) }.average()
        val stdDev = sqrt(variance)
        val cv = stdDev / mean

        // Criteria: Coefficient of variation < 0.25 (consistent amount) and spaced across time
        val days = sorted.map { it.txn.day }.distinct()
        val spanDays = days.maxOrNull()!! - days.minOrNull()!!

        if (cv <= 0.25 && spanDays >= 15) {
            val mostRecent = sorted.first()
            result.add(
                RecurringExpense(
                    merchantKey = key,
                    merchantDisplayName = mostRecent.txn.merchant.ifEmpty { key },
                    averageAmountRial = mean.roundToLong(),
                    count = sorted.size,
                    lastDay = mostRecent.txn.day,
                    categoryId = mostRecent.categoryId,
                    categoryFa = mostRecent.categoryFa,
                )
            )
        }
    }

    return result.sortedByDescending { it.averageAmountRial }
}

// ════════════════════════════════════════════════════════════════════════════════
// 5. FAILED POS TRANSACTION WATCHDOG (Ticket 05)
// ════════════════════════════════════════════════════════════════════════════════

enum class WatchdogStatus {
    PENDING,    // Within 72h window, awaiting bank reversal
    REFUNDED,   // Matching credit received
    OVERDUE,    // 72h elapsed without refund
}

data class FailedTxnWatchdog(
    val ref: String,
    val amountRial: Long,
    val bank: String,
    val mask: String,
    val atEpochMs: Long,
    val status: WatchdogStatus,
    val hoursRemaining: Int,
    val refundRef: String? = null,
)

/**
 * Identifies if a transaction represents a failed debit alert ("تراکنش ناموفق" / "مغایرت").
 */
fun isFailedDebitTxn(entry: LedgerEntry): Boolean {
    val txn = entry.txn
    val text = (txn.merchant + " " + txn.merchantNorm).lowercase()
    return text.contains("ناموفق") || text.contains("مغایرت") || text.contains("خطا")
}

/**
 * Evaluates pending failed debits against subsequent inbound credits within 72 hours.
 */
fun reconcileFailedWatchdogs(
    failedEntries: List<LedgerEntry>,
    allEntries: List<LedgerEntry>,
    nowEpochMs: Long = System.currentTimeMillis(),
): List<FailedTxnWatchdog> {
    val window72hMs = 72L * 60 * 60 * 1000
    val inbound = allEntries.filter { it.txn.direction == "in" && (it.txn.amountRial ?: 0L) > 0L }

    return failedEntries.map { failed ->
        val amount = failed.txn.amountRial ?: 0L
        val at = failed.txn.at
        val elapsed = nowEpochMs - at
        val remainingMs = window72hMs - elapsed
        val hoursLeft = max(0, (remainingMs / (1000 * 60 * 60)).toInt())

        // Look for matching refund credit (same amount and occurring after the failure)
        val matchingRefund = inbound.find { inEntry ->
            inEntry.txn.at >= at &&
                inEntry.txn.amountRial == amount &&
                (failed.txn.mask.isEmpty() || inEntry.txn.mask.isEmpty() || failed.txn.mask == inEntry.txn.mask)
        }

        val status = when {
            matchingRefund != null -> WatchdogStatus.REFUNDED
            remainingMs > 0 -> WatchdogStatus.PENDING
            else -> WatchdogStatus.OVERDUE
        }

        FailedTxnWatchdog(
            ref = failed.txn.ref,
            amountRial = amount,
            bank = failed.txn.bank,
            mask = failed.txn.mask,
            atEpochMs = at,
            status = status,
            hoursRemaining = hoursLeft,
            refundRef = matchingRefund?.txn?.ref,
        )
    }
}

// ════════════════════════════════════════════════════════════════════════════════
// 6. BEHAVIORAL LIFE-ENERGY & 48-HOUR WISHLIST (Ticket 06)
// ════════════════════════════════════════════════════════════════════════════════

data class LifeEnergyDuration(
    val hours: Int,
    val minutes: Int,
    val formattedFa: String,
)

/**
 * Translates an expenditure into hours and minutes of active labor.
 * Standard monthly working time in Iranian labor law is 176 hours (44 hours/week).
 */
fun calculateLifeEnergy(
    amountToman: Long,
    monthlyWageToman: Long = 18_000_000L,
    monthlyWorkHours: Int = 176,
): LifeEnergyDuration {
    if (amountToman <= 0L || monthlyWageToman <= 0L || monthlyWorkHours <= 0) {
        return LifeEnergyDuration(0, 0, "۰ ساعت")
    }
    val hourlyWage = monthlyWageToman.toDouble() / monthlyWorkHours.toDouble()
    val totalHours = amountToman.toDouble() / hourlyWage
    val hours = totalHours.toInt()
    val minutes = ((totalHours - hours) * 60).roundToLong().toInt()

    val text = when {
        hours == 0 && minutes > 0 -> "${faNumber(minutes.toDouble())} دقیقه کار"
        hours > 0 && minutes == 0 -> "${faNumber(hours.toDouble())} ساعت کار"
        else -> "${faNumber(hours.toDouble())} ساعت و ${faNumber(minutes.toDouble())} دقیقه کار"
    }
    return LifeEnergyDuration(hours, minutes, text)
}

enum class WishlistState {
    COOLING,    // Within 48-hour delay
    RECONSIDERED, // User chose not to purchase (money saved!)
    PURCHASED,   // Confirmed purchase after cooling off
}

data class WishlistItem(
    val id: String = UUID.randomUUID().toString(),
    val title: String,
    val amountToman: Long,
    val addedAtEpochMs: Long = System.currentTimeMillis(),
    val state: WishlistState = WishlistState.COOLING,
) {
    fun hoursRemaining(nowEpochMs: Long = System.currentTimeMillis()): Int {
        val fortyEightHoursMs = 48L * 60 * 60 * 1000
        val elapsed = nowEpochMs - addedAtEpochMs
        val remaining = fortyEightHoursMs - elapsed
        return max(0, (remaining / (1000 * 60 * 60)).toInt())
    }

    fun isCoolingPeriodElapsed(nowEpochMs: Long = System.currentTimeMillis()): Boolean =
        hoursRemaining(nowEpochMs) == 0
}

// ════════════════════════════════════════════════════════════════════════════════
// 7. WEALTH DEFENSE & PURCHASING OPTIMIZATION ENGINE
// ════════════════════════════════════════════════════════════════════════════════

enum class AssetClass(val titleFa: String, val riskLevelFa: String) {
    GOLD_ETF("صندوق طلای بورس (عیار/طلا)", "ریسک بسیار پایین - بدون حباب"),
    MELTED_GOLD_18K("طلای آب‌شده ۱۸ عیار نقدی", "ریسک پایین - بدون اجرت ساخت"),
    GOLD_COIN_BAHAR("سکه تمام بهار آزادی", "ریسک متوسط - حباب کمتر از امامی"),
    USDT_STABLECOIN("تتر (دلار دیجیتال)", "نقدینگی دلاری و حفظ قدرت خرید"),
    REAL_ESTATE_FUND("صندوق املاک و مستغلات بورس", "ریسک پایین - سپر تورمی مسکن متری"),
    PHYSICAL_PROPERTY("خرید قطعی ملک و آپارتمان", "سرمایه‌گذاری بنیادی بلندمدت")
}

data class AllocationSlice(
    val assetClass: AssetClass,
    val percentage: Int,
    val amountToman: Long,
    val rationaleFa: String,
    val actionableStepsFa: String
)

data class BestBuyAdvice(
    val userBudgetToman: Long,
    val primaryRecommendation: AssetClass,
    val headlineFa: String,
    val summaryFa: String,
    val allocations: List<AllocationSlice>,
    val warningsFa: List<String>,
    val realEstateContextFa: String?
)

object PurchasingOptimizationEngine {

    fun computeBestBuy(
        budgetToman: Long,
        usdtToman: Double = 226_700.0,
        gold18Toman: Double = 23_353_600.0,
        emamiCoinToman: Double = 229_230_000.0,
        robCoinToman: Double = 64_500_000.0
    ): BestBuyAdvice {
        val safeBudget = max(1_000_000L, budgetToman)
        val robBubble = evaluateCoinBubble(GoldCoinType.ROB, robCoinToman.roundToLong(), gold18Toman)
        val emamiBubble = evaluateCoinBubble(GoldCoinType.EMAMI, emamiCoinToman.roundToLong(), gold18Toman)

        val warnings = mutableListOf<String>()
        if (robBubble.bubblePercent > 10.0) {
            warnings.add("هشدار حباب: ربع سکه دارای ${faNumber(robBubble.bubblePercent)}٪ حباب سفته‌بازی است. خرید سکه فیزیکی توصیه نمی‌شود.")
        }
        if (emamiBubble.bubblePercent > 10.0) {
            warnings.add("حباب سکه امامی (${faNumber(emamiBubble.bubblePercent)}٪) بالاست؛ گواهی شمش یا سکه طرح قدیم بهار آزادی ترجیح دارد.")
        }

        return when {
            safeBudget < 15_000_000L -> {
                // Tier 1: Micro budget (< 15M Toman)
                val etfPart = (safeBudget * 0.70).toLong()
                val usdtPart = safeBudget - etfPart
                BestBuyAdvice(
                    userBudgetToman = safeBudget,
                    primaryRecommendation = AssetClass.GOLD_ETF,
                    headlineFa = "بهترین خرید: صندوق‌های طلای بورس (بدون حباب)",
                    summaryFa = "با بودجه کمتر از ۱۵ میلیون تومان، خرید سکه فیزیکی به دلیل حباب بالا موجب زیان فوری می‌شود. صندوق‌های طلا (مانند عیار، طلا و کهربا) امکان خرید کسری طلا را با حباب صفر و بدون اجرت فراهم می‌کنند.",
                    allocations = listOf(
                        AllocationSlice(AssetClass.GOLD_ETF, 70, etfPart, "پوشش ۱۰۰٪ تورم با شمش طلای ۹۹.۵٪", "از طریق کارگزاری‌های بورسی، نمادهای «عیار» یا «طلا» را با حداقل ۱۰۰ هزار تومان خریداری کنید."),
                        AllocationSlice(AssetClass.USDT_STABLECOIN, 30, usdtPart, "نقدینگی دلاری و مصونیت از افت ریال", "خرید تتر از صرافی‌های معتبر مانند تترلند یا والکس و نگهداری در کیف پول امن.")
                    ),
                    warningsFa = warnings,
                    realEstateContextFa = "متوسط هر متر مربع مسکن در پرند حدود ۲۵ تا ۳۰ میلیون تومان است؛ با صندوق‌های املاک بورس می‌توانید متراژ کسری پس‌انداز کنید."
                )
            }
            safeBudget < 80_000_000L -> {
                // Tier 2: Small capital (15M - 80M Toman)
                val goldPart = (safeBudget * 0.75).toLong()
                val usdtPart = safeBudget - goldPart
                BestBuyAdvice(
                    userBudgetToman = safeBudget,
                    primaryRecommendation = AssetClass.MELTED_GOLD_18K,
                    headlineFa = "بهترین خرید: طلای آب‌شده نقدی یا صندوق طلا",
                    summaryFa = "با این بودجه می‌توانید قطعات طلای آب‌شده دارای کد انگ آزمایشگاهی ری‌گیری یا واحدهای صندوق طلا تهیه کنید که برخلاف سکه و طلافروشی، اجرت ساخت و حباب ندارد.",
                    allocations = listOf(
                        AllocationSlice(AssetClass.MELTED_GOLD_18K, 75, goldPart, "حفظ مستقیم ارزش دارایی بدون اجرت و مالیات", "خرید از طلافروشی‌های دارای جواز با دریافت فاکتور رسمی و استعلام تلفنی کد انگ ری‌گیری."),
                        AllocationSlice(AssetClass.USDT_STABLECOIN, 25, usdtPart, "تثبیت دلاری سبد سرمایه", "حفظ بخش نقدی به صورت تتر برای شکار فرصت‌های خرید در بازار.")
                    ),
                    warningsFa = warnings,
                    realEstateContextFa = "این بودجه معادل پیش‌پرداخت حدود ۲ تا ۳ متر مربع مسکن در حومه تهران (پردیس یا پرند) است."
                )
            }
            safeBudget < 300_000_000L -> {
                // Tier 3: Medium capital (80M - 300M Toman)
                val baharPart = (safeBudget * 0.60).toLong()
                val etfPart = (safeBudget * 0.25).toLong()
                val usdtPart = safeBudget - baharPart - etfPart
                BestBuyAdvice(
                    userBudgetToman = safeBudget,
                    primaryRecommendation = AssetClass.GOLD_COIN_BAHAR,
                    headlineFa = "بهترین خرید: سکه تمام طرح قدیم (بهار آزادی) و گواهی شمش",
                    summaryFa = "سکه تمام بهار آزادی دقیقاً هم‌وزن سکه امامی (۸.۱۳۳ گرم عیار ۹۰۰) است اما حباب قیمتی آن بسیار کمتر است. ترکیب آن با گواهی شمش طلا بالاترین بازدهی را دارد.",
                    allocations = listOf(
                        AllocationSlice(AssetClass.GOLD_COIN_BAHAR, 60, baharPart, "طلای مسکوک قانونی با کمترین حباب بازار", "خرید سکه تمام طرح قدیم و نگهداری در صندوق امانات بانکی."),
                        AllocationSlice(AssetClass.GOLD_ETF, 25, etfPart, "نقدشوندگی فوری در کمتر از یک دقیقه", "خرید واحدهای صندوق طلا در تابلوی بورس کالا."),
                        AllocationSlice(AssetClass.USDT_STABLECOIN, 15, usdtPart, "توازن نقدینگی و دارایی‌های ارزی", "تتر جهت پوشش فوری شوک‌های قیمتی.")
                    ),
                    warningsFa = warnings,
                    realEstateContextFa = "امکان خرید سهام متری مسکن در صندوق‌های املاک و مستغلات بورس کالا."
                )
            }
            safeBudget < 1_500_000_000L -> {
                // Tier 4: Large capital (300M - 1.5B Toman)
                val goldPart = (safeBudget * 0.50).toLong()
                val reFundPart = (safeBudget * 0.25).toLong()
                val usdtPart = safeBudget - goldPart - reFundPart
                BestBuyAdvice(
                    userBudgetToman = safeBudget,
                    primaryRecommendation = AssetClass.REAL_ESTATE_FUND,
                    headlineFa = "بهترین خرید: سبد تدافعی طلا + صندوق املاک و مستغلات",
                    summaryFa = "در این محدوده سرمایه، ورود به بازار مسکن به صورت متری یا از طریق صندوق‌های ساختمانی بورس در کنار شمش طلا، بالاترین سپر تورمی را ایجاد می‌کند.",
                    allocations = listOf(
                        AllocationSlice(AssetClass.GOLD_ETF, 50, goldPart, "سپر تورمی آنی شمش طلا", "خرید گواهی شمش طلا و صندوق‌های طلا."),
                        AllocationSlice(AssetClass.REAL_ESTATE_FUND, 25, reFundPart, "بهره‌مندی از جهش قیمت مسکن در موج دوم تورم", "خرید نمادهای املاک و مستغلات در بورس."),
                        AllocationSlice(AssetClass.USDT_STABLECOIN, 25, usdtPart, "حفظ نقدینگی دلاری پرسرعت", "ذخیره در کیف پول سرد و امن.")
                    ),
                    warningsFa = warnings,
                    realEstateContextFa = "معادل خرید یک واحد آپارتمان مسکن مهر فاز ۱۱ پردیس یا پرند با وام بانکی."
                )
            }
            else -> {
                // Tier 5: Macro capital (1.5B+ Toman)
                val propertyPart = (safeBudget * 0.70).toLong()
                val goldPart = (safeBudget * 0.20).toLong()
                val usdtPart = safeBudget - propertyPart - goldPart
                BestBuyAdvice(
                    userBudgetToman = safeBudget,
                    primaryRecommendation = AssetClass.PHYSICAL_PROPERTY,
                    headlineFa = "بهترین خرید: ملک مسکونی / آپارتمان در قطب‌های رشد",
                    summaryFa = "با جهش دلار، بازار مسکن دچار تاخیر قیمتی ۳ تا ۶ ماهه می‌شود. در ماه‌های نخست جهش ارزی، قیمت دلاری هر متر مربع مسکن در کف قرار دارد و بهترین زمان خرید ملک است.",
                    allocations = listOf(
                        AllocationSlice(AssetClass.PHYSICAL_PROPERTY, 70, propertyPart, "انباشت ثروت پایدار با سند تک‌برگ", "خرید آپارتمان سنددار در پردیس، اندیشه یا مناطق مستعد تهران (منطقه ۵ یا ۲۲ چیتگر)."),
                        AllocationSlice(AssetClass.GOLD_ETF, 20, goldPart, "نقدینگی پشتیبان با بازدهی طلا", "واحدهای صندوق‌های طلای بدون حباب."),
                        AllocationSlice(AssetClass.USDT_STABLECOIN, 10, usdtPart, "تنخواه‌گردان دلاری", "تتر جهت پوشش هزینه‌های غیرمترقبه انتقال سند و بازسازی.")
                    ),
                    warningsFa = warnings,
                    realEstateContextFa = "خرید قطعی یک یا چند واحد مسکونی سنددار در پردیس، پرند، یا مناطق در حال رشد تهران."
                )
            }
        }
    }
}

