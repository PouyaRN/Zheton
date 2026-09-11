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
import androidx.glance.Image
import androidx.glance.ImageProvider
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
import androidx.glance.layout.size
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
    val snapshot = WidgetSnapshotEngine.getSnapshot(context)
    GlanceWidgetState(
        netWorthToman = snapshot.netWorthToman,
        isMasked = snapshot.isMasked,
        deltaPercent = snapshot.deltaPercent,
        deltaSince = snapshot.deltaSince,
        todaySpendToman = snapshot.todaySpendToman,
        goldRateToman = snapshot.goldRateToman,
        tetherRateToman = snapshot.tetherRateToman,
        nextCommitmentText = snapshot.nextCommitmentText,
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

/** Maps Iranian bank names to their authentic vector SVG drawables. */
fun bankLogoRes(bank: String): Int? {
    val enumLogo = when (runCatching { Bank.valueOf(bank) }.getOrDefault(Bank.OTHER)) {
        Bank.BLU -> R.drawable.ic_bank_blu
        Bank.SAMAN -> R.drawable.ic_bank_saman
        Bank.REFAH -> R.drawable.ic_bank_refah
        Bank.PASARGAD -> R.drawable.ic_bank_pasargad
        Bank.EGHTESAD_NOVIN -> R.drawable.ic_bank_eghtesad_novin
        Bank.KHAVARMIANEH -> R.drawable.ic_bank_khavar_mianeh
        Bank.SADERAT -> R.drawable.ic_bank_saderat
        Bank.RESALAT -> R.drawable.ic_bank_resalat
        Bank.PARSIAN -> R.drawable.ic_bank_parsian
        Bank.MELLAT -> R.drawable.ic_bank_mellat
        Bank.MELLI -> R.drawable.ic_bank_melli
        Bank.MASKAN -> R.drawable.ic_bank_maskan
        Bank.SEPAH -> R.drawable.ic_bank_sepah
        Bank.TEJARAT -> R.drawable.ic_bank_tejarat
        Bank.OTHER -> null
    }

    val extendedLogo = when {
        bank.contains("KESHAVARZI", ignoreCase = true) || bank.contains("کشاورزی") -> R.drawable.ic_bank_keshavarzi
        bank.contains("SHAHR", ignoreCase = true) || bank.contains("شهر") -> R.drawable.ic_bank_shahr
        bank.contains("AYANDEH", ignoreCase = true) || bank.contains("آینده") -> R.drawable.ic_bank_ayandeh
        bank.contains("SINA", ignoreCase = true) || bank.contains("سینا") -> R.drawable.ic_bank_sina
        bank.contains("POST", ignoreCase = true) || bank.contains("پست") -> R.drawable.ic_bank_post
        bank.contains("MEHR_IRAN", ignoreCase = true) || bank.contains("مهر") -> R.drawable.ic_bank_mehr_iran
        bank.contains("GARDESHGARI", ignoreCase = true) || bank.contains("گردشگری") -> R.drawable.ic_bank_gardeshgari
        bank.contains("SARMAYEH", ignoreCase = true) || bank.contains("سرمایه") -> R.drawable.ic_bank_sarmayeh
        bank.contains("IRAN_ZAMIN", ignoreCase = true) || bank.contains("ایران زمین") -> R.drawable.ic_bank_iran_zamin
        bank.contains("DEY", ignoreCase = true) || bank.contains("دی") -> R.drawable.ic_bank_dey
        bank.contains("SANAT_MADAN", ignoreCase = true) || bank.contains("صنعت و معدن") -> R.drawable.ic_bank_sanat_madan
        bank.contains("TOSEE_TAAVON", ignoreCase = true) || bank.contains("توسعه تعاون") -> R.drawable.ic_bank_tosee_taavon
        bank.contains("GHAVAMIN", ignoreCase = true) || bank.contains("قوامین") -> R.drawable.ic_bank_ghavamin
        bank.contains("KOSAR", ignoreCase = true) || bank.contains("کوثر") -> R.drawable.ic_bank_kosar
        bank.contains("KARAFARIN", ignoreCase = true) || bank.contains("کارآفرین") -> R.drawable.ic_bank_karafarin
        else -> null
    }

    return enumLogo ?: extendedLogo
}

/** Maps market asset keys to vector SVG icons. */
fun marketIconRes(key: String): Int {
    return when {
        key == "btc" -> R.drawable.ic_network_bitcoin
        key == "eth" -> R.drawable.ic_network_ethereum
        key == "sol" -> R.drawable.ic_network_solana
        key == "usdt" || key == "usd" -> R.drawable.ic_network_tron
        else -> R.drawable.ic_toman_gold
    }
}

@Composable
private fun ChandTomanWidgetBody(context: Context, state: GlanceWidgetState) {
    val darkSurface = Color(0xFF13110E)
    val cardSurface = Color(0xFF1B1813)
    val chipSurface = Color(0xFF221E17)
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
            .background(ImageProvider(R.drawable.widget_bg))
            .padding(14.dp)
            .clickable(actionStartActivity(mainComponent)),
    ) {
        Column(
            modifier = GlanceModifier.fillMaxSize(),
        ) {
            // Header: App title + Market Rates Ticker chips
            Row(
                modifier = GlanceModifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Image(
                    provider = ImageProvider(R.drawable.ic_toman_gold),
                    contentDescription = "چند تومان",
                    modifier = GlanceModifier.size(16.dp),
                )
                Spacer(GlanceModifier.width(6.dp))
                GlanceModamText(
                    text = "چند تومان",
                    sp = 14f,
                    color = goldColor,
                    weight = ModamWeight.HEAVY,
                )

                Spacer(GlanceModifier.defaultWeight())

                // Gold & Tether rates chips with icons
                if (state.goldRateToman != null && state.goldRateToman > 0) {
                    Box(
                        modifier = GlanceModifier
                            .background(chipSurface)
                            .cornerRadius(6.dp)
                            .padding(horizontal = 6.dp, vertical = 3.dp),
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Image(
                                provider = ImageProvider(R.drawable.ic_toman_gold),
                                contentDescription = null,
                                modifier = GlanceModifier.size(10.dp),
                            )
                            Spacer(GlanceModifier.width(4.dp))
                            GlanceModamText(
                                text = "طلا: ${faCompact(state.goldRateToman)}",
                                sp = 9.5f,
                                color = goldColor,
                                weight = ModamWeight.MEDIUM,
                            )
                        }
                    }
                }

                if (state.tetherRateToman != null && state.tetherRateToman > 0) {
                    Spacer(GlanceModifier.width(6.dp))
                    Box(
                        modifier = GlanceModifier
                            .background(chipSurface)
                            .cornerRadius(6.dp)
                            .padding(horizontal = 6.dp, vertical = 3.dp),
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Image(
                                provider = ImageProvider(R.drawable.ic_network_tron),
                                contentDescription = null,
                                modifier = GlanceModifier.size(10.dp),
                            )
                            Spacer(GlanceModifier.width(4.dp))
                            GlanceModamText(
                                text = "تتر: ${faCompact(state.tetherRateToman)}",
                                sp = 9.5f,
                                color = mintColor,
                                weight = ModamWeight.MEDIUM,
                            )
                        }
                    }
                }
            }

            Spacer(GlanceModifier.height(8.dp))

            // Main Total & Change Row
            Row(
                modifier = GlanceModifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(modifier = GlanceModifier.defaultWeight()) {
                    GlanceModamText(
                        text = "جمع دارایی خالص",
                        sp = 10.5f,
                        color = mutedColor,
                        weight = ModamWeight.MEDIUM,
                    )

                    Spacer(GlanceModifier.height(2.dp))

                    val totalStr = if (state.isMasked) {
                        "٭٭٭"
                    } else {
                        "${faCompact(state.netWorthToman, 2)} تومان"
                    }

                    GlanceModamText(
                        text = totalStr,
                        sp = 23f,
                        color = if (state.isMasked) goldColor else textWhite,
                        weight = ModamWeight.HEAVY,
                        narrow = true,
                    )
                }

                if (!state.isMasked && state.deltaPercent != null) {
                    val isPositive = state.deltaPercent >= 0.0
                    val deltaLabel = "${if (isPositive) "+" else ""}${faDecimal(abs(state.deltaPercent), 1)}٪ ${state.deltaSince}".trim()
                    GlanceModamPill(
                        text = deltaLabel,
                        sp = 11f,
                        tone = if (isPositive) mintColor else warnColor,
                        up = if (abs(state.deltaPercent) < 0.01) null else isPositive,
                    )
                }
            }

            Spacer(GlanceModifier.height(8.dp))

            // Info Card: Today's Spend & Installment Countdown
            Box(
                modifier = GlanceModifier
                    .fillMaxWidth()
                    .background(ImageProvider(R.drawable.widget_inner_card))
                    .padding(horizontal = 10.dp, vertical = 7.dp),
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

                    GlanceModamText(
                        text = spendLabel,
                        sp = 10.5f,
                        color = if (state.todaySpendToman > 0) Color(0xFFFFB4A6) else mintColor,
                        weight = ModamWeight.MEDIUM,
                    )

                    Spacer(GlanceModifier.defaultWeight())

                    if (state.nextCommitmentText != null) {
                        GlanceModamText(
                            text = state.nextCommitmentText,
                            sp = 10f,
                            color = goldColor,
                            weight = ModamWeight.MEDIUM,
                        )
                    }
                }
            }

            Spacer(GlanceModifier.height(7.dp))

            // 1-Tap Quick Action Row
            Box(
                modifier = GlanceModifier
                    .fillMaxWidth()
                    .background(ImageProvider(R.drawable.widget_action_btn))
                    .clickable(actionStartActivity(quickAddComponent))
                    .padding(vertical = 7.dp),
                contentAlignment = Alignment.Center,
            ) {
                GlanceModamText(
                    text = "＋ ثبت سریع هزینه یا درآمد",
                    sp = 11.5f,
                    color = goldColor,
                    weight = ModamWeight.SEMIBOLD,
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
    val darkSurface = Color(0xFF13110E)
    val goldColor = Color(0xFFF7C948)
    val mintColor = Color(0xFF3BE0A8)
    val mutedColor = Color(0xFF93B7B0)

    Box(
        modifier = GlanceModifier
            .fillMaxSize()
            .background(ImageProvider(R.drawable.widget_bg))
            .padding(12.dp)
            .clickable(actionStartActivity<MainActivity>()),
    ) {
        when {
            tickers.isEmpty() -> Box(
                modifier = GlanceModifier.fillMaxSize(),
                contentAlignment = Alignment.Center,
            ) {
                GlanceModamText(
                    text = "نرخ‌ها در دسترس نیست",
                    sp = 11f,
                    color = mutedColor,
                    weight = ModamWeight.REGULAR,
                )
            }
            else -> Column(modifier = GlanceModifier.fillMaxSize()) {
                Row(
                    modifier = GlanceModifier.fillMaxWidth().defaultWeight(),
                    horizontalAlignment = androidx.glance.layout.Alignment.Horizontal.CenterHorizontally,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    tickers.forEachIndexed { index, ticker ->
                        if (index > 0) Spacer(GlanceModifier.width(10.dp))
                        Column(horizontalAlignment = androidx.glance.layout.Alignment.Horizontal.CenterHorizontally) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Image(
                                    provider = ImageProvider(marketIconRes(ticker.key)),
                                    contentDescription = ticker.label,
                                    modifier = GlanceModifier.size(11.dp),
                                )
                                Spacer(GlanceModifier.width(3.dp))
                                GlanceModamText(
                                    text = ticker.label,
                                    sp = 9.5f,
                                    color = mutedColor,
                                    weight = ModamWeight.MEDIUM,
                                )
                            }
                            Spacer(GlanceModifier.height(2.dp))
                            GlanceModamText(
                                text = "${faCompact(ticker.rateToman)} ${ticker.unit}",
                                sp = 12.5f,
                                color = if (ticker.key.startsWith("gold") || ticker.key.startsWith("coin")) goldColor else mintColor,
                                weight = ModamWeight.HEAVY,
                                narrow = true,
                            )
                        }
                    }
                }
                if (updatedAt > 0) {
                    Spacer(GlanceModifier.height(2.dp))
                    GlanceModamText(
                        text = "بروزرسانی: ${faAgo(updatedAt, System.currentTimeMillis())}",
                        sp = 8.5f,
                        color = mutedColor,
                        weight = ModamWeight.REGULAR,
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
    val darkSurface = Color(0xFF13110E)
    val goldColor = Color(0xFFF7C948)
    val mintColor = Color(0xFF3BE0A8)
    val mutedColor = Color(0xFF93B7B0)

    Box(
        modifier = GlanceModifier
            .fillMaxSize()
            .background(ImageProvider(R.drawable.widget_bg))
            .padding(8.dp)
            .clickable(actionStartActivity<MainActivity>()),
        contentAlignment = Alignment.Center,
    ) {
        if (ticker == null) {
            GlanceModamText(
                text = "—",
                sp = 14f,
                color = mutedColor,
                weight = ModamWeight.MEDIUM,
            )
        } else {
            Column(horizontalAlignment = androidx.glance.layout.Alignment.Horizontal.CenterHorizontally) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Image(
                        provider = ImageProvider(marketIconRes(ticker.key)),
                        contentDescription = ticker.label,
                        modifier = GlanceModifier.size(13.dp),
                    )
                    Spacer(GlanceModifier.width(3.dp))
                    GlanceModamText(
                        text = ticker.label,
                        sp = 9f,
                        color = mutedColor,
                        weight = ModamWeight.MEDIUM,
                    )
                }
                Spacer(GlanceModifier.height(2.dp))
                GlanceModamText(
                    text = faCompact(ticker.rateToman),
                    sp = 14f,
                    color = if (ticker.key.startsWith("gold") || ticker.key.startsWith("coin")) goldColor else mintColor,
                    weight = ModamWeight.HEAVY,
                    narrow = true,
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
    val darkSurface = Color(0xFF13110E)
    val goldColor = Color(0xFFF7C948)
    val mintColor = Color(0xFF3BE0A8)
    val warnColor = Color(0xFFFF9E80)
    val mutedColor = Color(0xFF93B7B0)
    val textWhite = Color(0xFFFAF9F6)

    Box(
        modifier = GlanceModifier
            .fillMaxSize()
            .background(ImageProvider(R.drawable.widget_bg))
            .padding(14.dp)
            .clickable(actionStartActivity<MainActivity>()),
    ) {
        Column(modifier = GlanceModifier.fillMaxSize()) {
            Row(modifier = GlanceModifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Image(
                    provider = ImageProvider(R.drawable.ic_toman_gold),
                    contentDescription = null,
                    modifier = GlanceModifier.size(14.dp),
                )
                Spacer(GlanceModifier.width(5.dp))
                GlanceModamText(
                    text = "خرج امن امروز",
                    sp = 11f,
                    color = goldColor,
                    weight = ModamWeight.SEMIBOLD,
                )
                Spacer(GlanceModifier.defaultWeight())
                GlanceModamText(
                    text = "${faNumber(state.daysRemaining.toDouble())} روز تا پایان چرخه",
                    sp = 9.5f,
                    color = mutedColor,
                    weight = ModamWeight.MEDIUM,
                )
            }
            Spacer(GlanceModifier.height(4.dp))
            val amountStr = if (state.isMasked) "٭٭٭" else {
                if (state.isOverdrawn) "کسری بودجه!" else "${faCompact(state.dailyAllowanceToman.toDouble())} تومان"
            }
            GlanceModamText(
                text = amountStr,
                sp = 19f,
                color = when {
                    state.isMasked -> goldColor
                    state.isOverdrawn -> warnColor
                    else -> textWhite
                },
                weight = ModamWeight.HEAVY,
                narrow = true,
            )
            Spacer(GlanceModifier.height(6.dp))
            val fill = state.monthProgressPercent.coerceIn(0, 100)
            GlanceProgressGauge(
                fraction = fill / 100f,
                activeTone = if (state.isOverdrawn) warnColor else mintColor,
                bgTone = Color(0xFF26221A),
                heightDp = 6f,
                modifier = GlanceModifier.fillMaxWidth(),
            )
            Spacer(GlanceModifier.height(4.dp))
            Row(modifier = GlanceModifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                GlanceModamText(
                    text = if (state.isMasked) "پنهان" else "بودجه مصرف‌شده: ${faNumber(fill.toDouble())}٪",
                    sp = 9f,
                    color = mutedColor,
                    weight = ModamWeight.REGULAR,
                )
                if (state.goldGrams != null && state.goldGrams > 0.0) {
                    Spacer(GlanceModifier.defaultWeight())
                    GlanceModamText(
                        text = "معادل: ${faDecimal(state.goldGrams, 2)} گرم طلا",
                        sp = 9f,
                        color = goldColor,
                        weight = ModamWeight.REGULAR,
                    )
                }
            }
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
    val darkSurface = Color(0xFF13110E)
    val goldColor = Color(0xFFF7C948)
    val warnColor = Color(0xFFFF9E80)
    val mutedColor = Color(0xFF93B7B0)
    val textWhite = Color(0xFFFAF9F6)

    Box(
        modifier = GlanceModifier
            .fillMaxSize()
            .background(ImageProvider(R.drawable.widget_bg))
            .padding(14.dp)
            .clickable(actionStartActivity<MainActivity>()),
    ) {
        Column(modifier = GlanceModifier.fillMaxSize()) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Image(
                    provider = ImageProvider(R.drawable.ic_toman_gold),
                    contentDescription = null,
                    modifier = GlanceModifier.size(13.dp),
                )
                Spacer(GlanceModifier.width(5.dp))
                GlanceModamText(
                    text = "موجودی نقد عملیاتی",
                    sp = 11f,
                    color = mutedColor,
                    weight = ModamWeight.MEDIUM,
                )
            }
            Spacer(GlanceModifier.height(3.dp))
            val amountStr = if (state.isMasked) "٭٭٭" else "${faCompact(state.liquidCashToman.toDouble())} تومان"
            GlanceModamText(
                text = amountStr,
                sp = 18f,
                color = if (state.isMasked) goldColor else textWhite,
                weight = ModamWeight.HEAVY,
                narrow = true,
            )
            Spacer(GlanceModifier.height(6.dp))
            Row(modifier = GlanceModifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                val goldText = state.goldGrams?.let {
                    if (state.isMasked) "⋆⋆ گرم" else "معادل ${faDecimal(it, 2)} گرم طلای ۱۸"
                } ?: "نرخ طلا در دسترس نیست"

                GlanceModamText(
                    text = goldText,
                    sp = 10.5f,
                    color = goldColor,
                    weight = ModamWeight.SEMIBOLD,
                    modifier = GlanceModifier.defaultWeight(),
                )
                if (state.dailyBleedToman > 0 && !state.isMasked) {
                    Spacer(GlanceModifier.width(6.dp))
                    GlanceModamText(
                        text = "خونریزی: ${faCompact(state.dailyBleedToman.toDouble())}− ت",
                        sp = 9.5f,
                        color = warnColor,
                        weight = ModamWeight.REGULAR,
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
    val darkSurface = Color(0xFF13110E)
    val chipSurface = Color(0xFF262012)

    Box(
        modifier = GlanceModifier
            .fillMaxSize()
            .background(ImageProvider(R.drawable.widget_bg))
            .padding(10.dp),
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
                        .background(ImageProvider(R.drawable.widget_action_btn))
                        .padding(horizontal = 9.dp, vertical = 6.dp)
                        .clickable(
                            actionStartActivity(
                                Intent(context, QuickAddActivity::class.java).apply {
                                    putExtra("com.doxigo.muchtoman.extra.CATEGORY_ID", chip.categoryId)
                                },
                            ),
                        ),
                ) {
                    GlanceModamText(
                        text = "＋ ${chip.label}",
                        sp = 10.5f,
                        color = goldColor,
                        weight = ModamWeight.SEMIBOLD,
                    )
                }
                Spacer(GlanceModifier.width(5.dp))
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
    val darkSurface = Color(0xFF13110E)
    val cardSurface = Color(0xFF1B1914)
    val goldColor = Color(0xFFF7C948)
    val mintColor = Color(0xFF3BE0A8)
    val mutedColor = Color(0xFF93B7B0)
    val textWhite = Color(0xFFFAF9F6)

    Box(
        modifier = GlanceModifier
            .fillMaxSize()
            .background(ImageProvider(R.drawable.widget_bg))
            .padding(12.dp),
    ) {
        Column(modifier = GlanceModifier.fillMaxSize()) {
            Row(modifier = GlanceModifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Image(
                    provider = ImageProvider(R.drawable.ic_toman_gold),
                    contentDescription = null,
                    modifier = GlanceModifier.size(15.dp),
                )
                Spacer(GlanceModifier.width(6.dp))
                GlanceModamText(
                    text = "کارت‌های شتاب شما",
                    sp = 12f,
                    color = goldColor,
                    weight = ModamWeight.HEAVY,
                    modifier = GlanceModifier.defaultWeight(),
                )
                GlanceModamText(
                    text = "کپی با یک لمس",
                    sp = 9f,
                    color = mutedColor,
                    weight = ModamWeight.REGULAR,
                )
            }
            Spacer(GlanceModifier.height(6.dp))
            if (rows.isEmpty()) {
                Box(
                    modifier = GlanceModifier
                        .fillMaxWidth()
                        .padding(vertical = 12.dp)
                        .clickable(actionStartActivity<MainActivity>()),
                    contentAlignment = Alignment.Center,
                ) {
                    GlanceModamText(
                        text = "حسابی یافت نشد (یا حالت پنهان روشن است)",
                        sp = 10.5f,
                        color = mutedColor,
                        weight = ModamWeight.REGULAR,
                    )
                }
            } else {
                rows.take(3).forEach { row ->
                    QuickCardRowItem(context, row, cardSurface, goldColor, mintColor, textWhite, mutedColor)
                    Spacer(GlanceModifier.height(6.dp))
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
    mintColor: Color,
    textWhite: Color,
    mutedColor: Color,
) {
    Box(
        modifier = GlanceModifier
            .fillMaxWidth()
            .background(ImageProvider(R.drawable.widget_inner_card))
            .padding(horizontal = 10.dp, vertical = 7.dp),
    ) {
        Row(
            modifier = GlanceModifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            val logoRes = bankLogoRes(row.bankFa)
            if (logoRes != null) {
                Image(
                    provider = ImageProvider(logoRes),
                    contentDescription = row.bankFa,
                    modifier = GlanceModifier.size(26.dp),
                )
                Spacer(GlanceModifier.width(8.dp))
            }
            Column(modifier = GlanceModifier.defaultWeight()) {
                GlanceModamText(
                    text = row.label,
                    sp = 10.5f,
                    color = textWhite,
                    weight = ModamWeight.SEMIBOLD,
                )
                Spacer(GlanceModifier.height(2.dp))
                GlanceModamPan(
                    pan = row.fullPanDigits.ifBlank { row.maskedPan },
                    isMasked = row.fullPanDigits.isBlank(),
                    sp = 10.5f,
                    color = mutedColor,
                )
            }
            if (row.fullPanDigits.isNotBlank()) {
                Box(
                    modifier = GlanceModifier
                        .background(ImageProvider(R.drawable.widget_chip))
                        .padding(horizontal = 8.dp, vertical = 4.dp)
                        .clickable {
                            val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as? android.content.ClipboardManager
                            clipboard?.setPrimaryClip(
                                android.content.ClipData.newPlainText("pan", row.fullPanDigits),
                            )
                            android.widget.Toast.makeText(context, "شماره کارت کپی شد", android.widget.Toast.LENGTH_SHORT).show()
                        },
                ) {
                    GlanceModamText(
                        text = "کپی کارت",
                        sp = 9.5f,
                        color = goldColor,
                        weight = ModamWeight.MEDIUM,
                    )
                }
                if (row.sheba.isNotBlank()) {
                    Spacer(GlanceModifier.width(6.dp))
                    Box(
                        modifier = GlanceModifier
                            .background(ImageProvider(R.drawable.widget_chip))
                            .padding(horizontal = 8.dp, vertical = 4.dp)
                            .clickable {
                                val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as? android.content.ClipboardManager
                                clipboard?.setPrimaryClip(
                                    android.content.ClipData.newPlainText("sheba", cleanSheba(row.sheba)),
                                )
                                android.widget.Toast.makeText(context, "شماره شبا کپی شد", android.widget.Toast.LENGTH_SHORT).show()
                            },
                    ) {
                        GlanceModamText(
                            text = "کپی شبا",
                            sp = 9.5f,
                            color = mintColor,
                            weight = ModamWeight.MEDIUM,
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
