package com.doxigo.muchtoman

import android.app.Application
import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.doxigo.muchtoman.ui.components.CanvasGlyph
import com.doxigo.muchtoman.ui.haptics.LocalFintechHaptics
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/*
 * Quick add: the one way to enter a transaction by hand (QuickAdd.dc.html). Home's «ثبت خرج»
 * round action, the ledger's «تراکنش جدید», the Quick Add widget and the quick-settings tile all
 * open this screen. Sizes are the artboard's CSS pixels taken as dp/sp; colours come from
 * [CanvasTheme.tokens].
 */

/** The account a hand-entered row sits on when she picks «نقد» rather than one of her banks. */
const val MANUAL_ACCOUNT_ID = "MANUAL"

/** Longest amount the keypad takes, in Toman digits: just under a thousand billion. */
private const val MAX_DIGITS = 12

enum class QuickAddKind(val tab: String, val action: String) {
    EXPENSE("خرج", "ثبت خرج"),
    INCOME("درآمد", "ثبت درآمد"),
    /** Between two of her own accounts: counted in neither spending nor income. */
    TRANSFER("انتقال", "ثبت انتقال"),
}

/** One account she can put a row on: a bank she has, or cash. */
data class QuickAddAccount(
    val id: String,
    /** «ملت ۶۴۶۰», her nickname, or «نقد». */
    val label: String,
    /** The letter on the round mark. */
    val initial: String,
    val tint: Color,
)

/** What the screen hands back when she presses the primary button. */
data class QuickAddDraft(
    val kind: QuickAddKind,
    val amountRial: Long,
    val accountId: String,
    /** The receiving account; only for [QuickAddKind.TRANSFER]. */
    val toAccountId: String? = null,
    val categoryId: String? = null,
    val at: Long,
)

/** One row as [recordManualTransaction] writes it. A transfer is two of them. */
data class ManualLeg(
    val amountRial: Long,
    val isExpense: Boolean,
    val categoryId: String?,
    val merchant: String,
    val at: Long,
    val accountId: String,
)

// ── Pure pieces ───────────────────────────────────────────────────────────────────────────────

/** Persian digits, kept as written (leading zeros included): "0602" → "۰۶۰۲". */
internal fun faDigits(s: String): String = buildString(s.length) {
    for (c in s) append(if (c in '0'..'9') '۰' + (c - '0') else c)
}

/** The keypad: a digit appended. A leading zero means nothing, and the length is capped. */
fun quickAddDigit(digits: String, d: Int): String = when {
    d !in 0..9 -> digits
    digits.isEmpty() && d == 0 -> digits
    digits.length >= MAX_DIGITS -> digits
    else -> digits + d
}

/** The keypad's ۰۰۰: three zeros, only after something, and never past the cap. */
fun quickAddThousand(digits: String): String =
    if (digits.isEmpty() || digits.length + 3 > MAX_DIGITS) digits else digits + "000"

fun quickAddBackspace(digits: String): String = digits.dropLast(1)

/**
 * Her accounts for the account chip: every bank she has not switched off, then cash.
 * The label is her nickname when she gave one, otherwise the bank's short name and the last
 * four digits of its card or account.
 */
fun quickAddAccounts(bankAccounts: List<BankAccount>, disabledBanks: Set<String> = emptySet()): List<QuickAddAccount> {
    val banks = bankAccounts.filter { it.bank !in disabledBanks }.distinctBy { it.key }.map { acct ->
        val short = acct.bankFa.removePrefix("بانک").trim().ifBlank { acct.bankFa }
        val digits = toAsciiDigits(acct.mask.ifBlank { acct.fullPan }).filter { it.isDigit() }
        val tail = if (digits.length >= 4) " " + faDigits(digits.takeLast(4)) else ""
        val palette = BankCardThemes.resolve(acct.bank, acct.fullPan, acct.theme)
        QuickAddAccount(
            id = acct.key,
            label = acct.nickname.trim().ifBlank { short + tail },
            initial = short.take(1),
            tint = palette.backgroundGradient.getOrElse(palette.backgroundGradient.size / 2) { Color(0xFF48484C) },
        )
    }
    return banks + QuickAddAccount(MANUAL_ACCOUNT_ID, "نقد", "ن", Color(0xFF3A3A3E))
}

/** The account she used last, so the chip starts where she usually is; cash when there is none. */
fun quickAddDefaultAccount(accounts: List<QuickAddAccount>, entries: List<LedgerEntry>): String {
    val ids = accounts.map { it.id }.toSet()
    return entries.filter { it.txn.accountId in ids }.maxByOrNull { it.txn.at }?.txn?.accountId
        ?: accounts.firstOrNull()?.id
        ?: MANUAL_ACCOUNT_ID
}

