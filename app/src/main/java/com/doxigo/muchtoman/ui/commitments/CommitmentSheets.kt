package com.doxigo.muchtoman.ui.commitments

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.doxigo.muchtoman.Hero
import com.doxigo.muchtoman.InstallmentObligation
import com.doxigo.muchtoman.InstallmentPayment
import com.doxigo.muchtoman.LedgerEntry
import com.doxigo.muchtoman.ModamFigures
import com.doxigo.muchtoman.Radius
import com.doxigo.muchtoman.Space
import com.doxigo.muchtoman.UiState
import com.doxigo.muchtoman.faNumber
import com.doxigo.muchtoman.formatShamsiDate
import com.doxigo.muchtoman.jalaliDay
import com.doxigo.muchtoman.tehranDay
import com.doxigo.muchtoman.ui.components.DetailRow

/**
 * Full detail screen for an installment obligation.
 * Shows provider, totals, and the complete payment schedule.
 */
@Composable
fun ObligationDetailScreen(
    obligation: InstallmentObligation,
    payments: List<InstallmentPayment>,
    onMarkPaid: (String) -> Unit,
    onPayOffRemaining: (String) -> Unit,
    onArchive: (String) -> Unit,
    onBack: () -> Unit,
) {
    val paidCount = payments.count { it.paidDate != null }
    val allPaid = paidCount == obligation.installmentCount
    val totalRemaining = (obligation.installmentCount - paidCount).toLong() * obligation.installmentRial

    Surface(
        color = MaterialTheme.colorScheme.background,
        modifier = Modifier.fillMaxSize(),
    ) {
        Column(
            Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .verticalScroll(rememberScrollState()),
        ) {
            // Header
            Row(
                Modifier.fillMaxWidth().padding(horizontal = Space.l, vertical = Space.m),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconButton(onClick = onBack) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "بازگشت")
                }
                Column(Modifier.weight(1f)) {
                    Text(
                        obligation.provider.ifBlank { "اقساط" },
                        fontSize = 22.sp,
                        fontWeight = FontWeight.ExtraBold,
                    )
                    Text(
                        "${paidCount}/${obligation.installmentCount} قسط پرداخت شده",
                        fontSize = 13.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            // Summary card
            Card(
                shape = RoundedCornerShape(Radius.card),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant,
                ),
                modifier = Modifier.fillMaxWidth().padding(horizontal = Space.l),
            ) {
                Column(Modifier.padding(Space.l)) {
                    DetailRow("قیمت کل", "${faNumber(obligation.totalRial / 10.0)} تومان")
                    DetailRow("پیش پرداخت", "${faNumber(obligation.downPaymentRial / 10.0)} تومان")
                    DetailRow(
                        "مبلغ هر قسط",
                        "${faNumber(obligation.installmentRial / 10.0)} تومان × ${obligation.installmentCount}",
                    )
                    if (!allPaid) {
                        DetailRow(
                            "باقیمانده کل",
                            "${faNumber(totalRemaining / 10.0)} تومان",
                            bold = true,
                        )
                    } else {
                        DetailRow(
                            "وضعیت",
                            "تسویه کامل شده",
                            bold = true,
                            valueColor = Hero.mint,
                        )
                    }

                    // Budget relief info
                    val reliefToman = obligation.installmentRial / 10
                    if (reliefToman > 0 && allPaid) {
                        DetailRow(
                            "کاهش هزینه ماهانه",
                            "-${faNumber(reliefToman.toDouble())} تومان",
                            bold = true,
                            valueColor = Hero.mint,
                        )
                    }
                }
            }

            if (obligation.notes.isNotBlank()) {
                Text(
                    obligation.notes,
                    fontSize = 13.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = Space.l, vertical = Space.m),
                )
            }

            // Actions
            if (!allPaid) {
                Row(
                    Modifier.fillMaxWidth().padding(horizontal = Space.l, vertical = Space.s),
                    horizontalArrangement = Arrangement.spacedBy(Space.m),
                ) {
                    Button(
                        onClick = { onPayOffRemaining(obligation.id) },
                        modifier = Modifier.weight(1f),
                    ) {
                        Text("تسویه کل باقیمانده")
                    }
                }
            } else {
                Button(
                    onClick = { onArchive(obligation.id) },
                    modifier = Modifier.fillMaxWidth().padding(horizontal = Space.l, vertical = Space.s),
                ) {
                    Text("بایگانی")
                }
            }

            // Payment schedule
            Text(
                " برنامه پرداخت",
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(horizontal = Space.l, vertical = Space.m),
            )

            Card(
                shape = RoundedCornerShape(Radius.card),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant,
                ),
                modifier = Modifier.fillMaxWidth().padding(horizontal = Space.l),
            ) {
                Column(Modifier.padding(Space.m)) {
                    payments.sortedBy { it.installmentNumber }.forEach { payment ->
                        val isPaid = payment.paidDate != null
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .padding(vertical = Space.s),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Column(Modifier.weight(1f)) {
                                Text(
                                    "قسط ${payment.installmentNumber} — ${formatShamsiDate(payment.dueDate)}",
                                    fontSize = 13.sp,
                                    fontWeight = if (!isPaid) FontWeight.Bold else FontWeight.Normal,
                                    color = if (isPaid) MaterialTheme.colorScheme.onSurfaceVariant
                                    else MaterialTheme.colorScheme.onSurface,
                                )
                                if (isPaid) {
                                    Text(
                                        "پرداخت شده در ${formatShamsiDate(payment.paidDate!!)}",
                                        fontSize = 11.sp,
                                        color = Hero.mint,
                                    )
                                }
                            }
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    "${faNumber(payment.amountRial / 10.0)} ت",
                                    fontSize = 13.sp,
                                    fontFamily = ModamFigures,
                                    color = if (isPaid) MaterialTheme.colorScheme.onSurfaceVariant
                                    else MaterialTheme.colorScheme.onSurface,
                                )
                                Spacer(Modifier.width(Space.s))
                                if (!isPaid) {
                                    TextButton(onClick = { onMarkPaid(payment.id) }) {
                                        Text("پرداخت", fontSize = 12.sp)
                                    }
                                }
                            }
                        }
                    }
                }
            }

            Spacer(Modifier.padding(bottom = Space.xxl))
        }
    }
}

