package com.doxigo.muchtoman

import androidx.activity.compose.BackHandler
import androidx.activity.compose.LocalActivity
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Clear
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.fragment.app.FragmentActivity
import com.doxigo.muchtoman.ui.components.CanvasCard
import com.doxigo.muchtoman.ui.components.CanvasGlyph
import com.doxigo.muchtoman.ui.components.CanvasPageHeader
import com.doxigo.muchtoman.ui.components.OutlineIconButton
import com.doxigo.muchtoman.ui.components.OutlinedTextField
import com.doxigo.muchtoman.ui.components.PillChip
import kotlin.math.abs
import kotlin.math.roundToInt

/*
 * The ledger tab (دفتر), as docs/design/canvas/project/Ledger.dc.html draws it: the page header
 * with the month's count, this week as seven bars, four quick filters, and the day-grouped
 * rows. Every number on it is read from the ledger; nothing is sample data.
 *
 * Everything the old timeline could do is still here: search and the filter sheet behind the
 * two round buttons, selection and its batch bar (long-press a row), manual add, CSV export and
 * the hidden-rows reveal at the end of the chip row, and the per-row actions in the long-press
 * menu. Tapping a row opens its detail; tapping a hidden one restores it.
 */

// ── Pure model, unit-tested ──────────────────────────────────────────────────────────────────

/** Saturday = 0 … Friday = 6, for a Tehran day number. Day 0 (1970-01-01) was a Thursday. */
internal fun persianWeekday(day: Long): Int = Math.floorMod(day + 5, 7L).toInt()

/** The four quick filters on the canvas. */
internal enum class LedgerQuickFilter { ALL, UNCATEGORISED, DEPOSITS, INSTALLMENTS }

/** What the this-week card shows. */
internal data class LedgerWeek(
    /** Outflow per day, Saturday first, in Rial. Days after today are 0. */
    val daysRial: List<Long>,
    /** Index of today in [daysRial]. */
    val todayIndex: Int,
    /** This week's outflow so far. */
    val totalRial: Long,
    /** Last week's outflow from its Saturday to this same moment of its week. */
    val lastWeekSoFarRial: Long,
    /** Whether last week can be compared at all: something was recorded on or before it. */
    val hasBaseline: Boolean,
) {
    /** Fractional change against last week, or null without a baseline. */
    val change: Double?
        get() = if (!hasBaseline || lastWeekSoFarRial <= 0L) null
        else (totalRial - lastWeekSoFarRial).toDouble() / lastWeekSoFarRial
}

/** An entry that counts as money she spent: going out, not a transfer, duplicate or hidden. */
internal fun isOutflow(entry: LedgerEntry): Boolean =
    !entry.duplicate && !entry.transfer && !entry.hidden && (entry.txn.signedRial ?: 0L) < 0L

internal fun ledgerWeek(entries: List<LedgerEntry>, now: Long): LedgerWeek {
    val today = tehranDay(now)
    val todayIndex = persianWeekday(today)
    val saturday = today - todayIndex
    val days = LongArray(7)
    var lastWeek = 0L
    val lastSaturday = saturday - 7
    val sameMomentLastWeek = now - 7 * DAY_MS
    var earliest = Long.MAX_VALUE
    for (e in entries) {
        if (!e.duplicate) earliest = minOf(earliest, e.txn.day)
        if (!isOutflow(e)) continue
        val d = e.txn.day
        val rial = -(e.txn.signedRial ?: 0L)
        if (d in saturday..today) days[(d - saturday).toInt()] += rial
        else if (d in lastSaturday until saturday && e.txn.at <= sameMomentLastWeek) lastWeek += rial
    }
    return LedgerWeek(
        daysRial = days.toList(),
        todayIndex = todayIndex,
        totalRial = days.sum(),
        lastWeekSoFarRial = lastWeek,
        hasBaseline = earliest < saturday,
    )
}

/** The rows an installment filter keeps: linked to a purchase or a payment, or named one. */
internal fun installmentRefs(
    obligations: List<InstallmentObligation>,
    payments: Map<String, List<InstallmentPayment>>,
): Set<String> = buildSet {
    obligations.forEach { if (it.linkedTxnRef.isNotBlank()) add(it.linkedTxnRef) }
    payments.values.flatten().forEach { if (it.linkedTxnRef.isNotBlank()) add(it.linkedTxnRef) }
}

