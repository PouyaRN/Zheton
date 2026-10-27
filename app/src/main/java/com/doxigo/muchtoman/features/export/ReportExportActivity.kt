package com.doxigo.muchtoman.features.export

import android.app.Activity
import android.content.ClipData
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
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
import androidx.compose.material3.CircularProgressIndicator
import com.doxigo.muchtoman.ui.components.FilterChip
import androidx.compose.material3.MaterialTheme
import com.doxigo.muchtoman.ui.components.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.listSaver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.FileProvider
import com.doxigo.muchtoman.JalaliDate
import com.doxigo.muchtoman.ApplySystemBars
import com.doxigo.muchtoman.MuchTomanTheme
import com.doxigo.muchtoman.Radius
import com.doxigo.muchtoman.Space
import com.doxigo.muchtoman.Store
import com.doxigo.muchtoman.jalaliDay
import com.doxigo.muchtoman.jalaliMonthLength
import com.doxigo.muchtoman.jalaliOf
import com.doxigo.muchtoman.parseAmount
import com.doxigo.muchtoman.tehranDay
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

/**
 * The export screen: pick a window, pick a format, get a file.
 *
 * It is a separate activity rather than a sheet inside Reports for two reasons that matter to
 * this app. First, generating a report reads the whole ledger and can take a moment on an old
 * phone; a full-screen surface can show "در حال ساخت" without freezing a tab the user is
 * scrolling. Second — and this is the one that decided it — the report is handed to an external
 * app through a `FileProvider` URI, and an export screen that owns exactly that job is easier
 * to check than a branch hidden in the middle of the reports tab.
 *
 * Nothing is written outside `cacheDir/exports/`. See [exportDir] for why.
 */
class ReportExportActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // The app's theme is a NoActionBar one with transparent system bars; opting into edge to
        // edge is what makes the status/navigation bar padding in the screen mean anything.
        enableEdgeToEdge()
        // Read the theme mode once, the same way QuickAddActivity does; a theme change while the
        // export sheet is open is not a flow worth recreating the activity for.
        val store = Store(applicationContext)
        setContent {
            MuchTomanTheme(mode = store.themeMode) {
            ApplySystemBars()
                // The app is Persian regardless of the phone locale, so the export screen is
                // forced right-to-left even on a device set to English.
                CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
                    ReportExportScreen(onClose = { finish() })
                }
            }
        }
    }

    companion object {
        /**
         * The one call the rest of the app needs. Kept here so the wiring is a single import:
         *
         * ```
         * ReportExportActivity.launch(context)
         * ```
         */
        fun launch(context: Context) {
            val intent = Intent(context, ReportExportActivity::class.java)
            // A service or worker has no task to attach to and needs the flag; an activity does
            // not, and adding it there is the kind of thing that ends up with two copies of the
            // app on the stack. The context is unwrapped because a Compose tree can hold a
            // theme wrapper rather than the Activity itself.
            if (!context.isActivityContext()) intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(intent)
        }
    }
}

