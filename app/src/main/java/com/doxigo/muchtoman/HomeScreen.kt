package com.doxigo.muchtoman

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.widget.Toast
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredHeight
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsTopHeight
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.pulltorefresh.PullToRefreshDefaults
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.doxigo.muchtoman.domain.shetab.ShetabKit
import com.doxigo.muchtoman.features.reminders.ReminderSettingsActivity
import com.doxigo.muchtoman.ui.components.CanvasCard
import com.doxigo.muchtoman.ui.components.CanvasGlyph
import com.doxigo.muchtoman.ui.components.CanvasPage
import com.doxigo.muchtoman.ui.components.OutlineIconButton
import com.doxigo.muchtoman.ui.components.RoundAction
import com.doxigo.muchtoman.ui.components.TonePill
import kotlin.math.abs

/*
 * The Today tab, as Main.dc.html (night) and HomeLight.dc.html (day) draw it: identity bar, the
 * one answer (today's share of the budget), the month at a glance, four round actions, net worth,
 * and today's transactions. Sizes are the artboard's px as dp; colours are CanvasTheme.tokens.
 */

private const val MASK = "••••••"

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    state: UiState,
    listState: LazyListState,
    bottomInset: Dp,
    privacyMode: Boolean,
    onTogglePrivacy: () -> Unit,
    onRefresh: () -> Unit,
    onSettings: () -> Unit,
    onSearch: () -> Unit,
    onQuickAdd: () -> Unit,
    onAssets: () -> Unit,
    onReport: () -> Unit,
    onLedger: () -> Unit,
    onOpenTransaction: (String) -> Unit,
    onManageAccounts: () -> Unit,
    onSetBudget: (Long) -> Unit,
    modifier: Modifier = Modifier,
) {
    val t = CanvasTheme.tokens
    val today = tehranDay(System.currentTimeMillis())
    var budgetDialog by remember { mutableStateOf(false) }
    var notifications by remember { mutableStateOf(false) }
    val alerts = remember(state.story, state.commitments, state.ledger.entries, state.strangeSenders) {
        homeAlerts(state)
    }

    CanvasPage(modifier) {
        // Pull-to-refresh refetches rates and wallets, as the old hero's button did.
        var pulled by remember { mutableStateOf(false) }
        LaunchedEffect(state.refreshing) { if (!state.refreshing) pulled = false }
        val pullState = rememberPullToRefreshState()
        PullToRefreshBox(
            isRefreshing = state.refreshing && pulled,
            onRefresh = { pulled = true; onRefresh() },
            state = pullState,
            indicator = {
                PullToRefreshDefaults.Indicator(
                    state = pullState,
                    isRefreshing = state.refreshing && pulled,
                    containerColor = t.card,
                    color = t.accent,
                    modifier = Modifier.align(Alignment.TopCenter).statusBarsPadding(),
                )
            },
            modifier = Modifier.fillMaxSize(),
        ) {
            LazyColumn(
                state = listState,
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(
                    start = CanvasTheme.gutter,
                    end = CanvasTheme.gutter,
                    bottom = bottomInset + 24.dp,
                ),
            ) {
                item(key = "bar") {
                    IdentityBar(
                        name = state.name,
                        date = faWeekdayDate(today),
                        hasAlerts = alerts.isNotEmpty(),
                        onSettings = onSettings,
                        onTogglePrivacy = onTogglePrivacy,
                        privacyMode = privacyMode,
                        onSearch = onSearch,
                        onNotifications = { notifications = true },
                    )
                }
                item(key = "answer") {
                    Answer(state.budget, state.bankToman, privacyMode)
                }
                item(key = "month") {
                    MonthGlance(state.budget, privacyMode, onSetBudget = { budgetDialog = true })
                }
                item(key = "actions") {
                    Actions(
                        onQuickAdd = onQuickAdd,
                        onAssets = onAssets,
                        onReport = onReport,
                        accounts = state.bankAccounts,
                        disabled = state.disabledBanks,
                        onManageAccounts = onManageAccounts,
                    )
                }
                item(key = "networth") {
                    val glance = remember(state.history, state.totals.toman, today) {
                        netWorthGlance(state.history, state.totals.toman, today)
                    }
                    NetWorthCard(glance, privacyMode, onAssets)
                }
                item(key = "today_head") {
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .padding(top = 22.dp)
                            .padding(start = 2.dp, end = 2.dp, bottom = 10.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.Bottom,
                    ) {
                        Text("امروز", fontFamily = Modam, fontSize = 17.sp, fontWeight = FontWeight.ExtraBold, color = t.ink)
                        Text(
                            "همهٔ تراکنش‌ها",
                            fontFamily = Modam,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold,
                            color = t.accentText,
                            modifier = Modifier
                                .clip(RoundedCornerShape(8.dp))
                                .clickable(role = Role.Button, onClick = onLedger),
                        )
                    }
                }
                val rows = todaysEntries(state.ledger.entries, today)
                if (rows.isEmpty()) {
                    item(key = "today_empty") {
                        Text(
                            "امروز هنوز تراکنشی ثبت نشده.",
                            fontFamily = Modam,
                            fontSize = 13.sp,
                            color = t.muted,
                            modifier = Modifier.padding(horizontal = 2.dp, vertical = 10.dp),
                        )
                    }
                } else {
                    items(rows, key = { "t_" + it.txn.ref }) { entry ->
                        TodayRow(entry, privacyMode) { onOpenTransaction(entry.txn.ref) }
                    }
                }
            }
        }

        // Once the page scrolls, the status bar gets a strip of ground to sit on.
        val scrolled by remember {
            derivedStateOf { listState.firstVisibleItemIndex > 0 || listState.firstVisibleItemScrollOffset > 0 }
        }
        Box(
            Modifier
                .fillMaxWidth()
                .windowInsetsTopHeight(WindowInsets.statusBars)
                .alpha(if (scrolled) 1f else 0f)
                .background(t.ground),
        )
    }

    if (budgetDialog) {
        BudgetDialog(
            current = state.monthlyBudgetToman,
            onDismiss = { budgetDialog = false },
            onSave = { onSetBudget(it); budgetDialog = false },
        )
    }
    if (notifications) {
        ModalBottomSheet(onDismissRequest = { notifications = false }, containerColor = t.card) {
            NotificationsSheet(
                alerts = alerts,
                onOpen = { alert ->
                    notifications = false
                    when (alert.target) {
                        HomeAlert.Target.LEDGER -> onLedger()
                        HomeAlert.Target.ACCOUNTS -> onManageAccounts()
                        HomeAlert.Target.NONE -> Unit
                    }
                },
            )
        }
    }
}

