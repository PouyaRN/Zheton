package com.doxigo.muchtoman

import android.widget.Toast
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
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
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.DateRange
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.Notifications
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.Star
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.ceil
import kotlin.math.roundToLong

enum class LitheTab(val id: String, val labelFa: String, val headerTitleFa: String) {
    DASHBOARD("dashboard", "خانه", "داشبورد"),
    ANALYTICS("analytics", "تحلیل", "تحلیل مخارج"),
    COMMITMENTS("commitments", "اقساط", "اقساط و چک"),
    GOALS("goals", "اهداف", "پس‌انداز و اهداف"),
}

enum class QuickAddSheetMode(val id: String, val labelFa: String) {
    TXN("txn", "تراکنش"),
    SMS("sms", "اسکن پیامک"),
    GOAL("goal", "هدف پس‌انداز"),
    COMMITMENT("commitment", "قسط و چک"),
    HOLDING("holding", "دارایی"),
    CARD("card", "کارت بانکی"),
}

data class LitheGoalItem(
    val id: String,
    val title: String,
    val subtitle: String,
    val category: String, // "short" | "long" | "emergency"
    val currentToman: Long,
    val targetToman: Long,
    val badgeFa: String,
    val accentHex: Long,
)

data class LitheMarketRow(
    val id: String,
    val nameFa: String,
    val subtitleFa: String,
    val category: String, // "gold" | "coin" | "fx" | "crypto" | "fund"
    val priceToman: Long,
    val changePct: Double,
    val badgeColorHex: Long,
)

private val JALALI_DAYS = listOf(
    "امروز، ۲۵ اردیبهشت",
    "دیروز، ۲۴ اردیبهشت",
    "۲۳ اردیبهشت ۱۴۰۴",
    "۲۲ اردیبهشت ۱۴۰۴",
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LitheShell(
    state: UiState,
    vm: AppVm,
    privacyMode: Boolean,
    onTogglePrivacy: () -> Unit,
    onOpenSettings: () -> Unit,
    onOpenNativeLedger: () -> Unit,
    onOpenNativeAssets: () -> Unit,
    onOpenNativeCards: () -> Unit,
    onOpenBankSheet: () -> Unit,
) {
    val t = CanvasTheme.tokens
    val context = LocalContext.current
    val clipboard = LocalClipboardManager.current

    var activeTab by rememberSaveable { mutableStateOf(LitheTab.DASHBOARD) }
    var dayOffset by rememberSaveable { mutableIntStateOf(0) }

    // Interactive Modals
    var showCalculators by rememberSaveable { mutableStateOf(false) }
    var showMarketSheet by rememberSaveable { mutableStateOf(false) }
    var showTransferSheet by rememberSaveable { mutableStateOf(false) }
    var showChequeModal by rememberSaveable { mutableStateOf(false) }
    var showQuickAddSheet by rememberSaveable { mutableStateOf(false) }
    var quickAddMode by rememberSaveable { mutableStateOf(QuickAddSheetMode.TXN) }
    var quickAddInitialExpense by rememberSaveable { mutableStateOf(true) }

    // Tab 2 Analytics local state
    var analyticsPeriod by rememberSaveable { mutableStateOf("ماهانه") }
    var txnSearchQuery by rememberSaveable { mutableStateOf("") }
    var showTxnSearch by rememberSaveable { mutableStateOf(false) }
    var smartCapEnabled by rememberSaveable { mutableStateOf(false) }

    // Onboarding dialog on first launch
    if (!state.onboardingCompleted) {
        OnboardingDialog(
            onDismiss = vm::completeOnboarding,
        )
    }

    // Tab 3 Commitments local state
    var commitmentFilter by rememberSaveable { mutableStateOf("all") }
    var smsReminder3Days by rememberSaveable { mutableStateOf(true) }
    var voiceAlarmDueDay by rememberSaveable { mutableStateOf(false) }

    // Tab 4 Goals local state
    var goalFilter by rememberSaveable { mutableStateOf("all") }
    var roundUpEnabled by rememberSaveable { mutableStateOf(true) }
    var simTargetMillion by rememberSaveable { mutableFloatStateOf(50f) }
    var simMonthlyMillion by rememberSaveable { mutableFloatStateOf(4.5f) }

    val customGoals = remember {
        mutableStateListOf<LitheGoalItem>()
    }

    // Live market rates merged with Stitch defaults
    val marketItems = remember(state.effective) {
        val eff = state.effective
        listOf(
            LitheMarketRow("gold18", "طلا ۱۸ عیار", "هر گرم طلای ۱۸ عیار", "gold", (eff["gold18"] ?: 6_540_000.0).roundToLong(), 1.8, 0xFFF59E0B),
            LitheMarketRow("coin", "سکه امامی", "سکه تمام طرح جدید", "coin", (eff["coin"] ?: 74_800_000.0).roundToLong(), 2.4, 0xFFD97706),
            LitheMarketRow("coin_bahar", "سکه بهار آزادی", "طرح قدیم بانکی", "coin", (eff["coin_bahar"] ?: 71_200_000.0).roundToLong(), 1.9, 0xFFD97706),
            LitheMarketRow("coin_nim", "نیم‌سکه", "نیم‌سکه بهار آزادی", "coin", (eff["coin_nim"] ?: 42_600_000.0).roundToLong(), 1.5, 0xFFF59E0B),
            LitheMarketRow("coin_rob", "ربع‌سکه", "ربع‌سکه بانکی", "coin", (eff["coin_rob"] ?: 24_900_000.0).roundToLong(), -0.6, 0xFFF59E0B),
            LitheMarketRow("usdt", "تتر (USDT)", "دلار دیجیتال", "crypto", (eff["usdt"] ?: eff["usd"] ?: 89_450.0).roundToLong(), 0.9, 0xFF10B981),
            LitheMarketRow("usd", "دلار آزاد", "نرخ بازار فردوسی", "fx", (eff["usd"] ?: 89_100.0).roundToLong(), 0.7, 0xFF2563EB),
            LitheMarketRow("eur", "یورو", "نرخ بازار آزاد", "fx", (eff["eur"] ?: 96_800.0).roundToLong(), 0.4, 0xFF3B82F6),
            LitheMarketRow("btc", "بیت‌کوین (BTC)", "رمزارز جهانی به تومان", "crypto", (eff["btc"] ?: 7_850_000_000.0).roundToLong(), 3.2, 0xFFF97316),
            LitheMarketRow("ayar", "صندوق طلای عیار", "ETF پشتوانه طلا", "fund", 18_450L, 2.1, 0xFFEAB308),
            LitheMarketRow("afra", "صندوق درآمد ثابت افرا", "سود روزشمار ۳۰٪", "fund", 12_920L, 0.1, 0xFF10B981),
        )
    }

    val totalAssetToman = remember(state.totals.toman, customGoals) {
        val realTotal = state.totals.toman.roundToLong()
        if (realTotal > 0L) realTotal else 245_800_000L
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(t.ground),
    ) {
        // Main Scrollable Canvas
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding(),
            contentPadding = PaddingValues(
                start = 20.dp,
                end = 20.dp,
                top = 12.dp,
                bottom = 132.dp,
            ),
            verticalArrangement = Arrangement.spacedBy(18.dp),
        ) {
            // 1. Floating Pill Header
            item(key = "lithe_header") {
                LitheTopHeader(
                    activeTab = activeTab,
                    dateLabel = JALALI_DAYS[dayOffset.coerceIn(0, JALALI_DAYS.lastIndex)],
                    onPrevDay = { dayOffset = (dayOffset + 1).coerceAtMost(JALALI_DAYS.lastIndex) },
                    onNextDay = { dayOffset = (dayOffset - 1).coerceAtLeast(0) },
                    onOpenCalculators = { showCalculators = true },
                    onOpenSettings = onOpenSettings,
                )
            }

            // 2. Active Screen Body
            when (activeTab) {
                LitheTab.DASHBOARD -> {
                    item(key = "dash_hero") {
                        DashboardHeroBalanceCard(
                            totalToman = totalAssetToman,
                            privacyMode = privacyMode,
                            onTogglePrivacy = onTogglePrivacy,
                            onDeposit = {
                                quickAddMode = QuickAddSheetMode.TXN
                                quickAddInitialExpense = false
                                showQuickAddSheet = true
                            },
                            onExpense = {
                                quickAddMode = QuickAddSheetMode.TXN
                                quickAddInitialExpense = true
                                showQuickAddSheet = true
                            },
                            onTransfer = { showTransferSheet = true },
                            onScanSms = {
                                quickAddMode = QuickAddSheetMode.SMS
                                showQuickAddSheet = true
                            },
                        )
                    }

                    item(key = "dash_split_bento") {
                        val activeBudget = state.budget as? MonthlyBudget.Active
                        val firstActive = state.commitments.firstOrNull { !it.isSettled }
                        val subText = if (firstActive != null) "${firstActive.title} • ${faNumber(firstActive.remainingAmountToman)} ت" else "بدون قسط معوق"
                        DashboardSplitBentoRow(
                            budgetPercent = activeBudget?.spentPercent?.coerceIn(0, 100) ?: 0,
                            remainingBudgetToman = activeBudget?.let { (it.remainingRial / 10L).coerceAtLeast(0L) } ?: 0L,
                            activeCommitmentsCount = state.commitments.count { !it.isSettled },
                            commitmentSubtitle = subText,
                            onOpenBudget = { activeTab = LitheTab.ANALYTICS },
                            onOpenCommitments = { activeTab = LitheTab.COMMITMENTS },
                        )
                    }

                    item(key = "dash_insight") {
                        DashboardSmartInsightBanner(
                            onAction = {
                                activeTab = LitheTab.GOALS
                                Toast.makeText(context, "انتقال هوشمند به بخش پس‌انداز و اهداف باز شد", Toast.LENGTH_SHORT).show()
                            },
                        )
                    }

                    item(key = "dash_market_ribbon") {
                        DashboardMarketRibbon(
                            items = marketItems.take(5),
                            onShowAllMarket = { showMarketSheet = true },
                            onOpenNativeMarket = onOpenNativeAssets,
                        )
                    }

                    item(key = "dash_recent_txns") {
                        DashboardRecentTransactionsCard(
                            entries = state.ledger.entries.take(5),
                            privacyMode = privacyMode,
                            onSeeAll = { activeTab = LitheTab.ANALYTICS },
                            onOpenFullLedger = onOpenNativeLedger,
                        )
                    }
                }

                LitheTab.ANALYTICS -> {
                    item(key = "ana_period") {
                        AnalyticsPeriodSelector(
                            selected = analyticsPeriod,
                            onSelect = { analyticsPeriod = it },
                        )
                    }

                    item(key = "ana_hero_flow") {
                        AnalyticsCashflowHeroCard(
                            period = analyticsPeriod,
                            privacyMode = privacyMode,
                        )
                    }

                    item(key = "ana_insight") {
                        AnalyticsBehavioralInsightCard(
                            smartCapEnabled = smartCapEnabled,
                            onToggleCap = {
                                smartCapEnabled = !smartCapEnabled
                                if (smartCapEnabled) {
                                    vm.setMonthlyBudgetToman(28_000_000L)
                                    Toast.makeText(context, "سقف بودجه هوشمند ۲۸ میلیون تومان فعال شد", Toast.LENGTH_SHORT).show()
                                }
                            },
                        )
                    }

                    item(key = "ana_donut") {
                        AnalyticsCategoryDonutCard(privacyMode = privacyMode)
                    }

                    item(key = "ana_sms_banner") {
                        AnalyticsSmsSyncBanner(
                            onOpenSmsScanner = {
                                quickAddMode = QuickAddSheetMode.SMS
                                showQuickAddSheet = true
                            },
                        )
                    }

                    item(key = "ana_txns_list") {
                        AnalyticsMajorTransactionsCard(
                            entries = state.ledger.entries,
                            searchQuery = txnSearchQuery,
                            showSearch = showTxnSearch,
                            onToggleSearch = { showTxnSearch = !showTxnSearch },
                            onSearchChange = { txnSearchQuery = it },
                            privacyMode = privacyMode,
                            onOpenFullLedger = onOpenNativeLedger,
                        )
                    }
                }

                LitheTab.COMMITMENTS -> {
                    val urgentCommitment = remember(state.commitments) {
                        val now = System.currentTimeMillis()
                        val sevenDays = 7L * 24L * 3600L * 1000L
                        state.commitments.filter { !it.isSettled && it.nextDueTimestamp > 0 && it.nextDueTimestamp - now in 0..sevenDays }
                            .minByOrNull { it.nextDueTimestamp }
                    }

                    urgentCommitment?.let { comm ->
                        item(key = "comm_urgent_banner") {
                            CommitmentsUrgentBanner(
                                commitment = comm,
                                onPayNow = {
                                    vm.addManualTransaction(
                                        amountRial = comm.remainingAmountToman * 10L,
                                        isExpense = true,
                                        categoryId = null,
                                        merchant = comm.title,
                                        note = "پرداخت قسط",
                                    )
                                    vm.toggleCommitmentSettled(comm.id)
                                    Toast.makeText(context, "پرداخت ${comm.title} ثبت شد", Toast.LENGTH_SHORT).show()
                                },
                            )
                        }
                    }

                    item(key = "comm_hero") {
                        CommitmentsHeroSummaryCard(
                            userCommitments = state.commitments,
                            privacyMode = privacyMode,
                        )
                    }

                    item(key = "comm_filters") {
                        CommitmentsFilterBar(
                            selected = commitmentFilter,
                            onSelect = { commitmentFilter = it },
                        )
                    }

                    item(key = "comm_list") {
                        CommitmentsStreamList(
                            filter = commitmentFilter,
                            userCommitments = state.commitments,
                            bankAccounts = state.bankAccounts,
                            onOpenChequeModal = { showChequeModal = true },
                            onToggleUserCommitment = { vm.toggleCommitmentSettled(it) },
                            onCopyText = { label, text ->
                                clipboard.setText(AnnotatedString(text))
                                Toast.makeText(context, "$label کپی شد", Toast.LENGTH_SHORT).show()
                            },
                            onOpenNativeCards = onOpenNativeCards,
                            onOpenBankSheet = onOpenBankSheet,
                            onAddNewCommitment = {
                                quickAddMode = QuickAddSheetMode.COMMITMENT
                                showQuickAddSheet = true
                            },
                        )
                    }

                    item(key = "comm_reminders") {
                        CommitmentsReminderSettingsPod(
                            smsReminder = smsReminder3Days,
                            onSmsReminderChange = { smsReminder3Days = it },
                            voiceAlarm = voiceAlarmDueDay,
                            onVoiceAlarmChange = { voiceAlarmDueDay = it },
                            onAddCommitment = {
                                quickAddMode = QuickAddSheetMode.COMMITMENT
                                showQuickAddSheet = true
                            },
                        )
                    }
                }

                LitheTab.GOALS -> {
                    item(key = "goals_hero") {
                        GoalsHeroAllocationCard(
                            customGoals = customGoals,
                            holdingsTotalToman = state.totals.toman.roundToLong(),
                            privacyMode = privacyMode,
                        )
                    }

                    item(key = "goals_roundup") {
                        GoalsMicroSavingsPod(
                            enabled = roundUpEnabled,
                            onToggle = { roundUpEnabled = it },
                        )
                    }

                    item(key = "goals_filters") {
                        GoalsFilterBar(
                            selected = goalFilter,
                            onSelect = { goalFilter = it },
                        )
                    }

                    item(key = "goals_list") {
                        GoalsActiveListSection(
                            filter = goalFilter,
                            goals = customGoals,
                            holdings = state.listHoldings,
                            effectiveRates = state.effective,
                            onTopUpGoal = { goalId, addToman ->
                                val idx = customGoals.indexOfFirst { it.id == goalId }
                                if (idx >= 0) {
                                    val old = customGoals[idx]
                                    val nextAmt = (old.currentToman + addToman).coerceAtMost(old.targetToman)
                                    val pct = ((nextAmt.toDouble() / old.targetToman.toDouble()) * 100.0).roundToLong()
                                    customGoals[idx] = old.copy(
                                        currentToman = nextAmt,
                                        badgeFa = "${faDigits(pct.toString())}٪ تکمیل شده",
                                    )
                                    Toast.makeText(context, "مبلغ ${faNumber(addToman)} تومان به «${old.title}» افزوده شد", Toast.LENGTH_SHORT).show()
                                }
                            },
                            onOpenNativeMarket = onOpenNativeAssets,
                        )
                    }

                    item(key = "goals_simulator") {
                        GoalsSmartSimulatorPod(
                            targetMillion = simTargetMillion,
                            onTargetChange = { simTargetMillion = it },
                            monthlyMillion = simMonthlyMillion,
                            onMonthlyChange = { simMonthlyMillion = it },
                            onCreateGoalFromSim = {
                                quickAddMode = QuickAddSheetMode.GOAL
                                showQuickAddSheet = true
                            },
                        )
                    }
                }
            }
        }

        // 3. Floating Glassmorphic Capsule Bottom Dock
        Box(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(horizontal = 20.dp, vertical = 12.dp),
            contentAlignment = Alignment.Center,
        ) {
            LitheFloatingDock(
                activeTab = activeTab,
                onSelectTab = { activeTab = it },
                onQuickAdd = {
                    quickAddMode = when (activeTab) {
                        LitheTab.DASHBOARD, LitheTab.ANALYTICS -> QuickAddSheetMode.TXN
                        LitheTab.COMMITMENTS -> QuickAddSheetMode.COMMITMENT
                        LitheTab.GOALS -> QuickAddSheetMode.GOAL
                    }
                    quickAddInitialExpense = true
                    showQuickAddSheet = true
                },
            )
        }
    }

    // ── Modals & Bottom Sheets ───────────────────────────────────────────────────────────────────
    if (showCalculators) {
        CalculatorsBottomSheet(
            effectiveRates = state.effective,
            onDismiss = { showCalculators = false },
        )
    }

    if (showMarketSheet) {
        LitheLiveMarketBottomSheet(
            items = marketItems,
            onDismiss = { showMarketSheet = false },
            onAddHolding = {
                showMarketSheet = false
                quickAddMode = QuickAddSheetMode.HOLDING
                showQuickAddSheet = true
            },
            onOpenNativeAssets = {
                showMarketSheet = false
                onOpenNativeAssets()
            },
        )
    }

    if (showTransferSheet) {
        LitheTransferCardsSheet(
            bankAccounts = state.bankAccounts,
            onCopy = { label, value ->
                clipboard.setText(AnnotatedString(value))
                Toast.makeText(context, "$label کپی شد", Toast.LENGTH_SHORT).show()
            },
            onAddCard = {
                showTransferSheet = false
                quickAddMode = QuickAddSheetMode.CARD
                showQuickAddSheet = true
            },
            onDismiss = { showTransferSheet = false },
        )
    }

    if (showChequeModal) {
        LitheSayadChequeSheet(
            onCopySayadId = {
                clipboard.setText(AnnotatedString("2104998200148391"))
                Toast.makeText(context, "شناسه ۱۶ رقمی صیاد کپی شد", Toast.LENGTH_SHORT).show()
            },
            onDismiss = { showChequeModal = false },
        )
    }

    if (showQuickAddSheet) {
        LitheQuickAddModalSheet(
            initialMode = quickAddMode,
            initialIsExpense = quickAddInitialExpense,
            vm = vm,
            onAddGoalItem = { title, targetToman, currentToman, category ->
                val pct = if (targetToman > 0L) ((currentToman.toDouble() / targetToman.toDouble()) * 100.0).roundToLong() else 0L
                customGoals.add(
                    0,
                    LitheGoalItem(
                        id = "g_${System.currentTimeMillis()}",
                        title = title,
                        subtitle = "هدف شخصی ثبت‌شده",
                        category = category,
                        currentToman = currentToman,
                        targetToman = targetToman,
                        badgeFa = "${faDigits(pct.toString())}٪ تکمیل شده",
                        accentHex = 0xFF2563EB,
                    ),
                )
                vm.addGoal(title, targetToman * 10L)
            },
            onDismiss = { showQuickAddSheet = false },
        )
    }
}

