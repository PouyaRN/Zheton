package com.doxigo.muchtoman

import android.content.Context
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.appwidget.updateAll
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Executes persistence and ledger derivation for a manual transaction.
 * Shared by [QuickAddActivity], [MainActivity], and widget quick action callbacks.
 */
suspend fun recordManualTransaction(
    context: Context,
    amountRial: Long,
    isExpense: Boolean = true,
    categoryId: String? = null,
    merchant: String = "",
    note: String = "",
    epochMillis: Long = System.currentTimeMillis(),
): String = withContext(Dispatchers.Default) {
    val app = context.applicationContext
    val durable = DurableDb.get(app)
    val derived = DerivedDb.get(app)
    val store = Store(app)
    val id = uuid7(epochMillis)
    val signedRial = if (isExpense) -kotlin.math.abs(amountRial) else kotlin.math.abs(amountRial)
    val day = tehranDay(epochMillis)
    val now = System.currentTimeMillis()

    val row = ManualTxn(
        id = id,
        at = epochMillis,
        day = day,
        amountRial = signedRial,
        accountId = "MANUAL",
        categoryId = categoryId,
        merchant = merchant.trim(),
        note = note.trim(),
        createdAt = now,
        updatedAt = now,
        deleted = false,
    )
    durable.manual().put(row)

    if (!categoryId.isNullOrBlank()) {
        durable.decisions().put(
            TxnDecision(
                id = uuid7(now),
                ref = manualRef(id),
                kind = DecisionKind.CATEGORY,
                value = categoryId,
                createdAt = now,
                updatedAt = now,
            )
        )
    }

    if (note.isNotBlank()) {
        durable.decisions().put(
            TxnDecision(
                id = uuid7(now + 1),
                ref = manualRef(id),
                kind = DecisionKind.NOTE,
                value = note.trim(),
                createdAt = now,
                updatedAt = now,
            )
        )
    }

    derive(durable, derived, extraLookup(store.extraBankNumbers))
    updateTotalWidget(app)
    runCatching {
        ChandTomanGlanceWidget().updateAll(app)
    }

    id
}

/**
 * Lightweight, 1-tap quick transaction logging activity.
 * Displayed as a floating modal dialog overlay without full app startup latency.
 */
class QuickAddActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val store = Store(applicationContext)

        setContent {
            MuchTomanTheme(mode = store.themeMode) {
                CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
                    Surface(
                        modifier = Modifier.fillMaxSize(),
                        color = Color.Black.copy(alpha = 0.55f),
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .clickable(
                                    interactionSource = remember { MutableInteractionSource() },
                                    indication = null,
                                    onClick = { finish() },
                                ),
                            contentAlignment = Alignment.Center,
                        ) {
                            QuickAddDialogContent(
                                onDismiss = { finish() },
                                onSaved = { formattedAmount ->
                                    Toast.makeText(
                                        this@QuickAddActivity,
                                        "تراکنش $formattedAmount با موفقیت ثبت شد",
                                        Toast.LENGTH_SHORT
                                    ).show()
                                    finish()
                                }
                            )
                        }
                    }
                }
            }
        }
    }
}

private val QUICK_CATEGORIES = listOf(
    "cat_groceries" to "خواربار",
    "cat_dining" to "رستوران و کافه",
    "cat_transport" to "حمل و نقل",
    "cat_bills" to "قبض‌ها",
    "cat_shopping" to "خرید",
    "cat_fuel" to "بنزین",
    CAT_UNCATEGORISED to "دسته‌بندی نشده",
)