/** «امروز · ۱۰:۴۲», «دیروز · ۲۲:۰۵», or «۳ آبان · ۰۹:۱۵» on the Tehran clock. */
fun quickAddWhen(at: Long, now: Long): String {
    val day = tehranDay(at)
    val dayPart = when (tehranDay(now) - day) {
        0L -> "امروز"
        1L -> "دیروز"
        else -> jalaliOf(day).let { "${faNumber(it.day)} ${MONTHS[it.month - 1]}" }
    }
    val minutes = ((at + TEHRAN_OFFSET_MS) - tehranDay(at) * DAY_MS) / 60_000L
    val clock = faDigits("%02d:%02d".format(minutes / 60, minutes % 60))
    return "$dayPart · $clock"
}

/** The rows a draft becomes. A transfer is one leg out of the source and one into the target. */
fun quickAddLegs(draft: QuickAddDraft, accounts: List<QuickAddAccount>): List<ManualLeg> {
    val labels = accounts.associate { it.id to it.label }
    val cash = draft.accountId == MANUAL_ACCOUNT_ID
    return when (draft.kind) {
        QuickAddKind.EXPENSE -> listOf(
            ManualLeg(draft.amountRial, true, draft.categoryId, if (cash) "خرید نقدی" else "پرداخت", draft.at, draft.accountId),
        )
        QuickAddKind.INCOME -> listOf(
            ManualLeg(draft.amountRial, false, draft.categoryId, if (cash) "واریز نقدی" else "واریز", draft.at, draft.accountId),
        )
        QuickAddKind.TRANSFER -> {
            val to = draft.toAccountId ?: return emptyList()
            if (to == draft.accountId) return emptyList()
            listOf(
                ManualLeg(draft.amountRial, true, CAT_TRANSFER, "انتقال به ${labels[to] ?: to}", draft.at, draft.accountId),
                ManualLeg(draft.amountRial, false, CAT_TRANSFER, "انتقال از ${labels[draft.accountId] ?: draft.accountId}", draft.at, to),
            )
        }
    }
}

/** Writes every leg straight to the store; for hosts without the app's view model. */
suspend fun recordQuickAdd(context: android.content.Context, legs: List<ManualLeg>) {
    for (leg in legs) {
        recordManualTransaction(
            context = context,
            amountRial = leg.amountRial,
            isExpense = leg.isExpense,
            categoryId = leg.categoryId,
            merchant = leg.merchant,
            note = "",
            epochMillis = leg.at,
            accountId = leg.accountId,
        )
    }
}

/**
 * Saves through [AppVm.addManualTransaction]. A transfer's first leg is written directly so the
 * second leg's derive sees both and the ledger refreshes once with the pair in it.
 */
fun AppVm.saveQuickAdd(legs: List<ManualLeg>) {
    if (legs.isEmpty()) return
    val last = legs.last()
    val before = legs.dropLast(1)
    viewModelScope.launch(Dispatchers.Default) {
        runCatching { recordQuickAdd(getApplication<Application>(), before) }
            .onFailure { android.util.Log.w("muchtoman", "quick add first leg failed: $it") }
        addManualTransaction(last.amountRial, last.isExpense, last.categoryId, last.merchant, "", last.at, last.accountId)
    }
}

// ── Hosts ─────────────────────────────────────────────────────────────────────────────────────

/**
 * Quick add over the running app, as a full-screen window. [vm] defaults to the activity's
 * [AppVm], the same instance MainActivity holds.
 */
@Composable
fun QuickAddOverlay(
    onDismiss: () -> Unit,
    vm: AppVm = androidx.lifecycle.viewmodel.compose.viewModel(),
) {
    val state by vm.state.collectAsStateWithLifecycle()
    val accounts = remember(state.bankAccounts, state.disabledBanks) {
        quickAddAccounts(state.bankAccounts, state.disabledBanks)
    }
    val defaultAccount = remember(accounts) { quickAddDefaultAccount(accounts, state.ledger.allEntries) }
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(
            usePlatformDefaultWidth = false,
            decorFitsSystemWindows = false,
            dismissOnClickOutside = false,
        ),
    ) {
        QuickAddScreen(
            accounts = accounts,
            defaultAccountId = defaultAccount,
            categories = state.ledger.categories,
            hiddenCategories = state.hiddenCategories,
            suggest = { amountRial, accountId, at, direction ->
                vm.suggestCategory(amountRial, accountId, at, direction = direction)
            },
            onClose = onDismiss,
            onSave = { draft ->
                vm.saveQuickAdd(quickAddLegs(draft, accounts))
                onDismiss()
            },
        )
    }
}

