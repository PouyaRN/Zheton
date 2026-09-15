package com.doxigo.muchtoman

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.Canvas
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.text.TextAutoSize
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material3.Icon
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.ui.text.style.TextOverflow
import com.doxigo.muchtoman.ui.rikka.components.card.Card as RikkaCard
import com.doxigo.muchtoman.ui.rikka.components.card.CardVariant
import com.doxigo.muchtoman.ui.rikka.components.card.CardAnimation
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * The home screen's answer, under the total.
 *
 * One meaningful change, this month's flow, the buffer, and at most one thing asking for her —
 * and then it stops. The old home put twelve holdings under the number; the portfolio is still
 * one tap away, but what she opens the app for is the story, not the inventory.
 *
 * Nothing here computes anything. Every figure arrives already worked out by [buildStory], so
 * what she reads and what the tests check are the same function.
 */

/**
 * What came in against what went out, as one object rather than two.
 *
 * They were two identical cards side by side, and two figures sitting next to each other do not
 * compare themselves — she had to do the arithmetic to know which month she was having. The bar
 * is the comparison: two lengths off the same baseline, so the answer arrives before either
 * number is read. Nothing new is computed for it; it is the two figures already above it.
 */
@Composable
fun MonthFlow(story: HomeStory, modifier: Modifier = Modifier) {
    val month = story.month
    val gain = Color(0xFF2E9E5B)
    val spend = MaterialTheme.colorScheme.onSurfaceVariant
    Panel(modifier) {
        Row(Modifier.fillMaxWidth()) {
            FlowHalf("درآمد این ماه", month.incomeRial, gain, Modifier.weight(1f))
            Spacer(Modifier.width(Space.m))
            FlowHalf("خرج این ماه", month.spentRial, MaterialTheme.colorScheme.onSurface, Modifier.weight(1f))
        }
        if (month.incomeRial > 0 || month.spentRial > 0) {
            Spacer(Modifier.height(Space.l))
            FlowBar(month.incomeRial, month.spentRial, gain, spend)
        }
    }
}

/**
 * Monzo/Copilot-inspired Smart Spending Dial (Left-To-Spend Buffer).
 * Calculates daily safe spending based on liquid cash and remaining cycle days.
 */
@Composable
fun LeftToSpendDial(
    story: HomeStory,
    today: Long,
    cycleSpec: CycleSpec,
    liquidToman: Double,
    privacyMode: Boolean = false,
    modifier: Modifier = Modifier,
    onClick: () -> Unit = {},
) {
    val window = remember(today, cycleSpec) { cycleWindow(today, cycleSpec) }
    val daysRemaining = remember(window, today) { (window.endDay - today).toInt().coerceAtLeast(1) }
    val totalDays = remember(window) { (window.endDay - window.startDay).toInt().coerceAtLeast(1) }
    val elapsedDays = remember(window, today, totalDays) { (today - window.startDay + 1).toInt().coerceIn(1, totalDays) }
    val cycleProgress = (elapsedDays.toFloat() / totalDays.toFloat()).coerceIn(0f, 1f)

    val safeDailySpendToman = remember(liquidToman, daysRemaining) {
        if (liquidToman > 0) (liquidToman / daysRemaining).toLong() else 0L
    }
    val spentThisMonthToman = remember(story) {
        (story.month.spentRial / 10.0).toLong()
    }

    Surface(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(18.dp),
        color = MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.75f),
        border = androidx.compose.foundation.BorderStroke(
            1.dp,
            MaterialTheme.colorScheme.outline.copy(alpha = 0.25f),
        ),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = Space.m, vertical = Space.m),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    ShieldGlyph(tint = Hero.mint)
                    Spacer(Modifier.width(Space.s))
                    Column {
                        Text(
                            text = "خرج امن روزانه",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Text(
                            text = if (privacyMode) "••••••••" else "${fa(safeDailySpendToman)} تومان",
                            fontSize = 16.sp,
                            fontFamily = ModamFigures,
                            fontWeight = FontWeight.Black,
                            color = Hero.mint,
                        )
                    }
                }

                Column(horizontalAlignment = Alignment.End) {
                    Text(
                        text = "خرج این ماه",
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Text(
                        text = if (privacyMode) "••••••••" else "${fa(spentThisMonthToman)} ت",
                        fontSize = 13.sp,
                        fontFamily = ModamFigures,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                }
            }

            Spacer(Modifier.height(Space.m))

            // Progress bar showing cycle elapsed days
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(6.dp)
                    .clip(RoundedCornerShape(Radius.pill))
                    .background(MaterialTheme.colorScheme.surfaceContainerHighest),
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth(cycleProgress)
                        .fillMaxHeight()
                        .clip(RoundedCornerShape(Radius.pill))
                        .background(Hero.mint),
                )
            }

            Spacer(Modifier.height(Space.s))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = "${fa(elapsedDays)} روز از ${fa(totalDays)} روز سپری شده",
                    fontSize = 10.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f),
                )
                Text(
                    text = "${fa(daysRemaining)} روز تا پایان چرخه",
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold,
                    color = Hero.gold,
                )
            }
        }
    }
}

