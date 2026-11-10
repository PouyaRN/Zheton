package com.doxigo.muchtoman

import androidx.annotation.DrawableRes
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * Studio-grade branded badges for core Iranian financial & global crypto assets:
 * Tether (₮ USDT), US Dollar ($), Emami Coin (VectorDrawable), 18K Melted Gold (Au), Bitcoin (₿), Ethereum (Ξ).
 */
@Composable
fun SleekAssetBadge(
    assetId: String,
    modifier: Modifier = Modifier,
    size: Dp = 32.dp,
) {
    val key = assetId.lowercase()
    val badgeConfig = when {
        key.contains("usdt") || key.contains("tether") -> BadgeConfig(
            symbol = "₮",
            gradient = listOf(Color(0xFF00B075), Color(0xFF02875A)),
            borderColor = Color(0xFF4ADE80).copy(alpha = 0.4f),
            textColor = Color.White,
            fontSizeRatio = 0.58f,
            fontWeight = FontWeight.Black,
        )
        key == "usd" || key.contains("dollar") -> BadgeConfig(
            symbol = "$",
            gradient = listOf(Color(0xFF15803D), Color(0xFF0F5829)),
            borderColor = Color(0xFF86EFAC).copy(alpha = 0.35f),
            textColor = Color.White,
            fontSizeRatio = 0.55f,
            fontWeight = FontWeight.Black,
        )
        key.contains("gold18") || key.contains("geram18") || (key.contains("gold") && !key.contains("coin")) -> BadgeConfig(
            symbol = "Au",
            gradient = listOf(Color(0xFFF59E0B), Color(0xFFB45309)),
            borderColor = Color(0xFFFDE68A).copy(alpha = 0.5f),
            textColor = Color(0xFFFFFBEB),
            fontSizeRatio = 0.44f,
            fontWeight = FontWeight.ExtraBold,
        )
        key.contains("coin") || key.contains("emami") || key.contains("bahar") || key.contains("rob") || key.contains("nim") -> BadgeConfig(
            symbol = "Au",
            gradient = listOf(Color(0xFFEAB308), Color(0xFFCA8A04)),
            borderColor = Color(0xFFFEF08A).copy(alpha = 0.6f),
            textColor = Color.White,
            fontSizeRatio = 0.50f,
            fontWeight = FontWeight.Bold,
            drawableRes = R.drawable.ic_asset_coin,
        )
        key.contains("btc") || key.contains("bitcoin") -> BadgeConfig(
            symbol = "₿",
            gradient = listOf(Color(0xFFF7931A), Color(0xFFD97706)),
            borderColor = Color(0xFFFED7AA).copy(alpha = 0.4f),
            textColor = Color.White,
            fontSizeRatio = 0.55f,
            fontWeight = FontWeight.Black,
        )
        key.contains("eth") || key.contains("ethereum") -> BadgeConfig(
            symbol = "Ξ",
            gradient = listOf(Color(0xFF627EEA), Color(0xFF3B4F9B)),
            borderColor = Color(0xFFC7D2FE).copy(alpha = 0.4f),
            textColor = Color.White,
            fontSizeRatio = 0.52f,
            fontWeight = FontWeight.Black,
        )
        key.contains("eur") -> BadgeConfig(
            symbol = "€",
            gradient = listOf(Color(0xFF1D4ED8), Color(0xFF1E3A8A)),
            borderColor = Color(0xFF93C5FD).copy(alpha = 0.4f),
            textColor = Color.White,
            fontSizeRatio = 0.55f,
            fontWeight = FontWeight.Black,
        )
        key.contains("gbp") -> BadgeConfig(
            symbol = "£",
            gradient = listOf(Color(0xFF7C3AED), Color(0xFF5B21B6)),
            borderColor = Color(0xFFDDD6FE).copy(alpha = 0.4f),
            textColor = Color.White,
            fontSizeRatio = 0.55f,
            fontWeight = FontWeight.Black,
        )
        else -> BadgeConfig(
            symbol = assetId.take(3).uppercase(),
            gradient = listOf(Color(0xFF475569), Color(0xFF1E293B)),
            borderColor = Color(0xFF94A3B8).copy(alpha = 0.3f),
            textColor = Color(0xFFF1F5F9),
            fontSizeRatio = 0.38f,
            fontWeight = FontWeight.Bold,
        )
    }

    Box(
        modifier = modifier
            .size(size)
            .shadow(2.dp, CircleShape)
            .clip(CircleShape)
            .background(Brush.linearGradient(badgeConfig.gradient))
            .border(1.dp, badgeConfig.borderColor, CircleShape),
        contentAlignment = Alignment.Center,
    ) {
        if (badgeConfig.drawableRes != null) {
            Image(
                painter = painterResource(badgeConfig.drawableRes),
                contentDescription = null,
                modifier = Modifier.size(size * 0.62f),
            )
        } else {
            val fontSize = (size.value * badgeConfig.fontSizeRatio).sp
            Text(
                text = badgeConfig.symbol,
                fontSize = fontSize,
                fontWeight = badgeConfig.fontWeight,
                color = badgeConfig.textColor,
                maxLines = 1,
            )
        }
    }
}

private data class BadgeConfig(
    val symbol: String,
    val gradient: List<Color>,
    val borderColor: Color,
    val textColor: Color,
    val fontSizeRatio: Float,
    val fontWeight: FontWeight,
    @DrawableRes val drawableRes: Int? = null,
)
