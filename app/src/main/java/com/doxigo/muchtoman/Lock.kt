package com.doxigo.muchtoman

import android.Manifest
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricManager.Authenticators.BIOMETRIC_STRONG
import androidx.biometric.BiometricManager.Authenticators.BIOMETRIC_WEAK
import androidx.biometric.BiometricManager.Authenticators.DEVICE_CREDENTIAL
import androidx.biometric.BiometricPrompt
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.Spring
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.border
import androidx.compose.foundation.background
import androidx.compose.ui.geometry.Offset
import androidx.compose.foundation.clickable
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity
import com.doxigo.muchtoman.features.backup.BackupActivity
import com.doxigo.muchtoman.features.export.ReportExportActivity
import com.doxigo.muchtoman.features.reminders.ReminderSettingsActivity
import com.doxigo.muchtoman.features.smspicker.SmsPickerActivity

/**
 * Combining BIOMETRIC_STRONG with DEVICE_CREDENTIAL is not supported below API 30, so the
 * older path asks for WEAK. Nothing here guards a cryptographic key — it only hides a
 * balance from whoever else picks up the phone — so weak biometrics are an acceptable trade
 * for the feature working at all on her phone.
 */
private val authenticators: Int
    get() = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
        BIOMETRIC_STRONG or DEVICE_CREDENTIAL
    } else {
        BIOMETRIC_WEAK or DEVICE_CREDENTIAL
    }

/** False when there is no fingerprint AND no PIN/pattern — then the lock cannot be offered. */
fun canLock(context: Context): Boolean =
    BiometricManager.from(context).canAuthenticate(authenticators) == BiometricManager.BIOMETRIC_SUCCESS

/**
 * DEVICE_CREDENTIAL is always in the allowed set on purpose: a fingerprint that will not
 * read must never leave her locked out of her own balance. It also means no negative button
 * may be set — the framework rejects that combination.
 */
fun promptUnlock(activity: FragmentActivity, onSuccess: () -> Unit) {
    val prompt = BiometricPrompt(
        activity,
        ContextCompat.getMainExecutor(activity),
        object : BiometricPrompt.AuthenticationCallback() {
            override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                onSuccess()
            }
        },
    )
    prompt.authenticate(
        BiometricPrompt.PromptInfo.Builder()
            .setTitle("قفل اندیس // ANDIS OS")
            .setSubtitle("برای مشاهده دارایی‌ها، هویت خود را تأیید کنید")
            .setAllowedAuthenticators(authenticators)
            .build()
    )
}

/** Shown instead of the balance while locked. Deliberately carries no figures. */
@Composable
fun LockScreen(onUnlock: () -> Unit) {
    // The same deep field as the total it is standing in front of, so the lock reads as this
    // app closed rather than as a system screen that happens to be in the way.
    Box(
        Modifier
            .fillMaxSize()
            .background(Brush.verticalGradient(listOf(Hero.top, Hero.bottom))),
    ) {
        Column(
            Modifier
                .fillMaxSize()
                .systemBarsPadding()
                .padding(Space.xxl),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Box(
                Modifier
                    .size(96.dp)
                    .clip(CircleShape)
                    .background(Hero.well),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    Icons.Rounded.Lock,
                    contentDescription = null,
                    tint = Hero.gold,
                    modifier = Modifier.size(40.dp),
                )
            }
            Spacer(Modifier.height(Space.xxl))
            Text(
                "اندیس قفل شده",
                fontSize = 26.sp,
                fontWeight = FontWeight.ExtraBold,
                color = Hero.strong,
            )
            Spacer(Modifier.height(Space.m))
            Text(
                "برای دیدن دارایی‌هات، اثر انگشت یا رمز گوشی‌ات رو وارد کن.",
                fontSize = 15.sp,
                lineHeight = 25.sp,
                textAlign = TextAlign.Center,
                color = Hero.muted,
            )
            Spacer(Modifier.height(Space.huge))
            Button(
                onClick = onUnlock,
                shape = RoundedCornerShape(Radius.pill),
                colors = ButtonDefaults.buttonColors(
                    containerColor = Hero.gold,
                    contentColor = Hero.bottom,
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 60.dp),
            ) { Text("باز کردن", fontSize = 18.sp, fontWeight = FontWeight.Bold) }
        }
    }
}

/**
 * Its own page rather than a row in the asset list: a permanent switch sitting under her
 * holdings reads as one of them, and this is where anything else configurable will land.
 */