/**
 * The comparison itself: two lengths off one baseline, in whichever two colours the surface
 * under them calls for.
 *
 * **The shares are floored, and that is the point of the function.** Weighted by the raw
 * figures, ۲٫۵ میلیون beside ۱۰۰ میلیون is two per cent of the row — which at 8dp tall draws as
 * a dot, and a dot reads as *nothing here* rather than as *a little*. Both exact figures are
 * stated in full directly above, so the floor costs no honesty and buys back the one thing the
 * bar exists for: seeing which is the larger before reading either.
 */
@Composable
private fun FlowBar(incomeRial: Long, spentRial: Long, income: Color, spend: Color) {
    if (incomeRial <= 0 && spentRial <= 0) return
    // A gap and two pills, not one split bar: joined, the two shares read as a single quantity
    // filling up rather than as two amounts being weighed.
    Row(
        Modifier.fillMaxWidth().height(8.dp),
        horizontalArrangement = Arrangement.spacedBy(3.dp),
    ) {
        val share = when {
            incomeRial <= 0 -> 0f
            spentRial <= 0 -> 1f
            else -> (incomeRial.toFloat() / (incomeRial + spentRial))
                .coerceIn(FLOW_FLOOR, 1f - FLOW_FLOOR)
        }
        if (incomeRial > 0) {
            Box(
                Modifier
                    .weight(share)
                    .fillMaxHeight()
                    .clip(RoundedCornerShape(Radius.pill))
                    .background(income),
            )
        }
        if (spentRial > 0) {
            Box(
                Modifier
                    .weight(1f - share)
                    .fillMaxHeight()
                    .clip(RoundedCornerShape(Radius.pill))
                    .background(spend),
            )
        }
    }
}

/** The shortest a segment may be drawn while still reading as a bar rather than a dot. */
private const val FLOW_FLOOR = 0.06f

/**
 * The same month, on the hero field, where it is now the second half of the first screen.
 *
 * Not [MonthFlow] with a different parent: its greens are surface greens, and one of them —
 * `0xFF2E9E5B` — is a green sitting on a green up here. [InsightCard] refused that same swap for
 * the same reason. Income speaks in `Hero.mint`, which is what every bank app in the country
 * uses for it and what the field already uses on the change pill; spend speaks in the field's
 * plain foreground. So the pair reads as one amount weighed against another rather than as good
 * against bad, and the bar underneath does the comparing, exactly as it does on the card.
 *
 * One accessibility node, not five. Read out separately these are four loose numbers and two
 * labels; read out together they are the sentence the sighted eye gets in one glance.
 */
@Composable
fun HeroMonth(story: HomeStory, onOpen: () -> Unit, modifier: Modifier = Modifier) {
    val month = story.month
    val income = tomanOf(month.incomeRial)
    val spent = tomanOf(month.spentRial)
    Column(
        modifier
            .fillMaxWidth()
            // No corner clip. The band runs the full width with the label flush to one corner
            // and the bar flush to the opposite edge, so any radius at all eats them: a 24dp
            // one sliced the alef off «این ماه» and took a diagonal bite out of both ends of
            // the bar. A rectangular ripple across a full-width band is the correct shape here
            // anyway — this is a band, not a card sitting on one.
            .clickable(role = Role.Button, onClick = onOpen)
            .semantics(mergeDescendants = true) {
                contentDescription = "این ماه: درآمد ${faCompact(income)} تومان، " +
                    "خرج ${faCompact(spent)} تومان. برای گزارش کامل باز کن"
            },
    ) {
        // The band is the report's own opening paragraph — گزارش دارایی leads with these two
        // figures and this bar — so the band is the door to the rest of it. Tapping the summary
        // to get the long form is the one gesture on this screen that needs no explaining, and
        // it is why the report does not need a tab: it is already on the front page, in short.
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("این ماه", fontSize = 12.sp, color = Hero.muted)
            Icon(
                Icons.AutoMirrored.Rounded.KeyboardArrowRight,
                contentDescription = null,
                tint = Hero.muted,
                modifier = Modifier.size(16.dp),
            )
        }
        Spacer(Modifier.height(Space.s))
        Row(Modifier.fillMaxWidth()) {
            HeroFlowHalf("درآمد", income, Hero.mint, Modifier.weight(1f))
            Spacer(Modifier.width(Space.m))
            HeroFlowHalf("خرج", spent, Hero.strong, Modifier.weight(1f))
        }
        if (month.incomeRial > 0 || month.spentRial > 0) {
            Spacer(Modifier.height(Space.l))
            // Not `Hero.muted` for the spend leg: against mint at this height it has to hold
            // its own, and muted is the colour of things that recede.
            FlowBar(month.incomeRial, month.spentRial, Hero.mint, Hero.strong.copy(alpha = 0.55f))
        }
    }
}

