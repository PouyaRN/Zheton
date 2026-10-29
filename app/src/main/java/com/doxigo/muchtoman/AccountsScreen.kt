package com.doxigo.muchtoman

import android.widget.Toast
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.doxigo.muchtoman.ui.components.CanvasCard
import com.doxigo.muchtoman.ui.components.CanvasGlyph
import com.doxigo.muchtoman.ui.components.CanvasPageHeader
import com.doxigo.muchtoman.ui.components.OutlineIconButton
import com.doxigo.muchtoman.ui.haptics.LocalFintechHaptics

/*
 * The Accounts tab (حساب‌ها), laid out as docs/design/canvas/project/Accounts.dc.html.
 *
 * The one exception to the artboard is the card itself: the owner's rule is that the bank card
 * keeps its own look (BankCard3D, as AppleWalletAccordionDeck shows it). So where the canvas
 * draws its girih card stack, this screen puts the existing deck — the bank switcher chips over
 * BankCard3D, unchanged — and only everything around it follows the canvas.
 */

/**
 * @param onManageAccounts the existing account management sheet (round add button, card tap).
 * @param onEditCard the existing card editor (long press on the card).
 * @param onOpenLedger opens the ledger filtered to this account (گردش حساب).
 * @param commitments the existing commitments summary, drawn under the list when it has content.
 */
@Composable
fun AccountsScreen(
    state: UiState,
    privacyMode: Boolean,
    bottomInset: Dp,
    onManageAccounts: () -> Unit,
    onEditCard: (BankAccount) -> Unit,
    onOpenLedger: (BankAccount) -> Unit,
    modifier: Modifier = Modifier,
    commitments: (@Composable () -> Unit)? = null,
) {
    val t = CanvasTheme.tokens
    val accounts = state.bankAccounts
    var selectedKey by rememberSaveable { mutableStateOf<String?>(null) }
    val selected = accounts.find { it.key == selectedKey } ?: accounts.firstOrNull()
    val palette = selected?.let {
        remember(it.bankFa, it.mask, it.theme) { BankCardThemes.resolve(it.bankFa, it.mask, it.theme) }
    }

    Box(modifier.fillMaxSize().background(t.ground)) {
        // The card lights the room in its own bank colour.
        palette?.let { BankGlow(it.accentColor) }

        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(bottom = bottomInset + 24.dp),
            verticalArrangement = Arrangement.spacedBy(18.dp),
        ) {
            item(key = "header") {
                CanvasPageHeader(
                    title = Tab.CARDS.fa,
                    caption = "${fa(accounts.size)} حساب · به‌روز از پیامک‌های بانک",
                ) {
                    OutlineIconButton("افزودن حساب", onManageAccounts) { tint ->
                        CanvasGlyph(listOf("M12 5v14M5 12h14"), tint, size = 20.dp, stroke = 2f)
                    }
                }
            }

            if (selected == null) {
                item(key = "empty") { EmptyAccounts(onManageAccounts) }
            } else {
                item(key = "deck") {
                    Box(Modifier.padding(horizontal = CanvasTheme.gutter)) {
                        AccountDeck(
                            accounts = accounts,
                            active = selected,
                            cardHolderName = state.name,
                            privacyMode = privacyMode,
                            onSelect = { selectedKey = it.key },
                            onCardClick = { onManageAccounts() },
                            onCardLongPress = onEditCard,
                        )
                    }
                }
                item(key = "actions") {
                    AccountActions(
                        account = selected,
                        onOpenLedger = { onOpenLedger(selected) },
                        modifier = Modifier.padding(horizontal = CanvasTheme.gutter),
                    )
                }
                item(key = "flow") {
                    val flow = remember(state.ledger, selected.key) { state.accountFlow(selected.key) }
                    FlowCard(flow, privacyMode, Modifier.padding(horizontal = CanvasTheme.gutter))
                }
                val freshness = state.smsFreshness().associateBy { it.accountKey }
                item(key = "freshness") {
                    val ago = freshness[selected.key]
                    FreshnessLine(
                        selected,
                        ago,
                        Modifier.padding(horizontal = CanvasTheme.gutter),
                    )
                }
                val others = accounts.filter { it.key != selected.key }
                if (others.isNotEmpty()) {
                    item(key = "others") {
                        Column(Modifier.padding(horizontal = CanvasTheme.gutter)) {
                            others.forEach { a ->
                                OtherAccountRow(
                                    account = a,
                                    lastSmsAt = freshness[a.key]?.lastSmsAt ?: a.updatedAt,
                                    agoFa = freshness[a.key]?.agoFa ?: faAgo(a.updatedAt, System.currentTimeMillis()),
                                    privacyMode = privacyMode,
                                    onClick = { selectedKey = a.key },
                                )
                            }
                        }
                    }
                }
            }

            if (commitments != null) {
                item(key = "commitments") {
                    Box(Modifier.padding(horizontal = CanvasTheme.gutter)) { commitments() }
                }
            }
        }
    }
}

