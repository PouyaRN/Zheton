package com.doxigo.muchtoman

import android.content.Context
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.LayoutDirection
import androidx.glance.appwidget.updateAll
import kotlinx.coroutines.Dispatchers
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
    accountId: String = MANUAL_ACCOUNT_ID,
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
        accountId = accountId,
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
 * Quick add from outside the app: the quick-settings tile, the Quick Add widget and the
 * `zheton://quick_add` link. The same full-screen entry the app opens ([QuickAddScreen]).
 */
class QuickAddActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val store = Store(applicationContext)
        // A widget shortcut can preselect a category (e.g. بنزین) so the tap-to-log path
        // is one fewer decision.
        val presetCategoryId = intent?.getStringExtra(EXTRA_CATEGORY_ID)

        setContent {
            MuchTomanTheme(mode = store.themeMode) {
                CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
                    QuickAddStandalone(
                        presetCategoryId = presetCategoryId,
                        onClose = { finish() },
                        onSaved = { toman ->
                            Toast.makeText(
                                this@QuickAddActivity,
                                "${faCompact(toman.toDouble())} تومان ثبت شد",
                                Toast.LENGTH_SHORT,
                            ).show()
                            finish()
                        },
                    )
                }
            }
        }
    }

    companion object {
        /** Optional preselected category id passed by the Quick Add widget. */
        const val EXTRA_CATEGORY_ID = "com.pouyarn.zheton.extra.CATEGORY_ID"
    }
}
