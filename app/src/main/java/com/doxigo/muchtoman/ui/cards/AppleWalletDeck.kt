package com.doxigo.muchtoman.ui.cards

import android.widget.Toast
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.doxigo.muchtoman.BankAccount
import com.doxigo.muchtoman.BankCard3D
import com.doxigo.muchtoman.BankCardThemes
import com.doxigo.muchtoman.Hero
import com.doxigo.muchtoman.ModamFigures
import com.doxigo.muchtoman.Radius
import com.doxigo.muchtoman.Space
import com.doxigo.muchtoman.fa
import com.doxigo.muchtoman.faCompact
import com.doxigo.muchtoman.formatCardPanLtr
import com.doxigo.muchtoman.toAsciiDigits
import com.doxigo.muchtoman.ui.haptics.LocalFintechHaptics

/**
 * High-End 3D Bank Card Stage & Action Hub for Tab 3 (Cards).
 * Elevates bank cards into physical prominence with real-time 3D tilt physics,
 * horizontal fast-switcher chips, and a floating glass action dock with zero emojis.
 */
@Composable
fun AppleWalletAccordionDeck(
    accounts: List<BankAccount>,
    modifier: Modifier = Modifier,
    cardHolderName: String = "",
    privacyMode: Boolean = false,
    onCardClick: (BankAccount) -> Unit = {},
    onCardLongPress: (BankAccount) -> Unit = {},
    onFilterLedger: (BankAccount) -> Unit = {},
) {
    if (accounts.isEmpty()) return

    val fintechHaptics = LocalFintechHaptics.current
    val context = LocalContext.current
    val clipboardManager = LocalClipboardManager.current

    // Active featured card key
    var activeCardKey by remember(accounts) {
        mutableStateOf(accounts.first().key)
    }

    val activeAccount = accounts.find { it.key == activeCardKey } ?: accounts.first()
    val activePalette = remember(activeAccount.bankFa, activeAccount.mask, activeAccount.theme) {
        BankCardThemes.resolve(activeAccount.bankFa, activeAccount.mask, activeAccount.theme)
    }

    val rawDigits = toAsciiDigits(activeAccount.mask).filter { it.isDigit() }
    val copyPan = if (rawDigits.length >= 16) rawDigits.take(16) else rawDigits.ifBlank { activeAccount.mask }

    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        // Multi-card switcher chips (when user has 2+ accounts)
        if (accounts.size > 1) {
            val scrollState = rememberScrollState()
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(scrollState)
                    .padding(horizontal = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                accounts.forEachIndexed { index, account ->
                    val isSelected = account.key == activeAccount.key
                    val palette = remember(account.bankFa, account.mask, account.theme) {
                        BankCardThemes.resolve(account.bankFa, account.mask, account.theme)
                    }

                    Surface(
                        modifier = Modifier
                            .clip(RoundedCornerShape(Radius.pill))
                            .clickable {
                                fintechHaptics.click()
                                activeCardKey = account.key
                            },
                        shape = RoundedCornerShape(Radius.pill),
                        color = if (isSelected) {
                            palette.accentColor.copy(alpha = 0.18f)
                        } else {
                            MaterialTheme.colorScheme.surfaceContainerHighest.copy(alpha = 0.45f)
                        },
                        border = androidx.compose.foundation.BorderStroke(
                            1.dp,
                            if (isSelected) palette.accentColor else MaterialTheme.colorScheme.outline.copy(alpha = 0.2f),
                        ),
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(8.dp)
                                    .clip(CircleShape)
                                    .background(palette.accentColor),
                            )
                            Text(
                                text = account.bankFa,
                                fontSize = 12.sp,
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                color = if (isSelected) palette.accentColor else MaterialTheme.colorScheme.onSurface,
                            )
                            val lastFour = if (account.mask.length >= 4) account.mask.takeLast(4) else ""
                            if (lastFour.isNotBlank()) {
                                Text(
                                    text = "••$lastFour",
                                    fontSize = 10.sp,
                                    fontFamily = ModamFigures,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                                )
                            }
                        }
                    }
                }
            }
        }

        // The Hero 3D Card Stage
        AnimatedContent(
            targetState = activeAccount,
            transitionSpec = {
                fadeIn(spring(stiffness = Spring.StiffnessMediumLow)) togetherWith
                    fadeOut(spring(stiffness = Spring.StiffnessMediumLow))
            },
            label = "cardStageTransition",
        ) { targetAccount ->
            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                BankCard3D(
                    bankAccount = targetAccount,
                    cardHolderName = cardHolderName,
                    modifier = Modifier.fillMaxWidth(),
                    onClick = {
                        fintechHaptics.click()
                        onCardClick(targetAccount)
                    },
                    onLongClick = {
                        fintechHaptics.heavyClick()
                        onCardLongPress(targetAccount)
                    },
                    enable3DTiltDrag = true,
                    privacyMode = privacyMode,
                )
            }
        }

        // Luxury Floating Glass Action Dock (Zero Emojis, Pure Vector Glyphs)
        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(20.dp),
            color = MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.85f),
            border = androidx.compose.foundation.BorderStroke(
                1.dp,
                MaterialTheme.colorScheme.outline.copy(alpha = 0.22f),
            ),
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = Space.s, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceEvenly,
            ) {
                // Action 1: Copy Card PAN
                if (copyPan.isNotBlank()) {
                    Row(
                        modifier = Modifier
                            .clip(RoundedCornerShape(Radius.pill))
                            .clickable {
                                fintechHaptics.doubleTap()
                                clipboardManager.setText(AnnotatedString(copyPan))
                                Toast.makeText(context, "شماره کارت کپی شد", Toast.LENGTH_SHORT).show()
                            }
                            .padding(horizontal = 10.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        CardCopyGlyph(tint = activePalette.accentColor)
                        Text(
                            text = "کپی کارت",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            color = activePalette.accentColor,
                        )
                    }
                }

                // Divider
                if (copyPan.isNotBlank() && activeAccount.sheba.isNotBlank()) {
                    Box(
                        Modifier
                            .width(1.dp)
                            .height(18.dp)
                            .background(MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f)),
                    )
                }

                // Action 2: Copy SHEBA
                if (activeAccount.sheba.isNotBlank()) {
                    Row(
                        modifier = Modifier
                            .clip(RoundedCornerShape(Radius.pill))
                            .clickable {
                                fintechHaptics.doubleTap()
                                val cleanSheba = activeAccount.sheba.replace(" ", "").trim()
                                clipboardManager.setText(AnnotatedString(cleanSheba))
                                Toast.makeText(context, "شماره شبا کپی شد", Toast.LENGTH_SHORT).show()
                            }
                            .padding(horizontal = 10.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        ShebaGlyph(tint = Hero.gold)
                        Text(
                            text = "کپی شبا",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            color = Hero.gold,
                        )
                    }
                }

                // Divider
                Box(
                    Modifier
                        .width(1.dp)
                        .height(18.dp)
                        .background(MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f)),
                )

                // Action 3: Ledger / History
                Row(
                    modifier = Modifier
                        .clip(RoundedCornerShape(Radius.pill))
                        .clickable {
                            fintechHaptics.click()
                            onFilterLedger(activeAccount)
                        }
                        .padding(horizontal = 10.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    LedgerGlyph(tint = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(
                        text = "گردش حساب",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Medium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }

        // Secondary Accounts List (when multiple cards exist, for quick overview)
        if (accounts.size > 1) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 4.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text(
                    text = "سایر کارت‌های شما",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 4.dp),
                )

                accounts.filter { it.key != activeAccount.key }.forEach { account ->
                    val palette = remember(account.bankFa, account.mask, account.theme) {
                        BankCardThemes.resolve(account.bankFa, account.mask, account.theme)
                    }

                    Surface(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(16.dp))
                            .clickable {
                                fintechHaptics.click()
                                activeCardKey = account.key
                            },
                        shape = RoundedCornerShape(16.dp),
                        color = MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.6f),
                        border = androidx.compose.foundation.BorderStroke(
                            1.dp,
                            MaterialTheme.colorScheme.outline.copy(alpha = 0.18f),
                        ),
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = Space.m, vertical = 12.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(10.dp),
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(10.dp)
                                        .clip(CircleShape)
                                        .background(palette.accentColor),
                                )
                                Column {
                                    Text(
                                        text = account.bankFa,
                                        fontSize = 14.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.onSurface,
                                    )
                                    Text(
                                        text = if (privacyMode) "•••• ••••" else formatCardPanLtr(account.mask),
                                        fontSize = 11.sp,
                                        fontFamily = ModamFigures,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                            }

                            Column(horizontalAlignment = Alignment.End) {
                                Text(
                                    text = if (privacyMode) "••••••••" else "${faCompact(account.balance)} ت",
                                    fontSize = 14.sp,
                                    fontFamily = ModamFigures,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onSurface,
                                )
                                Text(
                                    text = "نمایش روی استیج",
                                    fontSize = 10.sp,
                                    color = palette.accentColor,
                                    fontWeight = FontWeight.SemiBold,
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

/**
 * Minimalist vector glyph for copying card PAN.
 */
@Composable
fun CardCopyGlyph(
    tint: Color,
    modifier: Modifier = Modifier,
) {
    Canvas(modifier = modifier.size(15.dp)) {
        val stroke = 1.4.dp.toPx()
        // Back card rectangle
        drawRoundRect(
            color = tint.copy(alpha = 0.5f),
            topLeft = Offset(size.width * 0.28f, 0f),
            size = Size(size.width * 0.72f, size.height * 0.72f),
            cornerRadius = CornerRadius(2.5.dp.toPx(), 2.5.dp.toPx()),
            style = Stroke(width = stroke),
        )
        // Front card rectangle
        drawRoundRect(
            color = tint,
            topLeft = Offset(0f, size.height * 0.28f),
            size = Size(size.width * 0.72f, size.height * 0.72f),
            cornerRadius = CornerRadius(2.5.dp.toPx(), 2.5.dp.toPx()),
            style = Stroke(width = stroke),
        )
    }
}

/**
 * Minimalist banking transfer / SHEBA vector glyph.
 */
@Composable
fun ShebaGlyph(
    tint: Color,
    modifier: Modifier = Modifier,
) {
    Canvas(modifier = modifier.size(15.dp)) {
        val stroke = 1.3.dp.toPx()
        // Bank triangular roof
        val roof = Path().apply {
            moveTo(size.width * 0.5f, 0f)
            lineTo(size.width, size.height * 0.32f)
            lineTo(0f, size.height * 0.32f)
            close()
        }
        drawPath(roof, color = tint)

        // 3 Pillars
        val colWidth = 1.8.dp.toPx()
        val topY = size.height * 0.42f
        val botY = size.height * 0.82f
        drawLine(tint, Offset(size.width * 0.2f, topY), Offset(size.width * 0.2f, botY), strokeWidth = colWidth)
        drawLine(tint, Offset(size.width * 0.5f, topY), Offset(size.width * 0.5f, botY), strokeWidth = colWidth)
        drawLine(tint, Offset(size.width * 0.8f, topY), Offset(size.width * 0.8f, botY), strokeWidth = colWidth)

        // Foundation step
        drawLine(
            color = tint,
            start = Offset(0f, size.height * 0.92f),
            end = Offset(size.width, size.height * 0.92f),
            strokeWidth = stroke * 1.2f,
            cap = StrokeCap.Round,
        )
    }
}

/**
 * Minimalist ledger / financial chart bars glyph.
 */
@Composable
fun LedgerGlyph(
    tint: Color,
    modifier: Modifier = Modifier,
) {
    Canvas(modifier = modifier.size(15.dp)) {
        val barW = 2.4.dp.toPx()
        val r = 1.2.dp.toPx()
        // 3 distinct trend bars
        drawRoundRect(tint.copy(alpha = 0.5f), Offset(size.width * 0.12f, size.height * 0.45f), Size(barW, size.height * 0.55f), CornerRadius(r, r))
        drawRoundRect(tint, Offset(size.width * 0.45f, size.height * 0.15f), Size(barW, size.height * 0.85f), CornerRadius(r, r))
        drawRoundRect(tint.copy(alpha = 0.75f), Offset(size.width * 0.78f, size.height * 0.3f), Size(barW, size.height * 0.7f), CornerRadius(r, r))
    }
}
