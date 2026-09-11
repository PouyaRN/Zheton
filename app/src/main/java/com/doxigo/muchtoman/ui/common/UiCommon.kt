package com.doxigo.muchtoman.ui.common

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.dp
import com.doxigo.muchtoman.Radius
import com.doxigo.muchtoman.Space

@Composable
fun Modifier.panel(): Modifier = this
    .fillMaxWidth()
    .clip(RoundedCornerShape(Radius.card))
    .background(MaterialTheme.colorScheme.surfaceContainer)
    .border(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f), RoundedCornerShape(Radius.card))
    .padding(Space.l)

@Composable
fun Panel(
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(modifier.panel(), content = content)
}

val ScreenEdgePadding = PaddingValues(horizontal = Space.xl)

fun bandShape(index: Int, count: Int): Shape {
    val r = Radius.group
    return when {
        count == 1 -> RoundedCornerShape(r)
        index == 0 -> RoundedCornerShape(topStart = r, topEnd = r)
        index == count - 1 -> RoundedCornerShape(bottomStart = r, bottomEnd = r)
        else -> RoundedCornerShape(0.dp)
    }
}