/**
 * Quick add without the app running (QuickAddActivity: the tile and the widget). Reads the
 * ledger once for the category suggestion and writes through [recordManualTransaction].
 */
@Composable
fun QuickAddStandalone(
    presetCategoryId: String?,
    onClose: () -> Unit,
    onSaved: (amountToman: Long) -> Unit,
) {
    val context = LocalContext.current
    val app = context.applicationContext
    val store = remember { Store(app) }
    var view by remember { mutableStateOf<LedgerView?>(null) }
    var saving by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    LaunchedEffect(Unit) {
        view = withContext(Dispatchers.IO) {
            runCatching { ledgerView(DerivedDb.get(app), DurableDb.get(app), limit = 1) }.getOrElse { LedgerView() }
        }
    }
    val v = view
    if (v == null) {
        Box(Modifier.fillMaxSize().background(CanvasTheme.tokens.ground))
        return
    }
    val accounts = remember { quickAddAccounts(store.bankAccounts, store.disabledBanks) }
    val hidden = remember { store.hiddenCategories }
    QuickAddScreen(
        accounts = accounts,
        defaultAccountId = remember(v) { quickAddDefaultAccount(accounts, v.allEntries) },
        categories = v.categories,
        hiddenCategories = hidden,
        presetCategoryId = presetCategoryId,
        saving = saving,
        suggest = { amountRial, accountId, at, direction ->
            suggestCategory(
                entries = v.allEntries,
                categories = v.categories,
                amountRial = amountRial,
                accountId = accountId,
                at = at,
                notes = v.notes,
                direction = direction,
                hidden = hidden,
            )
        },
        onClose = onClose,
        onSave = { draft ->
            if (!saving) {
                saving = true
                scope.launch {
                    runCatching { recordQuickAdd(app, quickAddLegs(draft, accounts)) }
                        .onSuccess { onSaved(draft.amountRial / 10L) }
                        .onFailure { saving = false }
                }
            }
        },
    )
}

// ── The screen ────────────────────────────────────────────────────────────────────────────────

