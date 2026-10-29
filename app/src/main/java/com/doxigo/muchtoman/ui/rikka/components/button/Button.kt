package com.doxigo.muchtoman.ui.rikka.components.button

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.doxigo.muchtoman.Modam
import com.doxigo.muchtoman.ui.rikka.foundation.LocalContentColor
import com.doxigo.muchtoman.ui.rikka.foundation.RikkaTheme

enum class ButtonVariant {
    Default,
    Secondary,
    Outline,
    Ghost,
    Destructive,
}

enum class ButtonSize {
    Sm,
    Default,
    Lg,
    Icon,
}

/**
 * Rikka Button — shadcn/ui inspired button for Compose Foundation with spring press animation.
 */
@Composable
fun Button(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    variant: ButtonVariant = ButtonVariant.Default,
    size: ButtonSize = ButtonSize.Default,
    enabled: Boolean = true,
    shape: Shape = RoundedCornerShape(10.dp),
    content: @Composable () -> Unit,
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()

    val scale by animateFloatAsState(
        targetValue = if (enabled && isPressed) 0.96f else 1.0f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessMedium
        ),
        label = "rikka_btn_scale"
    )

    val colors = RikkaTheme.colors
    val (bg, contentColor, border) = when (variant) {
        ButtonVariant.Default -> Triple(colors.primary, colors.onPrimary, Color.Transparent)
        ButtonVariant.Secondary -> Triple(colors.secondary, colors.onSecondary, Color.Transparent)
        ButtonVariant.Outline -> Triple(Color.Transparent, colors.onSurface, colors.border)
        ButtonVariant.Ghost -> Triple(Color.Transparent, colors.onSurface, Color.Transparent)
        ButtonVariant.Destructive -> Triple(colors.destructive, colors.onDestructive, Color.Transparent)
    }

    val (minHeight, padH, padV) = when (size) {
        ButtonSize.Sm -> Triple(32.dp, 12.dp, 4.dp)
        ButtonSize.Default -> Triple(40.dp, 16.dp, 8.dp)
        ButtonSize.Lg -> Triple(48.dp, 20.dp, 12.dp)
        ButtonSize.Icon -> Triple(40.dp, 8.dp, 8.dp)
    }

    CompositionLocalProvider(LocalContentColor provides if (enabled) contentColor else colors.onMuted) {
        Box(
            modifier = modifier
                .graphicsLayer {
                    scaleX = scale
                    scaleY = scale
                }
                .defaultMinSize(minHeight = minHeight)
                .clip(shape)
                .background(if (enabled) bg else colors.muted.copy(alpha = 0.5f), shape)
                .then(if (border != Color.Transparent) Modifier.border(1.dp, border, shape) else Modifier)
                .clickable(
                    interactionSource = interactionSource,
                    indication = null,
                    enabled = enabled,
                    onClick = onClick
                )
                .padding(horizontal = padH, vertical = padV),
            contentAlignment = Alignment.Center
        ) {
            content()
        }
    }
}

@Composable
fun Button(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    variant: ButtonVariant = ButtonVariant.Default,
    size: ButtonSize = ButtonSize.Default,
    enabled: Boolean = true,
) {
    Button(
        onClick = onClick,
        modifier = modifier,
        variant = variant,
        size = size,
        enabled = enabled,
    ) {
        Text(
            text = text,
            style = TextStyle(
                fontFamily = Modam,
                fontWeight = FontWeight.Bold,
                fontSize = if (size == ButtonSize.Sm) 12.sp else 14.sp,
                color = LocalContentColor.current
            )
        )
    }
}