// ── Identity bar ──────────────────────────────────────────────────────────────────────────────

@Composable
private fun IdentityBar(
    name: String,
    date: String,
    hasAlerts: Boolean,
    onSettings: () -> Unit,
    onTogglePrivacy: () -> Unit,
    privacyMode: Boolean,
    onSearch: () -> Unit,
    onNotifications: () -> Unit,
) {
    val t = CanvasTheme.tokens
    val context = LocalContext.current
    Row(
        Modifier
            .fillMaxWidth()
            .statusBarsPadding()
            .padding(top = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        val initial = name.trim().firstOrNull()?.toString() ?: "ا"
        Box(
            Modifier
                .size(40.dp)
                .clip(CircleShape)
                .background(t.inverse)
                .clickable(role = Role.Button, onClick = onSettings)
                .semantics { contentDescription = "پروفایل و تنظیمات" },
            contentAlignment = Alignment.Center,
        ) {
            Text(initial, fontFamily = Modam, fontSize = 15.sp, fontWeight = FontWeight.ExtraBold, color = t.onInverse)
        }
        Column(
            Modifier
                .weight(1f)
                // Double-tap the name: privacy mode, as on the old hero.
                .pointerInput(privacyMode) {
                    detectTapGestures(onDoubleTap = {
                        onTogglePrivacy()
                        val msg = if (!privacyMode) "حالت حریم خصوصی فعال شد" else "حالت حریم خصوصی غیرفعال شد"
                        Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
                    })
                },
        ) {
            Text(
                name.trim().ifBlank { "سلام" },
                fontFamily = Modam,
                fontSize = 15.sp,
                lineHeight = 20.sp,
                fontWeight = FontWeight.Bold,
                color = t.ink,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(date, fontFamily = Modam, fontSize = 11.sp, lineHeight = 15.sp, color = t.muted, maxLines = 1)
        }
        OutlineIconButton(label = "جست‌وجو", onClick = onSearch, size = 40.dp) { tint ->
            CanvasGlyph(
                listOf("M4 11a7 7 0 1 0 14 0a7 7 0 1 0 -14 0", "M20 20l-3.5-3.5"),
                tint = tint, size = 18.dp, stroke = 1.8f,
            )
        }
        Box {
            OutlineIconButton(label = "اعلان‌ها", onClick = onNotifications, size = 40.dp) { tint ->
                CanvasGlyph(
                    listOf("M6 16V11a6 6 0 1 1 12 0v5l1.5 2h-15z", "M10 20.5a2 2 0 0 0 4 0"),
                    tint = tint, size = 18.dp, stroke = 1.8f,
                )
            }
            if (hasAlerts) {
                // The artboard's dot: 7px, 9px from the top and 10px from the left edge.
                Box(
                    Modifier
                        .align(Alignment.TopEnd)
                        .padding(top = 9.dp, end = 10.dp)
                        .size(7.dp)
                        .clip(CircleShape)
                        .background(t.accent),
                )
            }
        }
    }
}

// ── The answer ────────────────────────────────────────────────────────────────────────────────

@Composable
private fun Answer(budget: MonthlyBudget, liquidToman: Double, privacyMode: Boolean) {
    val t = CanvasTheme.tokens
    val (label, toman) = when (budget) {
        is MonthlyBudget.Active -> "سهم امروزت از بودجه" to tomanOf(budget.dailyShareRial).toLong()
        is MonthlyBudget.Unset ->
            "خرج امن امروز، از موجودی حساب‌ها" to safeSpendFromCash(liquidToman, budget.cycle.daysLeft)
    }
    val figure = when {
        privacyMode -> MASK
        toman == null -> "—"
        else -> faNumber(toman)
    }
    // 74sp fits seven glyphs across the phone; longer figures step down rather than wrap.
    val size = when {
        figure.length <= 7 -> 74
        figure.length <= 9 -> 62
        else -> 50
    }
    Column(
        Modifier.fillMaxWidth().padding(top = 34.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Text(label, fontFamily = Modam, fontSize = 13.sp, color = t.inkSecondary)
        Text(
            figure,
            fontFamily = ModamFigures,
            fontSize = size.sp,
            lineHeight = size.sp,
            fontWeight = FontWeight.ExtraBold,
            letterSpacing = (-2).sp,
            color = t.ink,
            maxLines = 1,
            // line-height: 1 — the box is the figure, not the font's ascent and descent.
            style = androidx.compose.ui.text.TextStyle(
                platformStyle = androidx.compose.ui.text.PlatformTextStyle(includeFontPadding = false),
                lineHeightStyle = androidx.compose.ui.text.style.LineHeightStyle(
                    alignment = androidx.compose.ui.text.style.LineHeightStyle.Alignment.Center,
                    trim = androidx.compose.ui.text.style.LineHeightStyle.Trim.Both,
                ),
            ),
        )
        Text("تومان", fontFamily = Modam, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = t.muted)
    }
}

// ── The month at a glance ─────────────────────────────────────────────────────────────────────

@Composable
private fun MonthGlance(budget: MonthlyBudget, privacyMode: Boolean, onSetBudget: () -> Unit) {
    val t = CanvasTheme.tokens
    val cycle = budget.cycle
    Column(
        Modifier.fillMaxWidth().padding(top = 26.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Row(
            Modifier
                .fillMaxWidth()
                // Tapping the month line changes (or removes) the budget.
                .clickable(role = Role.Button, onClickLabel = "بودجهٔ ماهانه", onClick = onSetBudget),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.Bottom,
        ) {
            when (budget) {
                is MonthlyBudget.Active -> {
                    val unit = budgetUnit(tomanOf(budget.budgetRial))
                    val spent = if (privacyMode) MASK else unit.figure(tomanOf(budget.spentRial))
                    Text(
                        buildAnnotatedString {
                            append(spent)
                            append(" ")
                            withStyle(SpanStyle(fontSize = 12.sp, fontWeight = FontWeight.Medium, color = t.muted)) {
                                append("از بودجهٔ ${unit.figure(tomanOf(budget.budgetRial))} ${unit.word}ی ${cycle.monthNameFa}")
                            }
                        },
                        fontFamily = Modam,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold,
                        color = t.ink,
                        maxLines = 1,
                    )
                    val left = budget.remainingRial
                    Text(
                        when {
                            privacyMode -> "$MASK مونده"
                            left >= 0 -> "${unit.figure(tomanOf(left))} ${unit.word} مونده"
                            else -> "${unit.figure(tomanOf(-left))} ${unit.word} بیشتر از بودجه"
                        },
                        fontFamily = Modam,
                        fontSize = 12.sp,
                        color = if (left >= 0) t.muted else t.loss,
                        maxLines = 1,
                    )
                }
                is MonthlyBudget.Unset -> {
                    val spentToman = tomanOf(cycle.spentRial)
                    Text(
                        buildAnnotatedString {
                            append(if (privacyMode) MASK else faCompact(spentToman, 2))
                            append(" ")
                            withStyle(SpanStyle(fontSize = 12.sp, fontWeight = FontWeight.Medium, color = t.muted)) {
                                append("خرج ${cycle.monthNameFa} تا امروز")
                            }
                        },
                        fontFamily = Modam,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold,
                        color = t.ink,
                        maxLines = 1,
                    )
                    Text(
                        "بودجهٔ ماهانه بذار",
                        fontFamily = Modam,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        color = t.accentText,
                        modifier = Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .clickable(role = Role.Button, onClick = onSetBudget),
                    )
                }
            }
        }

        if (budget is MonthlyBudget.Active) {
            BudgetBar(
                spent = budget.spentFraction,
                plan = budget.planFraction,
                over = budget.remainingRial < 0,
                description = "${faNumber(budget.spentPercent)}٪ بودجهٔ ${cycle.monthNameFa} خرج شده، " +
                    "در حالی که برنامهٔ امروز ${faNumber((budget.planFraction * 100).toInt())}٪ است",
            )
        }

        DayStrip(cycle)

        val sentence = when (budget) {
            is MonthlyBudget.Active -> budget.explanationFa
            is MonthlyBudget.Unset ->
                "روز ${faNumber(cycle.dayOfCycle)} از ${faNumber(cycle.totalDays)} ${cycle.monthNameFa}. " +
                    "بودجه‌ای نذاشتی، پس این عدد موجودی حساب‌هاته تقسیم بر ${faNumber(cycle.daysLeft)} روزِ مونده."
        }
        Text(sentence, fontFamily = Modam, fontSize = 12.sp, lineHeight = 18.sp, color = t.inkSecondary)
    }
}

@Composable
private fun BudgetBar(spent: Float, plan: Float, over: Boolean, description: String) {
    val t = CanvasTheme.tokens
    BoxWithConstraints(
        Modifier
            .fillMaxWidth()
            .height(12.dp)
            .semantics { contentDescription = description },
    ) {
        Box(Modifier.fillMaxSize().clip(CircleShape).background(t.track))
        Box(
            Modifier
                .fillMaxWidth(spent.coerceIn(0f, 1f))
                .height(12.dp)
                .clip(CircleShape)
                .background(if (over) t.loss else t.accent),
        )
        // The plan-to-date tick: 2×22, overhanging the 12dp bar by 5 each side.
        Box(
            Modifier
                .align(Alignment.CenterStart)
                .offset(x = (maxWidth * plan.coerceIn(0f, 1f)) - 1.dp)
                .requiredHeight(22.dp)
                .width(2.dp)
                .clip(RoundedCornerShape(2.dp))
                .background(t.ink),
        )
    }
}

@Composable
private fun DayStrip(cycle: BudgetCycle) {
    val t = CanvasTheme.tokens
    // Past days: the artboards' #5A5A60 / #A3A3AA; derived from muted under the other themes.
    val past = when (t) {
        CanvasDark.tokens -> Color(0xFF5A5A60)
        CanvasLight.tokens -> Color(0xFFA3A3AA)
        else -> t.muted.copy(alpha = 0.6f)
    }
    Row(
        Modifier
            .fillMaxWidth()
            .semantics {
                contentDescription = "روز ${faNumber(cycle.dayOfCycle)} از ${faNumber(cycle.totalDays)} ${cycle.monthNameFa}"
            },
        horizontalArrangement = Arrangement.spacedBy(3.dp),
    ) {
        for (d in 1..cycle.totalDays) {
            val c = when {
                d < cycle.dayOfCycle -> past
                d == cycle.dayOfCycle -> t.accent
                else -> t.track
            }
            Box(Modifier.weight(1f).height(4.dp).clip(CircleShape).background(c))
        }
    }
}

// ── Round actions ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun Actions(
    onQuickAdd: () -> Unit,
    onAssets: () -> Unit,
    onReport: () -> Unit,
    accounts: List<BankAccount>,
    disabled: Set<String>,
    onManageAccounts: () -> Unit,
) {
    val context = LocalContext.current
    Row(
        Modifier.fillMaxWidth().padding(top = 22.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        RoundAction("ثبت خرج", onQuickAdd, Modifier.weight(1f), primary = true) { tint ->
            CanvasGlyph(listOf("M12 5v14M5 12h14"), tint, size = 24.dp, stroke = 2.2f)
        }
        RoundAction("دارایی", onAssets, Modifier.weight(1f)) { tint ->
            CanvasGlyph(
                listOf("M10 9a5 5 0 1 0 10 0a5 5 0 1 0 -10 0", "M4 15a5 5 0 1 0 10 0a5 5 0 1 0 -10 0"),
                tint, size = 22.dp, stroke = 1.8f,
            )
        }
        RoundAction("گزارش", onReport, Modifier.weight(1f)) { tint ->
            CanvasGlyph(listOf("M6 19V11M12 19V5M18 19v-6"), tint, size = 22.dp, stroke = 1.9f)
        }
        RoundAction(
            "کپی شبا",
            onClick = {
                val acc = mainShebaAccount(accounts, disabled)
                if (acc == null) {
                    Toast.makeText(context, "هنوز شبای هیچ حسابی ثبت نشده", Toast.LENGTH_SHORT).show()
                    onManageAccounts()
                } else {
                    copySheba(context, acc)
                }
            },
            modifier = Modifier.weight(1f),
        ) { tint ->
            CanvasGlyph(
                listOf(
                    "M10.5 8h7a2.5 2.5 0 0 1 2.5 2.5v7a2.5 2.5 0 0 1-2.5 2.5h-7a2.5 2.5 0 0 1-2.5-2.5v-7a2.5 2.5 0 0 1 2.5-2.5z",
                    "M16 8V6a2 2 0 0 0-2-2H6a2 2 0 0 0-2 2v8a2 2 0 0 0 2 2h2",
                ),
                tint, size = 22.dp, stroke = 1.8f,
            )
        }
    }
}

private fun copySheba(context: Context, acc: BankAccount) {
    val clip = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager ?: return
    clip.setPrimaryClip(ClipData.newPlainText("sheba", ShetabKit.cleanSheba(acc.sheba)))
    val name = acc.nickname.ifBlank { acc.bankFa }
    Toast.makeText(context, "شبای $name کپی شد", Toast.LENGTH_SHORT).show()
}

// ── Net worth ─────────────────────────────────────────────────────────────────────────────────

@Composable
private fun NetWorthCard(glance: NetWorthGlance, privacyMode: Boolean, onOpen: () -> Unit) {
    val t = CanvasTheme.tokens
    val rising = (glance.changeToman ?: 0.0) >= 0.0
    // The light artboard draws its curve in #12A15F, a step brighter than the gain text.
    val line = when {
        !rising -> t.loss
        t == CanvasLight.tokens -> Color(0xFF12A15F)
        else -> t.gain
    }
    CanvasCard(
        Modifier.fillMaxWidth().padding(top = 22.dp),
        onClick = onOpen,
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 12.dp),
    ) {
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text("دارایی خالص", fontFamily = Modam, fontSize = 12.sp, color = t.muted)
            val pct = glance.changePercent
            if (pct != null && !privacyMode) {
                val sign = if (pct >= 0) "+" else "−"
                TonePill(
                    text = "${ltrFigure(sign + faDecimal(abs(pct), 1) + "٪")} از اول ماه",
                    tone = if (pct >= 0) t.gain else t.loss,
                    well = if (pct >= 0) t.gainWell else t.loss.copy(alpha = 0.12f),
                )
            }
        }
        Spacer(Modifier.height(4.dp))
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.Bottom,
        ) {
            val parts = faCompact(glance.toman, 1).split(' ', limit = 2)
            Text(
                buildAnnotatedString {
                    append(if (privacyMode) MASK else parts[0])
                    parts.getOrNull(1)?.let {
                        append(" ")
                        withStyle(SpanStyle(fontFamily = Modam, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = t.muted)) {
                            append(it)
                        }
                    } ?: withStyle(SpanStyle(fontFamily = Modam, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = t.muted)) {
                        append(" تومان")
                    }
                },
                fontFamily = ModamFigures,
                fontSize = 30.sp,
                lineHeight = 34.sp,
                fontWeight = FontWeight.ExtraBold,
                color = t.ink,
                maxLines = 1,
            )
            if (glance.spark.size >= 2 && !privacyMode) {
                Sparkline(glance.spark, line, fillAlpha = if (t.isDark) 0.35f else 0.28f)
            }
        }
    }
}

/**
 * 150×44, oldest on the left and today on the right with a dot, as the SVG draws it (SVG
 * coordinates are physical, so the curve does not mirror under RTL). Smoothed with horizontal
 * control points so it never overshoots a real value's height by much.
 */
@Composable
private fun Sparkline(values: List<Double>, color: Color, fillAlpha: Float) {
    Canvas(Modifier.width(150.dp).height(44.dp)) {
        val lo = values.min()
        val hi = values.max()
        val span = (hi - lo).takeIf { it > 0.0 } ?: 1.0
        val top = 6.dp.toPx()
        val bottom = size.height - 10.dp.toPx()
        val right = size.width - 3.dp.toPx()
        val pts = values.mapIndexed { i, v ->
            val x = right * i / (values.size - 1)
            val y = if (hi == lo) (top + bottom) / 2 else bottom - ((v - lo) / span).toFloat() * (bottom - top)
            Offset(x, y)
        }
        val curve = Path().apply {
            moveTo(pts[0].x, pts[0].y)
            for (i in 1 until pts.size) {
                val a = pts[i - 1]
                val b = pts[i]
                val mx = (a.x + b.x) / 2
                cubicTo(mx, a.y, mx, b.y, b.x, b.y)
            }
        }
        val fill = Path().apply {
            addPath(curve)
            lineTo(pts.last().x, size.height)
            lineTo(pts[0].x, size.height)
            close()
        }
        drawPath(fill, Brush.verticalGradient(listOf(color.copy(alpha = fillAlpha), color.copy(alpha = 0f))))
        drawPath(curve, color, style = Stroke(width = 2.dp.toPx(), cap = StrokeCap.Round))
        drawCircle(color, radius = 3.dp.toPx(), center = pts.last())
    }
}

// ── Today's transactions ──────────────────────────────────────────────────────────────────────

@Composable
private fun TodayRow(entry: LedgerEntry, privacyMode: Boolean, onClick: () -> Unit) {
    val t = CanvasTheme.tokens
    val txn = entry.txn
    val income = txn.direction == "in"
    val merchant = txn.merchant.ifBlank { txn.merchantNorm }
        .ifBlank { entry.categoryFa }
        .ifBlank { if (income) "واریز" else "برداشت" }
    val toman = tomanOf(txn.amountRial ?: 0L)
    val amount = when {
        privacyMode -> MASK
        txn.direction == null -> faNumber(toman)
        else -> ltrFigure((if (income) "+" else "−") + faNumber(toman))
    }
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .clickable(role = Role.Button, onClick = onClick)
            .padding(vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Box(
            Modifier.size(44.dp).clip(CircleShape).background(if (income) t.gainWell else t.accentWell),
            contentAlignment = Alignment.Center,
        ) {
            CategoryIcon(entry.categoryFa, tint = if (income) t.gain else t.accentText, size = 20.dp, stroke = 1.8.dp)
        }
        Column(Modifier.weight(1f)) {
            Text(
                merchant,
                fontFamily = Modam,
                fontSize = 15.sp,
                lineHeight = 21.sp,
                fontWeight = FontWeight.SemiBold,
                color = t.ink,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                "${bankNameOf(txn.bank)} · ${faClock(txn.at)}",
                fontFamily = Modam,
                fontSize = 12.sp,
                lineHeight = 17.sp,
                color = t.muted,
                maxLines = 1,
            )
        }
        Text(
            amount,
            fontFamily = ModamFigures,
            fontSize = 15.sp,
            fontWeight = FontWeight.Bold,
            color = if (income) t.gain else t.ink,
            maxLines = 1,
        )
    }
}

// ── Notifications ─────────────────────────────────────────────────────────────────────────────

/** One thing asking for her, behind the bell. */
data class HomeAlert(val text: String, val target: Target) {
    enum class Target { LEDGER, ACCOUNTS, NONE }
}

/** What the bell's dot stands for: only things already in her data, never a promo. */
fun homeAlerts(state: UiState): List<HomeAlert> = buildList {
    state.story.attention?.let { add(HomeAlert(it.text, HomeAlert.Target.LEDGER)) }
    val now = System.currentTimeMillis()
    state.commitments
        .filter { !it.isSettled && it.nextDueTimestamp > 0L && it.nextDueTimestamp - now <= 3 * DAY_MS }
        .sortedBy { it.nextDueTimestamp }
        .forEach { c ->
            val days = c.nextDueDays
            val whenFa = if (days == 0) "امروز" else "${faNumber(days)} روز دیگه"
            add(HomeAlert("قسط «${c.title.ifBlank { c.provider }}» $whenFa سررسید می‌شه.", HomeAlert.Target.NONE))
        }
    val review = state.ledger.entries.count { it.needsReview && !it.hidden }
    // The story's attention line is usually this same count in other words; say it once.
    if (review > 0 && state.story.attention == null) add(HomeAlert("${faNumber(review)} تراکنش منتظر دسته‌بندیه.", HomeAlert.Target.LEDGER))
    if (state.strangeSenders.isNotEmpty()) {
        add(HomeAlert("پیامک از ${faNumber(state.strangeSenders.size)} فرستندهٔ ناشناس رسیده.", HomeAlert.Target.ACCOUNTS))
    }
}

@Composable
private fun NotificationsSheet(alerts: List<HomeAlert>, onOpen: (HomeAlert) -> Unit) {
    val t = CanvasTheme.tokens
    val context = LocalContext.current
    Column(
        Modifier.fillMaxWidth().padding(horizontal = CanvasTheme.gutter).padding(bottom = 28.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Text(
            "اعلان‌ها",
            fontFamily = Modam,
            fontSize = 17.sp,
            fontWeight = FontWeight.ExtraBold,
            color = t.ink,
            modifier = Modifier.padding(bottom = 8.dp),
        )
        if (alerts.isEmpty()) {
            Text("فعلاً چیزی منتظرت نیست.", fontFamily = Modam, fontSize = 14.sp, color = t.muted, modifier = Modifier.padding(vertical = 10.dp))
        }
        alerts.forEach { a ->
            Row(
                Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(14.dp))
                    .then(if (a.target != HomeAlert.Target.NONE) Modifier.clickable { onOpen(a) } else Modifier)
                    .padding(vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Box(Modifier.size(7.dp).clip(CircleShape).background(t.accent))
                Text(a.text, fontFamily = Modam, fontSize = 14.sp, lineHeight = 21.sp, color = t.ink, modifier = Modifier.weight(1f))
            }
        }
        Text(
            "تنظیم یادآورها",
            fontFamily = Modam,
            fontSize = 13.sp,
            fontWeight = FontWeight.Bold,
            color = t.accentText,
            modifier = Modifier
                .padding(top = 8.dp)
                .clip(RoundedCornerShape(8.dp))
                .clickable(role = Role.Button) {
                    context.startActivity(Intent(context, ReminderSettingsActivity::class.java))
                }
                .padding(vertical = 6.dp),
        )
    }
}

// ── Budget ────────────────────────────────────────────────────────────────────────────────────

@Composable
private fun BudgetDialog(current: Long, onDismiss: () -> Unit, onSave: (Long) -> Unit) {
    var text by remember { mutableStateOf(if (current > 0) current.toString() else "") }
    val parsed = parseAmount(text)?.toLong()?.takeIf { it > 0 }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("بودجهٔ ماهانه", fontFamily = Modam, fontWeight = FontWeight.Bold) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    "چقدر می‌خوای این ماه خرج کنی؟ سهم هر روز از همین حساب می‌شه.",
                    fontFamily = Modam,
                    fontSize = 13.sp,
                )
                OutlinedTextField(
                    value = text,
                    onValueChange = { text = it },
                    singleLine = true,
                    suffix = { Text("تومان", fontFamily = Modam) },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    supportingText = parsed?.let { { Text(faCompact(it.toDouble(), 2) + " تومان", fontFamily = Modam) } },
                    textStyle = androidx.compose.ui.text.TextStyle(fontFamily = ModamFigures, textAlign = TextAlign.Start),
                )
            }
        },
        confirmButton = {
            TextButton(onClick = { parsed?.let(onSave) }, enabled = parsed != null) {
                Text("ذخیره", fontFamily = Modam, fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            if (current > 0) {
                // Back to no budget: the answer falls back to safe-spend from cash.
                TextButton(onClick = { onSave(0L) }) { Text("حذف بودجه", fontFamily = Modam) }
            } else {
                TextButton(onClick = onDismiss) { Text("بی‌خیال", fontFamily = Modam) }
            }
        },
    )
}
