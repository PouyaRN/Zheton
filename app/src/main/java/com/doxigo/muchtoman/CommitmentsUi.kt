package com.doxigo.muchtoman

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * Visual executive card summarizing active commitments (BNPL, bank loans, Sayad checks)
 * with inflation relief metrics and due-date countdowns.
 */
@Composable
fun CommitmentsCard(
    commitments: List<Commitment>,
    modifier: Modifier = Modifier,
    onOpenHub: () -> Unit = {},
    onAddCommitment: () -> Unit = {},
    privacyMode: Boolean = false,
) {
    val activeCommitments = commitments.filter { !it.isSettled }
    if (activeCommitments.isEmpty()) return

    val totalRemainingToman = activeCommitments.sumOf { it.remainingAmountToman }
    // Calculate total psychological inflation relief across all active commitments
    val totalInflationReliefToman = activeCommitments.sumOf {
        val months = (it.nextDueDays / 30).coerceAtLeast(1)
        it.inflationReliefToman(months)
    }

    Card(
        onClick = onOpenHub,
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
                    "تعهدات مالی، اقساط و چک‌های صیادی",
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.weight(1f),
                )
                TextButton(onClick = onAddCommitment) {
                    Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(Space.xs))
                    Text("ثبت جدید", fontSize = 13.sp)
                }
            }

            Spacer(Modifier.height(Space.xs))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column {
                    Text(
                        "مانده کل تعهدات:",
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                    )
                    Text(
                        if (privacyMode) "••••••" else "${faNumber(totalRemainingToman.toDouble())} تومان",
                        fontSize = 15.sp,
                        fontFamily = ModamFigures,
                        fontWeight = FontWeight.Bold,
                    )
                }

                // Inflation relief badge
                if (totalInflationReliefToman > 0L) {
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .background(Hero.mint.copy(alpha = 0.15f))
                            .padding(horizontal = 8.dp, vertical = 4.dp),
                    ) {
                        Text(
                            text = if (privacyMode) "سپر تورمی فعال"
                            else "سپر تورمی: -${faNumber(totalInflationReliefToman.toDouble())} تومن",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = Hero.mint,
                        )
                    }
                }
            }

            Spacer(Modifier.height(Space.s))
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))
            Spacer(Modifier.height(Space.s))

            // Highlight top 2 urgent commitments
            val topItems = activeCommitments.sortedBy { it.nextDueDays }.take(2)
            for (item in topItems) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 3.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        val kindLabel = when (item.kind) {
                            CommitmentKind.BNPL -> "BNPL"
                            CommitmentKind.LOAN -> "وام"
                            CommitmentKind.CHECK -> "چک"
                            CommitmentKind.INSTALLMENT -> "قسط"
                        }
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(4.dp))
                                .background(MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f))
                                .padding(horizontal = 4.dp, vertical = 2.dp),
                        ) {
                            Text(kindLabel, fontSize = 9.sp, fontWeight = FontWeight.Bold)
                        }
                        Spacer(Modifier.width(Space.s))
                        Text(
                            item.title.ifBlank { item.provider },
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Medium,
                        )
                    }

                    Row(verticalAlignment = Alignment.CenterVertically) {
                        if (item.nextDueDays > 0) {
                            val shamsiDate = formatShamsiDate(item.nextDueTimestamp)
                            Text(
                                if (shamsiDate.isNotBlank()) "$shamsiDate (${faNumber(item.nextDueDays.toDouble())} روز)"
                                else "${faNumber(item.nextDueDays.toDouble())} روز تا سررسید",
                                fontSize = 11.sp,
                                color = if (item.nextDueDays <= 5) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            Spacer(Modifier.width(Space.s))
                        }
                        Text(
                            if (privacyMode) "••••••" else "${faNumber(item.remainingAmountToman.toDouble())}",
                            fontSize = 13.sp,
                            fontFamily = ModamFigures,
                            fontWeight = FontWeight.SemiBold,
                        )
                    }
                }
            }
        }
    }
}

