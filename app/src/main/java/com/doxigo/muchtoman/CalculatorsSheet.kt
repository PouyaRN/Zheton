package com.doxigo.muchtoman

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
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.pow
import kotlin.math.roundToLong

// ── Iranian CPI Index Table (Central Bank / SCI Annual Averages 1380–1405) ───────────────────
val IRAN_CPI_INDEX: Map<Int, Double> = mapOf(
    1380 to 2.1,
    1381 to 2.4,
    1382 to 2.8,
    1383 to 3.2,
    1384 to 3.6,
    1385 to 4.0,
    1386 to 4.8,
    1387 to 6.0,
    1388 to 6.6,
    1389 to 7.5,
    1390 to 9.1,
    1391 to 11.9,
    1392 to 16.0,
    1393 to 18.5,
    1394 to 20.7,
    1395 to 22.6,
    1396 to 24.8,
    1397 to 32.5,
    1398 to 43.8,
    1399 to 60.1,
    1400 to 84.3,
    1401 to 123.5,
    1402 to 178.2,
    1403 to 242.0,
    1404 to 325.0,
    1405 to 428.0,
)

data class CoinBubbleSpec(
    val id: String,
    val labelFa: String,
    val weightGrams: Double,
    val purity: Double,
    val mintFeeToman: Long,
)

val COIN_SPECS_LIST = listOf(
    CoinBubbleSpec("coin_emami", "سکه امامی", 8.13598, 0.900, 500_000L),
    CoinBubbleSpec("coin_bahar", "بهار آزادی", 8.13598, 0.900, 500_000L),
    CoinBubbleSpec("coin_nim", "نیم‌سکه", 4.06799, 0.900, 400_000L),
    CoinBubbleSpec("coin_rob", "ربع‌سکه", 2.033995, 0.900, 350_000L),
    CoinBubbleSpec("coin_gerami", "سکه یک‌گرمی", 1.01, 0.900, 300_000L),
)

data class CoinBubbleOutput(
    val intrinsicToman: Long,
    val bubbleToman: Long,
    val bubblePercent: Double,
    val riskLevel: String, // "low" | "medium" | "high"
)

fun calcCoinBubbleSpec(
    spec: CoinBubbleSpec,
    marketCoinPriceToman: Long,
    gold18GramPriceToman: Long,
): CoinBubbleOutput {
    val gold24Price = gold18GramPriceToman.toDouble() / 0.750
    val intrinsic = (spec.weightGrams * spec.purity * gold24Price + spec.mintFeeToman).roundToLong()
    val bubbleToman = marketCoinPriceToman - intrinsic
    val bubblePercent = if (intrinsic > 0L) (bubbleToman.toDouble() / intrinsic.toDouble()) * 100.0 else 0.0
    val riskLevel = when {
        bubblePercent > 22.0 -> "high"
        bubblePercent > 12.0 -> "medium"
        else -> "low"
    }
    return CoinBubbleOutput(intrinsic, bubbleToman, bubblePercent, riskLevel)
}

data class LoanPmtOutput(
    val monthlyPayment: Long,
    val totalPayment: Long,
    val totalInterest: Long,
)

fun calcLoanPmtValue(
    principalToman: Long,
    annualRatePercent: Double,
    months: Int,
): LoanPmtOutput {
    if (months <= 0 || principalToman <= 0L) return LoanPmtOutput(0L, 0L, 0L)
    if (annualRatePercent <= 0.0) {
        val m = (principalToman.toDouble() / months).roundToLong()
        return LoanPmtOutput(m, principalToman, 0L)
    }
    val r = annualRatePercent / 100.0 / 12.0
    val factor = (1.0 + r).pow(months.toDouble())
    val pmt = (principalToman.toDouble() * r * factor) / (factor - 1.0)
    val monthlyPayment = pmt.roundToLong()
    val totalPayment = monthlyPayment * months
    val totalInterest = (totalPayment - principalToman).coerceAtLeast(0L)
    return LoanPmtOutput(monthlyPayment, totalPayment, totalInterest)
}