/** True when [this] is, or wraps, an `Activity` — the only context that owns a task. */
private fun Context.isActivityContext(): Boolean {
    var current: Context? = this
    while (current is ContextWrapper) {
        if (current is Activity) return true
        current = current.baseContext
    }
    return current is Activity
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ReportExportScreen(onClose: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val today = remember { tehranDay(System.currentTimeMillis()) }
    val thisMonth = remember(today) { jalaliOf(today) }

    // Saveable, because rotating the phone mid-choice should not silently reset the window to
    // «این ماه» and the custom dates to today's month.
    var window by rememberSaveable { mutableStateOf(ReportWindow.THIS_MONTH) }
    var format by rememberSaveable { mutableStateOf(ReportFormat.XLSX) }
    var from by rememberSaveable(stateSaver = JalaliFieldsSaver) {
        mutableStateOf(JalaliFields.of(thisMonth.year, thisMonth.month, 1))
    }
    var to by rememberSaveable(stateSaver = JalaliFieldsSaver) {
        mutableStateOf(
            JalaliFields.of(
                thisMonth.year,
                thisMonth.month,
                jalaliMonthLength(thisMonth.year, thisMonth.month),
            ),
        )
    }

    var busy by remember { mutableStateOf(false) }
    var errorFa by remember { mutableStateOf<String?>(null) }
    var noticeFa by remember { mutableStateOf<String?>(null) }

    val customFrom = from.toDateOrNull()
    val customTo = to.toDateOrNull()
    val previewRange: DayRange? = when (window) {
        ReportWindow.CUSTOM -> if (customFrom != null && customTo != null) {
            runCatching { customRange(customFrom, customTo) }.getOrNull()
        } else {
            null
        }
        else -> window.resolve(today)
    }

    fun startExport() {
        if (busy) return
        val range = when {
            window != ReportWindow.CUSTOM -> window.resolve(today)
            customFrom == null || customTo == null -> {
                errorFa = "تاریخ شروع و پایان را کامل و درست وارد کن"
                return
            }
            else -> customRange(customFrom, customTo)
        }
        busy = true
        errorFa = null
        noticeFa = null
        scope.launch {
            val outcome = runCatching {
                withContext(Dispatchers.IO) { buildFile(context.applicationContext, range, format) }
            }
            busy = false
            outcome
                .onSuccess { file ->
                    noticeFa = "فایل ساخته شد: ${file.name}"
                    shareFile(context, file, format.mimeType)
                }
                .onFailure { failure ->
                    errorFa = failure.message?.takeIf { it.isNotBlank() } ?: "ساخت گزارش ناموفق بود"
                }
        }
    }

    Surface(color = MaterialTheme.colorScheme.background, modifier = Modifier.fillMaxSize()) {
        Column(
            Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding(),
        ) {
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = Space.xl, vertical = Space.l),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text("خروجی گزارش", fontSize = 24.sp, fontWeight = FontWeight.ExtraBold)
                Spacer(Modifier.weight(1f))
                TextButton(onClick = onClose) { Text("بستن") }
            }

            Column(
                Modifier
                    .weight(1f)
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = Space.xl),
            ) {
                SectionLabel("بازهٔ گزارش")
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(Space.s),
                    verticalArrangement = Arrangement.spacedBy(Space.s),
                ) {
                    ReportWindow.entries.forEach { candidate ->
                        FilterChip(
                            selected = window == candidate,
                            onClick = { window = candidate },
                            label = { Text(candidate.fa) },
                        )
                    }
                }
                Spacer(Modifier.height(Space.m))
                Text(
                    previewRange?.let { rangeLabelFa(it) } ?: "تاریخ‌ها را کامل کن",
                    fontSize = 13.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )

                if (window == ReportWindow.CUSTOM) {
                    Spacer(Modifier.height(Space.l))
                    Text("از تاریخ", fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                    JalaliDateFields(from) { from = it }
                    Spacer(Modifier.height(Space.s))
                    Text("تا تاریخ", fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                    JalaliDateFields(to) { to = it }
                }

                Spacer(Modifier.height(Space.xl))
                SectionLabel("قالب فایل")
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(Space.s),
                    verticalArrangement = Arrangement.spacedBy(Space.s),
                ) {
                    ReportFormat.entries.forEach { candidate ->
                        FilterChip(
                            selected = format == candidate,
                            onClick = { format = candidate },
                            label = { Text(candidate.fa) },
                        )
                    }
                }

                Spacer(Modifier.height(Space.xl))
                SectionLabel("چه چیزی در گزارش می‌آید")
                Bullet("خلاصهٔ درآمد، خرج و خالص بازه")
                Bullet("خرج و درآمد به تفکیک دسته، با سهم هر دسته")
                Bullet("جمع هر عضو خانواده، اگر عضوها ثبت شده باشند")
                Bullet("اقساط و تعهدهای سررسید در بازه")
                Bullet("ارزش دارایی‌ها با آخرین قیمت‌های ذخیره‌شده")
                Spacer(Modifier.height(Space.m))
                Text(
                    "متن پیامک، شمارهٔ کارت و شمارهٔ حساب در خروجی نیست. تراکنش‌های تکراری، " +
                        "پنهان و انتقال بین حساب‌های خودت هم در جمع‌ها حساب نمی‌شوند.",
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )

                errorFa?.let { message ->
                    Spacer(Modifier.height(Space.l))
                    Text(message, fontSize = 13.sp, color = MaterialTheme.colorScheme.error)
                }
                noticeFa?.let { message ->
                    Spacer(Modifier.height(Space.l))
                    Text(message, fontSize = 13.sp, color = MaterialTheme.colorScheme.primary)
                }

                Spacer(Modifier.height(Space.xl))
            }

            Button(
                onClick = { startExport() },
                enabled = !busy,
                shape = RoundedCornerShape(Radius.card),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(Space.xl)
                    .height(52.dp),
            ) {
                if (busy) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(18.dp),
                        strokeWidth = 2.dp,
                        color = MaterialTheme.colorScheme.onPrimary,
                    )
                    Spacer(Modifier.width(Space.s))
                    Text("در حال ساخت گزارش…")
                } else {
                    Text(
                        if (format == ReportFormat.PDF) "ساخت PDF و اشتراک‌گذاری"
                        else "ساخت اکسل و اشتراک‌گذاری",
                    )
                }
            }
        }
    }
}

@Composable
private fun SectionLabel(text: String) {
    Text(
        text,
        fontSize = 14.sp,
        fontWeight = FontWeight.Bold,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(bottom = Space.s),
    )
}

