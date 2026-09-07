package com.doxigo.muchtoman

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableDoubleStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * Unified Studio-Grade Financial Command Deck on the Home screen.
 * Combines 4 vital financial metrics (Safe daily spend, monthly rhythm, inflation bleed, inflation shield index)
 * with real-time wealth defense allocation and tactical shortcut tools.
 */
@Composable
internal fun HomeFinancialExecutiveDeck(
    state: UiState,
    modifier: Modifier = Modifier,
    privacyMode: Boolean = false,
    onOpenGoldBubble: () -> Unit = {},
    onOpenRecurring: () -> Unit = {},
    onOpenWishlist: () -> Unit = {},
) {
    val liquidCashToman = state.bankToman.toLong()
    val upcomingObligationsToman = (state.obligations.filter { !it.archived }.sumOf { obligation ->
        val paid = state.obligationPayments[obligation.id]?.count { it.paidDate != null } ?: 0
        val unpaidCount = (obligation.installmentCount - paid).coerceAtLeast(0)
        unpaidCount.toLong() * obligation.installmentRial
    } / 10L)
    val reservationsToman = (state.reservations.filter { !it.released }.sumOf { it.amountRial } / 10L)

    val burn = remember(liquidCashToman) {
        calculateDailyCashBurn(liquidCashToman)
    }
    val runway = remember(liquidCashToman, upcomingObligationsToman, reservationsToman) {
        calculateSafeToSpend(
            liquidCashToman = liquidCashToman,
            upcomingObligationsToman = upcomingObligationsToman,
            reservationsToman = reservationsToman,
        )
    }

    val todayJal = jalaliOf(tehranDay(System.currentTimeMillis()))
    val totalDaysInMonth = jalaliMonthLength(todayJal.year, todayJal.month)
    val monthProgress = (todayJal.day.toFloat() / totalDaysInMonth).coerceIn(0f, 1f)

    // Calculate wealth defense index
    val unhedgedToman = liquidCashToman
    val totalWealthToman = state.totals.toman.toLong()
    val hedgedToman = (totalWealthToman - unhedgedToman).coerceAtLeast(0L)
    val defenseIndex = if (totalWealthToman > 0) ((hedgedToman.toDouble() / totalWealthToman) * 100).toInt().coerceIn(0, 100) else 0

    val rates = state.rates.toman
    val usdtRate = rates["usdt"] ?: rates["usd"] ?: 93_000.0
    val gold18Rate = rates["gold18"] ?: 4_650_000.0
    val emamiRate = rates["coin_emami"] ?: 54_000_000.0
    val robRate = rates["coin_rob"] ?: 18_400_000.0

    val safeBudgetToman = kotlin.math.max(1_000_000L, runway.safeToSpendToman)
    val advice = remember(safeBudgetToman, usdtRate, gold18Rate, emamiRate, robRate) {
        PurchasingOptimizationEngine.computeBestBuy(
            budgetToman = safeBudgetToman,
            usdtToman = usdtRate,
            gold18Toman = gold18Rate,
            emamiCoinToman = emamiRate,
            robCoinToman = robRate,
        )
    }

    Panel(modifier) {
        // Header
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                "دیده‌بان پایش ثروت و تاب‌آوری مالی",
                fontSize = 15.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Text(
                "هوشمند",
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier
                    .clip(RoundedCornerShape(Radius.pill))
                    .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.12f))
                    .padding(horizontal = Space.s, vertical = 2.dp),
            )
        }

        Spacer(Modifier.height(Space.m))

        // 2x2 Vital Signs Grid
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(Space.m)) {
            // Safe Daily Spend
            Column(
                Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(Radius.card))
                    .background(
                        if (runway.isOverdrawn) MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.25f)
                        else MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.25f)
                    )
                    .padding(Space.m),
            ) {
                Text(
                    "سهمیه خرج امن روزانه",
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    text = if (runway.isOverdrawn) "کسری بودجه" else "${faRate(runway.dailyAllowanceToman.toDouble())} ت",
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Black,
                    fontFamily = ModamFigures,
                    color = if (runway.isOverdrawn) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary,
                    modifier = Modifier.privacyBlur(privacyMode),
                )
                Text(
                    "${faNumber(runway.daysRemaining.toDouble())} روز تا آخر ماه",
                    fontSize = 10.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            // Month Rhythm & Progress
            Column(
                Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(Radius.card))
                    .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f))
                    .padding(Space.m),
            ) {
                Text(
                    "پیشرفت زمانی این ماه",
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    text = "روز ${faNumber(todayJal.day.toDouble())} از ${faNumber(totalDaysInMonth.toDouble())}",
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = ModamFigures,
                    color = MaterialTheme.colorScheme.onBackground,
                )
                Spacer(Modifier.height(4.dp))
                Box(
                    Modifier
                        .fillMaxWidth()
                        .height(4.dp)
                        .clip(RoundedCornerShape(2.dp))
                        .background(MaterialTheme.colorScheme.surfaceVariant),
                ) {
                    Box(
                        Modifier
                            .fillMaxWidth(monthProgress)
                            .fillMaxHeight()
                            .clip(RoundedCornerShape(2.dp))
                            .background(MaterialTheme.colorScheme.primary),
                    )
                }
            }
        }

        Spacer(Modifier.height(Space.s))

        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(Space.m)) {
            // Inflation Bleed
            Column(
                Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(Radius.card))
                    .background(MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.18f))
                    .padding(Space.m),
            ) {
                Text(
                    "افت روزانه ارزش نقدینگی",
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    text = "${faRate(burn.dailyLossToman.toDouble())} ت/روز",
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Black,
                    fontFamily = ModamFigures,
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier.privacyBlur(privacyMode),
                )
                Text(
                    "سوزانده‌شده در تورم",
                    fontSize = 10.sp,
                    color = MaterialTheme.colorScheme.error.copy(alpha = 0.8f),
                )
            }

            // Inflation Shield Index
            Column(
                Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(Radius.card))
                    .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f))
                    .padding(Space.m),
            ) {
                Text(
                    "شاخص مصونیت از تورم",
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    text = "٪${faNumber(defenseIndex.toDouble())} مصون",
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Black,
                    fontFamily = ModamFigures,
                    color = if (defenseIndex >= 60) Color(0xFF10B981) else Color(0xFFF59E0B),
                )
                Text(
                    "طلا، ارز و دارایی محافظ",
                    fontSize = 10.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        Spacer(Modifier.height(Space.m))

        // Actionable Recommendation Bar
        Row(
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(Radius.card))
                .background(MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.2f))
                .padding(horizontal = Space.m, vertical = Space.s),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text("🛡️", fontSize = 18.sp, modifier = Modifier.padding(end = Space.s))
            Column(Modifier.weight(1f)) {
                Text(
                    advice.headlineFa,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary,
                )
                Text(
                    advice.summaryFa,
                    fontSize = 11.sp,
                    lineHeight = 16.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        Spacer(Modifier.height(Space.m))

        // Tactical shortcuts
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(Space.s),
        ) {
            OutlinedButton(
                onClick = onOpenGoldBubble,
                modifier = Modifier.weight(1f),
                contentPadding = PaddingValues(horizontal = Space.xs, vertical = Space.xs),
            ) {
                Text("حباب طلا", fontSize = 11.sp, maxLines = 1)
            }
            OutlinedButton(
                onClick = onOpenRecurring,
                modifier = Modifier.weight(1f),
                contentPadding = PaddingValues(horizontal = Space.xs, vertical = Space.xs),
            ) {
                Text("اقساط و دوره‌ای", fontSize = 11.sp, maxLines = 1)
            }
            OutlinedButton(
                onClick = onOpenWishlist,
                modifier = Modifier.weight(1f),
                contentPadding = PaddingValues(horizontal = Space.xs, vertical = Space.xs),
            ) {
                Text("کنترل خرید", fontSize = 11.sp, maxLines = 1)
            }
        }
    }
}

