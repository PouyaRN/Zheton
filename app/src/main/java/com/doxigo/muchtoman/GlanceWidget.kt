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