@Composable
fun SettingsScreen(
    name: String,
    themeMode: ThemeMode,
    lockEnabled: Boolean,
    widgetLock: Boolean,
    smsEnabled: Boolean,
    bankAccounts: List<BankAccount>,
    disabledBanks: Set<String>,
    activity: FragmentActivity,
    onNameChange: (String) -> Unit,
    onThemeChange: (ThemeMode) -> Unit,
    onSmsChange: (Boolean) -> Unit,
    onBankChange: (String, Boolean) -> Unit,
    onLockChange: (Boolean) -> Unit,
    onWidgetLockChange: (Boolean) -> Unit,
    hourlyWageToman: Long = 0L,
    onHourlyWageChange: (Long) -> Unit = {},
    cycleMode: CycleMode = CycleMode.JALALI_MONTH,
    cycleAnchorDay: Int = 1,
    monthlyBudgetToman: Long = 0L,
    onMonthlyBudgetChange: (Long) -> Unit = {},
    onCycleModeChange: (CycleMode) -> Unit = {},
    onCycleAnchorChange: (Int) -> Unit = {},
    onForceRescanSms: (Int) -> Unit = {},
    onCategoriesManage: () -> Unit,
    onBack: () -> Unit,
) {
    val context = LocalContext.current
    val available = remember { canLock(context) }

    // Switching it on is the moment to ask for the permission — not at launch, where she has
    // no idea what an app about money wants with her messages. Denied leaves it switched off.
    var granted by remember { mutableStateOf(canReadSms(context)) }
    val askSms = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { ok -> granted = ok; onSmsChange(ok) }
    // A restore replaces the store the running AppVm already read; recreating the activity is
    // the cheapest honest way to show the restored ledger instead of the stale one behind it.
    val backupLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult(),
    ) { result ->
        if (result.resultCode == android.app.Activity.RESULT_OK) {
            onBack()
            activity.recreate()
        }
    }
    // Held locally while typing so each keystroke is not a disk write; committed on leaving.
    var draft by remember { mutableStateOf(name) }

    Surface(color = MaterialTheme.colorScheme.background, modifier = Modifier.fillMaxSize()) {
        Column(
            Modifier
                .fillMaxSize()
                .systemBarsPadding()
                .imePadding()
                // A switch per bank makes this page taller than the screen on a phone that has
                // more than a couple of them.
                .verticalScroll(rememberScrollState())
                .padding(horizontal = Space.xl),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    "تنظیمات",
                    fontSize = 28.sp,
                    fontWeight = FontWeight.ExtraBold,
                    modifier = Modifier
                        .weight(1f)
                        .semantics { heading() },
                )
                TextButton(onClick = { onNameChange(draft); onBack() }) {
                    Text("ذخیره", fontSize = 17.sp)
                }
            }

            SectionLabel("اسمت")
            OutlinedTextField(
                value = draft,
                onValueChange = { draft = it.take(24) },
                singleLine = true,
                placeholder = { Text("مثلاً مریم") },
                shape = RoundedCornerShape(Radius.field),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                keyboardActions = KeyboardActions(onDone = { onNameChange(draft) }),
                modifier = Modifier
                    .fillMaxWidth()
                    .semantics { contentDescription = "اسمت" },
            )
            Text(
                "بالای برنامه باهاش بهت سلام می‌کنیم.",
                fontSize = 13.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = Space.s, start = Space.xs),
            )

            SectionLabel("ظاهر")
            ThemePicker(themeMode, onThemeChange)

            SectionLabel("پیامک‌های بانک")
            SettingCard(
                emoji = "✉️",
                title = "خواندن پیامک‌های بانک",
                subtitle = "با این گزینه، موجودی حساب‌ها از روی پیامک بانک به‌روز می‌شه.",
                checked = smsEnabled && granted,
                onChange = { on ->
                    if (on && !granted) askSms.launch(Manifest.permission.READ_SMS)
                    else onSmsChange(on)
                },
            )
            // Naming the banks is not decoration: only messages from their own numbers are read
            // now, so a bank missing from this line is the one and only reason its balance
            // never appears — and without the line there is nothing to tell her that.
            val watched = Bank.entries.filter { it.numbers.isNotEmpty() }.joinToString("، ") { it.fa }
            Text(
                if (smsEnabled && granted) {
                    "پیامک‌های $watched فقط روی همین گوشی خونده می‌شن و جایی فرستاده نمی‌شن."
                } else {
                    "فقط پیامک‌های $watched، اون هم روی همین گوشی، خونده می‌شن."
                },
                fontSize = 13.sp,
                lineHeight = 20.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = Space.s, start = Space.xs, end = Space.xs),
            )
            // The one way this can be set up wrong and still look like it is working. A bank
            // whose alerts arrive as its own app's notifications sends no message at all, so
            // there is nothing to read and nothing to report — the balance simply stops where
            // the last real پیامک left it, which reads as the app being wrong rather than as a
            // setting being off. First clause at full strength: it is the sentence that has to
            // survive being skimmed.
            Text(
                buildAnnotatedString {
                    withStyle(SpanStyle(color = MaterialTheme.colorScheme.onSurface)) {
                        append("فقط پیامک‌های بانکی خونده می‌شن، نه اعلان‌های اپ بانک. ")
                    }
                    append(
                        "بعضی بانک‌ها مثل بلو بانک به جای پیامک اعلان می‌فرستن. این‌جوری " +
                            "چیزی برای خوندن نیست و موجودی به‌روز نمی‌شه. از تنظیمات اپ بانک، " +
                            "پیامک تراکنش رو روشن کن.",
                    )
                },
                fontSize = 13.sp,
                lineHeight = 20.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = Space.m, start = Space.xs, end = Space.xs),
            )

            if (smsEnabled && granted) {
                // One switch per bank actually seen, not per bank we know how to read: a list
                // of fifteen banks she has no account at is a list nobody reads.
                val banks = bankAccounts.map { it.bank }.distinct()
                if (banks.isEmpty()) {
                    Text(
                        "هنوز پیامک بانکی نرسیده. اولین پیامک که بیاد، بانک اینجا نشون داده می‌شه.",
                        fontSize = 13.sp,
                        lineHeight = 20.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = Space.l, start = Space.xs, end = Space.xs),
                    )
                } else {
                    // One band, as on the asset list: these are N of the same thing, and N
                    // separate cards made each bank look like its own section.
                    Spacer(Modifier.height(Space.s))
                    banks.forEachIndexed { i, bank ->
                        val accounts = bankAccounts.filter { it.bank == bank }
                        SettingCard(
                            // VS16 pinned: bare U+1F3DB defaults to the monochrome text
                            // glyph on spec-following renderers, unlike the ✉️ card above.
                            emoji = "🏛️",
                            title = accounts.first().bankFa,
                            subtitle = "${faCompact(accounts.sumOf { it.balance })} تومان" +
                                if (accounts.any { !it.trusted }) "  •  نیاز به بررسی" else "",
                            checked = bank !in disabledBanks,
                            onChange = { on -> onBankChange(bank, on) },
                            shape = bandShape(i, banks.size),
                            divided = i < banks.size - 1,
                            // The bank's own mark, as in the accounts sheet. A row of identical
                            // 🏛️ told her nothing the name beside it did not already say.
                            badge = { BankLogo(bank, size = 44.dp) },
                        )
                    }
                }

                Spacer(Modifier.height(Space.m))
                var rescanning by remember { mutableStateOf(false) }
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(Radius.card))
                        .clickable(enabled = !rescanning) {
                            rescanning = true
                            onForceRescanSms(90)
                            android.widget.Toast.makeText(
                                context,
                                "بازخوانی پیامک‌های ۳ ماه اخیر آغاز شد",
                                android.widget.Toast.LENGTH_SHORT,
                            ).show()
                        },
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(Space.m),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Box(
                            modifier = Modifier
                                .size(40.dp)
                                .clip(CircleShape)
                                .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)),
                            contentAlignment = Alignment.Center,
                        ) {
                            Text("🔄", fontSize = 18.sp)
                        }
                        Spacer(Modifier.width(Space.m))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                "بازخوانی و اسکن مجدد پیامک‌ها",
                                fontSize = 15.sp,
                                fontWeight = FontWeight.Bold,
                            )
                            Text(
                                "اسکن مجدد پیامک‌های بانکی برای شناسایی پیام‌های جا افتاده",
                                fontSize = 12.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        if (rescanning) {
                            CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                        } else {
                            Text(
                                "اسکن",
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary,
                            )
                        }
                    }
                }
            }

            SectionLabel("امنیت")
            // One band of two rows, like the banks: the app's lock and the widget's mask are
            // siblings, not the same switch — she may want the app guarded but the number
            // glanceable on the home screen, or the other way round.
            SettingCard(
                emoji = "🔒",
                title = "قفل برنامه",
                // Always what the setting does. When it is unavailable, the helper text below
                // the band already carries the fix — and says where, which this line never did.
                subtitle = "با اثر انگشت یا رمز گوشی باز می‌شه",
                checked = lockEnabled,
                onChange = onLockChange,
                enabled = available,
                shape = bandShape(0, 2),
                divided = true,
            )
            SettingCard(
                emoji = "🙈",
                title = "قفل ویجت",
                subtitle = "مبلغ صفحهٔ اصلی رو با ٭٭٭ پنهان می‌کنه",
                checked = widgetLock,
                onChange = onWidgetLockChange,
                shape = bandShape(1, 2),
            )

            if (!available) {
                Text(
                    "برای فعال کردن قفل، اول توی تنظیمات گوشی رمز یا اثر انگشت بذار.",
                    fontSize = 13.sp,
                    lineHeight = 20.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = Space.m, start = Space.xs, end = Space.xs),
                )
            }

            SectionLabel("دسته‌بندی‌ها")
            Row(
                Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(Radius.group))
                    .clickable { onCategoriesManage() }
                    .padding(horizontal = Space.l, vertical = Space.l),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(
                    Modifier
                        .size(44.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.surfaceContainerHighest),
                    contentAlignment = Alignment.Center,
                ) { Text("🏷️", fontSize = 20.sp) }
                Column(Modifier.padding(horizontal = Space.m).weight(1f)) {
                    Text(
                        "مدیریت دسته‌بندی‌ها",
                        fontSize = 17.sp,
                        fontWeight = FontWeight.Bold,
                    )
                    Text(
                        "ساخت، ویرایش و حذف دسته‌بندی‌ها",
                        fontSize = 13.sp,
                        lineHeight = 20.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 2.dp),
                    )
                }
                Icon(
                    Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(20.dp),
                )
            }

            SectionLabel("ارزش زمانی پول (انرژی حیاتی)")
            Text(
                "با تعیین دستمزد ساعتی، مبالغ خرید به «ساعت کار مورد نیاز» تبدیل می‌شوند تا دید بهتری نسبت به ارزش واقعی خریدها داشته باشید.",
                fontSize = 13.sp,
                lineHeight = 20.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(bottom = Space.s, start = Space.xs, end = Space.xs),
            )
            var wageDraft by remember(hourlyWageToman) {
                mutableStateOf(if (hourlyWageToman > 0) hourlyWageToman.toString() else "")
            }
            OutlinedTextField(
                value = wageDraft,
                onValueChange = { input ->
                    val cleanDigits = toAsciiDigits(input).filter { it.isDigit() }
                    wageDraft = cleanDigits
                    val num = cleanDigits.toLongOrNull() ?: 0L
                    onHourlyWageChange(num)
                },
                singleLine = true,
                placeholder = { Text("مثلاً ۱۵۰,۰۰۰ تومان در ساعت") },
                suffix = { Text("تومان / ساعت", fontSize = 13.sp) },
                shape = RoundedCornerShape(Radius.field),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number, imeAction = ImeAction.Done),
                modifier = Modifier
                    .fillMaxWidth()
                    .semantics { contentDescription = "دستمزد ساعتی" },
            )
            if (wageDraft.isNotBlank()) {
                val parsedWage = wageDraft.toLongOrNull() ?: 0L
                if (parsedWage > 0L) {
                    val monthlyApprox = parsedWage * 176
                    Text(
                        "معادل تقریباً ${faCompact(monthlyApprox)} تومان درآمد ماهانه (بر مبنای ۴۴ ساعت کار هفتگی)",
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(top = Space.xs, start = Space.xs),
                    )
                }
            }

            SectionLabel("چرخهٔ مالی ماه")
            Text(
                "اگر حقوقت آخر ماه می‌رسه، ماه تقویمی از روز بعد از حقوق شروع می‌شه و همه‌جا کسری نشون می‌ده. روز واریز حقوق رو انتخاب کن تا درآمد و خرج از همون روز حساب بشه.",
                fontSize = 13.sp,
                lineHeight = 20.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(bottom = Space.s, start = Space.xs, end = Space.xs),
            )
            CyclePicker(
                mode = cycleMode,
                anchorDay = cycleAnchorDay,
                onModeChange = onCycleModeChange,
                onAnchorChange = onCycleAnchorChange,
            )

            SectionLabel("بودجهٔ ماهانه")
            Text(
                "چقدر می‌خوای این ماه خرج کنی؟ از روی این، سهم هر روزت از بودجه حساب می‌شه. خالی بذاری، همون خرج امن از موجودی نشون داده می‌شه.",
                fontSize = 13.sp,
                lineHeight = 20.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(bottom = Space.s, start = Space.xs, end = Space.xs),
            )
            var budgetDraft by remember(monthlyBudgetToman) {
                mutableStateOf(if (monthlyBudgetToman > 0) monthlyBudgetToman.toString() else "")
            }
            OutlinedTextField(
                value = budgetDraft,
                onValueChange = { input ->
                    val cleanDigits = toAsciiDigits(input).filter { it.isDigit() }.take(15)
                    budgetDraft = cleanDigits
                    onMonthlyBudgetChange(cleanDigits.toLongOrNull() ?: 0L)
                },
                singleLine = true,
                placeholder = { Text("مثلاً ۲۰,۰۰۰,۰۰۰") },
                suffix = { Text("تومان در ماه", fontSize = 13.sp) },
                shape = RoundedCornerShape(Radius.field),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number, imeAction = ImeAction.Done),
                modifier = Modifier
                    .fillMaxWidth()
                    .semantics { contentDescription = "بودجهٔ ماهانه" },
            )
            budgetDraft.toLongOrNull()?.takeIf { it > 0L }?.let { parsed ->
                Text(
                    "${faCompact(parsed)} تومان",
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(top = Space.xs, start = Space.xs),
                )
            }

            SectionLabel("داده و ابزارها")
            Text(
                "همهٔ این‌ها روی همین گوشی کار می‌کنند؛ نه حسابی لازم دارند نه اینترنتی.",
                fontSize = 13.sp,
                lineHeight = 20.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(bottom = Space.s, start = Space.xs, end = Space.xs),
            )
            ToolRow(
                emoji = "💾",
                title = "پشتیبان‌گیری و بازیابی",
                subtitle = "یک فایل رمزدار از داده‌هایت بگیر، یا از فایل برگردان",
                onClick = { backupLauncher.launch(Intent(context, BackupActivity::class.java)) },
                shape = bandShape(0, 4),
                divided = true,
            )
            ToolRow(
                emoji = "🔔",
                title = "یادآورها و سررسید",
                subtitle = "قسط و چک نزدیک سررسید را روی گوشی یادآوری کن",
                onClick = { context.startActivity(Intent(context, ReminderSettingsActivity::class.java)) },
                shape = bandShape(1, 4),
                divided = true,
            )
            ToolRow(
                emoji = "📄",
                title = "گزارش PDF و Excel",
                subtitle = "گزارش ماهانه یا خانوادگی برای خودت یا حسابدار",
                onClick = { ReportExportActivity.launch(context) },
                shape = bandShape(2, 4),
                divided = true,
            )
            ToolRow(
                emoji = "📥",
                title = "پیامک‌های خوانده‌نشده",
                subtitle = "همهٔ پیامک‌های گوشی را ببین و خودت انتخاب کن کدام تراکنش شود",
                onClick = { context.startActivity(SmsPickerActivity.intent(context)) },
                shape = bandShape(3, 4),
            )

            // The answer to "which version do you have?" over the phone, without her having
            // to find the system app-info page. A fixed gap, not weight(1f): inside a scrolling
            // column a weighted spacer has nothing to push against.
            //
            // The build number and the build type are both here because the name alone does not
            // identify an install: every locally built app carries the placeholder 1.0, so a
            // sandbox build and a real 1.0 release read identically — which is exactly the
            // question this line exists to answer.
            Spacer(Modifier.height(Space.xxl))
            Text(
                buildString {
                    append("اندیس • نسخهٔ ${faVersion(BuildConfig.VERSION_NAME)}")
                    append(" • ساخت ${faVersion(BuildConfig.VERSION_CODE.toString())}")
                    if (BuildConfig.BUILD_TYPE != "release") {
                        append(" • ${bidi(BuildConfig.BUILD_TYPE)}")
                    }
                },
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 100.dp),
            )
        }
    }
}