// ═══════════════════════════════════════════════════════════════════════════════════════════════
// 1. FLOATING PILL HEADER (Stitch Lithe Vitality)
// ═══════════════════════════════════════════════════════════════════════════════════════════════

@Composable
private fun LitheTopHeader(
    activeTab: LitheTab,
    dateLabel: String,
    onPrevDay: () -> Unit,
    onNextDay: () -> Unit,
    onOpenCalculators: () -> Unit,
    onOpenSettings: () -> Unit,
) {
    val t = CanvasTheme.tokens
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // Right side (in RTL): Date selector capsule on Dashboard, Page Title pill on other tabs
        if (activeTab == LitheTab.DASHBOARD) {
            Surface(
                shape = RoundedCornerShape(999.dp),
                color = t.card,
                shadowElevation = 2.dp,
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 5.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    Box(
                        modifier = Modifier
                            .size(30.dp)
                            .clip(CircleShape)
                            .clickable(onClick = onPrevDay)
                            .testTag("header_prev_day"),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Rounded.KeyboardArrowRight,
                            contentDescription = "روز قبل",
                            tint = t.muted,
                            modifier = Modifier.size(18.dp),
                        )
                    }
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(5.dp),
                        modifier = Modifier.padding(horizontal = 4.dp),
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.DateRange,
                            contentDescription = null,
                            tint = Color(0xFF2563EB),
                            modifier = Modifier.size(16.dp),
                        )
                        Text(
                            text = dateLabel,
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Bold,
                            color = t.ink,
                        )
                    }
                    Box(
                        modifier = Modifier
                            .size(30.dp)
                            .clip(CircleShape)
                            .clickable(onClick = onNextDay)
                            .testTag("header_next_day"),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Rounded.KeyboardArrowLeft,
                            contentDescription = "روز بعد",
                            tint = t.muted,
                            modifier = Modifier.size(18.dp),
                        )
                    }
                }
            }
        } else {
            Surface(
                shape = RoundedCornerShape(999.dp),
                color = t.card,
                shadowElevation = 2.dp,
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Box(
                        modifier = Modifier
                            .size(8.dp)
                            .clip(CircleShape)
                            .background(Color(0xFF2563EB)),
                    )
                    Text(
                        text = activeTab.headerTitleFa,
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.ExtraBold,
                        color = t.ink,
                    )
                }
            }
        }

        // Left side (in RTL): Smart Assistant / 7 Calculators pill + Settings icon circle
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Surface(
                onClick = onOpenCalculators,
                shape = RoundedCornerShape(999.dp),
                color = t.card,
                shadowElevation = 2.dp,
                modifier = Modifier.testTag("open_calculators_pill"),
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 9.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Star,
                        contentDescription = null,
                        tint = Color(0xFF2563EB),
                        modifier = Modifier.size(16.dp),
                    )
                    Text(
                        text = if (activeTab == LitheTab.DASHBOARD) "دستیار هوشمند" else "ماشین‌حساب",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold,
                        color = t.ink,
                    )
                }
            }

            Surface(
                onClick = onOpenSettings,
                shape = CircleShape,
                color = t.card,
                shadowElevation = 2.dp,
                modifier = Modifier
                    .size(40.dp)
                    .testTag("open_settings_button"),
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = Icons.Rounded.Settings,
                        contentDescription = "تنظیمات",
                        tint = t.inkSecondary,
                        modifier = Modifier.size(20.dp),
                    )
                }
            }
        }
    }
}

// ═══════════════════════════════════════════════════════════════════════════════════════════════
// 2. TAB 1 — DASHBOARD (خانه / داشبورد)
// ═══════════════════════════════════════════════════════════════════════════════════════════════

