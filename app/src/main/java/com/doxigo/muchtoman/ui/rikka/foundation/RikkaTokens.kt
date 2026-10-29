package com.doxigo.muchtoman.ui.rikka.foundation

import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.dp

@Immutable
data class RikkaShapes(
    val sm: Shape = RoundedCornerShape(10.dp),
    val md: Shape = RoundedCornerShape(14.dp),
    val lg: Shape = RoundedCornerShape(22.dp),
    val xl: Shape = RoundedCornerShape(28.dp),
    val full: Shape = CircleShape,
)

val LocalRikkaShapes = staticCompositionLocalOf { RikkaShapes() }

@Immutable
data class RikkaSpacing(
    val xs: androidx.compose.ui.unit.Dp = 4.dp,
    val s: androidx.compose.ui.unit.Dp = 8.dp,
    val m: androidx.compose.ui.unit.Dp = 12.dp,
    val l: androidx.compose.ui.unit.Dp = 16.dp,
    val xl: androidx.compose.ui.unit.Dp = 20.dp,
    val xxl: androidx.compose.ui.unit.Dp = 24.dp,
)

val LocalRikkaSpacing = staticCompositionLocalOf { RikkaSpacing() }

@Immutable
data class RikkaMotion(
    val pressedScale: Float = 0.97f,
    val hoverScale: Float = 1.02f,
)

val LocalRikkaMotion = staticCompositionLocalOf { RikkaMotion() }
