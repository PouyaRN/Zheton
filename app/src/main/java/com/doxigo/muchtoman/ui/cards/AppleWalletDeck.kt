package com.doxigo.muchtoman.ui.cards

import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
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
import com.doxigo.muchtoman.faNumber
import com.doxigo.muchtoman.formatCardPanLtr
import com.doxigo.muchtoman.toAsciiDigits
import com.doxigo.muchtoman.ui.haptics.LocalFintechHaptics

/**
 * Apple Wallet Accordion Fan-out Deck for Tab 3 (Cards).
 * Renders Iranian bank cards with fluid vertical spring stacking, 1-tap PAN & SHEBA copy,
 * and seamless expansion of the selected card.
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

    // Active expanded card key; if only one account, expand it by default
    var expandedCardKey by remember(accounts) {
        mutableStateOf(if (accounts.size == 1) accounts.first().key else null)
    }

    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        accounts.forEachIndexed { index, account ->
            val isExpanded = expandedCardKey == account.key
            val bankPalette = remember(account.bankFa, account.mask, account.theme) {
                BankCardThemes.resolve(account.bankFa, account.mask, account.theme)
            }

            val rawDigits = toAsciiDigits(account.mask).filter { it.isDigit() }
            val copyPan = if (rawDigits.length >= 16) rawDigits.take(16) else rawDigits.ifBlank { account.mask }

            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(20.dp))
                    .border(
                        1.dp,
                        if (isExpanded) bankPalette.accentColor.copy(alpha = 0.5f) else MaterialTheme.colorScheme.outline.copy(alpha = 0.2f),
                        RoundedCornerShape(20.dp),
                    ),
                shape = RoundedCornerShape(20.dp),
                color = MaterialTheme.colorScheme.surfaceContainerHigh,
            ) {
                Column(modifier = Modifier.fillMaxWidth()) {
                    // Header slice (always visible, acts as accordion trigger)
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                fintechHaptics.click()
                                expandedCardKey = if (isExpanded) null else account.key
                            }
                            .background(
                                Brush.horizontalGradient(
                                    bankPalette.backgroundGradient.ifEmpty { listOf(Color(0xFF1E293B), Color(0xFF0F172A)) }
                                )
                            )
                            .padding(horizontal = Space.m, vertical = 14.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                modifier = Modifier
                                    .size(10.dp)
                                    .clip(CircleShape)
                                    .background(bankPalette.accentColor),
                            )
                            Spacer(Modifier.width(Space.s))
                            Column {
                                Text(
                                    text = account.bankFa,
                                    fontSize = 15.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color.White,
                                )
                                Text(
                                    text = if (privacyMode) "••••  ••••" else formatCardPanLtr(account.mask),
                                    fontSize = 12.sp,
                                    color = Color.White.copy(alpha = 0.75f),
                                    fontFamily = ModamFigures,
                                )
                            }
                        }

                        Column(horizontalAlignment = Alignment.End) {
                            Text(
                                text = if (privacyMode) "••••••••" else "${faCompact(account.balance)} ت",
                                fontSize = 15.sp,
                                fontFamily = ModamFigures,
                                fontWeight = FontWeight.ExtraBold,
                                color = Color.White,
                            )
                            Text(
                                text = if (isExpanded) "بستن ▲" else "نمایش کامل ▼",
                                fontSize = 10.sp,
                                color = Color.White.copy(alpha = 0.6f),
                            )
                        }
                    }

                    // Expanded 3D card & Quick actions
                    AnimatedVisibility(
                        visible = isExpanded,
                        enter = fadeIn() + expandVertically(spring(dampingRatio = 0.75f, stiffness = 400f)),
                        exit = fadeOut() + shrinkVertically(spring(dampingRatio = 0.75f, stiffness = 400f)),
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = Space.m),
                            horizontalAlignment = Alignment.CenterHorizontally,
                        ) {
                            BankCard3D(
                                bankAccount = account,
                                cardHolderName = cardHolderName,
                                modifier = Modifier.padding(horizontal = Space.s),
                                onClick = {
                                    fintechHaptics.click()
                                    onCardClick(account)
                                },
                                onLongClick = {
                                    fintechHaptics.heavyClick()
                                    onCardLongPress(account)
                                },
                                enable3DTiltDrag = true,
                                privacyMode = privacyMode,
                            )

                            Spacer(Modifier.height(12.dp))

                            // Action pill dock (Copy PAN, Copy SHEBA, Filter Ledger)
                            Row(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(Radius.pill))
                                    .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                                    .padding(horizontal = Space.m, vertical = 6.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(Space.s),
                            ) {
                                if (copyPan.isNotBlank()) {
                                    Row(
                                        modifier = Modifier
                                            .clip(RoundedCornerShape(Radius.pill))
                                            .clickable {
                                                fintechHaptics.doubleTap()
                                                clipboardManager.setText(AnnotatedString(copyPan))
                                                Toast.makeText(context, "شماره کارت کپی شد", Toast.LENGTH_SHORT).show()
                                            }
                                            .padding(horizontal = Space.s, vertical = 2.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                                    ) {
                                        Text(
                                            text = "📋",
                                            fontSize = 12.sp,
                                        )
                                        Text(
                                            text = "کپی کارت",
                                            fontSize = 11.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = MaterialTheme.colorScheme.primary,
                                        )
                                    }
                                }

                                if (account.sheba.isNotBlank()) {
                                    Box(
                                        Modifier
                                            .width(1.dp)
                                            .height(14.dp)
                                            .background(MaterialTheme.colorScheme.outlineVariant)
                                    )
                                    Row(
                                        modifier = Modifier
                                            .clip(RoundedCornerShape(Radius.pill))
                                            .clickable {
                                                fintechHaptics.doubleTap()
                                                val cleanSheba = account.sheba.replace(" ", "").trim()
                                                clipboardManager.setText(AnnotatedString(cleanSheba))
                                                Toast.makeText(context, "شماره شبا کپی شد", Toast.LENGTH_SHORT).show()
                                            }
                                            .padding(horizontal = Space.s, vertical = 2.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                                    ) {
                                        Text("🏛️", fontSize = 11.sp)
                                        Text(
                                            text = "کپی شبا",
                                            fontSize = 11.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = Hero.gold,
                                        )
                                    }
                                }

                                Box(
                                    Modifier
                                        .width(1.dp)
                                        .height(14.dp)
                                        .background(MaterialTheme.colorScheme.outlineVariant)
                                )

                                Row(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(Radius.pill))
                                        .clickable {
                                            fintechHaptics.click()
                                            onFilterLedger(account)
                                        }
                                        .padding(horizontal = Space.s, vertical = 2.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                                ) {
                                    Text("📊", fontSize = 11.sp)
                                    Text(
                                        text = "گردش حساب",
                                        fontSize = 11.sp,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
