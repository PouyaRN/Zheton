package com.doxigo.muchtoman

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.GlanceTheme
import androidx.glance.action.actionStartActivity
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.SizeMode
import androidx.glance.appwidget.action.actionStartActivity
import androidx.glance.appwidget.cornerRadius
import androidx.glance.appwidget.provideContent
import androidx.glance.background
import androidx.glance.layout.Alignment
import androidx.glance.layout.Box
import androidx.glance.layout.Column
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.layout.width
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextAlign
import androidx.glance.text.TextStyle
import androidx.glance.unit.ColorProvider
import androidx.compose.ui.unit.DpSize
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.math.abs

data class GlanceWidgetState(
    val netWorthToman: Double,
    val isMasked: Boolean,
    val deltaPercent: Double?,
    val deltaSince: String,
    val todaySpendToman: Long,
    val goldRateToman: Double?,
    val tetherRateToman: Double?,
    val nextCommitmentText: String?,
)

suspend fun loadGlanceWidgetData(context: Context): GlanceWidgetState = withContext(Dispatchers.IO) {
    val store = Store(context)
    val rates = store.cachedRates
    val history = store.history
    val effective = effectiveRates(rates, store.overrides, store.cachedStocks)
    val total = computeTotals(
        listHoldings(store.holdings, store.smsEnabled, store.bankAccounts, store.disabledBanks),
        effective,
    ).toman

    val now = System.currentTimeMillis()
    val today = now / DAY_MS
    val baseDay = history.keys.filter { it < today }.maxOrNull()
    val base = baseDay?.let { history.getValue(it) }
    val delta = base?.let { total - it }
    val deltaPercent = base?.takeIf { it > 0 }?.let { (total - it) / it * 100 }
    val deltaSince = when {
        baseDay == null -> ""
        today - baseDay == 1L -> "از دیروز"
        else -> "${faNumber((today - baseDay).toDouble())} روز پیش"
    }

    // Today's spend from derived database
    var todaySpendToman = 0L
    runCatching {
        val derived = DerivedDb.get(context)
        val todayTehran = tehranDay(now)
        val txns = derived.txn().newest(200)
        val rial = txns.filter { it.day == todayTehran && it.direction == "out" }
            .sumOf { abs(it.signedRial ?: 0L) }
        todaySpendToman = rial / 10L
    }

    // Nearest upcoming commitment / installment
    val nextCommitment = store.commitments
        .filter { !it.isSettled && it.nextDueTimestamp > 0L }
        .minByOrNull { it.nextDueTimestamp }

    val nextCommitmentText = nextCommitment?.let {
        val days = it.nextDueDays
        val dueLabel = when (days) {
            0 -> "سررسید امروز"
            1 -> "سررسید فردا"
            else -> "${faNumber(days.toDouble())} روز مانده"
        }
        val providerName = it.provider.ifBlank { it.title }
        "$providerName: $dueLabel (${faCompact(it.remainingAmountToman.toDouble())} ت)"
    }

    val goldRate = effective["gold18"] ?: effective["geram18"]
    val tetherRate = effective["usdt"] ?: effective["usd"]

    GlanceWidgetState(
        netWorthToman = total,
        isMasked = store.widgetLock,
        deltaPercent = deltaPercent,
        deltaSince = deltaSince,
        todaySpendToman = todaySpendToman,
        goldRateToman = goldRate,
        tetherRateToman = tetherRate,
        nextCommitmentText = nextCommitmentText,
    )
}

/**
 * Modern Jetpack Glance interactive home screen widget for ChandToman.
 * Provides live Net Worth, daily spend, gold/tether rates, installment countdowns,
 * and 1-tap quick transaction logging.
 */
class ChandTomanGlanceWidget : GlanceAppWidget() {

    override val sizeMode: SizeMode = SizeMode.Exact

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val state = loadGlanceWidgetData(context)
        provideContent {
            GlanceTheme {
                ChandTomanWidgetBody(context, state)
            }
        }
    }
}

