package com.doxigo.muchtoman.features.reminders

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.doxigo.muchtoman.ApplySystemBars
import com.doxigo.muchtoman.MuchTomanTheme
import com.doxigo.muchtoman.Radius
import com.doxigo.muchtoman.Space
import com.doxigo.muchtoman.Store
import com.doxigo.muchtoman.faNumber
import com.doxigo.muchtoman.formatShamsiDate
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * The reminders settings screen, as a self-contained activity.
 *
 * It is a second activity for the same reason [com.doxigo.muchtoman.QuickAddActivity] is: the
 * main settings page is already a long scroll inside the app's own navigation, and reminders are
 * a self-contained block with its own permission flow, its own test button and its own honest
 * copy. Hanging it off the main settings page with an intent keeps that page from growing a
 * section that only makes sense with its own permission state.
 *
 * Everything here is sticky-immediate: a toggle writes the preference in the same tap and asks
 * WorkManager to replan, so there is no Save button that can be forgotten and no state that
 * survives only until the app is killed. The screen is honest about the two things it cannot
 * promise — that Android will show the notification at the exact minute, and that no other copy
 * of it exists — because this app has no account and no server to hide behind.
 */
class ReminderSettingsActivity : ComponentActivity() {

    /** Re-read on every resume: she can grant or revoke the permission from system settings. */
    private val notificationsAllowed = mutableStateOf(false)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        notificationsAllowed.value = NotificationPermission.isGranted(this)

