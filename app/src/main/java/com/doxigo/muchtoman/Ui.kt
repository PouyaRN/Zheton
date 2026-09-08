package com.doxigo.muchtoman

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.activity.compose.LocalActivity
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.windowInsetsTopHeight
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.TextAutoSize
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.DateRange
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.pulltorefresh.PullToRefreshDefaults
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.drawscope.inset
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.OffsetMapping
import androidx.compose.ui.text.input.TransformedText
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextDirection
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.Velocity
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.view.WindowCompat
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import coil3.compose.AsyncImage
import coil3.compose.AsyncImagePainter
import coil3.decode.DataSource
import coil3.request.ImageRequest
import coil3.request.crossfade
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.abs

/** Groups the integer part as she types and shows Persian digits, caret intact. */
internal val GroupedNumber = VisualTransformation { text ->
    val g = groupDigits(text.text)
    TransformedText(
        AnnotatedString(g.text),
        object : OffsetMapping {
            override fun originalToTransformed(offset: Int) =
                g.origToDisp[offset.coerceIn(0, g.origToDisp.lastIndex)]

            override fun transformedToOriginal(offset: Int) =
                g.dispToOrig[offset.coerceIn(0, g.dispToOrig.lastIndex)]
        },
    )
}

@Composable
internal fun Modifier.panel(): Modifier = this
    .fillMaxWidth()
    .clip(RoundedCornerShape(Radius.card))
    .background(MaterialTheme.colorScheme.surfaceContainer)
    .border(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f), RoundedCornerShape(Radius.card))
    .padding(Space.l)

@Composable
internal fun Panel(
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(modifier.panel(), content = content)
}

/**
 * The one inset from the screen edge. The hero field ignores it on purpose — it is the only
 * thing in the app that runs to the glass — so every other item in the list applies it itself.
 */
private val edge = PaddingValues(horizontal = Space.xl)

/**
 * Where a row sits in its band. Rows in the middle are square so they read as one object with
 * its neighbours rather than as a card that happens to be adjacent to another card.
 */
internal fun bandShape(index: Int, count: Int): Shape {
    val r = Radius.group
    return when {
        count == 1 -> RoundedCornerShape(r)
        index == 0 -> RoundedCornerShape(topStart = r, topEnd = r)
        index == count - 1 -> RoundedCornerShape(bottomStart = r, bottomEnd = r)
        else -> RoundedCornerShape(0.dp)
    }
}

/**
 * What the edit sheet is open on: an existing row, by its [key], or — when she picked an asset
 * to add — a key nothing holds yet, which is what lets a second تتر be added beside the first
 * instead of opening it.
 */
private data class Editing(val typeId: String, val key: String)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppRoot(vm: AppVm, state: UiState, activity: FragmentActivity) {
    var adding by remember { mutableStateOf(false) }
    var editing by remember { mutableStateOf<Editing?>(null) }
    var settings by remember { mutableStateOf(false) }
    var banks by remember { mutableStateOf(false) }
    var transactionRef by rememberSaveable { mutableStateOf<String?>(null) }
    var selectedObligation by remember { mutableStateOf<InstallmentObligation?>(null) }
    var addingObligation by remember { mutableStateOf(false) }
    var addingReservation by remember { mutableStateOf(false) }
    var categoriesManaged by remember { mutableStateOf(false) }
    var timelineCategoryId by rememberSaveable { mutableStateOf<String?>(null) }
    var timelineSearchQuery by rememberSaveable { mutableStateOf<String?>(null) }
    var currencyLens by rememberSaveable { mutableStateOf(CurrencyDenominator.TOMAN) }
    var privacyMode by rememberSaveable { mutableStateOf(false) }
    var showGoldBubbleSheet by remember { mutableStateOf(false) }
    var showRecurringSheet by remember { mutableStateOf(false) }
    var showWishlistSheet by remember { mutableStateOf(false) }
    var showQuickAdd by remember { mutableStateOf(false) }
    var showCommitmentsHubSheet by remember { mutableStateOf(false) }
    var showAddCommitmentSheet by remember { mutableStateOf(false) }
    var showExecutiveDeckSheet by remember { mutableStateOf(false) }

    // The permanent-delete undo door. One host, and the window's lifetime rides on the state
    // itself: when it nulls (the window elapsed or she hit Undo) the snackbar just leaves.
val snackbarHostState = remember { SnackbarHostState() }
    LaunchedEffect(state.pendingUndo) {
        val pending = state.pendingUndo ?: return@LaunchedEffect
        val result = snackbarHostState.showSnackbar(
            message = pending.label,
            actionLabel = "بازگردانی",
            duration = SnackbarDuration.Long,
        )
        if (result == SnackbarResult.ActionPerformed) vm.undoPermanentDelete()
    }

    // One tab, not three booleans. Three could be true at once, and were: every screen was an
    // overlay with its own «بستن», so an overlay opened from an overlay left her somewhere the
    // only way out of was the system back button. A single value cannot represent that state.
    // First of [tabs], not Tab.HOME: in the lite edition there is no home to start on, and the
    // one tab it has is where it opens.
    var tab by rememberSaveable { mutableStateOf(tabs.first()) }
    val portfolio = tab == Tab.ASSETS

    // Leaving the app re-arms the lock, so coming back asks again — and coming back also
    // refetches rates if the ones on screen are older than the Worker's cache window.
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_STOP -> vm.relock()
                Lifecycle.Event.ON_START -> vm.refreshIfStale()
                else -> Unit
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    // What is behind the status bar decides the colour of the clock and the signal icons, and
    // on this screen that changes as she scrolls: the field is dark in both themes, the paper
    // under it is not. Forcing light icons for the whole list put a white clock on cream once
    // the field scrolled away; following the theme put a black one on the field. So follow
    // whichever is actually up there. The report and settings pages start at the background
    // and simply follow the theme, as does the navigation bar, which never leaves it.
    val listState = rememberLazyListState()
    val heroUnderStatusBar by remember {
        derivedStateOf { listState.firstVisibleItemIndex == 0 }
    }
    // The tabs that start at the paper rather than at the field, plus every pushed screen.
    val paperUnderStatusBar = !state.locked && (
        settings || transactionRef != null ||
            tab == Tab.LEDGER || tab == Tab.REPORTS || tab == Tab.ASSETS ||
            !heroUnderStatusBar
        )
    val lightScheme = MaterialTheme.colorScheme.background.luminance() > 0.5f
    LaunchedEffect(paperUnderStatusBar, lightScheme) {
        WindowCompat.getInsetsController(activity.window, activity.window.decorView).apply {
            isAppearanceLightStatusBars = paperUnderStatusBar && lightScheme
            isAppearanceLightNavigationBars = lightScheme
        }
    }

    if (state.locked) {
        // Prompt immediately; the button is there for when it is dismissed.
        LaunchedEffect(Unit) { promptUnlock(activity) { vm.unlock() } }
        Surface(color = MaterialTheme.colorScheme.background) {
            LockScreen(onUnlock = { promptUnlock(activity) { vm.unlock() } })
        }
        return
    }

    if (settings) {
        // The system back button must step back to the list, not out of the app.
        BackHandler { settings = false }
        SettingsScreen(
            name = state.name,
            themeMode = state.themeMode,
            lockEnabled = state.lockEnabled,
            widgetLock = state.widgetLock,
            smsEnabled = state.smsEnabled,
            bankAccounts = state.bankAccounts,
            disabledBanks = state.disabledBanks,
            activity = activity,
            onNameChange = vm::setName,
            onThemeChange = vm::setThemeMode,
            onSmsChange = vm::setSmsEnabled,
            onBankChange = vm::setBankEnabled,
            onLockChange = { on ->
                if (on) promptUnlock(activity) { vm.setLockEnabled(true) }
                else vm.setLockEnabled(false)
            },
            onWidgetLockChange = vm::setWidgetLock,
            hourlyWageToman = state.hourlyWageToman,
            onHourlyWageChange = vm::setHourlyWageToman,
            cycleMode = state.cycleMode,
            cycleAnchorDay = state.cycleAnchorDay,
            onCycleModeChange = vm::setCycleMode,
            onCycleAnchorChange = vm::setCycleAnchorDay,
            onForceRescanSms = { days -> vm.forceRescanSms(days) },
            onCategoriesManage = { settings = false; categoriesManaged = true },
            onBack = { settings = false },
        )
        return
    }

    if (categoriesManaged) {
        BackHandler { categoriesManaged = false }
        CategoriesScreen(
            categories = state.ledger.categories,
            hiddenCategories = state.hiddenCategories,
            categoryCounts = state.ledger.categoryCounts,
            onAdd = vm::addCategory,
            onEdit = vm::editCategory,
            onDelete = { categoryId, targetId -> vm.deleteCategory(categoryId, targetId) },
            onToggleHidden = vm::setCategoryHidden,
            onBack = { categoriesManaged = false },
        )
        return
    }

    // Report screen retired — wealth trend now lives in the Reports tab.
    // All report entry points navigate to Tab.REPORTS instead.

    transactionRef?.let { ref ->
        val entry = state.ledger.entries.firstOrNull { it.txn.ref == ref }
            ?: state.ledger.hiddenEntries.firstOrNull { it.txn.ref == ref }
        entry?.let { entry ->
            BackHandler { transactionRef = null }
            val existingNote = vm.getNote(entry.txn.ref)
            TransactionScreen(
                entry = entry,
                categories = state.ledger.categories,
                hiddenCategories = state.hiddenCategories,
                existingNote = existingNote,
                onCategorise = vm::categorise,
                onSaveNote = vm::saveNote,
                onPinMerchant = { norm, display -> vm.pinMerchantAlias(norm, display) },
                onSetDirection = { e, direction -> vm.setTransactionDirection(e, direction) },
                onHide = { e, hidden -> vm.setTransactionHidden(e, hidden) },
                onUnpinCategory = vm::unpinCategory,
                onDeletePermanently = { e -> vm.deletePermanently(listOf(e)) },
                onLoadSource = vm::sourceOf,
                onToggleTransfer = { e -> vm.toggleTransfer(e) },
                gold18RateToman = state.effective["gold18"],
                usdtRateToman = state.effective["usdt"] ?: state.effective["usd"],
                hourlyWageToman = state.hourlyWageToman,
                allEntries = state.ledger.entries,
                onBack = { transactionRef = null },
            )
            return
        }
    }

    selectedObligation?.let { obligation ->
        BackHandler { selectedObligation = null }
        val payments = state.obligationPayments[obligation.id] ?: emptyList()
        ObligationDetailScreen(
            obligation = obligation,
            payments = payments,
            onMarkPaid = { paymentId -> vm.markInstallmentPaid(paymentId) },
            onPayOffRemaining = { vm.payOffRemaining(obligation.id) },
            onArchive = { vm.archiveObligation(obligation.id); selectedObligation = null },
            onBack = { selectedObligation = null },
        )
        return
    }

    // Credit purchase form — opened from the transaction menu.
    vm.markingCredit?.let { entry ->
        BackHandler { vm.cancelMarkCredit() }
        CreditPurchaseForm(
            entry = entry,
            onSave = { provider, total, installments, amount, startDate, notes ->
                vm.saveObligation(
                    provider = provider,
                    totalRial = total,
                    downPaymentRial = kotlin.math.abs(entry.txn.signedRial ?: entry.txn.amountRial ?: 0L),
                    installmentCount = installments,
                    installmentRial = amount,
                    startDate = startDate,
                    notes = notes,
                    linkedTxnRef = entry.txn.ref,
                )
                vm.cancelMarkCredit()
            },
            onDismiss = { vm.cancelMarkCredit() },
        )
        return
    }

    // Link-to-installment picker — opened from the transaction menu.
    vm.linkingInstallment?.let { entry ->
        BackHandler { vm.cancelLinkInstallment() }
        LinkInstallmentPicker(
            entry = entry,
            state = state,
            onLink = { paymentId ->
                vm.markInstallmentPaid(paymentId, entry.txn.ref)
                vm.cancelLinkInstallment()
            },
            onDismiss = { vm.cancelLinkInstallment() },
        )
        return
    }

    // Direct obligation creation form — opened from obligations card.
    if (addingObligation) {
        BackHandler { addingObligation = false }
        DirectObligationSheet(
            onSave = { provider, total, installments, amount, startDate, notes ->
                vm.saveObligation(
                    provider = provider,
                    totalRial = total,
                    downPaymentRial = 0L,
                    installmentCount = installments,
                    installmentRial = amount,
                    startDate = startDate,
                    notes = notes,
                    linkedTxnRef = "",
                )
                addingObligation = false
            },
            onDismiss = { addingObligation = false },
        )
        return
    }

    // Add reservation form — opened from home screen button.
    if (addingReservation) {
        BackHandler { addingReservation = false }
        AddReservationForm(
            categories = state.ledger.categories,
            onSave = { purpose, amount, categoryId ->
                vm.saveReservation(purpose, amount * 10, categoryId)
                addingReservation = false
            },
            onDismiss = { addingReservation = false },
        )
        return
    }

    if (showGoldBubbleSheet) {
        GoldBubbleSheet(
            effectiveRates = state.effective,
            onDismiss = { showGoldBubbleSheet = false },
        )
    }
    if (showRecurringSheet) {
        RecurringExpensesSheet(
            entries = state.ledger.entries,
            onDismiss = { showRecurringSheet = false },
        )
    }
    if (showWishlistSheet) {
        val liquidCashToman = state.bankToman.toLong()
        val upcomingObligationsToman = (state.obligations.filter { !it.archived }.sumOf { obligation ->
            val paid = state.obligationPayments[obligation.id]?.count { it.paidDate != null } ?: 0
            val unpaidCount = (obligation.installmentCount - paid).coerceAtLeast(0)
            unpaidCount.toLong() * obligation.installmentRial
        } / 10L)
        val reservationsToman = (state.reservations.filter { !it.released }.sumOf { it.amountRial } / 10L)
        val safeSpendResult = remember(liquidCashToman, upcomingObligationsToman, reservationsToman) {
            calculateSafeToSpend(
                liquidCashToman = liquidCashToman,
                upcomingObligationsToman = upcomingObligationsToman,
                reservationsToman = reservationsToman,
            )
        }
        WishlistSheet(
            dailySafeSpendToman = safeSpendResult.dailyAllowanceToman,
            onDismiss = { showWishlistSheet = false },
        )
    }

    if (showQuickAdd) {
        ManualTxnSheet(
            categories = state.ledger.categories,
            onDismiss = { showQuickAdd = false },
            onSave = { amountRial, isExpense, catId, merchant, note, at ->
                vm.addManualTransaction(amountRial, isExpense, catId, merchant, note, at)
                showQuickAdd = false
            },
        )
    }

    if (showCommitmentsHubSheet) {
        CommitmentsHubSheet(
            commitments = state.commitments,
            onDismiss = { showCommitmentsHubSheet = false },
            onAdd = { showAddCommitmentSheet = true },
            onToggleSettled = { vm.toggleCommitmentSettled(it) },
            onDelete = { vm.deleteCommitment(it) },
            privacyMode = privacyMode,
        )
    }

    if (showAddCommitmentSheet) {
        AddCommitmentSheet(
            onDismiss = { showAddCommitmentSheet = false },
            onSave = { commitment ->
                vm.saveCommitment(commitment)
                showAddCommitmentSheet = false
            },
        )
    }