@Composable
private fun ChandTomanWidgetBody(context: Context, state: GlanceWidgetState) {
    val darkSurface = Color(0xFF161410)
    val cardSurface = Color(0xFF221F19)
    val goldColor = Color(0xFFF7C948)
    val mintColor = Color(0xFF3BE0A8)
    val warnColor = Color(0xFFFFB4A6)
    val mutedColor = Color(0xFF93B7B0)
    val textWhite = Color(0xFFFAF9F6)

    val mainComponent = ComponentName(context, MainActivity::class.java)
    val quickAddComponent = ComponentName(context, QuickAddActivity::class.java)

    Box(
        modifier = GlanceModifier
            .fillMaxSize()
            .background(darkSurface)
            .cornerRadius(20.dp)
            .padding(12.dp)
            .clickable(actionStartActivity(mainComponent)),
    ) {
        Column(
            modifier = GlanceModifier.fillMaxSize(),
        ) {
            // Header: App title + Market Rates Ticker
            Row(
                modifier = GlanceModifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = "چند تومان",
                    style = TextStyle(
                        color = ColorProvider(goldColor),
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold,
                    ),
                )

                Spacer(GlanceModifier.defaultWeight())

                // Gold & Tether rates chips
                if (state.goldRateToman != null && state.goldRateToman > 0) {
                    Text(
                        text = "طلا: ${faCompact(state.goldRateToman)}",
                        style = TextStyle(
                            color = ColorProvider(mutedColor),
                            fontSize = 10.sp,
                        ),
                    )
                }

                if (state.tetherRateToman != null && state.tetherRateToman > 0) {
                    Spacer(GlanceModifier.width(6.dp))
                    Text(
                        text = "تتر: ${faCompact(state.tetherRateToman)}",
                        style = TextStyle(
                            color = ColorProvider(mutedColor),
                            fontSize = 10.sp,
                        ),
                    )
                }
            }

            Spacer(GlanceModifier.height(8.dp))

            // Main Total & Change Row
            Row(
                modifier = GlanceModifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(modifier = GlanceModifier.defaultWeight()) {
                    Text(
                        text = "جمع دارایی خالص",
                        style = TextStyle(
                            color = ColorProvider(mutedColor),
                            fontSize = 11.sp,
                        ),
                    )

                    val totalStr = if (state.isMasked) {
                        "٭٭٭"
                    } else {
                        "${faCompact(state.netWorthToman, 2)} تومان"
                    }

                    Text(
                        text = totalStr,
                        style = TextStyle(
                            color = ColorProvider(if (state.isMasked) goldColor else textWhite),
                            fontSize = 20.sp,
                            fontWeight = FontWeight.Bold,
                        ),
                    )
                }

                if (!state.isMasked && state.deltaPercent != null) {
                    val isPositive = state.deltaPercent >= 0.0
                    val deltaLabel = "${if (isPositive) "+" else ""}${faDecimal(abs(state.deltaPercent), 1)}٪ ${state.deltaSince}".trim()
                    Box(
                        modifier = GlanceModifier
                            .background(if (isPositive) Color(0x283BE0A8) else Color(0x28FFB4A6))
                            .cornerRadius(10.dp)
                            .padding(horizontal = 8.dp, vertical = 4.dp),
                    ) {
                        Text(
                            text = deltaLabel,
                            style = TextStyle(
                                color = ColorProvider(if (isPositive) mintColor else warnColor),
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Medium,
                            ),
                        )
                    }
                }
            }

            Spacer(GlanceModifier.height(8.dp))

            // Info Card: Today's Spend & Installment Countdown
            Box(
                modifier = GlanceModifier
                    .fillMaxWidth()
                    .background(cardSurface)
                    .cornerRadius(12.dp)
                    .padding(horizontal = 10.dp, vertical = 6.dp),
            ) {
                Row(
                    modifier = GlanceModifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    val spendLabel = if (state.todaySpendToman > 0) {
                        "خرج امروز: ${faCompact(state.todaySpendToman.toDouble())} ت"
                    } else {
                        "خرج امروز: صفر"
                    }

                    Text(
                        text = spendLabel,
                        style = TextStyle(
                            color = ColorProvider(if (state.todaySpendToman > 0) Color(0xFFFF9E80) else mintColor),
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Medium,
                        ),
                    )

                    Spacer(GlanceModifier.defaultWeight())

                    if (state.nextCommitmentText != null) {
                        Text(
                            text = state.nextCommitmentText,
                            style = TextStyle(
                                color = ColorProvider(goldColor),
                                fontSize = 10.sp,
                            ),
                        )
                    }
                }
            }

            Spacer(GlanceModifier.height(8.dp))

            // 1-Tap Quick Action Row
            Box(
                modifier = GlanceModifier
                    .fillMaxWidth()
                    .background(Color(0xFF322A14))
                    .cornerRadius(12.dp)
                    .clickable(actionStartActivity(quickAddComponent))
                    .padding(vertical = 6.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = "＋ ثبت سریع هزینه یا درآمد",
                    style = TextStyle(
                        color = ColorProvider(goldColor),
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        textAlign = TextAlign.Center,
                    ),
                )
            }
        }
    }
}

