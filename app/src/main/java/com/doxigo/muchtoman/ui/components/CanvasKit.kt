package com.doxigo.muchtoman.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandHorizontally
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkHorizontally
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.vector.PathParser
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.doxigo.muchtoman.CanvasTheme
import com.doxigo.muchtoman.Modam
import com.doxigo.muchtoman.Motion
import com.doxigo.muchtoman.Tab
import com.doxigo.muchtoman.ui.haptics.LocalFintechHaptics

/*
 * The redesign canvas's shared pieces (docs/design/canvas/project). Every size here is the
 * artboard's CSS pixel taken as a dp; every colour comes from [CanvasTheme.tokens], so the same
 * piece is exact under the canvas themes and derived under the other sixteen.
 */

// ── Backdrop ──────────────────────────────────────────────────────────────────────────────────

/**
 * One light, from the top start corner: Main.dc.html's 520px saffron radial, centred 120px in
 * from the right edge and 80px down, fading to .07 at 55% and to nothing at its edge.
 *
 * Fill the page with it behind the content; it draws nothing else.
 */
@Composable
fun SaffronGlow(modifier: Modifier = Modifier) {
    val t = CanvasTheme.tokens
    Canvas(modifier.fillMaxSize()) {
        val radius = 260.dp.toPx()
        val centre = Offset(size.width - 120.dp.toPx(), 80.dp.toPx())
        drawCircle(
            brush = Brush.radialGradient(
                0f to t.glow.copy(alpha = t.glowCore),
                0.55f to t.glow.copy(alpha = t.glowMid),
                1f to t.glow.copy(alpha = 0f),
                center = centre,
                radius = radius,
            ),
            radius = radius,
            center = centre,
        )
    }
}

// ── Floating pill nav ─────────────────────────────────────────────────────────────────────────

/** Height of the pill itself. */
val CanvasNavHeight = 66.dp

/** Gap between the pill and the bottom of the window, above the system navigation inset. */
private val CanvasNavLift = 10.dp

/** How far the scrim reaches above the pill (150px scrim, pill top at 94px). */
private val CanvasScrimReach = 56.dp

/**
 * The five places, as a floating pill: 66dp tall, inset 20dp from both edges, translucent
 * glass with a 1px edge. The tab she is on is an inverted pill with its icon and name; the
 * others are 52dp icon-only targets.
 *
 * It also draws the ground-coloured scrim that lets the page fade under it instead of colliding
 * with it, so put it in the Scaffold's bottomBar slot and let content run underneath (use the
 * Scaffold's bottom padding as the list's end content padding).
 */