/** "1.0" -> "۱٫۰" so the one latin run on an otherwise Persian page disappears. */
private fun faVersion(v: String): String =
    v.map { c -> if (c in '0'..'9') '۰' + (c - '0') else if (c == '.') '٫' else c }
        .joinToString("")

/**
 * One tappable row in a settings band — the same geometry as a bank row, without a switch.
 * A settings row that opens a screen is not a toggle, and drawing it as one taught her to tap
 * the wrong half of it.
 */
@Composable
private fun ToolRow(
    emoji: String,
    title: String,
    subtitle: String,
    onClick: () -> Unit,
    shape: androidx.compose.ui.graphics.Shape,
    divided: Boolean = false,
) {
    Column(
        Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(MaterialTheme.colorScheme.surfaceContainer)
            .clickable { onClick() },
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = Space.l, vertical = Space.l),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                Modifier
                    .size(44.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.surfaceContainerHighest),
                contentAlignment = Alignment.Center,
            ) { Text(emoji, fontSize = 20.sp) }
            Column(Modifier.padding(horizontal = Space.m).weight(1f)) {
                Text(title, fontSize = 17.sp, fontWeight = FontWeight.Bold)
                Text(
                    subtitle,
                    fontSize = 13.sp,
                    lineHeight = 20.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 2.dp),
                )
            }
            Icon(
                Icons.AutoMirrored.Filled.ArrowBack,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(20.dp),
            )
        }
        if (divided) {
            HorizontalDivider(
                modifier = Modifier.padding(start = 72.dp),
                thickness = 0.5.dp,
                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f),
            )
        }
    }
}