/**
 * The canvas's 640×520 radial behind the stack (top 40, centred), in the selected bank's
 * colour: .30 at the core, .08 at 55%, nothing at its edge. A little softer by day.
 */
@Composable
private fun BankGlow(color: Color) {
    val t = CanvasTheme.tokens
    val core = if (t.isDark) 0.30f else 0.16f
    val mid = if (t.isDark) 0.08f else 0.05f
    Canvas(Modifier.fillMaxSize()) {
        val ry = 260.dp.toPx()
        val centre = Offset(size.width / 2f, 300.dp.toPx())
        scale(scaleX = 640f / 520f, scaleY = 1f, pivot = centre) {
            drawCircle(
                brush = Brush.radialGradient(
                    0f to color.copy(alpha = core),
                    0.55f to color.copy(alpha = mid),
                    1f to color.copy(alpha = 0f),
                    center = centre,
                    radius = ry,
                ),
                radius = ry,
                center = centre,
            )
        }
    }
}

/**
 * The existing card deck: the bank switcher chips over [BankCard3D], exactly as
 * AppleWalletAccordionDeck draws them (same chips, same card, same cross-fade), with the
 * selection lifted out so it drives the rest of the screen. The deck's old action dock and
 * "other cards" list are replaced by the canvas's pills and list below it.
 */
@Composable
private fun AccountDeck(
    accounts: List<BankAccount>,
    active: BankAccount,
    cardHolderName: String,
    privacyMode: Boolean,
    onSelect: (BankAccount) -> Unit,
    onCardClick: (BankAccount) -> Unit,
    onCardLongPress: (BankAccount) -> Unit,
) {
    val fintechHaptics = LocalFintechHaptics.current
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        if (accounts.size > 1) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState())
                    .padding(horizontal = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                accounts.forEach { account ->
                    val isSelected = account.key == active.key
                    val palette = remember(account.bankFa, account.mask, account.theme) {
                        BankCardThemes.resolve(account.bankFa, account.mask, account.theme)
                    }
                    Surface(
                        modifier = Modifier
                            .clip(RoundedCornerShape(Radius.pill))
                            .clickable {
                                fintechHaptics.click()
                                onSelect(account)
                            },
                        shape = RoundedCornerShape(Radius.pill),
                        color = if (isSelected) {
                            palette.accentColor.copy(alpha = 0.18f)
                        } else {
                            MaterialTheme.colorScheme.surfaceContainerHighest.copy(alpha = 0.45f)
                        },
                        border = BorderStroke(
                            1.dp,
                            if (isSelected) palette.accentColor else MaterialTheme.colorScheme.outline.copy(alpha = 0.2f),
                        ),
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(8.dp)
                                    .clip(CircleShape)
                                    .background(palette.accentColor),
                            )
                            Text(
                                text = account.bankFa,
                                fontSize = 12.sp,
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                color = if (isSelected) palette.accentColor else MaterialTheme.colorScheme.onSurface,
                            )
                            val lastFour = if (account.mask.length >= 4) account.mask.takeLast(4) else ""
                            if (lastFour.isNotBlank()) {
                                Text(
                                    text = "••$lastFour",
                                    fontSize = 10.sp,
                                    fontFamily = ModamFigures,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                                )
                            }
                        }
                    }
                }
            }
        }

        AnimatedContent(
            targetState = active,
            transitionSpec = {
                fadeIn(spring(stiffness = Spring.StiffnessMediumLow)) togetherWith
                    fadeOut(spring(stiffness = Spring.StiffnessMediumLow))
            },
            label = "cardStageTransition",
        ) { targetAccount ->
            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                BankCard3D(
                    bankAccount = targetAccount,
                    cardHolderName = cardHolderName,
                    modifier = Modifier.fillMaxWidth(),
                    onClick = {
                        fintechHaptics.click()
                        onCardClick(targetAccount)
                    },
                    onLongClick = {
                        fintechHaptics.heavyClick()
                        onCardLongPress(targetAccount)
                    },
                    enable3DTiltDrag = true,
                    privacyMode = privacyMode,
                )
            }
        }
    }
}