/** Receiver for Android OS home screen widget updates */
class ChandTomanGlanceWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = ChandTomanGlanceWidget()
}

/** A single live market ticker line rendered inside a glance widget. */
data class MarketTicker(
    val key: String,
    val label: String,
    val rateToman: Double,
    val unit: String = "تومان",
)

/**
 * The keys a market widget can pin, in their display order, with their Persian labels.
 * Backed by [effectiveRates] keys so a widget selection is just a filter over one snapshot.
 */
val MARKET_TICKER_CATALOG: List<Pair<String, String>> = listOf(
    "gold18" to "گرم طلا",
    "coin_emami" to "سکه امامی",
    "coin_bahar" to "سکه بهار",
    "coin_nim" to "نیم سکه",
    "coin_rob" to "ربع سکه",
    "usd" to "دلار",
    "usdt" to "تتر",
    "eur" to "یورو",
    "dirham" to "درهم",
    "geram18" to "طلای دست‌دوم",
)

/** Filters a rates snapshot down to the tickers she pinned, in catalog order. */
fun marketTickers(effective: Map<String, Double>, pinned: Set<String>): List<MarketTicker> {
    return MARKET_TICKER_CATALOG
        .filter { (key, _) -> key in pinned }
        .mapNotNull { (key, label) ->
            effective[key]?.takeIf { it > 0 }?.let { MarketTicker(key, label, it) }
        }
}

/** Loads rates and returns the tickers for the given pinned keys. */
suspend fun loadMarketTickers(context: Context, pinned: Set<String>): List<MarketTicker> =
    withContext(Dispatchers.IO) {
        val store = Store(context)
        val effective = effectiveRates(store.cachedRates, store.overrides, store.cachedStocks)
        marketTickers(effective, pinned)
    }

/**
 * Multi-asset market ticker widget (2x1 / 4x1 / 4x2 responsive). She picks which rates to
 * pin; unpinned keys never render. Tapping opens the app's market view.
 */
class MarketTickerWidget(private val pinned: Set<String> = setOf("gold18", "usd", "usdt")) : GlanceAppWidget() {

    override val sizeMode: SizeMode = SizeMode.Responsive(
        setOf(DpSize(110.dp, 40.dp), DpSize(250.dp, 40.dp), DpSize(250.dp, 110.dp)),
    )

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val tickers = loadMarketTickers(context, pinned)
        val updatedAt = Store(context).cachedRates.updatedAt
        provideContent {
            GlanceTheme {
                MarketTickerBody(tickers, updatedAt)
            }
        }
    }
}

@Composable
private fun MarketTickerBody(tickers: List<MarketTicker>, updatedAt: Long) {
    val darkSurface = Color(0xFF161410)
    val goldColor = Color(0xFFF7C948)
    val mintColor = Color(0xFF3BE0A8)
    val mutedColor = Color(0xFF93B7B0)
    val textWhite = Color(0xFFFAF9F6)

    Box(
        modifier = GlanceModifier
            .fillMaxSize()
            .background(darkSurface)
            .cornerRadius(16.dp)
            .padding(8.dp)
            .clickable(actionStartActivity<MainActivity>()),
    ) {
        when {
            tickers.isEmpty() -> Text(
                text = "نرخ‌ها در دسترس نیست",
                style = TextStyle(color = ColorProvider(mutedColor), fontSize = 11.sp),
                modifier = GlanceModifier.fillMaxSize(),
            )
            else -> Column(modifier = GlanceModifier.fillMaxSize()) {
                Row(
                    modifier = GlanceModifier.fillMaxWidth().defaultWeight(),
                    horizontalAlignment = androidx.glance.layout.Alignment.Horizontal.CenterHorizontally,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    tickers.forEachIndexed { index, ticker ->
                        if (index > 0) Spacer(GlanceModifier.width(10.dp))
                        Column(horizontalAlignment = androidx.glance.layout.Alignment.Horizontal.CenterHorizontally) {
                            Text(
                                text = ticker.label,
                                style = TextStyle(color = ColorProvider(mutedColor), fontSize = 9.sp),
                                maxLines = 1,
                            )
                            Spacer(GlanceModifier.height(1.dp))
                            Text(
                                text = "${faCompact(ticker.rateToman)} ${ticker.unit}",
                                style = TextStyle(
                                    color = ColorProvider(if (ticker.key == "gold18") goldColor else mintColor),
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold,
                                ),
                                maxLines = 1,
                            )
                        }
                    }
                }
                if (updatedAt > 0) {
                    Spacer(GlanceModifier.height(2.dp))
                    Text(
                        text = "بروزرسانی: ${faAgo(updatedAt, System.currentTimeMillis())}",
                        style = TextStyle(color = ColorProvider(mutedColor), fontSize = 8.sp),
                        maxLines = 1,
                    )
                }
            }
        }
    }
}