/**
 * Bottom sheet form for marking a transaction as a credit/installment purchase.
 * Pre-fills the down payment from the transaction amount.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CreditPurchaseForm(
    entry: LedgerEntry,
    onSave: (provider: String, total: Long, installments: Int, amount: Long, startDate: Long, notes: String) -> Unit,
    onDismiss: () -> Unit,
) {
    val downPayment = kotlin.math.abs(entry.txn.signedRial ?: entry.txn.amountRial ?: 0L)
    var provider by remember { mutableStateOf("") }
    var totalText by remember { mutableStateOf("") }
    var installmentsText by remember { mutableStateOf("") }
    var amountText by remember { mutableStateOf("") }
    var notes by remember { mutableStateOf("") }
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
                .padding(bottom = Space.xl),
        ) {
            Text(
                "ثبت خرید اعتباری",
                fontSize = 20.sp,
                fontWeight = FontWeight.ExtraBold,
                modifier = Modifier.padding(bottom = Space.l),
            )

            Text(
                "پیش پرداخت: ${faNumber(downPayment / 10.0)} تومان",
                fontSize = 13.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(bottom = Space.m),
            )

            // Provider name
            OutlinedTextField(
                value = provider,
                onValueChange = { provider = it },
                label = { Text("نام فروشگاه / ارائه‌دهنده") },
                modifier = Modifier.fillMaxWidth().padding(bottom = Space.m),
                singleLine = true,
            )

            // Total purchase price
            OutlinedTextField(
                value = totalText,
                onValueChange = { totalText = it },
                label = { Text("قیمت کل (تومان)") },
                modifier = Modifier.fillMaxWidth().padding(bottom = Space.m),
                singleLine = true,
            )

            // Installment count and monthly amount
            Row(
                Modifier.fillMaxWidth().padding(bottom = Space.m),
                horizontalArrangement = Arrangement.spacedBy(Space.m),
            ) {
                OutlinedTextField(
                    value = installmentsText,
                    onValueChange = { installmentsText = it },
                    label = { Text("تعداد اقساط") },
                    modifier = Modifier.weight(1f),
                    singleLine = true,
                )
                OutlinedTextField(
                    value = amountText,
                    onValueChange = { amountText = it },
                    label = { Text("مبلغ هر قسط (تومان)") },
                    modifier = Modifier.weight(1f),
                    singleLine = true,
                )
            }

            // Notes
            OutlinedTextField(
                value = notes,
                onValueChange = { notes = it },
                label = { Text("یادداشت (اختیاری)") },
                modifier = Modifier.fillMaxWidth().padding(bottom = Space.l),
                singleLine = true,
            )

            // Submit
            val count = installmentsText.toIntOrNull() ?: 0
            val installmentToman = amountText.replace(",", "").replace("،", "").toLongOrNull() ?: 0L
            val totalToman = totalText.replace(",", "").replace("،", "").toLongOrNull() ?: 0L

            Button(
                onClick = {
                    onSave(
                        provider.trim(),
                        totalToman * 10L,
                        count,
                        installmentToman * 10L,
                        entry.txn.day,
                        notes.trim(),
                    )
                },
                enabled = provider.isNotBlank() && count > 0 && installmentToman > 0L,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text("ثبت اقساط")
            }
        }
    }
}

/**
 * Direct obligation creator sheet — creates an obligation without an originating transaction.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DirectObligationSheet(
    onSave: (provider: String, total: Long, installments: Int, amount: Long, startDate: Long, notes: String) -> Unit,
    onDismiss: () -> Unit,
) {
    var provider by remember { mutableStateOf("") }
    var totalText by remember { mutableStateOf("") }
    var installmentsText by remember { mutableStateOf("4") }
    var amountText by remember { mutableStateOf("") }
    var notes by remember { mutableStateOf("") }
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
                .padding(bottom = Space.xl),
        ) {
            Text(
                "ثبت تعهد / قسط جدید",
                fontSize = 20.sp,
                fontWeight = FontWeight.ExtraBold,
                modifier = Modifier.padding(bottom = Space.l),
            )

            OutlinedTextField(
                value = provider,
                onValueChange = { provider = it },
                label = { Text("طرف حساب / فروشگاه (مثلاً اسنپ‌پی، بانک)") },
                modifier = Modifier.fillMaxWidth().padding(bottom = Space.m),
                singleLine = true,
            )

            OutlinedTextField(
                value = totalText,
                onValueChange = { totalText = it },
                label = { Text("قیمت کل (تومان)") },
                modifier = Modifier.fillMaxWidth().padding(bottom = Space.m),
                singleLine = true,
            )

            Row(
                Modifier.fillMaxWidth().padding(bottom = Space.m),
                horizontalArrangement = Arrangement.spacedBy(Space.m),
            ) {
                OutlinedTextField(
                    value = installmentsText,
                    onValueChange = { installmentsText = it },
                    label = { Text("تعداد اقساط") },
                    modifier = Modifier.weight(1f),
                    singleLine = true,
                )
                OutlinedTextField(
                    value = amountText,
                    onValueChange = { amountText = it },
                    label = { Text("مبلغ هر قسط (تومان)") },
                    modifier = Modifier.weight(1f),
                    singleLine = true,
                )
            }

            OutlinedTextField(
                value = notes,
                onValueChange = { notes = it },
                label = { Text("یادداشت (اختیاری)") },
                modifier = Modifier.fillMaxWidth().padding(bottom = Space.l),
                singleLine = true,
            )

            val count = installmentsText.toIntOrNull() ?: 0
            val installmentToman = amountText.replace(",", "").replace("،", "").toLongOrNull() ?: 0L
            val totalToman = totalText.replace(",", "").replace("،", "").toLongOrNull() ?: 0L

            Button(
                onClick = {
                    onSave(
                        provider.trim(),
                        totalToman * 10L,
                        count,
                        installmentToman * 10L,
                        tehranDay(System.currentTimeMillis()),
                        notes.trim(),
                    )
                },
                enabled = provider.isNotBlank() && count > 0 && installmentToman > 0L,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text("ثبت تعهد")
            }
        }
    }
}

/**
 * Picker bottom sheet to link an installment payment to a transaction.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LinkInstallmentPicker(
    entry: LedgerEntry,
    state: UiState,
    onLink: (paymentId: String) -> Unit,
    onDismiss: () -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val txnAmount = kotlin.math.abs(entry.txn.signedRial ?: entry.txn.amountRial ?: 0L)

    // Gather all unpaid payments across active obligations.
    val activeObligationIds = state.obligations.filter { !it.archived }.map { it.id }.toSet()
    val unpaidPayments = state.obligationPayments.entries
        .filter { it.key in activeObligationIds }
        .flatMap { it.value }
        .filter { it.paidDate == null }

    // Prioritize payments with matching amount.
    val candidates = unpaidPayments.filter { it.amountRial == txnAmount }
    val displayList = if (candidates.isNotEmpty()) candidates else unpaidPayments

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.surface,
    ) {
        Column(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = Space.l)
                .padding(bottom = Space.xl),
        ) {
            Text(
                "اتصال به قسط",
                fontSize = 20.sp,
                fontWeight = FontWeight.ExtraBold,
                modifier = Modifier.padding(bottom = Space.s),
            )
            Text(
                "مبلغ تراکنش: ${faNumber(txnAmount / 10.0)} تومان",
                fontSize = 13.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(bottom = Space.m),
            )

            if (displayList.isEmpty()) {
                Text(
                    "قسط بدون پرداختی با این مبلغ یافت نشد",
                    fontSize = 14.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(vertical = Space.l),
                )
            } else {
                if (candidates.isNotEmpty()) {
                    Text(
                        "تطبیق دقیق:",
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(bottom = Space.s),
                    )
                }
                displayList.forEach { payment ->
                    val obName = state.obligations.firstOrNull { it.id == payment.obligationId }
                        ?.provider?.ifBlank { "اقساط" } ?: "اقساط"
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .clickable { onLink(payment.id) }
                            .padding(vertical = Space.m),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(Modifier.weight(1f)) {
                            Text(
                                obName,
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Medium,
                            )
                            Text(
                                "قسط ${faNumber(payment.installmentNumber.toDouble())} — ${formatShamsiDate(payment.dueDate)}",
                                fontSize = 12.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        Text(
                            "${faNumber(payment.amountRial / 10.0)}",
                            fontSize = 14.sp,
                            fontFamily = ModamFigures,
                            fontWeight = FontWeight.Bold,
                        )
                        Spacer(Modifier.width(Space.s))
                        Text(
                            "انتخاب",
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.primary,
                            fontWeight = FontWeight.SemiBold,
                        )
                    }
                }
            }
        }
    }
}