/**
 * Dedicated bottom sheet hub for managing all liabilities, BNPL installments, bank loans, and Sayad checks.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CommitmentsHubSheet(
    commitments: List<Commitment>,
    onDismiss: () -> Unit,
    onAdd: () -> Unit,
    onToggleSettled: (String) -> Unit,
    onDelete: (String) -> Unit,
    privacyMode: Boolean = false,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var filterKind by remember { mutableStateOf<CommitmentKind?>(null) }

    val filtered = commitments.filter {
        filterKind == null || it.kind == filterKind
    }.sortedBy { it.isSettled }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.surface,
    ) {
        Column(
            Modifier
                .fillMaxWidth()
                // Same guard as the other sheets: without it the hub's list handed its
                // leftover fling to the sheet spring and the whole sheet jumped at the end
                // of every scroll.
                .nestedScroll(SheetFlingGuard)
                .padding(horizontal = Space.l)
                .padding(bottom = Space.xl)
        ) {
            Row(
                Modifier.fillMaxWidth().padding(bottom = Space.m),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    "مدیریت تعهدات مالی و اقساط",
                    fontSize = 18.sp,
                    fontWeight = FontWeight.ExtraBold,
                )
                Button(
                    onClick = onAdd,
                    shape = RoundedCornerShape(Radius.pill),
                    colors = ButtonDefaults.buttonColors(containerColor = Hero.gold),
                    contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                ) {
                    Icon(Icons.Default.Add, contentDescription = null, tint = Color.Black, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("ثبت تعهد", color = Color.Black, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                }
            }

            // Category filter chips
            Row(
                Modifier.fillMaxWidth().padding(bottom = Space.m),
                horizontalArrangement = Arrangement.spacedBy(Space.xs),
            ) {
                FilterChip(
                    selected = filterKind == null,
                    onClick = { filterKind = null },
                    label = { Text("همه", fontSize = 12.sp) },
                )
                FilterChip(
                    selected = filterKind == CommitmentKind.BNPL,
                    onClick = { filterKind = CommitmentKind.BNPL },
                    label = { Text("BNPL", fontSize = 12.sp) },
                )
                FilterChip(
                    selected = filterKind == CommitmentKind.LOAN,
                    onClick = { filterKind = CommitmentKind.LOAN },
                    label = { Text("وام بانکی", fontSize = 12.sp) },
                )
                FilterChip(
                    selected = filterKind == CommitmentKind.CHECK,
                    onClick = { filterKind = CommitmentKind.CHECK },
                    label = { Text("چک صیادی", fontSize = 12.sp) },
                )
            }

            if (filtered.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = Space.xxl),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        "تعهدی در این دسته‌بندی ثبت نشده است.",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontSize = 14.sp,
                    )
                }
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(Space.m),
                ) {
                    items(filtered, key = { it.id }) { item ->
                        CommitmentItemRow(
                            item = item,
                            privacyMode = privacyMode,
                            onToggleSettled = { onToggleSettled(item.id) },
                            onDelete = { onDelete(item.id) },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun CommitmentItemRow(
    item: Commitment,
    privacyMode: Boolean,
    onToggleSettled: () -> Unit,
    onDelete: () -> Unit,
) {
    Card(
        shape = RoundedCornerShape(Radius.card),
        colors = CardDefaults.cardColors(
            containerColor = if (item.isSettled) MaterialTheme.colorScheme.surfaceContainerHighest.copy(alpha = 0.5f)
            else MaterialTheme.colorScheme.surfaceContainer,
        ),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(Space.m)) {
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(24.dp)
                            .clip(CircleShape)
                            .background(
                                if (item.isSettled) Hero.mint else MaterialTheme.colorScheme.outlineVariant
                            )
                            .clickable(onClick = onToggleSettled),
                        contentAlignment = Alignment.Center,
                    ) {
                        if (item.isSettled) {
                            Icon(Icons.Default.Check, contentDescription = "تکمیل شده", tint = Color.White, modifier = Modifier.size(16.dp))
                        }
                    }
                    Spacer(Modifier.width(Space.m))
                    Column {
                        Text(
                            item.title.ifBlank { item.provider },
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold,
                            color = if (item.isSettled) MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f) else MaterialTheme.colorScheme.onSurface,
                        )
                        Text(
                            "${item.provider} • ${item.kind.fa}",
                            fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                        )
                    }
                }

                IconButton(onClick = onDelete) {
                    Icon(Icons.Default.Delete, contentDescription = "حذف", tint = MaterialTheme.colorScheme.error.copy(alpha = 0.7f), modifier = Modifier.size(18.dp))
                }
            }

            Spacer(Modifier.height(Space.s))
            // Progress bar
            LinearProgressIndicator(
                progress = { item.progressPercent },
                modifier = Modifier.fillMaxWidth().height(6.dp).clip(RoundedCornerShape(3.dp)),
                color = if (item.isSettled) Hero.mint else Hero.gold,
                trackColor = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f),
            )

            Spacer(Modifier.height(Space.s))
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                val monthsFuture = (item.nextDueDays / 30).coerceAtLeast(1)
                val reliefToman = item.inflationReliefToman(monthsFuture)
                val shamsiDue = formatShamsiDate(item.nextDueTimestamp)

                Column {
                    if (!item.isSettled && shamsiDue.isNotBlank()) {
                        Text(
                            "سررسید: $shamsiDue (${faNumber(item.nextDueDays.toDouble())} روز)",
                            fontSize = 11.sp,
                            color = if (item.nextDueDays <= 5) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Text(
                        if (item.isSettled) "تسویه شده ✓"
                        else if (reliefToman > 0L) "سود از تورم: -${faNumber(reliefToman.toDouble())} تومان"
                        else "بدون تخفیف تورمی",
                        fontSize = 11.sp,
                        color = if (item.isSettled) Hero.mint else Color(0xFF00C853),
                        fontWeight = FontWeight.SemiBold,
                    )
                }

                Column(horizontalAlignment = Alignment.End) {
                    Text(
                        if (privacyMode) "••••••"
                        else "مانده: ${faNumber(item.remainingAmountToman.toDouble())} تومان",
                        fontSize = 13.sp,
                        fontFamily = ModamFigures,
                        fontWeight = FontWeight.Bold,
                    )
                    if (item.installmentCount > 1) {
                        Text(
                            "${faNumber(item.paidInstallmentCount.toDouble())} از ${faNumber(item.installmentCount.toDouble())} قسط",
                            fontSize = 10.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                        )
                    }
                }
            }
        }
    }
}

/**
 * Bottom sheet to add an offline-first financial commitment, BNPL loan, or Sayad check.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddCommitmentSheet(
    onDismiss: () -> Unit,
    onSave: (Commitment) -> Unit,
) {
    var title by remember { mutableStateOf("") }
    var provider by remember { mutableStateOf("") }
    var kind by remember { mutableStateOf(CommitmentKind.BNPL) }
    var totalAmountText by remember { mutableStateOf("") }
    var installmentsCountText by remember { mutableStateOf("4") }
    var dueDaysText by remember { mutableStateOf("30") }
    var note by remember { mutableStateOf("") }

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
                .padding(bottom = Space.xl)
        ) {
            Text(
                "ثبت تعهد مالی یا چک صیادی",
                fontSize = 18.sp,
                fontWeight = FontWeight.ExtraBold,
                modifier = Modifier.padding(bottom = Space.m),
            )

            // Kind selector
            Row(
                Modifier.fillMaxWidth().padding(bottom = Space.m),
                horizontalArrangement = Arrangement.spacedBy(Space.xs),
            ) {
                CommitmentKind.entries.forEach { k ->
                    FilterChip(
                        selected = kind == k,
                        onClick = { kind = k },
                        label = {
                            val shortLabel = when (k) {
                                CommitmentKind.BNPL -> "BNPL"
                                CommitmentKind.LOAN -> "وام"
                                CommitmentKind.CHECK -> "چک صیادی"
                                CommitmentKind.INSTALLMENT -> "خرید قسطی"
                            }
                            Text(shortLabel, fontSize = 11.sp)
                        },
                    )
                }
            }

            OutlinedTextField(
                value = title,
                onValueChange = { title = it },
                label = { Text("عنوان (مثلاً گوشی موبایل، وام ازدواج)") },
                modifier = Modifier.fillMaxWidth().padding(bottom = Space.s),
                singleLine = true,
            )

            OutlinedTextField(
                value = provider,
                onValueChange = { provider = it },
                label = { Text("ارائه‌دهنده یا بانک (مثلاً اسنپ‌پی، دیجی‌پی، بانک رسالت)") },
                modifier = Modifier.fillMaxWidth().padding(bottom = Space.s),
                singleLine = true,
            )

            OutlinedTextField(
                value = totalAmountText,
                onValueChange = { totalAmountText = it.filter { c -> c.isDigit() || c == ',' } },
                label = { Text("مبلغ کل تعهد (تومان)") },
                modifier = Modifier.fillMaxWidth().padding(bottom = Space.s),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                singleLine = true,
            )

            Row(
                Modifier.fillMaxWidth().padding(bottom = Space.s),
                horizontalArrangement = Arrangement.spacedBy(Space.m),
            ) {
                OutlinedTextField(
                    value = installmentsCountText,
                    onValueChange = { installmentsCountText = it.filter { c -> c.isDigit() } },
                    label = { Text("تعداد کل اقساط") },
                    modifier = Modifier.weight(1f),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    singleLine = true,
                )

                OutlinedTextField(
                    value = dueDaysText,
                    onValueChange = { dueDaysText = it.filter { c -> c.isDigit() } },
                    label = { Text("روز تا سررسید") },
                    modifier = Modifier.weight(1f),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    singleLine = true,
                )
            }

            OutlinedTextField(
                value = note,
                onValueChange = { note = it },
                label = { Text("یادداشت اختیاری (مثلاً شناسه صیاد ۱۶ رقمی)") },
                modifier = Modifier.fillMaxWidth().padding(bottom = Space.l),
                singleLine = true,
            )

            Button(
                onClick = {
                    val rawToman = totalAmountText.filter { it.isDigit() }.toLongOrNull() ?: 0L
                    val totalRial = rawToman * 10L
                    val count = installmentsCountText.filter { it.isDigit() }.toIntOrNull() ?: 1
                    val days = dueDaysText.filter { it.isDigit() }.toLongOrNull() ?: 30L
                    val dueTimestamp = System.currentTimeMillis() + (days * 24 * 3600 * 1000L)

                    if (totalRial > 0L) {
                        val commitment = Commitment(
                            id = uuid7(System.currentTimeMillis()),
                            title = title.ifBlank { provider.ifBlank { "تعهد مالی" } },
                            provider = provider.ifBlank { "شخصی" },
                            kind = kind,
                            totalAmountRial = totalRial,
                            remainingAmountRial = totalRial,
                            installmentCount = count.coerceAtLeast(1),
                            paidInstallmentCount = 0,
                            nextDueTimestamp = dueTimestamp,
                            note = note.trim(),
                        )
                        onSave(commitment)
                    }
                },
                enabled = totalAmountText.isNotBlank(),
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(Radius.pill),
                colors = ButtonDefaults.buttonColors(containerColor = Hero.gold),
            ) {
                Text("ذخیره تعهد", color = Color.Black, fontWeight = FontWeight.Bold)
            }
        }
    }
}

/**
 * Returns a human-friendly Shamsi date string like «۱۵ اردیبهشت ۱۴۰۳» from epoch millis.
 */
fun formatShamsiDate(millis: Long): String {
    if (millis <= 0L) return ""
    val jalali = jalaliOf(tehranDay(millis))
    val monthName = MONTHS.getOrElse(jalali.month - 1) { "" }
    return "${faNumber(jalali.day.toDouble())} $monthName ${jalali.year}"
}