/** Receiver for the multi-asset market ticker widget. */
class MarketTickerWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = MarketTickerWidget()
}

/**
 * Single-asset mini ticker widget (1x1). She pins exactly one rate — gold or dollar is the
 * usual pair — and the widget shows only that price at glance size.
 */
class SingleTickerMiniWidget(private val key: String = "gold18") : GlanceAppWidget() {

    override val sizeMode: SizeMode = SizeMode.Exact

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val tickers = loadMarketTickers(context, setOf(key))
        provideContent {
            GlanceTheme {
                SingleTickerMiniBody(tickers.firstOrNull())
            }
        }
    }
}

@Composable
private fun SingleTickerMiniBody(ticker: MarketTicker?) {
    val darkSurface = Color(0xFF161410)
    val goldColor = Color(0xFFF7C948)
    val mintColor = Color(0xFF3BE0A8)
    val mutedColor = Color(0xFF93B7B0)

    Box(
        modifier = GlanceModifier
            .fillMaxSize()
            .background(darkSurface)
            .cornerRadius(16.dp)
            .padding(6.dp)
            .clickable(actionStartActivity<MainActivity>()),
        contentAlignment = Alignment.Center,
    ) {
        if (ticker == null) {
            Text(
                text = "—",
                style = TextStyle(color = ColorProvider(mutedColor), fontSize = 12.sp),
            )
        } else {
            Column(horizontalAlignment = androidx.glance.layout.Alignment.Horizontal.CenterHorizontally) {
                Text(
                    text = ticker.label,
                    style = TextStyle(color = ColorProvider(mutedColor), fontSize = 8.sp),
                    maxLines = 1,
                )
                Spacer(GlanceModifier.height(1.dp))
                Text(
                    text = faCompact(ticker.rateToman),
                    style = TextStyle(
                        color = ColorProvider(if (ticker.key == "gold18") goldColor else mintColor),
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold,
                    ),
                    maxLines = 1,
                )
            }
        }
    }
}

/** Receiver for the single-asset mini ticker widget. */
class SingleTickerMiniWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = SingleTickerMiniWidget()
}

/** Everything the safe-spend & liquid widgets render, already computed off the store. */
data class RunwayWidgetState(
    val dailyAllowanceToman: Long,
    val safeToSpendToman: Long,
    val daysRemaining: Int,
    val monthProgressPercent: Int,
    val isOverdrawn: Boolean,
    val isMasked: Boolean,
    val liquidCashToman: Long,
    val goldGrams: Double?,
    val dailyBleedToman: Long,
)

/** Pure projection: from store numbers to what the runway widgets display. */
fun runwayWidgetState(
    liquidCashToman: Long,
    upcomingObligationsToman: Long,
    reservationsToman: Long,
    goldRateToman: Double?,
    isMasked: Boolean,
    todayEpochMs: Long = System.currentTimeMillis(),
): RunwayWidgetState {
    val safe = calculateSafeToSpend(
        liquidCashToman = liquidCashToman,
        upcomingObligationsToman = upcomingObligationsToman,
        reservationsToman = reservationsToman,
        todayEpochMs = todayEpochMs,
    )
    val tehranDayValue = tehranDay(todayEpochMs)
    val jalali = jalaliOf(tehranDayValue)
    val monthLength = jalaliMonthLength(jalali.year, jalali.month)
    // Budget consumption vs the calendar, not of it. Baseline: the bar tracks days gone by,
    // because a plan burning exactly its daily allowance is on pace. Commitments drag
    // today's allowance below what a commitment-free month would allow, and that drag
    // pushes the bar past the calendar mark; overdrawn pins it at 100.
    val daysElapsed = (jalali.day - 1).coerceAtLeast(0)
    val calendarShare = daysElapsed * 100 / monthLength
    val progress = when {
        safe.isOverdrawn -> 100
        safe.dailyAllowanceToman <= 0L -> calendarShare
        else -> {
            val commitmentFreeAllowance = calculateSafeToSpend(
                liquidCashToman = liquidCashToman,
                upcomingObligationsToman = 0L,
                reservationsToman = 0L,
                todayEpochMs = todayEpochMs,
            ).dailyAllowanceToman
            val drag = if (commitmentFreeAllowance > 0L) {
                1.0 - safe.dailyAllowanceToman.toDouble() / commitmentFreeAllowance.toDouble()
            } else 0.0
            (calendarShare + drag * 100.0 * (1.0 - calendarShare / 100.0)).toInt().coerceIn(0, 100)
        }
    }
    val bleed = calculateDailyCashBurn(liquidCashToman).dailyLossToman
    return RunwayWidgetState(
        dailyAllowanceToman = safe.dailyAllowanceToman,
        safeToSpendToman = safe.safeToSpendToman,
        daysRemaining = safe.daysRemaining,
        monthProgressPercent = progress,
        isOverdrawn = safe.isOverdrawn,
        isMasked = isMasked,
        liquidCashToman = liquidCashToman,
        goldGrams = goldRateToman?.takeIf { it > 0 }?.let { liquidCashToman.toDouble() / it },
        dailyBleedToman = bleed,
    )
}