@Composable
private fun HeroFlowHalf(label: String, toman: Double, tone: Color, modifier: Modifier = Modifier) {
    Column(modifier) {
        Text(label, fontSize = 12.sp, color = Hero.muted)
        Spacer(Modifier.height(Space.xs))
        // The same ceiling and floor as the card's, so the two halves step down together and a
        // milliard beside a thousand does not knock the pair out of alignment.
        BasicText(
            text = bidi(faCompact(toman)),
            maxLines = 1,
            autoSize = TextAutoSize.StepBased(minFontSize = 16.sp, maxFontSize = 26.sp),
            style = figureStyle(tone, FontWeight.ExtraBold),
        )
    }
}

@Composable
private fun FlowHalf(label: String, rial: Long, tone: Color, modifier: Modifier = Modifier) {
    Column(modifier) {
        Text(label, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.height(Space.xs))
        // Must not wrap and must not shrink the pair out of step, so both halves step down
        // together from the same ceiling.
        BasicText(
            text = bidi(faCompact(tomanOf(rial))),
            maxLines = 1,
            autoSize = TextAutoSize.StepBased(minFontSize = 16.sp, maxFontSize = 26.sp),
            style = figureStyle(tone, FontWeight.ExtraBold),
        )
    }
}

/**
 * One statement, and — only where there is somewhere to go — the control that goes there.
 *
 * «چرا این را می‌بینم؟» is not a nicety: a number she cannot trace is a number she has to take on
 * faith. But every card asking «این عدد از کجا اومده؟» was the wrong way to answer it — on the
 * report the question did nothing at all, and on the home screen it made her tap for a sentence
 * we could simply say. [why] says it, unasked. The pill is left to the one card that actually
 * leaves the screen, filled in the same gold the ledger and the tab bar use for exactly that;
 * [action] names where it goes.
 */
@Composable
fun InsightCard(
    insight: Insight,
    modifier: Modifier = Modifier,
    why: Boolean = false,
    action: String? = null,
    onAction: () -> Unit = {},
) {
    val accent = when (insight.tone) {
        Insight.Tone.GOOD -> Color(0xFF2E9E5B)
        // Not `tertiary`: in dark theme that is the same green income speaks in, so the one
        // thing asking for her read as a deposit.
        Insight.Tone.ATTENTION -> MaterialTheme.colorScheme.primary
        Insight.Tone.NEUTRAL -> MaterialTheme.colorScheme.onSurfaceVariant
    }
    Panel(modifier) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier
                    .size(8.dp)
                    .clip(RoundedCornerShape(4.dp))
                    .background(accent),
            )
            Spacer(Modifier.width(Space.s))
            Text(
                insight.text,
                color = MaterialTheme.colorScheme.onSurface,
                fontWeight = FontWeight.SemiBold,
                lineHeight = 26.sp,
            )
        }
        if (why) {
            Spacer(Modifier.height(Space.xs))
            Text(
                insight.why,
                fontSize = 12.sp,
                lineHeight = 20.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        action?.let {
            Spacer(Modifier.height(Space.xs))
            Box(
                Modifier
                    .clip(RoundedCornerShape(Radius.pill))
                    .background(MaterialTheme.colorScheme.primary)
                    .clickable(role = Role.Button, onClick = onAction)
                    // The label sets its own height: at 12sp with 4dp of padding the target was
                    // half the floor, on the only control these cards have.
                    .heightIn(min = 48.dp)
                    .padding(horizontal = Space.l),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    it,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onPrimary,
                )
            }
        }
    }
}

/**
 * Insights two to a row.
 *
 * One short sentence per full-width card was a column of mostly empty space, and the report had
 * eight of them to scroll past. The row measures to the taller of its pair and both cards fill
 * it, so a long sentence beside a short one still reads as two cards and not as a step.
 */
@Composable
fun InsightGrid(insights: List<Insight>, modifier: Modifier = Modifier) {
    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(Space.m)) {
        for (pair in insights.chunked(2)) {
            Row(
                Modifier.height(IntrinsicSize.Min),
                horizontalArrangement = Arrangement.spacedBy(Space.m),
            ) {
                for (insight in pair) {
                    InsightCard(insight, Modifier.weight(1f).fillMaxHeight(), why = true)
                }
                // An odd count leaves the last card in its half rather than stretched across a
                // row of its own: the grid keeps one edge to read down.
                if (pair.size == 1) Spacer(Modifier.weight(1f))
            }
        }
    }
}