    if (showExecutiveDeckSheet) {
        ModalBottomSheet(
            onDismissRequest = { showExecutiveDeckSheet = false },
            containerColor = MaterialTheme.colorScheme.surface,
        ) {
            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = Space.m)
                    .padding(bottom = Space.xl),
            ) {
                item {
                    HomeFinancialExecutiveDeck(
                        state = state,
                        privacyMode = privacyMode,
                        onOpenGoldBubble = {
                            showExecutiveDeckSheet = false
                            showGoldBubbleSheet = true
                        },
                        onOpenRecurring = {
                            showExecutiveDeckSheet = false
                            showCommitmentsHubSheet = true
                        },
                        onOpenWishlist = {
                            showExecutiveDeckSheet = false
                            showWishlistSheet = true
                        },
                    )
                }
            }
        }
    }

    // Anywhere but home, back goes home. This is the whole reason the bar exists: before it,
    // there was no way out of a screen except a «بستن» she had to find in the corner. The lite
    // edition is one tab, so it is already home and back means back, as it did in v1.0.4.
    if (tab != tabs.first()) BackHandler { tab = tabs.first() }

    // Merging 250 coin rates with the overrides is not free, and it is the same map for every
    // row and every section head on screen. Read it once per pass, not once per card.
    val effective = state.effective
    val dynamicTypes = remember(state.coins, state.stocks) {
        (state.coins.map { it.toAssetType() } + state.stocks.map { it.toAssetType() })
            .associateBy { it.id }
    }
    val holdingSections = remember(state.listHoldings, dynamicTypes) {
        holdingsByKind(state.listHoldings, dynamicTypes)
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        // The hero field runs off the top of the screen and under the status bar, so the
        // Scaffold must not hold the content down below it. Every child that needs the inset
        // takes it itself — the field with statusBarsPadding, the bar below with its own.
        contentWindowInsets = WindowInsets(0),
        snackbarHost = {
            SnackbarHost(snackbarHostState)
        },
        bottomBar = {
            // A full-width «اضافه کردن دارایی» used to sit above this, and the + in the field
            // already opens the same picker on this tab. Two buttons for one action, one of
            // them a gold slab against a gold slug, and the bar is where she looks anyway.
            if (tabs.size > 1) {
                TabBar(
                    selected = tab,
                    ledgerBadge = 0,
                    onSelect = { tab = it },
                    onQuickAdd = { showQuickAdd = true },
                )
            } else {
                // The lite edition has no bar, but the Scaffold's inset is the only thing keeping
                // the last holding clear of the gesture pill, so something still has to stand
                // there and be exactly that tall.
                Spacer(Modifier.fillMaxWidth().navigationBarsPadding())
            }
        },
    ) { pad ->
      // The tabs that are their own screens. They sit inside the Scaffold rather than
      // returning early, so the bar stays under them and there is always a way out.
      if (tab == Tab.LEDGER) {
        val activity = LocalActivity.current as? FragmentActivity
        TimelineScreen(
            ledger = state.ledger,
            state = state,
            bottomInset = pad.calculateBottomPadding(),
            onOpen = { transactionRef = it.txn.ref },
            onMarkCredit = { vm.startMarkCredit(it) },
            onLinkInstallment = { vm.startLinkInstallment(it) },
            onExport = { activity?.let { ctx -> vm.exportCsv(ctx) } },
            initialCategoryId = timelineCategoryId,
            initialSearchQuery = timelineSearchQuery,
            onRefresh = { vm.refreshLedger() },
            onAddManual = { amountRial, isExpense, catId, merchant, note, at ->
                vm.addManualTransaction(amountRial, isExpense, catId, merchant, note, at)
            },
            onBulkCategorise = vm::bulkCategorise,
            onBulkHide = vm::bulkHide,
            onBulkFlip = vm::bulkFlipDirection,
            onBulkDelete = vm::bulkDelete,
            onBulkNote = vm::bulkSaveNote,
            onBulkMerchant = vm::bulkPinMerchant,
            onHide = { entry, hidden -> vm.setTransactionHidden(entry, hidden) },
            onToggleShowHidden = { vm.setShowHidden(!state.showHidden) },
            onToggleTransfer = { entry -> vm.toggleTransfer(entry) },
            onFilterWide = { wide -> vm.setFilterWide(wide) },
            privacyMode = privacyMode,
        )
        return@Scaffold
      }
      if (tab == Tab.REPORTS) {
        val composition = remember(state.listHoldings, state.coins, state.effective, state.stocks) {
            compositionByKind(state.listHoldings, state.coins, state.effective, state.stocks)
        }
        ReportsTab(
            ledger = state.ledger,
            history = state.history,
            current = state.totals.toman,
            composition = composition,
            bankAccounts = state.bankAccounts,
            privacyMode = privacyMode,
            onSelectCategory = { catId ->
                timelineCategoryId = catId
                timelineSearchQuery = null
                tab = Tab.LEDGER
            },
            bottomInset = pad.calculateBottomPadding(),
            cycle = CycleSpec(state.cycleMode, state.cycleAnchorDay),
        )
        return@Scaffold
      }
      if (tab == Tab.ASSETS) {
        MarketAndAssetsScreen(
            state = state,
            effective = effective,
            onRefresh = { vm.refreshAll() },
            onAddHolding = { adding = true; vm.refreshStocksForPicker() },
            onEditHolding = { h -> editing = Editing(h.typeId, h.key) },
            bottomInset = pad.calculateBottomPadding(),
        )
        return@Scaffold
      }

      Box(Modifier.fillMaxSize()) {
        // Pulling the list down refetches, exactly like the به‌روزرسانی button — the button
        // stays, because a labelled control is still the one she can be told about by phone.
        // The pull indicator belongs to the pull alone: init and refreshIfStale() also set
        // loading, and ungated they dropped an uninvited spinner over the header on every
        // cold open. The card's own labelled spinner is the receipt for those fetches.
        var pulled by remember { mutableStateOf(false) }
        LaunchedEffect(state.refreshing) { if (!state.refreshing) pulled = false }
        val pullState = rememberPullToRefreshState()
        PullToRefreshBox(
            isRefreshing = state.refreshing && pulled,
            onRefresh = { pulled = true; vm.refreshAll() },
            state = pullState,
            // The default indicator is a light chip, and it lands on the dark green field.
            // Dressed in the field's own colours instead, and pushed clear of the status bar.
            indicator = {
                PullToRefreshDefaults.Indicator(
                    state = pullState,
                    isRefreshing = state.refreshing && pulled,
                    containerColor = Hero.top,
                    color = Hero.gold,
                    modifier = Modifier
                        .align(Alignment.TopCenter)
                        .statusBarsPadding(),
                )
            },
            modifier = Modifier
                .padding(pad)
                .fillMaxSize(),
        ) {
        // No horizontal contentPadding and no vertical arrangement: the hero field is
        // full-bleed and the rows in a band have to sit flush against each other, so every
        // item owns its own inset. `edge` is the one place that number lives.
        LazyColumn(
            state = listState,
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(bottom = pad.calculateBottomPadding() + 100.dp),
        ) {
            item(key = "hero") {
                HeroField(
                    state = state,
                    usdRate = effective["usd"],
                    onRefresh = vm::refreshAll,
                    onReport = { tab = Tab.REPORTS },
                    onSettings = { settings = true },
                    onAdd = { adding = true; vm.refreshStocksForPicker() },
                    portfolio = portfolio,
                    currencyLens = currencyLens,
                    onCurrencyLensChange = { currencyLens = it },
                    privacyMode = privacyMode,
                    onTogglePrivacy = { privacyMode = !privacyMode },
                )
            }

            // Interactive 3D Iranian Bank Card Deck — front and center
            if (state.bankAccounts.isNotEmpty()) {
                item(key = "bank_card_deck") {
                    Box(modifier = Modifier.padding(edge).padding(top = Space.l)) {
                        BankCardDeck(
                            accounts = state.bankAccounts,
                            cardHolderName = state.name,
                            onCardClick = { banks = true },
                            privacyMode = privacyMode,
                        )
                    }
                }
            }

            // Sleek Financial Health & Resilience Banner (Decluttered Home Dashboard)
            item(key = "home_financial_health_banner") {
                Box(modifier = Modifier.padding(edge).padding(top = Space.l)) {
                    CompactFinancialHealthBanner(
                        state = state,
                        privacyMode = privacyMode,
                        onOpenDetails = { showExecutiveDeckSheet = true },
                        onOpenReports = { tab = Tab.REPORTS },
                    )
                }
            }

            // Active Commitments Hub (BNPL, Loans, Sayad Checks)
            if (state.commitments.isNotEmpty()) {
                item(key = "commitments_card") {
                    CommitmentsCard(
                        commitments = state.commitments,
                        modifier = Modifier.padding(edge).padding(top = Space.l),
                        onOpenHub = { showCommitmentsHubSheet = true },
                        onAddCommitment = { showAddCommitmentSheet = true },
                        privacyMode = privacyMode,
                    )
                }
            }

            // Active Obligations — only rendered when there are active obligations to attend to
            val activeObligations = state.obligations.filter { !it.archived }
            if (activeObligations.isNotEmpty()) {
                item(key = "obligations") {
                    ObligationsCard(
                        state = state,
                        modifier = Modifier.padding(edge).padding(top = Space.l),
                        onOpenObligation = { selectedObligation = it },
                        onAddObligation = { addingObligation = true },
                    )
                }
            }

            // Active Reservations — only rendered when there are funds actively reserved
            val activeReservations = state.reservations.filter { !it.released }
            if (activeReservations.isNotEmpty()) {
                item(key = "reservations") {
                    ReservationsCard(
                        state = state,
                        modifier = Modifier.padding(edge).padding(top = Space.l),
                        onRelease = vm::releaseReservation,
                        onDelete = vm::deleteReservation,
                    )
                }
            }

            if (!portfolio) {
                // The story, and then it stops. One meaningful change, the month's flow, the
                // runway, and at most one thing asking for her — the inventory is behind the
                // row at the bottom, because what she opened the app for is how things are
                // going, not a list of what she owns.
                val story = state.story
                // The month itself is on the field now. What is left down here is the one case
                // the field cannot carry: nothing recorded yet, which is not a status but a
                // thing to do — switch the messages on — and a task does not belong on the hero.
                if (story.month.transactions == 0) {
                    item(key = "flow") {
                        Box(Modifier.padding(edge).padding(top = Space.l)) {
                            QuietStart(state.smsEnabled)
                        }
                    }
                }
                // Side by side, and each without its «why»: this is the glance surface, and the
                // sentence behind the sentence is on the report where there is room to read it.
                // A lone card of the two still takes the whole row.
                if (story.headline != null || story.attention != null) {
                    item(key = "story") {
                        Row(
                            Modifier
                                .padding(edge)
                                .padding(top = Space.m)
                                .height(IntrinsicSize.Min),
                            horizontalArrangement = Arrangement.spacedBy(Space.m),
                        ) {
                            story.headline?.let {
                                InsightCard(it, Modifier.weight(1f).fillMaxHeight())
                            }
                            story.attention?.let {
                                // This one leaves the screen, so it says where it goes.
                                InsightCard(
                                    it,
                                    Modifier.weight(1f).fillMaxHeight(),
                                    action = "دفتر رو باز کن",
                                    onAction = { tab = Tab.LEDGER },
                                )
                            }
                        }
                    }
                }
                if (state.totals.missing.isNotEmpty()) {
                    item(key = "missing") {
                        MissingNote(state.totals.missing, state.coins, state.stocks)
                    }
                }

                // The two doors feedback said were buried: registering what she owns, and the
                // installments hub. Chips, not cards — they are entrances, not content, and
                // they sit after the story so the glance stays first.
                item(key = "quick_actions") {
                    Row(
                        Modifier
                            .padding(edge)
                            .padding(top = Space.m),
                        horizontalArrangement = Arrangement.spacedBy(Space.m),
                    ) {
                        HomeQuickAction(
                            label = "ثبت دارایی",
                            icon = Icons.Rounded.Add,
                            onClick = { adding = true; vm.refreshStocksForPicker() },
                            modifier = Modifier.weight(1f),
                        )
                        HomeQuickAction(
                            label = "مدیریت اقساط",
                            icon = Icons.Rounded.DateRange,
                            onClick = { showCommitmentsHubSheet = true },
                            modifier = Modifier.weight(1f),
                        )
                    }
                }
            } else if (state.listHoldings.isEmpty()) {
                item { EmptyHint() }
            } else {
                // Dollars, gold, coins and crypto used to arrive as one undifferentiated stack
                // of cards. Banding them by kind separates them without moving anything: the
                // sections come out in the order the holdings are already stored in.
                val sections = holdingSections
                sections.forEach { (kind, held) ->
                    // A single section is not a separation, and its subtotal would only be the
                    // hero total said twice.
                    if (sections.size > 1) {
                        item(key = "kind_${kind.name}") {
                            // The same function the hero total uses, so the sections always add
                            // up to it: set-aside and unpriced holdings fall out of both alike,
                            // and no section can show money the total above it does not have.
                            val sum = computeTotals(held, effective)
                            // …which is also why the figure counts contributors and not rows.
                            // A sum of one is not a sum, it is the card below it said twice —
                            // and one live row beside a set-aside one is still a sum of one.
                            val counted = held.count { !it.excluded } - sum.missing.size
                            SectionHead(
                                title = kind.fa,
                                subtotal = sum.toman.takeIf { counted > 1 },
                                modifier = Modifier.animateItem(),
                            )
                        }
                    } else {
                        item(key = "band_top") { Spacer(Modifier.height(Space.xl)) }
                    }
                    itemsIndexed(held, key = { _, h -> h.key }) { i, h ->
                        val type = resolveType(h.typeId, dynamicTypes)
                        HoldingRow(
                            type = type,
                            name = h.nameOr(type.fa),
                            amount = h.amount,
                            rate = effective[h.typeId],
                            excluded = h.excluded,
                            // The bank row is not hers to edit — its amount comes from the
                            // messages — so it opens the accounts behind it instead.
                            onClick = {
                                if (h.typeId == BANK_ID) banks = true
                                else editing = Editing(h.typeId, h.key)
                            },
                            note = if (h.typeId == BANK_ID) bankNote(state) else null,
                            wallet = h.wallet,
                            walletRefreshing = h.key in state.refreshingWallets,
                            walletError = state.walletErrors[h.key],
                            // A band is one object with rows inside it, not a stack of cards:
                            // only its outer corners are round, and the rows are divided by a
                            // hairline that starts where the text does.
                            shape = bandShape(i, held.size),
                            divided = i < held.size - 1,
                            modifier = Modifier.animateItem(),
                        )
                    }
                }
            }

            if (portfolio && state.totals.missing.isNotEmpty()) {
                item { MissingNote(state.totals.missing, state.coins, state.stocks) }
            }
        }
        }

        // Once the field has scrolled off, it is paper up there and the list's own headings
        // were sliding under the clock. A strip in the background colour, arriving exactly
        // when the status-bar icons flip, gives them something to sit on. It cannot simply
        // always be there: while the field is up top, this would be a pale band across it.
        val strip by animateFloatAsState(
            if (paperUnderStatusBar) 1f else 0f,
            animationSpec = tween(Motion.fast),
            label = "statusStrip",
        )
        Box(
            Modifier
                .fillMaxWidth()
                .windowInsetsTopHeight(WindowInsets.statusBars)
                .alpha(strip)
                .background(MaterialTheme.colorScheme.background),
        )
      }
    }

    if (banks) {
        BankSheet(
            accounts = state.bankAccounts,
            disabled = state.disabledBanks,
            strangers = state.strangeSenders,
            userName = state.name,
            onAnchor = vm::setBankBalance,
            onToggle = vm::setBankEnabled,
            onForget = vm::forgetBankAccount,
            onUpdateDetails = vm::updateAccountDetails,
            onAddNumber = vm::addBankNumber,
            onDismissSender = vm::dismissSender,
            onRescan = vm::rescanSms,
            onDismiss = { banks = false },
        )
    }

    if (adding) {
        val available = remember(state.coins, state.stocks) { catalog(state.coins, state.stocks) }
        PickTypeSheet(
            all = available,
            already = state.holdings.map { it.typeId }.toSet(),
            onDismiss = { adding = false },
            // A fresh key every time: picking تتر when she already holds some opens an empty
            // sheet for a second one, rather than her existing balance.
            onPick = { adding = false; editing = Editing(it, newHoldingId()) },
        )
    }

    editing?.let { (typeId, key) ->
        val held = state.holdings.firstOrNull { it.key == key }
        EditSheet(
            key = key,
            type = resolveType(typeId, dynamicTypes),
            holding = held,
            rate = effective[typeId],
            isOverridden = state.overrides.containsKey(typeId),
            excluded = held?.excluded ?: false,
            walletBusy = key in state.refreshingWallets,
            walletError = state.walletErrors[key],
            onWalletEdit = { vm.clearWalletError(key) },
            onExcluded = { on -> vm.setExcluded(key, on) },
            onSaveManual = { amount -> vm.setHolding(key, typeId, amount); editing = null },
            onSaveWallet = { option, address, onSuccess ->
                vm.connectWallet(key, typeId, option, address, onSuccess)
            },
            onDelete = { vm.removeHolding(key); editing = null },
            onRate = { r -> vm.setOverride(typeId, r) },
            onLabel = { name -> vm.setLabel(key, name) },
            onDismiss = { editing = null },
        )
    }
}

/**
 * The reason the app exists, and now the ground the app stands on rather than a card floating
 * on it: a deep green field that runs to both edges and off the top of the screen, with the
 * total set in gold across it. Everything below defers to it.
 */
@Composable
internal fun HeroPanel(
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit,
) {
    val topColor = Hero.top
    val bottomColor = Hero.bottom
    val goldColor = Hero.gold
    Box(
        modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(bottomStart = Radius.hero, bottomEnd = Radius.hero))
            .background(Brush.verticalGradient(listOf(topColor, bottomColor))),
    ) {
        Canvas(Modifier.matchParentSize()) {
            drawRect(
                Brush.radialGradient(
                    colors = listOf(goldColor.copy(alpha = 0.13f), Color.Transparent),
                    center = Offset(size.width * 0.88f, size.height * -0.05f),
                    radius = size.width * 0.95f,
                ),
            )
        }
        Column(
            Modifier
                .statusBarsPadding()
                .padding(start = Space.xl, end = Space.xl, top = Space.s, bottom = Space.xl),
            content = content,
        )
    }
}

@Composable
internal fun HeroField(
    state: UiState,
    usdRate: Double?,
    onRefresh: () -> Unit,
    onReport: () -> Unit,
    onSettings: () -> Unit,
    onAdd: () -> Unit,
    /** Which screen this field is heading — the one thing that changes below the total. */
    portfolio: Boolean,
    currencyLens: CurrencyDenominator = CurrencyDenominator.TOMAN,
    onCurrencyLensChange: (CurrencyDenominator) -> Unit = {},
    privacyMode: Boolean = false,
    onTogglePrivacy: () -> Unit = {},
) {
    val total = state.totals.toman

    // The same money in the one other unit everyone here already thinks in. It comes from the
    // *effective* dollar rate, so a hand-typed override moves this figure with everything else,
    // and it is simply absent when there is no dollar rate — a converted total is only ever as
    // honest as the rate underneath it.
    val usd = usdRate?.takeIf { it > 0.0 && total > 0.0 }?.let { total / it }

    // "همین الان" must not still say that half an hour later. A slow tick keeps the label
    // honest; the minute granularity of faAgo means nothing finer would ever show anyway.
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(state.rates.updatedAt) {
        while (true) {
            now = System.currentTimeMillis()
            delay(30_000)
        }
    }
    // A day-old rate silently shown as current is the "quietly wrong total" this app is
    // built to avoid. Words carry the warning, not colour alone.
    val stale = state.rates.updatedAt > 0L && now - state.rates.updatedAt > 24 * 60 * 60_000L

    // A month is the shortest window the daily snapshots can answer honestly, and the one she
    // is most likely to be asking about. Absent — not zeroed — until there is a snapshot that
    // old to compare against.
    val change = remember(state.history, total) {
        changeOver(state.history, System.currentTimeMillis() / DAY_MS, 30, total)
    }

    HeroPanel {
            Row(verticalAlignment = Alignment.CenterVertically) {
                val context = LocalContext.current
                Text(
                    // The name turns the header into a greeting; without one the app just
                    // says what it is.
                    if (state.name.isNotBlank()) "سلام، ${state.name}" else "چقدر تومن",
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold,
                    color = Hero.muted,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier
                        .weight(1f)
                        .pointerInput(privacyMode) {
                            detectTapGestures(
                                onDoubleTap = {
                                    onTogglePrivacy()
                                    val msg = if (!privacyMode) "حالت حریم خصوصی فعال شد" else "حالت حریم خصوصی غیرفعال شد"
                                    android.widget.Toast.makeText(context, msg, android.widget.Toast.LENGTH_SHORT).show()
                                }
                            )
                        },
                )
                IconButton(onClick = onTogglePrivacy) {
                    Icon(
                        Icons.Rounded.Lock,
                        contentDescription = if (privacyMode) "حالت امن (فعال)" else "حالت عادی",
                        tint = if (privacyMode) Hero.gold else Hero.muted,
                    )
                }
                IconButton(onClick = onSettings) {
                    // Rounded, like every other icon on this field: Material's Outlined and
                    // Rounded sets differ in corner treatment, and one gear drawn from the
                    // other set is the sort of mismatch that reads as sloppiness without ever
                    // being noticed. Still not filled — fill is the selected-state variant in
                    // Material's icon language, and this marks no active state.
                    Icon(
                        Icons.Rounded.Settings,
                        contentDescription = "تنظیمات",
                        tint = Hero.muted,
                    )
                }
            }

            Spacer(Modifier.height(Space.m))

            var showLiquidPerspective by remember { mutableStateOf(false) }

            val liquidCashToman = state.bankToman.toLong()
            val upcomingObligationsToman = (state.obligations.filter { !it.archived }.sumOf { obligation ->
                val paid = state.obligationPayments[obligation.id]?.count { it.paidDate != null } ?: 0
                val unpaidCount = (obligation.installmentCount - paid).coerceAtLeast(0)
                unpaidCount.toLong() * obligation.installmentRial
            } / 10L)
            val reservationsToman = (state.reservations.filter { !it.released }.sumOf { it.amountRial } / 10L)
            val safeLiquidToman = (liquidCashToman - upcomingObligationsToman - reservationsToman).coerceAtLeast(0L).toDouble()

            val activeTotal = if (showLiquidPerspective) safeLiquidToman else total
            val activeUsd = usdRate?.let { if (it > 0) activeTotal / it else null }

            // Perspective toggle pill
            Row(
                modifier = Modifier
                    .clip(RoundedCornerShape(Radius.pill))
                    .background(Hero.well)
                    .padding(2.dp),
                horizontalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(Radius.pill))
                        .background(if (!showLiquidPerspective) Hero.gold.copy(alpha = 0.28f) else Color.Transparent)
                        .clickable { showLiquidPerspective = false }
                        .padding(horizontal = Space.s, vertical = 3.dp),
                ) {
                    Text(
                        "کل دارایی",
                        fontSize = 12.sp,
                        fontWeight = if (!showLiquidPerspective) FontWeight.ExtraBold else FontWeight.Medium,
                        fontFamily = ModamFigures,
                        color = if (!showLiquidPerspective) Hero.gold else Hero.muted,
                    )
                }
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(Radius.pill))
                        .background(if (showLiquidPerspective) Hero.gold.copy(alpha = 0.28f) else Color.Transparent)
                        .clickable { showLiquidPerspective = true }
                        .padding(horizontal = Space.s, vertical = 3.dp),
                ) {
                    Text(
                        "نقدینگی آزاد",
                        fontSize = 12.sp,
                        fontWeight = if (showLiquidPerspective) FontWeight.ExtraBold else FontWeight.Medium,
                        fontFamily = ModamFigures,
                        color = if (showLiquidPerspective) Hero.gold else Hero.muted,
                    )
                }
            }

            Spacer(Modifier.height(Space.s))

            // Label and dollar figure share a line
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    if (showLiquidPerspective) "تنخواه آزاد نقد (پس از کسر اقساط)" else "جمع دارایی‌هات",
                    fontSize = 14.sp,
                    color = Hero.muted,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
                activeUsd?.let {
                    Text(
                        "≈ \$${bidi(faRate(it))}",
                        fontSize = 14.sp,
                        fontWeight = FontWeight.SemiBold,
                        fontFamily = ModamFigures,
                        color = Hero.muted,
                        maxLines = 1,
                        modifier = Modifier
                            .padding(start = Space.s)
                            .privacyBlur(privacyMode)
                            .semantics { contentDescription = "حدود ${faRate(it)} دلار" },
                    )
                }
            }

            Spacer(Modifier.height(Space.xs))
            val (heroDisplayFigure, heroDisplayUnit) = remember(activeTotal, currencyLens, state.effective) {
                if (currencyLens == CurrencyDenominator.TOMAN) {
                    faCompact(activeTotal, 3, pad = true) to "تومان"
                } else {
                    formatInDenominator(activeTotal, currencyLens, state.effective)
                }
            }

            AnimatedContent(
                targetState = heroDisplayFigure,
                transitionSpec = {
                    (
                        slideInVertically(tween(Motion.medium, easing = Motion.enter)) { it / 3 } +
                            fadeIn(tween(Motion.medium))
                        ).togetherWith(
                        slideOutVertically(tween(Motion.fast, easing = Motion.exit)) { -it / 3 } +
                            fadeOut(tween(Motion.fast))
                    )
                },
                label = "total",
                modifier = Modifier.privacyBlur(privacyMode),
            ) { figure ->
                BasicText(
                    text = heroFigure(figure, Hero.gold, heroDisplayUnit),
                    maxLines = 1,
                    autoSize = TextAutoSize.StepBased(minFontSize = 28.sp, maxFontSize = 60.sp),
                    style = figureStyle(Hero.gold, FontWeight.Black),
                )
            }

            // Digits are quick to scan but easy to misread by a factor of ten. The words
            // are the check on that.
            val lensDescription = when (currencyLens) {
                CurrencyDenominator.GOLD_18K -> "معادل $heroDisplayFigure گرم طلای ۱۸ عیار"
                CurrencyDenominator.USD_TETHER -> "معادل $heroDisplayFigure تتر / دلار"
                CurrencyDenominator.EMAMI_COIN -> "معادل $heroDisplayFigure تمام سکه بهار آزادی"
                CurrencyDenominator.TOMAN -> null
            }
            lensDescription?.let { words ->
                Text(
                    words,
                    fontSize = 15.sp,
                    lineHeight = 25.sp,
                    color = Hero.strong.copy(alpha = 0.93f),
                    modifier = Modifier.padding(top = Space.s).privacyBlur(privacyMode),
                )
            } ?: faWordsToman(total)?.let { words ->
                Text(
                    words,
                    fontSize = 15.sp,
                    lineHeight = 25.sp,
                    color = Hero.strong.copy(alpha = 0.93f),
                    modifier = Modifier.padding(top = Space.s).privacyBlur(privacyMode),
                )
            }
            Text(
                "${faNumber(activeTotal)} تومان",
                fontSize = 13.sp,
                fontFamily = ModamFigures,
                color = Hero.muted,
                modifier = Modifier.padding(top = Space.xs).privacyBlur(privacyMode),
            )

            // The two perspectives are only honest when the gap between them is named: the
            // difference is gold and long-term holdings, not money she can tap for lunch.
            // Shown on both sides of the toggle, because either side can mislead alone.
            val illiquidToman = state.illiquidToman
            if (illiquidToman > 0.0 && !portfolio) {
                Spacer(Modifier.height(Space.xs))
                Text(
                    if (showLiquidPerspective) "طلا و پس‌انداز بلندمدتت جدا حساب می‌شه"
                    else "${faCompact(illiquidToman)} از این رقم طلا و دارایی بلندمدته، نه پول نقد",
                    fontSize = 12.sp,
                    color = Hero.muted,
                    modifier = Modifier.privacyBlur(privacyMode),
                )
            }

            // Currency Lens Selector Pill Strip (Ticket 01)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = Space.m)
                    .clip(RoundedCornerShape(Radius.pill))
                    .background(Hero.well)
                    .padding(2.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                CurrencyDenominator.entries.forEach { den ->
                    val selected = den == currencyLens
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(Radius.pill))
                            .background(if (selected) Hero.gold.copy(alpha = 0.28f) else Color.Transparent)
                            .clickable { onCurrencyLensChange(den) }
                            .padding(vertical = Space.xs),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            text = den.labelFa,
                            fontSize = 12.sp,
                            fontWeight = if (selected) FontWeight.ExtraBold else FontWeight.Medium,
                            fontFamily = ModamFigures,
                            color = if (selected) Hero.gold else Hero.muted,
                        )
                    }
                }
            }

            // 2-Second Financial Vital Signs: Immediate Answer to "How much did I spend today?"
            val todayDay = remember { tehranDay(System.currentTimeMillis()) }
            val todaySpentToman = remember(state.ledger.allEntries, todayDay) {
                val rial = state.ledger.allEntries
                    .filter { !it.duplicate && !it.hidden && !it.transfer && it.txn.direction == "out" && it.txn.day == todayDay }
                    .sumOf { kotlin.math.abs(it.txn.signedRial ?: 0L) }
                rial / 10L
            }

            Spacer(Modifier.height(Space.s))
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .background(Hero.well.copy(alpha = 0.65f))
                    .clickable(onClick = onReport)
                    .padding(horizontal = Space.m, vertical = 7.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(7.dp)
                            .clip(CircleShape)
                            .background(if (todaySpentToman > 0L) Color(0xFFFF7043) else Hero.mint)
                    )
                    Spacer(Modifier.width(Space.s))
                    Text(
                        "خرج امروز:",
                        fontSize = 12.sp,
                        color = Hero.muted,
                    )
                    Spacer(Modifier.width(4.dp))
                    Text(
                        if (todaySpentToman > 0L) "${faCompact(todaySpentToman.toDouble())} تومان" else "امروز خرجی ثبت نشده",
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = ModamFigures,
                        color = if (todaySpentToman > 0L) Hero.strong else Hero.mint,
                        modifier = Modifier.privacyBlur(privacyMode),
                    )
                }

                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        "خرج ماه: ${faCompact(tomanOf(state.story.month.spentRial))}",
                        fontSize = 11.sp,
                        fontFamily = ModamFigures,
                        color = Hero.muted,
                        modifier = Modifier.privacyBlur(privacyMode),
                    )
                    Icon(
                        Icons.AutoMirrored.Rounded.KeyboardArrowRight,
                        contentDescription = null,
                        tint = Hero.gold,
                        modifier = Modifier.size(16.dp),
                    )
                }
            }

            // Show "available to spend" when there are active installment obligations.
            if (state.committedRial > 0) {
                Spacer(Modifier.height(Space.s))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        "قابل خرج:",
                        fontSize = 13.sp,
                        color = Hero.muted,
                    )
                    Spacer(Modifier.width(Space.xs))
                    Text(
                        "${faNumber(state.availableToman)} تومان",
                        fontSize = 14.sp,
                        fontFamily = ModamFigures,
                        fontWeight = FontWeight.SemiBold,
                        color = Hero.strong,
                    )
                }
                // Warn when available balance is low relative to the bank balance.
                if (state.bankToman > 0 && state.availableToman < state.bankToman * 0.2) {
                    Spacer(Modifier.height(Space.xs))
                    Text(
                        "موجودی قابل خرج کمتر از ۲۰٪ کل موجودی است",
                        fontSize = 11.sp,
                        color = Color(0xFFFFB74D),
                    )
                }
            }

            // The month band is خانه's door to the report, so the field only needs a standalone
            // link when there is no band — a first month with nothing recorded in it yet.
            val band = !portfolio && state.story.month.transactions > 0
            when {
                change != null -> {
                    Spacer(Modifier.height(Space.m))
                    ChangePill(change, onClick = onReport)
                }
                // Before there are thirty days to compare against, the pill has nothing it can
                // honestly state. A door that only appears in the second month is a door nobody
                // finds, so the slot keeps its target and drops the figure.
                // No spacer: the link's own 48dp target already carries more air than the pill
                // needs, and stacked they left a hole under the total.
                !portfolio && !band -> ReportLink(onClick = onReport)
            }

            if (portfolio) {
                // Kept whole on دارایی, where all three verbs are real: اضافه کردن adds,
                // گزارش is this screen's only way into the report, and تازه کردن is the
                // labelled refresh. On خانه every one of them was navigation wearing a verb's
                // icon — «اضافه کردن» went to this very screen, and the tab bar was already
                // going there — so there the band carries the month instead.
                Spacer(Modifier.height(Space.xl))
                Row(Modifier.fillMaxWidth()) {
                    HeroAction(
                        label = "اضافه کردن",
                        icon = Icons.Rounded.Add,
                        onClick = onAdd,
                        modifier = Modifier.weight(1f),
                    )
                    HeroAction(
                        label = "گزارش",
                        onClick = onReport,
                        modifier = Modifier.weight(1f),
                    ) { BarsIcon(Hero.strong) }
                    HeroAction(
                        label = "تازه کردن",
                        icon = Icons.Rounded.Refresh,
                        spinning = state.refreshing,
                        enabled = !state.refreshing,
                        onClick = onRefresh,
                        modifier = Modifier.weight(1f),
                    )
                }
            } else if (band) {
                // What she has, and how the month is going, in one screen without scrolling.
                // This used to sit in a card below the fold, which meant the answer the home
                // screen exists to give arrived only if she thought to scroll for it.
                Spacer(Modifier.height(Space.l))
                HorizontalDivider(color = Hero.hairline)
                Spacer(Modifier.height(Space.l))
                HeroMonth(state.story, onOpen = onReport)
            }

            Spacer(Modifier.height(if (portfolio) Space.xl else Space.l))
            HorizontalDivider(color = Hero.hairline)
            Row(
                verticalAlignment = Alignment.CenterVertically,
                // The control carries its own optical space, so the strip does not need the
                // full step above it when one is there.
                modifier = Modifier.padding(top = if (portfolio) Space.m else Space.xs),
            ) {
                val failed = state.error != null && state.rates.updatedAt == 0L
                // Colour confirms; the words carry it. A dot on its own would be the one
                // thing in this app that says "something is wrong" in hue alone.
                Box(
                    Modifier
                        .size(7.dp)
                        .clip(CircleShape)
                        .background(
                            when {
                                failed -> Hero.warn
                                stale -> Hero.warn
                                else -> Hero.mint
                            },
                        ),
                )
                Spacer(Modifier.width(Space.s))
                Text(
                    when {
                        failed -> "نرخ‌ها به‌روز نشدن. اینترنتت رو چک کن"
                        stale -> "نرخ‌ها قدیمی‌ان؛ " + faAgo(state.rates.updatedAt, now)
                        else -> "نرخ‌ها: " + faAgo(state.rates.updatedAt, now)
                    },
                    fontSize = 13.sp,
                    // Not gold: inside this field gold is the answer and the action, and a
                    // warning wearing it read as a second call to act.
                    color = if (stale || failed) Hero.warn else Hero.muted,
                    modifier = Modifier.weight(1f),
                )
                // The circle that used to carry this is gone from خانه, and the strip is where
                // it belonged all along: this line is about how fresh the numbers are, and the
                // control that changes that answer now sits at the end of the sentence stating
                // it. Labelled, not a bare glyph — a control you can be told about over the
                // phone has to have a name.
                if (!portfolio) {
                    HeroRefresh(
                        spinning = state.refreshing,
                        onClick = onRefresh,
                    )
                }
            }
            if (state.error != null && state.rates.updatedAt > 0L) {
                Text(
                    "اتصال نشد. نرخ‌های قبلی نشون داده می‌شن.",
                    fontSize = 12.sp,
                    lineHeight = 19.sp,
                    color = Hero.muted,
                    modifier = Modifier.padding(top = Space.xs),
                )
            }
    }
}

