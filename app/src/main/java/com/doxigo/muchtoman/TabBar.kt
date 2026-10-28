package com.doxigo.muchtoman

import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.doxigo.muchtoman.ui.components.CanvasNavBar

/**
 * The five places the app goes, and the way back from any of them, in the canvas's order:
 * امروز، دفتر، دارایی، حساب‌ها، گزارش.
 *
 * Before this there was no way home except the system back button: every screen was an overlay
 * with a «بستن» in the corner, and overlays over overlays meant she could get somewhere she
 * could not get out of. A bar that is always there is not only tidier, it is the fix.
 *
 * The report used to share a slot with the ledger behind a segmented pill («دفتر و تحلیل»).
 * The redesign canvas gives it its own destination, so it has one.
 */
enum class Tab(val fa: String) {
    HOME("امروز"),
    LEDGER("دفتر"),
    ASSETS("دارایی"),
    CARDS("حساب‌ها"),
    REPORTS("گزارش"),
}

/**
 * The places *this edition* goes — the whole difference between the two APKs, in one line.
 *
 * The lite build is دارایی and nothing else, which is the app as it shipped through v1.0.4: a
 * list of what you own and what it is worth today. So it is a list of one, [TabBar] draws nothing
 * for a list of one, and the screens the other entries lead to are simply never reachable.
 *
 * Every `tab = …` in the app has to go through a member of this list, or the lite build can be
 * navigated somewhere it has no way out of.
 */
val tabs: List<Tab> = if (BuildConfig.LITE) listOf(Tab.ASSETS) else Tab.entries

/**
 * The app's bottom bar: the canvas's floating pill ([CanvasNavBar]) for the full edition. The
 * lite edition has one place and no bar, but still holds the gesture inset so the last row
 * clears it.
 */
@Composable
fun TabBar(
    selected: Tab,
    onSelect: (Tab) -> Unit,
    modifier: Modifier = Modifier,
) {
    if (tabs.size > 1) {
        CanvasNavBar(tabs = tabs, selected = selected, onSelect = onSelect, modifier = modifier)
    } else {
        Spacer(modifier.fillMaxWidth().navigationBarsPadding())
    }
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