data class InflationOutput(
    val adjustedToman: Long,
    val multiplier: Double,
    val inflationPercent: Double,
)

fun calcInflationValue(amountToman: Long, fromYear: Int, toYear: Int): InflationOutput {
    val iFrom = IRAN_CPI_INDEX[fromYear] ?: 2.1
    val iTo = IRAN_CPI_INDEX[toYear] ?: 428.0
    val multiplier = if (iFrom > 0.0) iTo / iFrom else 1.0
    val adjusted = (amountToman.toDouble() * multiplier).roundToLong()
    val inflationPercent = (multiplier - 1.0) * 100.0
    return InflationOutput(adjusted, multiplier, inflationPercent)
}

enum class CalcTab(val id: String, val labelFa: String, val badgeFa: String) {
    COIN_BUBBLE("coin_bubble", "حباب‌سنج سکه", "طلا و سکه"),
    LOAN("loan", "محاسبه قسط وام", "تسهیلات بانکی"),
    INFLATION("inflation", "تورم و قدرت خرید", "شاخص CPI"),
    MEHRIEH("mehrieh", "ارزش روز مهریه", "فرمول قوه قضاییه"),
    TAKHIR("takhir", "تأخیر تادیه", "ماده ۵۲۲"),
    SALARY("salary", "قدرت خرید حقوق", "تورم در برابر حقوق"),
    HABIT("habit", "هزینه عادت روزانه", "سود مرکب"),
}

internal fun enDigits(s: String): String = buildString(s.length) {
    for (c in s) {
        append(
            when (c) {
                in '۰'..'۹' -> '0' + (c - '۰')
                in '٠'..'٩' -> '0' + (c - '٠')
                else -> c
            },
        )
    }
}

private fun parseCleanLong(raw: String): Long =
    enDigits(raw).replace(",", "").replace(" ", "").trim().toLongOrNull() ?: 0L