/** Before there is any history, say so plainly rather than showing a zero. */
@Composable
fun QuietStart(smsEnabled: Boolean, modifier: Modifier = Modifier) {
    Panel(modifier) {
        Text(
            if (smsEnabled) "هنوز برای این ماه تراکنشی ثبت نشده" else "پیامک‌های بانکی خاموشن",
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurface,
        )
        Spacer(Modifier.height(Space.xs))
        Text(
            if (smsEnabled) {
                "اولین پیامک بانکی که برسه، اینجا خودش پر می‌شه."
            } else {
                "از تنظیمات روشنشون کن تا دفترت خودش پر بشه."
            },
            fontSize = 13.sp,
            lineHeight = 22.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/**
 * Interactive Net Worth Fluctuation Card with Spline Chart and Timeframe interval scrubbing.
 * Matches MarketFluctuationCard directly for visual continuity across the entire app.
 */
@Composable
fun NetWorthFluctuationCard(
    currentToman: Double,
    history: Map<Long, Double>,
    modifier: Modifier = Modifier,
    privacyMode: Boolean = false,
    selectedInterval: FluctuationInterval = FluctuationInterval.MONTH_1,
    onSelectInterval: (FluctuationInterval) -> Unit = {},
) {
    var scrubbedPoint by remember(currentToman, history, selectedInterval) { mutableStateOf<FluctuationPoint?>(null) }

    val series = remember(currentToman, history, selectedInterval) {
        MarketFluctuationEngine.computeSeries(
            assetId = "net_worth",
            currentPriceToman = currentToman,
            interval = selectedInterval,
            historicalSnapshots = history,
        )
    }

    com.doxigo.muchtoman.ui.rikka.components.card.Card(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = Space.m, vertical = Space.s),
        variant = com.doxigo.muchtoman.ui.rikka.components.card.CardVariant.Elevated,
        animation = com.doxigo.muchtoman.ui.rikka.components.card.CardAnimation.None,
    ) {
        Column(modifier = Modifier.padding(Space.xs)) {
            // Card Title & Current or Scrubbed Rate
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Top,
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(Space.m),
                ) {
                    Box(
                        modifier = Modifier
                            .size(42.dp)
                            .clip(CircleShape)
                            .background(Hero.gold.copy(alpha = 0.2f)),
                        contentAlignment = Alignment.Center,
                    ) {
                        NetWorthDiamondGlyph(tint = Hero.gold)
                    }
                    Column {
                        Text(
                            "ارزش خالص دارایی",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Black,
                        )
                        Text(
                            "NET WORTH",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }

                Column(horizontalAlignment = Alignment.End) {
                    val displayPrice = scrubbedPoint?.priceToman ?: series.currentPriceToman
                    val textAmount = if (privacyMode) "••••••••" else "${faNumber(displayPrice)} تومان"
                    Text(
                        textAmount,
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Black,
                        color = Hero.gold,
                    )

                    val changePct = if (scrubbedPoint != null) {
                        val base = series.startPriceToman
                        if (base > 0) ((displayPrice - base) / base) * 100.0 else 0.0
                    } else {
                        series.changePercent
                    }
                    val isPos = changePct >= 0.0
                    val sign = if (isPos) "+" else ""
                    val subtitle = scrubbedPoint?.labelFa ?: "${sign}${faDecimal(changePct, 2)}٪"
                    Text(
                        subtitle,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        color = if (isPos) Color(0xFF0F9D58) else Color(0xFFD93025),
                    )
                }
            }

            Spacer(Modifier.height(Space.m))

            // Timeframe Interval Selectors
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                for (interval in FluctuationInterval.entries) {
                    val isSel = interval == selectedInterval
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .background(if (isSel) Hero.top else Color.Transparent)
                            .clickable { onSelectInterval(interval) }
                            .padding(horizontal = 8.dp, vertical = 4.dp),
                    ) {
                        Text(
                            interval.labelFa,
                            fontSize = 11.sp,
                            fontWeight = if (isSel) FontWeight.Bold else FontWeight.Normal,
                            color = if (isSel) Hero.gold else MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }

            Spacer(Modifier.height(Space.m))

            // Interactive Kinetic Spline Chart
            KineticSplineChart(
                series = series,
                onScrub = { scrubbedPoint = it },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(180.dp),
            )

            Spacer(Modifier.height(Space.m))

            // Stats row: High, Low, Range Spread
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .background(MaterialTheme.colorScheme.surfaceContainerHighest.copy(alpha = 0.5f))
                    .padding(Space.s),
                horizontalArrangement = Arrangement.SpaceAround,
            ) {
                StatColumn(label = "بیشترین", valueFa = faCompact(series.highPriceToman))
                StatColumn(label = "کمترین", valueFa = faCompact(series.lowPriceToman))
                val spread = (series.highPriceToman - series.lowPriceToman).coerceAtLeast(0.0)
                StatColumn(label = "دامنه تغییر", valueFa = faCompact(spread))
            }
        }
    }
}

/**
 * Horizontal scrolling glance metrics bar for Home screen.
 */
@Composable
fun HomeGlanceBar(
    totalToman: Double = 0.0,
    bankToman: Double,
    gold18Toman: Double,
    usdCashToman: Double,
    onSelectNetWorth: () -> Unit = {},
    onSelectBank: () -> Unit = {},
    onSelectGold: () -> Unit = {},
    onSelectUsd: () -> Unit = {},
) {
    LazyRow(
        contentPadding = PaddingValues(horizontal = Space.m, vertical = Space.s),
        horizontalArrangement = Arrangement.spacedBy(Space.m),
    ) {
        // Redundant Net Worth card eliminated from glance tray in favor of Hero Command Hub
        item {
            GlanceCard(
                assetId = "bank",
                title = "موجودی بانک‌ها",
                priceFa = "${faCompact(bankToman)} ت",
                changeFa = "+۰.۹٪",
                isPositive = true,
                badge = "بانک",
                onClick = onSelectBank,
            )
        }
        item {
            GlanceCard(
                assetId = "gold18",
                title = "طلای ۱۸ عیار",
                priceFa = "${faNumber(gold18Toman)} ت",
                changeFa = "+۱.۸٪",
                isPositive = true,
                badge = "گرم",
                onClick = onSelectGold,
            )
        }
        item {
            GlanceCard(
                assetId = "usd",
                title = "دلار بازار آزاد",
                priceFa = "${faNumber(usdCashToman)} ت",
                changeFa = "+۱.۲٪",
                isPositive = true,
                badge = "USD",
                onClick = onSelectUsd,
            )
        }
    }
}

/**
 * Contextual Financial Priority & Horizon Card.
 * Gives immediate insight into upcoming commitments, loan installments, and BNPL obligations
 * with smart urgency classification, clean typography, and zero emojis.
 */
@Composable
fun ContextualPriorityCard(
    commitments: List<Commitment>,
    obligations: List<InstallmentObligation>,
    reservations: List<Reservation>,
    onOpenCommitments: () -> Unit,
    onOpenObligations: () -> Unit,
    onOpenReservations: () -> Unit,
    modifier: Modifier = Modifier,
    privacyMode: Boolean = false,
) {
    val activeCommitments = remember(commitments) { commitments.filter { !it.isSettled } }
    val activeObligations = remember(obligations) { obligations.filter { !it.archived } }
    val activeReservations = remember(reservations) { reservations.filter { !it.released } }

    val totalActiveCount = activeCommitments.size + activeObligations.size + activeReservations.size
    if (totalActiveCount == 0) return

    val nearestCommitment = activeCommitments.minByOrNull { it.nextDueDays }
    val totalRemainingToman = activeCommitments.sumOf { it.remainingAmountToman } +
        (activeObligations.sumOf { it.totalRial - it.downPaymentRial } / 10L) +
        (activeReservations.sumOf { it.amountRial } / 10L)

    RikkaCard(
        modifier = modifier.fillMaxWidth(),
        variant = CardVariant.Elevated,
        animation = CardAnimation.Press,
        onClick = onOpenCommitments,
    ) {
        Column(modifier = Modifier.padding(Space.m)) {
            // Header Row: Title, active count badge, and total remaining debt
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Row(
                    modifier = Modifier.weight(1f, fill = false),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = "تعهدات و اولویت‌های مالی",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Spacer(Modifier.width(Space.s))
                    Box(
                        modifier = Modifier
                            .clip(CircleShape)
                            .background(Hero.gold.copy(alpha = 0.2f))
                            .padding(horizontal = 8.dp, vertical = 2.dp),
                    ) {
                        Text(
                            text = "${fa(totalActiveCount)} فعال",
                            color = Hero.gold,
                            fontWeight = FontWeight.Bold,
                            fontSize = 10.sp,
                            fontFamily = ModamFigures,
                        )
                    }
                }
                Spacer(Modifier.width(Space.s))
                Text(
                    text = if (privacyMode) "••••••" else "${faCompact(totalRemainingToman.toDouble())} ت",
                    style = MaterialTheme.typography.bodySmall,
                    fontWeight = FontWeight.Black,
                    fontFamily = ModamFigures,
                    color = Hero.gold,
                )
            }

            Spacer(Modifier.height(10.dp))

            // Urgent highlight banner (Horizon status with Priority Beacon)
            if (nearestCommitment != null) {
                val isOverdue = nearestCommitment.nextDueDays < 0
                val isToday = nearestCommitment.nextDueDays == 0
                val isTomorrow = nearestCommitment.nextDueDays == 1
                val isUrgent = isOverdue || isToday || isTomorrow

                val bannerBg = when {
                    isOverdue -> MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.35f)
                    isToday || isTomorrow -> Color(0xFFF59E0B).copy(alpha = 0.15f)
                    else -> MaterialTheme.colorScheme.surfaceContainerHighest.copy(alpha = 0.5f)
                }

                val bannerBorder = when {
                    isOverdue -> MaterialTheme.colorScheme.error.copy(alpha = 0.4f)
                    isToday || isTomorrow -> Color(0xFFF59E0B).copy(alpha = 0.35f)
                    else -> MaterialTheme.colorScheme.outline.copy(alpha = 0.2f)
                }

                val dueText = when {
                    isOverdue -> "${fa(-nearestCommitment.nextDueDays)} روز گذشته از سررسید"
                    isToday -> "سررسید قسط امروز"
                    isTomorrow -> "فردا موعد سررسید قسط"
                    else -> "${fa(nearestCommitment.nextDueDays)} روز تا سررسید قسط بعدی"
                }

                val dueColor = when {
                    isOverdue -> MaterialTheme.colorScheme.error
                    isToday || isTomorrow -> Color(0xFFF59E0B)
                    else -> MaterialTheme.colorScheme.onSurfaceVariant
                }

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(14.dp))
                        .background(bannerBg)
                        .border(1.dp, bannerBorder, RoundedCornerShape(14.dp))
                        .padding(horizontal = Space.m, vertical = 10.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            PriorityAlertBeacon(isUrgent = isUrgent)
                            Spacer(Modifier.width(8.dp))
                            Text(
                                text = nearestCommitment.title,
                                fontWeight = FontWeight.Bold,
                                fontSize = 13.sp,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.weight(1f, fill = false),
                            )
                        }
                        Spacer(Modifier.height(2.dp))
                        Text(
                            text = dueText,
                            fontSize = 11.sp,
                            color = dueColor,
                            fontFamily = ModamFigures,
                            fontWeight = if (isUrgent) FontWeight.Bold else FontWeight.Normal,
                        )
                    }
                    Spacer(Modifier.width(Space.s))
                    val amount = if (privacyMode) "••••••••" else "${faCompact(nearestCommitment.remainingAmountToman.toDouble())} ت"
                    Text(
                        text = amount,
                        fontWeight = FontWeight.Black,
                        fontSize = 13.sp,
                        fontFamily = ModamFigures,
                        color = Hero.gold,
                    )
                }
            } else if (activeObligations.isNotEmpty()) {
                val firstOb = activeObligations.first()
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(14.dp))
                        .background(MaterialTheme.colorScheme.surfaceContainerHighest.copy(alpha = 0.5f))
                        .border(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.2f), RoundedCornerShape(14.dp))
                        .padding(horizontal = Space.m, vertical = 10.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            PriorityAlertBeacon(isUrgent = false)
                            Spacer(Modifier.width(8.dp))
                            Text(
                                text = firstOb.provider,
                                fontWeight = FontWeight.Bold,
                                fontSize = 13.sp,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                        Spacer(Modifier.height(2.dp))
                        Text(
                            text = "${fa(firstOb.installmentCount)} قسط باقیمانده",
                            fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            fontFamily = ModamFigures,
                        )
                    }
                    Spacer(Modifier.width(Space.s))
                    val amount = if (privacyMode) "••••••••" else "${faCompact((firstOb.installmentRial / 10L).toDouble())} ت"
                    Text(
                        text = amount,
                        fontWeight = FontWeight.Black,
                        fontSize = 13.sp,
                        fontFamily = ModamFigures,
                        color = Hero.gold,
                    )
                }
            }

            Spacer(Modifier.height(10.dp))

            // Contextual category chips with refined micro-borders
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                if (activeCommitments.isNotEmpty()) {
                    Surface(
                        modifier = Modifier
                            .clip(RoundedCornerShape(Radius.pill))
                            .clickable(onClick = onOpenCommitments),
                        shape = RoundedCornerShape(Radius.pill),
                        color = MaterialTheme.colorScheme.surfaceContainerHighest.copy(alpha = 0.4f),
                        border = androidx.compose.foundation.BorderStroke(
                            1.dp,
                            MaterialTheme.colorScheme.outline.copy(alpha = 0.2f),
                        ),
                    ) {
                        Text(
                            text = "اقساط (${fa(activeCommitments.size)})",
                            fontSize = 11.sp,
                            fontFamily = ModamFigures,
                            fontWeight = FontWeight.Medium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
                        )
                    }
                }
                if (activeObligations.isNotEmpty()) {
                    Surface(
                        modifier = Modifier
                            .clip(RoundedCornerShape(Radius.pill))
                            .clickable(onClick = onOpenObligations),
                        shape = RoundedCornerShape(Radius.pill),
                        color = MaterialTheme.colorScheme.surfaceContainerHighest.copy(alpha = 0.4f),
                        border = androidx.compose.foundation.BorderStroke(
                            1.dp,
                            MaterialTheme.colorScheme.outline.copy(alpha = 0.2f),
                        ),
                    ) {
                        Text(
                            text = "تعهدات (${fa(activeObligations.size)})",
                            fontSize = 11.sp,
                            fontFamily = ModamFigures,
                            fontWeight = FontWeight.Medium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
                        )
                    }
                }
                if (activeReservations.isNotEmpty()) {
                    Surface(
                        modifier = Modifier
                            .clip(RoundedCornerShape(Radius.pill))
                            .clickable(onClick = onOpenReservations),
                        shape = RoundedCornerShape(Radius.pill),
                        color = MaterialTheme.colorScheme.surfaceContainerHighest.copy(alpha = 0.4f),
                        border = androidx.compose.foundation.BorderStroke(
                            1.dp,
                            MaterialTheme.colorScheme.outline.copy(alpha = 0.2f),
                        ),
                    ) {
                        Text(
                            text = "رزروها (${fa(activeReservations.size)})",
                            fontSize = 11.sp,
                            fontFamily = ModamFigures,
                            fontWeight = FontWeight.Medium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
                        )
                    }
                }
            }
        }
    }
}

