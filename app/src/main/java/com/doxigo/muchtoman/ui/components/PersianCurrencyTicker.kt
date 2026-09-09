package com.doxigo.muchtoman.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.spring
import androidx.compose.animation.expandHorizontally
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkHorizontally
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableDoubleStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.doxigo.muchtoman.ModamFigures
import kotlin.math.abs
import kotlin.math.floor

/**
 * Persian digit glyph constants (۰ to ۹).
 */
private val PERSIAN_DIGITS = charArrayOf('۰', '۱', '۲', '۳', '۴', '۵', '۶', '۷', '۸', '۹')

/**
 * Persian thousands separator (U+066C: ARABIC THOUSANDS SEPARATOR).
 */
private const val PERSIAN_THOUSANDS_SEP = '٬'

/**
 * Persian decimal separator (U+066B: ARABIC DECIMAL SEPARATOR).
 */
private const val PERSIAN_DECIMAL_SEP = '٫'

/**
 * Structural model representing a place-value slot in the rolling ticker.
 */
private sealed class TickerSlot {
    abstract val key: String

    data class Digit(
        val exponent: Int,
        val targetDigit: Int,
    ) : TickerSlot() {
        override val key: String get() = "digit_$exponent"
    }

    data class ThousandsSeparator(
        val groupIndex: Int,
    ) : TickerSlot() {
        override val key: String get() = "sep_$groupIndex"
    }

    data class DecimalSeparator(
        val marker: String = "decimal_sep",
    ) : TickerSlot() {
        override val key: String get() = marker
    }

    data class Sign(
        val isNegative: Boolean,
    ) : TickerSlot() {
        override val key: String get() = "sign"
    }
}

/**
 * Deconstructs an arbitrary financial amount into a positional list of [TickerSlot] elements,
 * arranged strictly in Left-to-Right numeric place order (MSD on left, LSD on right).
 */
private fun decomposeAmountToSlots(
    amount: Double,
    decimals: Int = 0,
): List<TickerSlot> {
    val slots = mutableListOf<TickerSlot>()
    val isNegative = amount < 0
    val absAmount = abs(amount)

    if (isNegative) {
        slots.add(TickerSlot.Sign(isNegative = true))
    }

    val integerPart = floor(absAmount).toLong()
    val integerStr = integerPart.toString()
    val numIntDigits = integerStr.length

    // Decompose integer part from Most Significant Digit down to Units (k = numIntDigits - 1 down to 0)
    for (i in 0 until numIntDigits) {
        val exponent = numIntDigits - 1 - i
        val digitChar = integerStr[i]
        val digitVal = digitChar.digitToInt()

        slots.add(TickerSlot.Digit(exponent = exponent, targetDigit = digitVal))

        // Insert thousands separator if exponent is a multiple of 3 and not the units digit (k > 0)
        if (exponent > 0 && exponent % 3 == 0) {
            slots.add(TickerSlot.ThousandsSeparator(groupIndex = exponent / 3))
        }
    }

    // Optional fractional part
    if (decimals > 0) {
        slots.add(TickerSlot.DecimalSeparator())
        val fractionPart = absAmount - integerPart
        var multiplier = 1.0
        for (decExp in 1..decimals) {
            multiplier *= 10.0
            val fractionDigit = (floor(fractionPart * multiplier).toInt() % 10).coerceIn(0, 9)
            slots.add(TickerSlot.Digit(exponent = -decExp, targetDigit = fractionDigit))
        }
    }

    return slots
}

/**
 * Production-ready Persian Rolling Odometer Ticker.
 *
 * Designed for flagship Iranian fintech interfaces, providing 60/120fps fluid kinetic motion
 * with rigid physical place alignment, underdamped spring physics, and zero layout jitter.
 *
 * @param amount Current monetary value.
 * @param modifier Composable layout modifier.
 * @param unit Currency suffix (default: "تومان").
 * @param decimals Number of decimal digits to render (default: 0).
 * @param fontSize Typography size (default: 36.sp).
 * @param textColor Color of the numerals and separators.
 * @param unitColor Color of the trailing currency unit.
 * @param showVignette Whether to render specular vertical vignette overlays on each cylinder.
 */