@Composable
private fun SectionLabel(text: String) {
    Text(
        text,
        fontSize = 15.sp,
        fontWeight = FontWeight.ExtraBold,
        color = MaterialTheme.colorScheme.onBackground,
        // more air above a heading than below it
        modifier = Modifier
            .padding(top = Space.xxl, bottom = Space.m, start = Space.xs)
            // Real headings, so TalkBack can jump between sections of this long page.
            .semantics { heading() },
    )
}

private enum class ThemeFilterCategory(val fa: String) {
    ALL("همه"),
    LIGHT("روشن ☀️"),
    DARK("تیره 🌙"),
}

/** Interactive Theme Studio with live preview card, palette swatches, and category filtering. */
@Composable
private fun ThemePicker(current: ThemeMode, onChange: (ThemeMode) -> Unit) {
    val fintechHaptics = com.doxigo.muchtoman.ui.haptics.LocalFintechHaptics.current
    var selectedCategory by remember { mutableStateOf(ThemeFilterCategory.ALL) }

    val allThemes = ThemeMode.entries
    val filteredThemes = remember(selectedCategory) {
        when (selectedCategory) {
            ThemeFilterCategory.ALL -> allThemes
            ThemeFilterCategory.LIGHT -> allThemes.filter { !it.isDark && it != ThemeMode.SYSTEM }
            ThemeFilterCategory.DARK -> allThemes.filter { it.isDark }
        }
    }

    val activeSpec = remember(current) { getThemeVisualSpec(current) }

    Column(verticalArrangement = Arrangement.spacedBy(Space.m)) {
        // 1. Live Interactive Theme Preview Card
        ThemeLivePreviewCard(spec = activeSpec)

        // 2. Category Filter Switcher
        SegmentedChoice(
            options = ThemeFilterCategory.entries,
            selected = selectedCategory,
            label = { it.fa },
            onSelect = {
                fintechHaptics.click()
                selectedCategory = it
            },
            fontSize = 12.sp,
        )

        // 3. Grid of Visual Theme Swatch Cards
        Column(verticalArrangement = Arrangement.spacedBy(Space.s)) {
            filteredThemes.chunked(2).forEach { row ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(Space.s),
                ) {
                    row.forEach { theme ->
                        Box(modifier = Modifier.weight(1f)) {
                            ThemeSwatchCard(
                                theme = theme,
                                isSelected = theme == current,
                                onSelect = {
                                    fintechHaptics.click()
                                    onChange(theme)
                                },
                            )
                        }
                    }
                    if (row.size == 1) {
                        Spacer(modifier = Modifier.weight(1f))
                    }
                }
            }
        }
    }
}