/**
 * Refresh, at the end of the line that says how old the numbers are.
 *
 * Pull-to-refresh does the same thing and is the gesture most people will use. This is the one
 * that can be named down a telephone, which is why it survives the circle it used to live in.
 */
@Composable
private fun HeroRefresh(spinning: Boolean, onClick: () -> Unit) {
    val angle = if (spinning) {
        val spin = rememberInfiniteTransition(label = "strip-spin")
        spin.animateFloat(
            initialValue = 0f,
            targetValue = 360f,
            animationSpec = infiniteRepeatable(
                tween(900, easing = LinearEasing), RepeatMode.Restart,
            ),
            label = "strip-angle",
        ).value
    } else {
        0f
    }

    Row(
        Modifier
            .clip(RoundedCornerShape(Radius.pill))
            .clickable(role = Role.Button, enabled = !spinning, onClick = onClick)
            // The visible pill is 32dp; the row around it takes the press, so the target
            // clears the floor without a control that looks like it wants the whole line.
            .heightIn(min = 48.dp)
            .padding(start = Space.s),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Row(
            Modifier
                .clip(RoundedCornerShape(Radius.pill))
                .background(Hero.well)
                .padding(horizontal = Space.m, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            // Muted, not `strong`: this annotates the sentence beside it, and at full contrast
            // it became the brightest thing in the band — a second call to act, sitting
            // directly above the tab bar's gold one.
            Icon(
                Icons.Rounded.Refresh,
                contentDescription = null,
                tint = Hero.muted,
                modifier = Modifier
                    .size(15.dp)
                    // Counter-clockwise, like every other spinner in this app: the icon is
                    // drawn with its arrowhead leading anticlockwise.
                    .graphicsLayer { rotationZ = -angle },
            )
            Spacer(Modifier.width(Space.xs))
            Text(
                "تازه کردن",
                fontSize = 12.sp,
                fontWeight = FontWeight.Medium,
                color = Hero.muted,
            )
        }
    }
}

/**
 * The way into the report when the change pill has nothing to say yet.
 *
 * Same slot, same target, no figure — because inventing one before there are thirty days to
 * compare against is exactly the kind of confident wrong number this app exists to not show.
 */
@Composable
private fun ReportLink(onClick: () -> Unit) {
    Row(
        Modifier
            .clip(RoundedCornerShape(Radius.pill))
            .clickable(role = Role.Button, onClick = onClick)
            .heightIn(min = 48.dp)
            .padding(end = Space.m),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // Weighted like the pill's own tail, which is what it stands in for. At `strong` and
        // SemiBold it read as a heading — the loudest line under a total it is subordinate to.
        Text("گزارش دارایی", fontSize = 13.sp, color = Hero.muted)
        Icon(
            Icons.AutoMirrored.Rounded.KeyboardArrowRight,
            contentDescription = null,
            tint = Hero.muted,
            modifier = Modifier.size(18.dp),
        )
    }
}

/**
 * One circular action on the field. Revolut's device, and the reason it works: the things she
 * might do next sit directly under the number that prompts them, all the same size, so none of
 * them is the app arguing for itself.
 */
@Composable
private fun HeroAction(
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    icon: androidx.compose.ui.graphics.vector.ImageVector? = null,
    spinning: Boolean = false,
    enabled: Boolean = true,
    /** How many things are waiting. Zero shows nothing at all. */
    badge: Int = 0,
    content: @Composable () -> Unit = {},
) {
    // Only while it actually spins: an infinite transition left running costs a frame
    // callback for ever, on the one screen that is open all day.
    val angle = if (spinning) {
        val spin = rememberInfiniteTransition(label = "spin")
        spin.animateFloat(
            initialValue = 0f,
            targetValue = 360f,
            animationSpec = infiniteRepeatable(
                tween(900, easing = LinearEasing), RepeatMode.Restart,
            ),
            label = "angle",
        ).value
    } else {
        0f
    }

    // The circle takes the press. A ripple inside a 54dp disc is most of the disc, so the
    // action reads as flashing rather than as being pushed; the give is what makes it feel
    // like a button under the thumb.
    val press = remember { MutableInteractionSource() }
    val pressed by press.collectIsPressedAsState()
    val give by animateFloatAsState(
        targetValue = if (pressed) 0.94f else 1f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessMedium,
        ),
        label = "press",
    )

    Column(
        modifier = modifier
            .scale(give)
            .clip(RoundedCornerShape(Radius.card))
            .clickable(
                enabled = enabled,
                role = Role.Button,
                interactionSource = press,
                indication = LocalIndication.current,
                onClick = onClick,
            )
            .padding(vertical = Space.s)
            .semantics { contentDescription = label },
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(contentAlignment = Alignment.TopEnd) {
            Box(
                Modifier
                    .size(54.dp)
                    .clip(CircleShape)
                    .background(Hero.well),
                contentAlignment = Alignment.Center,
            ) {
                if (icon != null) {
                    Icon(
                        icon,
                        contentDescription = null,
                        tint = Hero.strong,
                        modifier = Modifier
                            .size(24.dp)
                            .graphicsLayer { rotationZ = -angle },
                    )
                } else {
                    content()
                }
            }
            // A count, and only when there is one. Nothing here is overdue and nothing is
            // wrong, so it wears the field's own gold rather than a red alarm — the app is
            // reporting, not nagging.
            //
            // Gold on `bottom`, not on Hero.well: `well` is a 10%-white raised surface, and a
            // number drawn in it lands translucent-white on near-white and simply is not there.
            if (badge > 0) {
                Box(
                    Modifier
                        .size(18.dp)
                        .clip(CircleShape)
                        .background(Hero.gold),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        faNumber(badge.coerceAtMost(99).toDouble()),
                        style = figureStyle(Hero.bottom, FontWeight.ExtraBold),
                        fontSize = 11.sp,
                    )
                }
            }
        }
        Spacer(Modifier.height(Space.s))
        Text(
            label,
            fontSize = 12.sp,
            fontWeight = FontWeight.Medium,
            color = Hero.muted,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

/**
 * One wide chip under the home story: an entrance to a feature, not content of its own.
 * Quieter than [HeroAction] on purpose — these exist so ثبت دارایی and the installments hub
 * stop being buried, not to start competing with the numbers above them.
 */
@Composable
private fun HomeQuickAction(
    label: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier
            .clip(RoundedCornerShape(Radius.pill))
            .background(Hero.well)
            .clickable(role = Role.Button, onClick = onClick)
            .padding(horizontal = Space.m, vertical = Space.s)
            .semantics { contentDescription = label },
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center,
    ) {
        Icon(
            icon,
            contentDescription = null,
            tint = Hero.gold,
            modifier = Modifier.size(18.dp),
        )
        Spacer(Modifier.width(Space.s))
        Text(
            label,
            fontSize = 13.sp,
            fontWeight = FontWeight.SemiBold,
            color = Hero.strong,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

/**
 * How the total has moved in a month, and the way into the report. Percent leads because it is
 * the figure that survives being compared to last month's; the Toman amount is one tap away.
 */
@Composable
private fun ChangePill(change: Change, onClick: () -> Unit) {
    val flat = abs(change.delta) < 1
    val gained = change.delta > 0
    val tone = when {
        flat -> Hero.muted
        gained -> Hero.mint
        else -> Hero.warn
    }
    val figure = when {
        flat -> "بدون تغییر"
        change.percent != null -> "${faDecimal(abs(change.percent), 1)}٪"
        else -> faCompact(abs(change.delta))
    }
    val tail = when {
        flat -> "در ۳۰ روز گذشته"
        gained -> "بیشتر از ۳۰ روز پیش"
        else -> "کمتر از ۳۰ روز پیش"
    }

    Row(
        Modifier
            .clip(RoundedCornerShape(Radius.pill))
            .clickable(role = Role.Button, onClick = onClick)
            // The pill is 32dp tall on purpose — it must not compete with the action circles
            // below it — so the row around it carries the touch target.
            .heightIn(min = 48.dp)
            .padding(end = Space.m),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Row(
            Modifier
                .clip(RoundedCornerShape(Radius.pill))
                .background(tone.copy(alpha = 0.18f))
                .padding(horizontal = Space.m, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (!flat) {
                TrendCaret(up = gained, tint = tone)
                Spacer(Modifier.width(6.dp))
            }
            Text(
                figure,
                fontSize = 14.sp,
                fontFamily = ModamFigures,
                fontWeight = FontWeight.Bold,
                color = tone,
            )
        }
        Spacer(Modifier.width(Space.s))
        Text(tail, fontSize = 13.sp, color = Hero.muted, maxLines = 1)
        // On خانه this is the only way into the report, so it has to look like a way in. A
        // percentage sitting on a green field reads as a statistic; the chevron is what makes
        // it a door.
        Icon(
            Icons.AutoMirrored.Rounded.KeyboardArrowRight,
            contentDescription = null,
            tint = Hero.muted,
            modifier = Modifier.size(18.dp),
        )
    }
}

/** Modam has no ▲ or ▼, and a drawn one is crisper at this size than any font would be anyway. */
@Composable
internal fun TrendCaret(up: Boolean, tint: Color, box: Dp = 9.dp) {
    Canvas(Modifier.size(box)) {
        val w = size.width
        val h = size.height
        val path = Path().apply {
            if (up) {
                moveTo(w / 2f, 0f); lineTo(w, h); lineTo(0f, h)
            } else {
                moveTo(0f, 0f); lineTo(w, 0f); lineTo(w / 2f, h)
            }
            close()
        }
        drawPath(path, tint)
    }
}

/**
 * Three rising bars for the report button — drawn, because the icon set material3 already
 * ships has no chart glyph, and one emoji in a row of monochrome icons reads as a sticker.
 */
private fun openUrl(context: Context, url: String): Boolean =
    runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url))) }.isSuccess

/**
 * That there is a newer build, and nothing else. The card is one tap target: what changed and
 * both of the decisions live in the sheet behind it, because a line of card is no place to read
 * a changelog and a dismiss button sitting beside the thing it dismisses gets pressed by
 * accident.
 */
@Composable
private fun UpdateNote(release: Release, onLater: () -> Unit) {
    // Keyed on the version: a newer release arriving while she has the card open must not
    // inherit the sheet state of the one it replaced.
    var open by remember(release.name) { mutableStateOf(false) }

    Card(
        onClick = { open = true },
        shape = RoundedCornerShape(Radius.card),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.secondaryContainer,
            contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
        ),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(horizontal = Space.l, vertical = Space.m)) {
            Text(
                // Latin digits, and bidi() so the runs do not reorder inside the sentence:
                // this is the tag she will see on the page the card opens, not a quantity.
                "نسخه ${bidi(release.name)} اومد",
                fontSize = 16.sp,
                fontWeight = FontWeight.ExtraBold,
            )
            Text(
                "بزن ببین چی عوض شده.",
                fontSize = 13.sp,
                color = MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.8f),
                modifier = Modifier.padding(top = Space.xs),
            )
        }
    }

    if (open) {
        UpdateSheet(
            release = release,
            onClose = { open = false },
            onLater = { open = false; onLater() },
        )
    }
}

/**
 * A collapsible card showing active installment obligations.
 * Always rendered on the home screen; shows an empty state when there are no obligations.
 */
@Composable
private fun ObligationsCard(
    state: UiState,
    modifier: Modifier = Modifier,
    onOpenObligation: (InstallmentObligation) -> Unit = {},
    onAddObligation: () -> Unit = {},
) {
    var expanded by remember { mutableStateOf(false) }
    val obligations = state.obligations.filter { !it.archived }

    if (obligations.isEmpty()) {
        Card(
            shape = RoundedCornerShape(Radius.card),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceVariant,
                contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
            ),
            modifier = modifier.fillMaxWidth(),
        ) {
            Row(
                Modifier.fillMaxWidth().padding(horizontal = Space.l, vertical = Space.m),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    "اقساط فعالی نیست",
                    fontSize = 14.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                    modifier = Modifier.weight(1f),
                )
                TextButton(onClick = onAddObligation) {
                    Icon(Icons.Rounded.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(Space.xs))
                    Text("ثبت قسط", fontSize = 13.sp)
                }
            }
        }
        return
    }

    val totalRemaining = obligations.sumOf { ob ->
        val paid = state.obligationPayments[ob.id]?.count { it.paidDate != null } ?: 0
        val unpaidCount = ob.installmentCount - paid
        unpaidCount.toLong() * ob.installmentRial
    }

    Card(
        onClick = { expanded = !expanded },
        shape = RoundedCornerShape(Radius.card),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant,
            contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
        ),
        modifier = modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(horizontal = Space.l, vertical = Space.m)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    "اقساط فعال",
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.weight(1f),
                )
                TextButton(onClick = onAddObligation) {
                    Icon(Icons.Rounded.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(Space.xs))
                    Text("افزودن", fontSize = 13.sp)
                }
                Spacer(Modifier.width(Space.xs))
                Text(
                    "${faNumber(obligations.size.toDouble())} مورد",
                    fontSize = 13.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                )
            }
            Spacer(Modifier.height(Space.xs))
            Text(
                "باقیمانده: ${faNumber(totalRemaining / 10.0)} تومان",
                fontSize = 14.sp,
                fontFamily = ModamFigures,
                fontWeight = FontWeight.SemiBold,
            )

            if (expanded) {
                Spacer(Modifier.height(Space.m))
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                Spacer(Modifier.height(Space.m))
                obligations.forEach { ob ->
                    val payments = state.obligationPayments[ob.id] ?: emptyList()
                    val paid = payments.count { it.paidDate != null }
                    val remaining = (ob.installmentCount - paid).toLong() * ob.installmentRial
                    val nextUnpaid = payments.filter { it.paidDate == null }.minByOrNull { it.dueDate }
                    val badge = nextUnpaid?.let { installmentDueBadge(it.dueDate) }
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .clickable { onOpenObligation(ob) }
                            .padding(vertical = Space.xs),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(Modifier.weight(1f)) {
                            Text(
                                ob.provider.ifBlank { "اقساط" },
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Medium,
                            )
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    "${paid}/${ob.installmentCount} قسط پرداخت شده",
                                    fontSize = 12.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                                )
                                if (badge != null) {
                                    Text(
                                        " • ",
                                        fontSize = 12.sp,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                                    )
                                    val badgeColor = when (badge.level) {
                                        UrgencyLevel.OVERDUE -> MaterialTheme.colorScheme.error
                                        UrgencyLevel.WARNING -> MaterialTheme.colorScheme.secondary
                                        UrgencyLevel.NORMAL -> MaterialTheme.colorScheme.onSurfaceVariant
                                    }
                                    Text(
                                        badge.text,
                                        fontSize = 11.sp,
                                        fontWeight = if (badge.level != UrgencyLevel.NORMAL) FontWeight.Bold else FontWeight.Normal,
                                        color = badgeColor,
                                    )
                                }
                            }
                        }
                        Text(
                            "${faNumber(remaining / 10.0)}",
                            fontSize = 14.sp,
                            fontFamily = ModamFigures,
                            fontWeight = FontWeight.Bold,
                        )
                    }
                }
            }
        }
    }
}