@Composable
fun QuickAddScreen(
    accounts: List<QuickAddAccount>,
    defaultAccountId: String,
    categories: List<Category>,
    hiddenCategories: Set<String>,
    suggest: (amountRial: Long?, accountId: String, at: Long, direction: String) -> CategorySuggestion,
    onClose: () -> Unit,
    onSave: (QuickAddDraft) -> Unit,
    presetCategoryId: String? = null,
    saving: Boolean = false,
) {
    val t = CanvasTheme.tokens
    val haptics = LocalFintechHaptics.current

    var kind by rememberSaveable { mutableStateOf(QuickAddKind.EXPENSE) }
    var digits by rememberSaveable { mutableStateOf("") }
    var accountId by rememberSaveable { mutableStateOf(defaultAccountId) }
    var toAccountId by rememberSaveable { mutableStateOf<String?>(null) }
    var pickedAt by rememberSaveable { mutableStateOf<Long?>(null) }
    var pickedCategory by rememberSaveable { mutableStateOf(presetCategoryId) }
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    var choosingAccount by remember { mutableStateOf(false) }
    var choosingTarget by remember { mutableStateOf(false) }
    var choosingCategory by remember { mutableStateOf(false) }
    var choosingTime by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        while (true) {
            delay(15_000)
            now = System.currentTimeMillis()
        }
    }
    BackHandler(onBack = onClose)

    val at = pickedAt ?: now
    val toman = digits.toLongOrNull() ?: 0L
    val amountRial = toman * 10L
    val direction = if (kind == QuickAddKind.INCOME) "in" else "out"
    val account = accounts.firstOrNull { it.id == accountId } ?: accounts.firstOrNull()
    val targets = accounts.filter { it.id != account?.id }
    val target = targets.firstOrNull { it.id == toAccountId }

    val suggestion = remember(kind, amountRial, accountId, at / 60_000L, categories) {
        if (kind == QuickAddKind.TRANSFER) CategorySuggestion.NONE
        else suggest(amountRial.takeIf { it > 0 }, accountId, at, direction)
    }
    val choices = remember(categories, hiddenCategories, direction) {
        categoryChoices(categories, direction, hiddenCategories)
    }
    val byId = remember(categories) { categories.associateBy { it.id } }
    val selectedCategory = pickedCategory ?: suggestion.top?.id
    val row: List<Category> = run {
        val base = (listOfNotNull(suggestion.top) + suggestion.next + choices).distinctBy { it.id }.take(4)
        val picked = pickedCategory?.let(byId::get)
        if (picked != null && base.none { it.id == picked.id }) listOf(picked) + base.take(3) else base
    }

    val reason = when (kind) {
        QuickAddKind.TRANSFER -> when {
            account == null || targets.isEmpty() -> "برای انتقال دست‌کم دو حساب لازمه"
            target == null -> "از ${account.label} به کدوم حسابت؟"
            else -> "از ${account.label} به ${target.label}؛ تو خرج و درآمد حساب نمی‌شه"
        }
        else -> when {
            selectedCategory != null && selectedCategory == suggestion.top?.id && suggestion.reason != null -> suggestion.reason
            selectedCategory != null -> "دسته: ${byId[selectedCategory]?.nameFa.orEmpty()}"
            else -> "دسته رو انتخاب کن"
        }
    }

    val canSave = amountRial > 0 && account != null && !saving &&
        (kind != QuickAddKind.TRANSFER || target != null)

    Box(Modifier.fillMaxSize().background(t.ground)) {
        QuickAddGlow()
        Column(
            Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding()
                .padding(start = 20.dp, end = 20.dp, top = 10.dp, bottom = 16.dp),
        ) {
            // Close and the type tabs.
            Row(
                Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Box(
                    Modifier
                        .size(44.dp)
                        .clip(CircleShape)
                        .border(1.dp, t.outlineBorder, CircleShape)
                        .clickable(role = Role.Button, onClick = onClose)
                        .semantics { contentDescription = "بستن" },
                    contentAlignment = Alignment.Center,
                ) {
                    CanvasGlyph(listOf("M6 6l12 12M18 6L6 18"), t.ink, size = 18.dp, stroke = 2f)
                }
                Row(
                    Modifier
                        .clip(CircleShape)
                        .background(t.card)
                        .border(1.dp, t.cardBorder, CircleShape)
                        .padding(3.dp),
                ) {
                    QuickAddKind.entries.forEach { k ->
                        val on = k == kind
                        Box(
                            Modifier
                                .height(36.dp)
                                .clip(CircleShape)
                                .then(if (on) Modifier.background(t.inverse) else Modifier)
                                .clickable(role = Role.Tab) {
                                    if (k != kind) {
                                        haptics.click()
                                        kind = k
                                        pickedCategory = null
                                    }
                                }
                                .semantics { selected = on }
                                .padding(horizontal = 16.dp),
                            contentAlignment = Alignment.Center,
                        ) {
                            Text(
                                k.tab,
                                fontFamily = Modam,
                                fontSize = 13.sp,
                                fontWeight = if (on) FontWeight.ExtraBold else FontWeight.SemiBold,
                                color = if (on) t.onInverse else t.muted,
                            )
                        }
                    }
                }
            }

            // The amount: the whole stage.
            Column(
                Modifier.weight(1f).fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(10.dp, Alignment.CenterVertically),
            ) {
                Text("مبلغ", fontFamily = Modam, fontSize = 12.sp, color = t.muted)
                val shown = if (toman > 0) faNumber(toman) else "۰"
                val size = when {
                    shown.length <= 7 -> 80
                    shown.length <= 9 -> 66
                    shown.length <= 11 -> 54
                    else -> 44
                }
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(
                        shown,
                        fontFamily = ModamFigures,
                        fontSize = size.sp,
                        lineHeight = size.sp,
                        fontWeight = FontWeight.ExtraBold,
                        letterSpacing = (-2).sp,
                        color = if (toman > 0) t.ink else t.muted,
                        maxLines = 1,
                    )
                    QuickAddCaret(height = (size * 0.8f).dp, color = t.accent)
                }
                Text(
                    faWordsToman(toman.toDouble()) ?: " ",
                    fontFamily = Modam,
                    fontSize = 14.sp,
                    color = t.inkSecondary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    textAlign = TextAlign.Center,
                )
                Row(Modifier.padding(top = 14.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (account != null) {
                        QuickAddChip(onClick = { choosingAccount = true }, leadingAvatar = account) { Text(account.label, style = chipStyle(t.ink)) }
                    }
                    QuickAddChip(onClick = { choosingTime = true }) { Text(quickAddWhen(at, now), style = chipStyle(t.ink)) }
                }
            }

            // Category, guessed first; for a transfer, the receiving account.
            Column(Modifier.padding(bottom = 14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    reason,
                    fontFamily = Modam,
                    fontSize = 12.sp,
                    color = t.muted,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    if (kind == QuickAddKind.TRANSFER) {
                        val shownTargets = targets.take(4).let { list ->
                            if (target != null && list.none { it.id == target.id }) listOf(target) + list.take(3) else list
                        }
                        shownTargets.forEach { acct ->
                            RoundChoice(
                                label = acct.label,
                                selected = acct.id == toAccountId,
                                onClick = { haptics.tick(); toAccountId = acct.id },
                            ) { tint ->
                                Text(acct.initial, fontFamily = Modam, fontSize = 18.sp, fontWeight = FontWeight.ExtraBold, color = tint)
                            }
                        }
                        repeat(4 - shownTargets.size) { Spacer(Modifier.weight(1f)) }
                        if (targets.size > 4) MoreChoice { choosingTarget = true } else Spacer(Modifier.weight(1f))
                    } else {
                        row.forEach { cat ->
                            RoundChoice(
                                label = cat.nameFa,
                                selected = cat.id == selectedCategory,
                                onClick = {
                                    haptics.tick()
                                    pickedCategory = if (cat.id == selectedCategory && pickedCategory != null) null else cat.id
                                },
                            ) { tint ->
                                CategoryIcon(cat.nameFa, tint, size = 22.dp, stroke = if (cat.id == selectedCategory) 1.9.dp else 1.7.dp)
                            }
                        }
                        repeat(4 - row.size) { Spacer(Modifier.weight(1f)) }
                        MoreChoice { choosingCategory = true }
                    }
                }
            }

            // Keypad.
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                listOf(listOf(1, 2, 3), listOf(4, 5, 6), listOf(7, 8, 9)).forEach { keys ->
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(0.dp)) {
                        keys.forEach { d ->
                            KeypadKey(onClick = { haptics.tick(); digits = quickAddDigit(digits, d) }) {
                                Text(faDigits(d.toString()), style = keypadStyle(t.ink))
                            }
                        }
                    }
                }
                Row(Modifier.fillMaxWidth()) {
                    KeypadKey(onClick = { haptics.tick(); digits = quickAddThousand(digits) }) {
                        Text("۰۰۰", fontFamily = ModamFigures, fontSize = 18.sp, fontWeight = FontWeight.Bold, color = t.inkSecondary)
                    }
                    KeypadKey(onClick = { haptics.tick(); digits = quickAddDigit(digits, 0) }) {
                        Text("۰", style = keypadStyle(t.ink))
                    }
                    KeypadKey(label = "پاک کردن آخرین رقم", onClick = { haptics.tick(); digits = quickAddBackspace(digits) }) {
                        CanvasGlyph(listOf("M20 6H9l-6 6 6 6h11z", "M16 10l-4 4M12 10l4 4"), t.ink, size = 26.dp, stroke = 1.7f)
                    }
                }
            }

            // Primary.
            Row(
                Modifier
                    .padding(top = 10.dp)
                    .fillMaxWidth()
                    .height(60.dp)
                    .alpha(if (canSave) 1f else 0.45f)
                    .clip(CircleShape)
                    .background(t.inverse)
                    .clickable(enabled = canSave, role = Role.Button) {
                        haptics.heavyClick()
                        onSave(
                            QuickAddDraft(
                                kind = kind,
                                amountRial = amountRial,
                                accountId = account!!.id,
                                toAccountId = if (kind == QuickAddKind.TRANSFER) target?.id else null,
                                categoryId = if (kind == QuickAddKind.TRANSFER) CAT_TRANSFER else selectedCategory,
                                at = pickedAt ?: System.currentTimeMillis(),
                            ),
                        )
                    },
                horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(kind.action, fontFamily = Modam, fontSize = 16.sp, fontWeight = FontWeight.ExtraBold, color = t.onInverse)
                CanvasGlyph(listOf("M5 12l5 5L20 7"), t.onInverse, size = 18.dp, stroke = 2.2f)
            }
        }
    }

    if (choosingAccount) {
        AccountSheet(
            title = if (kind == QuickAddKind.TRANSFER) "از کدوم حساب؟" else "کدوم حساب؟",
            accounts = accounts,
            selectedId = account?.id,
            onPick = {
                accountId = it
                if (toAccountId == it) toAccountId = null
                choosingAccount = false
            },
            onDismiss = { choosingAccount = false },
        )
    }
    if (choosingTarget) {
        AccountSheet(
            title = "به کدوم حساب؟",
            accounts = targets,
            selectedId = toAccountId,
            onPick = { toAccountId = it; choosingTarget = false },
            onDismiss = { choosingTarget = false },
        )
    }
    if (choosingCategory) {
        CategorySheet(
            categories = choices,
            selectedId = selectedCategory,
            onPick = { pickedCategory = it; choosingCategory = false },
            onDismiss = { choosingCategory = false },
        )
    }
    if (choosingTime) {
        WhenDialog(
            initial = at,
            now = System.currentTimeMillis(),
            onPick = { picked -> pickedAt = picked; choosingTime = false },
            onDismiss = { choosingTime = false },
        )
    }
}