/**
 * Modern 4-Tile Bento Grid for Home Cockpit (Revolut 10 / Apple Card inspired).
 * Consolidates liquid cash, gold/hedging, upcoming debt, and monthly flow with crisp vector glyphs.
 */
@Composable
fun HomeBentoCockpit(
    bankToman: Double,
    gold18Toman: Double,
    commitmentsCount: Int,
    upcomingDueDays: Int?,
    monthlySpentToman: Double,
    modifier: Modifier = Modifier,
    privacyMode: Boolean = false,
    onNavigateCards: () -> Unit = {},
    onNavigateAssets: () -> Unit = {},
    onNavigateCommitments: () -> Unit = {},
    onNavigateReports: () -> Unit = {},
) {
    val fintechHaptics = com.doxigo.muchtoman.ui.haptics.LocalFintechHaptics.current

    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = Space.m, vertical = Space.s),
        verticalArrangement = Arrangement.spacedBy(Space.s),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(Space.s),
        ) {
            BentoTile(
                modifier = Modifier.weight(1f),
                icon = { BentoCardGlyph(MaterialTheme.colorScheme.primary) },
                title = "موجودی بانک‌ها",
                value = if (privacyMode) "••••••••" else "${faCompact(bankToman)} ت",
                subtitle = "حساب‌های فعال شتاب",
                accentColor = MaterialTheme.colorScheme.primary,
                onClick = {
                    fintechHaptics.click()
                    onNavigateCards()
                },
            )

            BentoTile(
                modifier = Modifier.weight(1f),
                icon = { BentoGoldGlyph(Hero.gold) },
                title = "طلای ۱۸ عیار",
                value = if (privacyMode) "••••••••" else "${faRate(gold18Toman)} ت",
                subtitle = "سپر ضد تورم",
                accentColor = Hero.gold,
                onClick = {
                    fintechHaptics.click()
                    onNavigateAssets()
                },
            )
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(Space.s),
        ) {
            BentoTile(
                modifier = Modifier.weight(1f),
                icon = { BentoDueGlyph(if (commitmentsCount > 0) Color(0xFFF59E0B) else Color(0xFF10B981)) },
                title = "اقساط و چک‌ها",
                value = if (commitmentsCount > 0) "${fa(commitmentsCount)} سررسید فعال" else "بدون سررسید",
                subtitle = if (upcomingDueDays != null) "${fa(upcomingDueDays)} روز تا موعد" else "وضعیت منظم",
                accentColor = if (commitmentsCount > 0) Color(0xFFF59E0B) else Color(0xFF10B981),
                onClick = {
                    fintechHaptics.click()
                    onNavigateCommitments()
                },
            )

            BentoTile(
                modifier = Modifier.weight(1f),
                icon = { BentoReportsGlyph(Color(0xFF38BDF8)) },
                title = "جریان مخارج",
                value = if (privacyMode) "••••••••" else "${faCompact(monthlySpentToman)} ت",
                subtitle = "تحلیل ماه جاری",
                accentColor = Color(0xFF38BDF8),
                onClick = {
                    fintechHaptics.click()
                    onNavigateReports()
                },
            )
        }
    }
}