/** Loads [RunwayWidgetState] from the store on a background dispatcher. */
suspend fun loadRunwayWidgetState(context: Context): RunwayWidgetState = withContext(Dispatchers.IO) {
    val store = Store(context)
    val effective = effectiveRates(store.cachedRates, store.overrides, store.cachedStocks)
    val goldRate = effective["gold18"] ?: effective["geram18"]
    val holdings = listHoldings(store.holdings, store.smsEnabled, store.bankAccounts, store.disabledBanks)
    val liquidCash = computeTotals(holdings, effective).toman.toLong()
    val obligations = store.commitments
        .filter { !it.isSettled }
        .sumOf { it.remainingAmountToman }
    runwayWidgetState(
        liquidCashToman = liquidCash,
        upcomingObligationsToman = obligations,
        reservationsToman = 0L,
        goldRateToman = goldRate,
        isMasked = store.widgetLock,
    )
}

/**
 * Daily Safe Spend widget (2x1 / 2x2). Shows today's allowed burn, the month's progress
 * bar, and how many days remain in the financial cycle.
 */
class SafeSpendGlanceWidget : GlanceAppWidget() {

    override val sizeMode: SizeMode = SizeMode.Responsive(
        setOf(DpSize(180.dp, 40.dp), DpSize(180.dp, 110.dp)),
    )

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val state = loadRunwayWidgetState(context)
        provideContent {
            GlanceTheme {
                SafeSpendBody(state)
            }
        }
    }
}

@Composable
private fun SafeSpendBody(state: RunwayWidgetState) {
    val darkSurface = Color(0xFF161410)
    val goldColor = Color(0xFFF7C948)
    val mintColor = Color(0xFF3BE0A8)
    val warnColor = Color(0xFFFF9E80)
    val mutedColor = Color(0xFF93B7B0)
    val textWhite = Color(0xFFFAF9F6)

    Box(
        modifier = GlanceModifier
            .fillMaxSize()
            .background(darkSurface)
            .cornerRadius(16.dp)
            .padding(8.dp)
            .clickable(actionStartActivity<MainActivity>()),
    ) {
        Column(modifier = GlanceModifier.fillMaxSize()) {
            Row(modifier = GlanceModifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = "خرج امن امروز",
                    style = TextStyle(color = ColorProvider(mutedColor), fontSize = 10.sp),
                )
                Spacer(GlanceModifier.defaultWeight())
                Text(
                    text = "${faNumber(state.daysRemaining.toDouble())} روز تا پایان چرخه",
                    style = TextStyle(color = ColorProvider(mutedColor), fontSize = 9.sp),
                    maxLines = 1,
                )
            }
            Spacer(GlanceModifier.height(2.dp))
            Text(
                text = if (state.isMasked) "٭٭٭" else {
                    if (state.isOverdrawn) "کسری بودجه!" else "${faCompact(state.dailyAllowanceToman.toDouble())} تومان"
                },
                style = TextStyle(
                    color = ColorProvider(
                        when {
                            state.isMasked -> goldColor
                            state.isOverdrawn -> warnColor
                            else -> textWhite
                        },
                    ),
                    fontSize = 17.sp,
                    fontWeight = FontWeight.Bold,
                ),
                maxLines = 1,
            )
            Spacer(GlanceModifier.height(4.dp))
            // Month progress bar: filled portion in mint (warn when overdrawn), track dim.
            // Glance has no fractional fill, so widths come from a coarse 10-segment bar.
            val fill = state.monthProgressPercent.coerceIn(0, 100)
            Row(
                modifier = GlanceModifier.fillMaxWidth().height(4.dp).background(Color(0xFF2A271F)),
            ) {
                val filled = (fill / 10).coerceIn(0, 10)
                repeat(filled) {
                    Spacer(
                        GlanceModifier.defaultWeight().height(4.dp)
                            .background(if (state.isOverdrawn) warnColor else mintColor),
                    )
                }
                repeat(10 - filled) {
                    Spacer(
                        GlanceModifier.defaultWeight().height(4.dp)
                            .background(Color(0xFF2A271F)),
                    )
                }
            }
            Spacer(GlanceModifier.height(3.dp))
            Text(
                text = if (state.isMasked) "پنهان" else "بودجه مصرف‌شده: ${faNumber(fill.toDouble())}٪",
                style = TextStyle(color = ColorProvider(mutedColor), fontSize = 8.sp),
                maxLines = 1,
            )
        }
    }
}

