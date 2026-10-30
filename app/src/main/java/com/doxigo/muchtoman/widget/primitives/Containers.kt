package com.doxigo.muchtoman.widget.primitives

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDirection
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.doxigo.muchtoman.ZhetonTheme
import com.doxigo.muchtoman.HairlineDivider
import com.doxigo.muchtoman.Modam
import com.doxigo.muchtoman.ModamFigures
import com.doxigo.muchtoman.TABULAR

enum class WidgetDensity {
    COMPACT,
    STANDARD,
    EXPANDED,
}

/**
 * Open Editorial Section.
 * Replaces repetitive boxed cards with open typography, generous negative space, and hairline dividers.
 */
@Composable
fun EditorialSection(
    title: String,
    modifier: Modifier = Modifier,
    technicalLabel: String? = null,
    showTopDivider: Boolean = true,
    action: (@Composable () -> Unit)? = null,
    content: @Composable () -> Unit,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 10.dp)
    ) {
        if (showTopDivider) {
            HairlineDivider()
            Spacer(Modifier.height(14.dp))
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text(
                    text = title,
                    style = TextStyle(
                        fontFamily = Modam,
                        fontWeight = FontWeight.Bold,
                        fontSize = 17.sp,
                        color = ZhetonTheme.colors.textPrimary
                    )
                )
                if (technicalLabel != null) {
                    Spacer(Modifier.height(2.dp))
                    Text(
                        text = technicalLabel.uppercase(),
                        style = TextStyle(
                            fontFamily = ModamFigures,
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 10.sp,
                            letterSpacing = 0.8.sp,
                            color = ZhetonTheme.colors.textMuted
                        )
                    )
                }
            }
            if (action != null) {
                action()
            }
        }

        Spacer(Modifier.height(12.dp))
        content()
    }
}

/**
 * Clean Unboxed Key-Value Row for Financial Terminal views.
 */
@Composable
fun EditorialRow(
    label: String,
    value: String,
    modifier: Modifier = Modifier,
    unit: String = "",
    sublabel: String? = null,
    delta: Double? = null,
    onClick: (() -> Unit)? = null,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(vertical = 8.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column {
            Text(
                text = label,
                style = TextStyle(
                    fontFamily = Modam,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 14.sp,
                    color = ZhetonTheme.colors.textPrimary
                )
            )
            if (sublabel != null) {
                Text(
                    text = sublabel,
                    style = TextStyle(
                        fontFamily = Modam,
                        fontSize = 11.sp,
                        color = ZhetonTheme.colors.textMuted
                    )
                )
            }
        }

        Row(verticalAlignment = Alignment.CenterVertically) {
            FinancialValue(
                amount = value,
                unit = unit,
                size = FinancialValueSize.COMPACT,
                color = ZhetonTheme.colors.textPrimary
            )
            if (delta != null) {
                Spacer(Modifier.width(8.dp))
                FinancialDelta(deltaPercent = delta, showBackground = false)
            }
        }
    }
}

/**
 * Standard Technical Widget Header.
 */
@Composable
fun WidgetHeader(
    title: String,
    modifier: Modifier = Modifier,
    badgeText: String? = null,
    showBeacon: Boolean = false,
    beaconColor: Color = ZhetonTheme.colors.accentLime,
    action: (@Composable () -> Unit)? = null,
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (showBeacon) {
                StatusIndicator(active = true, activeColor = beaconColor, pulsing = true, size = 5.dp)
                Spacer(Modifier.width(6.dp))
            }
            Text(
                text = title.uppercase(),
                style = TextStyle(
                    fontFamily = Modam,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 11.sp,
                    letterSpacing = 0.5.sp,
                    color = ZhetonTheme.colors.textSecondary
                )
            )
            if (badgeText != null) {
                Spacer(Modifier.width(6.dp))
                DotMatrixBadge(
                    text = badgeText,
                    activeColor = beaconColor,
                    dotSize = 1.2.dp
                )
            }
        }
        if (action != null) {
            action()
        }
    }
}

/**
 * Standard ANDIS Financial OS Widget Container.
 * Near-black technical surface (#0A0D0F), precision border (#1A1F22), no bubbly shadows.
 */
@Composable
fun WidgetContainer(
    modifier: Modifier = Modifier,
    header: (@Composable () -> Unit)? = null,
    onClick: (() -> Unit)? = null,
    content: @Composable () -> Unit,
) {
    val shape = RoundedCornerShape(ZhetonTheme.radius.card)
    val baseModifier = modifier
        .fillMaxWidth()
        .background(ZhetonTheme.colors.surfacePrimary, shape)
        .border(1.dp, ZhetonTheme.colors.border, shape)
        .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
        .padding(14.dp)

    Column(modifier = baseModifier) {
        if (header != null) {
            header()
            Spacer(Modifier.height(10.dp))
        }
        content()
    }
}