/**
 * Smart Cash Burn and Safe-to-Spend Runway Card on the Home screen.
 */
@Composable
internal fun CashBurnAndRunwayCard(
    state: UiState,
    modifier: Modifier = Modifier,
    privacyMode: Boolean = false,
    onOpenGoldBubble: () -> Unit = {},
    onOpenRecurring: () -> Unit = {},
    onOpenWishlist: () -> Unit = {},
) {
    val liquidCashToman = state.bankToman.toLong()
    val upcomingObligationsToman = (state.obligations.filter { !it.archived }.sumOf { obligation ->
        val paid = state.obligationPayments[obligation.id]?.count { it.paidDate != null } ?: 0
        val unpaidCount = (obligation.installmentCount - paid).coerceAtLeast(0)
        unpaidCount.toLong() * obligation.installmentRial
    } / 10L)
    val reservationsToman = (state.reservations.filter { !it.released }.sumOf { it.amountRial } / 10L)

    val burn = remember(liquidCashToman) {
        calculateDailyCashBurn(liquidCashToman)
    }
    val runway = remember(liquidCashToman, upcomingObligationsToman, reservationsToman) {
        calculateSafeToSpend(
            liquidCashToman = liquidCashToman,
            upcomingObligationsToman = upcomingObligationsToman,
            reservationsToman = reservationsToman,
        )
    }

    Panel(modifier) {
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                "دیده‌بان تاب‌آوری و ارزش نقدینگی",
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Text(
                "هوشمند",
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier
                    .clip(RoundedCornerShape(Radius.pill))
                    .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.12f))
                    .padding(horizontal = Space.s, vertical = 2.dp),
            )
        }

        Spacer(Modifier.height(Space.m))

        // Burn section
        Row(
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(Radius.card))
                .background(MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.22f))
                .padding(Space.m),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                "🔥",
                fontSize = 24.sp,
                modifier = Modifier.padding(end = Space.m),
            )
            Column(Modifier.weight(1f)) {
                Text(
                    "افت روزانه ارزش نقدینگی به علت تورم",
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    text = "${faRate(burn.dailyLossToman.toDouble())} تومان در روز",
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Black,
                    fontFamily = ModamFigures,
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier.privacyBlur(privacyMode),
                )
                Text(
                    "با تورم سالانه ۴۵٪، ماهانه حدود ${faRate(burn.monthlyLossToman.toDouble())} تومان از ارزش ریالی شما سوخت می‌شود.",
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f),
                    modifier = Modifier.padding(top = 2.dp),
                )
            }
        }

        Spacer(Modifier.height(Space.s))

        // Safe-to-Spend Runway section
        Row(
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(Radius.card))
                .background(MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.22f))
                .padding(Space.m),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                "🛡️",
                fontSize = 24.sp,
                modifier = Modifier.padding(end = Space.m),
            )
            Column(Modifier.weight(1f)) {
                Text(
                    "موجودی امن خرج کردن (Safe-to-Spend)",
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    text = "${faCompact(runway.safeToSpendToman.toDouble())} تومان",
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Black,
                    fontFamily = ModamFigures,
                    color = if (runway.isOverdrawn) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary,
                    modifier = Modifier.privacyBlur(privacyMode),
                )
                Text(
                    if (runway.isOverdrawn) {
                        "هشدار: تعهدات اقساط و رزروها از موجودی حساب‌های شما بیشتر است!"
                    } else {
                        "سهمیه روزانه تا آخر ماه: روزی ${faRate(runway.dailyAllowanceToman.toDouble())} تومان (${bidi(runway.daysRemaining.toString())} روز باقی‌مانده)"
                    },
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.85f),
                    modifier = Modifier.padding(top = 2.dp),
                )
            }
        }

        Spacer(Modifier.height(Space.m))

        // Tactical shortcuts
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(Space.s),
        ) {
            OutlinedButton(
                onClick = onOpenGoldBubble,
                modifier = Modifier.weight(1f),
                contentPadding = PaddingValues(horizontal = Space.xs, vertical = Space.xs),
            ) {
                Text("حباب‌سنج طلا", fontSize = 11.sp, maxLines = 1)
            }
            OutlinedButton(
                onClick = onOpenRecurring,
                modifier = Modifier.weight(1f),
                contentPadding = PaddingValues(horizontal = Space.xs, vertical = Space.xs),
            ) {
                Text("اشتراک‌ها", fontSize = 11.sp, maxLines = 1)
            }
            OutlinedButton(
                onClick = onOpenWishlist,
                modifier = Modifier.weight(1f),
                contentPadding = PaddingValues(horizontal = Space.xs, vertical = Space.xs),
            ) {
                Text("لیست ۴۸ساعته", fontSize = 11.sp, maxLines = 1)
            }
        }
    }
}