/** The 16 digits to copy, from the full PAN she entered or a mask that happens to be whole. */
internal fun copyableCardNumber(account: BankAccount): String? =
    listOf(account.fullPan, account.mask)
        .map { toAsciiDigits(it).filter(Char::isDigit) }
        .firstOrNull { it.length == 16 }

/** The SHEBA to copy, canonical (IR + 24 digits, no spaces), or null when she has not entered one. */
internal fun copyableSheba(account: BankAccount): String? =
    account.sheba.takeIf { it.isNotBlank() }?.let { cleanSheba(it) }?.takeIf { it.isNotBlank() }

/** کپی کارت · کپی شبا · گردش حساب: three 48dp pills in one row, the last one inverted. */
@Composable
private fun AccountActions(account: BankAccount, onOpenLedger: () -> Unit, modifier: Modifier = Modifier) {
    val t = CanvasTheme.tokens
    val context = LocalContext.current
    val clipboard = LocalClipboardManager.current
    val haptics = LocalFintechHaptics.current
    val pan = copyableCardNumber(account)
    val sheba = copyableSheba(account)

    fun copy(value: String?, done: String, missing: String) {
        if (value == null) {
            Toast.makeText(context, missing, Toast.LENGTH_SHORT).show()
            return
        }
        haptics.doubleTap()
        clipboard.setText(AnnotatedString(value))
        Toast.makeText(context, done, Toast.LENGTH_SHORT).show()
    }

    Row(modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        ActionPill(
            "کپی کارت",
            enabled = pan != null,
            reason = "شمارهٔ کامل کارت ثبت نشده",
            modifier = Modifier.weight(1f),
        ) { copy(pan, "شماره کارت کپی شد", "شمارهٔ کامل کارت ثبت نشده؛ از ویرایش کارت واردش کن") }
        ActionPill(
            "کپی شبا",
            enabled = sheba != null,
            reason = "شبا ثبت نشده",
            modifier = Modifier.weight(1f),
        ) { copy(sheba, "شماره شبا کپی شد", "شبا ثبت نشده؛ از ویرایش کارت واردش کن") }
        Box(
            Modifier
                .weight(1f)
                .height(48.dp)
                .clip(CircleShape)
                .background(t.inverse)
                .clickable(role = Role.Button) { haptics.click(); onOpenLedger() },
            contentAlignment = Alignment.Center,
        ) {
            Text("گردش حساب", fontFamily = Modam, fontSize = 13.sp, fontWeight = FontWeight.ExtraBold, color = t.onInverse, maxLines = 1)
        }
    }
}

/**
 * An outlined 48dp pill. Disabled it fades, and a tap says why instead of doing nothing, so the
 * reason is never hidden.
 */
@Composable
private fun ActionPill(
    label: String,
    enabled: Boolean,
    reason: String,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    val t = CanvasTheme.tokens
    Box(
        modifier
            .height(48.dp)
            .clip(CircleShape)
            .background(t.outlineFill)
            .border(1.dp, t.outlineBorder, CircleShape)
            .clickable(role = Role.Button, onClick = onClick)
            .semantics { contentDescription = if (enabled) label else "$label، غیرفعال: $reason" }
            .alpha(if (enabled) 1f else 0.4f),
        contentAlignment = Alignment.Center,
    ) {
        Text(label, fontFamily = Modam, fontSize = 13.sp, fontWeight = FontWeight.Bold, color = t.ink, maxLines = 1)
    }
}