        val store = Store(applicationContext)
        val prefs = ReminderPrefs(applicationContext)
        enableEdgeToEdge()
        setContent {
            MuchTomanTheme(mode = store.themeMode) {
            ApplySystemBars()
                CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
                    Surface(
                        modifier = Modifier.fillMaxSize(),
                        color = MaterialTheme.colorScheme.background,
                    ) {
                        ReminderSettingsScreen(
                            activity = this@ReminderSettingsActivity,
                            prefs = prefs,
                            notificationsAllowed = notificationsAllowed,
                            onClose = { finish() },
                        )
                    }
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        notificationsAllowed.value = NotificationPermission.isGranted(this)
    }
}

/**
 * The one-liner the rest of the app calls.
 *
 * Keeping the intent here means the settings screen does not have to know this activity's class
 * name or whether the caller is an activity (the settings page) or a worker (which would need
 * `NEW_TASK`). One entry point, one place to change.
 */
fun openReminderSettings(context: Context) {
    val intent = Intent(context, ReminderSettingsActivity::class.java)
    if (context !is Activity) intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    context.startActivity(intent)
}

/** Which clock value the time dialog is currently editing. */
private enum class TimeTarget(val title: String) {
    REMINDER("ساعت یادآوری"),
    QUIET_START("شروع ساعت‌های سکوت"),
    QUIET_END("پایان ساعت‌های سکوت"),
}

@Composable
private fun ReminderSettingsScreen(
    activity: Activity,
    prefs: ReminderPrefs,
    notificationsAllowed: MutableState<Boolean>,
    onClose: () -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var enabled by remember { mutableStateOf(prefs.enabled) }
    var leadDays by remember { mutableStateOf(prefs.leadDays) }
    var minuteOfDay by remember { mutableStateOf(prefs.minuteOfDay) }
    var quietEnabled by remember { mutableStateOf(prefs.quietEnabled) }
    var quietStart by remember { mutableStateOf(prefs.quietStartMinute) }
    var quietEnd by remember { mutableStateOf(prefs.quietEndMinute) }

    var showPermissionRationale by remember { mutableStateOf(false) }
    var pendingEnable by remember { mutableStateOf(false) }
    var pendingTest by remember { mutableStateOf(false) }
    var editing by remember { mutableStateOf<TimeTarget?>(null) }
    var preview by remember { mutableStateOf<ReminderPlan?>(null) }
    var previewLoading by remember { mutableStateOf(true) }

    val allowed = notificationsAllowed.value

    // Writing the whole settings value keeps the screen and the store from disagreeing about,
    // say, the quiet window while only its end was edited.
    fun persist() {
        prefs.save(
            ReminderSettings(
                enabled = enabled,
                leadDays = leadDays,
                minuteOfDay = minuteOfDay,
                quietHours = QuietHours(
                    enabled = quietEnabled,
                    startMinuteOfDay = quietStart,
                    endMinuteOfDay = quietEnd,
                ),
            ),
        )
    }

    fun replan() {
        persist()
        val appContext = context.applicationContext
        scope.launch { ReminderScheduler.reschedule(appContext) }
    }

    /**
     * Decide between the explanation dialog and the system settings page.
     *
     * Android stops showing its own permission dialog after two refusals and gives the app no
     * direct signal for it. [ReminderPrefs.notificationPermissionAsked] plus
     * `shouldShowRequestPermissionRationale` is the best available read: if we have asked before
     * and the system no longer wants to explain, the settings page is the only place left. On
     * API < 33 there is no runtime permission at all, so the dialog explains and then sends her to
     * the app's notification settings.
     */
    fun askForPermission(enableAfter: Boolean, testAfter: Boolean) {
        pendingEnable = enableAfter
        pendingTest = testAfter
        ReminderNotifications.ensureChannel(context)
        val canAskRuntime = Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU
        val systemWillExplain = !canAskRuntime ||
            NotificationPermission.shouldShowRationale(activity) ||
            !prefs.notificationPermissionAsked
        if (systemWillExplain) {
            showPermissionRationale = true
        } else {
            // Refused for good: the dialog would not appear, so do not pretend to ask.
            NotificationPermission.openNotificationSettings(context)
            pendingEnable = false
            pendingTest = false
        }
    }

    fun testNotification() {
        if (!NotificationPermission.isGranted(context)) {
            askForPermission(enableAfter = false, testAfter = true)
            return
        }
        val shown = ReminderNotifications.postTest(context)
        Toast.makeText(
            context,
            if (shown) "اعلان آزمایشی فرستاده شد" else "اجازهٔ اعلان بسته است",
            Toast.LENGTH_SHORT,
        ).show()
    }

    fun setEnabled(next: Boolean) {
        if (!next) {
            enabled = false
            prefs.enabled = false
            replan()
            return
        }
        if (!NotificationPermission.isGranted(context)) {
            // The permission is asked for here and nowhere else: switching the feature on is the
            // only moment the request has an obvious reason attached to it.
            askForPermission(enableAfter = true, testAfter = false)
            return
        }
        enabled = true
        prefs.enabled = true
        replan()
    }

    val requestNotifications = rememberNotificationPermissionRequest { granted ->
        notificationsAllowed.value = granted
        if (granted) {
            if (pendingEnable) {
                enabled = true
                prefs.enabled = true
            }
            if (pendingTest) testNotification()
        }
        pendingEnable = false
        pendingTest = false
        if (enabled) replan()
    }

    // The preview is computed from exactly what the worker will read, so it is a preview of the
    // real schedule and not a second implementation of it that can drift.
    LaunchedEffect(enabled, leadDays, minuteOfDay, quietEnabled, quietStart, quietEnd, allowed) {
        previewLoading = true
        preview = if (!enabled) {
            null
        } else {
            withContext(Dispatchers.IO) {
                val now = System.currentTimeMillis()
                val items = loadReminderItems(context, now)
                ReminderPlanner.plan(items, now, prefs.settings(), prefs.firedKeys())
            }
        }
        previewLoading = false
    }

    BackHandler(onBack = onClose)

    if (showPermissionRationale) {
        val canAsk = Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU
        AlertDialog(
            onDismissRequest = {
                showPermissionRationale = false
                pendingEnable = false
                pendingTest = false
            },
            title = { Text("اجازهٔ نمایش اعلان", fontSize = 18.sp, fontWeight = FontWeight.Bold) },
            text = {
                Text(
                    "یادآورها فقط روی همین گوشی ساخته می‌شوند، ولی اندروید برای نشان دادنشان " +
                        "اجازهٔ اعلان می‌خواهد. بدون این اجازه هیچ یادآوری‌ای روی صفحه نمی‌آید.",
                    fontSize = 14.sp,
                    lineHeight = 22.sp,
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    showPermissionRationale = false
                    prefs.notificationPermissionAsked = true
                    if (canAsk) {
                        requestNotifications()
                    } else {
                        // Below Android 13 there is no runtime permission to grant; the system
                        // notification switch is the only place this can be changed.
                        NotificationPermission.openNotificationSettings(context)
                        pendingEnable = false
                        pendingTest = false
                    }
                }) {
                    Text(if (canAsk) "اجازه می‌دهم" else "تنظیمات اعلان‌ها")
                }
            },
            dismissButton = {
                TextButton(onClick = {
                    showPermissionRationale = false
                    pendingEnable = false
                    pendingTest = false
                }) { Text("فعلاً نه") }
            },
        )
    }