/**
 * Bottom Sheet for Gold Coin Bubble Analysis and tactical purchasing power.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun GoldBubbleSheet(
    effectiveRates: Map<String, Double>,
    onDismiss: () -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val spot18k = effectiveRates["gold18"] ?: 3_800_000.0

    var selectedCoin by remember { mutableStateOf(GoldCoinType.EMAMI) }

    val defaultMarketPrice = remember(selectedCoin, effectiveRates) {
        when (selectedCoin) {
            GoldCoinType.EMAMI -> effectiveRates["coin_emami"]?.toLong() ?: 45_000_000L
            GoldCoinType.BAHAR -> effectiveRates["coin_bahar"]?.toLong() ?: 41_000_000L
            GoldCoinType.NIM -> effectiveRates["coin_nim"]?.toLong() ?: 24_000_000L
            GoldCoinType.ROB -> effectiveRates["coin_rob"]?.toLong() ?: 15_000_000L
            GoldCoinType.GERAMI -> effectiveRates["coin_gerami"]?.toLong() ?: 7_200_000L
        }
    }

    var marketPriceText by remember(selectedCoin) {
        mutableStateOf(defaultMarketPrice.toString())
    }

    val marketPrice = marketPriceText.filter { it.isDigit() }.toLongOrNull() ?: defaultMarketPrice

    val bubble = remember(selectedCoin, marketPrice, spot18k) {
        evaluateCoinBubble(selectedCoin, marketPrice, spot18k)
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.surface,
    ) {
        Column(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = Space.l)
                .padding(bottom = Space.xl),
        ) {
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    "حباب‌سنج سکه و صندوق‌های طلا",
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                IconButton(onClick = onDismiss) {
                    Icon(Icons.Default.Close, contentDescription = "بستن")
                }
            }

            Spacer(Modifier.height(Space.s))

            Text(
                "مقایسه قیمت بازاری انواع سکه با ارزش خالص طلای داخل آن (عیار ۹۰۰ / ۲۱.۶ عیار)",
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            Spacer(Modifier.height(Space.m))

            // Coin selector chips
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(Space.xs),
            ) {
                GoldCoinType.entries.forEach { coin ->
                    val selected = coin == selectedCoin
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(Radius.card))
                            .background(
                                if (selected) MaterialTheme.colorScheme.primaryContainer
                                else MaterialTheme.colorScheme.surfaceContainerHighest,
                            )
                            .clickable {
                                selectedCoin = coin
                            }
                            .padding(vertical = Space.s),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            text = coin.nameFa,
                            fontSize = 11.sp,
                            fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
                            color = if (selected) MaterialTheme.colorScheme.onPrimaryContainer
                            else MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                        )
                    }
                }
            }

            Spacer(Modifier.height(Space.m))

            OutlinedTextField(
                value = marketPriceText,
                onValueChange = { marketPriceText = it },
                label = { Text("قیمت روز بازار (${selectedCoin.nameFa}) به تومان") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
            )

            Spacer(Modifier.height(Space.m))

            // Result Card
            Box(
                Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(Radius.card))
                    .background(MaterialTheme.colorScheme.surfaceContainer)
                    .border(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f), RoundedCornerShape(Radius.card))
                    .padding(Space.m),
            ) {
                Column {
                    Row(
                        Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        Text("ارزش طلای خالص (ارزش ذاتی):", fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text("${faRate(bubble.intrinsicPriceToman.toDouble())} تومان", fontSize = 13.sp, fontWeight = FontWeight.Bold, fontFamily = ModamFigures)
                    }
                    Spacer(Modifier.height(Space.xs))
                    Row(
                        Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        Text("مبلغ حباب:", fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text("${faRate(bubble.bubbleAmountToman.toDouble())} تومان", fontSize = 13.sp, fontWeight = FontWeight.Bold, fontFamily = ModamFigures)
                    }
                    Spacer(Modifier.height(Space.xs))
                    Row(
                        Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text("درصد حباب:", fontSize = 14.sp, fontWeight = FontWeight.Bold)
                        val bubbleColor = when {
                            bubble.bubblePercent > 20.0 -> MaterialTheme.colorScheme.error
                            bubble.bubblePercent > 10.0 -> Color(0xFFE68A00) // Amber
                            else -> MaterialTheme.colorScheme.primary // Green
                        }
                        Text(
                            "${bidi(String.format("%.1f", bubble.bubblePercent))}٪",
                            fontSize = 18.sp,
                            fontWeight = FontWeight.ExtraBold,
                            fontFamily = ModamFigures,
                            color = bubbleColor,
                        )
                    }

                    Spacer(Modifier.height(Space.m))

                    val guidance = when {
                        bubble.bubblePercent > 20.0 ->
                            "⚠️ حباب این مسکوک بسیار بالاست! خرید در این سطح ریسک تخلیه شدید حباب دارد. صندوق‌های طلای بورس (مانند عیار، کهربا، طلا) با حباب زیر ۲٪ گزینه بسیار امن‌تری برای حفظ قدرت خرید هستند."
                        bubble.bubblePercent > 10.0 ->
                            "ℹ️ حباب در حد متوسط است. برای مبالغ بالا، شمش و طلای آب‌شده یا صندوق‌های طلا بازدهی مطمئن‌تری دارند."
                        else ->
                            "✅ حباب سکه در محدوده منطقی است و بخش عمده قیمت ناشی از طلای خالص موجود در آن می‌باشد."
                    }
                    Text(
                        text = guidance,
                        fontSize = 12.sp,
                        lineHeight = 18.sp,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                }
            }
        }
    }
}

/**
 * Bottom Sheet for Recurring Expenses and Subscription Detection.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun RecurringExpensesSheet(
    entries: List<LedgerEntry>,
    onDismiss: () -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val recurringList = remember(entries) {
        detectRecurringExpenses(entries)
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.surface,
    ) {
        Column(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = Space.l)
                .padding(bottom = Space.xl),
        ) {
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    "اشتراک‌ها و هزینه‌های دوره‌ای",
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                IconButton(onClick = onDismiss) {
                    Icon(Icons.Default.Close, contentDescription = "بستن")
                }
            }

            Spacer(Modifier.height(Space.s))

            Text(
                "شناسایی خودکار پرداخت‌های تکراری ماهانه مانند اینترنت، شارژ، اشتراک‌های دیجیتال و قبوض.",
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            Spacer(Modifier.height(Space.m))

            if (recurringList.isEmpty()) {
                Box(
                    Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(Radius.card))
                        .background(MaterialTheme.colorScheme.surfaceContainerHighest)
                        .padding(Space.l),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        "هنوز هزینه دوره‌ای تکراری ثبت نشده است. با دریافت پیامک‌های دوره‌ای بعدی به صورت خودکار شناسایی خواهند شد.",
                        fontSize = 13.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        lineHeight = 20.sp,
                    )
                }
            } else {
                val totalMonthly = recurringList.sumOf { it.averageAmountRial } / 10L
                Text(
                    "مجموع تعهدات دوره‌ای در ماه: ${faCompact(totalMonthly.toDouble())} تومان",
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(bottom = Space.m),
                )

                LazyColumn(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(Space.s),
                ) {
                    items(recurringList) { item ->
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(Radius.card))
                                .background(MaterialTheme.colorScheme.surfaceContainer)
                                .border(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f), RoundedCornerShape(Radius.card))
                                .padding(Space.m),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Column(Modifier.weight(1f)) {
                                Text(
                                    item.merchantKey,
                                    fontSize = 15.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = MaterialTheme.colorScheme.onSurface,
                                )
                                Text(
                                    "${bidi(item.count.toString())} پرداخت ثبت شده (دسته‌بندی: ${item.categoryFa})",
                                    fontSize = 11.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                            Text(
                                "${faRate((item.averageAmountRial / 10L).toDouble())} تومان",
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Bold,
                                fontFamily = ModamFigures,
                                color = MaterialTheme.colorScheme.onSurface,
                            )
                        }
                    }
                }
            }
        }
    }
}

/**
 * Bottom Sheet for 48-Hour Wishlist and Behavioral Life-Energy.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun WishlistSheet(
    dailySafeSpendToman: Long = 0L,
    onDismiss: () -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val now = remember { System.currentTimeMillis() }

    // Persistent in-memory list for demo/session
    val items = remember {
        mutableStateListOf(
            WishlistItem(title = "اسپیکر قابل حمل بلوتوثی", amountToman = 2_500_000L, addedAtEpochMs = now - 18 * 3600 * 1000L),
            WishlistItem(title = "ساعت هوشمند", amountToman = 6_000_000L, addedAtEpochMs = now - 52 * 3600 * 1000L),
        )
    }

    var showAddDialog by remember { mutableStateOf(false) }
    var newTitle by remember { mutableStateOf("") }
    var newAmountText by remember { mutableStateOf("") }
    var totalSavedToman by remember { mutableLongStateOf(4_800_000L) }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.surface,
    ) {
        Column(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = Space.l)
                .padding(bottom = Space.xl),
        ) {
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    "لیست انتظار ۴۸ ساعته (کنترل تکانشگری)",
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                IconButton(onClick = onDismiss) {
                    Icon(Icons.Default.Close, contentDescription = "بستن")
                }
            }

            Spacer(Modifier.height(Space.s))

            Text(
                "پیش از خریدهای غیرضروری، ۴۸ ساعت به خودتان فرصت تفکر دهید. ارزش هر خرید بر اساس ساعات کار شما سنجیده می‌شود.",
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                lineHeight = 18.sp,
            )

            Spacer(Modifier.height(Space.m))

            // Saved counter badge
            Box(
                Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(Radius.card))
                    .background(MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.35f))
                    .padding(Space.m),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("🎉", fontSize = 22.sp, modifier = Modifier.padding(end = Space.s))
                    Column {
                        Text(
                            "صرفه‌جویی با منصرف شدن از خریدهای هیجانی:",
                            fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Text(
                            "${faRate(totalSavedToman.toDouble())} تومان پس‌انداز شده!",
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Bold,
                            fontFamily = ModamFigures,
                            color = MaterialTheme.colorScheme.primary,
                        )
                    }
                }
            }

            Spacer(Modifier.height(Space.m))

            if (!showAddDialog) {
                OutlinedButton(
                    onClick = { showAddDialog = true },
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(Space.xs))
                    Text("افزودن خرید جدید برای بررسی ۴۸ ساعته")
                }
            } else {
                Column(
                    Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(Radius.card))
                        .background(MaterialTheme.colorScheme.surfaceContainer)
                        .padding(Space.m),
                ) {
                    OutlinedTextField(
                        value = newTitle,
                        onValueChange = { newTitle = it },
                        label = { Text("عنوان کالا (مثلاً قهوه‌ساز)") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                    )
                    Spacer(Modifier.height(Space.s))
                    OutlinedTextField(
                        value = newAmountText,
                        onValueChange = { newAmountText = it },
                        label = { Text("قیمت به تومان") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                    )

                    val candidateAmount = newAmountText.filter { it.isDigit() }.toLongOrNull() ?: 0L
                    if (candidateAmount > 0L) {
                        val candidateEnergy = calculateLifeEnergy(candidateAmount)
                        val candidateDays = if (dailySafeSpendToman > 0L) candidateAmount.toDouble() / dailySafeSpendToman else 0.0
                        Spacer(Modifier.height(Space.xs))
                        Column(
                            Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(Radius.card))
                                .background(MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.25f))
                                .padding(Space.s),
                        ) {
                            Text(
                                "⏳ بهای انرژی: ${candidateEnergy.formattedFa} از کار و تلاش شما",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.primary,
                            )
                            if (candidateDays >= 0.1) {
                                Text(
                                    "⚡ اثر بر بودجه: مصرف ${faDecimal(candidateDays, 1)} روز از خرج روزانه امن",
                                    fontSize = 11.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                    }

                    Spacer(Modifier.height(Space.s))
                    Row(
                        Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.End,
                    ) {
                        TextButton(onClick = { showAddDialog = false }) {
                            Text("انصراف")
                        }
                        Spacer(Modifier.width(Space.s))
                        Button(
                            onClick = {
                                val amount = newAmountText.filter { it.isDigit() }.toLongOrNull() ?: 0L
                                if (newTitle.isNotBlank() && amount > 0L) {
                                    items.add(0, WishlistItem(title = newTitle, amountToman = amount, addedAtEpochMs = System.currentTimeMillis()))
                                    newTitle = ""
                                    newAmountText = ""
                                    showAddDialog = false
                                }
                            },
                        ) {
                            Text("شروع ۴۸ ساعت تفکر")
                        }
                    }
                }
            }

            Spacer(Modifier.height(Space.m))

            LazyColumn(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(Space.s),
            ) {
                items(items) { item ->
                    val remaining = item.hoursRemaining(now)
                    val elapsed = item.isCoolingPeriodElapsed(now)
                    val energy = calculateLifeEnergy(item.amountToman)
                    val daysBurned = if (dailySafeSpendToman > 0L) item.amountToman.toDouble() / dailySafeSpendToman else 0.0

                    Box(
                        Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(Radius.card))
                            .background(MaterialTheme.colorScheme.surfaceContainer)
                            .border(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f), RoundedCornerShape(Radius.card))
                            .padding(Space.m),
                    ) {
                        Column {
                            Row(
                                Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Text(
                                    item.title,
                                    fontSize = 15.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onSurface,
                                )
                                Text(
                                    "${faRate(item.amountToman.toDouble())} تومان",
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.Bold,
                                    fontFamily = ModamFigures,
                                )
                            }

                            Spacer(Modifier.height(Space.xs))

                            Text(
                                "⏳ بهای زمان: معادل ${energy.formattedFa} از کار شما",
                                fontSize = 12.sp,
                                color = MaterialTheme.colorScheme.primary,
                            )

                            if (daysBurned >= 0.1) {
                                Spacer(Modifier.height(2.dp))
                                Text(
                                    "⚡ فشار بر بودجه: بلعیدن ${faDecimal(daysBurned, 1)} روز از خرج روزانه امن",
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Medium,
                                    color = MaterialTheme.colorScheme.tertiary,
                                )
                            }

                            Spacer(Modifier.height(Space.xs))

                            Text(
                                if (elapsed) "✅ ۴۸ ساعت تفکر تمام شد — اگر هنوز می‌خواهید، بخرید!"
                                else "⏳ ${bidi(remaining.toString())} ساعت تا تصمیم نهایی مانده است",
                                fontSize = 11.sp,
                                color = if (elapsed) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error,
                            )

                            Spacer(Modifier.height(Space.s))

                            Row(
                                Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(Space.s),
                            ) {
                                OutlinedButton(
                                    onClick = {
                                        totalSavedToman += item.amountToman
                                        items.remove(item)
                                    },
                                    modifier = Modifier.weight(1f),
                                ) {
                                    Text("منصرف شدم 🎉 (صرفه‌جویی)", fontSize = 11.sp)
                                }
                                Button(
                                    onClick = {
                                        items.remove(item)
                                    },
                                    modifier = Modifier.weight(1f),
                                ) {
                                    Text("خرید انجام شد", fontSize = 11.sp)
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

// ════════════════════════════════════════════════════════════════════════════════
// 5. WEALTH DEFENSE ADVISOR CARD (Dynamic Best-Buy Engine)
// ════════════════════════════════════════════════════════════════════════════════

@Composable
internal fun WealthDefenseAdvisorCard(
    state: UiState,
    modifier: Modifier = Modifier,
) {
    val liquidCashToman = state.bankToman.toLong()
    val upcomingObligationsToman = (state.obligations.filter { !it.archived }.sumOf { obligation ->
        val paid = state.obligationPayments[obligation.id]?.count { it.paidDate != null } ?: 0
        val unpaidCount = (obligation.installmentCount - paid).coerceAtLeast(0)
        unpaidCount.toLong() * obligation.installmentRial
    } / 10L)
    val reservationsToman = (state.reservations.filter { !it.released }.sumOf { it.amountRial } / 10L)

    val safe = remember(liquidCashToman, upcomingObligationsToman, reservationsToman) {
        calculateSafeToSpend(
            liquidCashToman = liquidCashToman,
            upcomingObligationsToman = upcomingObligationsToman,
            reservationsToman = reservationsToman,
        ).safeToSpendToman
    }
    val liquidBudgetToman = kotlin.math.max(1_000_000L, safe)

    val rates = state.rates.toman
    val usdtRate = rates["usdt"] ?: rates["usd"] ?: 93_000.0
    val gold18Rate = rates["gold18"] ?: 4_650_000.0
    val emamiRate = rates["coin_emami"] ?: 54_000_000.0
    val robRate = rates["coin_rob"] ?: 18_400_000.0

    val advice = remember(liquidBudgetToman, usdtRate, gold18Rate, emamiRate, robRate) {
        PurchasingOptimizationEngine.computeBestBuy(
            budgetToman = liquidBudgetToman,
            usdtToman = usdtRate,
            gold18Toman = gold18Rate,
            emamiCoinToman = emamiRate,
            robCoinToman = robRate,
        )
    }

    Panel(modifier) {
        // Header
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(Space.xs),
            ) {
                Text("🛡️", fontSize = 16.sp)
                Text(
                    "دستیار ضد تورم و خرید بهینه",
                    fontWeight = FontWeight.Bold,
                    fontSize = 13.sp,
                    color = MaterialTheme.colorScheme.onSurface,
                )
            }

            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(Radius.pill))
                    .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.12f))
                    .padding(horizontal = Space.s, vertical = 3.dp),
            ) {
                Text(
                    "بودجه: ${faCompact(advice.userBudgetToman.toDouble())} ت",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.primary,
                )
            }
        }

        Spacer(Modifier.height(Space.s))

        // Primary Recommendation Headline
        if (advice.headlineFa.isNotBlank()) {
            Text(
                advice.headlineFa,
                fontWeight = FontWeight.ExtraBold,
                fontSize = 14.sp,
                color = MaterialTheme.colorScheme.primary,
                lineHeight = 20.sp,
            )
            Spacer(Modifier.height(4.dp))
        }

        // Summary Explanation
        if (advice.summaryFa.isNotBlank()) {
            Text(
                advice.summaryFa,
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                lineHeight = 18.sp,
            )
        }

        Spacer(Modifier.height(Space.m))

        // Allocations breakdown
        Text(
            "سبد تخصیص پیشنهادی بر اساس شرایط لحظه‌ای بازار:",
            fontSize = 11.sp,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurface,
        )

        Spacer(Modifier.height(Space.xs))

        advice.allocations.forEach { slice ->
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 3.dp)
                    .clip(RoundedCornerShape(Radius.card))
                    .background(MaterialTheme.colorScheme.surface)
                    .padding(Space.s),
            ) {
                Column {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            slice.assetClass.titleFa,
                            fontWeight = FontWeight.Bold,
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurface,
                        )
                        Text(
                            "${faNumber(slice.percentage.toDouble())}٪ (${faNumber(slice.amountToman.toDouble())} ت)",
                            fontWeight = FontWeight.Bold,
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.primary,
                        )
                    }
                    Spacer(Modifier.height(2.dp))
                    Text(
                        slice.rationaleFa,
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(Modifier.height(2.dp))
                    Text(
                        "اقدام: ${slice.actionableStepsFa}",
                        fontSize = 10.sp,
                        color = MaterialTheme.colorScheme.outline,
                    )
                }
            }
        }

        // Market Warnings (e.g. Coin Bubbles)
        if (advice.warningsFa.isNotEmpty()) {
            Spacer(Modifier.height(Space.s))
            advice.warningsFa.forEach { warn ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(Radius.card))
                        .background(MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.35f))
                        .padding(Space.xs),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text("⚠️", fontSize = 12.sp, modifier = Modifier.padding(end = 4.dp))
                    Text(
                        warn,
                        fontSize = 10.sp,
                        color = MaterialTheme.colorScheme.onErrorContainer,
                        lineHeight = 15.sp,
                    )
                }
                Spacer(Modifier.height(2.dp))
            }
        }

        // Real Estate context if present
        advice.realEstateContextFa?.let { reContext ->
            Spacer(Modifier.height(Space.xs))
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(Radius.card))
                    .background(MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.30f))
                    .padding(Space.xs),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text("🏢", fontSize = 12.sp, modifier = Modifier.padding(end = 4.dp))
                Text(
                    reContext,
                    fontSize = 10.sp,
                    color = MaterialTheme.colorScheme.onSecondaryContainer,
                    lineHeight = 15.sp,
                )
            }
        }
    }
}

