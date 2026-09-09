package com.doxigo.muchtoman

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.inset
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInParent
import androidx.compose.ui.platform.LocalDensity
import com.doxigo.muchtoman.ui.haptics.LocalFintechHaptics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * The five places the app goes, and the way back from any of them.
 *
 * Before this there was no way home except the system back button: every screen was an overlay
 * with a «بستن» in the corner, and overlays over overlays meant she could get somewhere she
 * could not get out of. A bar that is always there is not only tidier, it is the fix.
 *
 * Goals and the companion ledger are roots because they hold things she manages. The report is
 * a read-only drill-down from the total on Home, so it does not take a permanent slot here.
 *
 * ## Why it looks like this
 *
 * An island, not a floor. A bar welded edge to edge is a wall the page ends against; the bars
 * worth copying float clear of both edges and let the page run underneath, so the phone reads
 * as one continuous surface with a control resting on it.
 *
 * There is exactly one loud thing on it: the gold slug under the tab she is on. Everything
 * else — the slab, the labels, the glyphs — is held quiet so that one shape is unmissable
 * across the room, which is the only job this bar has. Gold is already the app's colour for
 * "this is the answer"; here it answers *where am I*.
 */
enum class Tab(val fa: String) {
    HOME("فرمان"),
    ASSETS("بازار"),
    CARDS("حساب‌ها"),
    LEDGER("دفتر"),
    REPORTS("دیده‌بان"),
}

/**
 * The places *this edition* goes — the whole difference between the two APKs, in one line.
 *
 * The lite build is دارایی and nothing else, which is the app as it shipped through v1.0.4: a
 * list of what you own and what it is worth today. So it is a list of one, [TabBar] draws nothing
 * for a list of one, and the four screens the other entries lead to are simply never reachable.
 *
 * Every `tab = …` in the app has to go through a member of this list, or the lite build can be
 * navigated somewhere it has no way out of.
 */
val tabs: List<Tab> = if (BuildConfig.LITE) listOf(Tab.ASSETS) else Tab.entries