@Composable
private fun ThemeLivePreviewCard(spec: ThemeVisualSpec) {
    val animHeroTop by animateColorAsState(spec.heroTopPreview, spring(stiffness = Spring.StiffnessMediumLow), label = "prev_top")
    val animHeroBottom by animateColorAsState(spec.heroBottomPreview, spring(stiffness = Spring.StiffnessMediumLow), label = "prev_bottom")
    val animBg by animateColorAsState(spec.bgPreview, spring(stiffness = Spring.StiffnessMediumLow), label = "prev_bg")
    val animSurface by animateColorAsState(spec.surfacePreview, spring(stiffness = Spring.StiffnessMediumLow), label = "prev_surface")
    val animAccent by animateColorAsState(spec.accentPreview, spring(stiffness = Spring.StiffnessMediumLow), label = "prev_accent")

    val textColor = if (spec.isDark) Color(0xFFF8FAFC) else Color(0xFF101A18)
    val mutedColor = if (spec.isDark) Color(0xFF94A3B8) else Color(0xFF5C6F68)

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(Radius.sheet),
        colors = CardDefaults.cardColors(containerColor = animBg),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.35f)),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
    ) {
        Column {
            // Mini Hero Header
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(bottomStart = Radius.hero, bottomEnd = Radius.hero))
                    .background(Brush.verticalGradient(listOf(animHeroTop, animHeroBottom)))
                    .padding(horizontal = Space.m, vertical = Space.m),
            ) {
                // Radial specular highlight
                Canvas(Modifier.matchParentSize()) {
                    drawRect(
                        Brush.radialGradient(
                            colors = listOf(animAccent.copy(alpha = 0.22f), Color.Transparent),
                            center = Offset(size.width * 0.9f, 0f),
                            radius = size.width * 0.8f,
                        )
                    )
                }

                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            text = "پیش‌نمایش زنده • اندیس",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = mutedColor,
                        )
                        Surface(
                            shape = RoundedCornerShape(Radius.pill),
                            color = animAccent.copy(alpha = 0.16f),
                            border = BorderStroke(1.dp, animAccent.copy(alpha = 0.35f)),
                        ) {
                            Text(
                                text = if (spec.mode == ThemeMode.SYSTEM) "⚙️ خودکار" else if (spec.isDark) "🌙 تیره" else "☀️ روشن",
                                fontSize = 10.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = animAccent,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
                            )
                        }
                    }

                    Spacer(Modifier.height(Space.xs))

                    Text(
                        text = "موجودی کل در دسترس",
                        fontSize = 11.sp,
                        color = mutedColor,
                    )

                    Row(
                        verticalAlignment = Alignment.Bottom,
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        Text(
                            text = "۴۸,۲۵۰,۰۰۰",
                            fontSize = 22.sp,
                            fontWeight = FontWeight.Black,
                            fontFamily = ModamFigures,
                            color = animAccent,
                        )
                        Text(
                            text = "تومان",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = animAccent.copy(alpha = 0.8f),
                            modifier = Modifier.padding(bottom = 3.dp),
                        )
                    }
                }
            }

            // Mini App Cockpit / Cards row
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = Space.m, vertical = Space.s),
                horizontalArrangement = Arrangement.spacedBy(Space.s),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                // Mini Bank Card Chip
                Surface(
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(Radius.card),
                    color = animSurface,
                    border = BorderStroke(1.dp, animAccent.copy(alpha = 0.3f)),
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = Space.s, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        Box(
                            modifier = Modifier
                                .size(10.dp)
                                .clip(CircleShape)
                                .background(animAccent),
                        )
                        Text(
                            text = "کارت اصلی ••۳۴",
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Medium,
                            color = textColor,
                            maxLines = 1,
                        )
                    }
                }

                // Mini Income badge
                Surface(
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(Radius.card),
                    color = animSurface,
                    border = BorderStroke(1.dp, Color(0xFF10B981).copy(alpha = 0.3f)),
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = Space.s, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        Box(
                            modifier = Modifier
                                .size(10.dp)
                                .clip(CircleShape)
                                .background(Color(0xFF10B981)),
                        )
                        Text(
                            text = "+۳,۵۰۰,۰۰۰",
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            fontFamily = ModamFigures,
                            color = Color(0xFF10B981),
                            maxLines = 1,
                        )
                    }
                }
            }

            // Footer active theme name strip
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(animSurface.copy(alpha = 0.6f))
                    .padding(horizontal = Space.m, vertical = 6.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = "تم انتخابی: ${spec.mode.fa}",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    color = textColor,
                )
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    Box(
                        modifier = Modifier
                            .size(6.dp)
                            .clip(CircleShape)
                            .background(animAccent),
                    )
                    Text(
                        text = "فعال",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = animAccent,
                    )
                }
            }
        }
    }
}