@Composable
private fun DashboardHeroBalanceCard(
    totalToman: Long,
    privacyMode: Boolean,
    onTogglePrivacy: () -> Unit,
    onDeposit: () -> Unit,
    onExpense: () -> Unit,
    onTransfer: () -> Unit,
    onScanSms: () -> Unit,
) {
    val t = CanvasTheme.tokens
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(28.dp),
        color = t.card,
        shadowElevation = 3.dp,
    ) {
        Column(modifier = Modifier.padding(22.dp)) {
            // Top label + monthly badge
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    modifier = Modifier.clickable(onClick = onTogglePrivacy),
                ) {
                    Text(
                        text = "مجموع کل دارایی",
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = t.muted,
                    )
                    Icon(
                        imageVector = Icons.Rounded.Lock,
                        contentDescription = "حالت محرمانه",
                        tint = t.muted,
                        modifier = Modifier.size(14.dp),
                    )
                }

                Surface(
                    shape = RoundedCornerShape(999.dp),
                    color = Color(0xFF10B981).copy(alpha = 0.12f),
                ) {
                    Text(
                        text = "↗ +۸.۴٪ این ماه",
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFF059669),
                    )
                }
            }

            Spacer(Modifier.height(10.dp))

            // Main Hero Figure
            Row(
                verticalAlignment = Alignment.Bottom,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text(
                    text = if (privacyMode) "•••••••••" else faNumber(totalToman),
                    style = figureStyle(t.ink, FontWeight.Black).copy(fontSize = 36.sp),
                    maxLines = 1,
                )
                Text(
                    text = "تومان",
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Bold,
                    color = t.muted,
                    modifier = Modifier.padding(bottom = 6.dp),
                )
            }

            Spacer(Modifier.height(10.dp))

            // Sub-row: Realized monthly profit
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(14.dp))
                    .background(t.track.copy(alpha = 0.55f))
                    .padding(horizontal = 12.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    Box(
                        modifier = Modifier
                            .size(8.dp)
                            .clip(CircleShape)
                            .background(Color(0xFF2563EB)),
                    )
                    Text(
                        text = "سود محقق شده ماهانه:",
                        style = MaterialTheme.typography.labelSmall,
                        color = t.muted,
                    )
                }
                Text(
                    text = if (privacyMode) "••••••" else "+۱۸٬۴۰۰٬۰۰۰ تومان",
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.ExtraBold,
                    color = t.ink,
                )
            }

            Spacer(Modifier.height(18.dp))

            // 4 Quick Action Buttons (Lithe Vitality grid)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                HeroActionButton(
                    label = "واریز",
                    symbol = "↙",
                    filled = true,
                    onClick = onDeposit,
                    modifier = Modifier
                        .weight(1f)
                        .testTag("hero_action_deposit"),
                )
                HeroActionButton(
                    label = "ثبت تراکنش",
                    symbol = "+",
                    filled = false,
                    onClick = onExpense,
                    modifier = Modifier
                        .weight(1f)
                        .testTag("hero_action_expense"),
                )
                HeroActionButton(
                    label = "انتقال",
                    symbol = "⇄",
                    filled = false,
                    onClick = onTransfer,
                    modifier = Modifier
                        .weight(1f)
                        .testTag("hero_action_transfer"),
                )
                HeroActionButton(
                    label = "اسکن قبض",
                    symbol = "⌗",
                    filled = false,
                    onClick = onScanSms,
                    modifier = Modifier
                        .weight(1f)
                        .testTag("hero_action_scan"),
                )
            }
        }
    }
}

@Composable
private fun HeroActionButton(
    label: String,
    symbol: String,
    filled: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val t = CanvasTheme.tokens
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(18.dp),
        color = if (filled) t.primaryAction else t.track.copy(alpha = 0.7f),
        modifier = modifier,
    ) {
        Column(
            modifier = Modifier.padding(vertical = 12.dp, horizontal = 4.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Text(
                text = symbol,
                fontSize = 18.sp,
                fontWeight = FontWeight.Black,
                color = if (filled) t.onPrimaryAction else t.ink,
            )
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.Bold,
                color = if (filled) t.onPrimaryAction else t.inkSecondary,
                maxLines = 1,
            )
        }
    }
}

@Composable
private fun DashboardSplitBentoRow(
    budgetPercent: Int,
    remainingBudgetToman: Long,
    activeCommitmentsCount: Int,
    commitmentSubtitle: String = "بدون قسط معوق",
    onOpenBudget: () -> Unit,
    onOpenCommitments: () -> Unit,
) {
    val t = CanvasTheme.tokens
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        // Card 1: Budget Status with Circular Ring
        Surface(
            onClick = onOpenBudget,
            shape = RoundedCornerShape(24.dp),
            color = t.card,
            shadowElevation = 2.dp,
            modifier = Modifier.weight(1f),
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.SpaceBetween,
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.Top,
                ) {
                    Text(
                        text = "وضعیت بودجه",
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        color = t.muted,
                    )
                    LitheCircularProgressRing(
                        percent = budgetPercent,
                        size = 44.dp,
                        color = Color(0xFF2563EB),
                        trackColor = t.track,
                    )
                }
                Spacer(Modifier.height(14.dp))
                Text(
                    text = "${faCompact(remainingBudgetToman)} تومان",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Black,
                    color = t.ink,
                )
                Text(
                    text = "باقی‌مانده از بودجه اردیبهشت",
                    style = MaterialTheme.typography.labelSmall,
                    color = t.muted,
                    fontSize = 10.sp,
                )
            }
        }

        // Card 2: Active Installments & Cheques
        Surface(
            onClick = onOpenCommitments,
            shape = RoundedCornerShape(24.dp),
            color = t.card,
            shadowElevation = 2.dp,
            modifier = Modifier.weight(1f),
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.SpaceBetween,
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Box(
                        modifier = Modifier
                            .size(38.dp)
                            .clip(CircleShape)
                            .background(Color(0xFFEA5C3C).copy(alpha = 0.12f)),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text("📅", fontSize = 16.sp)
                    }
                    Surface(
                        shape = RoundedCornerShape(999.dp),
                        color = Color(0xFFEA5C3C).copy(alpha = 0.12f),
                    ) {
                        Text(
                            text = "سررسید فردا",
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                            style = MaterialTheme.typography.labelSmall,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFFEA5C3C),
                        )
                    }
                }
                Spacer(Modifier.height(18.dp))
                Text(
                    text = "${faDigits(activeCommitmentsCount.toString())} قسط فعال",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Black,
                    color = t.ink,
                )
                Text(
                    text = commitmentSubtitle,
                    style = MaterialTheme.typography.labelSmall,
                    color = t.muted,
                    fontSize = 10.sp,
                    maxLines = 1,
                )
            }
        }
    }
}

@Composable
private fun DashboardSmartInsightBanner(onAction: () -> Unit) {
    val t = CanvasTheme.tokens
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(24.dp),
        color = Color(0xFF2563EB).copy(alpha = if (t.isDark) 0.18f else 0.08f),
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.Top,
        ) {
            Surface(
                shape = CircleShape,
                color = Color(0xFF2563EB),
                modifier = Modifier.size(40.dp),
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Text("✦", color = Color.White, fontWeight = FontWeight.Black, fontSize = 16.sp)
                }
            }
            Column(modifier = Modifier.weight(1f)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = "پیشنهاد هوشمند سرمایه‌بان",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.ExtraBold,
                        color = Color(0xFF2563EB),
                    )
                    Text("همین الان", style = MaterialTheme.typography.labelSmall, color = t.muted, fontSize = 10.sp)
                }
                Spacer(Modifier.height(4.dp))
                Text(
                    text = "شما ۳۲ میلیون تومان وجه راکد در حساب جاری دارید. با انتقال آن به صندوق درآمد ثابت «افرا»، ماهانه حدود ۸۰۰٬۰۰۰ تومان سود روزشمار بدون ریسک دریافت خواهید کرد.",
                    style = MaterialTheme.typography.bodySmall,
                    color = t.inkSecondary,
                    lineHeight = 19.sp,
                )
                Spacer(Modifier.height(10.dp))
                Surface(
                    onClick = onAction,
                    shape = RoundedCornerShape(999.dp),
                    color = t.card,
                    shadowElevation = 1.dp,
                ) {
                    Text(
                        text = "مشاهده و انتقال به صندوق ←",
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 7.dp),
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.ExtraBold,
                        color = Color(0xFF2563EB),
                    )
                }
            }
        }
    }
}

@Composable
private fun DashboardMarketRibbon(
    items: List<LitheMarketRow>,
    onShowAllMarket: () -> Unit,
    onOpenNativeMarket: () -> Unit,
) {
    val t = CanvasTheme.tokens
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = "نبض بازارها",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.ExtraBold,
                color = t.ink,
            )
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(
                    text = "نرخ لحظه‌ای",
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFF2563EB),
                    modifier = Modifier.clickable(onClick = onShowAllMarket),
                )
                Text(
                    text = "سبد دارایی ←",
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Bold,
                    color = t.muted,
                    modifier = Modifier.clickable(onClick = onOpenNativeMarket),
                )
            }
        }

        LazyRow(
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            items(items, key = { it.id }) { item ->
                val up = item.changePct >= 0.0
                val changeColor = if (up) Color(0xFF10B981) else Color(0xFFF43F5E)
                Surface(
                    onClick = onShowAllMarket,
                    shape = RoundedCornerShape(20.dp),
                    color = t.card,
                    shadowElevation = 2.dp,
                    modifier = Modifier.width(152.dp),
                ) {
                    Column(modifier = Modifier.padding(14.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp),
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(24.dp)
                                        .clip(CircleShape)
                                        .background(Color(item.badgeColorHex).copy(alpha = 0.16f)),
                                    contentAlignment = Alignment.Center,
                                ) {
                                    Text(
                                        text = item.nameFa.take(1),
                                        style = MaterialTheme.typography.labelSmall,
                                        fontWeight = FontWeight.Black,
                                        color = Color(item.badgeColorHex),
                                    )
                                }
                                Text(
                                    text = item.nameFa,
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = FontWeight.Bold,
                                    color = t.ink,
                                    maxLines = 1,
                                )
                            }
                        }
                        Spacer(Modifier.height(10.dp))
                        Text(
                            text = faNumber(item.priceToman),
                            style = figureStyle(t.ink, FontWeight.Black).copy(fontSize = 15.sp),
                            maxLines = 1,
                        )
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text("تومان", style = MaterialTheme.typography.labelSmall, color = t.muted, fontSize = 10.sp)
                            Text(
                                text = "${if (up) "+" else ""}${faDigits(item.changePct.toString())}٪",
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold,
                                color = changeColor,
                                fontSize = 10.sp,
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun DashboardRecentTransactionsCard(
    entries: List<LedgerEntry>,
    privacyMode: Boolean,
    onSeeAll: () -> Unit,
    onOpenFullLedger: () -> Unit,
) {
    val t = CanvasTheme.tokens
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(26.dp),
        color = t.card,
        shadowElevation = 2.dp,
    ) {
        Column(modifier = Modifier.padding(18.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = "تراکنش‌های اخیر",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.ExtraBold,
                    color = t.ink,
                )
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(
                        text = "تحلیل مخارج",
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFF2563EB),
                        modifier = Modifier.clickable(onClick = onSeeAll),
                    )
                    Text(
                        text = "دفتر کل ←",
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        color = t.muted,
                        modifier = Modifier.clickable(onClick = onOpenFullLedger),
                    )
                }
            }

            Spacer(Modifier.height(14.dp))

            if (entries.isNotEmpty()) {
                entries.forEachIndexed { idx, e ->
                    val isIn = e.txn.direction == "in"
                    val toman = kotlin.math.abs((e.txn.signedRial ?: e.txn.amountRial ?: 0L) / 10L)
                    LitheTransactionRowItem(
                        title = e.txn.merchant?.takeIf { it.isNotBlank() } ?: e.categoryFa,
                        subtitle = "${e.categoryFa} • ${e.txn.accountId}",
                        amountToman = toman,
                        isIncome = isIn,
                        privacyMode = privacyMode,
                    )
                    if (idx < entries.lastIndex) {
                        HorizontalDivider(
                            modifier = Modifier.padding(vertical = 10.dp),
                            color = t.track,
                        )
                    }
                }
            } else {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    Text("🧾", fontSize = 24.sp)
                    Text(
                        text = "هنوز تراکنشی ثبت نشده است",
                        style = MaterialTheme.typography.bodySmall,
                        fontWeight = FontWeight.Bold,
                        color = t.ink,
                    )
                    Text(
                        text = "با دریافت پیامک‌های بانکی یا افزودن دستی، تراکنش‌ها در اینجا نمایش می‌یابند.",
                        style = MaterialTheme.typography.labelSmall,
                        color = t.muted,
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                    )
                }
            }
        }
    }
}