// ── Pieces ────────────────────────────────────────────────────────────────────────────────────

private fun chipStyle(color: Color) = androidx.compose.ui.text.TextStyle(
    fontFamily = Modam,
    fontSize = 13.sp,
    fontWeight = FontWeight.SemiBold,
    color = color,
)

private fun keypadStyle(color: Color) = androidx.compose.ui.text.TextStyle(
    fontFamily = ModamFigures,
    fontSize = 26.sp,
    fontWeight = FontWeight.Medium,
    color = color,
)

/** The artboard's light: a 560px saffron radial whose centre sits 120px from the left, 400px down. */
@Composable
private fun QuickAddGlow() {
    val t = CanvasTheme.tokens
    Canvas(Modifier.fillMaxSize()) {
        val radius = 280.dp.toPx()
        val centre = Offset(120.dp.toPx(), 400.dp.toPx())
        drawCircle(
            brush = Brush.radialGradient(
                0f to t.glow.copy(alpha = if (t.isDark) 0.16f else 0.2f),
                1f to t.glow.copy(alpha = 0f),
                center = centre,
                radius = radius,
            ),
            radius = radius,
            center = centre,
        )
    }
}

@Composable
private fun QuickAddCaret(height: Dp, color: Color) {
    val blink by rememberInfiniteTransition(label = "caret").animateFloat(
        initialValue = 1f,
        targetValue = 0.15f,
        animationSpec = infiniteRepeatable(tween(530), RepeatMode.Reverse),
        label = "caret-alpha",
    )
    Box(
        Modifier
            .width(3.dp)
            .height(height)
            .alpha(blink)
            .clip(RoundedCornerShape(2.dp))
            .background(color),
    )
}