private val QUICK_AMOUNTS_TOMAN = listOf(
    50_000L to "۵۰ هزار",
    100_000L to "۱۰۰ هزار",
    200_000L to "۲۰۰ هزار",
    500_000L to "۵۰۰ هزار",
    1_000_000L to "۱ میلیون",
)

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun QuickAddDialogContent(
    onDismiss: () -> Unit,
    onSaved: (String) -> Unit,
) {
    var isExpense by remember { mutableStateOf(true) }
    var amountText by remember { mutableStateOf("") }
    var selectedCategoryId by remember { mutableStateOf<String?>("cat_groceries") }
    var note by remember { mutableStateOf("") }
    var isSaving by remember { mutableStateOf(false) }

    val rawAmountToman = amountText.replace(",", "").replace(" ", "").toLongOrNull() ?: 0L
    val amountRial = rawAmountToman * 10L
    val scope = rememberCoroutineScope()
    val context = androidx.compose.ui.platform.LocalContext.current

    Card(
        modifier = Modifier
            .fillMaxWidth(0.92f)
            .padding(vertical = 24.dp)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = {}, // Absorb clicks inside dialog
            ),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface,
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 12.dp),
    ) {
        Column(
            modifier = Modifier
                .padding(20.dp)
                .verticalScroll(rememberScrollState()),
        ) {
            // Title & Type Selector
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = "ثبت سریع تراکنش",
                    fontSize = 18.sp,
                    fontWeight = FontWeight.ExtraBold,
                    fontFamily = ModamFigures,
                    color = MaterialTheme.colorScheme.onSurface,
                )

                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    FilterChip(
                        selected = isExpense,
                        onClick = { isExpense = true },
                        label = { Text("هزینه", fontSize = 12.sp) },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = Color(0xFFFF7043).copy(alpha = 0.2f),
                            selectedLabelColor = Color(0xFFFF7043),
                        ),
                    )
                    FilterChip(
                        selected = !isExpense,
                        onClick = { isExpense = false },
                        label = { Text("درآمد", fontSize = 12.sp) },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = Hero.mint.copy(alpha = 0.2f),
                            selectedLabelColor = Hero.mint,
                        ),
                    )
                }
            }

            Spacer(Modifier.height(14.dp))

            // Amount Input Field
            OutlinedTextField(
                value = amountText,
                onValueChange = { input ->
                    amountText = input.filter { it.isDigit() }
                },
                modifier = Modifier.fillMaxWidth(),
                label = { Text("مبلغ (تومان)") },
                placeholder = { Text("مثلاً ۵۰,۰۰۰") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                shape = RoundedCornerShape(14.dp),
                trailingIcon = {
                    Text(
                        "تومان",
                        modifier = Modifier.padding(end = 12.dp),
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                },
            )

            if (rawAmountToman > 0) {
                Text(
                    text = "معادل ${faNumber(rawAmountToman.toDouble())} تومان (${faCompact(rawAmountToman.toDouble())})",
                    fontSize = 12.sp,
                    color = Hero.gold,
                    fontWeight = FontWeight.SemiBold,
                    fontFamily = ModamFigures,
                    modifier = Modifier.padding(top = 4.dp, start = 4.dp),
                )
            }

            // Quick preset chips
            Spacer(Modifier.height(10.dp))
            FlowRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                QUICK_AMOUNTS_TOMAN.forEach { (amount, label) ->
                    Surface(
                        modifier = Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .clickable {
                                amountText = amount.toString()
                            },
                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                    ) {
                        Text(
                            text = label,
                            fontSize = 11.sp,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 5.dp),
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }

            // Category Chips
            Spacer(Modifier.height(14.dp))
            Text(
                "دسته‌بندی:",
                fontSize = 13.sp,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(6.dp))
            FlowRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                QUICK_CATEGORIES.forEach { (catId, catName) ->
                    val isSelected = selectedCategoryId == catId
                    FilterChip(
                        selected = isSelected,
                        onClick = { selectedCategoryId = catId },
                        label = { Text(catName, fontSize = 11.sp) },
                    )
                }
            }

            // Note / Merchant Input
            Spacer(Modifier.height(12.dp))
            OutlinedTextField(
                value = note,
                onValueChange = { note = it },
                modifier = Modifier.fillMaxWidth(),
                label = { Text("توضیح یا فروشگاه (اختیاری)") },
                singleLine = true,
                shape = RoundedCornerShape(14.dp),
            )

            // Action Buttons
            Spacer(Modifier.height(20.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                OutlinedButton(
                    onClick = onDismiss,
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(12.dp),
                ) {
                    Text("انصراف")
                }

                Button(
                    onClick = {
                        if (rawAmountToman <= 0L || isSaving) return@Button
                        isSaving = true
                        scope.launch {
                            try {
                                recordManualTransaction(
                                    context = context,
                                    amountRial = amountRial,
                                    isExpense = isExpense,
                                    categoryId = selectedCategoryId,
                                    merchant = note.trim(),
                                    note = note.trim(),
                                )
                                onSaved("${faCompact(rawAmountToman.toDouble())} تومان")
                            } catch (_: Exception) {
                                isSaving = false
                            }
                        }
                    },
                    modifier = Modifier.weight(1.5f),
                    enabled = rawAmountToman > 0L && !isSaving,
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Hero.gold,
                        contentColor = Color(0xFF1B1915),
                    ),
                ) {
                    Text(
                        if (isSaving) "در حال ثبت..." else "ثبت تراکنش",
                        fontWeight = FontWeight.Bold,
                    )
                }
            }
        }
    }
}