@Composable
fun CanvasNavBar(
    tabs: List<Tab>,
    selected: Tab,
    onSelect: (Tab) -> Unit,
    modifier: Modifier = Modifier,
) {
    val t = CanvasTheme.tokens
    val haptics = LocalFintechHaptics.current
    Box(
        modifier
            .fillMaxWidth()
            .drawBehind {
                // Ground at 45% from the bottom up to transparent, reaching above our bounds.
                val reach = CanvasScrimReach.toPx()
                val h = size.height + reach
                drawRect(
                    brush = Brush.verticalGradient(
                        0f to t.ground.copy(alpha = 0f),
                        0.55f to t.ground,
                        1f to t.ground,
                        startY = -reach,
                        endY = size.height,
                    ),
                    topLeft = Offset(0f, -reach),
                    size = Size(size.width, h),
                )
            }
            .navigationBarsPadding()
            .padding(start = CanvasTheme.gutter, end = CanvasTheme.gutter, bottom = CanvasNavLift),
    ) {
        val pill = CircleShape
        Row(
            Modifier
                .fillMaxWidth()
                .height(CanvasNavHeight)
                .then(if (t.navShadow) Modifier.shadow(24.dp, pill, ambientColor = Color.Black.copy(alpha = 0.08f), spotColor = Color.Black.copy(alpha = 0.08f)) else Modifier)
                .clip(pill)
                .background(t.navFill)
                .border(1.dp, t.navBorder, pill)
                .padding(horizontal = 7.dp)
                .semantics { contentDescription = "ناوبری اصلی" },
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            tabs.forEach { tab ->
                val active = tab == selected
                val fill by animateColorAsState(
                    if (active) t.inverse else t.inverse.copy(alpha = 0f),
                    tween(Motion.fast), label = "nav_fill",
                )
                val tint by animateColorAsState(
                    if (active) t.onInverse else t.muted,
                    tween(Motion.fast), label = "nav_tint",
                )
                Row(
                    Modifier
                        .height(52.dp)
                        .clip(pill)
                        .background(fill)
                        .clickable(role = Role.Tab) {
                            if (!active) {
                                haptics.click()
                                onSelect(tab)
                            }
                        }
                        .semantics {
                            contentDescription = tab.fa
                            this.selected = active
                        }
                        .padding(horizontal = if (active) 18.dp else 15.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    CanvasGlyph(
                        paths = tab.glyph,
                        tint = tint,
                        size = if (active) 20.dp else 22.dp,
                        stroke = if (active) 2f else tab.idleStroke,
                    )
                    AnimatedVisibility(
                        visible = active,
                        enter = fadeIn(tween(Motion.fast)) + expandHorizontally(tween(Motion.medium, easing = Motion.enter)),
                        exit = fadeOut(tween(Motion.fast)) + shrinkHorizontally(tween(Motion.fast, easing = Motion.exit)),
                    ) {
                        Row {
                            Spacer(Modifier.width(7.dp))
                            Text(
                                tab.fa,
                                fontFamily = Modam,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.ExtraBold,
                                color = tint,
                                maxLines = 1,
                            )
                        }
                    }
                }
            }
        }
    }
}

/** The canvas's nav icons, as their SVG path data on a 24-unit box. */
private val Tab.glyph: List<String>
    get() = when (this) {
        Tab.HOME -> listOf("M4 10.5L12 4l8 6.5V20H4z")
        Tab.LEDGER -> listOf("M6 3h12v18l-3-2-3 2-3-2-3 2z", "M9 8h6M9 12h4")
        Tab.ASSETS -> listOf("M4 18l5-6 4 3 7-8")
        Tab.CARDS -> listOf("M6 6h12a3 3 0 0 1 3 3v7a3 3 0 0 1-3 3H6a3 3 0 0 1-3-3V9a3 3 0 0 1 3-3z", "M3 10h18")
        Tab.REPORTS -> listOf("M6 19V11M12 19V5M18 19v-6")
    }

private val Tab.idleStroke: Float get() = if (this == Tab.REPORTS) 1.9f else 1.8f

/**
 * A stroked icon drawn from SVG path data on a 24-unit box, round caps and joins — the way every
 * glyph on the canvas is drawn. [stroke] is in those 24 units, as in the SVG.
 */
@Composable
fun CanvasGlyph(
    paths: List<String>,
    tint: Color,
    modifier: Modifier = Modifier,
    size: Dp = 22.dp,
    stroke: Float = 1.8f,
) {
    val parsed = remember(paths) { paths.map { PathParser().parsePathString(it).toPath() } }
    Canvas(modifier.size(size)) {
        val k = this.size.width / 24f
        scale(k, pivot = Offset.Zero) {
            val pen = Stroke(width = stroke, cap = StrokeCap.Round, join = StrokeJoin.Round)
            parsed.forEach { p: Path -> drawPath(p, tint, style = pen) }
        }
    }
}

// ── Round actions and buttons ─────────────────────────────────────────────────────────────────

/**
 * A 56dp round action with its label under it (Home's row of four). [primary] is the one filled
 * action: saffron at night, ink by day. The rest are quiet discs with a 1px edge.
 */
@Composable
fun RoundAction(
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    primary: Boolean = false,
    icon: @Composable (tint: Color) -> Unit,
) {
    val t = CanvasTheme.tokens
    val haptics = LocalFintechHaptics.current
    Column(
        modifier
            .clip(RoundedCornerShape(16.dp))
            .clickable(role = Role.Button) { haptics.click(); onClick() }
            .padding(vertical = 4.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Box(
            Modifier
                .size(56.dp)
                .clip(CircleShape)
                .background(if (primary) t.primaryAction else t.actionFill)
                .then(if (primary) Modifier else Modifier.border(1.dp, t.actionBorder, CircleShape)),
            contentAlignment = Alignment.Center,
        ) { icon(if (primary) t.onPrimaryAction else t.ink) }
        Text(
            label,
            fontFamily = Modam,
            fontSize = 12.sp,
            fontWeight = FontWeight.SemiBold,
            color = t.ink,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

/**
 * The round outline control at the end of a page header: 44dp by default (Ledger, Accounts),
 * 40dp on Home's bar. A faint fill and a 1px edge at night, a white disc by day.
 */
@Composable
fun OutlineIconButton(
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    size: Dp = 44.dp,
    icon: @Composable (tint: Color) -> Unit,
) {
    val t = CanvasTheme.tokens
    Box(
        modifier
            .size(size)
            .clip(CircleShape)
            .background(t.outlineFill)
            .border(1.dp, t.outlineBorder, CircleShape)
            .clickable(role = Role.Button, onClick = onClick)
            .semantics { contentDescription = label },
        contentAlignment = Alignment.Center,
    ) { icon(t.ink) }
}

/**
 * A pill chip: 36dp (or 34dp when [compact]), outlined when idle, inverted ink when [selected].
 * An optional [leading] slot holds a small glyph or swatch.
 */
@Composable
fun PillChip(
    text: String,
    modifier: Modifier = Modifier,
    selected: Boolean = false,
    compact: Boolean = false,
    onClick: (() -> Unit)? = null,
    leading: (@Composable (tint: Color) -> Unit)? = null,
) {
    val t = CanvasTheme.tokens
    val tint = if (selected) t.onInverse else if (compact) t.muted else t.chipText
    Row(
        modifier
            .height(if (compact) 34.dp else 36.dp)
            .clip(CircleShape)
            .then(
                if (selected) Modifier.background(t.inverse)
                else Modifier.border(1.dp, t.outlineBorder, CircleShape)
            )
            .then(if (onClick != null) Modifier.clickable(role = Role.Button, onClick = onClick) else Modifier)
            .semantics { this.selected = selected }
            .padding(start = if (leading != null) 8.dp else if (compact) 12.dp else 14.dp, end = if (compact) 12.dp else 14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        leading?.invoke(tint)
        Text(
            text,
            fontFamily = Modam,
            fontSize = if (compact) 12.sp else 13.sp,
            fontWeight = if (selected) FontWeight.ExtraBold else FontWeight.Normal,
            color = tint,
            maxLines = 1,
        )
    }
}

/** A small tinted badge, e.g. «+۲٫۱٪ از اول ماه» on the net-worth card. */
@Composable
fun TonePill(text: String, tone: Color, well: Color, modifier: Modifier = Modifier) {
    Text(
        text,
        modifier = modifier
            .clip(CircleShape)
            .background(well)
            .padding(horizontal = 9.dp, vertical = 3.dp),
        fontFamily = Modam,
        fontSize = 11.sp,
        fontWeight = FontWeight.Bold,
        color = tone,
        maxLines = 1,
    )
}

// ── Page structure ────────────────────────────────────────────────────────────────────────────

/**
 * The top of a canvas page (Ledger, Accounts, Report): an 11sp caption over a 40sp ExtraBold
 * title on the start edge, round controls on the end edge, bottoms aligned.
 */
@Composable
fun CanvasPageHeader(
    title: String,
    modifier: Modifier = Modifier,
    caption: String? = null,
    actions: @Composable RowScope.() -> Unit = {},
) {
    val t = CanvasTheme.tokens
    Row(
        modifier
            .fillMaxWidth()
            .statusBarsPadding()
            .padding(start = CanvasTheme.gutter, end = CanvasTheme.gutter, top = 14.dp),
        verticalAlignment = Alignment.Bottom,
    ) {
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            caption?.let {
                Text(
                    it,
                    fontFamily = Modam,
                    fontSize = 11.sp,
                    color = t.muted,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Text(
                title,
                fontFamily = Modam,
                fontSize = 40.sp,
                lineHeight = 44.sp,
                fontWeight = FontWeight.ExtraBold,
                color = t.ink,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), content = actions)
    }
}

/**
 * A canvas card: card fill, 1px edge, 26dp corners, 16dp padding. Clickable when [onClick] is
 * given. For the small 18–22dp cards pass [radius].
 */
@Composable
fun CanvasCard(
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    radius: Dp = CanvasTheme.cardRadius,
    contentPadding: PaddingValues = PaddingValues(16.dp),
    content: @Composable ColumnScope.() -> Unit,
) {
    val t = CanvasTheme.tokens
    val shape = RoundedCornerShape(radius)
    Column(
        modifier
            .clip(shape)
            .background(t.card)
            .border(BorderStroke(1.dp, t.cardBorder), shape)
            .then(if (onClick != null) Modifier.clickable(role = Role.Button, onClick = onClick) else Modifier)
            .padding(contentPadding),
        content = content,
    )
}

/**
 * A page on the canvas: the ground, the saffron light behind its top, and the content over both.
 */
@Composable
fun CanvasPage(modifier: Modifier = Modifier, glow: Boolean = true, content: @Composable BoxScope.() -> Unit) {
    Box(modifier.fillMaxSize().background(CanvasTheme.tokens.ground)) {
        if (glow) SaffronGlow()
        content()
    }
}