internal fun matchesQuickFilter(
    entry: LedgerEntry,
    filter: LedgerQuickFilter,
    installments: Set<String>,
    note: String = "",
): Boolean = when (filter) {
    LedgerQuickFilter.ALL -> true
    LedgerQuickFilter.UNCATEGORISED -> isUncategorised(entry)
    LedgerQuickFilter.DEPOSITS -> entry.txn.direction == "in" && !entry.transfer
    LedgerQuickFilter.INSTALLMENTS -> entry.txn.ref in installments ||
        "قسط" in entry.txn.merchant || "قسط" in note
}

internal fun isUncategorised(entry: LedgerEntry): Boolean =
    !entry.transfer && entry.categoryId == CAT_UNCATEGORISED

/** «۱۰:۴۲», Tehran time. */
internal fun faClockOf(at: Long): String {
    val minute = (Math.floorMod(at + TEHRAN_OFFSET_MS, DAY_MS) / 60_000L).toInt()
    return persianDigits("%02d:%02d".format(java.util.Locale.US, minute / 60, minute % 60))
}

/** Digits only, no grouping: years and clocks. */
internal fun persianDigits(s: String): String = buildString {
    for (c in s) append(if (c in '0'..'9') '۰' + (c - '0') else c)
}

/** «آبان ۱۴۰۵ · ۴۸ تراکنش»: this Jalali month and how many transactions are in it. */
internal fun ledgerCaption(entries: List<LedgerEntry>, today: Long): String {
    val j = jalaliOf(today)
    val from = jalaliDay(j.year, j.month, 1)
    val until = from + jalaliMonthLength(j.year, j.month)
    val count = entries.count { !it.duplicate && !it.hidden && it.txn.day in from until until }
    return "${MONTHS[j.month - 1]} ${persianDigits(j.year.toString())} · ${fa(count)} تراکنش"
}

/** «−۱۸۵٬۰۰۰»: the whole toman figure with its sign, kept left-to-right. */
internal fun faSignedToman(rial: Long): String {
    val toman = abs(tomanOf(rial))
    return ltrFigure((if (rial > 0) "+" else if (rial < 0) "−" else "") + faNumber(toman))
}

// ── Screen ───────────────────────────────────────────────────────────────────────────────────

private val GlyphSearch = listOf("M4 11a7 7 0 1 0 14 0a7 7 0 1 0 -14 0", "M20 20l-3.5-3.5")
private val GlyphFilter = listOf("M4 7h16M7 12h10M10 17h4")
private val GlyphUncategorised = listOf("M12 8v5M12 16v.01M12 4a8 8 0 1 0 0 16a8 8 0 1 0 0-16")
private val GlyphDeposit = listOf("M17 7L7 17M7 9v8h8")

