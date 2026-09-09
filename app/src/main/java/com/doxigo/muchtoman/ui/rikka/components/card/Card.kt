package com.doxigo.muchtoman.ui.rikka.components.card

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.doxigo.muchtoman.Modam
import com.doxigo.muchtoman.ui.rikka.foundation.LocalContentColor
import com.doxigo.muchtoman.ui.rikka.foundation.RikkaTheme

enum class CardVariant {
    Default,
    Elevated,
    Ghost,
}

enum class CardAnimation {
    Press,
    None,
}

@Immutable
data class CardColorValues(
    val background: Color,
    val border: Color,
    val elevation: Dp,
)

object CardDefaults {
    @Composable
    fun shape(): Shape = RikkaTheme.shapes.lg

    @Composable
    fun colors(variant: CardVariant = CardVariant.Default): CardColorValues {
        val themeColors = RikkaTheme.colors
        return when (variant) {
            CardVariant.Default -> CardColorValues(
                background = themeColors.surface,
                border = themeColors.border,
                elevation = 0.dp
            )
            CardVariant.Elevated -> CardColorValues(
                background = themeColors.surfaceElevated,
                border = themeColors.border.copy(alpha = 0.6f),
                elevation = 2.dp
            )
            CardVariant.Ghost -> CardColorValues(
                background = Color.Transparent,
                border = Color.Transparent,
                elevation = 0.dp
            )
        }
    }
}

/**
 * Rikka Card — shadcn/ui inspired container for Compose Foundation with spring press animation.
 */
@Composable
fun Card(
    modifier: Modifier = Modifier,
    variant: CardVariant = CardVariant.Default,
    onClick: (() -> Unit)? = null,
    animation: CardAnimation = CardAnimation.Press,
    colors: CardColorValues = CardDefaults.colors(variant),
    shape: Shape = CardDefaults.shape(),
    content: @Composable ColumnScope.() -> Unit,
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val isClickable = onClick != null
    val hasAnimation = isClickable && animation == CardAnimation.Press

    val scale by animateFloatAsState(
        targetValue = if (hasAnimation && isPressed) 0.98f else 1.0f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessMedium
        ),
        label = "rikka_card_scale"
    )

    val borderModifier = if (colors.border != Color.Transparent) {
        Modifier.border(1.dp, colors.border, shape)
    } else {
        Modifier
    }

    val clickModifier = if (onClick != null) {
        Modifier.clickable(
            interactionSource = interactionSource,
            indication = null,
            onClick = onClick
        )
    } else {
        Modifier
    }

    CompositionLocalProvider(LocalContentColor provides RikkaTheme.colors.onSurface) {
        Column(
            modifier = modifier
                .graphicsLayer {
                    scaleX = scale
                    scaleY = scale
                }
                .clip(shape)
                .background(colors.background, shape)
                .then(borderModifier)
                .then(clickModifier)
                .padding(16.dp),
            content = content
        )
    }
}

@Composable
fun CardHeader(
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(4.dp),
        content = content
    )
}

@Composable
fun CardTitle(
    text: String,
    modifier: Modifier = Modifier,
    color: Color = RikkaTheme.colors.onSurface,
) {
    androidx.compose.material3.Text(
        text = text,
        style = TextStyle(
            fontFamily = Modam,
            fontWeight = FontWeight.Bold,
            fontSize = 16.sp,
            color = color
        ),
        modifier = modifier
    )
}

@Composable
fun CardDescription(
    text: String,
    modifier: Modifier = Modifier,
    color: Color = RikkaTheme.colors.onMuted,
) {
    androidx.compose.material3.Text(
        text = text,
        style = TextStyle(
            fontFamily = Modam,
            fontSize = 12.sp,
            color = color
        ),
        modifier = modifier
    )
}

@Composable
fun CardContent(
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(
        modifier = modifier.padding(top = 12.dp),
        content = content
    )
}
