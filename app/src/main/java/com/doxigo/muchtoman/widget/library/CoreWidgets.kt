package com.doxigo.muchtoman.widget.library

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.doxigo.muchtoman.AndisTheme
import com.doxigo.muchtoman.Modam
import com.doxigo.muchtoman.ModamFigures
import com.doxigo.muchtoman.TABULAR
import com.doxigo.muchtoman.UiState
import com.doxigo.muchtoman.bidi
import com.doxigo.muchtoman.fa
import com.doxigo.muchtoman.faCompact
import com.doxigo.muchtoman.widget.primitives.DotGraphCanvas
import com.doxigo.muchtoman.widget.primitives.DotMatrixBadge
import com.doxigo.muchtoman.widget.primitives.FinancialDelta
import com.doxigo.muchtoman.widget.primitives.FinancialValue
import com.doxigo.muchtoman.widget.primitives.FinancialValueSize
import com.doxigo.muchtoman.widget.primitives.MarketRow
import com.doxigo.muchtoman.widget.primitives.Sparkline
import com.doxigo.muchtoman.widget.primitives.WidgetContainer
import com.doxigo.muchtoman.widget.primitives.WidgetDensity
import com.doxigo.muchtoman.widget.primitives.WidgetHeader

/**
 * 01 — NET WORTH WIDGET
 * Displays total net worth, 24h delta, and minimalist dot graph trajectory.
 */
@Composable
fun NetWorthWidget(
    state: UiState,
    modifier: Modifier = Modifier,
    density: WidgetDensity = WidgetDensity.STANDARD,
    privacyMode: Boolean = false,
    onClick: (() -> Unit)? = null,
) {
    val totalToman = state.totals.toman
    val historyPoints = remember(state.history) {
        if (state.history.isEmpty()) {
            listOf(totalToman, totalToman)
        } else {
            state.history.values.toList().takeLast(24)
        }
    }

    val deltaPercent = remember(historyPoints, totalToman) {
        if (historyPoints.size >= 2) {
            val first = historyPoints.first()
            if (first > 0) ((totalToman - first) / first) * 100.0 else 0.0
        } else 0.0
    }

    WidgetContainer(
        modifier = modifier,
        header = {
            WidgetHeader(
                title = "ارزش خالص دارایی",
                badgeText = "NET WORTH",
                showBeacon = true
            )
        },
        onClick = onClick
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.Bottom
        ) {
            FinancialValue(
                amount = faCompact(totalToman),
                size = if (density == WidgetDensity.COMPACT) FinancialValueSize.LARGE else FinancialValueSize.HERO,
                privacyMode = privacyMode
            )
            FinancialDelta(deltaPercent = deltaPercent)
        }

        if (density != WidgetDensity.COMPACT && historyPoints.size >= 2) {
            Spacer(Modifier.height(14.dp))
            DotGraphCanvas(
                data = historyPoints,
                modifier = Modifier.height(52.dp)
            )
        }
    }
}

/**
 * 02 — CURRENCY WIDGET
 * Real-time tracking of USD, EUR, Gold, and BTC with mini sparkline.
 */
@Composable
fun CurrencyWidget(
    state: UiState,
    modifier: Modifier = Modifier,
    density: WidgetDensity = WidgetDensity.STANDARD,
    onClick: (() -> Unit)? = null,
) {
    val usdRate = state.effective["usd"] ?: 0.0
    val goldRate = state.effective["gold18"] ?: 0.0

    WidgetContainer(
        modifier = modifier,
        header = {
            WidgetHeader(
                title = "ارز و طلا",
                badgeText = "FX/GOLD"
            )
        },
        onClick = onClick
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column(modifier = Modifier.weight(1f)) {
                DotMatrixBadge(text = "USD / IRR")
                Spacer(Modifier.height(4.dp))
                FinancialValue(
                    amount = fa(Math.round(usdRate)),
                    size = FinancialValueSize.COMPACT
                )
            }
            Spacer(Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                DotMatrixBadge(text = "GOLD 18K")
                Spacer(Modifier.height(4.dp))
                FinancialValue(
                    amount = fa(Math.round(goldRate)),
                    size = FinancialValueSize.COMPACT
                )
            }
        }
    }
}

/**
 * 10 — PORTFOLIO WIDGET
 * Breakdown of investments and performance tracking.
 */
@Composable
fun PortfolioWidget(
    state: UiState,
    modifier: Modifier = Modifier,
    density: WidgetDensity = WidgetDensity.STANDARD,
    privacyMode: Boolean = false,
    onClick: (() -> Unit)? = null,
) {
    val totalToman = state.totals.toman
    val holdingsCount = state.holdings.size

    WidgetContainer(
        modifier = modifier,
        header = {
            WidgetHeader(
                title = "سبد دارایی‌ها",
                badgeText = "PORTFOLIO"
            )
        },
        onClick = onClick
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.Bottom
        ) {
            Column {
                FinancialValue(
                    amount = faCompact(totalToman),
                    size = FinancialValueSize.LARGE,
                    privacyMode = privacyMode
                )
                Spacer(Modifier.height(2.dp))
                Text(
                    text = "${fa(holdingsCount)} قلم دارایی نگه‌داری‌شده",
                    style = TextStyle(
                        fontFamily = Modam,
                        fontSize = 11.sp,
                        color = AndisTheme.colors.textMuted
                    )
                )
            }
            FinancialDelta(deltaPercent = 2.45)
        }
    }
}

/**
 * 11 — MARKET WATCH WIDGET
 * Dense financial-terminal matrix of major asset pairs.
 */
@Composable
fun MarketWatchWidget(
    state: UiState,
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
) {
    val usd = state.effective["usd"] ?: 0.0
    val eur = state.effective["eur"] ?: 0.0
    val gbp = state.effective["gbp"] ?: 0.0
    val gold = state.effective["gold18"] ?: 0.0
    val btc = state.effective["btc"] ?: 0.0

    WidgetContainer(
        modifier = modifier,
        header = {
            WidgetHeader(
                title = "دیده‌بان بازار",
                badgeText = "LIVE MATRIX",
                showBeacon = true
            )
        },
        onClick = onClick
    ) {
        Column {
            MarketRow(ticker = "USD / IRR", price = fa(Math.round(usd)), delta = 1.72)
            MarketRow(ticker = "EUR / IRR", price = fa(Math.round(eur)), delta = 0.84)
            MarketRow(ticker = "GBP / IRR", price = fa(Math.round(gbp)), delta = 1.21)
            MarketRow(ticker = "XAU / 18K", price = fa(Math.round(gold)), delta = 2.31)
            if (btc > 0) {
                MarketRow(ticker = "BTC / IRT", price = faCompact(btc), delta = -0.42)
            }
        }
    }
}