@Composable
fun TabBar(
    selected: Tab,
    ledgerBadge: Int,
    onSelect: (Tab) -> Unit,
    modifier: Modifier = Modifier,
    onQuickAdd: (() -> Unit)? = null,
) {
    val fintechHaptics = LocalFintechHaptics.current
    val density = LocalDensity.current

    // Coordinates mapping: Tab -> Pair(xDp, widthDp)
    var tabBounds by remember { mutableStateOf<Map<Tab, Pair<Dp, Dp>>>(emptyMap()) }

    val selectedBounds = tabBounds[selected]
    val targetOffset = selectedBounds?.first ?: 0.dp
    val targetWidth = selectedBounds?.second ?: 0.dp

    // Continuous spring animation for the floating capsule pill
    val animatedPillOffset by animateDpAsState(
        targetValue = targetOffset,
        animationSpec = spring(
            dampingRatio = 0.75f,
            stiffness = 400f,
        ),
        label = "gliding_pill_offset",
    )
    val animatedPillWidth by animateDpAsState(
        targetValue = targetWidth,
        animationSpec = spring(
            dampingRatio = 0.75f,
            stiffness = 400f,
        ),
        label = "gliding_pill_width",
    )

    Surface(
        modifier = modifier
            .navigationBarsPadding()
            .padding(horizontal = Space.m, vertical = Space.s),
        shape = RoundedCornerShape(22.dp),
        color = MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.95f),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.45f)),
        shadowElevation = 8.dp,
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(68.dp)
                .padding(horizontal = 4.dp, vertical = 4.dp),
        ) {
            // Gliding capsule pill indicator
            if (targetWidth > 0.dp) {
                Box(
                    modifier = Modifier
                        .offset(x = animatedPillOffset)
                        .width(animatedPillWidth)
                        .fillMaxHeight()
                        .padding(vertical = 2.dp, horizontal = 2.dp)
                        .clip(RoundedCornerShape(16.dp))
                        .background(
                            Brush.verticalGradient(
                                listOf(
                                    Hero.top,
                                    Hero.bottom.copy(alpha = 0.90f),
                                )
                            )
                        )
                        .border(1.dp, Hero.gold.copy(alpha = 0.35f), RoundedCornerShape(16.dp)),
                )
            }

            // Tab items row
            Row(
                modifier = Modifier.fillMaxSize(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                val half = tabs.size / 2
                tabs.forEachIndexed { index, tab ->
                    if (onQuickAdd != null && index == half) {
                        // Central high-velocity quick entry action FAB
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxHeight(),
                            contentAlignment = Alignment.Center,
                        ) {
                            Surface(
                                onClick = {
                                    fintechHaptics.heavyClick()
                                    onQuickAdd()
                                },
                                shape = CircleShape,
                                color = Hero.gold,
                                shadowElevation = 4.dp,
                                modifier = Modifier.size(44.dp),
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Canvas(modifier = Modifier.size(20.dp)) {
                                        val stroke = 2.4.dp.toPx()
                                        // Horizontal plus bar
                                        drawLine(
                                            color = Color(0xFF050607),
                                            start = Offset(0f, size.height / 2),
                                            end = Offset(size.width, size.height / 2),
                                            strokeWidth = stroke,
                                            cap = StrokeCap.Round,
                                        )
                                        // Vertical plus bar
                                        drawLine(
                                            color = Color(0xFF050607),
                                            start = Offset(size.width / 2, 0f),
                                            end = Offset(size.width / 2, size.height),
                                            strokeWidth = stroke,
                                            cap = StrokeCap.Round,
                                        )
                                    }
                                }
                            }
                        }
                    }

                    val badge = if (tab == Tab.LEDGER) ledgerBadge else 0
                    val isSelected = tab == selected
                    val iconScale by animateFloatAsState(
                        targetValue = if (isSelected) 1.08f else 1.0f,
                        animationSpec = spring(
                            dampingRatio = Spring.DampingRatioMediumBouncy,
                            stiffness = Spring.StiffnessMedium,
                        ),
                        label = "tab_icon_scale",
                    )

                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight()
                            .onGloballyPositioned { coords ->
                                val parent = coords.parentCoordinates
                                if (parent != null) {
                                    val pos = coords.positionInParent()
                                    val xDp = with(density) { pos.x.toDp() }
                                    val widthDp = with(density) { coords.size.width.toDp() }
                                    if (tabBounds[tab] != Pair(xDp, widthDp)) {
                                        tabBounds = tabBounds + (tab to Pair(xDp, widthDp))
                                    }
                                }
                            }
                            .clip(RoundedCornerShape(16.dp))
                            .clickable(
                                interactionSource = remember { MutableInteractionSource() },
                                indication = null,
                            ) {
                                if (!isSelected) {
                                    fintechHaptics.click()
                                    onSelect(tab)
                                }
                            },
                        contentAlignment = Alignment.Center,
                    ) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center,
                        ) {
                            BadgedBox(
                                badge = {
                                    if (badge > 0) {
                                        Badge(
                                            containerColor = Hero.gold,
                                            contentColor = Color(0xFF050607),
                                        ) {
                                            Text(
                                                faNumber(badge.coerceAtMost(99).toDouble()),
                                                style = figureStyle(Color(0xFF050607)),
                                                fontSize = 10.sp,
                                            )
                                        }
                                    }
                                },
                            ) {
                                val tint = if (isSelected) Hero.gold else MaterialTheme.colorScheme.onSurfaceVariant
                                Canvas(
                                    Modifier
                                        .size(22.dp)
                                        .graphicsLayer {
                                            scaleX = iconScale
                                            scaleY = iconScale
                                            compositingStrategy = CompositingStrategy.Offscreen
                                        },
                                ) { drawTabIcon(tab, tint) }
                            }

                            Spacer(Modifier.height(3.dp))

                            Text(
                                tab.fa,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                style = TextStyle(
                                    fontFamily = Modam,
                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                    fontSize = 11.sp,
                                    color = if (isSelected) Hero.gold else MaterialTheme.colorScheme.onSurfaceVariant,
                                ),
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun GlidingTabBar(
    selected: Tab,
    onSelect: (Tab) -> Unit,
    modifier: Modifier = Modifier,
    ledgerBadge: Int = 0,
    onQuickAdd: (() -> Unit)? = null,
) {
    TabBar(
        selected = selected,
        ledgerBadge = ledgerBadge,
        onSelect = onSelect,
        modifier = modifier,
        onQuickAdd = onQuickAdd,
    )
}

/**
 * Drawn, not imported.
 */
private fun DrawScope.drawTabIcon(tab: Tab, tint: Color) {
    inset(3.dp.toPx()) {
        when (tab) {
            Tab.HOME -> drawHome(tint)
            Tab.ASSETS -> drawAssets(tint)
            Tab.CARDS -> drawCards(tint)
            Tab.LEDGER -> drawLedger(tint)
            Tab.REPORTS -> drawReports(tint)
        }
    }
}

private fun DrawScope.drawCards(tint: Color) {
    val w = size.width
    val h = size.height
    drawRoundRect(
        color = tint,
        topLeft = Offset(w * 0.06f, h * 0.18f),
        size = Size(w * 0.88f, h * 0.64f),
        cornerRadius = CornerRadius(w * 0.08f, w * 0.08f),
        style = pen()
    )
    drawLine(
        color = tint,
        start = Offset(w * 0.06f, h * 0.42f),
        end = Offset(w * 0.94f, h * 0.42f),
        strokeWidth = 1.5.dp.toPx()
    )
    drawCircle(
        color = tint,
        radius = w * 0.06f,
        center = Offset(w * 0.74f, h * 0.62f)
    )
}

/**
 * The one pen. Round on every cap and every corner, so nothing in the set ends in a chisel.
 *
 * Thinner for the category glyphs ([CategoryIcon]), which draw into an 18dp box rather than a
 * 24dp one: the same nib in a smaller box is a heavier drawing, and heavier is what a mark
 * beside 14sp type must not be.
 */
internal fun DrawScope.pen(width: Dp = 2.dp) = Stroke(
    width = width.toPx(),
    cap = StrokeCap.Round,
    join = StrokeJoin.Round,
)

/**
 * How far a knocked-out hole reaches past the near shape it belongs to: half a stroke to swallow
 * the near outline's own width, then a stroke of daylight. Two outlines that pass within less
 * than that read as interlocked rather than as one in front of the other, which is the whole
 * illusion these two glyphs are built on.
 */
private fun DrawScope.knockOut() = 1.dp.toPx() + 1.4.dp.toPx()

private fun DrawScope.drawHome(tint: Color) {
    val w = size.width
    val h = size.height
    // One closed outline rather than a roof floating over a box: the old glyph left a gap
    // between the two, and at this size a gap reads as a mistake rather than as depth.
    val walls = Path().apply {
        moveTo(w * 0.04f, h * 0.44f)
        lineTo(w * 0.5f, h * 0.04f)
        lineTo(w * 0.96f, h * 0.44f)
        lineTo(w * 0.96f, h * 0.96f)
        lineTo(w * 0.04f, h * 0.96f)
        close()
    }
    drawPath(walls, tint, style = pen())
    // An arched door. A rectangle would have done the same work, but the arch is the one line
    // in the set that belongs to this app rather than to every icon sheet on the shelf.
    val door = Path().apply {
        moveTo(w * 0.34f, h * 0.96f)
        lineTo(w * 0.34f, h * 0.76f)
        quadraticTo(w * 0.5f, h * 0.56f, w * 0.66f, h * 0.76f)
        lineTo(w * 0.66f, h * 0.96f)
    }
    drawPath(door, tint, style = pen())
}

/**
 * A receipt: a slip with a torn foot and two rules on it.
 *
 * This was three ruled lines, and three ruled lines is the menu glyph — every icon sheet on the
 * shelf uses it for "a list of anything", which is precisely the wrong word for a book of money.
 * They were also stacked shortest-first to hand the review badge an empty corner, and the badge
 * landed on the short rule anyway. So: a shape with a noun. The torn foot is the whole tell —
 * cover it and this is a document; show it and it is a receipt — and the badge now sits over a
 * corner, the way a badge sits over every other icon ever drawn.
 */
private fun DrawScope.drawLedger(tint: Color) {
    val w = size.width
    val h = size.height
    // Nearly the full box. Narrower, it read as a ruler: a tall thin outline with rungs in it.
    val left = w * 0.06f
    val right = w * 0.94f
    val top = h * 0.08f
    val foot = h * 0.92f
    val teeth = 4
    val step = (right - left) / teeth
    val slip = Path().apply {
        moveTo(left, foot)
        lineTo(left, top)
        lineTo(right, top)
        lineTo(right, foot)
        for (i in 0 until teeth) {
            val x = right - step * i
            lineTo(x - step / 2f, foot - h * 0.11f)
            lineTo(x - step, foot)
        }
    }
    drawPath(slip, tint, style = pen())
    // Two rules, the lower one short — a line item and its amount, not a paragraph.
    for ((i, len) in listOf(1f, 0.55f).withIndex()) {
        val y = h * (0.34f + i * 0.19f)
        val inset = (right - left) * 0.16f
        drawLine(
            tint,
            Offset(left + inset, y),
            Offset(left + inset + (right - left - inset * 2f) * len, y),
            strokeWidth = 2.dp.toPx(),
            cap = StrokeCap.Round,
        )
    }
}

private fun DrawScope.drawGoals(tint: Color) {
    val centre = Offset(size.width / 2f, size.height / 2f)
    val r = size.minDimension / 2f
    drawCircle(tint, radius = r * 0.88f, center = centre, style = pen())
    drawCircle(tint, radius = r * 0.44f, center = centre, style = pen())
    drawCircle(tint, radius = r * 0.1f, center = centre)
}

/**
 * Two coins, the near one in front of the far one.
 *
 * Three attempts to draw this as a stack seen from the side all failed for the same reason:
 * an ellipse with arcs under it is the database glyph, and no amount of spacing talks a reader
 * out of a shape they already know. Face-on, a coin is a disc, and two discs with one occluding
 * the other is money in every icon set there is — and it borrows the same occlusion the
 * companion cards use, which is what makes those two read as one hand.
 */
private fun DrawScope.drawAssets(tint: Color) {
    // The radius and the separation are load-bearing, and the first pairing had them wrong.
    // The near coin clears a hole of r + knockOut around itself; at r = 0.30 and centres 0.28
    // apart that hole was 7.8dp against a 7.1dp separation, so it reached past the far coin's
    // own centre and the two discs fused into one continuous curve. It drew a «۶», not money.
    // Smaller and further apart: the hole now stops well short, and the far coin keeps the
    // three quarters of its outline that make it legible as a second coin.
    val r = size.minDimension * 0.26f
    val near = Offset(size.width * 0.32f, size.height * 0.68f)
    drawCircle(tint, r, Offset(size.width * 0.68f, size.height * 0.32f), style = pen())
    drawCircle(Color.Black, r + knockOut(), near, blendMode = BlendMode.Clear)
    drawCircle(tint, r, near, style = pen())
}

/**
 * The two of them.
 *
 * This was two overlapping rounded rectangles — her phone and the one she set up for someone
 * else — which is a perfectly good drawing of the mechanism and the universal glyph for *copy*.
 * Nobody reads a screen's plumbing off its tab; they read who it is for. This tab is for the
 * person she shares a household with, so it draws people.
 *
 * One figure, and it took three tries to get there. Two heads over a shared shoulder line was
 * the obvious drawing of a household, and it is also — at any head size, any spacing, any
 * curvature — a face. Two round marks above a symmetric arc is a face; small ones set wide over
 * a flat arc is a face wearing a frown. There is no tuning out of it, because the reading does
 * not come from the proportions, it comes from the arrangement.
 *
 * So: one person, which is what «همراه» says anyway. A single circle over an arc cannot be
 * misread as a face — there is only one eye — and it cannot be misread as *copy*, which is the
 * whole reason the two cards had to go.
 */
private fun DrawScope.drawReports(tint: Color) {
    val w = size.width
    val h = size.height
    // Three vertical bars, ascending left to right — the simplest chart glyph.
    val barWidth = w * 0.2f
    val gap = w * 0.08f
    val baseY = h * 0.85f
    val bars = listOf(h * 0.35f, h * 0.55f, h * 0.75f)
    bars.forEachIndexed { i, barH ->
        drawRoundRect(
            color = tint,
            topLeft = Offset(w * 0.12f + i * (barWidth + gap), baseY - barH),
            size = Size(barWidth, barH),
            cornerRadius = androidx.compose.ui.geometry.CornerRadius(w * 0.06f, w * 0.06f),
            style = Stroke(width = 2.dp.toPx(), cap = StrokeCap.Round),
        )
    }
}