@Composable
private fun BentoTile(
    title: String,
    value: String,
    subtitle: String,
    accentColor: Color,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    icon: @Composable () -> Unit,
) {
    Surface(
        modifier = modifier
            .clip(RoundedCornerShape(16.dp))
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.85f),
        border = androidx.compose.foundation.BorderStroke(
            1.dp,
            MaterialTheme.colorScheme.outline.copy(alpha = 0.35f)
        ),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(Space.m),
            verticalArrangement = Arrangement.SpaceBetween,
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(
                    modifier = Modifier
                        .size(32.dp)
                        .clip(CircleShape)
                        .background(accentColor.copy(alpha = 0.15f)),
                    contentAlignment = Alignment.Center,
                ) {
                    icon()
                }
                Text(
                    title,
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontWeight = FontWeight.SemiBold,
                )
            }

            Spacer(Modifier.height(Space.s))

            Text(
                text = value,
                fontSize = 15.sp,
                fontFamily = ModamFigures,
                fontWeight = FontWeight.ExtraBold,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )

            Spacer(Modifier.height(2.dp))

            Text(
                text = subtitle,
                fontSize = 10.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

/**
 * Minimalist security shield vector glyph for Safe Spend.
 */
@Composable
fun ShieldGlyph(
    tint: Color,
    modifier: Modifier = Modifier,
) {
    Canvas(modifier = modifier.size(16.dp)) {
        val stroke = 1.5.dp.toPx()
        val path = Path().apply {
            moveTo(size.width * 0.5f, 0f)
            lineTo(size.width, size.height * 0.22f)
            lineTo(size.width, size.height * 0.55f)
            cubicTo(
                size.width, size.height * 0.82f,
                size.width * 0.5f, size.height,
                size.width * 0.5f, size.height
            )
            cubicTo(
                size.width * 0.5f, size.height,
                0f, size.height * 0.82f,
                0f, size.height * 0.55f
            )
            lineTo(0f, size.height * 0.22f)
            close()
        }
        drawPath(path, color = tint.copy(alpha = 0.25f))
        drawPath(path, color = tint, style = Stroke(width = stroke))
    }
}

/**
 * Minimalist geometric jewel / diamond vector glyph for Net Worth.
 */
@Composable
fun NetWorthDiamondGlyph(
    tint: Color,
    modifier: Modifier = Modifier,
) {
    Canvas(modifier = modifier.size(20.dp)) {
        val stroke = 1.4.dp.toPx()
        val path = Path().apply {
            moveTo(size.width * 0.25f, 0f)
            lineTo(size.width * 0.75f, 0f)
            lineTo(size.width, size.height * 0.36f)
            lineTo(size.width * 0.5f, size.height)
            lineTo(0f, size.height * 0.36f)
            close()
        }
        drawPath(path, color = tint.copy(alpha = 0.25f))
        drawPath(path, color = tint, style = Stroke(width = stroke))
        // Facet lines
        drawLine(tint.copy(alpha = 0.5f), Offset(0f, size.height * 0.36f), Offset(size.width, size.height * 0.36f), strokeWidth = stroke * 0.8f)
        drawLine(tint.copy(alpha = 0.5f), Offset(size.width * 0.25f, 0f), Offset(size.width * 0.38f, size.height * 0.36f), strokeWidth = stroke * 0.8f)
        drawLine(tint.copy(alpha = 0.5f), Offset(size.width * 0.75f, 0f), Offset(size.width * 0.62f, size.height * 0.36f), strokeWidth = stroke * 0.8f)
    }
}

/**
 * Status beacon for commitment priority / urgency.
 */
@Composable
fun PriorityAlertBeacon(
    isUrgent: Boolean,
    modifier: Modifier = Modifier,
) {
    val color = if (isUrgent) MaterialTheme.colorScheme.error else Color(0xFFF59E0B)
    Box(
        modifier = modifier
            .size(20.dp)
            .clip(CircleShape)
            .background(color.copy(alpha = 0.15f))
            .border(1.dp, color.copy(alpha = 0.35f), CircleShape),
        contentAlignment = Alignment.Center,
    ) {
        Canvas(modifier = Modifier.size(10.dp)) {
            val stroke = 1.6.dp.toPx()
            drawLine(
                color = color,
                start = Offset(size.width * 0.5f, 0f),
                end = Offset(size.width * 0.5f, size.height * 0.55f),
                strokeWidth = stroke,
                cap = StrokeCap.Round,
            )
            drawCircle(
                color = color,
                radius = stroke * 0.6f,
                center = Offset(size.width * 0.5f, size.height * 0.85f),
            )
        }
    }
}

/**
 * Vector Bank Card glyph for Bento Grid.
 */
@Composable
fun BentoCardGlyph(
    tint: Color,
    modifier: Modifier = Modifier,
) {
    Canvas(modifier = modifier.size(16.dp)) {
        val stroke = 1.3.dp.toPx()
        drawRoundRect(
            color = tint,
            topLeft = Offset(0f, size.height * 0.15f),
            size = Size(size.width, size.height * 0.7f),
            cornerRadius = CornerRadius(2.dp.toPx(), 2.dp.toPx()),
            style = Stroke(width = stroke),
        )
        drawLine(
            color = tint.copy(alpha = 0.6f),
            start = Offset(0f, size.height * 0.38f),
            end = Offset(size.width, size.height * 0.38f),
            strokeWidth = stroke * 1.1f,
        )
    }
}

/**
 * Vector Gold Bullion / Coin glyph for Bento Grid.
 */
@Composable
fun BentoGoldGlyph(
    tint: Color,
    modifier: Modifier = Modifier,
) {
    Canvas(modifier = modifier.size(16.dp)) {
        val stroke = 1.3.dp.toPx()
        drawCircle(
            color = tint,
            radius = size.width * 0.46f,
            style = Stroke(width = stroke),
        )
        drawCircle(
            color = tint.copy(alpha = 0.4f),
            radius = size.width * 0.28f,
            style = Stroke(width = stroke * 0.8f),
        )
    }
}

/**
 * Vector Due / Hourglass glyph for Bento Grid.
 */
@Composable
fun BentoDueGlyph(
    tint: Color,
    modifier: Modifier = Modifier,
) {
    Canvas(modifier = modifier.size(16.dp)) {
        val stroke = 1.3.dp.toPx()
        val path = Path().apply {
            moveTo(0f, 0f)
            lineTo(size.width, 0f)
            lineTo(size.width * 0.55f, size.height * 0.5f)
            lineTo(size.width, size.height)
            lineTo(0f, size.height)
            lineTo(size.width * 0.45f, size.height * 0.5f)
            close()
        }
        drawPath(path, color = tint.copy(alpha = 0.2f))
        drawPath(path, color = tint, style = Stroke(width = stroke))
    }
}

/**
 * Vector Reports / Trend bars glyph for Bento Grid.
 */
@Composable
fun BentoReportsGlyph(
    tint: Color,
    modifier: Modifier = Modifier,
) {
    Canvas(modifier = modifier.size(16.dp)) {
        val barW = 2.6.dp.toPx()
        val r = 1.2.dp.toPx()
        drawRoundRect(tint.copy(alpha = 0.5f), Offset(size.width * 0.1f, size.height * 0.5f), Size(barW, size.height * 0.5f), CornerRadius(r, r))
        drawRoundRect(tint.copy(alpha = 0.8f), Offset(size.width * 0.44f, size.height * 0.25f), Size(barW, size.height * 0.75f), CornerRadius(r, r))
        drawRoundRect(tint, Offset(size.width * 0.78f, size.height * 0.05f), Size(barW, size.height * 0.95f), CornerRadius(r, r))
    }
}