private fun formatFaPercent(v: Double): String =
    faDigits(String.format(java.util.Locale.US, "%.1f", v))

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CalculatorsBottomSheet(
    effectiveRates: Map<String, Double>,
    onDismiss: () -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val t = CanvasTheme.tokens
    var activeTab by remember { mutableStateOf(CalcTab.COIN_BUBBLE) }

    val defaultGold18 = (effectiveRates["gold18"] ?: 6_540_000.0).roundToLong()
    val defaultCoinEmami = (effectiveRates["coin"] ?: effectiveRates["coin_emami"] ?: 74_800_000.0).roundToLong()

    // 1. Coin bubble state
    var selectedCoinIndex by remember { mutableIntStateOf(0) }
    var gold18Input by remember(defaultGold18) { mutableStateOf(defaultGold18.toString()) }
    var coinPriceInput by remember(defaultCoinEmami) { mutableStateOf(defaultCoinEmami.toString()) }

    // 2. Loan PMT state
    var loanAmountInput by remember { mutableStateOf("200000000") }
    var loanRate by remember { mutableFloatStateOf(23f) }
    var loanMonths by remember { mutableIntStateOf(36) }

    // 3. Inflation state
    var infAmountInput by remember { mutableStateOf("10000000") }
    var infFromYear by remember { mutableIntStateOf(1395) }
    var infToYear by remember { mutableIntStateOf(1405) }

    // 4. Mehrieh state
    var mehriehAmountInput by remember { mutableStateOf("1000000") }
    var mehriehMarriageYear by remember { mutableIntStateOf(1385) }
    var mehriehClaimYear by remember { mutableIntStateOf(1405) }

    // 5. Takhir Tadieh state
    var takhirDebtInput by remember { mutableStateOf("50000000") }
    var takhirDueYear by remember { mutableIntStateOf(1400) }
    var takhirPayYear by remember { mutableIntStateOf(1405) }

    // 6. Salary power state
    var oldSalaryInput by remember { mutableStateOf("12000000") }
    var oldSalaryYear by remember { mutableIntStateOf(1400) }
    var newSalaryInput by remember { mutableStateOf("35000000") }
    var newSalaryYear by remember { mutableIntStateOf(1405) }

    // 7. Habit cost state
    var habitDailyInput by remember { mutableStateOf("120000") }
    var habitDaysPerWeek by remember { mutableIntStateOf(5) }
    var habitYears by remember { mutableIntStateOf(3) }
    var habitYieldPercent by remember { mutableFloatStateOf(35f) }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = t.ground,
        shape = RoundedCornerShape(topStart = 32.dp, topEnd = 32.dp),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(horizontal = 20.dp)
                .padding(bottom = 24.dp),
        ) {
            // Header
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column {
                    Text(
                        text = "ابزارها و ماشین‌حساب‌های مالی",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Black,
                        color = t.ink,
                    )
                    Text(
                        text = "۷ محاسبه‌گر تخصصی اقتصاد ایران با نرخ‌های لحظه‌ای",
                        style = MaterialTheme.typography.bodySmall,
                        color = t.muted,
                    )
                }
                IconButton(
                    onClick = onDismiss,
                    modifier = Modifier
                        .size(38.dp)
                        .clip(CircleShape)
                        .background(t.card)
                        .testTag("close_calculators_button"),
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Close,
                        contentDescription = "بستن",
                        tint = t.inkSecondary,
                        modifier = Modifier.size(18.dp),
                    )
                }
            }

            Spacer(Modifier.height(14.dp))

            // Horizontal Scrollable Pill Tabs
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                CalcTab.entries.forEach { tab ->
                    val selected = activeTab == tab
                    Surface(
                        onClick = { activeTab = tab },
                        shape = RoundedCornerShape(999.dp),
                        color = if (selected) t.primaryAction else t.card,
                        shadowElevation = if (selected) 4.dp else 1.dp,
                        modifier = Modifier.testTag("calc_tab_${tab.id}"),
                    ) {
                        Text(
                            text = tab.labelFa,
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Bold,
                            color = if (selected) t.onPrimaryAction else t.inkSecondary,
                        )
                    }
                }
            }

            Spacer(Modifier.height(16.dp))

            // Active Calculator Body
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                when (activeTab) {
                    CalcTab.COIN_BUBBLE -> {
                        val spec = COIN_SPECS_LIST[selectedCoinIndex]
                        val g18 = parseCleanLong(gold18Input).coerceAtLeast(100_000L)
                        val cPrice = parseCleanLong(coinPriceInput).coerceAtLeast(100_000L)
                        val res = calcCoinBubbleSpec(spec, cPrice, g18)

                        LitheCard {
                            Text(
                                text = "نوع سکه",
                                style = MaterialTheme.typography.labelSmall,
                                color = t.muted,
                                fontWeight = FontWeight.Bold,
                            )
                            Spacer(Modifier.height(8.dp))
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .horizontalScroll(rememberScrollState()),
                                horizontalArrangement = Arrangement.spacedBy(6.dp),
                            ) {
                                COIN_SPECS_LIST.forEachIndexed { idx, s ->
                                    val sel = idx == selectedCoinIndex
                                    Surface(
                                        onClick = {
                                            selectedCoinIndex = idx
                                            val livePrice = when (s.id) {
                                                "coin_emami" -> effectiveRates["coin"] ?: 74_800_000.0
                                                "coin_bahar" -> effectiveRates["coin_bahar"] ?: 71_200_000.0
                                                "coin_nim" -> effectiveRates["coin_nim"] ?: 42_600_000.0
                                                "coin_rob" -> effectiveRates["coin_rob"] ?: 24_900_000.0
                                                else -> 11_800_000.0
                                            }
                                            coinPriceInput = livePrice.roundToLong().toString()
                                        },
                                        shape = RoundedCornerShape(14.dp),
                                        color = if (sel) Color(0xFF2563EB) else t.track,
                                    ) {
                                        Text(
                                            text = s.labelFa,
                                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                                            style = MaterialTheme.typography.labelSmall,
                                            fontWeight = FontWeight.Bold,
                                            color = if (sel) Color.White else t.inkSecondary,
                                        )
                                    }
                                }
                            }

                            Spacer(Modifier.height(12.dp))
                            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                Box(Modifier.weight(1f)) {
                                    CalcNumberField(
                                        label = "نرخ گرم طلای ۱۸ عیار (تومان)",
                                        value = gold18Input,
                                        onValueChange = { gold18Input = it },
                                    )
                                }
                                Box(Modifier.weight(1f)) {
                                    CalcNumberField(
                                        label = "قیمت روز ${spec.labelFa} (تومان)",
                                        value = coinPriceInput,
                                        onValueChange = { coinPriceInput = it },
                                    )
                                }
                            }
                        }

                        val riskColor = when (res.riskLevel) {
                            "high" -> Color(0xFFF43F5E)
                            "medium" -> Color(0xFFF59E0B)
                            else -> Color(0xFF10B981)
                        }
                        val riskLabel = when (res.riskLevel) {
                            "high" -> "حباب سنگین (ریسک بالا)"
                            "medium" -> "حباب متعادل بازار"
                            else -> "حباب کم (نزدیک ارزش ذاتی)"
                        }

                        LitheResultCard(
                            badge = riskLabel,
                            badgeColor = riskColor,
                            title = "درصد حباب ${spec.labelFa}",
                            heroValue = "٪${formatFaPercent(res.bubblePercent)}",
                            heroSub = "معادل ${faNumber(res.bubbleToman)} تومان بالاتر از ارزش ذاتی طلا",
                            metrics = listOf(
                                "ارزش ذاتی طلا" to "${faNumber(res.intrinsicToman)} ت",
                                "مبلغ حباب" to "${faNumber(res.bubbleToman)} ت",
                            ),
                        )
                    }

                    CalcTab.LOAN -> {
                        val principal = parseCleanLong(loanAmountInput)
                        val res = calcLoanPmtValue(principal, loanRate.toDouble(), loanMonths)

                        LitheCard {
                            CalcNumberField(
                                label = "مبلغ کل وام (تومان)",
                                value = loanAmountInput,
                                onValueChange = { loanAmountInput = it },
                            )
                            Spacer(Modifier.height(12.dp))
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                            ) {
                                Text("نرخ سود سالانه", style = MaterialTheme.typography.labelSmall, color = t.muted)
                                Text(
                                    "٪${faDigits(loanRate.toInt().toString())}",
                                    style = MaterialTheme.typography.labelMedium,
                                    fontWeight = FontWeight.Black,
                                    color = Color(0xFF2563EB),
                                )
                            }
                            Slider(
                                value = loanRate,
                                onValueChange = { loanRate = it },
                                valueRange = 0f..36f,
                                colors = SliderDefaults.colors(
                                    thumbColor = Color(0xFF2563EB),
                                    activeTrackColor = Color(0xFF2563EB),
                                ),
                            )
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                            ) {
                                Text("مدت بازپرداخت", style = MaterialTheme.typography.labelSmall, color = t.muted)
                                Text(
                                    "${faDigits(loanMonths.toString())} ماه",
                                    style = MaterialTheme.typography.labelMedium,
                                    fontWeight = FontWeight.Black,
                                    color = t.ink,
                                )
                            }
                            Slider(
                                value = loanMonths.toFloat(),
                                onValueChange = { loanMonths = it.toInt().coerceAtLeast(6) },
                                valueRange = 6f..120f,
                                colors = SliderDefaults.colors(
                                    thumbColor = t.primaryAction,
                                    activeTrackColor = t.primaryAction,
                                ),
                            )
                        }

                        LitheResultCard(
                            badge = "فرمول استاندارد بانکی PMT",
                            badgeColor = Color(0xFF2563EB),
                            title = "مبلغ هر قسط ماهانه",
                            heroValue = "${faNumber(res.monthlyPayment)} تومان",
                            heroSub = "برای ${faDigits(loanMonths.toString())} ماه با نرخ سود ٪${faDigits(loanRate.toInt().toString())}",
                            metrics = listOf(
                                "کل بازپرداخت" to "${faNumber(res.totalPayment)} ت",
                                "کل سود بانکی" to "${faNumber(res.totalInterest)} ت",
                            ),
                        )
                    }

                    CalcTab.INFLATION -> {
                        val amt = parseCleanLong(infAmountInput)
                        val res = calcInflationValue(amt, infFromYear, infToYear)

                        LitheCard {
                            CalcNumberField(
                                label = "مبلغ در سال مبدا (تومان)",
                                value = infAmountInput,
                                onValueChange = { infAmountInput = it },
                            )
                            Spacer(Modifier.height(12.dp))
                            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                Box(Modifier.weight(1f)) {
                                    YearSelectorDropdown(
                                        label = "از سال",
                                        selectedYear = infFromYear,
                                        onSelect = { infFromYear = it },
                                    )
                                }
                                Box(Modifier.weight(1f)) {
                                    YearSelectorDropdown(
                                        label = "به سال",
                                        selectedYear = infToYear,
                                        onSelect = { infToYear = it },
                                    )
                                }
                            }
                        }

                        LitheResultCard(
                            badge = "بر اساس شاخص بهای مصرف‌کننده (CPI)",
                            badgeColor = Color(0xFFF59E0B),
                            title = "ارزش معادل در سال ${faDigits(infToYear.toString())}",
                            heroValue = "${faNumber(res.adjustedToman)} تومان",
                            heroSub = "تورم انباشته این دوره: ٪${formatFaPercent(res.inflationPercent)}",
                            metrics = listOf(
                                "ضریب رشد شاخص" to "${formatFaPercent(res.multiplier)} برابر",
                                "افت قدرت خرید ریال" to "٪${formatFaPercent((1.0 - 1.0 / res.multiplier.coerceAtLeast(1.0)) * 100.0)}",
                            ),
                        )
                    }

                    CalcTab.MEHRIEH -> {
                        val amt = parseCleanLong(mehriehAmountInput)
                        val prevYear = (mehriehClaimYear - 1).coerceIn(1380, 1405)
                        val iMarriage = IRAN_CPI_INDEX[mehriehMarriageYear] ?: 2.1
                        val iPrev = IRAN_CPI_INDEX[prevYear] ?: 325.0
                        val ratio = if (iMarriage > 0.0) iPrev / iMarriage else 1.0
                        val currentVal = (amt.toDouble() * ratio).roundToLong()

                        LitheCard {
                            CalcNumberField(
                                label = "مبلغ مهریه وجه رایج در عقدنامه (تومان)",
                                value = mehriehAmountInput,
                                onValueChange = { mehriehAmountInput = it },
                            )
                            Spacer(Modifier.height(12.dp))
                            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                Box(Modifier.weight(1f)) {
                                    YearSelectorDropdown(
                                        label = "سال وقوع عقد",
                                        selectedYear = mehriehMarriageYear,
                                        onSelect = { mehriehMarriageYear = it },
                                    )
                                }
                                Box(Modifier.weight(1f)) {
                                    YearSelectorDropdown(
                                        label = "سال مطالبه",
                                        selectedYear = mehriehClaimYear,
                                        onSelect = { mehriehClaimYear = it },
                                    )
                                }
                            }
                        }

                        LitheResultCard(
                            badge = "فرمول رسمی بانک مرکزی (شاخص سال قبل)",
                            badgeColor = Color(0xFF10B981),
                            title = "ارزش روز مهریه به نرخ شاخص ${faDigits(prevYear.toString())}",
                            heroValue = "${faNumber(currentVal)} تومان",
                            heroSub = "نسبت شاخص سال ${faDigits(prevYear.toString())} به سال عقد (${faDigits(mehriehMarriageYear.toString())}): ${formatFaPercent(ratio)} برابر",
                            metrics = listOf(
                                "شاخص سال عقد" to formatFaPercent(iMarriage),
                                "شاخص سال قبل مطالبه" to formatFaPercent(iPrev),
                            ),
                        )
                    }

                    CalcTab.TAKHIR -> {
                        val debt = parseCleanLong(takhirDebtInput)
                        val iDue = IRAN_CPI_INDEX[takhirDueYear] ?: 84.3
                        val iPay = IRAN_CPI_INDEX[takhirPayYear] ?: 428.0
                        val ratio = if (iDue > 0.0) (iPay / iDue).coerceAtLeast(1.0) else 1.0
                        val totalPayable = (debt.toDouble() * ratio).roundToLong()
                        val penalty = (totalPayable - debt).coerceAtLeast(0L)

                        LitheCard {
                            CalcNumberField(
                                label = "اصل مبلغ چک یا طلب (تومان)",
                                value = takhirDebtInput,
                                onValueChange = { takhirDebtInput = it },
                            )
                            Spacer(Modifier.height(12.dp))
                            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                Box(Modifier.weight(1f)) {
                                    YearSelectorDropdown(
                                        label = "سال سررسید چک / دین",
                                        selectedYear = takhirDueYear,
                                        onSelect = { takhirDueYear = it },
                                    )
                                }
                                Box(Modifier.weight(1f)) {
                                    YearSelectorDropdown(
                                        label = "سال وصول / پرداخت",
                                        selectedYear = takhirPayYear,
                                        onSelect = { takhirPayYear = it },
                                    )
                                }
                            }
                        }

                        LitheResultCard(
                            badge = "ماده ۵۲۲ قانون آیین دادرسی مدنی",
                            badgeColor = Color(0xFFEA5C3C),
                            title = "مجموع اصل دین و خسارت تأخیر تادیه",
                            heroValue = "${faNumber(totalPayable)} تومان",
                            heroSub = "خسارت تأخیر خالص: ${faNumber(penalty)} تومان",
                            metrics = listOf(
                                "اصل بدهی" to "${faNumber(debt)} ت",
                                "ضریب شاخص" to "${formatFaPercent(ratio)} برابر",
                            ),
                        )
                    }

                    CalcTab.SALARY -> {
                        val oldSal = parseCleanLong(oldSalaryInput)
                        val newSal = parseCleanLong(newSalaryInput)
                        val inf = calcInflationValue(oldSal, oldSalaryYear, newSalaryYear)
                        val diff = newSal - inf.adjustedToman
                        val diffPct = if (inf.adjustedToman > 0L) (diff.toDouble() / inf.adjustedToman.toDouble()) * 100.0 else 0.0

                        LitheCard {
                            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                Box(Modifier.weight(1f)) {
                                    CalcNumberField(
                                        label = "حقوق قدیم (تومان)",
                                        value = oldSalaryInput,
                                        onValueChange = { oldSalaryInput = it },
                                    )
                                }
                                Box(Modifier.weight(1f)) {
                                    YearSelectorDropdown(
                                        label = "سال قدیم",
                                        selectedYear = oldSalaryYear,
                                        onSelect = { oldSalaryYear = it },
                                    )
                                }
                            }
                            Spacer(Modifier.height(10.dp))
                            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                Box(Modifier.weight(1f)) {
                                    CalcNumberField(
                                        label = "حقوق فعلی (تومان)",
                                        value = newSalaryInput,
                                        onValueChange = { newSalaryInput = it },
                                    )
                                }
                                Box(Modifier.weight(1f)) {
                                    YearSelectorDropdown(
                                        label = "سال فعلی",
                                        selectedYear = newSalaryYear,
                                        onSelect = { newSalaryYear = it },
                                    )
                                }
                            }
                        }

                        val ahead = diff >= 0L
                        LitheResultCard(
                            badge = if (ahead) "رشد واقعی قدرت خرید" else "عقب‌ماندگی از تورم",
                            badgeColor = if (ahead) Color(0xFF10B981) else Color(0xFFF43F5E),
                            title = "حقوق لازم برای حفظ قدرت خرید",
                            heroValue = "${faNumber(inf.adjustedToman)} تومان",
                            heroSub = if (ahead)
                                "حقوق فعلی شما ${faNumber(diff)} تومان (٪${formatFaPercent(diffPct)}) جلوتر از تورم است"
                            else
                                "حقوق فعلی شما ${faNumber(-diff)} تومان (٪${formatFaPercent(-diffPct)}) کمتر از تورم رشد کرده است",
                            metrics = listOf(
                                "حقوق فعلی" to "${faNumber(newSal)} ت",
                                "شکاف با تورم" to "${if (ahead) "+" else "-"}${faNumber(kotlin.math.abs(diff))} ت",
                            ),
                        )
                    }

                    CalcTab.HABIT -> {
                        val daily = parseCleanLong(habitDailyInput)
                        val monthlyCost = (daily * habitDaysPerWeek * 52.0 / 12.0).roundToLong()
                        val months = habitYears * 12
                        val r = (habitYieldPercent.toDouble() / 100.0) / 12.0
                        val rawSpent = monthlyCost * months
                        val futureValue = if (r <= 0.0) rawSpent else {
                            (monthlyCost.toDouble() * (((1.0 + r).pow(months.toDouble()) - 1.0) / r) * (1.0 + r)).roundToLong()
                        }
                        val goldGrams = if (defaultGold18 > 0L) futureValue.toDouble() / defaultGold18.toDouble() else 0.0

                        LitheCard {
                            CalcNumberField(
                                label = "هزینه هر نوبت عادت (مثلاً قهوه یا اسنپ به تومان)",
                                value = habitDailyInput,
                                onValueChange = { habitDailyInput = it },
                            )
                            Spacer(Modifier.height(10.dp))
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                            ) {
                                Text("تعداد دفعات در هفته", style = MaterialTheme.typography.labelSmall, color = t.muted)
                                Text("${faDigits(habitDaysPerWeek.toString())} بار در هفته", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Black, color = t.ink)
                            }
                            Slider(
                                value = habitDaysPerWeek.toFloat(),
                                onValueChange = { habitDaysPerWeek = it.toInt().coerceIn(1, 7) },
                                valueRange = 1f..7f,
                            )
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                            ) {
                                Text("افق زمانی پس‌انداز", style = MaterialTheme.typography.labelSmall, color = t.muted)
                                Text("${faDigits(habitYears.toString())} سال", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Black, color = Color(0xFF2563EB))
                            }
                            Slider(
                                value = habitYears.toFloat(),
                                onValueChange = { habitYears = it.toInt().coerceIn(1, 10) },
                                valueRange = 1f..10f,
                            )
                        }

                        LitheResultCard(
                            badge = "اثر سود مرکب (بازده سالانه ٪${faDigits(habitYieldPercent.toInt().toString())})",
                            badgeColor = Color(0xFF10B981),
                            title = "سرمایه انباشته پس از ${faDigits(habitYears.toString())} سال",
                            heroValue = "${faNumber(futureValue)} تومان",
                            heroSub = "معادل تقریبی ${formatFaPercent(goldGrams)} گرم طلای ۱۸ عیار به نرخ امروز",
                            metrics = listOf(
                                "هزینه ماهانه عادت" to "${faNumber(monthlyCost)} ت",
                                "مجموع پول نقد خرج‌شده" to "${faNumber(rawSpent)} ت",
                            ),
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun LitheCard(content: @Composable () -> Unit) {
    val t = CanvasTheme.tokens
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(24.dp),
        color = t.card,
        shadowElevation = 2.dp,
    ) {
        Column(modifier = Modifier.padding(18.dp)) {
            content()
        }
    }
}

