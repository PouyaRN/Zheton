package com.doxigo.muchtoman.features.backup

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.Window
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.doxigo.muchtoman.DerivedDb
import com.doxigo.muchtoman.DurableDb
import com.doxigo.muchtoman.MuchTomanTheme
import com.doxigo.muchtoman.Radius
import com.doxigo.muchtoman.Space
import com.doxigo.muchtoman.Store
import com.doxigo.muchtoman.derive
import com.doxigo.muchtoman.extraLookup
import com.doxigo.muchtoman.faNumber
import com.doxigo.muchtoman.jalaliOf
import com.doxigo.muchtoman.tehranDay
import com.doxigo.muchtoman.updateTotalWidget
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

/**
 * Injection seams for the screen.
 *
 * The Activity is the only Android-bound part of the feature; everything it calls is an
 * interface it receives from here. That is what lets the Lead (or a future test harness)
 * substitute a fake source, target or transport without touching the UI, and it is why the
 * Activity itself contains no cryptography and no database calls.
 */
object BackupWiring {
    @Volatile
    var sourceFactory: (Context) -> BackupSource = { context ->
        DeviceBackupSource(Store(context), DurableDb.get(context))
    }

    @Volatile
    var targetFactory: (Context) -> BackupTarget = { context ->
        val store = Store(context)
        val durable = DurableDb.get(context)
        DeviceBackupTarget(store, durable) {
            // The derived ledger is not in the backup — it is rebuilt from the durable rows.
            derive(durable, DerivedDb.get(context), extraLookup(store.extraBankNumbers))
            updateTotalWidget(context)
        }
    }

    @Volatile
    var transportFactory: () -> VaultTransport = { HttpVaultTransport() }
}

/**
 * The data-safety screen: a passphrase-encrypted export, a file import, and the optional vault.
 *
 * It is a `ComponentActivity` of its own rather than a page inside `MainActivity`, for the same
 * reason `QuickAddActivity` is: backing up or restoring is a task with a beginning and an end,
 * and it should not be lost to a tab switch or a navigation state machine. It is deliberately
 * **not** exported and has no intent filters (see the wiring report), so only this app can open
 * it.
 *
 * After a successful restore it calls `setResult(RESULT_OK)`. The caller in `MainActivity` —
 * which is outside this feature's write scope — should `recreate()` on that result, because the
 * in-memory `UiState` was built before the restore and nothing here can reach it. Until then the
 * restored data is on disk but the screen behind this one is stale.
 */
class BackupActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val store = Store(applicationContext)
        setContent {
            MuchTomanTheme(mode = store.themeMode) {
                CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
                    BackupScreen(
                        controller = remember { BackupController() },
                        local = remember { BackupLocalState(applicationContext) },
                        source = remember { BackupWiring.sourceFactory(applicationContext) },
                        target = remember { BackupWiring.targetFactory(applicationContext) },
                        transportFactory = BackupWiring.transportFactory,
                        window = this@BackupActivity.window,
                        onRestored = { setResult(RESULT_OK) },
                        onClose = { finish() },
                    )
                }
            }
        }
    }
}