@Composable
private fun ThemeSwatchCard(
    theme: ThemeMode,
    isSelected: Boolean,
    onSelect: () -> Unit,
) {
    val spec = remember(theme) { getThemeVisualSpec(theme) }
    val cardBg = spec.surfacePreview
    // Derive the label colour from the card that is actually drawn: `spec.isDark` disagrees with
    // the SYSTEM swatch and printed a near-black «خودکار» on a near-black card.
    val cardIsLight = (cardBg.red * 0.299f + cardBg.green * 0.587f + cardBg.blue * 0.114f) > 0.5f
    val textColor = if (cardIsLight) Color(0xFF101A18) else Color(0xFFF8FAFC)

    val activeBorderColor = spec.accentPreview
    val inactiveBorderColor = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.45f)

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(Radius.group))
            .clickable(onClick = onSelect),
        shape = RoundedCornerShape(Radius.group),
        color = cardBg,
        border = BorderStroke(
            width = if (isSelected) 2.dp else 1.dp,
            color = if (isSelected) activeBorderColor else inactiveBorderColor,
        ),
        shadowElevation = if (isSelected) 3.dp else 0.dp,
    ) {
        Column(
            modifier = Modifier.padding(horizontal = Space.m, vertical = Space.s),
            verticalArrangement = Arrangement.spacedBy(Space.xs),
        ) {
            // Top row: Swatches & Selection/Mode tag
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                // 3 color dots: Background, Surface/Hero, Accent
                Row(horizontalArrangement = Arrangement.spacedBy((-3).dp)) {
                    Box(
                        modifier = Modifier
                            .size(16.dp)
                            .clip(CircleShape)
                            .background(spec.bgPreview)
                            .border(1.dp, Color.White.copy(alpha = 0.4f), CircleShape),
                    )
                    Box(
                        modifier = Modifier
                            .size(16.dp)
                            .clip(CircleShape)
                            .background(spec.heroTopPreview)
                            .border(1.dp, Color.White.copy(alpha = 0.4f), CircleShape),
                    )
                    Box(
                        modifier = Modifier
                            .size(16.dp)
                            .clip(CircleShape)
                            .background(spec.accentPreview)
                            .border(1.dp, Color.White.copy(alpha = 0.6f), CircleShape),
                    )
                }

                if (isSelected) {
                    Box(
                        modifier = Modifier
                            .size(18.dp)
                            .clip(CircleShape)
                            .background(spec.accentPreview),
                        contentAlignment = Alignment.Center,
                    ) {
                        val iconTint = if ((spec.accentPreview.red * 0.299f + spec.accentPreview.green * 0.587f + spec.accentPreview.blue * 0.114f) > 0.5f) Color(0xFF050607) else Color.White
                        Icon(
                            imageVector = Icons.Rounded.Check,
                            contentDescription = "انتخاب شده",
                            tint = iconTint,
                            modifier = Modifier.size(12.dp),
                        )
                    }
                } else {
                    Text(
                        text = if (theme == ThemeMode.SYSTEM) "خودکار" else if (spec.isDark) "تیره" else "روشن",
                        fontSize = 10.sp,
                        color = textColor.copy(alpha = 0.65f),
                    )
                }
            }

            // Bottom row: Theme title
            Text(
                text = theme.fa,
                fontSize = 12.sp,
                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.SemiBold,
                color = if (isSelected) spec.accentPreview else textColor,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

/** One switched setting: emoji, what it does, what it currently means, and the switch. */
@Composable
fun SettingCard(
    emoji: String,
    title: String,
    subtitle: String,
    checked: Boolean,
    onChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    shape: Shape = RoundedCornerShape(Radius.group),
    divided: Boolean = false,
    /** Replaces the emoji plate where the row has a real mark of its own. */
    badge: (@Composable () -> Unit)? = null,
) {
    Box(modifier.fillMaxWidth().clip(shape).background(MaterialTheme.colorScheme.surface)) {
        // toggleable on the row, not onCheckedChange on the switch: the whole card becomes
        // one control named by its title — four bare "switch, on" in a row told a TalkBack
        // user nothing — and the text is part of the hit target.
        Row(
            Modifier
                .toggleable(
                    value = checked,
                    enabled = enabled,
                    role = Role.Switch,
                    onValueChange = onChange,
                )
                .padding(horizontal = Space.l, vertical = Space.l),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            // The same circular badge the asset rows use, so a settable thing and an owned
            // thing at least agree about what an icon looks like in this app.
            if (badge != null) {
                badge()
            } else {
                Box(
                    Modifier
                        .size(44.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.surfaceContainerHighest),
                    contentAlignment = Alignment.Center,
                ) { Text(emoji, fontSize = 20.sp) }
            }
            Column(Modifier.padding(horizontal = Space.m).weight(1f)) {
                Text(
                    title,
                    fontSize = 17.sp,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                if (subtitle.isNotBlank()) {
                    Text(
                        subtitle,
                        fontSize = 13.sp,
                        lineHeight = 20.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 2.dp),
                    )
                }
            }
            Switch(checked = checked, enabled = enabled, onCheckedChange = null)
        }
        // Inset to where the text starts, like the asset rows: a rule under the badge cuts
        // the row in half instead of separating it from the next one.
        if (divided) {
            HorizontalDivider(
                color = MaterialTheme.colorScheme.outlineVariant,
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .padding(start = Space.l + 44.dp + Space.m),
            )
        }
    }
}

/**
 * Choosing the shape of the financial month: the plain calendar month, or a cycle that starts on
 * payday. Anchors read as Persian ordinals so the ۲۹ام is a payday, not a spreadsheet cell; 31
 * quietly means «آخر ماه» because in the short months that is exactly what it lands on.
 */
@Composable
private fun CyclePicker(
    mode: CycleMode,
    anchorDay: Int,
    onModeChange: (CycleMode) -> Unit,
    onAnchorChange: (Int) -> Unit,
) {
    val anchors = remember { listOf(28, 29, 30, 31) }

    Column(verticalArrangement = Arrangement.spacedBy(Space.s)) {
        SegmentedChoice(
            options = listOf(CycleMode.JALALI_MONTH, CycleMode.ANCHOR),
            selected = mode,
            label = {
                if (it == CycleMode.JALALI_MONTH) "ماه تقویمی"
                else "چرخهٔ حقوق"
            },
            onSelect = onModeChange,
            fontSize = 13.sp,
        )
        androidx.compose.animation.AnimatedVisibility(
            visible = mode == CycleMode.ANCHOR,
            enter = androidx.compose.animation.expandVertically() + androidx.compose.animation.fadeIn(),
            exit = androidx.compose.animation.shrinkVertically() + androidx.compose.animation.fadeOut(),
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(Space.s)) {
                SegmentedChoice(
                    options = anchors,
                    selected = anchorDay.takeIf { it in anchors } ?: 29,
                    label = { day ->
                        if (day == 31) "آخر ماه" else "${faNumber(day.toLong())}ام"
                    },
                    onSelect = onAnchorChange,
                    fontSize = 13.sp,
                )
                val now = tehranDay(System.currentTimeMillis())
                val start = jalaliOf(cycleWindow(now, CycleSpec(CycleMode.ANCHOR, anchorDay)).startDay)
                Text(
                    "چرخهٔ فعلی از ${faNumber(start.year.toLong())}/${faNumber(start.month.toLong())}/${faNumber(start.day.toLong())} شروع می‌شه.",
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(start = Space.xs),
                )
            }
        }
    }
}