/** Receiver for the Daily Safe Spend widget. */
class SafeSpendGlanceWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = SafeSpendGlanceWidget()
}

/**
 * Liquid Balance & Gold Peg widget (2x1 / 2x2). Operational cash on the left, its
 * 18K-gold gram equivalent and daily inflation bleed on the right.
 */
class LiquidGoldGlanceWidget : GlanceAppWidget() {

    override val sizeMode: SizeMode = SizeMode.Responsive(
        setOf(DpSize(180.dp, 40.dp), DpSize(180.dp, 110.dp)),
    )

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val state = loadRunwayWidgetState(context)
        provideContent {
            GlanceTheme {
                LiquidGoldBody(state)
            }
        }
    }
}

@Composable
private fun LiquidGoldBody(state: RunwayWidgetState) {
    val darkSurface = Color(0xFF161410)
    val goldColor = Color(0xFFF7C948)
    val warnColor = Color(0xFFFF9E80)
    val mutedColor = Color(0xFF93B7B0)
    val textWhite = Color(0xFFFAF9F6)

    Box(
        modifier = GlanceModifier
            .fillMaxSize()
            .background(darkSurface)
            .cornerRadius(16.dp)
            .padding(8.dp)
            .clickable(actionStartActivity<MainActivity>()),
    ) {
        Column(modifier = GlanceModifier.fillMaxSize()) {
            Text(
                text = "موجودی نقد عملیاتی",
                style = TextStyle(color = ColorProvider(mutedColor), fontSize = 10.sp),
                maxLines = 1,
            )
            Spacer(GlanceModifier.height(2.dp))
            Text(
                text = if (state.isMasked) "٭٭٭" else "${faCompact(state.liquidCashToman.toDouble())} تومان",
                style = TextStyle(
                    color = ColorProvider(if (state.isMasked) goldColor else textWhite),
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold,
                ),
                maxLines = 1,
            )
            Spacer(GlanceModifier.height(4.dp))
            Row(modifier = GlanceModifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                val goldText = state.goldGrams?.let {
                    if (state.isMasked) "⋆⋆ گرم" else "معادل ${faDecimal(it, 2)} گرم طلای ۱۸"
                } ?: "نرخ طلا در دسترس نیست"
                Text(
                    text = goldText,
                    style = TextStyle(color = ColorProvider(goldColor), fontSize = 10.sp),
                    maxLines = 1,
                    modifier = GlanceModifier.defaultWeight(),
                )
                if (state.dailyBleedToman > 0 && !state.isMasked) {
                    Spacer(GlanceModifier.width(6.dp))
                    Text(
                        text = "خونریزی روزانه: ${faCompact(state.dailyBleedToman.toDouble())}− تومان",
                        style = TextStyle(color = ColorProvider(warnColor), fontSize = 9.sp),
                        maxLines = 1,
                    )
                }
            }
        }
    }
}

/** Receiver for the Liquid Balance & Gold Peg widget. */
class LiquidGoldGlanceWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = LiquidGoldGlanceWidget()
}

/** One launcher shortcut on the Quick Expense widget: a category chip. */
data class QuickAddChip(
    val label: String,
    val categoryId: String,
)

