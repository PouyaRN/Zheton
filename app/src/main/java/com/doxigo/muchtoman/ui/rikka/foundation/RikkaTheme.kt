package com.doxigo.muchtoman.ui.rikka.foundation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.ui.graphics.Color

object RikkaTheme {
    val colors: RikkaColors
        @Composable
        @ReadOnlyComposable
        get() = LocalRikkaColors.current

    val shapes: RikkaShapes
        @Composable
        @ReadOnlyComposable
        get() = LocalRikkaShapes.current

    val spacing: RikkaSpacing
        @Composable
        @ReadOnlyComposable
        get() = LocalRikkaSpacing.current

    val motion: RikkaMotion
        @Composable
        @ReadOnlyComposable
        get() = LocalRikkaMotion.current
}

@Composable
fun RikkaTheme(
    colors: RikkaColors = NeobankRikkaPalette.MidnightNavyDark,
    shapes: RikkaShapes = RikkaShapes(),
    spacing: RikkaSpacing = RikkaSpacing(),
    motion: RikkaMotion = RikkaMotion(),
    content: @Composable () -> Unit,
) {
    CompositionLocalProvider(
        LocalRikkaColors provides colors,
        LocalContentColor provides colors.onSurface,
        LocalRikkaShapes provides shapes,
        LocalRikkaSpacing provides spacing,
        LocalRikkaMotion provides motion,
        content = content,
    )
}