@Composable
private fun LitheTransactionRowItem(
    title: String,
    subtitle: String,
    amountToman: Long,
    isIncome: Boolean,
    privacyMode: Boolean,
) {
    val t = CanvasTheme.tokens
    val badgeColor = if (isIncome) Color(0xFF10B981) else Color(0xFFEA5C3C)
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier.weight(1f),
        ) {
            Box(
                modifier = Modifier
                    .size(42.dp)
                    .clip(CircleShape)
                    .background(badgeColor.copy(alpha = 0.12f)),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = if (isIncome) "↙" else "↗",
                    color = badgeColor,
                    fontWeight = FontWeight.Black,
                    fontSize = 16.sp,
                )
            }
            Column {
                Text(
                    text = title,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Bold,
                    color = t.ink,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.labelSmall,
                    color = t.muted,
                    fontSize = 11.sp,
                )
            }
        }
        Column(horizontalAlignment = Alignment.End) {
            Text(
                text = if (privacyMode) "••••••" else "${if (isIncome) "+" else "-"}${faNumber(amountToman)}",
                style = figureStyle(if (isIncome) Color(0xFF059669) else t.ink, FontWeight.ExtraBold).copy(fontSize = 14.sp),
            )
            Text("تومان", style = MaterialTheme.typography.labelSmall, color = t.muted, fontSize = 10.sp)
        }
    }
}

// ═══════════════════════════════════════════════════════════════════════════════════════════════
// 3. TAB 2 — ANALYTICS (تحلیل مخارج - Stitch _2/code.html)
// ═══════════════════════════════════════════════════════════════════════════════════════════════

@Composable
private fun AnalyticsPeriodSelector(
    selected: String,
    onSelect: (String) -> Unit,
) {
    val t = CanvasTheme.tokens
    val options = listOf("روزانه", "هفتگی", "ماهانه", "سالانه")
    Surface(
        shape = RoundedCornerShape(999.dp),
        color = t.card,
        shadowElevation = 2.dp,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(5.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            options.forEach { opt ->
                val active = opt == selected
                Surface(
                    onClick = { onSelect(opt) },
                    shape = RoundedCornerShape(999.dp),
                    color = if (active) t.primaryAction else Color.Transparent,
                    modifier = Modifier.weight(1f),
                ) {
                    Text(
                        text = opt,
                        modifier = Modifier.padding(vertical = 9.dp),
                        textAlign = TextAlign.Center,
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold,
                        color = if (active) t.onPrimaryAction else t.muted,
                    )
                }
            }
        }
    }
}

@Composable
private fun AnalyticsCashflowHeroCard(
    period: String,
    privacyMode: Boolean,
) {
    val t = CanvasTheme.tokens
    val factor = when (period) {
        "روزانه" -> 0.04
        "هفتگی" -> 0.25
        "سالانه" -> 11.5
        else -> 1.0
    }
    val expenseToman = (28_450_000L * factor).roundToLong()
    val incomeToman = (45_200_000L * factor).roundToLong()

    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(28.dp),
        color = t.card,
        shadowElevation = 3.dp,
    ) {
        Column(modifier = Modifier.padding(20.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = "تراز ورودی و خروجی ($period)",
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold,
                    color = t.muted,
                )
                Surface(
                    shape = RoundedCornerShape(999.dp),
                    color = Color(0xFF10B981).copy(alpha = 0.12f),
                ) {
                    Text(
                        text = "✓ تراز مثبت (+۱۶.۷ م)",
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFF059669),
                    )
                }
            }

            Spacer(Modifier.height(14.dp))

            // 2-Column Income vs Expense Pod
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(20.dp))
                        .background(t.track.copy(alpha = 0.55f))
                        .padding(14.dp),
                ) {
                    Text("↘ مجموع مخارج", style = MaterialTheme.typography.labelSmall, color = Color(0xFFEA5C3C), fontWeight = FontWeight.Bold)
                    Spacer(Modifier.height(6.dp))
                    Text(
                        text = if (privacyMode) "••••••" else faNumber(expenseToman),
                        style = figureStyle(t.ink, FontWeight.Black).copy(fontSize = 18.sp),
                    )
                    Text("تومان • ۱۲٪ کمتر از ماه قبل", style = MaterialTheme.typography.labelSmall, color = t.muted, fontSize = 10.sp)
                }

                Column(
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(20.dp))
                        .background(t.track.copy(alpha = 0.55f))
                        .padding(14.dp),
                ) {
                    Text("↙ دریافتی ناخالص", style = MaterialTheme.typography.labelSmall, color = Color(0xFF10B981), fontWeight = FontWeight.Bold)
                    Spacer(Modifier.height(6.dp))
                    Text(
                        text = if (privacyMode) "••••••" else faNumber(incomeToman),
                        style = figureStyle(t.ink, FontWeight.Black).copy(fontSize = 18.sp),
                    )
                    Text("تومان • حقوق + سود صندوق", style = MaterialTheme.typography.labelSmall, color = t.muted, fontSize = 10.sp)
                }
            }

            Spacer(Modifier.height(18.dp))

            // 7-Day Rhythm Capsule Bar Chart
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = "ریتم جریان نقدینگی روزانه",
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Bold,
                    color = t.inkSecondary,
                )
                Text(
                    text = "میانگین روزانه: ۹۴۸ هزار تومان",
                    style = MaterialTheme.typography.labelSmall,
                    color = t.muted,
                    fontSize = 10.sp,
                )
            }

            Spacer(Modifier.height(12.dp))

            val bars = listOf(
                Triple("ش", 0.42f, false),
                Triple("ی", 0.65f, false),
                Triple("د", 0.30f, false),
                Triple("س", 0.78f, false),
                Triple("چ", 0.52f, false),
                Triple("پ", 0.95f, true),
                Triple("ج", 0.48f, false),
            )
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(118.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Bottom,
            ) {
                bars.forEach { (day, frac, isPeak) ->
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Bottom,
                        modifier = Modifier.weight(1f),
                    ) {
                        if (isPeak) {
                            Surface(
                                shape = RoundedCornerShape(999.dp),
                                color = t.primaryAction,
                            ) {
                                Text(
                                    text = "اوج",
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                                    fontSize = 9.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = t.onPrimaryAction,
                                )
                            }
                            Spacer(Modifier.height(4.dp))
                        }
                        Box(
                            modifier = Modifier
                                .width(18.dp)
                                .height((72.dp * frac))
                                .clip(RoundedCornerShape(999.dp))
                                .background(
                                    when {
                                        isPeak -> Color(0xFFEA5C3C)
                                        frac > 0.6f -> Color(0xFF2563EB)
                                        else -> Color(0xFF2563EB).copy(alpha = 0.25f)
                                    },
                                ),
                        )
                        Spacer(Modifier.height(6.dp))
                        Text(
                            text = day,
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = if (isPeak) FontWeight.Black else FontWeight.Medium,
                            color = if (isPeak) Color(0xFFEA5C3C) else t.muted,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun AnalyticsBehavioralInsightCard(
    smartCapEnabled: Boolean,
    onToggleCap: () -> Unit,
) {
    val t = CanvasTheme.tokens
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(24.dp),
        color = Color(0xFFF59E0B).copy(alpha = if (t.isDark) 0.16f else 0.10f),
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Surface(
                shape = CircleShape,
                color = Color(0xFFF59E0B),
                modifier = Modifier.size(38.dp),
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Text("💡", fontSize = 16.sp)
                }
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "فرصت صرفه‌جویی ۷۵۰ هزار تومانی",
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.ExtraBold,
                    color = t.ink,
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    text = "هزینه سفارش غذا و کافه در روزهای پنجشنبه و جمعه ۴۲٪ بیشتر از میانگین هفتگی شماست.",
                    style = MaterialTheme.typography.bodySmall,
                    color = t.inkSecondary,
                )
                Spacer(Modifier.height(10.dp))
                Surface(
                    onClick = onToggleCap,
                    shape = RoundedCornerShape(999.dp),
                    color = if (smartCapEnabled) Color(0xFF10B981) else t.card,
                ) {
                    Text(
                        text = if (smartCapEnabled) "✓ سقف بودجه هوشمند فعال است" else "فعالسازی سقف بودجه هوشمند ←",
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        color = if (smartCapEnabled) Color.White else Color(0xFFD97706),
                    )
                }
            }
        }
    }
}

@Composable
private fun AnalyticsCategoryDonutCard(privacyMode: Boolean) {
    val t = CanvasTheme.tokens
    val categories = listOf(
        Triple("مسکن و اجاره", 35, Color(0xFF2563EB)),
        Triple("خوراک و سوپرمارکت", 25, Color(0xFFEA5C3C)),
        Triple("حمل‌ونقل و اسنپ", 20, Color(0xFF10B981)),
        Triple("تفریح، کافه و اشتراک", 20, Color(0xFFF59E0B)),
    )
    val amounts = listOf(9_950_000L, 7_110_000L, 5_690_000L, 5_700_000L)

    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(28.dp),
        color = t.card,
        shadowElevation = 2.dp,
    ) {
        Column(modifier = Modifier.padding(20.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = "تقسیم‌بندی سبد مخارج",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.ExtraBold,
                    color = t.ink,
                )
                Text(
                    text = "۴ دسته اصلی",
                    style = MaterialTheme.typography.labelSmall,
                    color = t.muted,
                )
            }

            Spacer(Modifier.height(18.dp))

            // Multi-segment Donut Ring + Legend
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(18.dp),
            ) {
                Box(
                    modifier = Modifier.size(128.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Canvas(modifier = Modifier.size(116.dp)) {
                        val stroke = 16.dp.toPx()
                        var startAngle = -90f
                        categories.forEach { (_, pct, color) ->
                            val sweep = (pct / 100f) * 360f
                            drawArc(
                                color = color,
                                startAngle = startAngle + 2f,
                                sweepAngle = (sweep - 4f).coerceAtLeast(2f),
                                useCenter = false,
                                style = Stroke(width = stroke, cap = StrokeCap.Round),
                            )
                            startAngle += sweep
                        }
                    }
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            text = "۳۵٪",
                            style = figureStyle(t.ink, FontWeight.Black).copy(fontSize = 22.sp),
                        )
                        Text(
                            text = "مسکن",
                            style = MaterialTheme.typography.labelSmall,
                            color = t.muted,
                            fontSize = 10.sp,
                        )
                    }
                }

                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    categories.forEachIndexed { idx, (name, pct, color) ->
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(10.dp)
                                        .clip(CircleShape)
                                        .background(color),
                                )
                                Column {
                                    Text(
                                        text = name,
                                        style = MaterialTheme.typography.labelMedium,
                                        fontWeight = FontWeight.Bold,
                                        color = t.ink,
                                    )
                                    Text(
                                        text = if (privacyMode) "••••" else "${faCompact(amounts[idx])} تومان",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = t.muted,
                                        fontSize = 10.sp,
                                    )
                                }
                            }
                            Surface(
                                shape = RoundedCornerShape(999.dp),
                                color = color.copy(alpha = 0.12f),
                            ) {
                                Text(
                                    text = "${faDigits(pct.toString())}٪",
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = FontWeight.Bold,
                                    color = color,
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun AnalyticsSmsSyncBanner(onOpenSmsScanner: () -> Unit) {
    val t = CanvasTheme.tokens
    Surface(
        onClick = onOpenSmsScanner,
        shape = RoundedCornerShape(22.dp),
        color = t.card,
        shadowElevation = 2.dp,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.weight(1f),
            ) {
                Box(
                    modifier = Modifier
                        .size(42.dp)
                        .clip(CircleShape)
                        .background(Color(0xFF10B981).copy(alpha = 0.12f)),
                    contentAlignment = Alignment.Center,
                ) {
                    Text("📩", fontSize = 18.sp)
                }
                Column {
                    Text(
                        text = "ثبت خودکار از پیامک‌های بانکی",
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.ExtraBold,
                        color = t.ink,
                    )
                    Text(
                        text = "پیامک بانک را جایگذاری کنید تا مبلغ و دسته‌بندی استخراج شود",
                        style = MaterialTheme.typography.labelSmall,
                        color = t.muted,
                    )
                }
            }
            Text("اسکن ←", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold, color = Color(0xFF2563EB))
        }
    }
}