/** The chips the Quick Expense widget shows, in display order. */
val QUICK_ADD_CHIPS: List<QuickAddChip> = listOf(
    QuickAddChip("خواربار", "cat_groceries"),
    QuickAddChip("کافه", "cat_dining"),
    QuickAddChip("بنزین", "cat_fuel"),
    QuickAddChip("حمل", "cat_transport"),
    QuickAddChip("قبض", "cat_bills"),
    QuickAddChip("سایر", "cat_uncategorised"),
)

/**
 * Quick Expense Add widget (1x1 / 2x1). Each chip launches the quick-add dialog with that
 * category already picked, so logging a routine expense is tap → amount → save.
 */
class QuickAddGlanceWidget : GlanceAppWidget() {

    override val sizeMode: SizeMode = SizeMode.Responsive(
        setOf(DpSize(40.dp, 40.dp), DpSize(180.dp, 40.dp)),
    )

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        provideContent {
            GlanceTheme {
                QuickAddBody(context)
            }
        }
    }
}

@Composable
private fun QuickAddBody(context: Context) {
    val goldColor = Color(0xFFF7C948)
    val darkSurface = Color(0xFF161410)
    val chipSurface = Color(0xFF322A14)

    Box(
        modifier = GlanceModifier
            .fillMaxSize()
            .background(darkSurface)
            .cornerRadius(16.dp)
            .padding(6.dp),
        contentAlignment = Alignment.Center,
    ) {
        Row(
            modifier = GlanceModifier.fillMaxSize(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalAlignment = androidx.glance.layout.Alignment.Horizontal.CenterHorizontally,
        ) {
            QUICK_ADD_CHIPS.forEach { chip ->
                Box(
                    modifier = GlanceModifier
                        .background(chipSurface)
                        .cornerRadius(10.dp)
                        .padding(horizontal = 8.dp, vertical = 4.dp)
                        .clickable(
                            actionStartActivity(
                                Intent(context, QuickAddActivity::class.java).apply {
                                    putExtra("com.doxigo.muchtoman.extra.CATEGORY_ID", chip.categoryId)
                                },
                            ),
                        ),
                ) {
                    Text(
                        text = "＋${chip.label}",
                        style = TextStyle(
                            color = ColorProvider(goldColor),
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                        ),
                        maxLines = 1,
                    )
                }
                Spacer(GlanceModifier.width(4.dp))
            }
        }
    }
}

/** Receiver for the Quick Expense Add widget. */
class QuickAddGlanceWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = QuickAddGlanceWidget()
}

/** One card row in the Quick Cards launcher widget. */
data class QuickCardRow(
    val label: String,
    val bankFa: String,
    val maskedPan: String,
    val fullPanDigits: String,
    val sheba: String,
)

/** Projects stored accounts into the widget's card rows; masked when the widget is locked. */
fun quickCardRows(accounts: List<BankAccount>, isMasked: Boolean): List<QuickCardRow> {
    if (isMasked) return emptyList()
    return accounts.map { acc ->
        val digits = toAsciiDigits(acc.fullPan.ifBlank { acc.mask }).filter { it.isDigit() }
        val display = if (digits.length == 16) {
            val bin = digits.take(6)
            val tail = digits.takeLast(4)
            "‎$bin••••••$tail"
        } else {
            acc.mask
        }
        QuickCardRow(
            label = acc.nickname.ifBlank { acc.bankFa },
            bankFa = acc.bankFa,
            maskedPan = display,
            fullPanDigits = if (digits.length == 16) digits else "",
            sheba = acc.sheba,
        )
    }
}

/** Loads the card rows from the store on a background dispatcher. */
suspend fun loadQuickCardRows(context: Context): List<QuickCardRow> = withContext(Dispatchers.IO) {
    val store = Store(context)
    quickCardRows(store.bankAccounts, store.widgetLock)
}

/**
 * Quick Cards Carousel widget (4x2). Every active card with a full PAN shows a copy button;
 * when a SHEBA is stored, a second button copies it. Tapping a row without digits opens
 * the app instead.
 */
class QuickCardsGlanceWidget : GlanceAppWidget() {

    override val sizeMode: SizeMode = SizeMode.Responsive(
        setOf(DpSize(250.dp, 110.dp), DpSize(250.dp, 180.dp)),
    )

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val rows = loadQuickCardRows(context)
        provideContent {
            GlanceTheme {
                QuickCardsBody(context, rows)
            }
        }
    }
}

