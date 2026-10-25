package com.doxigo.muchtoman.features.smspicker

import android.Manifest
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.LayoutDirection
import com.doxigo.muchtoman.MuchTomanTheme
import com.doxigo.muchtoman.Store
import com.doxigo.muchtoman.canReadSms
import com.doxigo.muchtoman.faNumber
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * The inbox picker as its own screen, so the privacy line, the permission ask and a batch of
 * hundreds of messages do not have to live inside the main timeline's navigation.
 *
 * It is deliberately self-contained: the theme, RTL, the read, the write and the undo window
 * all happen here. The only thing it needs from the host is a manifest line and one
 * `startActivity` — see [SmsPickerWiring] for the optional view-model takeover.
 */
class SmsPickerActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val store = Store(applicationContext)
        setContent {
            MuchTomanTheme(mode = store.themeMode) {
                // The whole app is RTL because every transaction is read in Persian; this
                // screen has no exception, and inheriting the phone's locale would flip the
                // numbers and the checkboxes for an English-locale phone.
                CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
                    SmsPickerRoute(onClose = { finish() })
                }
            }
        }
    }

    companion object {
        /**
         * The one way in. No deep link and no `exported` activity: her inbox is opened from a
         * tap inside the app, never from another app or a browser.
         */
        fun intent(context: Context): Intent = Intent(context, SmsPickerActivity::class.java)
    }
}

/**
 * Writes survive the screen on purpose. The picker closes once the batch is handed over, and
 * finishing, rotating or the process reclaiming the composition must not be able to leave the
 * write half-done — the same reason the widget has its own dispatcher.
 */
private val SMS_PICKER_WRITER = CoroutineScope(SupervisorJob() + Dispatchers.IO)

@Composable
private fun SmsPickerRoute(onClose: () -> Unit) {
    val context = LocalContext.current
    val app = context.applicationContext

    var state by remember { mutableStateOf(SmsPickerState()) }
    var loading by remember { mutableStateOf(true) }
    var committing by remember { mutableStateOf(false) }
    var permissionGranted by remember { mutableStateOf(canReadSms(context)) }
    var reloadToken by remember { mutableStateOf(0) }

    val askPermission = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { granted ->
        permissionGranted = granted
        if (granted) reloadToken++
    }

    LaunchedEffect(permissionGranted, reloadToken) {
        if (!permissionGranted) {
            loading = false
            return@LaunchedEffect
        }
        loading = true
        val inbox = loadPickerInbox(context)
        // reloaded() keeps the ticks that are still valid, so granting the permission or
        // confirming a sender does not throw away what she had already chosen.
        state = state.reloaded(inbox.messages, inbox.processedIds, inbox.extra)
        loading = false
    }

    SmsPickerScreen(
        state = state,
        onStateChange = { state = it },
        loading = loading,
        committing = committing,
        permissionGranted = permissionGranted,
        onRequestPermission = { askPermission.launch(Manifest.permission.READ_SMS) },
        onAddSender = { bank, sender ->
            val hook = SmsPickerWiring.onAddSender
            if (hook != null) {
                hook(bank, sender)
                reloadToken++
            } else {
                SMS_PICKER_WRITER.launch {
                    registerPickerSender(app, bank, sender)
                    withContext(Dispatchers.Main) { reloadToken++ }
                }
            }
        },
        onConfirm = { chosen ->
            val hook = SmsPickerWiring.onConfirm
            if (hook != null) {
                // The host owns the write and the refresh that follows it, so this screen's
                // only remaining job is to get out of the way.
                hook(chosen)
                onClose()
            } else {
                committing = true
                SMS_PICKER_WRITER.launch {
                    val report = runCatching { DurableSmsPickerIngest(app).commit(chosen) }
                    withContext(Dispatchers.Main) {
                        committing = false
                        Toast.makeText(app, reportMessage(report), Toast.LENGTH_LONG).show()
                        onClose()
                    }
                }
            }
        },
        onClose = onClose,
    )
}

/** What to say when the write returns — the count is the writer's, never the screen's guess. */
private fun reportMessage(result: Result<SmsCommitReport>): String = result.fold(
    onSuccess = { report ->
        if (report.storedMessages == 0) {
            "این پیامک‌ها از قبل در دفتر بودند"
        } else {
            "ثبت شد · ${faNumber(report.createdEntries.toLong())} تراکنش"
        }
    },
    onFailure = { "ثبت پیامک‌ها انجام نشد؛ دوباره تلاش کن" },
)
