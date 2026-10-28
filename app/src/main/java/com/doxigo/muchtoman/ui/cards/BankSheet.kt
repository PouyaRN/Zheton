package com.doxigo.muchtoman.ui.cards

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
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsTopHeight
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
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.DateRange
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.Lock
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
import com.doxigo.muchtoman.ui.components.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import com.doxigo.muchtoman.ui.components.OutlinedTextField
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
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
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
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLayoutDirection
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
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.Velocity
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
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
import com.doxigo.muchtoman.Bank
import com.doxigo.muchtoman.BankAccount
import com.doxigo.muchtoman.BankCardDeck
import com.doxigo.muchtoman.BankCardThemes
import com.doxigo.muchtoman.BankLogo
import com.doxigo.muchtoman.GroupedNumber
import com.doxigo.muchtoman.Hero
import com.doxigo.muchtoman.Modam
import com.doxigo.muchtoman.ModamFigures
import com.doxigo.muchtoman.Radius
import com.doxigo.muchtoman.RowAmount
import com.doxigo.muchtoman.RowTitle
import com.doxigo.muchtoman.SheetFlingGuard
import com.doxigo.muchtoman.Space
import com.doxigo.muchtoman.StrangeSender
import com.doxigo.muchtoman.bandShape
import com.doxigo.muchtoman.bidi
import com.doxigo.muchtoman.faAgo
import com.doxigo.muchtoman.faNumber
import com.doxigo.muchtoman.openSmsThread
import com.doxigo.muchtoman.parseAmount
import com.doxigo.muchtoman.trimNumber
import com.doxigo.muchtoman.ui.cards.CardEditorSheet
import com.doxigo.muchtoman.ui.categories.CategoriesScreen
import com.doxigo.muchtoman.ui.commitments.CreditPurchaseForm
import com.doxigo.muchtoman.ui.commitments.DirectObligationSheet
import com.doxigo.muchtoman.ui.commitments.LinkInstallmentPicker
import com.doxigo.muchtoman.ui.commitments.ObligationDetailScreen
import com.doxigo.muchtoman.ui.components.HeroPanel
import kotlin.math.abs
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Every tracked account, one line each, with the raw figure and when it last moved. This is
 * the audit surface: a balance read out of a message is only safe to act on if the message it
 * came from can be found and the number corrected, so nothing here is hidden behind a summary.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun BankSheet(
    accounts: List<BankAccount>,
    disabled: Set<String>,
    strangers: List<StrangeSender>,
    userName: String = "",
    onAnchor: (String, Double) -> Unit,
    onToggle: (String, Boolean) -> Unit,
    onForget: (String) -> Unit,
    onUpdateDetails: (String, String, String, String, String?) -> Unit = { _, _, _, _, _ -> },
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
internal fun BankAccountRow(
    account: BankAccount,
    off: Boolean,
    now: Long,
    fixing: Boolean,
    onFix: () -> Unit,
    onAnchor: (Double) -> Unit,
    onToggle: (Boolean) -> Unit,
    onForget: () -> Unit,
    onUpdateDetails: (String, String, String, String, String?) -> Unit = { _, _, _, _, _ -> },
    onOpenSms: () -> Unit,
) {
    val muted = MaterialTheme.colorScheme.onSurfaceVariant
    var draft by remember(account.key) { mutableStateOf("") }
    var editingDetails by remember(account.key) { mutableStateOf(false) }
    var nicknameDraft by remember(account.key) { mutableStateOf(account.nickname) }
    var panDraft by remember(account.key) { mutableStateOf(account.fullPan) }
    var expiryDraft by remember(account.key) { mutableStateOf(account.expiry) }
    var themeDraft by remember(account.key) { mutableStateOf(account.theme) }

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

                Spacer(Modifier.height(Space.s))
                Text(
                    "تاریخ انقضا (ماه/سال، مثل ۰۷/۰۵)",
                    fontSize = 12.sp,
                    color = muted,
                )
                OutlinedTextField(
                    value = expiryDraft,
                    onValueChange = {
                        // Digits and the one slash, «MM/YY» — anything else never enters.
                        val digits = it.filter { c -> c.isDigit() || c in '۰'..'۹' }.take(4)
                        expiryDraft = if (digits.length >= 3) {
                            digits.substring(0, 2) + "/" + digits.substring(2)
                        } else digits
                    },
                    placeholder = { Text("۰۷/۰۵", fontSize = 12.sp) },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    shape = RoundedCornerShape(Radius.field),
                    modifier = Modifier.fillMaxWidth().padding(top = Space.xs),
                )

                Spacer(Modifier.height(Space.s))
                Text(
                    "رنگ کارت",
                    fontSize = 12.sp,
                    color = muted,
                )
                Spacer(Modifier.height(Space.xs))
                Row(
                    horizontalArrangement = Arrangement.spacedBy(Space.s),
                    modifier = Modifier.horizontalScroll(rememberScrollState()),
                ) {
                    // «رنگ خود بانک» clears the override — the theme the bank taught the app.
                    val bankSelected = themeDraft.isBlank()
                    Box(
                        modifier = Modifier
                            .size(36.dp)
                            .clip(CircleShape)
                            .background(Brush.linearGradient(listOf(Color(0xFF1A1C20), Color(0xFF3A3D44))))
                            .border(
                                if (bankSelected) 3.dp else 1.dp,
                                if (bankSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant,
                                CircleShape,
                            )
                            .clickable { themeDraft = "" },
                        contentAlignment = Alignment.Center,
                    ) {
                        Text("بانک", fontSize = 9.sp, color = Color.White)
                    }
                    BankCardThemes.PRESETS.forEach { (id, preset) ->
                        val selected = themeDraft == id
                        Box(
                            modifier = Modifier
                                .size(36.dp)
                                .clip(CircleShape)
                                .background(Brush.linearGradient(preset.backgroundGradient))
                                .border(
                                    if (selected) 3.dp else 1.dp,
                                    if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant,
                                    CircleShape,
                                )
                                .clickable { themeDraft = id },
                        )
                    }
                }

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
                            onUpdateDetails(account.key, nicknameDraft, panDraft, expiryDraft, themeDraft)
                            editingDetails = false
                        },
                        shape = RoundedCornerShape(Radius.pill),
                    ) {
                        Text("ذخیره مشخصات", fontSize = 13.sp)
                    }
                }
            }

            // Wraps rather than squeezing «حذف حساب» into a column of single letters.
            @OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
            androidx.compose.foundation.layout.FlowRow(Modifier.padding(top = Space.xs)) {
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