@Composable
private fun Bullet(text: String) {
    Row(Modifier.padding(vertical = 3.dp)) {
        Text("•", fontSize = 14.sp, color = MaterialTheme.colorScheme.primary)
        Spacer(Modifier.width(Space.s))
        Text(text, fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurface)
    }
}

/** Three short fields, because a Jalali date picker widget does not exist here and one tap too many kills the flow. */
@Composable
private fun JalaliDateFields(fields: JalaliFields, onChange: (JalaliFields) -> Unit) {
    Row(
        Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(Space.s),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        JalaliField("سال", fields.year, Modifier.weight(1.2f)) {
            onChange(fields.copy(year = it.take(4)))
        }
        JalaliField("ماه", fields.month, Modifier.weight(1f)) {
            onChange(fields.copy(month = it.take(2)))
        }
        JalaliField("روز", fields.day, Modifier.weight(1f)) {
            onChange(fields.copy(day = it.take(2)))
        }
    }
}

@Composable
private fun JalaliField(label: String, value: String, modifier: Modifier, onValueChange: (String) -> Unit) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text(label) },
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
        shape = RoundedCornerShape(Radius.field),
        modifier = modifier,
    )
}

private val JalaliFieldsSaver = listSaver<JalaliFields, String>(
    save = { listOf(it.year, it.month, it.day) },
    restore = { JalaliFields(it[0], it[1], it[2]) },
)

/**
 * The three numbers as text, because they are edited on a phone keyboard and Persian digits are
 * what the user actually types. Parsing and validation happen once, in [toDateOrNull].
 */
private data class JalaliFields(val year: String, val month: String, val day: String) {

    fun toDateOrNull(): JalaliDate? {
        val y = positiveInt(year) ?: return null
        val m = positiveInt(month) ?: return null
        val d = positiveInt(day) ?: return null
        if (y !in 1300..1500 || m !in 1..12) return null
        val dayNumber = runCatching { jalaliDay(y, m, d) }.getOrNull() ?: return null
        // A round-trip catches things like «۳۱ اسفند» in a non-leap year: the converter would
        // silently roll it into the next month, and the report would cover days she never chose.
        return if (jalaliOf(dayNumber) == JalaliDate(y, m, d)) JalaliDate(y, m, d) else null
    }

    companion object {
        fun of(year: Int, month: Int, day: Int) = JalaliFields(
            year = faDigits(year.toString()),
            month = faDigits(month.toString()),
            day = faDigits(day.toString()),
        )
    }
}

/** Reads both digit systems — [parseAmount] already handles «۱۴۰۵», «١٤٠٥» and plain ASCII. */
private fun positiveInt(text: String): Int? =
    parseAmount(text)?.toInt()?.takeIf { it > 0 }

private fun faDigits(text: String): String =
    text.map { if (it in '0'..'9') '۰' + (it - '0') else it }.joinToString("")

/**
 * Reads the ledger, builds the document and renders it — all off the main thread.
 *
 * The empty-ledger check is here rather than in the builder so the builder can still produce a
 * valid (zero) report for an empty *range*, which is a real thing to export: "this month had no
 * transactions" is an answer. A ledger with no transactions at all is a setup problem, and the
 * message says so in the user's words.
 */
private suspend fun buildFile(context: Context, range: DayRange, format: ReportFormat): File {
    val inputs = ReportSource(context).inputs(range)
    if (inputs.entries.isEmpty()) {
        throw IllegalStateException("هنوز تراکنشی ثبت نشده است؛ اول یک تراکنش اضافه کن")
    }
    val document = buildReport(inputs)
    val bytes = format.renderer(context).render(document)
    val dir = exportDir(context.cacheDir)
    pruneExports(dir)
    return File(dir, exportFileName(range, format)).apply { writeBytes(bytes) }
}

/**
 * The same share shape the CSV export already uses, and for the same reason: the URI must be a
 * `content://` one granted to the receiving app, never a `file://` path, or Android 7+ throws
 * `FileUriExposedException` at the exact moment the user expects a share sheet.
 *
 * The authority is the one declared in the manifest (`${applicationId}.fileprovider`); nothing
 * about it is re-declared in this package.
 */
private fun shareFile(context: Context, file: File, mimeType: String) {
    runCatching {
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
        val send = Intent(Intent.ACTION_SEND).apply {
            type = mimeType
            putExtra(Intent.EXTRA_STREAM, uri)
            putExtra(Intent.EXTRA_SUBJECT, "گزارش مالی چندتومان")
            clipData = ClipData.newRawUri(file.name, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        val chooser = Intent.createChooser(send, "اشتراک‌گذاری گزارش").apply {
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(chooser)
    }.onFailure {
        Toast.makeText(context, "برنامه‌ای برای اشتراک‌گذاری پیدا نشد", Toast.LENGTH_SHORT).show()
    }
}