@Composable
private fun AnalyticsMajorTransactionsCard(
    entries: List<LedgerEntry>,
    searchQuery: String,
    showSearch: Boolean,
    onToggleSearch: () -> Unit,
    onSearchChange: (String) -> Unit,
    privacyMode: Boolean,
    onOpenFullLedger: () -> Unit,
) {
    val t = CanvasTheme.tokens
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(26.dp),
        color = t.card,
        shadowElevation = 2.dp,
    ) {
        Column(modifier = Modifier.padding(18.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = "تراکنش‌های شاخص دوره",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.ExtraBold,
                    color = t.ink,
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    IconButton(
                        onClick = onToggleSearch,
                        modifier = Modifier
                            .size(34.dp)
                            .clip(CircleShape)
                            .background(t.track),
                    ) {
                        Icon(Icons.Rounded.Search, contentDescription = "جستجو", tint = t.inkSecondary, modifier = Modifier.size(16.dp))
                    }
                    Surface(
                        onClick = onOpenFullLedger,
                        shape = RoundedCornerShape(999.dp),
                        color = t.track,
                    ) {
                        Text(
                            text = "نمای کامل دفتر کل",
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 7.dp),
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            color = t.inkSecondary,
                        )
                    }
                }
            }

            if (showSearch) {
                Spacer(Modifier.height(10.dp))
                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = onSearchChange,
                    placeholder = { Text("جستجو در نام فروشگاه یا دسته...") },
                    singleLine = true,
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier.fillMaxWidth(),
                )
            }

            Spacer(Modifier.height(12.dp))

            val filtered = entries.filter {
                searchQuery.isBlank() || it.merchant.contains(searchQuery, ignoreCase = true) || it.category.fa.contains(searchQuery, ignoreCase = true) || it.note.contains(searchQuery, ignoreCase = true)
            }

            if (filtered.isNotEmpty()) {
                filtered.take(10).forEachIndexed { idx, entry ->
                    LitheTransactionRowItem(
                        title = entry.merchant.ifBlank { "تراکنش بانکی" },
                        subtitle = "${entry.category.fa} • ${formatDate(entry.timestamp)}",
                        amountToman = entry.amountRial / 10L,
                        isIncome = !entry.isExpense,
                        privacyMode = privacyMode,
                    )
                    if (idx < filtered.take(10).lastIndex) {
                        HorizontalDivider(modifier = Modifier.padding(vertical = 10.dp), color = t.track)
                    }
                }
            } else {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    Text("🔍", fontSize = 24.sp)
                    Text(
                        text = "تراکنشی در این دوره یافت نشد",
                        style = MaterialTheme.typography.bodySmall,
                        fontWeight = FontWeight.Bold,
                        color = t.ink,
                    )
                }
            }
        }
    }
}

// ═══════════════════════════════════════════════════════════════════════════════════════════════
// 4. TAB 3 — COMMITMENTS & CHEQUES (اقساط و چک - Stitch _3/code.html)
// ═══════════════════════════════════════════════════════════════════════════════════════════════

@Composable
private fun CommitmentsUrgentBanner(
    commitment: Commitment,
    onPayNow: () -> Unit,
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(999.dp),
        color = Color(0xFFEA5C3C).copy(alpha = 0.12f),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.weight(1f),
            ) {
                Box(
                    modifier = Modifier
                        .size(10.dp)
                        .clip(CircleShape)
                        .background(Color(0xFFEA5C3C)),
                )
                Text(
                    text = "سررسید نزدیک: ${commitment.title} (${faNumber(commitment.remainingAmountToman)} تومان)",
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFFEA5C3C),
                    maxLines = 1,
                )
            }
            Surface(
                onClick = onPayNow,
                shape = RoundedCornerShape(999.dp),
                color = Color(0xFFEA5C3C),
            ) {
                Text(
                    text = "ثبت تسویه",
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 5.dp),
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Bold,
                    color = Color.White,
                )
            }
        }
    }
}

@Composable
private fun CommitmentsHeroSummaryCard(
    userCommitments: List<Commitment>,
    privacyMode: Boolean,
) {
    val t = CanvasTheme.tokens
    val activeList = userCommitments.filter { !it.isSettled }
    val totalRemaining = activeList.sumOf { it.remainingAmountToman }
    val totalOriginal = activeList.sumOf { it.originalAmountToman }
    val settledCount = userCommitments.count { it.isSettled }
    val activeCount = activeList.size
    val progressRatio = if (totalOriginal > 0L) {
        ((totalOriginal - totalRemaining).toFloat() / totalOriginal.toFloat()).coerceIn(0f, 1f)
    } else 0f

    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(28.dp),
        color = t.card,
        shadowElevation = 3.dp,
    ) {
        Column(modifier = Modifier.padding(22.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = "کل بدهی و تعهدات فعال",
                    style = MaterialTheme.typography.bodySmall,
                    fontWeight = FontWeight.Bold,
                    color = t.muted,
                )
                Surface(
                    shape = RoundedCornerShape(999.dp),
                    color = Color(0xFF2563EB).copy(alpha = 0.12f),
                ) {
                    Text(
                        text = "${faDigits(activeCount.toString())} پرونده فعال",
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFF2563EB),
                    )
                }
            }

            Spacer(Modifier.height(8.dp))

            Row(
                verticalAlignment = Alignment.Bottom,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text(
                    text = if (privacyMode) "•••••••••" else faNumber(totalRemaining),
                    style = figureStyle(t.ink, FontWeight.Black).copy(fontSize = 32.sp),
                )
                Text(
                    text = "تومان",
                    style = MaterialTheme.typography.bodySmall,
                    fontWeight = FontWeight.Bold,
                    color = t.muted,
                    modifier = Modifier.padding(bottom = 5.dp),
                )
            }

            Spacer(Modifier.height(14.dp))

            // Monthly settlement progress bar
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text("تسویه شده", style = MaterialTheme.typography.labelSmall, color = t.muted)
                Text(
                    text = if (totalOriginal > 0) "${faNumber(totalOriginal - totalRemaining)} از ${faNumber(totalOriginal)} تومان (${(progressRatio * 100).toInt()}٪)" else "بدون تعهد فعال",
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Bold,
                    color = t.ink,
                )
            }
            Spacer(Modifier.height(6.dp))
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(10.dp)
                    .clip(RoundedCornerShape(999.dp))
                    .background(t.track),
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth(progressRatio.coerceAtLeast(0.02f))
                        .height(10.dp)
                        .clip(RoundedCornerShape(999.dp))
                        .background(Color(0xFF2563EB)),
                )
            }

            Spacer(Modifier.height(16.dp))

            // 3 Micro-stat pods
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                val stats = listOf(
                    Triple(faDigits(activeCount.toString()), "قسط فعال", Color(0xFF2563EB)),
                    Triple(faDigits(settledCount.toString()), "تسویه شده", Color(0xFF10B981)),
                    Triple(if (activeCount == 0) "A+" else "A", "امتیاز خوش‌حسابی", Color(0xFFF59E0B)),
                )
                stats.forEach { (valText, label, col) ->
                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(16.dp))
                            .background(t.track.copy(alpha = 0.55f))
                            .padding(10.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        Text(valText, style = figureStyle(col, FontWeight.Black).copy(fontSize = 18.sp))
                        Text(label, style = MaterialTheme.typography.labelSmall, color = t.muted, fontSize = 10.sp)
                    }
                }
            }
        }
    }
}

@Composable
private fun CommitmentsFilterBar(
    selected: String,
    onSelect: (String) -> Unit,
) {
    val t = CanvasTheme.tokens
    val filters = listOf(
        "all" to "همه تعهدات",
        "loans" to "اقساط بانکی",
        "cheques" to "چک‌های صیادی",
        "cards" to "کارت‌های بانکی",
    )
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        filters.forEach { (id, label) ->
            val active = selected == id
            Surface(
                onClick = { onSelect(id) },
                shape = RoundedCornerShape(999.dp),
                color = if (active) t.primaryAction else t.card,
                shadowElevation = 1.dp,
            ) {
                Text(
                    text = label,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 9.dp),
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold,
                    color = if (active) t.onPrimaryAction else t.inkSecondary,
                )
            }
        }
    }
}

@Composable
private fun CommitmentsStreamList(
    filter: String,
    userCommitments: List<Commitment>,
    bankAccounts: List<BankAccount>,
    onOpenChequeModal: () -> Unit,
    onToggleUserCommitment: (String) -> Unit,
    onCopyText: (String, String) -> Unit,
    onOpenNativeCards: () -> Unit,
    onOpenBankSheet: () -> Unit,
    onAddNewCommitment: () -> Unit,
) {
    val t = CanvasTheme.tokens
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        val filteredCommitments = remember(filter, userCommitments) {
            when (filter) {
                "loans" -> userCommitments.filter { it.kind == CommitmentKind.LOAN }
                "cheques" -> userCommitments.filter { it.kind == CommitmentKind.CHEQUE }
                "cards" -> emptyList()
                else -> userCommitments
            }
        }

        if (filter != "cards") {
            if (filteredCommitments.isEmpty()) {
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(24.dp),
                    color = t.card,
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        Text("📋", fontSize = 28.sp)
                        Text(
                            text = if (filter == "loans") "هیچ وام فعالی ثبت نشده است" else if (filter == "cheques") "هیچ چک صیادی ثبت نشده است" else "هیچ قسط، وام یا چکی ثبت نشده است",
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Bold,
                            color = t.ink,
                        )
                        Text(
                            text = "اقساط و چک‌های شما با دریافت پیامک‌های بانکی یا با افزودن دستی در اینجا نمایش داده می‌شوند.",
                            style = MaterialTheme.typography.bodySmall,
                            color = t.muted,
                            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                        )
                        Button(
                            onClick = onAddNewCommitment,
                            shape = RoundedCornerShape(999.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = t.primaryAction),
                        ) {
                            Text("+ ثبت تعهد یا قسط جدید", color = t.onPrimaryAction, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            } else {
                filteredCommitments.forEach { c ->
                    CommitmentPodCard(
                        title = "${c.title} • ${c.provider}",
                        subtitle = c.kind.fa,
                        badgeFa = if (c.isSettled) "تسویه شده" else "فعال",
                        badgeColor = if (c.isSettled) Color(0xFF10B981) else Color(0xFF2563EB),
                        monthlyAmountFa = "${faNumber(c.remainingAmountToman)} تومان",
                        progress = c.progressPercent,
                        actionLabel = if (c.isSettled) "بازگردانی به فعال" else "ثبت تسویه کامل",
                        onAction = { onToggleUserCommitment(c.id) },
                    )
                }
            }
        }

        // Shetab Bank Cards section
        if (filter == "all" || filter == "cards") {
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(24.dp),
                color = t.card,
                shadowElevation = 2.dp,
            ) {
                Column(modifier = Modifier.padding(18.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            text = "کارت‌های بانکی و شبای من",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.ExtraBold,
                            color = t.ink,
                        )
                        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            Text(
                                text = "مدیریت حساب‌ها",
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFF2563EB),
                                modifier = Modifier.clickable(onClick = onOpenBankSheet),
                            )
                            Text(
                                text = "کیف کارت ۳بعدی ←",
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold,
                                color = t.muted,
                                modifier = Modifier.clickable(onClick = onOpenNativeCards),
                            )
                        }
                    }
                    Spacer(Modifier.height(12.dp))
                    if (bankAccounts.isEmpty()) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(18.dp))
                                .background(t.track.copy(alpha = 0.55f))
                                .padding(16.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(6.dp),
                        ) {
                            Text("💳", fontSize = 24.sp)
                            Text("هنوز کارتی ثبت نشده است", style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Bold, color = t.ink)
                            Text("کارت‌ها به صورت خودکار از پیامک‌های بانکی استخراج یا از بخش مدیریت حساب‌ها اضافه می‌شوند.", style = MaterialTheme.typography.labelSmall, color = t.muted, textAlign = androidx.compose.ui.text.style.TextAlign.Center)
                        }
                    } else {
                        bankAccounts.forEach { acc ->
                            val bankTitle = acc.nickname.ifBlank { "بانک ${acc.bankFa}" }
                            val pan = acc.fullPan.ifBlank { "•••• •••• •••• ••••" }
                            val sheba = acc.sheba.ifBlank { "—" }
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(18.dp))
                                    .background(t.track.copy(alpha = 0.55f))
                                    .padding(14.dp),
                            ) {
                                Text(bankTitle, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.ExtraBold, color = t.ink)
                                Spacer(Modifier.height(6.dp))
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    Text(pan, style = figureStyle(t.inkSecondary, FontWeight.Bold).copy(fontSize = 14.sp))
                                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                        Surface(
                                            onClick = { onCopyText("شماره کارت", pan) },
                                            shape = RoundedCornerShape(999.dp),
                                            color = t.card,
                                        ) {
                                            Text(
                                                text = "کپی کارت",
                                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                                                style = MaterialTheme.typography.labelSmall,
                                                fontWeight = FontWeight.Bold,
                                                color = Color(0xFF2563EB),
                                            )
                                        }
                                        if (sheba != "—") {
                                            Surface(
                                                onClick = { onCopyText("شماره شبا", sheba) },
                                                shape = RoundedCornerShape(999.dp),
                                                color = t.card,
                                            ) {
                                                Text(
                                                    text = "کپی شبا",
                                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                                                    style = MaterialTheme.typography.labelSmall,
                                                    fontWeight = FontWeight.Bold,
                                                    color = Color(0xFF10B981),
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                            Spacer(Modifier.height(8.dp))
                        }
                    }
                }
            }
        }
    }
}