@Composable
fun PersianCurrencyTicker(
    amount: Double,
    modifier: Modifier = Modifier,
    unit: String = "تومان",
    decimals: Int = 0,
    fontSize: TextUnit = 36.sp,
    textColor: Color = Color(0xFFF1F3F4),
    unitColor: Color = Color(0xFFB0B5B9),
    showVignette: Boolean = true,
) {
    var previousAmount by remember { mutableDoubleStateOf(amount) }
    val isIncreasing = amount >= previousAmount

    SideEffect {
        previousAmount = amount
    }

    val textStyle = remember(fontSize, textColor) {
        TextStyle(
            fontFamily = ModamFigures,
            fontWeight = FontWeight.ExtraBold,
            fontSize = fontSize,
            fontFeatureSettings = "tnum",
            color = textColor,
        )
    }

    // Pre-calculate exact tabular dimensions using Compose TextMeasurer
    val density = LocalDensity.current
    val textMeasurer = rememberTextMeasurer()
    val measuredDimensions = remember(textStyle, density) {
        val digitMeasure = textMeasurer.measure(
            text = "۸",
            style = textStyle,
        )
        val sepMeasure = textMeasurer.measure(
            text = PERSIAN_THOUSANDS_SEP.toString(),
            style = textStyle,
        )
        val digitWidthDp = with(density) { digitMeasure.size.width.toDp() }
        val digitHeightDp = with(density) { digitMeasure.size.height.toDp() }
        val sepWidthDp = with(density) { sepMeasure.size.width.toDp() }
        Triple(digitWidthDp, digitHeightDp, sepWidthDp)
    }

    val (digitWidth, digitHeight, sepWidth) = measuredDimensions
    val slots = remember(amount, decimals) { decomposeAmountToSlots(amount, decimals) }

    // Outer layout: Authentic Persian RTL flow
    CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
        Row(
            modifier = modifier,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            // Numeric Container: Isolated Left-to-Right Island for correct mathematical magnitude ordering
            CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    slots.forEach { slot ->
                        when (slot) {
                            is TickerSlot.Sign -> {
                                Text(
                                    text = if (slot.isNegative) "−" else "+",
                                    style = textStyle,
                                )
                            }
                            is TickerSlot.ThousandsSeparator -> {
                                AnimatedVisibility(
                                    visible = true,
                                    enter = expandHorizontally(
                                        animationSpec = spring(
                                            dampingRatio = 0.8f,
                                            stiffness = 380f,
                                        ),
                                    ) + fadeIn(),
                                    exit = shrinkHorizontally(
                                        animationSpec = spring(
                                            dampingRatio = 0.8f,
                                            stiffness = 380f,
                                        ),
                                    ) + fadeOut(),
                                    modifier = Modifier.width(sepWidth),
                                ) {
                                    Box(
                                        modifier = Modifier.width(sepWidth),
                                        contentAlignment = Alignment.Center,
                                    ) {
                                        Text(
                                            text = PERSIAN_THOUSANDS_SEP.toString(),
                                            style = textStyle,
                                        )
                                    }
                                }
                            }
                            is TickerSlot.DecimalSeparator -> {
                                Box(
                                    modifier = Modifier.width(sepWidth),
                                    contentAlignment = Alignment.Center,
                                ) {
                                    Text(
                                        text = PERSIAN_DECIMAL_SEP.toString(),
                                        style = textStyle,
                                    )
                                }
                            }
                            is TickerSlot.Digit -> {
                                AnimatedVisibility(
                                    visible = true,
                                    enter = expandHorizontally(
                                        animationSpec = spring(
                                            dampingRatio = 0.8f,
                                            stiffness = 380f,
                                        ),
                                    ) + fadeIn(),
                                    exit = shrinkHorizontally(
                                        animationSpec = spring(
                                            dampingRatio = 0.8f,
                                            stiffness = 380f,
                                        ),
                                    ) + fadeOut(),
                                    modifier = Modifier.width(digitWidth),
                                ) {
                                    SingleDigitCylinder(
                                        targetDigit = slot.targetDigit,
                                        isIncreasing = isIncreasing,
                                        digitWidth = digitWidth,
                                        digitHeight = digitHeight,
                                        textStyle = textStyle,
                                        showVignette = showVignette,
                                    )
                                }
                            }
                        }
                    }
                }
            }

            // Currency Unit Suffix (renders on the left in RTL)
            if (unit.isNotBlank()) {
                Box(
                    modifier = Modifier.graphicsLayer { translationX = 0f },
                ) {
                    Text(
                        text = " $unit",
                        style = textStyle.copy(
                            fontSize = (fontSize.value * 0.44f).sp,
                            fontWeight = FontWeight.Bold,
                            color = unitColor,
                        ),
                    )
                }
            }
        }
    }
}