    editing?.let { target ->
        val initial = when (target) {
            TimeTarget.REMINDER -> minuteOfDay
            TimeTarget.QUIET_START -> quietStart
            TimeTarget.QUIET_END -> quietEnd
        }
        TimeEntryDialog(
            title = target.title,
            initialMinute = initial,
            onDismiss = { editing = null },
            onConfirm = { picked ->
                when (target) {
                    TimeTarget.REMINDER -> minuteOfDay = picked
                    TimeTarget.QUIET_START -> quietStart = picked
                    TimeTarget.QUIET_END -> quietEnd = picked
                }
                editing = null
                replan()
            },
        )
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .systemBarsPadding()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = Space.xl, vertical = Space.l),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(
                    "یادآورها",
                    fontSize = 28.sp,
                    fontWeight = FontWeight.ExtraBold,
                    color = MaterialTheme.colorScheme.onBackground,
                )
                Text(
                    "چک، وام و اقساط — فقط روی همین گوشی",
                    fontSize = 13.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = Space.xs),
                )
            }
            TextButton(onClick = onClose) { Text("بازگشت", fontSize = 15.sp) }
        }

        Spacer(Modifier.height(Space.l))

        ReminderCard {
            Text(
                "همهٔ یادآوری‌ها روی همین گوشی محاسبه می‌شوند",
                fontSize = 15.sp,
                fontWeight = FontWeight.Bold,
            )
            Text(
                "یادآورها فقط روی گوشی ساخته می‌شوند؛ هیچ اعلانی از این گوشی بیرون نمی‌رود " +
                    "و سروری در جریان نیست. اندروید ممکن است یادآور را چند دقیقه دیرتر نشان " +
                    "دهد؛ برای همین از آلارم دقیق استفاده نمی‌کنیم تا باتری گوشی بیدار نماند.",
                fontSize = 13.sp,
                lineHeight = 21.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = Space.s),
            )
        }

        if (!allowed) {
            Spacer(Modifier.height(Space.m))
            ReminderCard(containerColor = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.55f)) {
                Text(
                    "اجازهٔ اعلان بسته است",
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold,
                )
                Text(
                    "تا وقتی این اجازه روشن نشود، یادآوری‌ای روی صفحه نمی‌آید. " +
                        "یادآوری‌ها همچنان محاسبه می‌شوند، ولی دیده نمی‌شوند.",
                    fontSize = 13.sp,
                    lineHeight = 21.sp,
                    modifier = Modifier.padding(top = Space.s),
                )
                Row(horizontalArrangement = Arrangement.spacedBy(Space.s)) {
                    OutlinedButton(
                        onClick = { NotificationPermission.openNotificationSettings(context) },
                        shape = RoundedCornerShape(Radius.field),
                    ) { Text("تنظیمات اعلان‌ها", fontSize = 13.sp) }
                    TextButton(onClick = {
                        askForPermission(enableAfter = false, testAfter = false)
                    }) { Text("اجازه بده", fontSize = 13.sp) }
                }
            }
        }

        Spacer(Modifier.height(Space.l))
        ReminderCard {
            ToggleRow(
                title = "یادآوری سررسیدها",
                subtitle = if (enabled) {
                    "روشن — ${leadText(leadDays)}"
                } else {
                    "خاموش — هیچ اعلانی نمی‌آید."
                },
                checked = enabled,
                onCheckedChange = { setEnabled(it) },
            )
        }

        Spacer(Modifier.height(Space.m))
        ReminderCard {
            Text("چند روز قبل یادآوری شود؟", fontSize = 15.sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(Space.s))
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center,
            ) {
                StepButton("−", enabled = leadDays > 0) {
                    leadDays = (leadDays - 1).coerceAtLeast(0)
                    replan()
                }
                Text(
                    if (leadDays == 0) "روز سررسید" else "${faNumber(leadDays)} روز قبل",
                    fontSize = 17.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier
                        .width(150.dp)
                        .padding(horizontal = Space.s),
                    textAlign = TextAlign.Center,
                )
                StepButton("+", enabled = leadDays < ReminderSettings.MAX_LEAD_DAYS) {
                    leadDays = (leadDays + 1).coerceAtMost(ReminderSettings.MAX_LEAD_DAYS)
                    replan()
                }
            }
            Text(
                "اگر سررسید از الان دورتر از این تعداد روز باشد، هنوز یادآوری‌ای نمی‌آید. " +
                    "صفر یعنی همان روز.",
                fontSize = 12.sp,
                lineHeight = 19.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = Space.s),
            )
        }

        Spacer(Modifier.height(Space.m))
        ReminderCard {
            Text("ساعت یادآوری", fontSize = 15.sp, fontWeight = FontWeight.Bold)
            SettingRow(
                title = "ساعت نمایش یادآورها",
                value = faClock(minuteOfDay),
                onClick = { editing = TimeTarget.REMINDER },
            )
            Text(
                "یادآور روزانه (ثبت تراکنش‌ها و سررسید نزدیک) و یادآوری سررسیدها هر دو این " +
                    "ساعت می‌آیند، نه در لحظهٔ دقیق سررسید.",
                fontSize = 12.sp,
                lineHeight = 19.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        Spacer(Modifier.height(Space.m))
        ReminderCard {
            ToggleRow(
                title = "ساعت‌های سکوت",
                subtitle = "در این بازه اعلانی نمی‌آید.",
                checked = quietEnabled,
                onCheckedChange = {
                    quietEnabled = it
                    replan()
                },
            )
            if (quietEnabled) {
                SettingRow(
                    title = "از",
                    value = faClock(quietStart),
                    onClick = { editing = TimeTarget.QUIET_START },
                )
                SettingRow(
                    title = "تا",
                    value = faClock(quietEnd),
                    onClick = { editing = TimeTarget.QUIET_END },
                )
                Text(
                    "اگر یادآوری داخل این بازه بیفتد، به پایان بازه منتقل می‌شود — حتی اگر " +
                        "بعد از سررسید باشد. بازه‌ای که از نیمه‌شب عبور می‌کند (مثلاً ۲۳ تا ۸) " +
                        "به‌درستی حساب می‌شود.",
                    fontSize = 12.sp,
                    lineHeight = 19.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                if (quietStart == quietEnd) {
                    Text(
                        "شروع و پایان برابر است، یعنی این بازه خالی است و سکوتی اعمال نمی‌شود.",
                        fontSize = 12.sp,
                        lineHeight = 19.sp,
                        color = MaterialTheme.colorScheme.error,
                        modifier = Modifier.padding(top = Space.s),
                    )
                }
            }
        }

        Spacer(Modifier.height(Space.m))
        ReminderCard {
            Text("اعلان آزمایشی", fontSize = 15.sp, fontWeight = FontWeight.Bold)
            Text(
                "برای اینکه ببینی اعلان‌ها روی این گوشی کار می‌کنند. این اعلان در سابقهٔ " +
                    "یادآوری‌ها ثبت نمی‌شود.",
                fontSize = 12.sp,
                lineHeight = 19.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = Space.xs),
            )
            Button(
                onClick = { testNotification() },
                shape = RoundedCornerShape(Radius.field),
                modifier = Modifier.padding(top = Space.s),
            ) {
                Text("فرستادن اعلان آزمایشی", fontWeight = FontWeight.Bold)
            }
        }

        Spacer(Modifier.height(Space.m))
        ReminderCard {
            Text("یادآوری بعدی", fontSize = 15.sp, fontWeight = FontWeight.Bold)
            Text(
                nextReminderText(preview, enabled, previewLoading),
                fontSize = 14.sp,
                lineHeight = 22.sp,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.padding(top = Space.s),
            )
            Text(
                "این خط از همان داده‌ای ساخته می‌شود که کارگر واقعی می‌خواند؛ پیش‌نمایش " +
                    "جداگانه‌ای نیست که با واقعیت فرق کند.",
                fontSize = 12.sp,
                lineHeight = 19.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = Space.s),
            )
        }

        Spacer(Modifier.height(Space.huge))
    }
}