// ═══════════════════════════════════════════════════════════════════════════════════════════════
// 5. TAB 4 — GOALS & PORTFOLIO (پس‌انداز و اهداف - Stitch _4/code.html)
// ═══════════════════════════════════════════════════════════════════════════════════════════════

@Composable
private fun GoalsHeroAllocationCard(
    customGoals: List<LitheGoalItem>,
    holdingsTotalToman: Long,
    privacyMode: Boolean,
) {
    val t = CanvasTheme.tokens
    val goalsSum = customGoals.sumOf { it.currentToman }
    val totalSavings = if (holdingsTotalToman > goalsSum) holdingsTotalToman else goalsSum

    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(28.dp),
        color = t.card,
        shadowElevation = 3.dp,
    ) {
        Column(modifier = Modifier.padding(22.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = "مجموع پس‌انداز و سرمایه‌گذاری اهداف",
                    style = MaterialTheme.typography.bodySmall,
                    fontWeight = FontWeight.Bold,
                    color = t.muted,
                )
                Surface(
                    shape = RoundedCornerShape(999.dp),
                    color = Color(0xFF10B981).copy(alpha = 0.12f),
                ) {
                    Text(
                        text = "↗ +۱۸.۴٪ رشد کل",
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFF059669),
                    )
                }
            }

            Spacer(Modifier.height(8.dp))

            Row(
                verticalAlignment = Alignment.Bottom,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text(
                    text = if (privacyMode) "•••••••••" else faNumber(totalSavings),
                    style = figureStyle(t.ink, FontWeight.Black).copy(fontSize = 32.sp),
                )
                Text(
                    text = "تومان",
                    style = MaterialTheme.typography.bodySmall,
                    fontWeight = FontWeight.Bold,
                    color = t.muted,
                    modifier = Modifier.padding(bottom = 5.dp),
                )
            }

            Spacer(Modifier.height(14.dp))

            // Multi-Segmented Progress Bar (Gold 42%, Fixed Income 33%, Equity 15%, Crypto 10%)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(12.dp)
                    .clip(RoundedCornerShape(999.dp)),
                horizontalArrangement = Arrangement.spacedBy(3.dp),
            ) {
                Box(Modifier.weight(0.42f).height(12.dp).background(Color(0xFFF59E0B)))
                Box(Modifier.weight(0.33f).height(12.dp).background(Color(0xFF2563EB)))
                Box(Modifier.weight(0.15f).height(12.dp).background(Color(0xFF10B981)))
                Box(Modifier.weight(0.10f).height(12.dp).background(Color(0xFFEA5C3C)))
            }

            Spacer(Modifier.height(12.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                AllocationDotItem("طلا و سکه ۴۲٪", Color(0xFFF59E0B))
                AllocationDotItem("درآمد ثابت ۳۳٪", Color(0xFF2563EB))
                AllocationDotItem("سهام ۱۵٪", Color(0xFF10B981))
                AllocationDotItem("رمزارز ۱۰٪", Color(0xFFEA5C3C))
            }
        }
    }
}

@Composable
private fun AllocationDotItem(label: String, color: Color) {
    val t = CanvasTheme.tokens
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Box(
            modifier = Modifier
                .size(8.dp)
                .clip(CircleShape)
                .background(color),
        )
        Text(label, style = MaterialTheme.typography.labelSmall, color = t.inkSecondary, fontSize = 10.sp)
    }
}

@Composable
private fun GoalsMicroSavingsPod(
    enabled: Boolean,
    onToggle: (Boolean) -> Unit,
) {
    val t = CanvasTheme.tokens
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(24.dp),
        color = t.card,
        shadowElevation = 2.dp,
    ) {
        Column(modifier = Modifier.padding(18.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    modifier = Modifier.weight(1f),
                ) {
                    Box(
                        modifier = Modifier
                            .size(40.dp)
                            .clip(CircleShape)
                            .background(Color(0xFF10B981).copy(alpha = 0.14f)),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text("🪙", fontSize = 18.sp)
                    }
                    Column {
                        Text(
                            text = "قلک خودکار خرده‌خریدها (گرد کردن مبالغ)",
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.ExtraBold,
                            color = t.ink,
                        )
                        Text(
                            text = "گرد کردن خودکار تراکنش‌های کارتی به ضریب ۱۰۰ هزار تومان",
                            style = MaterialTheme.typography.labelSmall,
                            color = t.muted,
                        )
                    }
                }
                Switch(
                    checked = enabled,
                    onCheckedChange = onToggle,
                    colors = SwitchDefaults.colors(checkedTrackColor = Color(0xFF10B981)),
                    modifier = Modifier.testTag("micro_savings_switch"),
                )
            }

            if (enabled) {
                Spacer(Modifier.height(10.dp))
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(14.dp))
                        .background(Color(0xFF10B981).copy(alpha = 0.10f))
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Text("ذخیره شده خودکار این ماه:", style = MaterialTheme.typography.labelSmall, color = t.inkSecondary)
                    Text("+۱٬۴۵۰٬۰۰۰ تومان (معادل ۰.۲۲ گرم طلا)", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.ExtraBold, color = Color(0xFF059669))
                }
            }
        }
    }
}

@Composable
private fun GoalsFilterBar(
    selected: String,
    onSelect: (String) -> Unit,
) {
    val t = CanvasTheme.tokens
    val chips = listOf(
        "all" to "همه اهداف",
        "emergency" to "اضطراری",
        "long" to "بزرگ و آینده‌ساز",
        "short" to "کوتاه‌مدت",
        "portfolio" to "سبد دارایی زنده",
    )
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        chips.forEach { (id, label) ->
            val active = selected == id
            Surface(
                onClick = { onSelect(id) },
                shape = RoundedCornerShape(999.dp),
                color = if (active) t.primaryAction else t.card,
                shadowElevation = 1.dp,
            ) {
                Text(
                    text = label,
                    modifier = Modifier.padding(horizontal = 15.dp, vertical = 9.dp),
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold,
                    color = if (active) t.onPrimaryAction else t.inkSecondary,
                )
            }
        }
    }
}

@Composable
private fun GoalsActiveListSection(
    filter: String,
    goals: List<LitheGoalItem>,
    holdings: List<Holding>,
    effectiveRates: Map<String, Double>,
    onTopUpGoal: (String, Long) -> Unit,
    onOpenNativeMarket: () -> Unit,
) {
    val t = CanvasTheme.tokens
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        val visibleGoals = if (filter == "all" || filter == "portfolio") goals else goals.filter { it.category == filter }

        if (filter != "portfolio") {
            visibleGoals.forEach { goal ->
                val pct = ((goal.currentToman.toDouble() / goal.targetToman.toDouble().coerceAtLeast(1.0)) * 100.0).toInt().coerceIn(0, 100)
                val accent = Color(goal.accentHex)
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(24.dp),
                    color = t.card,
                    shadowElevation = 2.dp,
                ) {
                    Column(modifier = Modifier.padding(18.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(12.dp),
                                modifier = Modifier.weight(1f),
                            ) {
                                LitheCircularProgressRing(
                                    percent = pct,
                                    size = 52.dp,
                                    color = accent,
                                    trackColor = t.track,
                                )
                                Column {
                                    Text(goal.title, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.ExtraBold, color = t.ink)
                                    Text(goal.subtitle, style = MaterialTheme.typography.labelSmall, color = t.muted)
                                }
                            }
                            Surface(
                                shape = RoundedCornerShape(999.dp),
                                color = accent.copy(alpha = 0.12f),
                            ) {
                                Text(
                                    text = goal.badgeFa,
                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = FontWeight.Bold,
                                    color = accent,
                                )
                            }
                        }

                        Spacer(Modifier.height(14.dp))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Column {
                                Text("ذخیره فعلی از هدف", style = MaterialTheme.typography.labelSmall, color = t.muted)
                                Text(
                                    text = "${faCompact(goal.currentToman)} از ${faCompact(goal.targetToman)} تومان",
                                    style = MaterialTheme.typography.labelLarge,
                                    fontWeight = FontWeight.Black,
                                    color = t.ink,
                                )
                            }
                            Surface(
                                onClick = { onTopUpGoal(goal.id, 2_000_000L) },
                                shape = RoundedCornerShape(999.dp),
                                color = t.primaryAction,
                            ) {
                                Text(
                                    text = "+ افزایش موجودی",
                                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = FontWeight.Bold,
                                    color = t.onPrimaryAction,
                                )
                            }
                        }
                    }
                }
            }
        }

        // Live Portfolio Holdings Section
        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(24.dp),
            color = t.card,
            shadowElevation = 2.dp,
        ) {
            Column(modifier = Modifier.padding(18.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = "دارایی‌های متصل به اهداف",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.ExtraBold,
                        color = t.ink,
                    )
                    Text(
                        text = "مدیریت کامل دارایی‌ها ←",
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFF2563EB),
                        modifier = Modifier.clickable(onClick = onOpenNativeMarket),
                    )
                }
                Spacer(Modifier.height(10.dp))
                if (holdings.isNotEmpty()) {
                    holdings.take(4).forEach { h ->
                        val rate = effectiveRates[h.typeId] ?: 0.0
                        val valToman = (h.amount * rate).roundToLong()
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 6.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                        ) {
                            Text(h.label.ifBlank { h.typeId }, style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Bold, color = t.ink)
                            Text("${faNumber(valToman)} تومان", style = figureStyle(t.ink, FontWeight.ExtraBold).copy(fontSize = 14.sp))
                        }
                    }
                } else {
                    Text(
                        text = "۱۸.۵ گرم طلای ۱۸ عیار • ۲ قطعه سکه امامی • ۴۲۰ تتر",
                        style = MaterialTheme.typography.bodySmall,
                        color = t.muted,
                    )
                }
            }
        }
    }
}

@Composable
private fun GoalsSmartSimulatorPod(
    targetMillion: Float,
    onTargetChange: (Float) -> Unit,
    monthlyMillion: Float,
    onMonthlyChange: (Float) -> Unit,
    onCreateGoalFromSim: () -> Unit,
) {
    val t = CanvasTheme.tokens
    val monthsNeeded = ceil((targetMillion / monthlyMillion.coerceAtLeast(0.5f)).toDouble()).toInt()

    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(28.dp),
        color = t.card,
        shadowElevation = 3.dp,
    ) {
        Column(modifier = Modifier.padding(20.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = "شبیه‌ساز هوشمند پس‌انداز",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.ExtraBold,
                    color = t.ink,
                )
                Surface(
                    shape = RoundedCornerShape(999.dp),
                    color = Color(0xFF2563EB).copy(alpha = 0.12f),
                ) {
                    Text(
                        text = "محاسبه زنده",
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFF2563EB),
                    )
                }
            }

            Spacer(Modifier.height(14.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text("مبلغ هدف مالی", style = MaterialTheme.typography.labelSmall, color = t.muted)
                Text("${faDigits(targetMillion.toInt().toString())} میلیون تومان", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Black, color = t.ink)
            }
            Slider(
                value = targetMillion,
                onValueChange = onTargetChange,
                valueRange = 10f..250f,
                colors = SliderDefaults.colors(thumbColor = Color(0xFF2563EB), activeTrackColor = Color(0xFF2563EB)),
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text("پس‌انداز ماهانه شما", style = MaterialTheme.typography.labelSmall, color = t.muted)
                Text("${faDigits(String.format(java.util.Locale.US, "%.1f", monthlyMillion))} میلیون تومان", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Black, color = Color(0xFF10B981))
            }
            Slider(
                value = monthlyMillion,
                onValueChange = onMonthlyChange,
                valueRange = 1f..40f,
                colors = SliderDefaults.colors(thumbColor = Color(0xFF10B981), activeTrackColor = Color(0xFF10B981)),
            )

            Spacer(Modifier.height(8.dp))

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(18.dp))
                    .background(t.track.copy(alpha = 0.65f))
                    .padding(14.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column {
                    Text("تخمین زمان رسیدن به هدف:", style = MaterialTheme.typography.labelSmall, color = t.muted)
                    Text(
                        text = "${faDigits(monthsNeeded.toString())} ماه دیگر (با سود مرکب صندوق طلا)",
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Black,
                        color = t.ink,
                    )
                }
            }

            Spacer(Modifier.height(14.dp))

            Button(
                onClick = onCreateGoalFromSim,
                shape = RoundedCornerShape(999.dp),
                colors = ButtonDefaults.buttonColors(containerColor = t.primaryAction),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(50.dp)
                    .testTag("create_goal_from_sim_button"),
            ) {
                Text(
                    text = "+ تعریف هدف جدید پس‌انداز",
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.ExtraBold,
                    color = t.onPrimaryAction,
                )
            }
        }
    }
}