/**
 * Individual physical cylinder rendering a single digit column (۰ to ۹).
 */
@Composable
private fun SingleDigitCylinder(
    targetDigit: Int,
    isIncreasing: Boolean,
    digitWidth: Dp,
    digitHeight: Dp,
    textStyle: TextStyle,
    showVignette: Boolean,
) {
    // Current continuous rotation position of the cylinder
    val animatedPosition = remember { Animatable(targetDigit.toFloat()) }
    var currentTarget by remember { mutableStateOf(targetDigit) }

    LaunchedEffect(targetDigit, isIncreasing) {
        val oldVal = currentTarget
        currentTarget = targetDigit

        if (oldVal != targetDigit) {
            // Calculate directional delta ensuring mechanical consistency across all wheels
            val delta = if (isIncreasing) {
                if (targetDigit >= oldVal) {
                    (targetDigit - oldVal).toFloat()
                } else {
                    (10 - oldVal + targetDigit).toFloat()
                }
            } else {
                if (targetDigit <= oldVal) {
                    (targetDigit - oldVal).toFloat()
                } else {
                    -(oldVal + (10 - targetDigit)).toFloat()
                }
            }

            val targetValue = animatedPosition.value + delta

            animatedPosition.animateTo(
                targetValue = targetValue,
                animationSpec = spring(
                    dampingRatio = 0.8f,
                    stiffness = 380f,
                ),
            )

            // Normalize base offset modulo 10 to prevent unbounded float accumulation
            val normalized = ((animatedPosition.value % 10f) + 10f) % 10f
            animatedPosition.snapTo(normalized)
        }
    }

    val density = LocalDensity.current
    val digitHeightPx = with(density) { digitHeight.toPx() }

    Box(
        modifier = Modifier
            .width(digitWidth)
            .height(digitHeight)
            .clipToBounds(),
        contentAlignment = Alignment.Center,
    ) {
        // Render 10 discrete digit layers positioned along the vertical cylinder barrel
        for (digitIndex in 0..9) {
            val glyph = PERSIAN_DIGITS[digitIndex]

            Box(
                modifier = Modifier
                    .width(digitWidth)
                    .height(digitHeight)
                    .graphicsLayer {
                        val currentVal = animatedPosition.value
                        // Compute cyclic offset relative to the active viewport
                        var diff = (digitIndex.toFloat() - currentVal) % 10f
                        if (diff > 5f) diff -= 10f
                        if (diff < -5f) diff += 10f

                        translationY = diff * digitHeightPx
                        // Subtle perspective scale and alpha attenuation for entering/exiting glyphs
                        val distance = abs(diff)
                        alpha = (1f - (distance * 0.65f)).coerceIn(0f, 1f)
                    },
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = glyph.toString(),
                    style = textStyle,
                    maxLines = 1,
                )
            }
        }

        // Specular Vignette: Simulates bevel depth and dial casing shadows
        if (showVignette) {
            Box(
                modifier = Modifier
                    .matchParentSize()
                    .background(
                        Brush.verticalGradient(
                            0.0f to Color.Black.copy(alpha = 0.45f),
                            0.18f to Color.Transparent,
                            0.82f to Color.Transparent,
                            1.0f to Color.Black.copy(alpha = 0.45f),
                        ),
                    ),
            )
        }
    }
}