@Composable
private fun QuickCardsBody(context: Context, rows: List<QuickCardRow>) {
    val darkSurface = Color(0xFF161410)
    val cardSurface = Color(0xFF221F19)
    val goldColor = Color(0xFFF7C948)
    val mutedColor = Color(0xFF93B7B0)
    val textWhite = Color(0xFFFAF9F6)

    Box(
        modifier = GlanceModifier
            .fillMaxSize()
            .background(darkSurface)
            .cornerRadius(16.dp)
            .padding(8.dp),
    ) {
        Column(modifier = GlanceModifier.fillMaxSize()) {
            Row(modifier = GlanceModifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = "کارت‌های شتاب شما",
                    style = TextStyle(color = ColorProvider(goldColor), fontSize = 11.sp, fontWeight = FontWeight.Bold),
                    maxLines = 1,
                    modifier = GlanceModifier.defaultWeight(),
                )
                Text(
                    text = "کپی با یک لمس",
                    style = TextStyle(color = ColorProvider(mutedColor), fontSize = 8.sp),
                    maxLines = 1,
                )
            }
            Spacer(GlanceModifier.height(4.dp))
            if (rows.isEmpty()) {
                Text(
                    text = if (rows.isEmpty()) "حسابی یافت نشد (یا حالت پنهان روشن است)" else "",
                    style = TextStyle(color = ColorProvider(mutedColor), fontSize = 10.sp),
                    maxLines = 2,
                    modifier = GlanceModifier.fillMaxWidth().clickable(actionStartActivity<MainActivity>()),
                )
            } else {
                rows.take(3).forEach { row ->
                    QuickCardRowItem(context, row, cardSurface, goldColor, textWhite, mutedColor)
                    Spacer(GlanceModifier.height(4.dp))
                }
            }
        }
    }
}

@Composable
private fun QuickCardRowItem(
    context: Context,
    row: QuickCardRow,
    cardSurface: Color,
    goldColor: Color,
    textWhite: Color,
    mutedColor: Color,
) {
    Box(
        modifier = GlanceModifier
            .fillMaxWidth()
            .background(cardSurface)
            .cornerRadius(10.dp)
            .padding(horizontal = 8.dp, vertical = 4.dp),
    ) {
        Row(
            modifier = GlanceModifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = GlanceModifier.defaultWeight()) {
                Text(
                    text = row.label,
                    style = TextStyle(color = ColorProvider(textWhite), fontSize = 10.sp, fontWeight = FontWeight.Bold),
                    maxLines = 1,
                )
                Text(
                    text = row.maskedPan,
                    style = TextStyle(color = ColorProvider(mutedColor), fontSize = 9.sp),
                    maxLines = 1,
                )
            }
            if (row.fullPanDigits.isNotBlank()) {
                Box(
                    modifier = GlanceModifier
                        .background(Color(0xFF322A14))
                        .cornerRadius(8.dp)
                        .padding(horizontal = 6.dp, vertical = 3.dp)
                        .clickable {
                            val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as? android.content.ClipboardManager
                            clipboard?.setPrimaryClip(
                                android.content.ClipData.newPlainText("pan", row.fullPanDigits),
                            )
                            android.widget.Toast.makeText(context, "شماره کارت کپی شد", android.widget.Toast.LENGTH_SHORT).show()
                        },
                ) {
                    Text(
                        text = "کپی شماره",
                        style = TextStyle(color = ColorProvider(goldColor), fontSize = 9.sp, fontWeight = FontWeight.Bold),
                        maxLines = 1,
                    )
                }
                if (row.sheba.isNotBlank()) {
                    Spacer(GlanceModifier.width(4.dp))
                    Box(
                        modifier = GlanceModifier
                            .background(Color(0xFF322A14))
                            .cornerRadius(8.dp)
                            .padding(horizontal = 6.dp, vertical = 3.dp)
                            .clickable {
                                val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as? android.content.ClipboardManager
                                clipboard?.setPrimaryClip(
                                    android.content.ClipData.newPlainText("sheba", cleanSheba(row.sheba)),
                                )
                                android.widget.Toast.makeText(context, "شماره شبا کپی شد", android.widget.Toast.LENGTH_SHORT).show()
                            },
                    ) {
                        Text(
                            text = "کپی شبا",
                            style = TextStyle(color = ColorProvider(goldColor), fontSize = 9.sp, fontWeight = FontWeight.Bold),
                            maxLines = 1,
                        )
                    }
                }
            }
        }
    }
}

/** Receiver for the Quick Cards Carousel widget. */
class QuickCardsGlanceWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = QuickCardsGlanceWidget()
}