@OptIn(ExperimentalFoundationApi::class, androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
fun LedgerScreen(
    state: UiState,
    vm: AppVm,
    bottomInset: Dp,
    timelineCategoryId: String?,
    timelineSearchQuery: String?,
    onOpenTransaction: (String) -> Unit,
    privacyMode: Boolean,
    onSelectionModeChanged: (Boolean) -> Unit = {},
    modifier: Modifier = Modifier,
) {
    val t = CanvasTheme.tokens
    val activity = LocalActivity.current as? FragmentActivity
    val ledger = state.ledger
    val now = remember(ledger.allEntries, ledger.entries) { System.currentTimeMillis() }
    val today = tehranDay(now)
    val all = ledger.allEntries.ifEmpty { ledger.entries }

    val visible = remember(ledger.entries, ledger.hiddenEntries, state.showHidden) {
        val base = ledger.entries.filterNot { it.duplicate }
        if (state.showHidden) base + ledger.hiddenEntries else base
    }
    var searchQuery by remember(timelineSearchQuery) { mutableStateOf(timelineSearchQuery ?: "") }
    var searchOpen by remember(timelineSearchQuery) { mutableStateOf(!timelineSearchQuery.isNullOrBlank()) }
    var categoryId by remember(timelineCategoryId) { mutableStateOf(timelineCategoryId) }
    var quick by remember { mutableStateOf(LedgerQuickFilter.ALL) }
    var filterTokens by remember { mutableStateOf(listOf<FilterToken>()) }
    var showFilter by remember { mutableStateOf(false) }
    var showAddManual by remember { mutableStateOf(false) }
    var selecting by remember { mutableStateOf(false) }
    var selected by remember { mutableStateOf(setOf<String>()) }
    fun endSelection() { selecting = false; selected = emptySet() }

    val installments = remember(state.obligations, state.obligationPayments) {
        installmentRefs(state.obligations, state.obligationPayments)
    }
    val filtered = remember(visible, searchQuery, categoryId, filterTokens, quick, installments) {
        visible.filter { e ->
            val note = ledger.notes[e.txn.ref].orEmpty()
            matchesTimelineQuery(e, note, searchQuery) &&
                (categoryId == null || e.categoryId == categoryId) &&
                matchesFilter(e, filterTokens) &&
                matchesQuickFilter(e, quick, installments, note)
        }
    }
    val grouped = remember(filtered) { filtered.groupBy { it.txn.day }.toSortedMap(compareByDescending { it }) }
    val uncategorisedCount = remember(visible) { visible.count { !it.hidden && isUncategorised(it) } }
    val week = remember(all, now) { ledgerWeek(all, now) }
    val caption = remember(all, today) { ledgerCaption(all, today) }

    if (selecting) BackHandler { endSelection() }
    LaunchedEffect(selecting) { onSelectionModeChanged(selecting) }
    DisposableEffect(Unit) { onDispose { if (selecting) onSelectionModeChanged(false) } }
    LaunchedEffect(filterTokens) { vm.setFilterWide(filterTokens.isNotEmpty()) }

    if (showAddManual) {
        ManualTxnSheet(
            categories = ledger.categories,
            onDismiss = { showAddManual = false },
            onSave = { amountRial, isExpense, catId, merchant, note, at ->
                vm.addManualTransaction(amountRial, isExpense, catId, merchant, note, at)
                showAddManual = false
            },
        )
    }
    if (showFilter) {
        FilterSheet(
            tokens = filterTokens,
            categories = ledger.categories,
            onApply = { filterTokens = it; endSelection() },
            onDismiss = { showFilter = false },
        )
    }

    Column(modifier.fillMaxSize().background(t.ground)) {
        if (selecting) {
            Row(
                Modifier.fillMaxWidth().statusBarsPadding().padding(horizontal = 12.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconButton(onClick = { endSelection() }) {
                    Icon(Icons.Rounded.Close, contentDescription = "خروج از انتخاب", tint = t.ink)
                }
                Text(
                    if (selected.isEmpty()) "هیچ‌کدام انتخاب نشده" else "${fa(selected.size)} انتخاب شده",
                    fontFamily = Modam, fontSize = 16.sp, fontWeight = FontWeight.Bold, color = t.ink,
                    modifier = Modifier.weight(1f),
                )
                val allRefs = filtered.map { it.txn.ref }.toSet()
                val allChosen = allRefs.isNotEmpty() && selected.size == allRefs.size
                TextButton(onClick = { selected = if (allChosen) emptySet() else allRefs }) {
                    Text(if (allChosen) "برداشتن همه" else "انتخاب همه", fontFamily = Modam, color = t.accentText)
                }
            }
        }

        var pulled by remember { mutableStateOf(false) }
        LaunchedEffect(state.refreshing) { if (!state.refreshing) pulled = false }
        PullToRefreshBox(
            isRefreshing = state.refreshing && pulled,
            onRefresh = { pulled = true; vm.refreshLedger() },
            modifier = Modifier.weight(1f).fillMaxWidth(),
        ) {
            LazyColumn(
                Modifier.fillMaxSize(),
                contentPadding = PaddingValues(bottom = bottomInset + 24.dp),
            ) {
                if (!selecting) {
                    item(key = "header") {
                        CanvasPageHeader(
                            title = Tab.LEDGER.fa,
                            caption = caption,
                        ) {
                            OutlineIconButton("جست‌وجو", onClick = { searchOpen = !searchOpen }) { tint ->
                                CanvasGlyph(GlyphSearch, tint, size = 18.dp)
                            }
                            Box {
                                OutlineIconButton(
                                    if (filterTokens.isEmpty()) "فیلتر" else "فیلتر (${fa(filterTokens.size)})",
                                    onClick = { showFilter = true; endSelection() },
                                ) { tint -> CanvasGlyph(GlyphFilter, tint, size = 18.dp) }
                                if (filterTokens.isNotEmpty()) {
                                    Box(
                                        Modifier.align(Alignment.TopEnd).size(10.dp)
                                            .clip(CircleShape).background(t.accent)
                                    )
                                }
                            }
                        }
                    }
                }
                if (searchOpen) {
                    item(key = "search") {
                        val focus = remember { FocusRequester() }
                        LaunchedEffect(Unit) { if (searchQuery.isBlank()) runCatching { focus.requestFocus() } }
                        OutlinedTextField(
                            value = searchQuery,
                            onValueChange = { searchQuery = it },
                            modifier = Modifier.fillMaxWidth()
                                .padding(start = CanvasTheme.gutter, end = CanvasTheme.gutter, top = 18.dp)
                                .focusRequester(focus),
                            shape = CircleShape,
                            colors = com.doxigo.muchtoman.ui.components.revFieldColors().copy(
                                unfocusedContainerColor = t.card,
                                focusedContainerColor = t.card,
                                unfocusedIndicatorColor = t.cardBorder,
                            ),
                            placeholder = { Text("جستجو در تراکنش‌ها", fontFamily = Modam, color = t.muted) },
                            leadingIcon = { CanvasGlyph(GlyphSearch, t.muted, size = 18.dp) },
                            trailingIcon = {
                                IconButton(onClick = { searchQuery = ""; searchOpen = false }) {
                                    Icon(Icons.Rounded.Clear, contentDescription = "بستن جستجو", tint = t.muted)
                                }
                            },
                            singleLine = true,
                        )
                    }
                }
                item(key = "week") {
                    WeekCard(week, privacyMode, Modifier.padding(start = CanvasTheme.gutter, end = CanvasTheme.gutter, top = 18.dp))
                }
                item(key = "chips") {
                    QuickChips(
                        quick = quick,
                        onQuick = { quick = it; endSelection() },
                        uncategorisedCount = uncategorisedCount,
                        categoryName = categoryId?.let { id -> ledger.categories.firstOrNull { it.id == id }?.nameFa },
                        onClearCategory = { categoryId = null },
                        hiddenCount = ledger.hiddenCount,
                        showHidden = state.showHidden,
                        onToggleHidden = { vm.setShowHidden(!state.showHidden) },
                        onSelect = { selecting = true; selected = emptySet() },
                        onAdd = { showAddManual = true },
                        onExport = { activity?.let { vm.exportCsv(it) } },
                        modifier = Modifier.padding(top = 18.dp),
                    )
                }
                item(key = "watchdog") {
                    val watchdogs = remember(visible) {
                        reconcileFailedWatchdogs(visible.filter { isFailedDebitTxn(it) }, visible, System.currentTimeMillis())
                    }
                    FailedTxnWatchdogBanner(watchdogs = watchdogs, onFilterRef = { searchQuery = it; searchOpen = true })
                }
                val narrowed = searchQuery.isNotBlank() || categoryId != null || filterTokens.isNotEmpty()
                if (narrowed) {
                    item(key = "narrowed") {
                        Row(
                            Modifier.fillMaxWidth().padding(horizontal = CanvasTheme.gutter, vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(
                                "${fa(filtered.size)} تراکنش پیدا شد",
                                fontFamily = Modam, fontSize = 12.sp, color = t.muted, modifier = Modifier.weight(1f),
                            )
                            TextButton(onClick = {
                                searchQuery = ""; searchOpen = false; categoryId = null; filterTokens = emptyList()
                                endSelection()
                            }) { Text("پاک کردن فیلترها", fontFamily = Modam, fontSize = 12.sp, color = t.accentText) }
                        }
                    }
                }
                if (grouped.isEmpty()) {
                    item(key = "empty") {
                        val pure = !narrowed && quick == LedgerQuickFilter.ALL
                        Column(
                            Modifier.fillMaxWidth().padding(horizontal = CanvasTheme.gutter, vertical = 36.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(10.dp),
                        ) {
                            Text(
                                if (pure) "هنوز هیچ تراکنشی ثبت نشده" else "تراکنشی با این فیلتر نیست",
                                fontFamily = Modam, fontSize = 15.sp, fontWeight = FontWeight.Bold, color = t.ink,
                            )
                            if (pure) PillChip("ثبت تراکنش دستی", onClick = { showAddManual = true })
                        }
                    }
                }
                grouped.entries.forEachIndexed { i, (day, rows) ->
                    item(key = "day-$day") {
                        DayHeading(day, today, rows, privacyMode, Modifier.padding(top = if (i == 0) 18.dp else 14.dp))
                    }
                    items(rows, key = { "row-" + it.txn.ref }) { entry ->
                        LedgerRow(
                            entry = entry,
                            state = state,
                            selecting = selecting,
                            selected = entry.txn.ref in selected,
                            privacyMode = privacyMode,
                            onClick = {
                                when {
                                    selecting -> selected = if (entry.txn.ref in selected) selected - entry.txn.ref else selected + entry.txn.ref
                                    entry.hidden -> vm.setTransactionHidden(entry, false)
                                    else -> onOpenTransaction(entry.txn.ref)
                                }
                            },
                            onStartSelection = { selecting = true; selected = setOf(entry.txn.ref) },
                            vm = vm,
                        )
                    }
                }
            }
        }

        if (selecting) {
            val rows = remember(filtered, selected) { filtered.filter { it.txn.ref in selected } }
            BatchActionBar(
                selectedRefs = selected,
                matchCount = filtered.size,
                categories = ledger.categories,
                hiddenCategories = state.hiddenCategories,
                onPickCategory = { id -> if (rows.isNotEmpty()) vm.bulkCategorise(rows, id); endSelection() },
                onHide = { vm.bulkHide(rows, true); endSelection() },
                onFlip = { vm.bulkFlipDirection(rows); endSelection() },
                onDelete = { vm.bulkDelete(rows); endSelection() },
                onNote = { note -> if (rows.isNotEmpty()) vm.bulkSaveNote(rows, note); endSelection() },
                onMerchant = { name -> if (rows.isNotEmpty()) vm.bulkPinMerchant(rows, name); endSelection() },
                onCancel = { endSelection() },
            )
        }
    }
}

/** Past days' bars and future days' stubs, read off the card so every theme gets its own. */
@Composable
private fun barColors(): Pair<Color, Color> {
    val t = CanvasTheme.tokens
    // On the night canvas these land on #3A3A3F and #1A1A1D.
    return lerp(t.card, t.ink, 0.19f) to lerp(t.card, t.ink, 0.05f)
}

@Composable
private fun WeekCard(week: LedgerWeek, privacyMode: Boolean, modifier: Modifier = Modifier) {
    val t = CanvasTheme.tokens
    val (pastBar, futureBar) = barColors()
    CanvasCard(modifier.fillMaxWidth(), contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 18.dp, bottom = 14.dp)) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.Top) {
            Column(Modifier.weight(1f)) {
                Text("خرج این هفته", fontFamily = Modam, fontSize = 12.sp, color = t.muted)
                val parts = faCompact(tomanOf(week.totalRial)).split(' ', limit = 2)
                Text(
                    buildAnnotatedString {
                        if (privacyMode) append("••••••") else {
                            append(parts[0])
                            append(" ")
                            withStyle(SpanStyle(fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = t.muted, fontFamily = Modam)) {
                                append(parts.getOrNull(1) ?: "تومان")
                            }
                        }
                    },
                    style = figureStyle(t.ink, FontWeight.ExtraBold),
                    fontSize = 26.sp,
                    maxLines = 1,
                )
            }
            val change = week.change
            val (text, tone, well) = when {
                change == null -> Triple(
                    if (week.hasBaseline) "هفتهٔ پیش تا این موقع خرجی نبود" else "هنوز هفتهٔ پیشی برای مقایسه نیست",
                    t.muted, t.actionFill,
                )
                abs(change) < 0.005 -> Triple("همان اندازهٔ همین موقع هفتهٔ پیش", t.muted, t.actionFill)
                change < 0 -> Triple("${fa((-change * 100).roundToInt())}٪ کمتر از همین موقع هفتهٔ پیش", t.gain, t.gainWell)
                else -> Triple("${fa((change * 100).roundToInt())}٪ بیشتر از همین موقع هفتهٔ پیش", t.loss, t.loss.copy(alpha = 0.12f))
            }
            Text(
                text,
                modifier = Modifier.padding(top = 4.dp).widthIn(max = 210.dp).clip(CircleShape).background(well)
                    .padding(horizontal = 9.dp, vertical = 3.dp),
                fontFamily = Modam, fontSize = 11.sp, fontWeight = FontWeight.Bold, color = tone,
                maxLines = 1, overflow = TextOverflow.Ellipsis,
            )
        }
        Spacer(Modifier.height(14.dp))
        val max = week.daysRial.max().coerceAtLeast(1L)
        val todayRial = week.daysRial[week.todayIndex]
        Row(
            Modifier.fillMaxWidth().height(104.dp).semantics {
                contentDescription = "خرج روزانهٔ این هفته تا امروز؛ امروز ${faCompact(tomanOf(todayRial))} تومان"
            },
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.Bottom,
        ) {
            week.daysRial.forEachIndexed { i, rial ->
                Column(
                    Modifier.weight(1f).fillMaxHeight(),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(6.dp, Alignment.Bottom),
                ) {
                    if (i == week.todayIndex && rial > 0L) {
                        Text(
                            if (privacyMode) "•••" else faCompact(tomanOf(rial)),
                            fontFamily = ModamFigures, fontSize = 11.sp, fontWeight = FontWeight.SemiBold,
                            color = t.accentText, maxLines = 1, softWrap = false,
                        )
                    }
                    val h = if (i > week.todayIndex) 4.dp else maxOf(6, (rial.toDouble() / max * 72).roundToInt()).dp
                    Box(
                        Modifier.fillMaxWidth().height(h).clip(RoundedCornerShape(10.dp)).background(
                            when {
                                i == week.todayIndex -> t.accent
                                i > week.todayIndex -> futureBar
                                else -> pastBar
                            }
                        )
                    )
                }
            }
        }
        Spacer(Modifier.height(14.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            listOf("ش", "ی", "د", "س", "چ", "پ", "ج").forEachIndexed { i, d ->
                Text(
                    d, Modifier.weight(1f), textAlign = TextAlign.Center, fontFamily = Modam, fontSize = 11.sp,
                    color = if (i == week.todayIndex) t.ink else t.muted,
                )
            }
        }
    }
}

@Composable
private fun QuickChips(
    quick: LedgerQuickFilter,
    onQuick: (LedgerQuickFilter) -> Unit,
    uncategorisedCount: Int,
    categoryName: String?,
    onClearCategory: () -> Unit,
    hiddenCount: Int,
    showHidden: Boolean,
    onToggleHidden: () -> Unit,
    onSelect: () -> Unit,
    onAdd: () -> Unit,
    onExport: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val t = CanvasTheme.tokens
    Row(
        modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = CanvasTheme.gutter),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // A category handed over from the report sits first, selected; tapping it lets go.
        if (categoryName != null) {
            PillChip(categoryName, selected = true, onClick = onClearCategory, leading = { tint ->
                Icon(Icons.Rounded.Close, contentDescription = "برداشتن دسته", tint = tint, modifier = Modifier.size(14.dp))
            })
        }
        PillChip("همه", selected = quick == LedgerQuickFilter.ALL, onClick = { onQuick(LedgerQuickFilter.ALL) })
        // The one warm chip: what still needs her, with how many.
        val unSel = quick == LedgerQuickFilter.UNCATEGORISED
        Row(
            Modifier.height(36.dp).clip(CircleShape)
                .background(if (unSel) t.inverse else t.accentWell)
                .clickable { onQuick(if (unSel) LedgerQuickFilter.ALL else LedgerQuickFilter.UNCATEGORISED) }
                .semantics { this.selected = unSel }
                .padding(start = 12.dp, end = if (uncategorisedCount > 0) 8.dp else 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Text(
                "دسته‌بندی‌نشده", fontFamily = Modam, fontSize = 13.sp,
                fontWeight = if (unSel) FontWeight.ExtraBold else FontWeight.Bold,
                color = if (unSel) t.onInverse else t.accentText, maxLines = 1,
            )
            if (uncategorisedCount > 0) {
                Box(
                    Modifier.height(20.dp).widthIn(min = 20.dp).clip(CircleShape).background(t.accent).padding(horizontal = 5.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(fa(uncategorisedCount), fontFamily = ModamFigures, fontSize = 11.sp, color = Color.Black, maxLines = 1)
                }
            }
        }
        PillChip("واریزها", selected = quick == LedgerQuickFilter.DEPOSITS, onClick = {
            onQuick(if (quick == LedgerQuickFilter.DEPOSITS) LedgerQuickFilter.ALL else LedgerQuickFilter.DEPOSITS)
        })
        PillChip("قسط‌ها", selected = quick == LedgerQuickFilter.INSTALLMENTS, onClick = {
            onQuick(if (quick == LedgerQuickFilter.INSTALLMENTS) LedgerQuickFilter.ALL else LedgerQuickFilter.INSTALLMENTS)
        })
        // The ledger's tools, past the canvas's four: they scroll in from the end.
        Box(Modifier.width(1.dp).height(20.dp).background(t.outlineBorder))
        if (hiddenCount > 0) {
            PillChip(
                if (showHidden) "مخفی‌ها" else "پنهان‌شده (${fa(hiddenCount)})",
                selected = showHidden, compact = true, onClick = onToggleHidden,
            )
        }
        PillChip("انتخاب", compact = true, onClick = onSelect)
        PillChip("تراکنش جدید", compact = true, onClick = onAdd)
        PillChip("خروجی CSV", compact = true, onClick = onExport)
    }
}

@Composable
private fun DayHeading(day: Long, today: Long, rows: List<LedgerEntry>, privacyMode: Boolean, modifier: Modifier = Modifier) {
    val t = CanvasTheme.tokens
    val net = rows.filterNot { it.transfer || it.hidden }.sumOf { it.txn.signedRial ?: 0L }
    Row(
        modifier.fillMaxWidth().padding(start = CanvasTheme.gutter + 2.dp, end = CanvasTheme.gutter + 2.dp, top = 2.dp, bottom = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            faDay(day, today), fontFamily = Modam, fontSize = 12.sp, fontWeight = FontWeight.Bold, color = t.ink,
            modifier = Modifier.weight(1f).semantics { heading() },
        )
        if (net != 0L) {
            Text(if (privacyMode) "••••••" else faSignedToman(net), fontFamily = ModamFigures, fontSize = 12.sp, color = t.muted)
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun LedgerRow(
    entry: LedgerEntry,
    state: UiState,
    selecting: Boolean,
    selected: Boolean,
    privacyMode: Boolean,
    onClick: () -> Unit,
    onStartSelection: () -> Unit,
    vm: AppVm,
) {
    val t = CanvasTheme.tokens
    val txn = entry.txn
    val incoming = txn.direction == "in" && !entry.transfer
    val uncategorised = isUncategorised(entry)
    var menu by remember { mutableStateOf(false) }
    val haptics = com.doxigo.muchtoman.ui.haptics.LocalFintechHaptics.current

    Box {
        Row(
            Modifier
                .fillMaxWidth()
                .background(if (selected) t.accentWell else Color.Transparent)
                .combinedClickable(
                    onClick = onClick,
                    onLongClick = { haptics.click(); if (selecting) onClick() else menu = true },
                )
                .padding(horizontal = CanvasTheme.gutter, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            if (selecting) {
                Box(
                    Modifier.size(22.dp).clip(RoundedCornerShape(6.dp)).then(
                        if (selected) Modifier.background(t.accent)
                        else Modifier.border(1.5.dp, t.outlineBorder, RoundedCornerShape(6.dp))
                    ),
                    contentAlignment = Alignment.Center,
                ) {
                    if (selected) Icon(Icons.Rounded.Check, contentDescription = "انتخاب شده", tint = Color.Black, modifier = Modifier.size(14.dp))
                }
            }
            val (bg, fg) = when {
                incoming -> t.gainWell to t.gain
                uncategorised -> t.actionFill to t.accent
                else -> t.actionFill to t.ink
            }
            Box(Modifier.size(44.dp).clip(CircleShape).background(bg), contentAlignment = Alignment.Center) {
                when {
                    incoming -> CanvasGlyph(GlyphDeposit, fg, size = 20.dp)
                    uncategorised -> CanvasGlyph(GlyphUncategorised, fg, size = 20.dp)
                    else -> CategoryIcon(
                        if (entry.transfer) "انتقال بین حساب‌ها" else entry.categoryFa, fg,
                        size = 20.dp, stroke = 1.5.dp,
                    )
                }
            }
            Column(Modifier.weight(1f)) {
                Text(
                    txn.merchant.ifBlank { if (txn.sourceKind == "manual") "مورد دستی" else bankNameOf(txn.bank) },
                    fontFamily = Modam, fontSize = 15.sp, lineHeight = 21.sp, fontWeight = FontWeight.SemiBold,
                    color = if (entry.hidden) t.muted else t.ink,
                    textDecoration = if (entry.hidden) TextDecoration.LineThrough else null,
                    maxLines = 1, overflow = TextOverflow.Ellipsis,
                )
                val sub = if (uncategorised) "هنوز دسته‌بندی نشده" else listOf(
                    if (entry.transfer) "انتقال بین حساب‌ها" else entry.categoryFa,
                    bankNameOf(txn.bank).removePrefix("بانک ").trim(),
                    faClockOf(txn.at),
                ).filter { it.isNotBlank() }.joinToString(" · ")
                Text(
                    if (entry.ownerName.isNotBlank()) "$sub · از ${entry.ownerName}" else sub,
                    fontFamily = Modam, fontSize = 12.sp, lineHeight = 17.sp,
                    color = if (uncategorised) t.accentText else t.muted,
                    maxLines = 1, overflow = TextOverflow.Ellipsis,
                )
            }
            val rial = txn.signedRial ?: txn.amountRial
            Text(
                when {
                    rial == null -> "مانده"
                    privacyMode -> "••••••"
                    txn.signedRial == null -> ltrFigure(faNumber(abs(tomanOf(rial))))
                    else -> faSignedToman(rial)
                },
                style = figureStyle(
                    when {
                        entry.transfer || rial == null -> t.muted
                        incoming -> t.gain
                        else -> t.ink
                    },
                    FontWeight.Bold,
                ),
                fontSize = 15.sp,
                textDecoration = if (entry.transfer) TextDecoration.LineThrough else null,
                maxLines = 1,
            )
        }
        RowMenu(entry, state, menu, onDismiss = { menu = false }, onStartSelection = onStartSelection, onOpen = onClick, vm = vm)
    }
}

/** The long-press menu: selection, and every per-row action the old row menu had. */
@Composable
private fun RowMenu(
    entry: LedgerEntry,
    state: UiState,
    expanded: Boolean,
    onDismiss: () -> Unit,
    onStartSelection: () -> Unit,
    onOpen: () -> Unit,
    vm: AppVm,
) {
    val clipboard = LocalClipboardManager.current
    val context = LocalContext.current
    val txn = entry.txn
    val rial = txn.signedRial ?: txn.amountRial
    DropdownMenu(expanded = expanded, onDismissRequest = onDismiss) {
        DropdownMenuItem(text = { Text("انتخاب چندتایی") }, onClick = { onDismiss(); onStartSelection() })
        DropdownMenuItem(text = { Text("مشاهده و ویرایش") }, onClick = { onDismiss(); onOpen() })
        DropdownMenuItem(text = { Text("کپی متن رسید") }, onClick = {
            onDismiss()
            clipboard.setText(AnnotatedString(formatReceipt(entry, state.ledger.notes[txn.ref].orEmpty())))
            android.widget.Toast.makeText(context, "رسید تراکنش کپی شد", android.widget.Toast.LENGTH_SHORT).show()
        })
        DropdownMenuItem(
            text = { Text(if (entry.hidden) "نمایش مجدد" else "پنهان‌سازی از گزارش‌ها") },
            onClick = { onDismiss(); vm.setTransactionHidden(entry, !entry.hidden) },
        )
        DropdownMenuItem(
            text = { Text(if (entry.transfer) "لغو انتقال بین حساب‌ها (محاسبه در مخارج)" else "ثبت به عنوان انتقال بین حساب‌ها") },
            onClick = { onDismiss(); vm.toggleTransfer(entry) },
        )
        if (!entry.transfer && (txn.direction == "out" || txn.direction == null) && rial != null && rial < 0) {
            if (state.obligations.none { it.linkedTxnRef == txn.ref }) {
                DropdownMenuItem(text = { Text("ثبت به عنوان خرید اعتباری") }, onClick = { onDismiss(); vm.startMarkCredit(entry) })
            }
            if (state.obligationPayments.values.any { p -> p.any { it.paidDate == null } }) {
                DropdownMenuItem(text = { Text("پیوند به قسط وام") }, onClick = { onDismiss(); vm.startLinkInstallment(entry) })
            }
        }
    }
}