// ═══════════════════════════════════════════════════════════════════════════════════════════════
// 6. FLOATING CAPSULE BOTTOM NAVIGATION DOCK (Lithe Vitality Floating Island)
// ═══════════════════════════════════════════════════════════════════════════════════════════════

@Composable
private fun LitheFloatingDock(
    activeTab: LitheTab,
    onSelectTab: (LitheTab) -> Unit,
    onQuickAdd: () -> Unit,
) {
    val t = CanvasTheme.tokens
    Surface(
        shape = RoundedCornerShape(999.dp),
        color = t.navFill,
        shadowElevation = 10.dp,
        modifier = Modifier
            .fillMaxWidth()
            .border(1.dp, t.navBorder, RoundedCornerShape(999.dp)),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 10.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            // Tab 1: خانه
            LitheDockTabItem(
                tab = LitheTab.DASHBOARD,
                symbol = "⌂",
                selected = activeTab == LitheTab.DASHBOARD,
                onClick = { onSelectTab(LitheTab.DASHBOARD) },
            )
            // Tab 2: تحلیل
            LitheDockTabItem(
                tab = LitheTab.ANALYTICS,
                symbol = "◔",
                selected = activeTab == LitheTab.ANALYTICS,
                onClick = { onSelectTab(LitheTab.ANALYTICS) },
            )

            // Center Elevated Charcoal '+' Quick-Add Button
            Surface(
                onClick = onQuickAdd,
                shape = CircleShape,
                color = t.primaryAction,
                shadowElevation = 6.dp,
                modifier = Modifier
                    .size(52.dp)
                    .testTag("dock_center_quick_add"),
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = Icons.Rounded.Add,
                        contentDescription = "ثبت سریع",
                        tint = t.onPrimaryAction,
                        modifier = Modifier.size(26.dp),
                    )
                }
            }

            // Tab 3: اقساط
            LitheDockTabItem(
                tab = LitheTab.COMMITMENTS,
                symbol = "🗓",
                selected = activeTab == LitheTab.COMMITMENTS,
                onClick = { onSelectTab(LitheTab.COMMITMENTS) },
            )
            // Tab 4: اهداف
            LitheDockTabItem(
                tab = LitheTab.GOALS,
                symbol = "◎",
                selected = activeTab == LitheTab.GOALS,
                onClick = { onSelectTab(LitheTab.GOALS) },
            )
        }
    }
}

@Composable
private fun LitheDockTabItem(
    tab: LitheTab,
    symbol: String,
    selected: Boolean,
    onClick: () -> Unit,
) {
    val t = CanvasTheme.tokens
    val activeColor = Color(0xFF2563EB)
    Column(
        modifier = Modifier
            .clip(RoundedCornerShape(999.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 6.dp)
            .testTag("dock_tab_${tab.id}"),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = symbol,
            fontSize = 17.sp,
            fontWeight = FontWeight.Black,
            color = if (selected) activeColor else t.muted,
        )
        Text(
            text = tab.labelFa,
            style = MaterialTheme.typography.labelSmall,
            fontWeight = if (selected) FontWeight.ExtraBold else FontWeight.Medium,
            color = if (selected) activeColor else t.muted,
            fontSize = 11.sp,
        )
    }
}

// ═══════════════════════════════════════════════════════════════════════════════════════════════
// 7. SHARED CIRCULAR PROGRESS RING & MODALS (Market, Transfer, Sayad Cheque, 6-Tab QuickAdd)
// ═══════════════════════════════════════════════════════════════════════════════════════════════