/**
 * A card showing active fund reservations.
 * Always rendered on the home screen; shows an empty state when there are no reservations.
 */
@Composable
private fun ReservationsCard(
    state: UiState,
    modifier: Modifier = Modifier,
    onRelease: (String) -> Unit,
    onDelete: (String) -> Unit,
) {
    val reservations = state.reservations.filter { !it.released }

    if (reservations.isEmpty()) {
        Card(
            shape = RoundedCornerShape(Radius.card),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceVariant,
                contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
            ),
            modifier = modifier.fillMaxWidth(),
        ) {
            Text(
                "رزرو فعالی نیست",
                modifier = Modifier.padding(horizontal = Space.l, vertical = Space.m),
                fontSize = 14.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
            )
        }
        return
    }

    val totalReserved = reservations.sumOf { it.amountRial }

    Card(
        shape = RoundedCornerShape(Radius.card),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant,
            contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
        ),
        modifier = modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(horizontal = Space.l, vertical = Space.m)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    "رزروها",
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.weight(1f),
                )
                Text(
                    "${faNumber(reservations.size.toDouble())} مورد",
                    fontSize = 13.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                )
            }
            Spacer(Modifier.height(Space.xs))
            Text(
                "مجموع رزرو: ${faNumber(totalReserved / 10.0)} تومان",
                fontSize = 14.sp,
                fontFamily = ModamFigures,
                fontWeight = FontWeight.SemiBold,
            )

            reservations.forEach { reservation ->
                Spacer(Modifier.height(Space.s))
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                Spacer(Modifier.height(Space.s))
                Row(
                    Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(Modifier.weight(1f)) {
                        Text(
                            reservation.purpose,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Medium,
                        )
                        if (reservation.categoryId != null) {
                            Text(
                                state.ledger.categories.firstOrNull { it.id == reservation.categoryId }?.nameFa ?: "",
                                fontSize = 12.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                            )
                        }
                    }
                    Text(
                        "${faNumber(reservation.amountRial / 10.0)}",
                        fontSize = 14.sp,
                        fontFamily = ModamFigures,
                        fontWeight = FontWeight.Bold,
                    )
                    Spacer(Modifier.width(Space.s))
                    TextButton(onClick = { onRelease(reservation.id) }) {
                        Text("آزاد", fontSize = 12.sp)
                    }
                    TextButton(onClick = { onDelete(reservation.id) }) {
                        Text("حذف", fontSize = 12.sp, color = MaterialTheme.colorScheme.error)
                    }
                }
            }
        }
    }
}

/**
 * Bottom sheet form for adding a new fund reservation.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AddReservationForm(
    categories: List<Category>,
    onSave: (purpose: String, amount: Long, categoryId: String?) -> Unit,
    onDismiss: () -> Unit,
) {
    var purpose by remember { mutableStateOf("") }
    var amountText by remember { mutableStateOf("") }
    var selectedCategoryId by remember { mutableStateOf<String?>(null) }
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

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
            Text(
                "افزودن رزرو",
                fontSize = 20.sp,
                fontWeight = FontWeight.ExtraBold,
                modifier = Modifier.padding(bottom = Space.l),
            )

            OutlinedTextField(
                value = purpose,
                onValueChange = { purpose = it },
                label = { Text("هدف رزرو") },
                modifier = Modifier.fillMaxWidth().padding(bottom = Space.m),
                singleLine = true,
            )

            OutlinedTextField(
                value = amountText,
                onValueChange = { amountText = it.filter { c -> c.isDigit() || c == ',' } },
                label = { Text("مبلغ (تومان)") },
                modifier = Modifier.fillMaxWidth().padding(bottom = Space.m),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                singleLine = true,
            )

            Text(
                "دسته‌بندی (اختیاری)",
                fontSize = 13.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(bottom = Space.s),
            )

            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(Space.s),
                modifier = Modifier.padding(bottom = Space.l),
            ) {
                categories.filter { it.kind == CategoryKind.EXPENSE }.forEach { category ->
                    FilterChip(
                        selected = category.id == selectedCategoryId,
                        onClick = {
                            selectedCategoryId = if (category.id == selectedCategoryId) null else category.id
                        },
                        label = { Text(category.nameFa, fontSize = 12.sp) },
                    )
                }
            }

            Button(
                onClick = {
                    val amount = amountText.replace(",", "").toLongOrNull() ?: return@Button
                    if (purpose.isBlank() || amount <= 0) return@Button
                    onSave(purpose, amount, selectedCategoryId)
                },
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text("ذخیره")
            }
        }
    }
}

/**
 * What the new build changes, and the two things she can do about it.
 *
 * Nothing updates a sideloaded app on its own, so the button is the entire update path: it hands
 * the file to whatever she downloads with and she installs it herself. The app never fetches the
 * APK, never asks for the installer permission, and nothing touches the phone without her. The
 * link is the Worker's proxied copy, which answers with a Content-Disposition attachment, so the
 * download starts on the tap instead of landing her on a github.com page that, from Iran, most
 * often does not load at all.
 *
 * The two ways out are deliberately not the same. «بعداً» is an answer — the card goes away and
 * this version is not mentioned again. Swiping the sheet down or tapping the scrim is not an
 * answer, so the card is still there afterwards; nobody should lose the update by brushing it
 * away before reading it.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun UpdateSheet(release: Release, onClose: () -> Unit, onLater: () -> Unit) {
    val context = LocalContext.current
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val scope = rememberCoroutineScope()
    // Play the sheet out before removing it from composition, or the choice just blinks.
    fun close(then: () -> Unit) = scope.launch { sheetState.hide(); then() }

    ModalBottomSheet(
        onDismissRequest = onClose,
        sheetState = sheetState,
        shape = RoundedCornerShape(topStart = Radius.sheet, topEnd = Radius.sheet),
        containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
    ) {
        Column(
            Modifier
                .navigationBarsPadding()
                .padding(horizontal = Space.xl)
                .padding(bottom = Space.l),
        ) {
            Text(
                "نسخه ${bidi(release.name)} اومد",
                fontSize = 26.sp,
                fontWeight = FontWeight.ExtraBold,
                modifier = Modifier.semantics { heading() },
            )
            Text(
                // A release cut without a note is not an error worth showing her as one.
                if (release.notes.isEmpty()) "توضیحی همراهش نیومده، ولی از این که داری تازه‌تره."
                else "این چیزهاییه که عوض شده:",
                fontSize = 13.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                lineHeight = 20.sp,
                modifier = Modifier.padding(top = Space.xs),
            )

            // weight(fill = false) rather than a fixed max height: the list takes what is left
            // above the buttons and no more, so a short changelog sizes the sheet to itself and
            // a long one scrolls instead of pushing the button that acts on it off the bottom.
            // A dp cap does neither — it clips four ordinary lines mid-sentence on a tall screen
            // and still overflows a short one.
            Column(
                Modifier
                    .weight(1f, fill = false)
                    .nestedScroll(SheetFlingGuard)
                    .verticalScroll(rememberScrollState()),
            ) {
                release.notes.forEach { line ->
                    Row(Modifier.padding(top = Space.m)) {
                        Text("•", fontSize = 15.sp, color = MaterialTheme.colorScheme.primary)
                        Spacer(Modifier.width(Space.s))
                        Text(line, fontSize = 15.sp, lineHeight = 26.sp)
                    }
                }
            }

            Spacer(Modifier.height(Space.xl))
            Button(
                onClick = { close { onClose(); openUrl(context, release.downloadUrl) } },
                shape = RoundedCornerShape(Radius.pill),
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 60.dp),
            ) { Text("گرفتن فایل نصب", fontSize = 16.sp, fontWeight = FontWeight.Bold) }

            TextButton(
                onClick = { close(onLater) },
                colors = ButtonDefaults.textButtonColors(
                    contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 48.dp),
            ) { Text("بعداً", fontSize = 15.sp) }
        }
    }
}

@Composable
private fun BarsIcon(tint: Color) {
    // 24dp box, 2dp inset: Material icons keep ~2dp of built-in padding, so drawing
    // edge-to-edge at 22dp made the bars read heavier than the gear beside them.
    Canvas(
        Modifier
            .size(24.dp)
            .semantics { contentDescription = "گزارش دارایی" },
    ) {
        inset(2.dp.toPx()) {
            val w = size.width * 0.2f
            val r = CornerRadius(w / 2f)
            drawRoundRect(tint, Offset(0f, size.height * 0.45f), Size(w, size.height * 0.55f), r)
            drawRoundRect(tint, Offset((size.width - w) / 2f, size.height * 0.2f), Size(w, size.height * 0.8f), r)
            drawRoundRect(tint, Offset(size.width - w, 0f), Size(w, size.height), r)
        }
    }
}

/**
 * Three ruled lines: a page of the ledger, drawn rather than imported.
 *
 * The icons artifact this project pins is the core set, and it has no receipt or list glyph.
 * Three rounded rects cost less than a second dependency and match [BarsIcon] beside them,
 * which is the point — the two live in the same row and must read as one family.
 */
@Composable
private fun LedgerIcon(tint: Color) {
    Canvas(
        Modifier
            .size(24.dp)
            .semantics { contentDescription = "دفتر تراکنش‌ها" },
    ) {
        inset(2.dp.toPx()) {
            val h = size.height * 0.14f
            val r = CornerRadius(h / 2f)
            drawRoundRect(tint, Offset(0f, size.height * 0.10f), Size(size.width, h), r)
            drawRoundRect(tint, Offset(0f, size.height * 0.43f), Size(size.width * 0.78f, h), r)
            drawRoundRect(tint, Offset(0f, size.height * 0.76f), Size(size.width * 0.52f, h), r)
        }
    }
}

/**
 * A real logo where one exists, an emoji for the fixed assets, and the ticker as a last
 * resort. The letters sit underneath and are covered once the logo loads, so a slow network
 * shows an identifiable badge rather than a hole.
 *
 * [network] marks which chain the coin actually sits on. Tether on Tron and Tether on Ethereum
 * are the same logo and the same row, and until this the only thing telling them apart was a
 * 12sp line that a long address truncates.
 */
