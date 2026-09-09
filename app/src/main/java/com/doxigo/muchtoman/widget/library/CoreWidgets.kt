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
import com.doxigo.muchtoman.HairlineDivider
import com.doxigo.muchtoman.Modam
import com.doxigo.muchtoman.ModamFigures
import com.doxigo.muchtoman.TABULAR
import com.doxigo.muchtoman.UiState
import com.doxigo.muchtoman.fa
import com.doxigo.muchtoman.faCompact
import com.doxigo.muchtoman.widget.primitives.EditorialRow
import com.doxigo.muchtoman.widget.primitives.EditorialSection
import com.doxigo.muchtoman.widget.primitives.FinancialDelta
import com.doxigo.muchtoman.widget.primitives.FinancialValue
import com.doxigo.muchtoman.widget.primitives.FinancialValueSize
import com.doxigo.muchtoman.widget.primitives.MarketRow
import com.doxigo.muchtoman.widget.primitives.MonumentalValue
import com.doxigo.muchtoman.widget.primitives.SubtleLineChart
import com.doxigo.muchtoman.widget.primitives.WidgetDensity

/**
 * 01 — HERO NET WORTH (Obsidian Editorial Centerpiece)
 * Monumental 44sp typography, generous negative space, and a breathing hairline chart.
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
            state.history.values.toList().takeLast(30)
        }
    }

    val deltaPercent = remember(historyPoints, totalToman) {
        if (historyPoints.size >= 2) {
            val first = historyPoints.first()
            if (first > 0) ((totalToman - first) / first) * 100.0 else 0.0
        } else 0.0
    }

    EditorialSection(
        title = "ارزش خالص دارایی",
        technicalLabel = "NET WORTH · 30D",
        showTopDivider = false,
        modifier = modifier.then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier),
        action = {
            FinancialDelta(deltaPercent = deltaPercent, showBackground = true)
        }
    ) {
        MonumentalValue(
            amount = faCompact(totalToman),
            unit = "تومان",
            deltaPercent = deltaPercent,
            deltaPeriod = "این ماه",
            privacyMode = privacyMode
        )

        if (density != WidgetDensity.COMPACT && historyPoints.size >= 2) {
            Spacer(Modifier.height(18.dp))
            SubtleLineChart(
                data = historyPoints,
                modifier = Modifier.height(64.dp),
                lineColor = AndisTheme.colors.accentLime
            )
        }
    }
}

/**
 * 02 — CURRENCY & GOLD TERMINAL LIST
 * Editorial financial quotes with precise numeric alignment and hairline dividers.
 */
@Composable
fun CurrencyWidget(
    state: UiState,
    modifier: Modifier = Modifier,
    density: WidgetDensity = WidgetDensity.STANDARD,
    onClick: (() -> Unit)? = null,
) {
    val usdRate = state.effective["usd"] ?: 0.0
    val eurRate = state.effective["eur"] ?: 0.0
    val goldRate = state.effective["gold18"] ?: 0.0

    EditorialSection(
        title = "ارز و طلا",
        technicalLabel = "CURRENCIES & METALS",
        modifier = modifier.then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
    ) {
        Column {
            EditorialRow(
                label = "دلار آمریکا",
                sublabel = "USD / IRR",
                value = fa(Math.round(usdRate)),
                unit = "تومان",
                delta = 1.72
            )
            HairlineDivider(color = AndisTheme.colors.border)
            EditorialRow(
                label = "طلای ۱۸ عیار",
                sublabel = "GOLD 18K",
                value = fa(Math.round(goldRate)),
                unit = "تومان / گرم",
                delta = 2.31
            )
            if (density == WidgetDensity.EXPANDED) {
                HairlineDivider(color = AndisTheme.colors.border)
                EditorialRow(
                    label = "یورو اروپا",
                    sublabel = "EUR / IRR",
                    value = fa(Math.round(eurRate)),
                    unit = "تومان",
                    delta = 0.84
                )
            }
        }
    }
}

/**
 * 10 — PORTFOLIO ALLOCATION
 * Spacious, unboxed wealth breakdown.
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

    EditorialSection(
        title = "سبد دارایی‌ها",
        technicalLabel = "PORTFOLIO · ASSETS",
        modifier = modifier.then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
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
                Spacer(Modifier.height(4.dp))
                Text(
                    text = "${fa(holdingsCount)} قلم دارایی فعال در پرتفوی",
                    style = TextStyle(
                        fontFamily = Modam,
                        fontSize = 12.sp,
                        color = AndisTheme.colors.textSecondary
                    )
                )
            }
            FinancialDelta(deltaPercent = 2.45, showBackground = true)
        }
    }
}

/**
 * 11 — MARKET WATCH MATRIX
 * Dense financial-terminal list of major asset pairs.
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

    EditorialSection(
        title = "دیده‌بان بازار",
        technicalLabel = "GLOBAL TICKERS",
        modifier = modifier.then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
    ) {
        Column {
            MarketRow(ticker = "USD / IRR", price = fa(Math.round(usd)), delta = 1.72)
            HairlineDivider(color = AndisTheme.colors.border)
            MarketRow(ticker = "EUR / IRR", price = fa(Math.round(eur)), delta = 0.84)
            HairlineDivider(color = AndisTheme.colors.border)
            MarketRow(ticker = "GBP / IRR", price = fa(Math.round(gbp)), delta = 1.21)
            HairlineDivider(color = AndisTheme.colors.border)
            MarketRow(ticker = "XAU / 18K", price = fa(Math.round(gold)), delta = 2.31)
            if (btc > 0) {
                HairlineDivider(color = AndisTheme.colors.border)
                MarketRow(ticker = "BTC / USD", price = faCompact(btc), delta = -0.42)
            }
        }
    }
}
