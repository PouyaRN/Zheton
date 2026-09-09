package com.doxigo.muchtoman.ui.rikka.foundation

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

@Immutable
data class RikkaColors(
    val background: Color,
    val onBackground: Color,
    val surface: Color,
    val onSurface: Color,
    val primary: Color,
    val onPrimary: Color,
    val secondary: Color,
    val onSecondary: Color,
    val muted: Color,
    val onMuted: Color,
    val destructive: Color,
    val onDestructive: Color,
    val success: Color,
    val onSuccess: Color,
    val warning: Color,
    val onWarning: Color,
    val border: Color,
    val ring: Color,
    val primaryHover: Color = Color.Unspecified,
    val primaryPressed: Color = Color.Unspecified,
    val surfaceHover: Color = Color.Unspecified,
    val surfacePressed: Color = Color.Unspecified,
)

val LocalRikkaColors = staticCompositionLocalOf<RikkaColors> {
    NeobankRikkaPalette.MidnightNavyDark
}

val LocalContentColor = staticCompositionLocalOf { Color(0xFFF8FAFC) }

object NeobankRikkaPalette {
    val MidnightNavyDark = RikkaColors(
        background = Color(0xFF070A0F),
        onBackground = Color(0xFFF8FAFC),
        surface = Color(0xFF0F1622),
        onSurface = Color(0xFFF8FAFC),
        primary = Color(0xFF38BDF8),
        onPrimary = Color(0xFF070A0F),
        secondary = Color(0xFF162030),
        onSecondary = Color(0xFFF8FAFC),
        muted = Color(0xFF162030),
        onMuted = Color(0xFF64748B),
        destructive = Color(0xFFF43F5E),
        onDestructive = Color(0xFFFFFFFF),
        success = Color(0xFF10B981),
        onSuccess = Color(0xFFFFFFFF),
        warning = Color(0xFFF59E0B),
        onWarning = Color(0xFF070A0F),
        border = Color(0xFF1E293B),
        ring = Color(0xFF38BDF8),
    )
}
