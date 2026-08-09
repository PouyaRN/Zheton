package com.doxigo.muchtoman

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.roundToLong

/**
 * Goals, and the «آیا ارزشش را داشت؟» answers.
 *
 * These two are the whole reward system. There are no points, no streaks, no confetti and no
 * leaderboard, because the FCA's own experiments found those change how people take financial
 * risks — and this app has no business doing that to anybody. What is left is the encouragement
 * with evidence behind it: a concrete goal she set, and her own verdict on her own spending.
 */

@Composable
fun GoalsScreen(
    goals: List<GoalProgress>,
    categories: List<Category>,
    onAdd: (String, Long, String, String?) -> Unit,
    onDelete: (String) -> Unit,
    bottomInset: androidx.compose.ui.unit.Dp,
) {
    var name by remember { mutableStateOf("") }
    var amount by remember { mutableStateOf("") }
    var isCap by remember { mutableStateOf(false) }
    var selectedCategoryId by remember { mutableStateOf<String?>(null) }
    val targetRial = remember(amount) {
        parseAmount(amount)
            ?.takeIf { it > 0.0 && it <= Long.MAX_VALUE / 10.0 }
            ?.let { (it * 10.0).roundToLong() }
    }

    Surface(color = MaterialTheme.colorScheme.background, modifier = Modifier.fillMaxSize()) {
        Column(
            Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .padding(horizontal = Space.xl)
                .verticalScroll(rememberScrollState())
                .padding(bottom = bottomInset + Space.l),
        ) {
            Row(Modifier.fillMaxWidth().padding(vertical = Space.m), verticalAlignment = Alignment.CenterVertically) {
                Text(
                    "هدف‌ها",
                    fontSize = 28.sp,
                    fontWeight = FontWeight.ExtraBold,
                    color = MaterialTheme.colorScheme.onBackground,
                    modifier = Modifier.weight(1f).semantics { heading() },
                )
            }

            if (goals.isEmpty()) {
                Text(
                    "اینجا فقط هدف‌هایی رو می‌بینی که خودت انتخاب کردی. " +
                        "خبری از امتیاز، زنجیره یا مقایسه با بقیه نیست.",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    lineHeight = 26.sp,
                )
                Spacer(Modifier.height(Space.l))
            }

            for (progress in goals) {
                GoalCard(progress, categories, onDelete = { onDelete(progress.goal.id) })
                Spacer(Modifier.height(Space.m))
            }

            Spacer(Modifier.height(Space.l))
            Text(
                "هدف تازه",
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onBackground,
            )
            Spacer(Modifier.height(Space.s))

            // Goal type selector
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(Space.m),
            ) {
                FilterChip(
                    selected = !isCap,
                    onClick = { isCap = false; selectedCategoryId = null },
                    label = { Text("پس‌انداز") },
                    modifier = Modifier.weight(1f),
                )
                FilterChip(
                    selected = isCap,
                    onClick = { isCap = true },
                    label = { Text("سقف هزینه") },
                    modifier = Modifier.weight(1f),
                )
            }

            if (isCap) {
                Spacer(Modifier.height(Space.s))
                Text(
                    "دسته‌بندی",
                    fontSize = 13.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(bottom = Space.xs),
                )
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(Space.s),
                ) {
                    categories.filter { it.kind == "expense" }.forEach { category ->
                        FilterChip(
                            selected = category.id == selectedCategoryId,
                            onClick = {
                                selectedCategoryId = if (category.id == selectedCategoryId) null else category.id
                            },
                            label = { Text(category.nameFa, fontSize = 12.sp) },
                        )
                    }
                }
            }

            Spacer(Modifier.height(Space.s))
            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                label = { Text(if (isCap) "نام سقف (مثلاً: رستوران)" else "هدفت چیه؟") },
                singleLine = true,
                shape = RoundedCornerShape(Radius.field),
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(Space.s))
            OutlinedTextField(
                value = amount,
                onValueChange = { amount = it },
                label = { Text("مبلغ به تومان") },
                singleLine = true,
                visualTransformation = GroupedNumber,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                supportingText = if (amount.isNotBlank() && targetRial == null) ({
                    Text("این عدد قابل خوندن نیست. فقط عدد وارد کن.")
                }) else null,
                shape = RoundedCornerShape(Radius.field),
                modifier = Modifier
                    .fillMaxWidth()
                    .semantics { contentDescription = "مبلغ هدف به تومان" },
            )
            Spacer(Modifier.height(Space.s))
            Button(
                onClick = {
                    val rial = targetRial ?: return@Button
                    if (name.isBlank()) return@Button
                    val kind = if (isCap) GoalKind.CAP else GoalKind.SAVE
                    val goalName = if (isCap && selectedCategoryId != null) {
                        val catName = categories.firstOrNull { it.id == selectedCategoryId }?.nameFa ?: ""
                        "$name ($catName)"
                    } else name.trim()
                    onAdd(goalName, rial, kind, if (isCap) selectedCategoryId else null)
                    name = ""; amount = ""; selectedCategoryId = null
                },
                enabled = name.isNotBlank() && targetRial != null && (!isCap || selectedCategoryId != null),
                modifier = Modifier.fillMaxWidth(),
            ) { Text("ذخیره هدف") }

            Spacer(Modifier.height(Space.huge))
        }
    }
}