/**
 * High-Density Market Terminal Row.
 */
@Composable
fun MarketRow(
    ticker: String,
    price: String,
    delta: Double,
    modifier: Modifier = Modifier,
    unit: String = "",
    onClick: (() -> Unit)? = null,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(vertical = 6.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            DotMatrixBadge(
                text = ticker,
                activeColor = ZhetonTheme.colors.textPrimary,
                surfaceColor = ZhetonTheme.colors.surfaceSecondary,
                borderColor = ZhetonTheme.colors.border
            )
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            FinancialValue(
                amount = price,
                unit = unit,
                size = FinancialValueSize.COMPACT,
                color = ZhetonTheme.colors.textPrimary
            )
            Spacer(Modifier.width(10.dp))
            FinancialDelta(deltaPercent = delta)
        }
    }
}

/**
 * Technical Account Balance Row.
 */
@Composable
fun AccountRow(
    name: String,
    balance: String,
    allocationPercent: Double,
    modifier: Modifier = Modifier,
    identifier: String? = null,
    indicatorColor: Color = ZhetonTheme.colors.accentLime,
    onClick: (() -> Unit)? = null,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(vertical = 7.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .size(6.dp)
                    .background(indicatorColor, CircleShape)
            )
            Spacer(Modifier.width(8.dp))
            Column {
                Text(
                    text = name,
                    style = TextStyle(
                        fontFamily = Modam,
                        fontWeight = FontWeight.Bold,
                        fontSize = 13.sp,
                        color = ZhetonTheme.colors.textPrimary
                    )
                )
                if (identifier != null) {
                    Text(
                        text = identifier,
                        style = TextStyle(
                            fontFamily = ModamFigures,
                            fontSize = 10.sp,
                            fontFeatureSettings = TABULAR,
                            color = ZhetonTheme.colors.textMuted,
                            textDirection = TextDirection.Ltr
                        )
                    )
                }
            }
        }
        Column(horizontalAlignment = Alignment.End) {
            FinancialValue(
                amount = balance,
                size = FinancialValueSize.COMPACT,
                color = ZhetonTheme.colors.textPrimary
            )
            Text(
                text = String.format(java.util.Locale.US, "%.1f%%", allocationPercent),
                style = TextStyle(
                    fontFamily = ModamFigures,
                    fontWeight = FontWeight.Medium,
                    fontSize = 10.sp,
                    fontFeatureSettings = TABULAR,
                    color = ZhetonTheme.colors.textMuted
                )
            )
        }
    }
}

/**
 * Ultra-Clean Financial Stream Transaction Row.
 */
@Composable
fun TransactionRow(
    merchant: String,
    category: String,
    amount: String,
    time: String,
    isExpense: Boolean,
    modifier: Modifier = Modifier,
    accountName: String? = null,
    onClick: (() -> Unit)? = null,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(vertical = 7.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(
            modifier = Modifier.weight(1f),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(5.dp)
                    .background(
                        if (isExpense) ZhetonTheme.colors.danger else ZhetonTheme.colors.accentLime,
                        CircleShape
                    )
            )
            Spacer(Modifier.width(8.dp))
            Column {
                Text(
                    text = merchant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    style = TextStyle(
                        fontFamily = Modam,
                        fontWeight = FontWeight.Bold,
                        fontSize = 13.sp,
                        color = ZhetonTheme.colors.textPrimary
                    )
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = category,
                        style = TextStyle(
                            fontFamily = Modam,
                            fontSize = 10.sp,
                            color = ZhetonTheme.colors.textSecondary
                        )
                    )
                    if (accountName != null) {
                        Text(
                            text = " • $accountName",
                            style = TextStyle(
                                fontFamily = Modam,
                                fontSize = 10.sp,
                                color = ZhetonTheme.colors.textMuted
                            )
                        )
                    }
                }
            }
        }
        Column(horizontalAlignment = Alignment.End) {
            Text(
                text = (if (isExpense) "- " else "+ ") + amount,
                style = TextStyle(
                    fontFamily = ModamFigures,
                    fontWeight = FontWeight.Bold,
                    fontSize = 13.sp,
                    fontFeatureSettings = TABULAR,
                    color = if (isExpense) ZhetonTheme.colors.textPrimary else ZhetonTheme.colors.accentLime,
                    textDirection = TextDirection.Ltr
                )
            )
            Text(
                text = time,
                style = TextStyle(
                    fontFamily = ModamFigures,
                    fontSize = 10.sp,
                    fontFeatureSettings = TABULAR,
                    color = ZhetonTheme.colors.textMuted
                )
            )
        }
    }
}