@Composable
private fun LitheResultCard(
    badge: String,
    badgeColor: Color,
    title: String,
    heroValue: String,
    heroSub: String,
    metrics: List<Pair<String, String>>,
) {
    val t = CanvasTheme.tokens
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(26.dp),
        color = t.card,
        shadowElevation = 3.dp,
    ) {
        Column(modifier = Modifier.padding(20.dp)) {
            Surface(
                shape = RoundedCornerShape(999.dp),
                color = badgeColor.copy(alpha = 0.12f),
            ) {
                Text(
                    text = badge,
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 5.dp),
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.ExtraBold,
                    color = badgeColor,
                )
            }
            Spacer(Modifier.height(10.dp))
            Text(
                text = title,
                style = MaterialTheme.typography.bodySmall,
                color = t.muted,
                fontWeight = FontWeight.Bold,
            )
            Spacer(Modifier.height(4.dp))
            Text(
                text = heroValue,
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Black,
                color = t.ink,
            )
            Spacer(Modifier.height(4.dp))
            Text(
                text = heroSub,
                style = MaterialTheme.typography.bodySmall,
                color = t.inkSecondary,
            )
            Spacer(Modifier.height(14.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                metrics.forEach { (k, v) ->
                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(16.dp))
                            .background(t.track.copy(alpha = 0.65f))
                            .padding(12.dp),
                    ) {
                        Text(k, style = MaterialTheme.typography.labelSmall, color = t.muted)
                        Spacer(Modifier.height(4.dp))
                        Text(v, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Black, color = t.ink)
                    }
                }
            }
        }
    }
}

