package com.doxigo.muchtoman.widget.primitives

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
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
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.doxigo.muchtoman.AndisTheme
import com.doxigo.muchtoman.Modam
import com.doxigo.muchtoman.ModamFigures
import com.doxigo.muchtoman.TABULAR

enum class FinancialValueSize(val amountSize: TextUnit, val unitSize: TextUnit, val weight: FontWeight) {
    HERO(34.sp, 14.sp, FontWeight.Black),
    LARGE(24.sp, 12.sp, FontWeight.ExtraBold),
    MEDIUM(18.sp, 11.sp, FontWeight.Bold),
    COMPACT(15.sp, 10.sp, FontWeight.SemiBold),
}

/**
 * Dominant Financial Value Instrument.
 * Renders large tabular digits that visually dominate descriptive labels.
 */
@Composable
fun FinancialValue(
    amount: String,
    modifier: Modifier = Modifier,
    unit: String = "تومان",
    size: FinancialValueSize = FinancialValueSize.LARGE,
    color: Color = AndisTheme.colors.textPrimary,
    unitColor: Color = AndisTheme.colors.textSecondary,
    privacyMode: Boolean = false,
) {
    val displayAmount = if (privacyMode) "••••••••" else amount

    Row(
        modifier = modifier,
        verticalAlignment = Alignment.Bottom
    ) {
        Text(
            text = displayAmount,
            style = TextStyle(
                fontFamily = ModamFigures,
                fontWeight = size.weight,
                fontSize = size.amountSize,
                fontFeatureSettings = TABULAR,
                color = color,
                textDirection = TextDirection.Ltr
            )
        )
        if (unit.isNotEmpty()) {
            Spacer(Modifier.width(5.dp))
            Text(
                text = unit,
                style = TextStyle(
                    fontFamily = Modam,
                    fontWeight = FontWeight.Medium,
                    fontSize = size.unitSize,
                    color = unitColor
                ),
                modifier = Modifier.padding(bottom = (size.amountSize.value * 0.12f).dp)
            )
        }
    }
}

/**
 * Financial Delta Pill (+4.82% / -1.24%).
 * Strict functional signal: neon lime (#D4FF00) for gains, technical red (#FF443A) for losses.
 */
@Composable
fun FinancialDelta(
    deltaPercent: Double,
    modifier: Modifier = Modifier,
    prefix: String = "",
    suffix: String = "%",
    showBackground: Boolean = true,
) {
    val isPositive = deltaPercent > 0.001
    val isNegative = deltaPercent < -0.001

    val color = when {
        isPositive -> AndisTheme.colors.accentLime
        isNegative -> AndisTheme.colors.danger
        else -> AndisTheme.colors.textSecondary
    }

    val arrow = when {
        isPositive -> "↑ "
        isNegative -> "↓ "
        else -> "• "
    }

    val formatted = String.format(java.util.Locale.US, "%.2f", Math.abs(deltaPercent))
    val text = "$arrow$prefix$formatted$suffix"

    if (showBackground) {
        Box(
            modifier = modifier
                .background(AndisTheme.colors.surfaceSecondary, RoundedCornerShape(2.dp))
                .border(1.dp, AndisTheme.colors.border, RoundedCornerShape(2.dp))
                .padding(horizontal = 6.dp, vertical = 2.dp),
            contentAlignment = Alignment.Center
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(4.dp)
                        .background(color, CircleShape)
                )
                Spacer(Modifier.width(4.dp))
                Text(
                    text = text,
                    style = TextStyle(
                        fontFamily = ModamFigures,
                        fontWeight = FontWeight.Bold,
                        fontSize = 11.sp,
                        fontFeatureSettings = TABULAR,
                        color = color
                    )
                )
            }
        }
    } else {
        Text(
            text = text,
            modifier = modifier,
            style = TextStyle(
                fontFamily = ModamFigures,
                fontWeight = FontWeight.Bold,
                fontSize = 12.sp,
                fontFeatureSettings = TABULAR,
                color = color
            )
        )
    }
}