// ─────────────────────────── small building blocks ───────────────────────────

@Composable
private fun ReminderCard(
    containerColor: Color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f),
    content: @Composable ColumnScope.() -> Unit,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(Radius.group),
        colors = CardDefaults.cardColors(containerColor = containerColor),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(Space.l),
            content = content,
        )
    }
}

@Composable
private fun ToggleRow(
    title: String,
    subtitle: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, fontSize = 15.sp, fontWeight = FontWeight.Bold)
            Text(
                subtitle,
                fontSize = 12.sp,
                lineHeight = 19.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 2.dp),
            )
        }
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}

@Composable
private fun SettingRow(title: String, value: String, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = Space.m),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            title,
            fontSize = 14.sp,
            modifier = Modifier.weight(1f),
            color = MaterialTheme.colorScheme.onSurface,
        )
        Text(
            value,
            fontSize = 17.sp,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.primary,
        )
    }
}

@Composable
private fun StepButton(
    label: String,
    enabled: Boolean = true,
    onClick: () -> Unit,
) {
    OutlinedButton(
        onClick = onClick,
        enabled = enabled,
        shape = RoundedCornerShape(Radius.pill),
        contentPadding = PaddingValues(horizontal = Space.l, vertical = Space.s),
    ) {
        Text(label, fontSize = 15.sp, fontWeight = FontWeight.Bold)
    }
}