@Composable
private fun AccountAvatar(account: QuickAddAccount, size: Dp = 28.dp) {
    Box(
        Modifier.size(size).clip(CircleShape).background(account.tint),
        contentAlignment = Alignment.Center,
    ) {
        Text(account.initial, fontFamily = Modam, fontSize = 11.sp, fontWeight = FontWeight.ExtraBold, color = Color.White)
    }
}

/** 40dp outlined chip under the amount; the account chip carries the bank's 28dp mark. */
@Composable
private fun QuickAddChip(
    onClick: () -> Unit,
    leadingAvatar: QuickAddAccount? = null,
    content: @Composable () -> Unit,
) {
    val t = CanvasTheme.tokens
    Row(
        Modifier
            .height(40.dp)
            .clip(CircleShape)
            .background(t.outlineFill)
            .border(1.dp, t.outlineBorder, CircleShape)
            .clickable(role = Role.Button, onClick = onClick)
            .padding(start = if (leadingAvatar != null) 6.dp else 14.dp, end = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        leadingAvatar?.let { AccountAvatar(it) }
        content()
    }
}

/** One of the row of five: a 54dp disc over an 11sp label. Saffron when chosen. */
@Composable
private fun RowScope.RoundChoice(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    icon: @Composable (tint: Color) -> Unit,
) {
    val t = CanvasTheme.tokens
    Column(
        Modifier
            .weight(1f)
            .clip(RoundedCornerShape(14.dp))
            .clickable(role = Role.Button, onClick = onClick)
            .semantics { this.selected = selected },
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Box(
            Modifier
                .size(54.dp)
                .clip(CircleShape)
                .background(if (selected) t.accent else t.actionFill)
                .then(if (!selected && !t.isDark) Modifier.border(1.dp, t.actionBorder, CircleShape) else Modifier),
            contentAlignment = Alignment.Center,
        ) { icon(if (selected) Color.Black else t.ink) }
        Text(
            label,
            fontFamily = Modam,
            fontSize = 11.sp,
            fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
            color = if (selected) t.ink else t.inkSecondary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

/** «بیشتر»: the dashed disc that opens the full list. */
@Composable
private fun RowScope.MoreChoice(onClick: () -> Unit) {
    val t = CanvasTheme.tokens
    val dash = if (t.isDark) Color.White.copy(alpha = 0.22f) else Color.Black.copy(alpha = 0.22f)
    Column(
        Modifier
            .weight(1f)
            .clip(RoundedCornerShape(14.dp))
            .clickable(role = Role.Button, onClick = onClick)
            .semantics { contentDescription = "دسته‌های بیشتر" },
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Box(
            Modifier
                .size(54.dp)
                .drawBehind {
                    val w = 1.dp.toPx()
                    drawCircle(
                        color = dash,
                        radius = size.minDimension / 2 - w / 2,
                        style = Stroke(width = w, pathEffect = PathEffect.dashPathEffect(floatArrayOf(4.dp.toPx(), 3.dp.toPx()))),
                    )
                },
            contentAlignment = Alignment.Center,
        ) {
            Canvas(Modifier.size(20.dp)) {
                val k = size.width / 24f
                listOf(5f, 12f, 19f).forEach { x -> drawCircle(t.ink, radius = 1.6f * k, center = Offset(x * k, 12f * k)) }
            }
        }
        Text("بیشتر", fontFamily = Modam, fontSize = 11.sp, color = t.inkSecondary, maxLines = 1)
    }
}

@Composable
private fun RowScope.KeypadKey(
    onClick: () -> Unit,
    label: String? = null,
    content: @Composable () -> Unit,
) {
    Box(
        Modifier
            .weight(1f)
            .height(56.dp)
            .clip(RoundedCornerShape(16.dp))
            .clickable(role = Role.Button, onClick = onClick)
            .then(if (label != null) Modifier.semantics { contentDescription = label } else Modifier),
        contentAlignment = Alignment.Center,
    ) { content() }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AccountSheet(
    title: String,
    accounts: List<QuickAddAccount>,
    selectedId: String?,
    onPick: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    val t = CanvasTheme.tokens
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = t.card,
    ) {
        Column(
            Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(horizontal = 20.dp).padding(bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Text(title, fontFamily = Modam, fontSize = 18.sp, fontWeight = FontWeight.ExtraBold, color = t.ink, modifier = Modifier.padding(bottom = 8.dp))
            accounts.forEach { acct ->
                val on = acct.id == selectedId
                Row(
                    Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(18.dp))
                        .background(if (on) t.accentWell else Color.Transparent)
                        .clickable(role = Role.Button) { onPick(acct.id) }
                        .padding(horizontal = 10.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    AccountAvatar(acct, size = 36.dp)
                    Text(acct.label, style = chipStyle(t.ink).copy(fontSize = 15.sp), modifier = Modifier.weight(1f))
                    if (on) CanvasGlyph(listOf("M5 12l5 5L20 7"), t.accentText, size = 18.dp, stroke = 2.2f)
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CategorySheet(
    categories: List<Category>,
    selectedId: String?,
    onPick: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    val t = CanvasTheme.tokens
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = t.card,
    ) {
        Column(Modifier.fillMaxWidth().padding(horizontal = 20.dp)) {
            Text("همهٔ دسته‌ها", fontFamily = Modam, fontSize = 18.sp, fontWeight = FontWeight.ExtraBold, color = t.ink, modifier = Modifier.padding(bottom = 14.dp))
            LazyVerticalGrid(
                columns = GridCells.Fixed(4),
                verticalArrangement = Arrangement.spacedBy(14.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                modifier = Modifier.padding(bottom = 24.dp),
            ) {
                items(categories, key = { it.id }) { cat ->
                    val on = cat.id == selectedId
                    Column(
                        Modifier.clip(RoundedCornerShape(14.dp)).clickable(role = Role.Button) { onPick(cat.id) },
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        Box(
                            Modifier.size(54.dp).clip(CircleShape).background(if (on) t.accent else t.actionFill),
                            contentAlignment = Alignment.Center,
                        ) { CategoryIcon(cat.nameFa, if (on) Color.Black else t.ink, size = 22.dp, stroke = 1.7.dp) }
                        Text(
                            cat.nameFa,
                            fontFamily = Modam,
                            fontSize = 11.sp,
                            fontWeight = if (on) FontWeight.Bold else FontWeight.Normal,
                            color = if (on) t.ink else t.inkSecondary,
                            maxLines = 2,
                            textAlign = TextAlign.Center,
                        )
                    }
                }
            }
        }
    }
}

/** When it happened: one of the last sixty days, then the hour and minute on the Tehran clock. */
@Composable
private fun WhenDialog(
    initial: Long,
    now: Long,
    onPick: (Long?) -> Unit,
    onDismiss: () -> Unit,
) {
    val t = CanvasTheme.tokens
    val today = tehranDay(now)
    var day by remember { mutableLongStateOf(tehranDay(initial)) }
    val startMinutes = ((initial + TEHRAN_OFFSET_MS) - tehranDay(initial) * DAY_MS) / 60_000L
    var hour by remember { mutableStateOf((startMinutes / 60).toInt()) }
    var minute by remember { mutableStateOf((startMinutes % 60).toInt()) }
    val picked = tehranDayStart(day) + (hour * 60L + minute) * 60_000L
    val future = picked > now

    Dialog(onDismissRequest = onDismiss) {
        Column(
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(CanvasTheme.cardRadius))
                .background(t.card)
                .border(1.dp, t.cardBorder, RoundedCornerShape(CanvasTheme.cardRadius))
                .padding(vertical = 20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Text("کِی بود؟", fontFamily = Modam, fontSize = 18.sp, fontWeight = FontWeight.ExtraBold, color = t.ink, modifier = Modifier.padding(horizontal = 20.dp))
            LazyRow(
                contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 20.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                items((0L until 60L).map { today - it }) { d ->
                    val on = d == day
                    val label = when (today - d) {
                        0L -> "امروز"
                        1L -> "دیروز"
                        else -> jalaliOf(d).let { "${faNumber(it.day)} ${MONTHS[it.month - 1]}" }
                    }
                    Box(
                        Modifier
                            .height(36.dp)
                            .clip(CircleShape)
                            .then(if (on) Modifier.background(t.inverse) else Modifier.border(1.dp, t.outlineBorder, CircleShape))
                            .clickable(role = Role.Button) { day = d }
                            .padding(horizontal = 14.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(label, fontFamily = Modam, fontSize = 13.sp, fontWeight = if (on) FontWeight.ExtraBold else FontWeight.Normal, color = if (on) t.onInverse else t.chipText)
                    }
                }
            }
            // Clock: minute on the left, hour on the right, as an RTL reader writes ۱۰:۴۲.
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 20.dp),
                horizontalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterHorizontally),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Stepper("ساعت", hour, onChange = { hour = (it + 24) % 24 })
                Text(":", fontFamily = ModamFigures, fontSize = 32.sp, fontWeight = FontWeight.ExtraBold, color = t.muted)
                Stepper("دقیقه", minute, step = 5, onChange = { minute = (it + 60) % 60 })
            }
            if (future) {
                Text("این ساعت هنوز نرسیده", fontFamily = Modam, fontSize = 12.sp, color = t.loss, modifier = Modifier.padding(horizontal = 20.dp))
            }
            Row(Modifier.fillMaxWidth().padding(horizontal = 20.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Box(
                    Modifier.weight(1f).height(48.dp).clip(CircleShape).border(1.dp, t.outlineBorder, CircleShape)
                        .clickable(role = Role.Button) { onPick(null) },
                    contentAlignment = Alignment.Center,
                ) { Text("همین الان", style = chipStyle(t.ink)) }
                Box(
                    Modifier.weight(1f).height(48.dp).clip(CircleShape).background(t.inverse).alpha(if (future) 0.45f else 1f)
                        .clickable(enabled = !future, role = Role.Button) { onPick(picked) },
                    contentAlignment = Alignment.Center,
                ) { Text("تأیید", style = chipStyle(t.onInverse).copy(fontWeight = FontWeight.ExtraBold)) }
            }
        }
    }
}

@Composable
private fun Stepper(label: String, value: Int, step: Int = 1, onChange: (Int) -> Unit) {
    val t = CanvasTheme.tokens
    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(label, fontFamily = Modam, fontSize = 11.sp, color = t.muted)
        StepButton("M6 15l6-6 6 6", "$label بیشتر") { onChange(value + step) }
        Text(faDigits("%02d".format(value)), fontFamily = ModamFigures, fontSize = 32.sp, fontWeight = FontWeight.ExtraBold, color = t.ink)
        StepButton("M6 9l6 6 6-6", "$label کمتر") { onChange(value - step) }
    }
}

@Composable
private fun StepButton(path: String, label: String, onClick: () -> Unit) {
    val t = CanvasTheme.tokens
    Box(
        Modifier.size(40.dp).clip(CircleShape).background(t.actionFill).clickable(role = Role.Button, onClick = onClick)
            .semantics { contentDescription = label },
        contentAlignment = Alignment.Center,
    ) { CanvasGlyph(listOf(path), t.ink, size = 18.dp, stroke = 2f) }
}