/** "۱۸٫۲ میلیون" → ("۱۸٫۲", "میلیون"); a figure under a thousand has no unit. */
private fun compactParts(toman: Long): Pair<String, String?> {
    val s = faCompact(toman)
    val i = s.lastIndexOf(' ')
    return if (i < 0) s to null else s.substring(0, i) to s.substring(i + 1)
}

/** «۳۰ روز اخیر»: the totals, then the mirrored bars — deposits up, withdrawals down. */
@Composable
private fun FlowCard(flow: AccountFlow, privacyMode: Boolean, modifier: Modifier = Modifier) {
    val t = CanvasTheme.tokens
    val gain = t.gain
    val down = if (t.isDark) Color(0xFF6B6B72) else t.muted.copy(alpha = 0.55f)
    val axis = t.ink.copy(alpha = 0.14f)
    CanvasCard(modifier.fillMaxWidth()) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text("۳۰ روز اخیر", fontFamily = Modam, fontSize = 14.sp, fontWeight = FontWeight.Bold, color = t.ink)
            Spacer(Modifier.weight(1f))
            val (inFig, inUnit) = compactParts(flow.totalInToman)
            val (outFig, outUnit) = compactParts(flow.totalOutToman)
            val summary = buildAnnotatedString {
                append("واریز ")
                withStyle(SpanStyle(color = gain, fontWeight = FontWeight.Bold)) {
                    append(if (privacyMode) "••••" else inFig)
                }
                // One unit at the end when both sides share it, as the canvas writes it.
                if (inUnit != null && inUnit != outUnit && !privacyMode) append(" $inUnit")
                append(" · برداشت ")
                append(if (privacyMode) "••••" else outFig)
                if (outUnit != null && !privacyMode) append(" $outUnit")
            }
            Text(summary, fontFamily = Modam, fontSize = 12.sp, color = t.muted, maxLines = 1)
        }
        Spacer(Modifier.height(10.dp))
        val peak = flow.days.maxOfOrNull { maxOf(it.inToman, it.outToman) }?.takeIf { it > 0 } ?: 1L
        val total = flow.totalInToman + flow.totalOutToman
        Canvas(
            Modifier
                .fillMaxWidth()
                .height(92.dp)
                .semantics {
                    contentDescription = "واریز و برداشت روزانه در ۳۰ روز اخیر" +
                        if (total == 0L) "؛ هنوز تراکنشی نیست" else ""
                },
        ) {
            val n = flow.days.size.coerceAtLeast(1)
            val gap = 3.dp.toPx()
            val barW = (size.width - gap * (n - 1)) / n
            val upH = 46.dp.toPx()
            val lineY = upH
            val downTop = upH + 1.dp.toPx()
            val downH = 44.dp.toPx()
            val r = CornerRadius(3.dp.toPx())
            val minH = 2.dp.toPx()
            drawRect(axis, Offset(0f, lineY), Size(size.width, 1.dp.toPx()))
            flow.days.forEachIndexed { i, d ->
                // Oldest first, drawn from the right edge as Persian reads, so today is the leftmost bar.
                val x = size.width - (i + 1) * barW - i * gap
                if (d.inToman > 0) {
                    val h = (d.inToman.toFloat() / peak * upH).coerceAtLeast(minH)
                    drawRoundRect(gain, Offset(x, lineY - h), Size(barW, h), r)
                }
                if (d.outToman > 0) {
                    val h = (d.outToman.toFloat() / peak * downH).coerceAtLeast(minH)
                    drawRoundRect(down, Offset(x, downTop), Size(barW, h), r)
                }
            }
        }
    }
}