/**
 * The time picker, small and Persian.
 *
 * The platform `TimePickerDialog` follows the phone's locale, which on an English phone prints
 * latin digits in the middle of an otherwise Persian screen. Two steppers and a live clock are
 * less code than teaching that dialog otherwise, and they work identically on API 24.
 */
@Composable
private fun TimeEntryDialog(
    title: String,
    initialMinute: Int,
    onDismiss: () -> Unit,
    onConfirm: (Int) -> Unit,
) {
    var minute by remember(initialMinute) { mutableStateOf(initialMinute) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title, fontSize = 17.sp, fontWeight = FontWeight.Bold) },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(
                    faClock(minute),
                    fontSize = 40.sp,
                    fontWeight = FontWeight.Black,
                    color = MaterialTheme.colorScheme.primary,
                )
                Spacer(Modifier.height(Space.l))
                Row(horizontalArrangement = Arrangement.spacedBy(Space.s)) {
                    StepButton("− ساعت") {
                        minute = (minute - 60 + MINUTES_PER_DAY) % MINUTES_PER_DAY
                    }
                    StepButton("+ ساعت") {
                        minute = (minute + 60) % MINUTES_PER_DAY
                    }
                }
                Spacer(Modifier.height(Space.s))
                Row(horizontalArrangement = Arrangement.spacedBy(Space.s)) {
                    StepButton("− ۵ دقیقه") {
                        minute = (minute - 5 + MINUTES_PER_DAY) % MINUTES_PER_DAY
                    }
                    StepButton("+ ۵ دقیقه") {
                        minute = (minute + 5) % MINUTES_PER_DAY
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(minute) }) { Text("تأیید", fontWeight = FontWeight.Bold) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("انصراف") }
        },
    )
}

// ─────────────────────────── copy helpers ───────────────────────────

private fun leadText(leadDays: Int): String =
    if (leadDays == 0) "روز سررسید" else "${faNumber(leadDays)} روز قبل"

private fun nextReminderText(plan: ReminderPlan?, enabled: Boolean, loading: Boolean): String {
    if (!enabled) return "خاموش است."
    if (loading) return "در حال خواندن سررسیدها…"
    val fire = plan?.nextAny ?: return "در ۴۵ روز آینده سررسیدی ثبت نشده است."
    val what = if (fire.daily) {
        "یادآور روزانه"
    } else {
        fire.item?.let { "«${it.label}»" } ?: "سررسید"
    }
    val date = formatShamsiDate(fire.atMillis)
    val clock = faClock(ReminderPlanner.localMinuteOfDay(fire.atMillis))
    return "$what — $date، ساعت $clock"
}

/** «۰۹:۰۵» — a clock that keeps Persian digits and two positions. */
private fun faClock(minuteOfDay: Int): String {
    val hour = (minuteOfDay / 60) % 24
    val minute = minuteOfDay % 60
    return "${twoDigits(hour)}:${twoDigits(minute)}"
}

private fun twoDigits(value: Int): String =
    value.toString().padStart(2, '0').map { char ->
        if (char in '0'..'9') '۰' + (char - '0') else char
    }.joinToString("")