@Composable
private fun AssetIcon(type: AssetType, size: Dp = 44.dp, network: String? = null) {
    // Tinted by kind rather than one grey for all. It only ever shows behind the emoji and
    // letter fallbacks — a coin's own logo covers it — which is exactly where a list of
    // eleven identical grey discs was hardest to scan.
    val scheme = MaterialTheme.colorScheme
    val tint = when (type.kind) {
        Kind.CASH -> scheme.primaryContainer
        Kind.FIAT -> scheme.tertiaryContainer
        Kind.GOLD, Kind.SILVER, Kind.COIN -> scheme.secondaryContainer
        Kind.CRYPTO, Kind.STOCK -> scheme.surfaceVariant
    }
    Box(contentAlignment = Alignment.Center) {
        Box(
            modifier = Modifier
                .size(size)
                .clip(CircleShape)
                .background(tint),
            contentAlignment = Alignment.Center,
        ) {
            val letters = @Composable {
                // Three letters at 12sp, never four at 9: this badge is the only identity a coin
                // has until its logo loads, and 9sp is below the floor an older eye can read.
                Text(
                    type.id.take(3).uppercase(),
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            when {
                // The real currency mark for the one asset that IS the currency — an emoji bank
                // was always a stand-in.
                type.id == TOMAN_ID -> Icon(
                    painterResource(R.drawable.ic_toman),
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(size * 0.52f),
                )
                type.iconUrl != null -> {
                    var success by remember(type.iconUrl) { mutableStateOf(false) }
                    var loaded by remember(type.iconUrl) { mutableStateOf(false) }
                    if (!loaded) letters()
                    // crossfade: Coil swaps hard by default, and a list of logos popping in
                    // over their letters reads as flicker.
                    AsyncImage(
                        model = ImageRequest.Builder(LocalContext.current)
                            .data(type.iconUrl)
                            .crossfade(true)
                            .build(),
                        contentDescription = null,
                        modifier = Modifier.size(size * 0.62f),
                        // A row scrolled off is thrown away, so scrolling back composes this
                        // afresh with loaded=false and the letters underneath — which is
                        // invisible under an opaque logo, and exactly visible under a
                        // transparent one. Ethereum's diamond covers a third of its canvas, so
                        // every pass over the list restamped "ETH" through the glyph for a
                        // quarter second. From the memory cache there is no fade for the
                        // letters to cover — Coil skips the transition — so they can leave the
                        // moment the logo lands.
                        onState = { st ->
                            if (st is AsyncImagePainter.State.Success) {
                                if (st.result.dataSource == DataSource.MEMORY_CACHE) loaded = true
                                else success = true
                            }
                        },
                    )
                    // Success is when the fade STARTS; the letters leave after it lands, or
                    // the circle shows exactly the hole they exist to cover.
                    LaunchedEffect(success) {
                        if (success) {
                            delay(250)
                            loaded = true
                        }
                    }
                }
                type.emoji != null -> Text(type.emoji, fontSize = (size.value * 0.52f).sp)
                else -> letters()
            }
        }
        // Bottom-end corner, sitting on the logo rather than beside it — the chain is a
        // property of that coin, and given its own slot in the row it reads as a second asset.
        // The ring is the row's own background, so the two discs stay two discs even where a
        // dark chain logo meets a dark coin logo.
        network?.let {
            Box(
                Modifier
                    .align(Alignment.BottomEnd)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.surface)
                    .padding(1.5.dp),
            ) { WalletNetworkLogo(it, size = size * 0.36f) }
        }
    }
}

/**
 * The bank row's second line. It names how many accounts are behind the figure and, when any
 * of them is still a guess, says so there rather than only inside the sheet — a number she has
 * to open something to distrust is a number she will trust.
 */
private fun bankNote(state: UiState): String {
    val live = state.bankAccounts.filterNot { it.bank in state.disabledBanks }
    val off = state.bankAccounts.size - live.size
    // Just "۴ حساب": the row is already titled حساب‌های بانکی, and the longer wording pushed
    // the warning off the end of a single line, which is the half it cannot afford to lose.
    val counted = "${faNumber(live.size.toDouble())} حساب"
    return when {
        live.any { !it.trusted } -> "$counted  •  نیاز به بررسی"
        off > 0 -> "$counted  •  ${faNumber(off.toDouble())} خاموش"
        else -> counted
    }
}

// Material3's sheet hands whatever fling velocity its content leaves over to settle(), even
// when the sheet is already fully expanded and the leftover points further up — which kicks
// the settle spring from a standstill and bounces the sheet past its anchor and back. That is
// the jump every list gives at the end of its scroll. Upward leftovers have nowhere to go
// here (onPreFling already pulls a half-dragged sheet back up), so swallow them; downward
// ones still fling the sheet closed.
// ponytail: attach to each sheet's scrolling content. Four sheets in, still no wrapper: three
// of them bound a LazyColumn to 0.88 of the screen and the fourth wraps its content, so the
// shared part is this one line. Extract when two sheets want the same body, not the same guard.
internal val SheetFlingGuard = object : NestedScrollConnection {
    override suspend fun onPostFling(consumed: Velocity, available: Velocity): Velocity =
        if (available.y < 0f) available else Velocity.Zero
}

/**
 * Every tracked account, one line each, with the raw figure and when it last moved. This is
 * the audit surface: a balance read out of a message is only safe to act on if the message it
 * came from can be found and the number corrected, so nothing here is hidden behind a summary.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun BankSheet(
    accounts: List<BankAccount>,
    disabled: Set<String>,
    strangers: List<StrangeSender>,
    userName: String = "",
    onAnchor: (String, Double) -> Unit,
    onToggle: (String, Boolean) -> Unit,
    onForget: (String) -> Unit,
    onUpdateDetails: (String, String, String) -> Unit = { _, _, _ -> },
    onAddNumber: (String, String) -> Unit,
    onDismissSender: (String) -> Unit,
    onRescan: () -> Unit,
    onDismiss: () -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val context = LocalContext.current
    val muted = MaterialTheme.colorScheme.onSurfaceVariant
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    var fixing by remember { mutableStateOf<String?>(null) }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        shape = RoundedCornerShape(topStart = Radius.sheet, topEnd = Radius.sheet),
        containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
    ) {
        Column(
            Modifier
                .nestedScroll(SheetFlingGuard)
                .fillMaxHeight(0.88f)
                .navigationBarsPadding()
                .imePadding()
                .padding(horizontal = Space.xl),
        ) {
            Text(
                "حساب‌های بانکی",
                fontSize = 26.sp,
                fontWeight = FontWeight.ExtraBold,
                modifier = Modifier.semantics { heading() },
            )
            Text(
                "موجودی این حساب‌ها از پیامک بانک‌ها خونده می‌شه. اگه بانکی رو خاموش کنی، موجودیش توی جمع نمیاد.",
                fontSize = 13.sp,
                color = muted,
                lineHeight = 20.sp,
                modifier = Modifier.padding(top = Space.xs),
            )

            LazyColumn(
                contentPadding = PaddingValues(top = Space.l, bottom = Space.xxl),
                verticalArrangement = Arrangement.spacedBy(Space.s),
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
            ) {
                if (accounts.isNotEmpty()) {
                    item(key = "sheet_3d_cards") {
                        BankCardDeck(
                            accounts = accounts,
                            cardHolderName = userName,
                            modifier = Modifier.padding(bottom = Space.m),
                            onCardClick = {},
                            privacyMode = false,
                        )
                    }
                }

                items(accounts, key = { it.key }) { acc ->
                    BankAccountRow(
                        account = acc,
                        off = acc.bank in disabled,
                        now = now,
                        fixing = fixing == acc.key,
                        onFix = { fixing = if (fixing == acc.key) null else acc.key },
                        onAnchor = { v -> onAnchor(acc.key, v); fixing = null },
                        onToggle = { on -> onToggle(acc.bank, on) },
                        onForget = { onForget(acc.key) },
                        onUpdateDetails = onUpdateDetails,
                        onOpenSms = { openSmsThread(context, acc.sender) },
                    )
                }

                // A message that names one of her banks, arrived from a number the list does
                // not have — the one silence worth breaking, since without it a bank that adds
                // a shortcode simply freezes and looks like a reading bug. Her tap is what adds
                // the number; the message's own text only ever suggests.
                items(strangers, key = { "s_${it.sender}" }) { stranger ->
                    val bankFa = runCatching { Bank.valueOf(stranger.bank) }
                        .getOrDefault(Bank.OTHER).fa
                    Card(
                        onClick = { openSmsThread(context, stranger.sender) },
                        shape = RoundedCornerShape(Radius.card),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.secondaryContainer,
                        ),
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Column(Modifier.padding(Space.l)) {
                            Row(verticalAlignment = Alignment.Top) {
                                BankLogo(stranger.bank)
                                Spacer(Modifier.width(Space.s))
                                Text(
                                    "یه پیامک شبیه پیامک‌های $bankFa از شماره‌ای رسیده که هنوز توی فهرست نیست:",
                                    fontSize = 13.sp,
                                    lineHeight = 20.sp,
                                    color = MaterialTheme.colorScheme.onSecondaryContainer,
                                )
                            }
                            Text(
                                bidi(stranger.sender),
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSecondaryContainer,
                                modifier = Modifier.padding(top = Space.xs),
                            )
                            Text(
                                "«${stranger.snippet}»",
                                fontSize = 12.sp,
                                lineHeight = 19.sp,
                                color = MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.75f),
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.padding(top = Space.xs),
                            )
                            Text(
                                "باز کردن پیامک‌ها",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                textDecoration = TextDecoration.Underline,
                                color = MaterialTheme.colorScheme.onSecondaryContainer,
                                modifier = Modifier.padding(top = Space.s),
                            )
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                // Air between the confirm and the irreversible dismiss —
                                // borderless buttons at zero gap invite the edge mis-tap.
                                horizontalArrangement = Arrangement.spacedBy(Space.m),
                            ) {
                                TextButton(
                                    onClick = { onAddNumber(stranger.bank, stranger.sender) },
                                    modifier = Modifier.weight(1f),
                                ) {
                                    Text(
                                        "این شماره مال $bankFa هست؛ اضافه‌اش کن",
                                        fontSize = 14.sp,
                                        fontWeight = FontWeight.Bold,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                    )
                                }
                                // Two taps, as for deleting a holding. This sits right beside
                                // the confirm button and there is no undo: a mistap here
                                // silences that bank for good.
                                var sure by remember(stranger.sender) { mutableStateOf(false) }
                                TextButton(onClick = {
                                    if (sure) onDismissSender(stranger.sender) else sure = true
                                }) {
                                    Text(
                                        // Named consequence, like every other second tap in
                                        // the app — and announced, so the armed state exists
                                        // for someone listening too.
                                        if (sure) "برای رد کردن، دوباره بزن" else "رد کردن",
                                        fontSize = 14.sp,
                                        fontWeight = if (sure) FontWeight.Bold else FontWeight.Normal,
                                        color = MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.75f),
                                        modifier = Modifier.semantics {
                                            liveRegion = LiveRegionMode.Polite
                                        },
                                    )
                                }
                            }
                        }
                    }
                }

                // A message is read once and skipped ever after, so a balance that has gone
                // wrong stays wrong on its own. This is the way back: forget the lot and read
                // her messages again from the start.
                item {
                    var sure by remember { mutableStateOf(false) }
                    TextButton(
                        onClick = { if (sure) { onRescan(); sure = false } else sure = true },
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = Space.l),
                    ) {
                        Text(
                            if (sure) "همه مبلغ‌ها دوباره از پیامک‌ها خونده بشن؟ دوباره بزن"
                            else "دوباره خوندن همه پیامک‌ها",
                            fontSize = 14.sp,
                            fontWeight = if (sure) FontWeight.Bold else FontWeight.Normal,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
                        )
                    }
                }
            }
        }
    }

}

@Composable
private fun BankAccountRow(
    account: BankAccount,
    off: Boolean,
    now: Long,
    fixing: Boolean,
    onFix: () -> Unit,
    onAnchor: (Double) -> Unit,
    onToggle: (Boolean) -> Unit,
    onForget: () -> Unit,
    onUpdateDetails: (String, String, String) -> Unit = { _, _, _ -> },
    onOpenSms: () -> Unit,
) {
    val muted = MaterialTheme.colorScheme.onSurfaceVariant
    var draft by remember(account.key) { mutableStateOf("") }
    var editingDetails by remember(account.key) { mutableStateOf(false) }
    var nicknameDraft by remember(account.key) { mutableStateOf(account.nickname) }
    var panDraft by remember(account.key) { mutableStateOf(account.fullPan) }

    Card(
        shape = RoundedCornerShape(Radius.card),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(Space.l)) {
            // Off dims by colour, not by a blanket alpha: alpha over the whole row took the
            // 12sp caption — the line that explains the state — below every contrast floor.
            // Top, and the unit inline, for the same reason the list row outside this sheet
            // does both: the balance belongs on the account's own line, not floating beside
            // the caption under it.
            Row(verticalAlignment = Alignment.Top) {
                BankLogo(account.bank)
                Spacer(Modifier.width(Space.s))
                Column(Modifier.weight(1f)) {
                    RowTitle(
                        account.nickname.ifBlank { account.bankFa },
                        color = if (off) muted else MaterialTheme.colorScheme.onSurface,
                        max = 17.sp,
                    )
                    Text(
                        listOfNotNull(
                            account.bankFa.takeIf { account.nickname.isNotBlank() },
                            (account.fullPan.takeIf { it.isNotBlank() } ?: account.mask.takeIf { it.isNotBlank() })?.let { bidi(it) },
                            faAgo(account.updatedAt, now),
                            "خاموش".takeIf { off },
                        ).joinToString("  •  "),
                        fontSize = 12.sp,
                        color = muted,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.padding(top = 2.dp),
                    )
                }
                Spacer(Modifier.width(Space.s))
                RowAmount(
                    account.balance,
                    color = if (off) muted else MaterialTheme.colorScheme.onSurface,
                    struck = off,
                    unit = "تومان",
                )
            }

            // The same switch, with the same words, as setting an asset aside in its edit
            // sheet — one habit covers both. toggleable on the row merges label and switch
            // into one named control, and makes the whole line the hit target. Only for an
            // anchored account: an unanchored one is never in the total, and a switch
            // saying "counted" right above the red caption saying "not counted" would be
            // the card contradicting itself.
            if (account.anchored) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .toggleable(value = !off, role = Role.Switch, onValueChange = onToggle)
                        // With onCheckedChange = null the Switch no longer carries its own
                        // 48dp minimum; the row has to.
                        .heightIn(min = 48.dp)
                        .padding(top = Space.xs),
                ) {
                    Text(
                        "توی جمع حساب بشه",
                        fontSize = 14.sp,
                        color = if (off) muted else MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.weight(1f),
                    )
                    Switch(checked = !off, onCheckedChange = null)
                }
            }

            // The whole point of the flag: say what is uncertain and offer the one thing that
            // fixes it, rather than leaving a number on screen that quietly might be wrong.
            if (!account.trusted) {
                Text(
                    if (!account.anchored)
                            "این عدد فقط جمع تراکنش‌هاست، نه موجودی واقعی، و توی جمع حساب نشده. " +
                            "موجودی حسابت رو وارد کن تا توی جمع بیاد."
                    else "این عدد از پیامکی خونده شده که از درست بودنش مطمئن نیستیم. اگه اشتباهه، اصلاحش کن.",
                    fontSize = 12.sp,
                    lineHeight = 19.sp,
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier.padding(top = Space.s),
                )
            }

            if (fixing) {
                // A label that stays, not a placeholder that vanishes at the first digit —
                // while she types a balance, the unit must still be on screen.
                Text(
                    "موجودی به تومان",
                    fontSize = 14.sp,
                    color = muted,
                    modifier = Modifier.padding(top = Space.s),
                )
                OutlinedTextField(
                    value = draft,
                    onValueChange = { draft = it },
                    singleLine = true,
                    visualTransformation = GroupedNumber,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    supportingText = if (draft.isNotBlank() && parseAmount(draft) == null) ({
                        Text("این عدد قابل خوندن نیست. فقط عدد وارد کن.")
                    }) else null,
                    shape = RoundedCornerShape(Radius.field),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = Space.xs)
                        .semantics { contentDescription = "موجودی به تومان" },
                )
            }

            if (editingDetails) {
                Spacer(Modifier.height(Space.s))
                Text(
                    "نام مستعار یا برچسب کارت",
                    fontSize = 12.sp,
                    color = muted,
                )
                OutlinedTextField(
                    value = nicknameDraft,
                    onValueChange = { nicknameDraft = it },
                    placeholder = { Text("مثلاً: خرج روزانه، حقوق، پس‌انداز", fontSize = 12.sp) },
                    singleLine = true,
                    shape = RoundedCornerShape(Radius.field),
                    modifier = Modifier.fillMaxWidth().padding(top = Space.xs),
                )

                Spacer(Modifier.height(Space.s))
                Text(
                    "شماره کامل ۱۶ رقمی کارت (جهت کپی سریع و QR)",
                    fontSize = 12.sp,
                    color = muted,
                )
                OutlinedTextField(
                    value = panDraft,
                    onValueChange = {
                        val filtered = it.filter { c -> c.isDigit() || c in '۰'..'۹' }.take(16)
                        panDraft = filtered
                    },
                    placeholder = { Text("۶۰۳۷۹۹••••••••••", fontSize = 12.sp) },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    shape = RoundedCornerShape(Radius.field),
                    modifier = Modifier.fillMaxWidth().padding(top = Space.xs),
                )

                Row(
                    modifier = Modifier.fillMaxWidth().padding(top = Space.s),
                    horizontalArrangement = Arrangement.End,
                ) {
                    TextButton(onClick = { editingDetails = false }) {
                        Text("انصراف", fontSize = 13.sp)
                    }
                    Spacer(Modifier.width(Space.s))
                    Button(
                        onClick = {
                            onUpdateDetails(account.key, nicknameDraft, panDraft)
                            editingDetails = false
                        },
                        shape = RoundedCornerShape(Radius.pill),
                    ) {
                        Text("ذخیره مشخصات", fontSize = 13.sp)
                    }
                }
            }

            Row(Modifier.padding(top = Space.xs)) {
                if (account.sender.isNotBlank()) {
                    TextButton(onClick = onOpenSms) {
                        Text("دیدن پیامک‌ها", fontSize = 13.sp)
                    }
                }
                TextButton(onClick = { editingDetails = !editingDetails }) {
                    Text(if (editingDetails) "بستن مشخصات" else "نام و شماره کارت", fontSize = 13.sp)
                }
                if (fixing) {
                    TextButton(
                        onClick = { parseAmount(draft)?.let(onAnchor) },
                        enabled = parseAmount(draft) != null,
                    ) { Text("ذخیره موجودی", fontSize = 13.sp) }
                } else {
                    TextButton(onClick = onFix) { Text("اصلاح موجودی", fontSize = 13.sp) }
                }
                Spacer(Modifier.weight(1f))
                TextButton(
                    onClick = onForget,
                    colors = ButtonDefaults.textButtonColors(
                        contentColor = MaterialTheme.colorScheme.error,
                    ),
                ) { Text("حذف حساب", fontSize = 13.sp) }
            }
        }
    }
}

/**
 * The band title for one kind, carrying that kind's own total — the question the flat list
 * could never answer: how much of this is dollars, how much is crypto.
 *
 * Type and air do the separating. No rule, no badge, no colour: the heads have to read as
 * quieter than the cards they introduce, or the list gains structure and loses its subject.
 * Same size, weight and muted colour as the picker's headings, so the two lists agree about
 * what a kind looks like; the number takes Medium so it reads as the payload without
 * competing with the label for the eye.
 */
@Composable
private fun SectionHead(title: String, subtotal: Double?, modifier: Modifier = Modifier) {
    val muted = MaterialTheme.colorScheme.onSurfaceVariant
    Row(
        modifier
            .fillMaxWidth()
            .padding(edge)
            // A heading belongs to what follows it — more air above than below, as in the
            // picker. The 4dp inset is optical: flush against a 28dp corner radius, the label
            // reads as hanging outside the band it heads.
            .padding(top = Space.xxl, bottom = Space.m, start = Space.xs, end = Space.xs),
        verticalAlignment = Alignment.Bottom,
    ) {
        Text(
            title,
            fontSize = 15.sp,
            fontWeight = FontWeight.ExtraBold,
            color = MaterialTheme.colorScheme.onBackground,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier
                .weight(1f)
                .semantics { heading() },
        )
        subtotal?.let {
            Text(
                // Three decimals, like the rows it sits above: a header reading ۱٫۴ over rows
                // that add up to ۱٫۴۰۹ looks like one of the two is wrong.
                "${faCompact(it, 3, pad = true)} تومان",
                fontSize = 13.sp,
                fontFamily = ModamFigures,
                fontWeight = FontWeight.SemiBold,
                color = muted,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

/**
 * A row's value in Toman, at the same three decimals as the hero total — the same money
 * should not read as "۹٫۷ میلیارد" in one place and "۹٫۷۴۳ میلیارد" in the other.
 *
 * Shrink-to-fit like the hero, and bounded: three decimals make the figure noticeably wider,
 * and the asset's name (which shares the row) must not be the thing that gets ellipsised for
 * it on a narrow phone.
 */
/**
 * A row's title. Gives up a point or two of size before it gives up letters: the name of the
 * thing is what the row is for, and "حساب‌های بان…" is worse to read than the same words a
 * little smaller. Ellipsis stays as the last resort, below which shrinking would not help.
 */
@Composable
private fun RowTitle(
    text: String,
    color: Color,
    modifier: Modifier = Modifier,
    max: TextUnit = 18.sp,
) {
    BasicText(
        text = text,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
        autoSize = TextAutoSize.StepBased(minFontSize = 13.sp, maxFontSize = max),
        modifier = modifier,
        style = TextStyle(fontFamily = Modam, fontWeight = FontWeight.Bold, color = color),
    )
}

@Composable
private fun RowAmount(
    toman: Double,
    color: Color,
    struck: Boolean = false,
    /**
     * Set inline, the way the hero card sets it, rather than stacked underneath. A caption on
     * its own line makes the figure two lines tall, and everything the row says about the
     * asset then starts a full line-height below the name it belongs to — the name ends up
     * stranded at the top of the card with a hole under it.
     */
    unit: String? = null,
) {
    val muted = MaterialTheme.colorScheme.onSurfaceVariant
    BasicText(
        text = buildAnnotatedString {
            append(faCompact(toman, 3, pad = true))
            // em, not sp: the whole line is shrink-to-fit, and the unit has to shrink with the
            // figure or it overtakes it on the row where the figure is smallest.
            unit?.let {
                // The space stays near full size, or it shrinks with the unit and the two
                // words touch. See the hero figure, which has the same seam.
                append(" ")
                withStyle(SpanStyle(fontSize = 0.7.em, fontWeight = FontWeight.Medium, color = muted)) {
                    append(it)
                }
            }
        },
        maxLines = 1,
        autoSize = TextAutoSize.StepBased(minFontSize = 13.sp, maxFontSize = 19.sp),
        // Bounded so the figure, not the asset's name, is what gives way on a narrow phone:
        // "۵۵۹٫۵۰۰ میلیون" is half again as wide as "۵۵۹٫۵ میلیون" was.
        modifier = Modifier.widthIn(max = if (unit == null) 116.dp else 140.dp),
        style = figureStyle(color, FontWeight.Bold).copy(
            textDecoration = if (struck) TextDecoration.LineThrough else null,
        ),
    )
}

private fun shortWalletAddress(address: String): String =
    if (address.length <= 15) address else "${address.take(7)}...${address.takeLast(5)}"

@Composable
private fun HoldingRow(
    type: AssetType,
    /** Her own name for this holding where she gave it one, the asset's own where she did not. */
    name: String,
    amount: Double,
    rate: Double?,
    excluded: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    /** Replaces the amount-and-rate line, for a row whose amount did not come from her. */
    note: String? = null,
    wallet: WalletLink? = null,
    walletRefreshing: Boolean = false,
    walletError: String? = null,
    shape: Shape = RoundedCornerShape(Radius.group),
    divided: Boolean = false,
) {
    val muted = MaterialTheme.colorScheme.onSurfaceVariant
    val strong = MaterialTheme.colorScheme.onSurface

    // No card, no elevation, no gap: a band of holdings is one object with rows inside it.
    // Individual cards gave every row the same weight as the band it belonged to, and eleven
    // of them read as eleven separate announcements rather than one list.
    //
    // No gesture either: setting an asset aside lives behind the row's edit sheet, as a
    // labelled switch. Swipes proved too easy to trigger scrolling the list and too hard
    // to discover on purpose.
    Box(
        modifier
            .padding(edge)
            .fillMaxWidth()
            .clip(shape)
            .background(MaterialTheme.colorScheme.surface)
            .clickable(role = Role.Button, onClick = onClick),
    ) {
        // Set-aside dims by colour, not a blanket alpha: alpha over the whole row took the
        // 12sp caption that explains the state below every contrast floor.
        // Top, not centre: the badge belongs to the name, and centring it against a row that
        // may carry two more lines under that name left it floating beside the wrong one.
        // 44dp is the height of the name and the line below it, so it spans exactly the pair.
        Row(
            Modifier.padding(horizontal = Space.l, vertical = Space.l),
            verticalAlignment = Alignment.Top,
        ) {
            Box(Modifier.alpha(if (excluded) 0.55f else 1f)) {
                AssetIcon(type, network = wallet?.network)
            }
            Spacer(Modifier.width(Space.m))

            Column(Modifier.weight(1f)) {
                // The hero's own device, one size down: the name and the figure share the top
                // line, so the row reads as one sentence instead of two blocks that happen to
                // be next to each other. It also hands every line below it the full width of
                // the block rather than the strip left over beside the figure — which is what
                // the rate used to be truncated into.
                Row(verticalAlignment = Alignment.Top) {
                    RowTitle(
                        name,
                        color = if (excluded) muted else strong,
                        // One step under the figure. The money is the answer this row exists
                        // to give; the name only says which money.
                        max = 17.sp,
                        modifier = Modifier.weight(1f),
                    )
                    Spacer(Modifier.width(Space.s))
                    // End, not Start: the figures are already flush with the card's edge, so
                    // this is the only alignment that keeps a set-aside row's caption in the
                    // same column instead of hanging it off the ragged inner end of a number.
                    Column(horizontalAlignment = Alignment.End) {
                        when {
                            excluded -> {
                                if (rate != null) {
                                    RowAmount(amount * rate, color = muted, struck = true)
                                }
                                // Stacked, not inline: this one is the row's state, not the
                                // figure's unit, and it has to survive being read on its own.
                                Text("توی جمع حساب نشده", fontSize = 12.sp, color = muted)
                            }
                            rate == null ->
                                Text("نرخش پیدا نشد", fontSize = 14.sp, color = MaterialTheme.colorScheme.error)
                            else -> RowAmount(amount * rate, color = strong, unit = "تومان")
                        }
                    }
                }

                val held = "${faHeld(amount, type.dec)} ${bidi(type.unitFa)}"
                // null when this line is a sentence rather than an amount-and-rate pair.
                val shownRate = rate?.takeIf { note == null && type.id != TOMAN_ID }?.let(::faRate)
                if (shownRate == null) {
                    Text(
                        when {
                            note != null -> note
                            // Says how the figure got here, which is the whole difference
                            // between this row and the bank one right below it.
                            type.id == TOMAN_ID -> "دستی وارد شده"
                            else -> held
                        },
                        fontSize = 13.sp,
                        color = muted,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.padding(top = 2.dp),
                    )
                } else {
                    // The rate carries no weight, so Row measures it first and gives it the
                    // width it asks for: when the line cannot hold both, the amount she typed
                    // herself is what gives way, never the rate she opened the app to read.
                    // One string, not two Texts — the label and its figure have to stay in one
                    // bidi run or the separator drifts off the end of the line.
                    Row(Modifier.padding(top = 2.dp)) {
                        Text(
                            held,
                            fontSize = 13.sp,
                            color = muted,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f, fill = false),
                        )
                        Text(
                            buildAnnotatedString {
                                // "نرخ" rather than "هر <unit>": one less latin run, and short
                                // enough that a long ticker cannot push the line onto a second.
                                withStyle(SpanStyle(color = muted)) { append("•  نرخ ") }
                                // Full strength and a weight up — the one figure on this line
                                // worth finding at a glance, and the reason the line exists.
                                withStyle(SpanStyle(color = strong, fontWeight = FontWeight.Medium)) {
                                    append(shownRate)
                                }
                            },
                            fontSize = 13.sp,
                            maxLines = 1,
                            modifier = Modifier.padding(start = Space.s),
                        )
                    }
                }

                wallet?.let {
                    // One line, not two: the address is a "this is tracked for you" mark, not
                    // something she can check by reading — and stacked, it was the third loose
                    // block that made the card read as pieces rather than a row.
                    val visibleStatus = when {
                        walletRefreshing -> "در حال گرفتن از ${it.networkFa}"
                        walletError != null -> "به‌روز نشد  •  موجودی قبلی نشون داده می‌شه"
                        else -> "${it.networkFa}  •  ${bidi(shortWalletAddress(it.address))}"
                    }
                    val spokenStatus = when {
                        walletRefreshing -> "در حال گرفتن موجودی از ${it.networkFa}"
                        walletError != null -> "موجودی به‌روز نشد؛ موجودی قبلی نشون داده می‌شه"
                        else -> "خوندن خودکار از ${it.networkFa}، آدرس ${it.address}"
                    }
                    Text(
                        visibleStatus,
                        fontSize = 12.sp,
                        color = if (walletError != null) MaterialTheme.colorScheme.error else muted,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier
                            .padding(top = 2.dp)
                            .semantics { contentDescription = spokenStatus },
                    )
                }
            }
        }
        // Inset to where the text starts, not to the card edge: a rule that runs under the
        // badge cuts the row in half instead of separating it from the next one. Only between
        // rows — a rule under the last one is the band's own bottom edge drawn twice.
        if (divided) {
            HorizontalDivider(
                color = MaterialTheme.colorScheme.outlineVariant,
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .padding(start = Space.l + 44.dp + Space.m),
            )
        }
    }
}

/**
 * It names the button rather than repeating it. The bar at the bottom is on screen the whole
 * time this hint is, and two identical green pills one above the other read as a mistake —
 * the reader's question becomes which of them is the real one.
 */
@Composable
private fun EmptyHint() {
    Column(
        Modifier
            .fillMaxWidth()
            .padding(edge)
            .padding(top = Space.huge, bottom = Space.xxl),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            Modifier
                .size(84.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.surfaceContainer),
            contentAlignment = Alignment.Center,
        ) { Text("👛", fontSize = 40.sp) }
        Spacer(Modifier.height(Space.xl))
        Text(
            "هنوز چیزی اضافه نکردی",
            fontSize = 22.sp,
            fontWeight = FontWeight.ExtraBold,
        )
        Spacer(Modifier.height(Space.s))
        Text(
            "از دکمه پایین پول نقد، دلار، طلا، سکه یا رمزارز اضافه کن تا جمعشون رو ببینی.",
            fontSize = 15.sp,
            lineHeight = 25.sp,
            textAlign = TextAlign.Center,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun MissingNote(missing: List<String>, coins: List<Coin>, stocks: List<Stock>) {
    val names = missing.map { resolveType(it, coins, stocks).fa }.distinct().joinToString("، ")
    Row(
        Modifier
            .padding(edge)
            .padding(top = Space.l)
            .fillMaxWidth()
            .clip(RoundedCornerShape(Radius.card))
            .background(MaterialTheme.colorScheme.errorContainer)
            .padding(Space.l),
    ) {
        Text(
            "نرخ $names پیدا نشد و توی جمع حساب نشده. می‌تونی نرخش رو دستی وارد کنی.",
            fontSize = 13.sp,
            lineHeight = 21.sp,
            color = MaterialTheme.colorScheme.onErrorContainer,
        )
    }
}

/**
 * The kinds too numerous to list, mapped to what to call them when saying so. These come from
 * the network by the hundred, so the picker shows a first handful and leaves the rest to
 * search; every other kind is a short fixed list that belongs on screen in full.
 */
private val BROWSABLE_BY_SEARCH = mapOf(
    Kind.CRYPTO to "رمزارزها",
    Kind.STOCK to "نمادها",
)

/**
 * A drag-to-dismiss sheet rather than a dialog: the list is long, the phone is held one
 * handed, and the controls belong near the thumb.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PickTypeSheet(
    all: List<AssetType>,
    already: Set<String>,
    onDismiss: () -> Unit,
    onPick: (String) -> Unit,
) {
    var query by remember { mutableStateOf("") }
    val q = query.trim()

    val sections: List<Pair<String?, List<AssetType>>> = remember(q, all) {
        if (q.isNotEmpty()) {
            // Persian name, latin name or ticker — matchesSearch owns which of them count.
            listOf<Pair<String?, List<AssetType>>>(null to all.filter { matchesSearch(it, q) })
        } else {
            // Capped, or 250 tickers bury the assets she actually owns — and بورس is four
            // times that again.
            Kind.entries.map { kind ->
                val items = all.filter { it.kind == kind }
                kind.fa to if (kind in BROWSABLE_BY_SEARCH) items.take(12) else items
            }
        }
    }

    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val scope = rememberCoroutineScope()
    // Play the sheet out before removing it from composition, or the selection just blinks.
    fun close(then: () -> Unit) = scope.launch { sheetState.hide(); then() }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        shape = RoundedCornerShape(topStart = Radius.sheet, topEnd = Radius.sheet),
        containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
    ) {
        Column(
            // A bounded height is what makes the drag smooth: with fillMaxSize on the list
            // the sheet grows to the whole screen, loses its corners and scrim, and every
            // drag re-measures the content.
            Modifier
                .nestedScroll(SheetFlingGuard)
                .fillMaxHeight(0.88f)
                .navigationBarsPadding()
                .imePadding()
                .padding(horizontal = Space.xl)
        ) {
            Text(
                "چی می‌خوای اضافه کنی؟",
                fontSize = 26.sp,
                fontWeight = FontWeight.ExtraBold,
                modifier = Modifier.semantics { heading() },
            )

            OutlinedTextField(
                value = query,
                onValueChange = { query = it },
                singleLine = true,
                placeholder = { Text("جستجو...") },
                leadingIcon = { Icon(Icons.Rounded.Search, contentDescription = null) },
                trailingIcon = {
                    if (query.isNotBlank()) {
                        IconButton(onClick = { query = "" }) {
                            Icon(Icons.Rounded.Close, contentDescription = "پاک کردن")
                        }
                    }
                },
                shape = RoundedCornerShape(Radius.field),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = Space.m, bottom = Space.xs)
                    .semantics { contentDescription = "جستجو" },
            )

            LazyColumn(
                contentPadding = PaddingValues(bottom = Space.xxl),
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
            ) {
                if (q.isNotEmpty() && sections.all { it.second.isEmpty() }) {
                    item {
                        // Names the query and points at the exit: search also matches the
                        // latin name and the ticker, and this is the one moment to say so.
                        Text(
                            // bidi(): a mixed query like "gold 18" would otherwise render
                            // with its runs reordered inside the guillemets.
                            "برای «${bidi(q)}» چیزی پیدا نشد. اسم انگلیسی یا نمادش رو هم امتحان کن.",
                            fontSize = 15.sp,
                            lineHeight = 24.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(vertical = Space.xxl),
                        )
                    }
                }

                sections.forEach { (title, items) ->
                    if (items.isEmpty()) return@forEach
                    if (title != null) {
                        item(key = "h_$title") {
                            // The same heading the asset list uses, so a kind looks like the
                            // same kind of thing whether she is reading her money or adding to it.
                            Text(
                                title,
                                fontSize = 15.sp,
                                fontWeight = FontWeight.ExtraBold,
                                color = MaterialTheme.colorScheme.onSurface,
                                // more air above a heading than below it
                                modifier = Modifier
                                    .padding(top = Space.xxl, bottom = Space.s, start = Space.xs)
                                    .semantics { heading() },
                            )
                        }
                    }
                    items(items, key = { it.id }) { type ->
                        PickRow(type, type.id in already) { close { onPick(type.id) } }
                    }
                    // The cue that more exist sits under the rows it discloses — at the
                    // bottom of the whole sheet, after gold and سکه, a capped section just
                    // looked complete.
                    val more = BROWSABLE_BY_SEARCH.entries.firstOrNull { it.key.fa == title }
                    if (q.isEmpty() && more != null) {
                        item(key = "more_hint_$title") {
                            Text(
                                "بقیه ${more.value} رو می‌خوای؟ اسمش رو جستجو کن.",
                                fontSize = 13.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(top = Space.s, start = Space.xs),
                            )
                        }
                    }
                }
            }
        }
    }
}

// ──────────────────────── category management ────────────────────────

@Composable
fun CategoriesScreen(
    categories: List<Category>,
    hiddenCategories: Set<String> = emptySet(),
    categoryCounts: Map<String, Int> = emptyMap(),
    onAdd: (String, String) -> Unit,
    onEdit: (String, String) -> Unit,
    /** (category id, target id) — target null deletes outright (only when empty is safe). */
    onDelete: (String, String?) -> Unit,
    onToggleHidden: (String, Boolean) -> Unit,
    onBack: () -> Unit,
) {
    var adding by remember { mutableStateOf(false) }
    var editingCategory by remember { mutableStateOf<Category?>(null) }
    var deletingCategory by remember { mutableStateOf<Category?>(null) }
    var newName by remember { mutableStateOf("") }
    var selectedKind by remember { mutableStateOf(CategoryKind.EXPENSE) }

    Surface(color = MaterialTheme.colorScheme.background, modifier = Modifier.fillMaxSize()) {
        Column(
            Modifier
                .fillMaxSize()
                .systemBarsPadding()
                .imePadding(),
        ) {
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = Space.xl, vertical = Space.m),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconButton(onClick = onBack) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "بازگشت")
                }
                Text(
                    "دسته‌بندی‌ها",
                    fontSize = 24.sp,
                    fontWeight = FontWeight.ExtraBold,
                    modifier = Modifier.weight(1f),
                )
                IconButton(onClick = { adding = true; newName = ""; selectedKind = CategoryKind.EXPENSE }) {
                    Icon(Icons.Rounded.Add, contentDescription = "افزودن")
                }
            }

            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(bottom = Space.xxl),
            ) {
                items(categories, key = { it.id }) { category ->
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .padding(horizontal = Space.xl, vertical = Space.s),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        CategoryIcon(
                            category.nameFa,
                            MaterialTheme.colorScheme.onSurfaceVariant,
                            size = 20.dp,
                            stroke = 1.6.dp,
                        )
                        Spacer(Modifier.width(Space.m))
                        Column(Modifier.weight(1f)) {
                            Text(
                                category.nameFa,
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Medium,
                            )
                            Text(
                                when (category.kind) {
                                    CategoryKind.EXPENSE -> "خرج"
                                    CategoryKind.INCOME -> "درآمد"
                                    else -> "انتقال"
                                },
                                fontSize = 12.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        if (!category.builtin) {
                            IconButton(onClick = { editingCategory = category; newName = category.nameFa }) {
                                Icon(
                                    Icons.Rounded.Edit,
                                    contentDescription = "ویرایش",
                                    modifier = Modifier.size(18.dp),
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                            IconButton(onClick = { deletingCategory = category }) {
                                Icon(
                                    Icons.Rounded.Delete,
                                    contentDescription = "حذف",
                                    modifier = Modifier.size(18.dp),
                                    tint = MaterialTheme.colorScheme.error,
                                )
                            }
                        } else {
                            val hidden = category.id in hiddenCategories
                            TextButton(
                                onClick = { onToggleHidden(category.id, !hidden) },
                                contentPadding = PaddingValues(horizontal = Space.s),
                            ) {
                                Text(
                                    if (hidden) "نمایش" else "پنهان",
                                    fontSize = 11.sp,
                                    color = if (hidden) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    // Add category dialog
    if (adding) {
        AlertDialog(
            onDismissRequest = { adding = false },
            title = { Text("دسته‌بندی تازه") },
            text = {
                Column {
                    OutlinedTextField(
                        value = newName,
                        onValueChange = { newName = it.take(30) },
                        label = { Text("نام") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Spacer(Modifier.height(Space.m))
                    Row(
                        Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(Space.s),
                    ) {
                        listOf(CategoryKind.EXPENSE to "خرج", CategoryKind.INCOME to "درآمد").forEach { (kind, label) ->
                            FilterChip(
                                selected = selectedKind == kind,
                                onClick = { selectedKind = kind },
                                label = { Text(label) },
                            )
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        if (newName.isNotBlank()) {
                            onAdd(newName, selectedKind)
                            adding = false
                        }
                    },
                ) { Text("افزودن") }
            },
            dismissButton = {
                TextButton(onClick = { adding = false }) { Text("لغو") }
            },
        )
    }

    // Edit category dialog
    editingCategory?.let { cat ->
        AlertDialog(
            onDismissRequest = { editingCategory = null },
            title = { Text("ویرایش دسته‌بندی") },
            text = {
                OutlinedTextField(
                    value = newName,
                    onValueChange = { newName = it.take(30) },
                    label = { Text("نام") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        if (newName.isNotBlank()) {
                            onEdit(cat.id, newName)
                            editingCategory = null
                        }
                    },
                ) { Text("ذخیره") }
            },
            dismissButton = {
                TextButton(onClick = { editingCategory = null }) { Text("لغو") }
            },
        )
    }

    // Delete confirmation dialog. Empty categories delete outright; a category with
    // transactions must say where they go before it may die, so nothing is orphaned.
    deletingCategory?.let { cat ->
        val count = categoryCounts[cat.id] ?: 0
        if (count == 0) {
            AlertDialog(
                onDismissRequest = { deletingCategory = null },
                title = { Text("حذف دسته‌بندی") },
                text = { Text("«${cat.nameFa}» خالی است و حذف می‌شود.") },
                confirmButton = {
                    TextButton(
                        onClick = { onDelete(cat.id, null); deletingCategory = null },
                        colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error),
                    ) { Text("حذف") }
                },
                dismissButton = {
                    TextButton(onClick = { deletingCategory = null }) { Text("لغو") }
                },
            )
        } else {
            var targetId by remember(cat.id) { mutableStateOf<String?>(null) }
            AlertDialog(
                onDismissRequest = { deletingCategory = null },
                title = { Text("انتقال تراکنش‌های «${cat.nameFa}»") },
                text = {
                    Column {
                        Text(
                            "${faNumber(count.toDouble())} تراکنش در این دسته هست. قبل از حذف باید به دسته دیگری بروند.",
                            fontSize = 13.sp,
                            lineHeight = 20.sp,
                        )
                        Spacer(Modifier.height(Space.m))
                        LazyColumn(Modifier.heightIn(max = 220.dp)) {
                            items(categories.filter { it.id != cat.id }, key = { it.id }) { target ->
                                Row(
                                    Modifier
                                        .fillMaxWidth()
                                        .clip(RoundedCornerShape(Radius.field))
                                        .background(
                                            if (target.id == targetId) MaterialTheme.colorScheme.primaryContainer
                                            else Color.Transparent
                                        )
                                        .clickable { targetId = target.id }
                                        .padding(Space.m),
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    CategoryIcon(
                                        target.nameFa,
                                        MaterialTheme.colorScheme.onSurfaceVariant,
                                        size = 18.dp,
                                        stroke = 1.4.dp,
                                    )
                                    Spacer(Modifier.width(Space.m))
                                    Text(target.nameFa, fontSize = 14.sp)
                                }
                            }
                        }
                    }
                },
                confirmButton = {
                    TextButton(
                        enabled = targetId != null,
                        onClick = {
                            targetId?.let { onDelete(cat.id, it) }
                            deletingCategory = null
                        },
                        colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error),
                    ) { Text("انتقال و حذف") }
                },
                dismissButton = {
                    TextButton(onClick = { deletingCategory = null }) { Text("لغو") }
                },
            )
        }
    }
}

@Composable
private fun PickRow(type: AssetType, already: Boolean, onClick: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(Radius.field))
            .clickable(role = Role.Button, onClick = onClick)
            .padding(horizontal = Space.s, vertical = Space.m),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        AssetIcon(type, size = 40.dp)
        Spacer(Modifier.width(Space.m))
        Column(Modifier.weight(1f)) {
            Text(
                type.fa,
                fontSize = 17.sp,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            // The ticker tells one coin from another where the Persian names blur together;
            // for a نماد the same job is done by the company behind it, since its own id is
            // an instrument code that means nothing to anyone.
            val subtitle = when (type.kind) {
                Kind.CRYPTO -> type.id.uppercase()
                Kind.STOCK -> type.en
                else -> ""
            }
            if (subtitle.isNotBlank()) {
                Text(
                    subtitle,
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        if (already) {
            // A chip, not loose grey words: it is a state this row is in, and it has to read
            // as attached to the row rather than as a second, quieter label.
            Text(
                "اضافه شده",
                fontSize = 12.sp,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier
                    .clip(RoundedCornerShape(Radius.pill))
                    .background(MaterialTheme.colorScheme.surfaceContainerHighest)
                    .padding(horizontal = Space.m, vertical = 5.dp),
            )
        }
    }
}

private enum class AmountSource { WALLET, MANUAL }

/**
 * A label over a group inside a sheet. It owns the air above itself, so the sections of a long
 * sheet are separated by one rule rather than by whatever `Spacer` each of them happened to be
 * given — three of them had drifted to three different gaps.
 *
 * More space above than below, because a heading belongs to what follows it.
 */
@Composable
private fun SheetLabel(text: String, modifier: Modifier = Modifier) {
    Text(
        text,
        fontSize = 14.sp,
        fontWeight = FontWeight.Bold,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = modifier
            .padding(top = Space.xxl, bottom = Space.s, start = Space.xs)
            .semantics { heading() },
    )
}

/**
 * One track, one filled pill — the app's only shape for "pick exactly one of a few". The
 * report's window picker, the theme picker and the two ways of recording a balance were three
 * different-looking answers to the same question.
 *
 * The fill is not the only signal: the chosen pill is also the only bold one, and it carries
 * `selected` for anyone listening rather than looking.
 */
@Composable
internal fun <T> SegmentedChoice(
    options: List<T>,
    selected: T,
    label: (T) -> String,
    onSelect: (T) -> Unit,
    modifier: Modifier = Modifier,
    enabled: (T) -> Boolean = { true },
    role: Role = Role.RadioButton,
    fontSize: TextUnit = 15.sp,
) {
    Row(
        modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(Radius.pill))
            .background(MaterialTheme.colorScheme.surfaceContainer)
            .padding(Space.xs)
            .selectableGroup(),
    ) {
        options.forEach { option ->
            val active = option == selected
            val usable = enabled(option)
            val animatedBg by animateColorAsState(
                targetValue = if (active) MaterialTheme.colorScheme.primary else Color.Transparent,
                animationSpec = spring(stiffness = Spring.StiffnessMediumLow),
                label = "segment_bg",
            )
            val animatedTextColor by animateColorAsState(
                targetValue = when {
                    active -> MaterialTheme.colorScheme.onPrimary
                    usable -> MaterialTheme.colorScheme.onSurface
                    else -> MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
                },
                animationSpec = spring(stiffness = Spring.StiffnessMediumLow),
                label = "segment_txt",
            )
            Box(
                Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(Radius.pill))
                    .background(animatedBg)
                    .selectable(
                        selected = active,
                        enabled = usable,
                        role = role,
                        onClick = { onSelect(option) },
                    )
                    .heightIn(min = 48.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    label(option),
                    fontSize = fontSize,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    fontWeight = if (active) FontWeight.Bold else FontWeight.Medium,
                    color = animatedTextColor,
                )
            }
        }
    }
}

/**
 * Nine networks as nine full-width rows was half the sheet spent on one choice, and the logo —
 * the thing the eye actually looks for — sat after a label of variable length, so no two logos
 * shared an edge. Two columns, logo leading from a fixed inset, name after it: the column of
 * marks becomes the index, and the whole choice fits in a glance instead of a scroll.
 *
 * Chosen is a fill *and* a ring *and* a weight, because a fill alone is colour doing the work
 * of structure.
 */
@Composable
private fun WalletNetworkChoice(
    options: List<WalletOption>,
    selected: String,
    enabled: Boolean,
    onSelect: (String) -> Unit,
) {
    Column(
        Modifier
            .fillMaxWidth()
            .alpha(if (enabled) 1f else 0.6f)
            .selectableGroup(),
        verticalArrangement = Arrangement.spacedBy(Space.m),
    ) {
        options.chunked(2).forEach { pair ->
            Row(horizontalArrangement = Arrangement.spacedBy(Space.m)) {
                pair.forEach { option ->
                    val active = option.network == selected
                    Row(
                        Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(Radius.field))
                            .background(
                                if (active) MaterialTheme.colorScheme.primaryContainer
                                else MaterialTheme.colorScheme.surfaceContainer,
                            )
                            .border(
                                width = if (active) 2.dp else 0.dp,
                                color = if (active) MaterialTheme.colorScheme.primary
                                else Color.Transparent,
                                shape = RoundedCornerShape(Radius.field),
                            )
                            .selectable(
                                selected = active,
                                enabled = enabled,
                                role = Role.RadioButton,
                                onClick = { onSelect(option.network) },
                            )
                            .heightIn(min = 56.dp)
                            .padding(horizontal = Space.m),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        WalletNetworkLogo(option.network)
                        Spacer(Modifier.width(Space.s))
                        Text(
                            option.networkFa,
                            fontSize = 14.sp,
                            fontWeight = if (active) FontWeight.Bold else FontWeight.Medium,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
                // An odd count keeps the grid's columns rather than stretching the last tile
                // across both — the shared leading edge is the point of the layout.
                if (pair.size == 1) Spacer(Modifier.weight(1f))
            }
        }
    }
}

/**
 * The bank's own mark, on a white plate.
 *
 * The plate is not decoration: these are the banks' real logos, drawn for a white page, and a
 * dozen of them are near-black — on the dark theme they would be a black shape on a dark card.
 * The `when` is exhaustive on purpose, so a bank added to the enum cannot ship without one;
 * every logo the list has is already in `res/drawable` as `ic_bank_<name>`.
 */
@Composable
internal fun BankLogo(bank: String, size: Dp = 32.dp) {
    val enumLogo = when (runCatching { Bank.valueOf(bank) }.getOrDefault(Bank.OTHER)) {
        Bank.BLU -> R.drawable.ic_bank_blu
        Bank.SAMAN -> R.drawable.ic_bank_saman
        Bank.REFAH -> R.drawable.ic_bank_refah
        Bank.PASARGAD -> R.drawable.ic_bank_pasargad
        Bank.EGHTESAD_NOVIN -> R.drawable.ic_bank_eghtesad_novin
        Bank.KHAVARMIANEH -> R.drawable.ic_bank_khavar_mianeh
        Bank.SADERAT -> R.drawable.ic_bank_saderat
        Bank.RESALAT -> R.drawable.ic_bank_resalat
        Bank.PARSIAN -> R.drawable.ic_bank_parsian
        Bank.MELLAT -> R.drawable.ic_bank_mellat
        Bank.MELLI -> R.drawable.ic_bank_melli
        Bank.MASKAN -> R.drawable.ic_bank_maskan
        Bank.SEPAH -> R.drawable.ic_bank_sepah
        Bank.TEJARAT -> R.drawable.ic_bank_tejarat
        Bank.OTHER -> null
    }

    val extendedLogo = when {
        bank.contains("KESHAVARZI", ignoreCase = true) || bank.contains("کشاورزی") -> R.drawable.ic_bank_keshavarzi
        bank.contains("SHAHR", ignoreCase = true) || bank.contains("شهر") -> R.drawable.ic_bank_shahr
        bank.contains("AYANDEH", ignoreCase = true) || bank.contains("آینده") -> R.drawable.ic_bank_ayandeh
        bank.contains("SINA", ignoreCase = true) || bank.contains("سینا") -> R.drawable.ic_bank_sina
        bank.contains("POST", ignoreCase = true) || bank.contains("پست") -> R.drawable.ic_bank_post
        bank.contains("MEHR_IRAN", ignoreCase = true) || bank.contains("مهر") -> R.drawable.ic_bank_mehr_iran
        bank.contains("GARDESHGARI", ignoreCase = true) || bank.contains("گردشگری") -> R.drawable.ic_bank_gardeshgari
        bank.contains("SARMAYEH", ignoreCase = true) || bank.contains("سرمایه") -> R.drawable.ic_bank_sarmayeh
        bank.contains("IRAN_ZAMIN", ignoreCase = true) || bank.contains("ایران زمین") -> R.drawable.ic_bank_iran_zamin
        bank.contains("DEY", ignoreCase = true) || bank.contains("دی") -> R.drawable.ic_bank_dey
        bank.contains("SANAT_MADAN", ignoreCase = true) || bank.contains("صنعت و معدن") -> R.drawable.ic_bank_sanat_madan
        bank.contains("TOSEE_TAAVON", ignoreCase = true) || bank.contains("توسعه تعاون") -> R.drawable.ic_bank_tosee_taavon
        bank.contains("GHAVAMIN", ignoreCase = true) || bank.contains("قوامین") -> R.drawable.ic_bank_ghavamin
        bank.contains("KOSAR", ignoreCase = true) || bank.contains("کوثر") -> R.drawable.ic_bank_kosar
        bank.contains("KARAFARIN", ignoreCase = true) || bank.contains("کارآفرین") -> R.drawable.ic_bank_karafarin
        else -> null
    }

    val logo = enumLogo ?: extendedLogo

    Box(
        modifier = Modifier
            .size(size)
            .clip(CircleShape)
            .background(
                if (logo != null) Color.White else MaterialTheme.colorScheme.surfaceVariant,
            )
            // The name is right beside it in text; a second reading of it is noise.
            .clearAndSetSemantics { },
        contentAlignment = Alignment.Center,
    ) {
        if (logo != null) {
            Image(
                painter = painterResource(logo),
                contentDescription = null,
                // The inset scales with the plate: a fixed 4dp that reads as breathing room at
                // 32dp is a hairline at 44dp, and the mark then touches the circle's edge.
                modifier = Modifier
                    .fillMaxSize()
                    .padding(size / 8),
            )
        } else {
            Text("💳", fontSize = (size.value / 2).sp)
        }
    }
}

@Composable
private fun WalletNetworkLogo(network: String, size: Dp = 28.dp) {
    val logo = when (network.lowercase()) {
        "bitcoin" -> R.drawable.ic_network_bitcoin
        "ethereum" -> R.drawable.ic_network_ethereum
        "solana" -> R.drawable.ic_network_solana
        "tron" -> R.drawable.ic_network_tron
        "bsc" -> R.drawable.ic_network_bsc
        "arbitrum" -> R.drawable.ic_network_arbitrum
        "polygon" -> R.drawable.ic_network_polygon
        "optimism" -> R.drawable.ic_network_optimism
        "avalanche" -> R.drawable.ic_network_avalanche
        else -> null
    }

    Box(
        modifier = Modifier
            .size(size)
            .clip(CircleShape)
            .clearAndSetSemantics { },
        contentAlignment = Alignment.Center,
    ) {
        if (logo != null) {
            Image(
                painter = painterResource(logo),
                contentDescription = null,
                modifier = Modifier.fillMaxSize(),
            )
        } else {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(MaterialTheme.colorScheme.surfaceVariant),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    network.take(2).uppercase(),
                    // Scaled, not fixed: at badge size two letters at 10sp are wider than the
                    // disc they sit in, and the fallback clipped instead of naming the chain.
                    fontSize = (size.value * 0.36f).sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun EditSheet(
    // Which holding this sheet is about, not which asset: two rows can share an asset, so the
    // fields below have to be re-seeded per holding.
    key: String,
    type: AssetType,
    holding: Holding?,
    rate: Double?,
    isOverridden: Boolean,
    excluded: Boolean,
    walletBusy: Boolean,
    walletError: String?,
    onWalletEdit: () -> Unit,
    onExcluded: (Boolean) -> Unit,
    onSaveManual: (Double) -> Unit,
    onSaveWallet: (WalletOption, String, () -> Unit) -> Unit,
    onDelete: () -> Unit,
    onRate: (Double?) -> Unit,
    onLabel: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    val current = holding?.amount
    val linkedWallet = holding?.wallet
    val linkedOption = linkedWallet?.let {
        WalletOption(it.network, it.networkFa, it.contract)
    }
    val walletOptions = remember(type.wallets, linkedOption) {
        (type.wallets + listOfNotNull(linkedOption)).distinctBy { it.network }
    }
    val startsWithWallet = type.kind == Kind.CRYPTO && walletOptions.isNotEmpty() &&
        (linkedWallet != null || holding == null)

    // Raw digits live in state; grouping is purely visual so parsing stays exact.
    var source by remember(key, linkedWallet != null, walletOptions.isNotEmpty()) {
        mutableStateOf(if (startsWithWallet) AmountSource.WALLET else AmountSource.MANUAL)
    }
    var text by remember(key) {
        mutableStateOf(current?.let { trimNumber(it, type.dec) } ?: "")
    }
    var rateText by remember { mutableStateOf(rate?.let { trimNumber(it, 0) } ?: "") }
    var editingRate by remember { mutableStateOf(false) }
    var labelText by remember(key) { mutableStateOf(holding?.label.orEmpty()) }
    var naming by remember(key) { mutableStateOf(false) }
    var adjusting by remember { mutableStateOf(false) }
    var deltaText by remember { mutableStateOf("") }
    var confirmDelete by remember { mutableStateOf(false) }
    var manualSubmitted by remember { mutableStateOf(false) }
    var walletAddress by remember(key, linkedWallet?.address) {
        mutableStateOf(linkedWallet?.address.orEmpty())
    }
    var selectedNetwork by remember(key, linkedWallet?.network, walletOptions) {
        mutableStateOf(linkedWallet?.network ?: walletOptions.firstOrNull()?.network.orEmpty())
    }
    var localWalletError by remember { mutableStateOf<String?>(null) }

    val manualAmount = parseAmount(text)
    val selectedWallet = walletOptions.firstOrNull { it.network == selectedNetwork }
    val linkedSelection = linkedWallet != null &&
        linkedWallet.network == selectedNetwork &&
        linkedWallet.address == walletAddress.trim() &&
        linkedWallet.contract == selectedWallet?.contract
    val amount = if (source == AmountSource.MANUAL) manualAmount
    else current?.takeIf { linkedSelection }
    val typedRate = parseAmount(rateText)?.takeIf { it > 0 }
    // While she is typing a manual rate, the "می‌شود …" preview follows what she types, so
    // the consequence of the override is visible before it is committed.
    val previewRate = if (editingRate) typedRate ?: rate else rate
    val muted = MaterialTheme.colorScheme.onSurfaceVariant

    val amountFocus = remember { FocusRequester() }
    val walletAddressFocus = remember { FocusRequester() }
    if (current == null && source == AmountSource.MANUAL) {
        LaunchedEffect(source) {
            delay(250) // let the sheet finish sliding in before the IME starts moving it
            amountFocus.requestFocus()
        }
    }

    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val scope = rememberCoroutineScope()
    fun close(then: () -> Unit) = scope.launch { sheetState.hide(); then() }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        shape = RoundedCornerShape(topStart = Radius.sheet, topEnd = Radius.sheet),
        containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
    ) {
        Column(
            // Scrolls: with the keyboard up — and it comes up on its own when adding — the
            // expanded sections pushed ذخیره off the bottom of shorter phones with no way
            // to reach it.
            Modifier
                .nestedScroll(SheetFlingGuard)
                .navigationBarsPadding()
                .imePadding()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = Space.xl),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                AssetIcon(type, size = 42.dp)
                Spacer(Modifier.width(Space.m))
                Column(Modifier.weight(1f)) {
                    Text(
                        holding?.nameOr(type.fa) ?: type.fa,
                        fontSize = 26.sp,
                        fontWeight = FontWeight.ExtraBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    // Only once the two differ: under "تتر" this line would just say "تتر".
                    if (!holding?.label.isNullOrBlank()) {
                        Text(
                            type.fa,
                            fontSize = 13.sp,
                            color = muted,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
            }

            // Named, not renamed: the asset keeps its own name everywhere else, and this is
            // the label on this particular holding of it — "تتر شخصی" beside "تتر مشترک".
            // Behind a tap, like every other secondary field in this sheet, because most
            // holdings never need one.
            if (holding != null) {
                if (!naming) {
                    TextButton(
                        onClick = { naming = true },
                        modifier = Modifier.padding(top = Space.s),
                    ) {
                        Text(
                            if (holding.label.isBlank()) "اسم دلخواه بذار"
                            else "تغییر اسم",
                            fontSize = 14.sp,
                        )
                    }
                } else {
                    val nameFocus = remember { FocusRequester() }
                    LaunchedEffect(Unit) { nameFocus.requestFocus() }
                    SheetLabel("اسم دلخواه")
                    OutlinedTextField(
                        value = labelText,
                        onValueChange = { labelText = it.take(32) },
                        singleLine = true,
                        // A label that stays, not a placeholder that vanishes at the first
                        // letter — the example is the whole explanation of what this is for.
                        placeholder = { Text("مثلاً ${type.fa} شخصی") },
                        shape = RoundedCornerShape(Radius.field),
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                        keyboardActions = KeyboardActions(
                            onDone = { onLabel(labelText); naming = false },
                        ),
                        modifier = Modifier
                            .fillMaxWidth()
                            .focusRequester(nameFocus)
                            .semantics { contentDescription = "اسم دلخواه" },
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(Space.m)) {
                        TextButton(onClick = { onLabel(labelText); naming = false }) {
                            Text("ذخیره اسم", fontSize = 14.sp)
                        }
                        if (holding.label.isNotBlank()) {
                            TextButton(onClick = {
                                labelText = ""
                                onLabel("")
                                naming = false
                            }) { Text("اسم اصلی", fontSize = 14.sp) }
                        }
                        Spacer(Modifier.weight(1f))
                        TextButton(onClick = { labelText = holding.label; naming = false }) {
                            Text("بستن", fontSize = 14.sp, color = muted)
                        }
                    }
                }
            }

            if (type.kind == Kind.CRYPTO) {
                SheetLabel("موجودی رو چطور وارد می‌کنی؟")
                if (walletOptions.isNotEmpty()) {
                    SegmentedChoice(
                        options = listOf(AmountSource.WALLET, AmountSource.MANUAL),
                        selected = source,
                        label = { if (it == AmountSource.WALLET) "خودکار" else "دستی" },
                        enabled = { !walletBusy },
                        onSelect = {
                            source = it
                            adjusting = false
                            localWalletError = null
                            onWalletEdit()
                        },
                    )
                    // One line under the track instead of a caption inside each pill: the
                    // detail only ever describes the chosen one, and printing both put the
                    // answer to a question she had not asked next to the one she had.
                    Text(
                        if (source == AmountSource.WALLET)
                            "موجودی از آدرس عمومی کیف پول خونده می‌شه."
                        else "مقدار رو خودت وارد می‌کنی.",
                        fontSize = 13.sp,
                        lineHeight = 20.sp,
                        color = muted,
                        modifier = Modifier.padding(top = Space.s, start = Space.xs),
                    )
                } else {
                    Surface(
                        shape = RoundedCornerShape(Radius.field),
                        color = MaterialTheme.colorScheme.surfaceContainer,
                    ) {
                        Text(
                            "برای این رمزارز، شبکه‌ای برای خوندن خودکار پیدا نشد. مقدار رو دستی وارد کن.",
                            fontSize = 14.sp,
                            lineHeight = 22.sp,
                            color = muted,
                            modifier = Modifier.padding(Space.l),
                        )
                    }
                }
            }

            if (source == AmountSource.MANUAL) {
                SheetLabel("چقدر ${bidi(type.unitFa)} داری؟")
                val amountInvalid = manualAmount == null && (text.isNotBlank() || manualSubmitted)
                val goldGramEntry = type.id == "gold18"
                if (goldGramEntry) {
                    // The bazaar weighs طلا in حبه and odd grams, not clean decimals. Two
                    // small fields compose the total; typing into the big one still wins,
                    // because it is re-derived from the small ones the moment either moves.
                    var kernelText by remember(key) { mutableStateOf("") }
                    var gramText by remember(key) { mutableStateOf("") }
                    fun composeFromParts() {
                        GoldInput.totalGrams(gramText, kernelText)?.let { total ->
                            text = trimNumber(total, type.dec)
                            manualSubmitted = false
                        }
                    }
                    fun partsFromTotal() {
                        val total = parseAmount(text) ?: return
                        val (kernels, grams) = GoldInput.split(total)
                        kernelText = if (kernels > 0) kernels.toString() else ""
                        gramText = if (grams > 0.0) trimNumber(grams, 3) else ""
                    }
                    Row(
                        Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(Space.m),
                    ) {
                        OutlinedTextField(
                            value = kernelText,
                            onValueChange = { input ->
                                kernelText = toAsciiDigits(input).filter { it.isDigit() }.take(3)
                                composeFromParts()
                            },
                            singleLine = true,
                            label = { Text("حبه", fontSize = 13.sp) },
                            placeholder = { Text("مثلاً ۳") },
                            textStyle = TextStyle(
                                fontFamily = ModamFigures,
                                fontSize = 22.sp,
                                fontWeight = FontWeight.Bold,
                            ),
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            shape = RoundedCornerShape(Radius.field),
                            modifier = Modifier
                                .weight(1f)
                                .semantics { contentDescription = "تعداد حبه" },
                        )
                        OutlinedTextField(
                            value = gramText,
                            onValueChange = { input ->
                                gramText = input
                                composeFromParts()
                            },
                            singleLine = true,
                            label = { Text("گرم اضافه", fontSize = 13.sp) },
                            placeholder = { Text("مثلاً ۰٫۰۴") },
                            textStyle = TextStyle(
                                fontFamily = ModamFigures,
                                fontSize = 22.sp,
                                fontWeight = FontWeight.Bold,
                            ),
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                            shape = RoundedCornerShape(Radius.field),
                            modifier = Modifier
                                .weight(1f)
                                .semantics { contentDescription = "گرم اضافه" },
                        )
                    }
                    // The composed total, readable back in the words she used — and the
                    // bridge in the other direction: edit the total by hand and the parts
                    // re-split to match, so the two entries never disagree.
                    val totalNow = parseAmount(text)
                    if (totalNow != null && totalNow > 0.0) {
                        Text(
                            "${faHeld(totalNow, type.dec)} گرم (${GoldInput.splitFa(totalNow)})",
                            fontSize = 13.sp,
                            color = muted,
                            modifier = Modifier.padding(top = Space.xs, start = Space.xs),
                        )
                    }
                    TextButton(onClick = { partsFromTotal() }) {
                        Text("بازخوانی از مقدار کل", fontSize = 13.sp)
                    }
                } else {
                    OutlinedTextField(
                value = text,
                onValueChange = { text = it; manualSubmitted = false },
                singleLine = true,
                isError = amountInvalid,
                visualTransformation = GroupedNumber,
                textStyle = TextStyle(
                    fontFamily = ModamFigures,
                    fontSize = 26.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface,
                ),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                // A red outline is colour alone; the words say what to fix, and why ذخیره
                // is not available yet.
                supportingText = if (amountInvalid) ({
                    Text(
                        if (text.isBlank()) "مقدار دارایی رو وارد کن."
                        else "این عدد قابل خوندن نیست. فقط عدد وارد کن.",
                    )
                }) else null,
                shape = RoundedCornerShape(Radius.field),
                modifier = Modifier
                    .fillMaxWidth()
                    .focusRequester(amountFocus)
                    // The visible prompt above is not programmatically attached; this is.
                    .semantics { contentDescription = "چقدر ${type.unitFa} داری؟" },
                )
                }

            // The figure she just typed, in words — the guard against an extra zero.
            if (manualAmount != null && manualAmount % 1.0 == 0.0 && manualAmount >= 1000) {
                Text(
                    "${faWords(manualAmount.toLong())} ${bidi(type.unitFa)}",
                    fontSize = 14.sp,
                    lineHeight = 22.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = Space.s, start = Space.xs, end = Space.xs),
                )
            }

            // Received some, spent some: she types the change and the field above becomes the
            // new total, instead of her doing the sum on paper. ذخیره commits it like any
            // other edit, so there is still exactly one moment money is written.
            if (current != null) {
                if (!adjusting) {
                    TextButton(onClick = { adjusting = true }) {
                        Text("اضافه یا کم کردن", fontSize = 14.sp)
                    }
                } else {
                    val deltaFocus = remember { FocusRequester() }
                    LaunchedEffect(Unit) { deltaFocus.requestFocus() }
                    // A label that stays, not a placeholder that vanishes at the first digit.
                    Text(
                        "مقدار تغییر رو وارد کن",
                        fontSize = 14.sp,
                        color = muted,
                        modifier = Modifier.padding(top = Space.s),
                    )
                    Spacer(Modifier.height(Space.xs))
                    // A delta finer than the asset's own precision is rejected, never
                    // rounded: trimNumber rounds half-up, and half a سکه rounded to a whole
                    // one is money invented — the one direction this app must never err in.
                    val typedDelta = parseAmount(deltaText)?.takeIf { it > 0 }
                    val delta = typedDelta?.takeIf { parseAmount(trimNumber(it, type.dec)) == it }
                    OutlinedTextField(
                        value = deltaText,
                        onValueChange = { deltaText = it },
                        singleLine = true,
                        visualTransformation = GroupedNumber,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        supportingText = if (typedDelta != null && delta == null) ({
                            Text(
                                if (type.dec == 0) "فقط عدد کامل وارد کن."
                                else "حداکثر ${faNumber(type.dec.toDouble())} رقم اعشار وارد کن.",
                            )
                        }) else null,
                        shape = RoundedCornerShape(Radius.field),
                        modifier = Modifier
                            .fillMaxWidth()
                            .focusRequester(deltaFocus)
                            .semantics {
                                contentDescription = "مقدار تغییر ${type.unitFa}"
                            },
                    )
                    val base = amount ?: current
                    fun apply(next: Double) {
                        text = trimNumber(next, type.dec)
                        deltaText = ""
                        adjusting = false
                    }
                    // Air between the two opposite intents: an edge mis-tap here flips the
                    // sign of a money adjustment.
                    Row(horizontalArrangement = Arrangement.spacedBy(Space.m)) {
                        TextButton(
                            onClick = { delta?.let { apply(base + it) } },
                            enabled = delta != null,
                        ) { Text("＋ اضافه کن", fontSize = 14.sp, fontWeight = FontWeight.Bold) }
                        TextButton(
                            onClick = { delta?.let { apply(base - it) } },
                            // A holding cannot go below nothing — taking out more than is
                            // there is a typo, not a request.
                            enabled = delta != null && base - delta >= 0,
                        ) { Text("− کم کن", fontSize = 14.sp, fontWeight = FontWeight.Bold) }
                        Spacer(Modifier.weight(1f))
                        TextButton(onClick = { adjusting = false; deltaText = "" }) {
                            Text("بستن", fontSize = 14.sp, color = muted)
                        }
                    }
                }
            }

            } else {
                SheetLabel("شبکه رمزارز")
                WalletNetworkChoice(
                    options = walletOptions,
                    selected = selectedNetwork,
                    enabled = !walletBusy,
                    onSelect = {
                        if (selectedNetwork != it) walletAddress = ""
                        selectedNetwork = it
                        localWalletError = null
                        onWalletEdit()
                    },
                )

                SheetLabel("آدرس عمومی کیف پول")
                OutlinedTextField(
                    value = walletAddress,
                    onValueChange = {
                        walletAddress = it.take(128)
                        localWalletError = null
                        onWalletEdit()
                    },
                    singleLine = true,
                    enabled = !walletBusy,
                    isError = localWalletError != null,
                    textStyle = TextStyle(
                        fontSize = 16.sp,
                        textAlign = TextAlign.Left,
                        textDirection = TextDirection.Ltr,
                    ),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Ascii),
                    supportingText = localWalletError?.let { message ->
                        { Text(message) }
                    },
                    shape = RoundedCornerShape(Radius.field),
                    modifier = Modifier
                        .fillMaxWidth()
                        .focusRequester(walletAddressFocus)
                        .semantics { contentDescription = "آدرس عمومی کیف پول" },
                )
                Text(
                    "فقط آدرس عمومی رو وارد کن. عبارت بازیابی یا کلید خصوصی رو هیچ‌وقت وارد نکن.",
                    fontSize = 13.sp,
                    lineHeight = 20.sp,
                    color = muted,
                    modifier = Modifier.padding(horizontal = Space.xs),
                )
                Text(
                    "برای خوندن موجودی، آدرس به سرویس عمومی همون شبکه فرستاده می‌شه.",
                    fontSize = 12.sp,
                    lineHeight = 19.sp,
                    color = muted,
                    modifier = Modifier.padding(horizontal = Space.xs, vertical = Space.xs),
                )

                if (linkedSelection && current != null) {
                    Spacer(Modifier.height(Space.l))
                    // Full width, like everything else in the sheet. Wrapping its content made
                    // it a narrower card directly above a wider one, which read as two
                    // unrelated objects rather than one answer in two units.
                    Surface(
                        shape = RoundedCornerShape(Radius.card),
                        color = MaterialTheme.colorScheme.primaryContainer,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Column(Modifier.padding(Space.l)) {
                            Text(
                                "موجودی فعلی",
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onPrimaryContainer,
                            )
                            BasicText(
                                // faHeld, not the asset's full precision: the row outside this
                                // sheet says ۱۰٬۷۰۹٫۱۳ and this said ۱۰٬۷۰۹٫۱۳۵۶۸۱ — one
                                // number in two lengths reads as two different numbers.
                                text = "${faHeld(current, type.dec)} ${bidi(type.unitFa)}",
                                maxLines = 1,
                                autoSize = TextAutoSize.StepBased(
                                    minFontSize = 18.sp,
                                    maxFontSize = 30.sp,
                                ),
                                modifier = Modifier.padding(top = 2.dp),
                                style = figureStyle(
                                    MaterialTheme.colorScheme.onPrimaryContainer,
                                    FontWeight.ExtraBold,
                                ),
                            )
                            // The same money in Toman, inside the same card rather than in a
                            // second one below it. It is the conversion of the figure above,
                            // not a separate finding.
                            previewRate?.let { rate ->
                                BasicText(
                                    text = "≈ ${faCompact(current * rate, 3, pad = true)} تومان",
                                    maxLines = 1,
                                    autoSize = TextAutoSize.StepBased(
                                        minFontSize = 13.sp,
                                        maxFontSize = 17.sp,
                                    ),
                                    modifier = Modifier.padding(top = Space.xs),
                                    style = figureStyle(
                                        MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f),
                                        FontWeight.SemiBold,
                                    ),
                                )
                            }
                            Text(
                                when {
                                    walletBusy -> "در حال گرفتن موجودی"
                                    walletError != null -> walletError
                                    else -> "آخرین بار: ${faAgo(linkedWallet.updatedAt, System.currentTimeMillis())}"
                                },
                                fontSize = 12.sp,
                                lineHeight = 19.sp,
                                color = if (walletError != null) MaterialTheme.colorScheme.error
                                else MaterialTheme.colorScheme.onPrimaryContainer,
                                modifier = Modifier.semantics {
                                    if (walletError != null) liveRegion = LiveRegionMode.Polite
                                },
                            )
                        }
                    }
                } else if (walletError != null) {
                    Text(
                        walletError,
                        fontSize = 13.sp,
                        lineHeight = 20.sp,
                        color = MaterialTheme.colorScheme.error,
                        modifier = Modifier
                            .padding(horizontal = Space.xs, vertical = Space.s)
                            .semantics { liveRegion = LiveRegionMode.Polite },
                    )
                }
            }

            // Not when the read-balance card is up: that card already carries the Toman line,
            // and the two of them said the same conversion twice, in two differently sized
            // boxes, one under the other.
            val balanceCardShown = source == AmountSource.WALLET && linkedSelection && current != null
            if (amount != null && previewRate != null && type.id != TOMAN_ID && !balanceCardShown) {
                Spacer(Modifier.height(Space.l))
                Box(
                    Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(Radius.card))
                        .background(MaterialTheme.colorScheme.primaryContainer)
                        .padding(Space.l),
                ) {
                    // Same figure the row shows, so it carries the same three decimals — and
                    // shrinks rather than wrapping, since it is one sentence.
                    BasicText(
                        text = "یعنی ${faCompact(amount * previewRate, 3, pad = true)} تومان",
                        maxLines = 1,
                        autoSize = TextAutoSize.StepBased(minFontSize = 14.sp, maxFontSize = 18.sp),
                        style = TextStyle(
                            fontFamily = ModamFigures,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onPrimaryContainer,
                        ),
                    )
                }
            }

            if (type.id != TOMAN_ID) {
                Spacer(Modifier.height(Space.m))
                if (!editingRate) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            if (rate == null) "نرخ پیدا نشد"
                            else "هر ${bidi(type.unitFa)}: ${faNumber(rate)} تومان" +
                                if (isOverridden) "  (دستی وارد شده)" else "",
                            fontSize = 14.sp,
                            color = muted,
                            modifier = Modifier.weight(1f),
                        )
                        TextButton(onClick = { editingRate = true }) {
                            Text("تغییر نرخ", fontSize = 14.sp)
                        }
                    }
                } else {
                    // She tapped "تغییر نرخ" to type a rate; typing must land in this field,
                    // not silently keep appending to the amount above.
                    val rateFocus = remember { FocusRequester() }
                    LaunchedEffect(Unit) { rateFocus.requestFocus() }
                    Text("هر ${bidi(type.unitFa)} چند تومان؟", fontSize = 14.sp, color = muted)
                    Spacer(Modifier.height(Space.xs))
                    OutlinedTextField(
                        value = rateText,
                        onValueChange = { rateText = it },
                        singleLine = true,
                        visualTransformation = GroupedNumber,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        shape = RoundedCornerShape(Radius.field),
                        modifier = Modifier
                            .fillMaxWidth()
                            .focusRequester(rateFocus)
                            .semantics { contentDescription = "هر ${type.unitFa} چند تومان؟" },
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(Space.m)) {
                        TextButton(onClick = { onRate(typedRate); editingRate = false }) {
                            Text("ذخیره نرخ", fontSize = 14.sp)
                        }
                        if (isOverridden) {
                            TextButton(onClick = {
                                onRate(null); rateText = ""; editingRate = false
                            }) { Text("برگشت به نرخ خودکار", fontSize = 14.sp) }
                        }
                    }
                }
            }

            if (current != null) {
                Spacer(Modifier.height(Space.m))
                // toggleable on the row: label and switch become one named control, and the
                // words are the hit target too.
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .toggleable(
                            value = !excluded,
                            role = Role.Switch,
                            onValueChange = { on -> onExcluded(!on) },
                        )
                        // With onCheckedChange = null the Switch no longer carries its own
                        // 48dp minimum; the row has to.
                        .heightIn(min = 48.dp),
                ) {
                    Column(Modifier.weight(1f)) {
                        Text("توی جمع حساب بشه", fontSize = 15.sp)
                        Text(
                            // e.g. savings she does not want staring back from the total
                            "اگه فعلاً نمی‌خوای توی جمع بیاد، خاموشش کن.",
                            fontSize = 12.sp,
                            color = muted,
                        )
                    }
                    Switch(checked = !excluded, onCheckedChange = null)
                }
            }

            Spacer(Modifier.height(Space.xl))
            Button(
                onClick = {
                    if (source == AmountSource.MANUAL) {
                        manualSubmitted = true
                        if (manualAmount != null) close { onSaveManual(manualAmount) }
                        else amountFocus.requestFocus()
                    } else {
                        val option = selectedWallet ?: return@Button
                        if (walletAddress.isBlank()) {
                            localWalletError = "آدرس عمومی کیف پول رو وارد کن."
                            walletAddressFocus.requestFocus()
                        } else if (!isWalletAddressFormatValid(option.network, walletAddress)) {
                            localWalletError = "این آدرس با شبکه انتخاب‌شده جور نیست."
                            walletAddressFocus.requestFocus()
                        } else {
                            onSaveWallet(option, walletAddress.trim()) { close(onDismiss) }
                        }
                    }
                },
                enabled = source == AmountSource.MANUAL || (!walletBusy && selectedWallet != null),
                shape = RoundedCornerShape(Radius.pill),
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 60.dp),
            ) {
                if (source == AmountSource.WALLET && walletBusy) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(20.dp),
                        strokeWidth = 2.dp,
                        color = MaterialTheme.colorScheme.onPrimary,
                    )
                    Spacer(Modifier.width(Space.s))
                }
                Text(
                    when {
                        source == AmountSource.MANUAL && linkedWallet != null -> "ذخیره مقدار دستی"
                        source == AmountSource.MANUAL -> "ذخیره"
                        linkedSelection -> "گرفتن دوباره موجودی"
                        else -> "چک و ذخیره"
                    },
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                )
            }

            if (current != null) {
                // One stray tap must not erase a holding: the first tap only changes the
                // label to a question, the second actually deletes.
                TextButton(
                    onClick = { if (confirmDelete) close(onDelete) else confirmDelete = true },
                    colors = ButtonDefaults.textButtonColors(
                        contentColor = MaterialTheme.colorScheme.error,
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = Space.xs),
                ) {
                    Text(
                        if (confirmDelete) "مطمئنی؟ برای حذف دوباره بزن"
                        else "حذف این دارایی",
                        fontSize = 15.sp,
                        fontWeight = if (confirmDelete) FontWeight.Bold else FontWeight.Normal,
                        // Announced, or the two-tap safeguard is invisible to TalkBack — a
                        // second double-tap deletes with no confirmation ever perceived.
                        modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
                    )
                }
            }

            Spacer(Modifier.height(Space.l))
        }
    }
}

/**
 * Full detail screen for an installment obligation.
 * Shows provider, totals, and the complete payment schedule.
 */
@Composable
fun ObligationDetailScreen(
    obligation: InstallmentObligation,
    payments: List<InstallmentPayment>,
    onMarkPaid: (String) -> Unit,
    onPayOffRemaining: (String) -> Unit,
    onArchive: (String) -> Unit,
    onBack: () -> Unit,
) {
    val paidCount = payments.count { it.paidDate != null }
    val allPaid = paidCount == obligation.installmentCount
    val totalRemaining = (obligation.installmentCount - paidCount).toLong() * obligation.installmentRial

    Surface(
        color = MaterialTheme.colorScheme.background,
        modifier = Modifier.fillMaxSize(),
    ) {
        Column(
            Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .verticalScroll(rememberScrollState()),
        ) {
            // Header
            Row(
                Modifier.fillMaxWidth().padding(horizontal = Space.l, vertical = Space.m),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconButton(onClick = onBack) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "بازگشت")
                }
                Column(Modifier.weight(1f)) {
                    Text(
                        obligation.provider.ifBlank { "اقساط" },
                        fontSize = 22.sp,
                        fontWeight = FontWeight.ExtraBold,
                    )
                    Text(
                        "${paidCount}/${obligation.installmentCount} قسط پرداخت شده",
                        fontSize = 13.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            // Summary card
            Card(
                shape = RoundedCornerShape(Radius.card),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant,
                ),
                modifier = Modifier.fillMaxWidth().padding(horizontal = Space.l),
            ) {
                Column(Modifier.padding(Space.l)) {
                    DetailRow("قیمت کل", "${faNumber(obligation.totalRial / 10.0)} تومان")
                    DetailRow("پیش پرداخت", "${faNumber(obligation.downPaymentRial / 10.0)} تومان")
                    DetailRow("هر قسط", "${faNumber(obligation.installmentRial / 10.0)} تومان")
                    Spacer(Modifier.height(Space.s))
                    HorizontalDivider()
                    Spacer(Modifier.height(Space.s))
                    DetailRow(
                        "باقیمانده",
                        "${faNumber(totalRemaining / 10.0)} تومان",
                        bold = true,
                    )
                    val remainingToman = totalRemaining / 10.0
                    val remainingCount = (obligation.installmentCount - paidCount).coerceAtLeast(0)
                    val avgMonths = (remainingCount + 1.0) / 2.0
                    val reliefToman = if (remainingToman > 0 && avgMonths > 0) {
                        val discount = Math.pow(1.0 + 0.45, avgMonths / 12.0)
                        (remainingToman - (remainingToman / discount)).toLong().coerceAtLeast(0L)
                    } else 0L
                    if (reliefToman > 0L) {
                        Spacer(Modifier.height(Space.xs))
                        DetailRow(
                            "سود از تورم (کاهش ارزش بدهی)",
                            "-${faNumber(reliefToman.toDouble())} تومان",
                            bold = true,
                            valueColor = Hero.mint,
                        )
                    }
                }
            }

            if (obligation.notes.isNotBlank()) {
                Text(
                    obligation.notes,
                    fontSize = 13.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = Space.l, vertical = Space.m),
                )
            }

            // Actions
            if (!allPaid) {
                Row(
                    Modifier.fillMaxWidth().padding(horizontal = Space.l, vertical = Space.s),
                    horizontalArrangement = Arrangement.spacedBy(Space.m),
                ) {
                    Button(
                        onClick = { onPayOffRemaining(obligation.id) },
                        modifier = Modifier.weight(1f),
                    ) {
                        Text("تسویه کل باقیمانده")
                    }
                }
            } else {
                Button(
                    onClick = { onArchive(obligation.id) },
                    modifier = Modifier.fillMaxWidth().padding(horizontal = Space.l, vertical = Space.s),
                ) {
                    Text("بایگانی")
                }
            }

            // Payment schedule
            Text(
                " برنامه پرداخت",
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(horizontal = Space.l, vertical = Space.m),
            )

            Card(
                shape = RoundedCornerShape(Radius.card),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant,
                ),
                modifier = Modifier.fillMaxWidth().padding(horizontal = Space.l),
            ) {
                Column {
                    payments.sortedBy { it.installmentNumber }.forEach { payment ->
                        val isPaid = payment.paidDate != null
                        val badge = if (!isPaid) installmentDueBadge(payment.dueDate) else null
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .clickable(enabled = !isPaid) { onMarkPaid(payment.id) }
                                .padding(horizontal = Space.l, vertical = Space.m),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            // Status indicator
                            Box(
                                Modifier
                                    .size(8.dp)
                                    .clip(CircleShape)
                                    .background(
                                        when {
                                            isPaid -> Color(0xFF2E9E5B)
                                            badge?.level == UrgencyLevel.OVERDUE -> MaterialTheme.colorScheme.error
                                            badge?.level == UrgencyLevel.WARNING -> MaterialTheme.colorScheme.secondary
                                            else -> MaterialTheme.colorScheme.outline
                                        }
                                    ),
                            )
                            Spacer(Modifier.width(Space.m))
                            Column(Modifier.weight(1f)) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text(
                                        "قسط ${faNumber(payment.installmentNumber.toDouble())}",
                                        fontSize = 14.sp,
                                        fontWeight = FontWeight.Medium,
                                    )
                                    if (badge != null) {
                                        Spacer(Modifier.width(Space.s))
                                        val badgeColor = when (badge.level) {
                                            UrgencyLevel.OVERDUE -> MaterialTheme.colorScheme.error
                                            UrgencyLevel.WARNING -> MaterialTheme.colorScheme.secondary
                                            UrgencyLevel.NORMAL -> MaterialTheme.colorScheme.onSurfaceVariant
                                        }
                                        val badgeBg = when (badge.level) {
                                            UrgencyLevel.OVERDUE -> MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.6f)
                                            UrgencyLevel.WARNING -> MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.6f)
                                            UrgencyLevel.NORMAL -> MaterialTheme.colorScheme.surfaceContainerHighest
                                        }
                                        Surface(
                                            color = badgeBg,
                                            shape = RoundedCornerShape(Radius.pill),
                                        ) {
                                            Text(
                                                badge.text,
                                                fontSize = 10.sp,
                                                fontWeight = FontWeight.SemiBold,
                                                color = badgeColor,
                                                modifier = Modifier.padding(horizontal = Space.s, vertical = 2.dp),
                                            )
                                        }
                                    }
                                }
                                Text(
                                    formatDate(payment.dueDate),
                                    fontSize = 12.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                            Text(
                                "${faNumber(payment.amountRial / 10.0)}",
                                fontSize = 14.sp,
                                fontFamily = ModamFigures,
                                fontWeight = FontWeight.Bold,
                                color = if (isPaid) Color(0xFF2E9E5B) else MaterialTheme.colorScheme.onBackground,
                            )
                            if (!isPaid) {
                                Spacer(Modifier.width(Space.s))
                                Text(
                                    "پرداخت",
                                    fontSize = 12.sp,
                                    color = MaterialTheme.colorScheme.primary,
                                    fontWeight = FontWeight.SemiBold,
                                )
                            }
                        }
                    }
                }
            }

            Spacer(Modifier.height(Space.xl))
        }
    }
}

@Composable
private fun DetailRow(
    label: String,
    value: String,
    bold: Boolean = false,
    valueColor: Color = Color.Unspecified,
) {
    Row(
        Modifier.fillMaxWidth().padding(vertical = Space.xs),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            label,
            fontSize = 13.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.weight(1f),
        )
        Text(
            value,
            fontSize = 14.sp,
            fontFamily = ModamFigures,
            fontWeight = if (bold) FontWeight.Bold else FontWeight.Normal,
            color = valueColor,
        )
    }
}

private fun formatDate(millis: Long): String {
    if (millis <= 0L) return ""
    val jalali = jalaliOf(tehranDay(millis))
    val monthName = MONTHS.getOrElse(jalali.month - 1) { "" }
    return "${faNumber(jalali.day.toDouble())} $monthName ${jalali.year}"
}

/**
 * Bottom sheet form for marking a transaction as a credit/installment purchase.
 * Pre-fills the down payment from the transaction amount.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CreditPurchaseForm(
    entry: LedgerEntry,
    onSave: (provider: String, total: Long, installments: Int, amount: Long, startDate: Long, notes: String) -> Unit,
    onDismiss: () -> Unit,
) {
    val downPayment = kotlin.math.abs(entry.txn.signedRial ?: entry.txn.amountRial ?: 0L)
    var provider by remember { mutableStateOf("") }
    var totalText by remember { mutableStateOf("") }
    var installmentsText by remember { mutableStateOf("") }
    var amountText by remember { mutableStateOf("") }
    var notes by remember { mutableStateOf("") }
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

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
            Text(
                "ثبت خرید اعتباری",
                fontSize = 20.sp,
                fontWeight = FontWeight.ExtraBold,
                modifier = Modifier.padding(bottom = Space.l),
            )

            Text(
                "پیش پرداخت: ${faNumber(downPayment / 10.0)} تومان",
                fontSize = 13.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(bottom = Space.m),
            )

            // Provider name
            OutlinedTextField(
                value = provider,
                onValueChange = { provider = it },
                label = { Text("نام فروشگاه / ارائه‌دهنده") },
                modifier = Modifier.fillMaxWidth().padding(bottom = Space.m),
                singleLine = true,
            )

            // Total price
            OutlinedTextField(
                value = totalText,
                onValueChange = { totalText = it.filter { c -> c.isDigit() || c == ',' } },
                label = { Text("قیمت کل (ریال)") },
                modifier = Modifier.fillMaxWidth().padding(bottom = Space.m),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                singleLine = true,
            )

            // Number of installments
            OutlinedTextField(
                value = installmentsText,
                onValueChange = { installmentsText = it.filter { c -> c.isDigit() } },
                label = { Text("تعداد اقساط") },
                modifier = Modifier.fillMaxWidth().padding(bottom = Space.m),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                singleLine = true,
            )

            // Installment amount
            OutlinedTextField(
                value = amountText,
                onValueChange = { amountText = it.filter { c -> c.isDigit() || c == ',' } },
                label = { Text("مبلغ هر قسط (ریال)") },
                modifier = Modifier.fillMaxWidth().padding(bottom = Space.m),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                singleLine = true,
            )

            // Notes
            OutlinedTextField(
                value = notes,
                onValueChange = { notes = it },
                label = { Text("یادداشت (اختیاری)") },
                modifier = Modifier.fillMaxWidth().padding(bottom = Space.l),
                maxLines = 2,
            )

            Button(
                onClick = {
                    val t = totalText.replace(",", "").toLongOrNull()
                    val i = installmentsText.toIntOrNull()
                    val a = amountText.replace(",", "").toLongOrNull()
                    if (t != null && i != null && a != null && t > 0 && i > 0 && a > 0) {
                        onSave(provider, t, i, a, System.currentTimeMillis(), notes)
                    }
                },
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text("ذخیره")
            }
        }
    }
}

/**
 * Bottom sheet for directly creating an installment obligation (e.g. cash or non-SMS BNPL).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DirectObligationSheet(
    onSave: (provider: String, totalRial: Long, installments: Int, amountRial: Long, startDate: Long, notes: String) -> Unit,
    onDismiss: () -> Unit,
) {
    var provider by remember { mutableStateOf("") }
    var totalText by remember { mutableStateOf("") }
    var installmentsText by remember { mutableStateOf("4") }
    var amountText by remember { mutableStateOf("") }
    var notes by remember { mutableStateOf("") }
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    val totalToman = totalText.filter { it.isDigit() }.toLongOrNull() ?: 0L
    val count = installmentsText.filter { it.isDigit() }.toIntOrNull() ?: 0

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
            Text(
                "ثبت مستقیم قسط",
                fontSize = 20.sp,
                fontWeight = FontWeight.ExtraBold,
                modifier = Modifier.padding(bottom = Space.l),
            )

            OutlinedTextField(
                value = provider,
                onValueChange = { provider = it },
                label = { Text("نام فروشگاه یا ارائه‌دهنده (مثلاً اسنپ‌پی)") },
                modifier = Modifier.fillMaxWidth().padding(bottom = Space.m),
                singleLine = true,
            )

            OutlinedTextField(
                value = totalText,
                onValueChange = {
                    totalText = it.filter { c -> c.isDigit() || c == ',' }
                    val t = it.filter { c -> c.isDigit() }.toLongOrNull() ?: 0L
                    val c = installmentsText.filter { ch -> ch.isDigit() }.toIntOrNull() ?: 0
                    if (t > 0 && c > 0) {
                        amountText = (t / c).toString()
                    }
                },
                label = { Text("قیمت کل (تومان)") },
                modifier = Modifier.fillMaxWidth().padding(bottom = Space.m),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                singleLine = true,
            )

            OutlinedTextField(
                value = installmentsText,
                onValueChange = {
                    installmentsText = it.filter { c -> c.isDigit() }
                    val c = it.filter { ch -> ch.isDigit() }.toIntOrNull() ?: 0
                    val t = totalText.filter { ch -> ch.isDigit() }.toLongOrNull() ?: 0L
                    if (t > 0 && c > 0) {
                        amountText = (t / c).toString()
                    }
                },
                label = { Text("تعداد اقساط") },
                modifier = Modifier.fillMaxWidth().padding(bottom = Space.m),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                singleLine = true,
            )

            OutlinedTextField(
                value = amountText,
                onValueChange = { amountText = it.filter { c -> c.isDigit() || c == ',' } },
                label = { Text("مبلغ هر قسط (تومان)") },
                modifier = Modifier.fillMaxWidth().padding(bottom = Space.m),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                singleLine = true,
            )

            OutlinedTextField(
                value = notes,
                onValueChange = { notes = it },
                label = { Text("یادداشت (اختیاری)") },
                modifier = Modifier.fillMaxWidth().padding(bottom = Space.l),
                singleLine = true,
            )

            Button(
                onClick = {
                    val t = totalText.filter { it.isDigit() }.toLongOrNull()
                    val i = installmentsText.filter { it.isDigit() }.toIntOrNull()
                    val a = amountText.filter { it.isDigit() }.toLongOrNull()
                    if (provider.isNotBlank() && t != null && i != null && a != null && t > 0 && i > 0 && a > 0) {
                        onSave(provider.trim(), t * 10L, i, a * 10L, System.currentTimeMillis(), notes.trim())
                    }
                },
                enabled = provider.isNotBlank() && totalToman > 0 && count > 0,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text("ذخیره اقساط")
            }
        }
    }
}

/**
 * Bottom sheet for linking a transaction to an unpaid installment.
 * Shows matching installments (same amount, close date) first.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun LinkInstallmentPicker(
    entry: LedgerEntry,
    state: UiState,
    onLink: (paymentId: String) -> Unit,
    onDismiss: () -> Unit,
) {
    val txnAmount = kotlin.math.abs(entry.txn.signedRial ?: entry.txn.amountRial ?: 0L)
    val txnDate = entry.txn.at

    // Find unpaid installments that match the amount (exact) and are within ±30 days.
    val candidates = state.obligationPayments.values.flatten()
        .filter { it.paidDate == null && it.amountRial == txnAmount }
        .sortedBy { kotlin.math.abs(it.dueDate - txnDate) }

    // Also show all unpaid installments if no exact match.
    val allUnpaid = state.obligationPayments.values.flatten()
        .filter { it.paidDate == null }
        .sortedBy { it.dueDate }

    val displayList = if (candidates.isNotEmpty()) candidates else allUnpaid

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = MaterialTheme.colorScheme.surface,
    ) {
        Column(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = Space.l)
                .padding(bottom = Space.xl),
        ) {
            Text(
                "پیوند به قسط",
                fontSize = 20.sp,
                fontWeight = FontWeight.ExtraBold,
                modifier = Modifier.padding(bottom = Space.s),
            )
            Text(
                "مبلغ تراکنش: ${faNumber(txnAmount / 10.0)} تومان",
                fontSize = 13.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(bottom = Space.m),
            )

            if (displayList.isEmpty()) {
                Text(
                    "قسط بدون پرداختی با این مبلغ یافت نشد",
                    fontSize = 14.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(vertical = Space.l),
                )
            } else {
                if (candidates.isNotEmpty()) {
                    Text(
                        "تطبیق دقیق:",
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(bottom = Space.s),
                    )
                }
                displayList.forEach { payment ->
                    // Find the obligation name.
                    val obName = state.obligations.firstOrNull { it.id == payment.obligationId }
                        ?.provider?.ifBlank { "اقساط" } ?: "اقساط"
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .clickable { onLink(payment.id) }
                            .padding(vertical = Space.m),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(Modifier.weight(1f)) {
                            Text(
                                obName,
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Medium,
                            )
                            Text(
                                "قسط ${faNumber(payment.installmentNumber.toDouble())} — ${formatDate(payment.dueDate)}",
                                fontSize = 12.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        Text(
                            "${faNumber(payment.amountRial / 10.0)}",
                            fontSize = 14.sp,
                            fontFamily = ModamFigures,
                            fontWeight = FontWeight.Bold,
                        )
                        Spacer(Modifier.width(Space.s))
                        Text(
                            "انتخاب",
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.primary,
                            fontWeight = FontWeight.SemiBold,
                        )
                    }
                }
            }
        }
    }
}