/** The selected account's freshness: when its bank last texted. */
@Composable
private fun FreshnessLine(account: BankAccount, freshness: SmsFreshness?, modifier: Modifier = Modifier) {
    val t = CanvasTheme.tokens
    val at = freshness?.lastSmsAt ?: account.updatedAt
    val ago = freshness?.agoFa ?: faAgo(at, System.currentTimeMillis())
    Row(modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Box(
            Modifier
                .size(6.dp)
                .clip(CircleShape)
                .background(if (at > 0) t.gain else t.muted),
        )
        Text(
            if (at > 0) "${account.bankFa} · آخرین پیامک $ago" else "${account.bankFa} · $ago",
            fontFamily = Modam,
            fontSize = 12.sp,
            color = t.muted,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

/** The bank's short name for the list: «بانک سامان» → «سامان». */
internal fun shortBankName(account: BankAccount): String =
    account.bankFa.removePrefix("بانک ").removeSuffix(" ایران").trim()

/** One other account: bank mark, name with the last four digits, «آخرین پیامک …», balance. */
@Composable
private fun OtherAccountRow(account: BankAccount, lastSmsAt: Long, agoFa: String, privacyMode: Boolean, onClick: () -> Unit) {
    val t = CanvasTheme.tokens
    val palette = remember(account.bankFa, account.mask, account.theme) {
        BankCardThemes.resolve(account.bankFa, account.mask, account.theme)
    }
    val name = shortBankName(account)
    val digits = toAsciiDigits(account.mask).filter(Char::isDigit)
    val last4 = if (digits.length >= 4) digits.takeLast(4) else ""
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .clickable(role = Role.Button, onClick = onClick)
            .padding(vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        // The mark wears the card's own colours, so it reads as the same card in miniature.
        Box(
            Modifier
                .size(44.dp)
                .clip(RoundedCornerShape(14.dp))
                .background(Brush.linearGradient(palette.backgroundGradient.ifEmpty { listOf(palette.accentColor, palette.accentColor) })),
            contentAlignment = Alignment.Center,
        ) {
            Text(name.take(1), fontFamily = Modam, fontSize = 15.sp, fontWeight = FontWeight.ExtraBold, color = palette.textColor)
        }
        Column(Modifier.weight(1f)) {
            Text(
                if (last4.isEmpty()) name else "$name ${if (privacyMode) "••••" else faDigitsOnly(last4)}",
                fontFamily = Modam,
                fontSize = 15.sp,
                fontWeight = FontWeight.SemiBold,
                color = t.ink,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                if (lastSmsAt > 0) "آخرین پیامک $agoFa" else agoFa,
                fontFamily = Modam,
                fontSize = 12.sp,
                color = t.muted,
                maxLines = 1,
            )
        }
        Text(
            if (privacyMode) "••••••" else fa(account.balance),
            fontFamily = Modam,
            fontSize = 15.sp,
            fontWeight = FontWeight.Bold,
            color = t.ink,
            maxLines = 1,
        )
    }
}

/** Persian digits for a run of ASCII digits, leading zeros kept (a card's last four can be «۰۴۲۱»). */
private fun faDigitsOnly(s: String): String =
    s.map { c -> if (c in '0'..'9') '۰' + (c - '0') else c }.joinToString("")

/** No card yet: what turns them on, and the way to the account management. */
@Composable
private fun EmptyAccounts(onManageAccounts: () -> Unit) {
    val t = CanvasTheme.tokens
    CanvasCard(Modifier.fillMaxWidth().padding(horizontal = CanvasTheme.gutter)) {
        Column(
            Modifier.fillMaxWidth().padding(8.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text("هنوز کارت بانکی اضافه نشده", fontFamily = Modam, fontSize = 16.sp, fontWeight = FontWeight.Bold, color = t.ink)
            Text(
                "با فعال‌کردن خواندن پیامک‌ها، کارت‌ها و موجودی شما به صورت خودکار اضافه می‌شوند. همچنین می‌توانید حساب‌ها را به صورت دستی مدیریت کنید.",
                fontFamily = Modam,
                fontSize = 13.sp,
                lineHeight = 20.sp,
                color = t.muted,
                textAlign = TextAlign.Center,
            )
            Box(
                Modifier
                    .height(48.dp)
                    .clip(CircleShape)
                    .background(t.inverse)
                    .clickable(role = Role.Button, onClick = onManageAccounts)
                    .padding(horizontal = 22.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text("مدیریت و فعال‌سازی حساب‌ها", fontFamily = Modam, fontSize = 13.sp, fontWeight = FontWeight.ExtraBold, color = t.onInverse)
            }
        }
    }
}