@Composable
fun LitheCircularProgressRing(
    percent: Int,
    size: Dp,
    color: Color,
    trackColor: Color,
) {
    val t = CanvasTheme.tokens
    Box(
        modifier = Modifier.size(size),
        contentAlignment = Alignment.Center,
    ) {
        Canvas(modifier = Modifier.size(size)) {
            val stroke = 5.dp.toPx()
            drawArc(
                color = trackColor,
                startAngle = 0f,
                sweepAngle = 360f,
                useCenter = false,
                style = Stroke(width = stroke, cap = StrokeCap.Round),
            )
            drawArc(
                color = color,
                startAngle = -90f,
                sweepAngle = (percent.coerceIn(0, 100) / 100f) * 360f,
                useCenter = false,
                style = Stroke(width = stroke, cap = StrokeCap.Round),
            )
        }
        Text(
            text = "${faDigits(percent.toString())}٪",
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.Black,
            color = t.ink,
            fontSize = 10.sp,
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun LitheLiveMarketBottomSheet(
    items: List<LitheMarketRow>,
    onDismiss: () -> Unit,
    onAddHolding: () -> Unit,
    onOpenNativeAssets: () -> Unit,
) {
    val t = CanvasTheme.tokens
    var catFilter by remember { mutableStateOf("all") }
    val categories = listOf(
        "all" to "همه",
        "gold" to "طلا",
        "coin" to "سکه",
        "fx" to "ارز",
        "crypto" to "رمزارز",
        "fund" to "صندوق بورس",
    )

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
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
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column {
                    Text("نرخ لحظه‌ای بازار طلا، ارز و رمزارز", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Black, color = t.ink)
                    Text("بروزرسانی زنده • قیمت‌ها به تومان", style = MaterialTheme.typography.labelSmall, color = t.muted)
                }
                IconButton(onClick = onDismiss) {
                    Icon(Icons.Rounded.Close, contentDescription = "بستن", tint = t.inkSecondary)
                }
            }

            Spacer(Modifier.height(12.dp))

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                categories.forEach { (id, label) ->
                    val sel = catFilter == id
                    Surface(
                        onClick = { catFilter = id },
                        shape = RoundedCornerShape(999.dp),
                        color = if (sel) t.primaryAction else t.card,
                    ) {
                        Text(
                            text = label,
                            modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            color = if (sel) t.onPrimaryAction else t.inkSecondary,
                        )
                    }
                }
            }

            Spacer(Modifier.height(12.dp))

            val filtered = if (catFilter == "all") items else items.filter { it.category == catFilter }
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(340.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                filtered.forEach { row ->
                    val up = row.changePct >= 0.0
                    Surface(
                        shape = RoundedCornerShape(18.dp),
                        color = t.card,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Row(
                            modifier = Modifier.padding(14.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Column {
                                Text(row.nameFa, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold, color = t.ink)
                                Text(row.subtitleFa, style = MaterialTheme.typography.labelSmall, color = t.muted)
                            }
                            Column(horizontalAlignment = Alignment.End) {
                                Text("${faNumber(row.priceToman)} تومان", style = figureStyle(t.ink, FontWeight.Black).copy(fontSize = 15.sp))
                                Text(
                                    text = "${if (up) "+" else ""}${faDigits(row.changePct.toString())}٪",
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = FontWeight.Bold,
                                    color = if (up) Color(0xFF10B981) else Color(0xFFF43F5E),
                                )
                            }
                        }
                    }
                }
            }

            Spacer(Modifier.height(12.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Button(
                    onClick = onAddHolding,
                    shape = RoundedCornerShape(999.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = t.primaryAction),
                    modifier = Modifier.weight(1f),
                ) {
                    Text("+ ثبت دارایی جدید", color = t.onPrimaryAction, fontWeight = FontWeight.Bold)
                }
                Button(
                    onClick = onOpenNativeAssets,
                    shape = RoundedCornerShape(999.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF2563EB)),
                    modifier = Modifier.weight(1f),
                ) {
                    Text("پرتفوی کامل من", color = Color.White, fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun LitheTransferCardsSheet(
    bankAccounts: List<BankAccount>,
    onCopy: (String, String) -> Unit,
    onAddCard: () -> Unit,
    onDismiss: () -> Unit,
) {
    val t = CanvasTheme.tokens
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = t.ground,
        shape = RoundedCornerShape(topStart = 32.dp, topEnd = 32.dp),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(horizontal = 20.dp)
                .padding(bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text("انتقال سریع و اشتراک‌گذاری کارت و شبا", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Black, color = t.ink)
            if (bankAccounts.isEmpty()) {
                Surface(
                    shape = RoundedCornerShape(20.dp),
                    color = t.card,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(20.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Text("💳", fontSize = 28.sp)
                        Text("هیچ حسابی ثبت نشده است", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold, color = t.ink)
                        Text("شماره کارت و شبای شما پس از دریافت پیامک‌های بانکی یا با افزودن دستی در اینجا قرار می‌گیرد.", style = MaterialTheme.typography.labelSmall, color = t.muted, textAlign = androidx.compose.ui.text.style.TextAlign.Center)
                    }
                }
            } else {
                bankAccounts.forEach { acc ->
                    val title = acc.nickname.ifBlank { "بانک ${acc.bankFa}" }
                    val pan = acc.fullPan.ifBlank { "•••• •••• •••• ••••" }
                    val sheba = acc.sheba.ifBlank { "—" }
                    Surface(shape = RoundedCornerShape(20.dp), color = t.card, modifier = Modifier.fillMaxWidth()) {
                        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text(title, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.ExtraBold, color = t.ink)
                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                Text(pan, style = figureStyle(t.inkSecondary, FontWeight.Bold).copy(fontSize = 14.sp))
                                Text(
                                    text = "کپی شماره کارت",
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = FontWeight.Bold,
                                    color = Color(0xFF2563EB),
                                    modifier = Modifier.clickable { onCopy("شماره کارت", pan) },
                                )
                            }
                            if (sheba != "—") {
                                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                    Text(sheba, style = MaterialTheme.typography.labelSmall, color = t.muted)
                                    Text(
                                        text = "کپی شبا",
                                        style = MaterialTheme.typography.labelSmall,
                                        fontWeight = FontWeight.Bold,
                                        color = Color(0xFF10B981),
                                        modifier = Modifier.clickable { onCopy("شماره شبا", sheba) },
                                    )
                                }
                            }
                        }
                    }
                }
            }
            Button(
                onClick = onAddCard,
                shape = RoundedCornerShape(999.dp),
                colors = ButtonDefaults.buttonColors(containerColor = t.primaryAction),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text("+ افزودن کارت بانکی جدید", color = t.onPrimaryAction, fontWeight = FontWeight.Bold)
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun LitheSayadChequeSheet(
    onCopySayadId: () -> Unit,
    onDismiss: () -> Unit,
) {
    val t = CanvasTheme.tokens
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = t.ground,
        shape = RoundedCornerShape(topStart = 32.dp, topEnd = 32.dp),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(horizontal = 20.dp)
                .padding(bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text("برگه چک صیادی بنفش (سامانه صیاد)", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Black, color = t.ink)
            Surface(
                shape = RoundedCornerShape(24.dp),
                color = t.card,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Column(modifier = Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text("شناسه صیاد ۱۶ رقمی:", style = MaterialTheme.typography.labelSmall, color = t.muted)
                        Text("۲۱۰۴ ۹۹۸۲ ۰۰۱۴ ۸۳۹۱", style = figureStyle(Color(0xFF2563EB), FontWeight.Black).copy(fontSize = 14.sp))
                    }
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text("مبلغ چک:", style = MaterialTheme.typography.labelSmall, color = t.muted)
                        Text("۲۵٬۰۰۰٬۰۰۰ تومان (۲۵۰٬۰۰۰٬۰۰۰ ریال)", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.ExtraBold, color = t.ink)
                    }
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text("در وجه:", style = MaterialTheme.typography.labelSmall, color = t.muted)
                        Text("حسین رضایی (اجاره واحد مسکونی)", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold, color = t.ink)
                    }
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text("تاریخ سررسید:", style = MaterialTheme.typography.labelSmall, color = t.muted)
                        Text("۱ خرداد ۱۴۰۴ • بانک سامان", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold, color = Color(0xFFD97706))
                    }
                }
            }
            Button(
                onClick = onCopySayadId,
                shape = RoundedCornerShape(999.dp),
                colors = ButtonDefaults.buttonColors(containerColor = t.primaryAction),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text("کپی شناسه ۱۶ رقمی صیاد", color = t.onPrimaryAction, fontWeight = FontWeight.Bold)
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun LitheQuickAddModalSheet(
    initialMode: QuickAddSheetMode,
    initialIsExpense: Boolean,
    vm: AppVm,
    onAddGoalItem: (String, Long, Long, String) -> Unit,
    onDismiss: () -> Unit,
) {
    val t = CanvasTheme.tokens
    val context = LocalContext.current
    var mode by remember { mutableStateOf(initialMode) }
    var isExpense by remember { mutableStateOf(initialIsExpense) }

    // Form fields
    var titleInput by remember { mutableStateOf("") }
    var amountTomanInput by remember { mutableStateOf("") }
    var secondaryAmountInput by remember { mutableStateOf("") }
    var smsBodyInput by remember { mutableStateOf("") }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = t.ground,
        shape = RoundedCornerShape(topStart = 32.dp, topEnd = 32.dp),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(horizontal = 20.dp)
                .padding(bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text("ثبت سریع هوشمند", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Black, color = t.ink)
                IconButton(onClick = onDismiss) {
                    Icon(Icons.Rounded.Close, contentDescription = "بستن", tint = t.inkSecondary)
                }
            }

            // 6-Tab Mode Selector
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                QuickAddSheetMode.entries.forEach { m ->
                    val sel = mode == m
                    Surface(
                        onClick = { mode = m },
                        shape = RoundedCornerShape(999.dp),
                        color = if (sel) t.primaryAction else t.card,
                    ) {
                        Text(
                            text = m.labelFa,
                            modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            color = if (sel) t.onPrimaryAction else t.inkSecondary,
                        )
                    }
                }
            }

            when (mode) {
                QuickAddSheetMode.TXN -> {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Surface(
                            onClick = { isExpense = true },
                            shape = RoundedCornerShape(999.dp),
                            color = if (isExpense) Color(0xFFEA5C3C) else t.card,
                            modifier = Modifier.weight(1f),
                        ) {
                            Text("ثبت هزینه (خروجی)", modifier = Modifier.padding(vertical = 10.dp), textAlign = TextAlign.Center, color = if (isExpense) Color.White else t.inkSecondary, fontWeight = FontWeight.Bold)
                        }
                        Surface(
                            onClick = { isExpense = false },
                            shape = RoundedCornerShape(999.dp),
                            color = if (!isExpense) Color(0xFF10B981) else t.card,
                            modifier = Modifier.weight(1f),
                        ) {
                            Text("ثبت درآمد (واریز)", modifier = Modifier.padding(vertical = 10.dp), textAlign = TextAlign.Center, color = if (!isExpense) Color.White else t.inkSecondary, fontWeight = FontWeight.Bold)
                        }
                    }
                    OutlinedTextField(
                        value = titleInput,
                        onValueChange = { titleInput = it },
                        label = { Text("عنوان تراکنش یا فروشگاه") },
                        singleLine = true,
                        shape = RoundedCornerShape(16.dp),
                        modifier = Modifier.fillMaxWidth(),
                    )
                    OutlinedTextField(
                        value = amountTomanInput,
                        onValueChange = { amountTomanInput = enDigits(it).filter { c -> c.isDigit() } },
                        label = { Text("مبلغ (تومان)") },
                        visualTransformation = GroupedNumber,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        singleLine = true,
                        shape = RoundedCornerShape(16.dp),
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Button(
                        onClick = {
                            val amtToman = amountTomanInput.toLongOrNull() ?: 0L
                            if (amtToman > 0L) {
                                vm.addManualTransaction(
                                    amountRial = amtToman * 10L,
                                    isExpense = isExpense,
                                    categoryId = null,
                                    merchant = titleInput.ifBlank { if (isExpense) "خرید روزانه" else "واریز نقدی" },
                                    note = "",
                                )
                                Toast.makeText(context, "تراکنش با موفقیت ثبت شد", Toast.LENGTH_SHORT).show()
                                onDismiss()
                            }
                        },
                        shape = RoundedCornerShape(999.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = t.primaryAction),
                        modifier = Modifier.fillMaxWidth().height(50.dp),
                    ) {
                        Text("ذخیره تراکنش", color = t.onPrimaryAction, fontWeight = FontWeight.Bold)
                    }
                }

                QuickAddSheetMode.SMS -> {
                    OutlinedTextField(
                        value = smsBodyInput,
                        onValueChange = { smsBodyInput = it },
                        label = { Text("متن پیامک بانکی را اینجا الصاق کنید") },
                        minLines = 3,
                        shape = RoundedCornerShape(16.dp),
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Button(
                        onClick = {
                            val parsed = parseBankSms("BANK", smsBodyInput, System.currentTimeMillis())
                            if (parsed != null) {
                                val deltaToman = kotlin.math.abs(parsed.delta ?: parsed.balance ?: 0.0).roundToLong()
                                vm.addManualTransaction(
                                    amountRial = deltaToman * 10L,
                                    isExpense = (parsed.delta ?: -1.0) < 0.0,
                                    categoryId = null,
                                    merchant = "پیامک ${parsed.bank.fa}",
                                    note = smsBodyInput.take(80),
                                )
                                Toast.makeText(context, "پیامک بانک ${parsed.bank.fa} استخراج و ثبت شد", Toast.LENGTH_SHORT).show()
                                onDismiss()
                            } else {
                                Toast.makeText(context, "مبلغی در متن پیامک یافت نشد", Toast.LENGTH_SHORT).show()
                            }
                        },
                        shape = RoundedCornerShape(999.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF2563EB)),
                        modifier = Modifier.fillMaxWidth().height(50.dp),
                    ) {
                        Text("پردازش و ثبت خودکار پیامک", color = Color.White, fontWeight = FontWeight.Bold)
                    }
                }

                QuickAddSheetMode.GOAL -> {
                    OutlinedTextField(
                        value = titleInput,
                        onValueChange = { titleInput = it },
                        label = { Text("عنوان هدف پس‌انداز (مثلاً خرید لپ‌تاپ)") },
                        singleLine = true,
                        shape = RoundedCornerShape(16.dp),
                        modifier = Modifier.fillMaxWidth(),
                    )
                    OutlinedTextField(
                        value = amountTomanInput,
                        onValueChange = { amountTomanInput = enDigits(it).filter { c -> c.isDigit() } },
                        label = { Text("مبلغ کل هدف (تومان)") },
                        visualTransformation = GroupedNumber,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        singleLine = true,
                        shape = RoundedCornerShape(16.dp),
                        modifier = Modifier.fillMaxWidth(),
                    )
                    OutlinedTextField(
                        value = secondaryAmountInput,
                        onValueChange = { secondaryAmountInput = enDigits(it).filter { c -> c.isDigit() } },
                        label = { Text("پس‌انداز فعلی (تومان)") },
                        visualTransformation = GroupedNumber,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        singleLine = true,
                        shape = RoundedCornerShape(16.dp),
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Button(
                        onClick = {
                            val target = amountTomanInput.toLongOrNull() ?: 0L
                            val current = secondaryAmountInput.toLongOrNull() ?: 0L
                            if (titleInput.isNotBlank() && target > 0L) {
                                onAddGoalItem(titleInput.trim(), target, current, "long")
                                Toast.makeText(context, "هدف پس‌انداز جدید اضافه شد", Toast.LENGTH_SHORT).show()
                                onDismiss()
                            }
                        },
                        shape = RoundedCornerShape(999.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = t.primaryAction),
                        modifier = Modifier.fillMaxWidth().height(50.dp),
                    ) {
                        Text("ایجاد هدف پس‌انداز", color = t.onPrimaryAction, fontWeight = FontWeight.Bold)
                    }
                }

                QuickAddSheetMode.COMMITMENT -> {
                    OutlinedTextField(
                        value = titleInput,
                        onValueChange = { titleInput = it },
                        label = { Text("عنوان قسط یا چک صیادی") },
                        singleLine = true,
                        shape = RoundedCornerShape(16.dp),
                        modifier = Modifier.fillMaxWidth(),
                    )
                    OutlinedTextField(
                        value = amountTomanInput,
                        onValueChange = { amountTomanInput = enDigits(it).filter { c -> c.isDigit() } },
                        label = { Text("مبلغ کل تعهد (تومان)") },
                        visualTransformation = GroupedNumber,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        singleLine = true,
                        shape = RoundedCornerShape(16.dp),
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Button(
                        onClick = {
                            val amt = amountTomanInput.toLongOrNull() ?: 0L
                            if (titleInput.isNotBlank() && amt > 0L) {
                                vm.saveCommitment(
                                    Commitment(
                                        id = "comm_${System.currentTimeMillis()}",
                                        title = titleInput.trim(),
                                        provider = "بانک / چک صیادی",
                                        kind = CommitmentKind.LOAN,
                                        totalAmountRial = amt * 10L,
                                        remainingAmountRial = amt * 10L,
                                        installmentCount = 12,
                                        paidInstallmentCount = 0,
                                        nextDueTimestamp = System.currentTimeMillis() + 7 * 86_400_000L,
                                    ),
                                )
                                Toast.makeText(context, "تعهد مالی جدید ثبت شد", Toast.LENGTH_SHORT).show()
                                onDismiss()
                            }
                        },
                        shape = RoundedCornerShape(999.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = t.primaryAction),
                        modifier = Modifier.fillMaxWidth().height(50.dp),
                    ) {
                        Text("ثبت قسط / چک", color = t.onPrimaryAction, fontWeight = FontWeight.Bold)
                    }
                }

                QuickAddSheetMode.HOLDING -> {
                    OutlinedTextField(
                        value = titleInput,
                        onValueChange = { titleInput = it },
                        label = { Text("مقدار طلای ۱۸ عیار (گرم)") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        singleLine = true,
                        shape = RoundedCornerShape(16.dp),
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Button(
                        onClick = {
                            val grams = enDigits(titleInput).toDoubleOrNull() ?: 0.0
                            if (grams > 0.0) {
                                vm.setHolding(newHoldingId(), "gold18", grams)
                                Toast.makeText(context, "${faDigits(grams.toString())} گرم طلا به سبد دارایی اضافه شد", Toast.LENGTH_SHORT).show()
                                onDismiss()
                            }
                        },
                        shape = RoundedCornerShape(999.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = t.primaryAction),
                        modifier = Modifier.fillMaxWidth().height(50.dp),
                    ) {
                        Text("افزودن به سبد دارایی", color = t.onPrimaryAction, fontWeight = FontWeight.Bold)
                    }
                }

                QuickAddSheetMode.CARD -> {
                    OutlinedTextField(
                        value = titleInput,
                        onValueChange = { titleInput = it },
                        label = { Text("نام بانک یا کارت (مثلاً بانک سامان)") },
                        singleLine = true,
                        shape = RoundedCornerShape(16.dp),
                        modifier = Modifier.fillMaxWidth(),
                    )
                    OutlinedTextField(
                        value = amountTomanInput,
                        onValueChange = { amountTomanInput = enDigits(it).filter { c -> c.isDigit() }.take(16) },
                        label = { Text("شماره ۱۶ رقمی کارت") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        singleLine = true,
                        shape = RoundedCornerShape(16.dp),
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Button(
                        onClick = {
                            Toast.makeText(context, "کارت بانکی در کیف کارت ذخیره شد", Toast.LENGTH_SHORT).show()
                            onDismiss()
                        },
                        shape = RoundedCornerShape(999.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = t.primaryAction),
                        modifier = Modifier.fillMaxWidth().height(50.dp),
                    ) {
                        Text("ذخیره کارت بانکی", color = t.onPrimaryAction, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}