@Composable
private fun GoalCard(progress: GoalProgress, categories: List<Category>, onDelete: () -> Unit) {
    // Green ≤70%, amber 70–90%, red >90% for caps; green when done for savings.
    val barColor = when {
        progress.cap && progress.done -> Color(0xFF2E9E5B)
        progress.cap && progress.share <= 0.7f -> Color(0xFF2E9E5B)
        progress.cap && progress.share <= 0.9f -> Color(0xFFF9A825)
        progress.cap -> MaterialTheme.colorScheme.error
        progress.done -> Color(0xFF2E9E5B)
        else -> MaterialTheme.colorScheme.primary
    }
    Panel {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                progress.goal.nameFa,
                Modifier.weight(1f),
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Text(
                "حذف",
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier
                    .clip(RoundedCornerShape(Radius.pill))
                    .clickable(role = Role.Button, onClick = onDelete)
                    .padding(vertical = Space.xs, horizontal = Space.s),
            )
        }
        Spacer(Modifier.height(Space.s))
        Text(
            bidi("${faCompact(tomanOf(progress.currentRial))} از ${faCompact(tomanOf(progress.targetRial))}"),
            style = figureStyle(MaterialTheme.colorScheme.onSurface, FontWeight.ExtraBold),
            fontSize = 20.sp,
        )
        Spacer(Modifier.height(Space.s))
        // Progress bar with tint
        Box(
            Modifier
                .fillMaxWidth()
                .height(8.dp)
                .clip(RoundedCornerShape(4.dp))
                .background(MaterialTheme.colorScheme.surface),
        ) {
            Box(
                Modifier
                    .fillMaxWidth(progress.share)
                    .fillMaxHeight()
                    .clip(RoundedCornerShape(4.dp))
                    .background(barColor),
            )
        }
        // Call-out: what category this cap counts
        if (progress.cap && progress.goal.categoryId != null) {
            val catFa = categories.find { it.id == progress.goal.categoryId }?.nameFa ?: ""
            if (catFa.isNotBlank()) {
                Spacer(Modifier.height(Space.xs))
                Text(
                    "شامل دسته «$catFa»",
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        // Pace estimate
        if (progress.paceDay != null) {
            Spacer(Modifier.height(Space.xs))
            Text(
                "با این روند، روز ${faNumber(progress.paceDay.toDouble())} تموم می‌شه.",
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        when {
            progress.done && !progress.cap -> {
                Spacer(Modifier.height(Space.s))
                Text("به هدفت رسیدی.", fontWeight = FontWeight.Bold, color = barColor)
            }
            progress.underWater -> {
                Spacer(Modifier.height(Space.s))
                Text(
                    "از وقتی این هدف رو گذاشتی، هنوز چیزی پس‌انداز نشده.",
                    fontSize = 13.sp,
                    lineHeight = 22.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            progress.cap && progress.done -> {
                Spacer(Modifier.height(Space.s))
                Text("زیر سقف موندی.", fontWeight = FontWeight.Bold, color = barColor)
            }
        }
    }
}

