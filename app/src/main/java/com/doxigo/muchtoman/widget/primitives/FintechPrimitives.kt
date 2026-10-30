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
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDirection
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.doxigo.muchtoman.ZhetonTheme
import com.doxigo.muchtoman.Modam
import com.doxigo.muchtoman.ModamFigures
import com.doxigo.muchtoman.TABULAR

/**
 * Revolut 10-Style Quick Actions Dock.
 * 4 thumb-friendly circular controls for rapid financial interactions.
 */
@Composable
fun QuickActionsDock(
    onAdd: () -> Unit,
    onTransfer: () -> Unit,
    onCards: () -> Unit,
    onAnalytics: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        QuickActionButton(
            label = "افزودن",
            iconText = "+",
            isPrimary = true,
            onClick = onAdd
        )
        QuickActionButton(
            label = "انتقال",
            iconText = "⇄",
            onClick = onTransfer
        )
        QuickActionButton(
            label = "کارت‌ها",
            iconText = "💳",
            onClick = onCards
        )
        QuickActionButton(
            label = "تحلیل",
            iconText = "📊",
            onClick = onAnalytics
        )
    }
}

@Composable
private fun QuickActionButton(
    label: String,
    iconText: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    isPrimary: Boolean = false,
) {
    Column(
        modifier = modifier.clickable(onClick = onClick),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(
            modifier = Modifier
                .size(52.dp)
                .clip(CircleShape)
                .background(
                    if (isPrimary) ZhetonTheme.colors.accentLime
                    else MaterialTheme.colorScheme.surfaceContainerHigh
                )
                .border(
                    1.dp,
                    if (isPrimary) Color.Transparent else ZhetonTheme.colors.border,
                    CircleShape
                ),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = iconText,
                fontSize = if (iconText == "+" || iconText == "⇄") 22.sp else 18.sp,
                fontWeight = FontWeight.Bold,
                color = if (isPrimary) Color(0xFF070A0F) else ZhetonTheme.colors.textPrimary
            )
        }
        Spacer(Modifier.height(6.dp))
        Text(
            text = label,
            style = TextStyle(
                fontFamily = Modam,
                fontWeight = FontWeight.SemiBold,
                fontSize = 12.sp,
                color = ZhetonTheme.colors.textSecondary
            )
        )
    }
}

/**
 * Premium Neobank Card Container.
 * Midnight navy surface with 16dp rounded corners and subtle hairline border.
 */
@Composable
fun NeobankCard(
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    content: @Composable () -> Unit,
) {
    Card(
        modifier = modifier
            .fillMaxWidth()
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            content()
        }
    }
}

/**
 * Monzo-Style Spaces / Pots Progress Item.
 */
@Composable
fun PotsProgress(
    title: String,
    currentAmount: String,
    targetAmount: String,
    progress: Float,
    modifier: Modifier = Modifier,
    iconEmoji: String = "🎯",
    tintColor: Color = ZhetonTheme.colors.accentLime,
    onClick: (() -> Unit)? = null,
) {
    NeobankCard(modifier = modifier, onClick = onClick) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(36.dp)
                        .clip(CircleShape)
                        .background(tintColor.copy(alpha = 0.18f)),
                    contentAlignment = Alignment.Center
                ) {
                    Text(iconEmoji, fontSize = 16.sp)
                }
                Spacer(Modifier.width(10.dp))
                Column {
                    Text(
                        text = title,
                        style = TextStyle(
                            fontFamily = Modam,
                            fontWeight = FontWeight.Bold,
                            fontSize = 14.sp,
                            color = ZhetonTheme.colors.textPrimary
                        )
                    )
                    Text(
                        text = "$currentAmount از $targetAmount",
                        style = TextStyle(
                            fontFamily = Modam,
                            fontSize = 11.sp,
                            color = ZhetonTheme.colors.textSecondary
                        )
                    )
                }
            }
            Text(
                text = "${(progress * 100).toInt()}%",
                style = TextStyle(
                    fontFamily = ModamFigures,
                    fontWeight = FontWeight.Black,
                    fontSize = 13.sp,
                    fontFeatureSettings = TABULAR,
                    color = tintColor
                )
            )
        }
        Spacer(Modifier.height(10.dp))
        DotProgressBar(
            progress = progress,
            activeColor = tintColor,
            inactiveColor = ZhetonTheme.colors.border
        )
    }
}

/**
 * Monzo / N26-Style Transaction Feed Item.
 */
@Composable
fun TransactionFeedItem(
    merchant: String,
    category: String,
    amountFormatted: String,
    timeFormatted: String,
    isExpense: Boolean,
    modifier: Modifier = Modifier,
    bankName: String? = null,
    onClick: (() -> Unit)? = null,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(vertical = 10.dp, horizontal = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(
            modifier = Modifier.weight(1f),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(CircleShape)
                    .background(
                        if (isExpense) MaterialTheme.colorScheme.surfaceContainerHighest.copy(alpha = 0.5f)
                        else ZhetonTheme.colors.emerald.copy(alpha = 0.18f)
                    ),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = if (isExpense) "🛍️" else "💳",
                    fontSize = 16.sp
                )
            }
            Spacer(Modifier.width(12.dp))
            Column {
                Text(
                    text = merchant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    style = TextStyle(
                        fontFamily = Modam,
                        fontWeight = FontWeight.Bold,
                        fontSize = 14.sp,
                        color = ZhetonTheme.colors.textPrimary
                    )
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = category,
                        style = TextStyle(
                            fontFamily = Modam,
                            fontSize = 11.sp,
                            color = ZhetonTheme.colors.textSecondary
                        )
                    )
                    if (bankName != null) {
                        Text(
                            text = " • $bankName",
                            style = TextStyle(
                                fontFamily = Modam,
                                fontSize = 11.sp,
                                color = ZhetonTheme.colors.textMuted
                            )
                        )
                    }
                }
            }
        }
        Column(horizontalAlignment = Alignment.End) {
            val prefix = if (isExpense) "- " else "+ "
            Text(
                text = prefix + amountFormatted,
                style = TextStyle(
                    fontFamily = ModamFigures,
                    fontWeight = FontWeight.Black,
                    fontSize = 14.sp,
                    fontFeatureSettings = TABULAR,
                    color = if (isExpense) ZhetonTheme.colors.textPrimary else ZhetonTheme.colors.emerald,
                    textDirection = TextDirection.Ltr
                )
            )
            Text(
                text = timeFormatted,
                style = TextStyle(
                    fontFamily = ModamFigures,
                    fontSize = 11.sp,
                    fontFeatureSettings = TABULAR,
                    color = ZhetonTheme.colors.textMuted
                )
            )
        }
    }
}