@Composable
private fun CalcNumberField(
    label: String,
    value: String,
    onValueChange: (String) -> Unit,
) {
    val t = CanvasTheme.tokens
    Column {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = t.muted,
            fontWeight = FontWeight.Bold,
        )
        Spacer(Modifier.height(6.dp))
        OutlinedTextField(
            value = value,
            onValueChange = { raw ->
                val digitsOnly = enDigits(raw).filter { it.isDigit() }.take(13)
                onValueChange(digitsOnly)
            },
            singleLine = true,
            visualTransformation = GroupedNumber,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            shape = RoundedCornerShape(16.dp),
            colors = OutlinedTextFieldDefaults.colors(
                focusedContainerColor = t.track.copy(alpha = 0.5f),
                unfocusedContainerColor = t.track.copy(alpha = 0.5f),
                focusedBorderColor = Color(0xFF2563EB),
                unfocusedBorderColor = Color.Transparent,
            ),
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

@Composable
private fun YearSelectorDropdown(
    label: String,
    selectedYear: Int,
    onSelect: (Int) -> Unit,
) {
    val t = CanvasTheme.tokens
    var expanded by remember { mutableStateOf(false) }
    val years = remember { (1380..1405).toList().reversed() }

    Column {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = t.muted,
            fontWeight = FontWeight.Bold,
        )
        Spacer(Modifier.height(6.dp))
        Box {
            Surface(
                onClick = { expanded = true },
                shape = RoundedCornerShape(16.dp),
                color = t.track.copy(alpha = 0.5f),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 14.dp, vertical = 14.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = "سال ${faDigits(selectedYear.toString())}",
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Bold,
                        color = t.ink,
                    )
                    Text("▾", color = t.muted)
                }
            }
            DropdownMenu(
                expanded = expanded,
                onDismissRequest = { expanded = false },
                modifier = Modifier.height(260.dp),
            ) {
                years.forEach { yr ->
                    DropdownMenuItem(
                        text = {
                            Text(
                                text = "سال ${faDigits(yr.toString())}",
                                fontWeight = if (yr == selectedYear) FontWeight.Black else FontWeight.Normal,
                            )
                        },
                        onClick = {
                            onSelect(yr)
                            expanded = false
                        },
                    )
                }
            }
        }
    }
}
