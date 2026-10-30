package com.doxigo.muchtoman.widget.primitives

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.doxigo.muchtoman.ZhetonTheme
import com.doxigo.muchtoman.Modam
import com.doxigo.muchtoman.ModamFigures
import com.doxigo.muchtoman.TABULAR

/**
 * Technical Financial Timeline.
 * Renders installment / milestone progress: ●━━●━━●━━○━━○
 */
@Composable
fun FinancialTimeline(
    totalSteps: Int,
    currentStep: Int,
    modifier: Modifier = Modifier,
    activeColor: Color = ZhetonTheme.colors.accentLime,
    inactiveColor: Color = ZhetonTheme.colors.border,
    surfaceSecondary: Color = ZhetonTheme.colors.surfaceSecondary,
    dotRadius: Dp = 3.5.dp,
    lineHeight: Dp = 1.5.dp,
) {
    if (totalSteps <= 1) return
    val isRtl = LocalLayoutDirection.current == LayoutDirection.Rtl

    Canvas(
        modifier = modifier
            .fillMaxWidth()
            .height(dotRadius * 3)
    ) {
        val stepX = size.width / (totalSteps - 1).coerceAtLeast(1)
        val centerY = size.height / 2f
        val radiusPx = dotRadius.toPx()
        val strokePx = lineHeight.toPx()

        for (i in 0 until totalSteps - 1) {
            val startX = i * stepX
            val endX = (i + 1) * stepX

            val isLineActive = if (isRtl) {
                (totalSteps - 1 - i) <= currentStep
            } else {
                i < currentStep
            }

            drawLine(
                color = if (isLineActive) activeColor else inactiveColor,
                start = Offset(startX, centerY),
                end = Offset(endX, centerY),
                strokeWidth = strokePx,
                cap = StrokeCap.Round
            )
        }

        for (i in 0 until totalSteps) {
            val x = i * stepX
            val isDotActive = if (isRtl) {
                (totalSteps - 1 - i) < currentStep
            } else {
                i < currentStep
            }

            drawCircle(
                color = if (isDotActive) activeColor else surfaceSecondary,
                radius = radiusPx,
                center = Offset(x, centerY)
            )
            if (!isDotActive) {
                drawCircle(
                    color = inactiveColor,
                    radius = radiusPx,
                    center = Offset(x, centerY),
                    style = androidx.compose.ui.graphics.drawscope.Stroke(width = 1.2.dp.toPx())
                )
            }
        }
    }
}

/**
 * Pulsing or Solid Status Indicator.
 */
@Composable
fun StatusIndicator(
    active: Boolean = true,
    modifier: Modifier = Modifier,
    activeColor: Color = ZhetonTheme.colors.accentLime,
    inactiveColor: Color = ZhetonTheme.colors.textMuted,
    pulsing: Boolean = false,
    size: Dp = 6.dp,
) {
    val infiniteTransition = rememberInfiniteTransition(label = "statusPulse")
    val alpha by infiniteTransition.animateFloat(
        initialValue = if (pulsing && active) 0.35f else 1f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(700, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "statusAlpha"
    )

    Box(
        modifier = modifier
            .size(size)
            .background(
                color = (if (active) activeColor else inactiveColor).copy(alpha = if (pulsing) alpha else 1f),
                shape = CircleShape
            )
    )
}

/**
 * Standard Telemetry Metric Block.
 */
@Composable
fun Metric(
    label: String,
    value: String,
    modifier: Modifier = Modifier,
    unit: String = "",
    delta: Double? = null,
) {
    Column(modifier = modifier) {
        Text(
            text = label.uppercase(),
            style = TextStyle(
                fontFamily = Modam,
                fontWeight = FontWeight.Medium,
                fontSize = 10.sp,
                color = ZhetonTheme.colors.textMuted
            )
        )
        Spacer(Modifier.height(2.dp))
        Row(verticalAlignment = Alignment.Bottom) {
            Text(
                text = value,
                style = TextStyle(
                    fontFamily = ModamFigures,
                    fontWeight = FontWeight.Bold,
                    fontSize = 15.sp,
                    fontFeatureSettings = TABULAR,
                    color = ZhetonTheme.colors.textPrimary
                )
            )
            if (unit.isNotEmpty()) {
                Spacer(Modifier.width(3.dp))
                Text(
                    text = unit,
                    style = TextStyle(
                        fontFamily = Modam,
                        fontWeight = FontWeight.Normal,
                        fontSize = 10.sp,
                        color = ZhetonTheme.colors.textSecondary
                    )
                )
            }
            if (delta != null) {
                Spacer(Modifier.width(6.dp))
                FinancialDelta(deltaPercent = delta, showBackground = false)
            }
        }
    }
}