@Composable
private fun BackupScreen(
    controller: BackupController,
    local: BackupLocalState,
    source: BackupSource,
    target: BackupTarget,
    transportFactory: () -> VaultTransport,
    window: Window,
    onRestored: () -> Unit,
    onClose: () -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var includeMessages by remember { mutableStateOf(true) }
    var passphrase by remember { mutableStateOf("") }
    var confirm by remember { mutableStateOf("") }
    var reveal by remember { mutableStateOf(false) }

    var busy by remember { mutableStateOf(false) }
    var status by remember { mutableStateOf<String?>(null) }
    var error by remember { mutableStateOf<String?>(null) }

    var lastExportUri by remember { mutableStateOf<Uri?>(null) }
    var pending by remember { mutableStateOf<BackupInspect.Ready?>(null) }
    var lastReport by remember { mutableStateOf<BackupRestoreReport?>(null) }
    var restorePreferences by remember { mutableStateOf(true) }
    var restoreMessages by remember { mutableStateOf(true) }

    var vaultEnabled by remember { mutableStateOf(local.vaultEnabled) }
    var generatedCode by remember { mutableStateOf<String?>(null) }
    var showCode by remember { mutableStateOf(false) }
    var wroteCode by remember { mutableStateOf(false) }
    var recoveryInput by remember { mutableStateOf("") }
    var vaultBase by remember { mutableStateOf(local.vaultBaseUrl) }

    val passphraseOk = passphrase.length >= BackupFile.MIN_PASSPHRASE_CHARS
    val matches = passphrase.isNotEmpty() && passphrase == confirm

    // A recovery code on screen is a credential. Keep it out of the recents thumbnail and out
    // of screenshots for as long as it is visible, and clear the flag on the way out.
    DisposableEffect(showCode) {
        if (showCode) window.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
        else window.clearFlags(WindowManager.LayoutParams.FLAG_SECURE)
        onDispose { window.clearFlags(WindowManager.LayoutParams.FLAG_SECURE) }
    }

    // ──────────────────────────── export and share ────────────────────────────

    val createDocument = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/octet-stream")
    ) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        val password = passphrase
        busy = true
        error = null
        status = null
        scope.launch {
            try {
                val payload = withContext(Dispatchers.IO) { source.snapshot(includeMessages) }
                val bytes = withContext(Dispatchers.Default) {
                    controller.export(payload, password.toCharArray())
                }
                withContext(Dispatchers.IO) { writeDocument(context, uri, bytes) }
                local.lastExportAt = System.currentTimeMillis()
                lastExportUri = uri
                status = "پشتیبان ساخته شد: ${faNumber(bytes.size / 1024)} کیلوبایت. " +
                    "رمز عبور را جایی امن نگه دار؛ بدون آن این فایل باز نمی‌شود."
                passphrase = ""
                confirm = ""
            } catch (_: Exception) {
                error = "ساختن پشتیبان کامل نشد. اگر فایل نیمه‌کاره مانده، پاکش کن."
            } finally {
                busy = false
            }
        }
    }

    val openDocument = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        val password = passphrase
        busy = true
        error = null
        status = null
        scope.launch {
            try {
                val bytes = withContext(Dispatchers.IO) { readDocument(context, uri) }
                val inspected = withContext(Dispatchers.Default) {
                    controller.inspect(bytes, password.toCharArray())
                }
                when (inspected) {
                    is BackupInspect.Ready -> {
                        pending = inspected
                        lastReport = null
                    }

                    BackupInspect.WrongPassphrase ->
                        error = "رمز عبور با این فایل نمی‌خورد — یا فایل بعد از ساختن تغییر کرده است."

                    is BackupInspect.Invalid ->
                        error = "این فایل پشتیبان معتبر نیست یا با نسخهٔ دیگری از برنامه ساخته شده."
                }
            } catch (_: Exception) {
                error = "خواندن فایل ممکن نشد."
            } finally {
                busy = false
            }
        }
    }

    fun applyPending() {
        val ready = pending ?: return
        busy = true
        error = null
        status = null
        scope.launch {
            try {
                val report = controller.restore(
                    ready.payload,
                    target,
                    BackupRestoreOptions(
                        restorePreferences = restorePreferences,
                        restoreMessages = restoreMessages,
                    ),
                )
                lastReport = report
                local.lastImportAt = System.currentTimeMillis()
                pending = null
                onRestored()
                status = "بازگردانی انجام شد: ${faNumber(report.totalRows)} ردیف. " +
                    "برای دیدن دادهٔ تازه، این صفحه را ببند."
            } catch (_: Exception) {
                error = "بازگردانی کامل نشد. ممکن است بخشی از داده‌ها نوشته شده باشد؛ " +
                    "قبل از تکرار، برنامه را دوباره باز کن."
            } finally {
                busy = false
            }
        }
    }

    // ──────────────────────────── vault ────────────────────────────

    fun vaultClient(configuredBase: String): VaultClient? =
        runCatching { VaultClient(configuredBase, transportFactory()) }.getOrNull()

    fun uploadToVault() {
        val key = VaultRecoveryCode.parse(recoveryInput)
        if (key == null) {
            error = "کد بازیابی معتبر نیست. دوازده واژه را با فاصله بنویس."
            return
        }
        val client = vaultClient(vaultBase)
        if (client == null) {
            error = "آدرس گاوصندوق معتبر نیست؛ باید با https شروع شود."
            return
        }
        if (!passphraseOk) {
            error = "رمز عبور باید حداقل ۸ نویسه باشد."
            return
        }
        val password = passphrase
        busy = true
        error = null
        status = null
        scope.launch {
            try {
                val payload = withContext(Dispatchers.IO) { source.snapshot(includeMessages) }
                val envelope = withContext(Dispatchers.Default) {
                    controller.export(payload, password.toCharArray())
                }
                val result = withContext(Dispatchers.IO) { client.upload(key, envelope) }
                when (result) {
                    VaultUpload.Ok -> {
                        local.lastUploadAt = System.currentTimeMillis()
                        status = "پشتیبان در گاوصندوق گذاشته شد. برای بازگردانی هم کد بازیابی لازم است، " +
                            "هم رمز عبور."
                    }

                    is VaultUpload.Refused -> error = if (result.reason == "too_large") {
                        "حجم این پشتیبان از سقف گاوصندوق بیشتر است؛ از «ساختن فایل» استفاده کن."
                    } else {
                        "چیزی که ساخته شد، شکل پشتیبان را نداشت؛ برای امنیت فرستاده نشد."
                    }

                    is VaultUpload.Failed -> error = "گاوصندوق این بارگذاری را نپذیرفت (کد ${faNumber(result.status)})."
                }
            } catch (_: Exception) {
                error = "بارگذاری در گاوصندوق کامل نشد."
            } finally {
                busy = false
            }
        }
    }

    fun downloadFromVault() {
        val key = VaultRecoveryCode.parse(recoveryInput)
        if (key == null) {
            error = "کد بازیابی معتبر نیست. دوازده واژه را با فاصله بنویس."
            return
        }
        val client = vaultClient(vaultBase)
        if (client == null) {
            error = "آدرس گاوصندوق معتبر نیست؛ باید با https شروع شود."
            return
        }
        val password = passphrase
        busy = true
        error = null
        status = null
        scope.launch {
            try {
                when (val downloaded = withContext(Dispatchers.IO) { client.download(key) }) {
                    is VaultDownload.Found -> {
                        val inspected = withContext(Dispatchers.Default) {
                            controller.inspect(downloaded.envelope, password.toCharArray())
                        }
                        when (inspected) {
                            is BackupInspect.Ready -> {
                                pending = inspected
                                lastReport = null
                                local.lastVaultRestoreAt = System.currentTimeMillis()
                                status = "پشتیبان از گاوصندوق گرفته شد؛ پایین تأییدش کن."
                            }

                            BackupInspect.WrongPassphrase ->
                                error = "رمز عبور با این پشتیبان نمی‌خورد."

                            is BackupInspect.Invalid ->
                                error = "پشتیبانِ گاوصندوق معتبر نیست (شاید کد بازیابی عوض شده)."
                        }
                    }

                    VaultDownload.Missing ->
                        error = "با این کد بازیابی، پشتیبانی در گاوصندوق پیدا نشد."

                    is VaultDownload.Failed ->
                        error = "گرفتن پشتیبان از گاوصندوق ممکن نشد (کد ${faNumber(downloaded.status)})."
                }
            } catch (_: Exception) {
                error = "گرفتن پشتیبان از گاوصندوق ممکن نشد."
            } finally {
                busy = false
            }
        }
    }

    // ──────────────────────────── layout ────────────────────────────

    Surface(color = MaterialTheme.colorScheme.background, modifier = Modifier.fillMaxSize()) {
        Column(
            Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .imePadding()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = Space.xl)
                .padding(bottom = Space.xxl)
                .navigationBarsPadding(),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    "پشتیبان و گاوصندوق",
                    fontSize = 26.sp,
                    fontWeight = FontWeight.ExtraBold,
                    modifier = Modifier
                        .weight(1f)
                        .semantics { heading() },
                )
                TextButton(onClick = onClose) { Text("بستن", fontSize = 15.sp) }
            }
            Text(
                "نسخهٔ رمزگذاری‌شده‌ای از دفتر، برای وقتی که گوشی عوض می‌شود یا از دست می‌رود.",
                fontSize = 13.sp,
                lineHeight = 22.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            Spacer(Modifier.height(Space.l))
            ThreatModel()

            Spacer(Modifier.height(Space.l))
            PassphraseField(
                passphrase = passphrase,
                confirm = confirm,
                reveal = reveal,
                onPassphrase = { passphrase = it },
                onConfirm = { confirm = it },
                onReveal = { reveal = !reveal },
            )
            if (passphrase.isNotEmpty() && !passphraseOk) {
                Hint("رمز عبور باید حداقل ${faNumber(BackupFile.MIN_PASSPHRASE_CHARS)} نویسه باشد.")
            } else if (confirm.isNotEmpty() && !matches) {
                Hint("دو رمز عبور یکسان نیستند.")
            }

            Spacer(Modifier.height(Space.l))
            SectionHeading("۱. ساختن فایل")
            SettingRow(
                title = "متن خام پیامک‌های بانکی هم در پشتیبان باشد",
                subtitle = if (includeMessages) {
                    "بدون پیامک‌ها، دفتر از پیامک‌های همین گوشی بازسازی می‌شود؛ با آن‌ها، حتی اگر پیامک‌ها پاک شده باشند هم دفتر برمی‌گردد."
                } else {
                    "پیامک‌ها در فایل نمی‌آیند؛ فقط اعداد و تصمیم‌های ساخته‌شده."
                },
                checked = includeMessages,
                onChange = { includeMessages = it },
            )
            Spacer(Modifier.height(Space.s))
            Button(
                onClick = {
                    if (!passphraseOk || !matches) {
                        error = "اول رمز عبور را بنویس و تکرارش کن."
                        return@Button
                    }
                    error = null
                    status = null
                    createDocument.launch(BackupFile.suggestedFileName())
                },
                enabled = !busy,
                shape = RoundedCornerShape(Radius.pill),
                modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp),
            ) {
                Text("ساختن فایل پشتیبان", fontWeight = FontWeight.Bold)
            }
            if (lastExportUri != null) {
                Spacer(Modifier.height(Space.s))
                OutlinedButton(
                    onClick = {
                        val uri = lastExportUri ?: return@OutlinedButton
                        val send = Intent(Intent.ACTION_SEND).apply {
                            type = "application/octet-stream"
                            putExtra(Intent.EXTRA_STREAM, uri)
                            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                        }
                        runCatching {
                            context.startActivity(Intent.createChooser(send, "فرستادن پشتیبان"))
                        }.onFailure {
                            error = "برنامه‌ای برای فرستادن پیدا نشد. فایل را از همان مسیری که ذخیره کردی بردار."
                        }
                    },
                    shape = RoundedCornerShape(Radius.pill),
                    modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp),
                ) { Text("فرستادن فایل ساخته‌شده") }
            }
            LastTime("آخرین فایل ساخته‌شده", local.lastExportAt)

            Spacer(Modifier.height(Space.xl))
            SectionHeading("۲. بازگرداندن از فایل")
            Text(
                "رمز عبور همان رمزی است که موقع ساختن فایل گذاشتی. فایل با رمز اشتباه باز نمی‌شود.",
                fontSize = 13.sp,
                lineHeight = 22.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(Space.s))
            Button(
                onClick = {
                    if (!passphraseOk) {
                        error = "اول رمز عبور فایل را بنویس."
                        return@Button
                    }
                    error = null
                    status = null
                    openDocument.launch(arrayOf("*/*"))
                },
                enabled = !busy,
                shape = RoundedCornerShape(Radius.pill),
                modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp),
            ) {
                Text("انتخاب فایل پشتیبان", fontWeight = FontWeight.Bold)
            }
            LastTime("آخرین بازگردانی از فایل", local.lastImportAt)

            pending?.let { ready ->
                Spacer(Modifier.height(Space.m))
                RestoreConfirmCard(
                    summary = ready.payload.summary(ready.byteSize),
                    restorePreferences = restorePreferences,
                    restoreMessages = restoreMessages,
                    onPreferences = { restorePreferences = it },
                    onMessages = { restoreMessages = it },
                    busy = busy,
                    onConfirm = { applyPending() },
                    onCancel = { pending = null },
                )
            }

            lastReport?.let { report ->
                Spacer(Modifier.height(Space.m))
                Surface(
                    shape = RoundedCornerShape(Radius.card),
                    color = MaterialTheme.colorScheme.secondaryContainer,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Column(Modifier.padding(Space.m)) {
                        Text(
                            "بازگردانی انجام شد",
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSecondaryContainer,
                        )
                        Spacer(Modifier.height(Space.xs))
                        Text(
                            "${faNumber(report.totalRows)} ردیف نوشته شد؛ " +
                                "پیامک‌ها: ${faNumber(report.messages)}، " +
                                "تصمیم‌ها: ${faNumber(report.decisions)}، " +
                                "دستی‌ها: ${faNumber(report.manualTransactions)}.",
                            fontSize = 13.sp,
                            lineHeight = 22.sp,
                            color = MaterialTheme.colorScheme.onSecondaryContainer,
                        )
                        Spacer(Modifier.height(Space.s))
                        // The rest of the app read its state before this restore. Nothing in
                        // this feature can reach AppVm, so offer the one action that does: a
                        // fresh task, which makes MainActivity build its state from disk again.
                        // (The caller may also recreate on RESULT_OK once it is wired for it.)
                        OutlinedButton(
                            onClick = { restartApp(context) },
                            shape = RoundedCornerShape(Radius.pill),
                            modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp),
                        ) { Text("بازکردن دوبارهٔ برنامه برای دیدن دادهٔ تازه") }
                    }
                }
            }

            Spacer(Modifier.height(Space.xl))
            SectionHeading("۳. گاوصندوق اختیاری")
            Text(
                "فقط تودهٔ رمزشده روی سرور می‌رود؛ کلیدش از کد بازیابی ساخته می‌شود و کلید رمز عبور هم روی همین گوشی می‌ماند. " +
                    "هر دو لازم است: کد بازیابی توده را پیدا می‌کند، رمز عبور بازش می‌کند.",
                fontSize = 13.sp,
                lineHeight = 22.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            Spacer(Modifier.height(Space.m))
            if (!vaultEnabled) {
                Button(
                    onClick = {
                        generatedCode = VaultRecoveryCode.generate()
                        wroteCode = false
                        showCode = true
                    },
                    enabled = !busy,
                    shape = RoundedCornerShape(Radius.pill),
                    modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp),
                ) { Text("فعال‌کردن گاوصندوق و ساختن کد بازیابی", fontWeight = FontWeight.Bold) }
            } else {
                SettingRow(
                    title = "گاوصندوق روشن است",
                    subtitle = "خاموش‌کردنش فقط روی همین گوشی است؛ نسخهٔ روی سرور تا پایان عمرش می‌ماند.",
                    checked = true,
                    onChange = {
                        vaultEnabled = false
                        local.vaultEnabled = false
                        status = "گاوصندوق روی این گوشی خاموش شد."
                    },
                )
            }

            generatedCode?.let { code ->
                Spacer(Modifier.height(Space.m))
                RecoveryCodeCard(
                    code = code,
                    show = showCode,
                    wroteCode = wroteCode,
                    onShow = { showCode = !showCode },
                    onWrote = { wroteCode = it },
                    onCopy = {
                        copyToClipboard(context, code)
                        status = "کد بازیابی کپی شد. کلیپ‌بورد را پاک کن اگر لازم نیست."
                    },
                    onRegenerate = {
                        generatedCode = VaultRecoveryCode.generate()
                        recoveryInput = ""
                        wroteCode = false
                        showCode = true
                        status = "کد تازه ساخته شد. کد قبلی دیگر به پشتیبان‌های تازه دسترسی ندارد."
                    },
                )
                Spacer(Modifier.height(Space.s))
                Button(
                    onClick = {
                        if (!wroteCode) {
                            error = "اول تأیید کن که کد را نوشته‌ای."
                            return@Button
                        }
                        local.vaultEnabled = true
                        local.recoveryAcknowledged = true
                        vaultEnabled = true
                        recoveryInput = code
                        showCode = false
                        status = "گاوصندوق روشن شد. کد بازیابی را در جای امن نگه دار."
                    },
                    enabled = !busy && wroteCode,
                    shape = RoundedCornerShape(Radius.pill),
                    modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp),
                ) { Text("کد را نوشتم؛ گاوصندوق را روشن کن", fontWeight = FontWeight.Bold) }
            }

            if (vaultEnabled) {
                Spacer(Modifier.height(Space.m))
                OutlinedTextField(
                    value = recoveryInput,
                    onValueChange = { recoveryInput = it },
                    label = { Text("کد بازیابی (۱۲ واژه)") },
                    placeholder = { Text("مثلاً acorn actor adapt …") },
                    singleLine = false,
                    maxLines = 3,
                    modifier = Modifier
                        .fillMaxWidth()
                        .semantics { contentDescription = "کد بازیابی" },
                    shape = RoundedCornerShape(Radius.field),
                )
                if (recoveryInput.isNotBlank() && VaultRecoveryCode.parse(recoveryInput) == null) {
                    Hint("این ۱۲ واژه با هم نمی‌خوانند. یک واژه را اشتباه نوشته‌ای؟")
                }
                Spacer(Modifier.height(Space.s))
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(Space.s),
                ) {
                    Button(
                        onClick = { uploadToVault() },
                        enabled = !busy && passphraseOk,
                        shape = RoundedCornerShape(Radius.pill),
                        modifier = Modifier.weight(1f).heightIn(min = 52.dp),
                    ) { Text("گذاشتن در گاوصندوق", fontWeight = FontWeight.Bold) }
                    OutlinedButton(
                        onClick = { downloadFromVault() },
                        enabled = !busy,
                        shape = RoundedCornerShape(Radius.pill),
                        modifier = Modifier.weight(1f).heightIn(min = 52.dp),
                    ) { Text("گرفتن از گاوصندوق") }
                }
                LastTime("آخرین بارگذاری", local.lastUploadAt)
                LastTime("آخرین بازگردانی از گاوصندوق", local.lastVaultRestoreAt)

                Spacer(Modifier.height(Space.m))
                OutlinedTextField(
                    value = vaultBase,
                    onValueChange = { vaultBase = it },
                    label = { Text("آدرس گاوصندوق") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri, imeAction = ImeAction.Done),
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(Radius.field),
                )
                TextButton(
                    onClick = {
                        if (vaultClient(vaultBase) == null) {
                            error = "آدرس باید با https شروع شود (یا آدرس محلی برای توسعه)."
                        } else {
                            local.vaultBaseUrl = vaultBase
                            status = "آدرس گاوصندوق ذخیره شد."
                        }
                    },
                ) { Text("ذخیرهٔ آدرس") }
            }

            Spacer(Modifier.height(Space.m))
            if (busy) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                    Spacer(Modifier.width(Space.s))
                    Text("در حال کار…", fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            status?.let {
                Spacer(Modifier.height(Space.s))
                Text(it, fontSize = 13.sp, lineHeight = 22.sp, color = MaterialTheme.colorScheme.tertiary)
            }
            error?.let {
                Spacer(Modifier.height(Space.s))
                Text(it, fontSize = 13.sp, lineHeight = 22.sp, color = MaterialTheme.colorScheme.error)
            }

            Spacer(Modifier.height(Space.xxl))
            Text(
                "هیچ حسابی ساخته نمی‌شود، هیچ تلهمتری فرستاده نمی‌شود و هیچ نسخه‌ای از پشتیبان پیش ما نیست.",
                fontSize = 12.sp,
                lineHeight = 20.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

// ──────────────────────────── pieces ────────────────────────────

@Composable
private fun ThreatModel() {
    Surface(
        shape = RoundedCornerShape(Radius.card),
        color = MaterialTheme.colorScheme.surfaceVariant,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(Space.m)) {
            Text(
                "این پشتیبان چه چیزی را محافظت می‌کند — و چه چیزی را نه",
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Spacer(Modifier.height(Space.s))
            Bullet("رمزگذاری: AES-256-GCM با کلیدی که از رمز عبور ساخته می‌شود (PBKDF2-HMAC-SHA256، ۶۰۰٬۰۰۰ دور، نمک تصادفی ۱۶ بایتی). رمز عبور هیچ‌جا ذخیره نمی‌شود؛ اگر فراموشش کنی، هیچ‌کس — از جمله ما — نمی‌تواند فایل را باز کند.")
            Bullet("فایل یک تودهٔ دودویی با پسوند .mtbackup است. اسم فایل و اندازه‌اش دیده می‌شود؛ محتوایش نه.")
            Bullet("اگر «متن خام پیامک» روشن باشد، پیامک‌های بانکی هم داخل فایل‌اند — رمزشده، ولی حاضر. اگر نمی‌خواهی، خاموشش کن.")
            Bullet("گاوصندوق اختیاری است: سرور فقط تودهٔ رمزشده را نگه می‌دارد. اندازه، زمان و آدرس شبکه را می‌بیند؛ محتوا را نه. شناسهٔ گاوصندوق هشِ کلید بازیابی است، نه خودِ کد.")
            Bullet("کد بازیابی مثل کلید گاوصندوق است: هرکس آن را داشته باشد می‌تواند توده را بگیرد یا پاکش کند. بدون رمز عبور نمی‌تواند بازش کند — و بدون کد، رمز عبور هم کافی نیست.")
            Bullet("در پشتیبان نیست: قفل برنامه (تنظیم همین گوشی است)، کلید خانوادهٔ همگام‌سازی (بکاپ نباید مجوز خانه را با خود ببرد)، دسته‌بندی‌های بایگانی‌شده و ردیف‌های حذف‌شده.")
            Bullet("بازگردانی چیزی را پاک نمی‌کند: ردیف‌های دفتر با شناسه کنار دادهٔ موجود می‌نشینند. فهرست دارایی‌ها و حساب‌ها اما با محتوای فایل جایگزین می‌شوند.")
        }
    }
}

@Composable
private fun Bullet(text: String) {
    Row(Modifier.padding(bottom = Space.xs)) {
        Text("• ", fontSize = 13.sp, lineHeight = 22.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(text, fontSize = 13.sp, lineHeight = 22.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun PassphraseField(
    passphrase: String,
    confirm: String,
    reveal: Boolean,
    onPassphrase: (String) -> Unit,
    onConfirm: (String) -> Unit,
    onReveal: () -> Unit,
) {
    SectionHeading("رمز عبور پشتیبان")
    Text(
        "یک جملهٔ بلند که فقط خودت می‌دانی. این رمز هیچ‌جا ذخیره نمی‌شود و قابل بازیابی نیست.",
        fontSize = 13.sp,
        lineHeight = 22.sp,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    Spacer(Modifier.height(Space.s))
    OutlinedTextField(
        value = passphrase,
        onValueChange = onPassphrase,
        label = { Text("رمز عبور") },
        singleLine = true,
        visualTransformation = if (reveal) VisualTransformation.None else PasswordVisualTransformation(),
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password, imeAction = ImeAction.Next),
        trailingIcon = {
            TextButton(onClick = onReveal) {
                Text(if (reveal) "پنهان" else "نمایش", fontSize = 12.sp)
            }
        },
        modifier = Modifier
            .fillMaxWidth()
            .semantics { contentDescription = "رمز عبور پشتیبان" },
        shape = RoundedCornerShape(Radius.field),
    )
    Spacer(Modifier.height(Space.s))
    OutlinedTextField(
        value = confirm,
        onValueChange = onConfirm,
        label = { Text("تکرار رمز عبور") },
        singleLine = true,
        visualTransformation = if (reveal) VisualTransformation.None else PasswordVisualTransformation(),
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password, imeAction = ImeAction.Done),
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(Radius.field),
    )
}

@Composable
private fun RestoreConfirmCard(
    summary: BackupSummary,
    restorePreferences: Boolean,
    restoreMessages: Boolean,
    onPreferences: (Boolean) -> Unit,
    onMessages: (Boolean) -> Unit,
    busy: Boolean,
    onConfirm: () -> Unit,
    onCancel: () -> Unit,
) {
    Surface(
        shape = RoundedCornerShape(Radius.card),
        color = MaterialTheme.colorScheme.secondaryContainer,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(Space.m)) {
            Text(
                "این فایل باز شد؛ بازگردانی‌اش کنم؟",
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSecondaryContainer,
            )
            Spacer(Modifier.height(Space.xs))
            Text(
                "ساخته‌شده: ${formatWhen(summary.createdAt)}" +
                    (if (summary.appVersion.isBlank()) "" else "، نسخهٔ ${summary.appVersion}") + "\n" +
                    "پیامک: ${faNumber(summary.messages)} • دستی: ${faNumber(summary.manualTransactions)} • " +
                    "تصمیم: ${faNumber(summary.decisions)} • قانون: ${faNumber(summary.rules)} • " +
                    "دسته: ${faNumber(summary.categories)} • دارایی: ${faNumber(summary.holdings)} • " +
                    "حساب: ${faNumber(summary.bankAccounts)}",
                fontSize = 13.sp,
                lineHeight = 22.sp,
                color = MaterialTheme.colorScheme.onSecondaryContainer,
            )
            Spacer(Modifier.height(Space.s))
            SettingRow(
                title = "تنظیم‌ها هم برگردند",
                subtitle = "اسم، تم، دستمزد ساعتی، چرخهٔ ماه و چیدمان ویجت‌ها.",
                checked = restorePreferences,
                onChange = onPreferences,
            )
            if (summary.messages > 0) {
                SettingRow(
                    title = "پیامک‌ها هم برگردند",
                    subtitle = "با «نه»، پیامک‌های این گوشی دست‌نخورده می‌مانند و پیامکی از فایل اضافه نمی‌شود.",
                    checked = restoreMessages,
                    onChange = onMessages,
                )
            }
            Spacer(Modifier.height(Space.s))
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(Space.s),
            ) {
                Button(
                    onClick = onConfirm,
                    enabled = !busy,
                    shape = RoundedCornerShape(Radius.pill),
                    modifier = Modifier.weight(1f).heightIn(min = 48.dp),
                ) { Text("بازگردانی", fontWeight = FontWeight.Bold) }
                OutlinedButton(
                    onClick = onCancel,
                    enabled = !busy,
                    shape = RoundedCornerShape(Radius.pill),
                    modifier = Modifier.weight(1f).heightIn(min = 48.dp),
                ) { Text("انصراف") }
            }
        }
    }
}

@Composable
private fun RecoveryCodeCard(
    code: String,
    show: Boolean,
    wroteCode: Boolean,
    onShow: () -> Unit,
    onWrote: (Boolean) -> Unit,
    onCopy: () -> Unit,
    onRegenerate: () -> Unit,
) {
    Surface(
        shape = RoundedCornerShape(Radius.card),
        color = MaterialTheme.colorScheme.surfaceVariant,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(Space.m)) {
            Text(
                "کد بازیابی",
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Text(
                "۱۲ واژه، به همین ترتیب. روی کاغذ بنویس و در جای امن نگه دار. این کد دوباره نشان داده نمی‌شود.",
                fontSize = 12.sp,
                lineHeight = 20.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(Space.s))
            if (show) {
                Column(verticalArrangement = Arrangement.spacedBy(Space.xs)) {
                    code.split(' ').chunked(4).forEach { row ->
                        Row(
                            Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(Space.xs),
                        ) {
                            row.forEach { word ->
                                Box(
                                    Modifier
                                        .weight(1f)
                                        .clip(RoundedCornerShape(Radius.field))
                                        .background(MaterialTheme.colorScheme.surface)
                                        .padding(vertical = Space.s, horizontal = Space.xs),
                                    contentAlignment = Alignment.Center,
                                ) {
                                    Text(word, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                                }
                            }
                        }
                    }
                }
                Spacer(Modifier.height(Space.s))
                Row(horizontalArrangement = Arrangement.spacedBy(Space.s)) {
                    TextButton(onClick = onCopy) { Text("کپی", fontSize = 13.sp) }
                    TextButton(onClick = onShow) { Text("پنهان‌کردن", fontSize = 13.sp) }
                    TextButton(onClick = onRegenerate) { Text("کد تازه", fontSize = 13.sp) }
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(checked = wroteCode, onCheckedChange = onWrote)
                    Text("نوشتم و در جای امن گذاشتم.", fontSize = 13.sp)
                }
            } else {
                Button(
                    onClick = onShow,
                    shape = RoundedCornerShape(Radius.pill),
                    modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp),
                ) { Text("نمایش کد بازیابی") }
            }
        }
    }
}

@Composable
private fun SettingRow(
    title: String,
    subtitle: String,
    checked: Boolean,
    onChange: (Boolean) -> Unit,
) {
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(Radius.field))
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .padding(Space.m),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onSurface)
            Spacer(Modifier.height(2.dp))
            Text(subtitle, fontSize = 12.sp, lineHeight = 20.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Spacer(Modifier.width(Space.s))
        Switch(checked = checked, onCheckedChange = onChange)
    }
}

@Composable
private fun SectionHeading(title: String) {
    Text(
        title,
        fontSize = 18.sp,
        fontWeight = FontWeight.Bold,
        color = MaterialTheme.colorScheme.onBackground,
        modifier = Modifier
            .padding(bottom = Space.s)
            .semantics { heading() },
    )
}

@Composable
private fun Hint(text: String) {
    Text(
        text,
        fontSize = 12.sp,
        lineHeight = 20.sp,
        color = MaterialTheme.colorScheme.error,
        modifier = Modifier.padding(top = Space.xs),
    )
}

@Composable
private fun LastTime(label: String, at: Long) {
    Text(
        if (at <= 0L) "$label: —" else "$label: ${formatWhen(at)}",
        fontSize = 12.sp,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(top = Space.s),
    )
}

// ──────────────────────────── helpers ────────────────────────────

private fun formatWhen(epochMillis: Long): String {
    if (epochMillis <= 0L) return "—"
    val date = jalaliOf(tehranDay(epochMillis)).toString()
    val time = SimpleDateFormat("HH:mm", Locale.US)
        .apply { timeZone = TimeZone.getTimeZone("Asia/Tehran") }
        .format(Date(epochMillis))
    return faDigits("$date، $time")
}

private fun faDigits(value: String): String = buildString(value.length) {
    for (ch in value) {
        append(if (ch in '0'..'9') '۰' + (ch - '0') else ch)
    }
}

/**
 * Starts the app again in a fresh task, so `MainActivity` builds its `UiState` from the store
 * it has just had rewritten. The alternative — leaving a stale total on screen after a
 * successful restore — is the one outcome a data-safety feature must not allow.
 */
private fun restartApp(context: Context) {
    val intent = Intent(context, com.doxigo.muchtoman.MainActivity::class.java)
        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
    context.startActivity(intent)
}

private fun copyToClipboard(context: Context, text: String) {
    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager ?: return
    clipboard.setPrimaryClip(ClipData.newPlainText("کد بازیابی", text))
}

private fun writeDocument(context: Context, uri: Uri, bytes: ByteArray) {
    val stream = context.contentResolver.openOutputStream(uri)
        ?: throw IllegalStateException("no output stream")
    stream.use { it.write(bytes) }
}

/**
 * Reads a chosen document with the same ceiling the envelope parser uses. A file that is too
 * large is refused before it is fully in memory; a provider that keeps writing cannot make the
 * app allocate without bound.
 */
private fun readDocument(context: Context, uri: Uri): ByteArray {
    val stream = context.contentResolver.openInputStream(uri)
        ?: throw IllegalStateException("no input stream")
    return stream.use { input ->
        val out = ByteArrayOutputStream(64 * 1024)
        val buffer = ByteArray(64 * 1024)
        var total = 0
        while (true) {
            val read = input.read(buffer)
            if (read < 0) break
            total += read
            if (total > BackupFile.MAX_ENVELOPE_BYTES) {
                throw IllegalStateException("file is too large")
            }
            out.write(buffer, 0, read)
        }
        out.toByteArray()
    }
}
