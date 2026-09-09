package com.doxigo.muchtoman

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.Typeface
import android.os.Build
import android.util.LruCache
import android.util.TypedValue
import androidx.annotation.FontRes
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.core.content.res.ResourcesCompat
import androidx.compose.ui.unit.dp
import androidx.glance.GlanceModifier
import androidx.glance.Image
import androidx.glance.ImageProvider
import androidx.glance.LocalContext
import androidx.glance.layout.ContentScale
import androidx.glance.layout.height
import java.util.concurrent.ConcurrentHashMap
import kotlin.math.ceil

enum class ModamWeight(@FontRes val fontRes: Int) {
    REGULAR(R.font.modam),
    MEDIUM(R.font.modam_medium),
    SEMIBOLD(R.font.modam_semibold),
    HEAVY(R.font.modam_heavy),
}

object GlanceTypographyEngine {
    private const val TABULAR = "tnum"
    private val typefaceCache = ConcurrentHashMap<Int, Typeface>()

    // LRU Cache for rendered text and component bitmaps to avoid allocations on widget broadcasts
    private val bitmapCache = object : LruCache<String, Bitmap>(128) {
        override fun entryRemoved(evicted: Boolean, key: String?, oldValue: Bitmap?, newValue: Bitmap?) {
            // Let GC collect evicted bitmaps safely
        }
    }

    fun getTypeface(context: Context, @FontRes fontRes: Int): Typeface {
        return typefaceCache.getOrPut(fontRes) {
            runCatching { ResourcesCompat.getFont(context, fontRes) }.getOrNull() ?: Typeface.DEFAULT
        }
    }

    fun spToPx(context: Context, sp: Float): Float =
        TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_SP, sp, context.resources.displayMetrics)

    fun dpToPx(context: Context, dp: Float): Float =
        dp * context.resources.displayMetrics.density

    fun fade(color: Int, a: Int): Int = (color and 0x00FFFFFF) or (a shl 24)

    /**
     * Renders a text string into an anti-aliased bitmap set in the authentic Modam typeface.
     */
    fun renderTextBitmap(
        context: Context,
        text: String,
        sp: Float,
        color: Int,
        weight: ModamWeight = ModamWeight.REGULAR,
        tabular: Boolean = true,
        narrow: Boolean = false,
    ): Bitmap {
        if (text.isEmpty()) {
            return Bitmap.createBitmap(1, 1, Bitmap.Config.ARGB_8888)
        }

        val cacheKey = "txt_${text}_${sp}_${color}_${weight.name}_${tabular}_${narrow}"
        synchronized(bitmapCache) {
            bitmapCache.get(cacheKey)?.let { return it }
        }

        val tf = getTypeface(context, weight.fontRes)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.SUBPIXEL_TEXT_FLAG).apply {
            typeface = tf
            textSize = spToPx(context, sp)
            this.color = color
            if (tabular) {
                fontFeatureSettings = TABULAR
            }
            if (narrow && Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                fontVariationSettings = "'wdth' 90"
            }
        }

        val fm = paint.fontMetrics
        val width = ceil(paint.measureText(text)).toInt().coerceAtLeast(1)
        // bottom - top accounts for Persian diacritics / dots
        val height = ceil(fm.bottom - fm.top).toInt().coerceAtLeast(1)

        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        Canvas(bitmap).drawText(text, 0f, -fm.top, paint)

        synchronized(bitmapCache) {
            bitmapCache.put(cacheKey, bitmap)
        }
        return bitmap
    }

    /**
     * Renders a status badge/pill with directional caret (▲/▼) and Persian text in Modam SemiBold.
     */
    fun renderPillBitmap(
        context: Context,
        text: String,
        sp: Float,
        tone: Int,
        up: Boolean?,
    ): Bitmap {
        val cacheKey = "pill_${text}_${sp}_${tone}_${up}"
        synchronized(bitmapCache) {
            bitmapCache.get(cacheKey)?.let { return it }
        }

        val d = context.resources.displayMetrics
        val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.SUBPIXEL_TEXT_FLAG).apply {
            typeface = getTypeface(context, R.font.modam_semibold)
            textSize = spToPx(context, sp)
            fontFeatureSettings = TABULAR
            color = tone
        }

        val fm = paint.fontMetrics
        val padX = 8f * d.density
        val padY = 4f * d.density
        val gap = 4f * d.density
        val caret = if (up == null) 0f else paint.textSize * 0.45f
        val textW = paint.measureText(text)

        val w = ceil(padX * 2 + textW + if (up == null) 0f else caret + gap).toInt().coerceAtLeast(1)
        val h = ceil(padY * 2 + (fm.bottom - fm.top)).toInt().coerceAtLeast(1)

        val bitmap = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)

        // Pill background wash (15% opacity)
        val bgWash = fade(tone, 0x24)
        canvas.drawRoundRect(
            RectF(0f, 0f, w.toFloat(), h.toFloat()),
            h / 2f,
            h / 2f,
            Paint(Paint.ANTI_ALIAS_FLAG).apply { color = bgWash }
        )

        // RTL: Caret sits on the right leading edge
        if (up != null) {
            val cx = w - padX - caret / 2f
            val cy = h / 2f
            val r = caret / 2f
            val sign = if (up) -1f else 1f
            canvas.drawPath(
                Path().apply {
                    moveTo(cx, cy + sign * r * 0.88f)
                    lineTo(cx + r, cy - sign * r * 0.60f)
                    lineTo(cx - r, cy - sign * r * 0.60f)
                    close()
                },
                paint,
            )
        }

        canvas.drawText(text, padX, padY - fm.top, paint)

        synchronized(bitmapCache) {
            bitmapCache.put(cacheKey, bitmap)
        }
        return bitmap
    }

    /**
     * Renders a 16-digit bank card PAN strictly Left-to-Right with 4-4-4-4 block separation.
     */
    fun renderPanBitmap(
        context: Context,
        pan: String,
        isMasked: Boolean,
        sp: Float = 13f,
        color: Int = 0xFFFAF9F6.toInt(),
    ): Bitmap {
        val cacheKey = "pan_${pan}_${isMasked}_${sp}_${color}"
        synchronized(bitmapCache) {
            bitmapCache.get(cacheKey)?.let { return it }
        }

        val digits = pan.filter { it.isDigit() }
        val displayText = if (isMasked) {
            val last4 = if (digits.length >= 4) digits.takeLast(4) else "••••"
            "••••  ••••  ••••  $last4"
        } else {
            if (digits.length == 16) {
                "${digits.substring(0, 4)}  ${digits.substring(4, 8)}  ${digits.substring(8, 12)}  ${digits.substring(12, 16)}"
            } else {
                pan
            }
        }

        val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.SUBPIXEL_TEXT_FLAG).apply {
            typeface = getTypeface(context, R.font.modam_semibold)
            textSize = spToPx(context, sp)
            fontFeatureSettings = TABULAR
            this.color = color
        }

        val fm = paint.fontMetrics
        val w = ceil(paint.measureText(displayText)).toInt().coerceAtLeast(1)
        val h = ceil(fm.bottom - fm.top).toInt().coerceAtLeast(1)

        val bitmap = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        Canvas(bitmap).drawText(displayText, 0f, -fm.top, paint)

        synchronized(bitmapCache) {
            bitmapCache.put(cacheKey, bitmap)
        }
        return bitmap
    }

    /**
     * Renders a smooth financial trajectory sparkline with subtle gradient area.
     */
    fun renderSparklineBitmap(
        context: Context,
        points: List<Pair<Long, Double>>,
        tone: Int,
        widthPx: Int,
        heightPx: Int,
    ): Bitmap {
        val w = widthPx.coerceAtLeast(1)
        val h = heightPx.coerceAtLeast(1)

        if (points.isEmpty()) {
            return Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        }

        val d = context.resources.displayMetrics.density
        val bitmap = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)

        val minV = points.minOf { it.second }
        val maxV = points.maxOf { it.second }
        val span = (maxV - minV).takeIf { it > 0.0 } ?: 1.0
        val d0 = points.first().first
        val dSpan = (points.last().first - d0).coerceAtLeast(1L).toFloat()
        val padY = h * 0.12f

        fun px(day: Long) = (day - d0) / dSpan * w
        fun py(v: Double) =
            if (maxV == minV) h / 2f
            else (h - padY) - (((v - minV) / span).toFloat() * (h - 2 * padY))

        val line = Path()
        points.forEachIndexed { i, (day, v) ->
            if (i == 0) line.moveTo(px(day), py(v)) else line.lineTo(px(day), py(v))
        }

        val area = Path().apply {
            addPath(line)
            lineTo(w.toFloat(), h.toFloat())
            lineTo(0f, h.toFloat())
            close()
        }

        canvas.drawPath(
            area,
            Paint(Paint.ANTI_ALIAS_FLAG).apply {
                shader = LinearGradient(
                    0f, 0f, 0f, h.toFloat(),
                    fade(tone, 0x44), fade(tone, 0x00),
                    Shader.TileMode.CLAMP,
                )
            },
        )
        canvas.drawPath(
            line,
            Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = tone
                style = Paint.Style.STROKE
                strokeWidth = 2.2f * d
                strokeCap = Paint.Cap.ROUND
                strokeJoin = Paint.Join.ROUND
            },
        )
        return bitmap
    }

    /**
     * Renders a sleek horizontal progress gauge for daily budget/burn rate tracking.
     */
    fun renderProgressGaugeBitmap(
        context: Context,
        fraction: Float,
        activeTone: Int,
        bgTone: Int,
        widthPx: Int,
        heightPx: Int,
    ): Bitmap {
        val w = widthPx.coerceAtLeast(1)
        val h = heightPx.coerceAtLeast(1)
        val bitmap = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        val r = h / 2f

        // Background track
        canvas.drawRoundRect(
            RectF(0f, 0f, w.toFloat(), h.toFloat()),
            r, r,
            Paint(Paint.ANTI_ALIAS_FLAG).apply { color = bgTone }
        )

        // Active fill (RTL or LTR, width bounded)
        val fillW = (w * fraction.coerceIn(0f, 1f)).coerceAtLeast(h.toFloat())
        // In Persian RTL interfaces, fill starts from the right edge
        canvas.drawRoundRect(
            RectF(w - fillW, 0f, w.toFloat(), h.toFloat()),
            r, r,
            Paint(Paint.ANTI_ALIAS_FLAG).apply { color = activeTone }
        )

        return bitmap
    }
}

/**
 * High-fidelity Modam text composable for Jetpack Glance.
 * Renders via sharp bitmap to bypass the Android Launcher font fallback barrier.
 */
@Composable
fun GlanceModamText(
    text: String,
    modifier: GlanceModifier = GlanceModifier,
    sp: Float = 13f,
    color: Color = Color(0xFFFAF9F6),
    weight: ModamWeight = ModamWeight.REGULAR,
    tabular: Boolean = true,
    narrow: Boolean = false,
    contentDescription: String? = text,
) {
    val context = LocalContext.current
    val colorInt = color.toArgb()
    val bitmap = GlanceTypographyEngine.renderTextBitmap(
        context = context,
        text = text,
        sp = sp,
        color = colorInt,
        weight = weight,
        tabular = tabular,
        narrow = narrow,
    )

    Image(
        provider = ImageProvider(bitmap),
        contentDescription = contentDescription,
        modifier = modifier,
    )
}

/**
 * Directional status pill for Glance widgets (e.g. +۲٫۴٪ از دیروز).
 */
@Composable
fun GlanceModamPill(
    text: String,
    modifier: GlanceModifier = GlanceModifier,
    sp: Float = 10f,
    tone: Color = Color(0xFF3BE0A8),
    up: Boolean? = null,
    contentDescription: String? = text,
) {
    val context = LocalContext.current
    val bitmap = GlanceTypographyEngine.renderPillBitmap(
        context = context,
        text = text,
        sp = sp,
        tone = tone.toArgb(),
        up = up,
    )

    Image(
        provider = ImageProvider(bitmap),
        contentDescription = contentDescription,
        modifier = modifier,
    )
}

/**
 * Shetab card 16-digit PAN rendered strictly LTR in Modam SemiBold.
 */
@Composable
fun GlanceModamPan(
    pan: String,
    isMasked: Boolean,
    modifier: GlanceModifier = GlanceModifier,
    sp: Float = 13f,
    color: Color = Color(0xFFFAF9F6),
    contentDescription: String? = "شماره کارت",
) {
    val context = LocalContext.current
    val bitmap = GlanceTypographyEngine.renderPanBitmap(
        context = context,
        pan = pan,
        isMasked = isMasked,
        sp = sp,
        color = color.toArgb(),
    )

    Image(
        provider = ImageProvider(bitmap),
        contentDescription = contentDescription,
        modifier = modifier,
    )
}

/**
 * Smooth anti-aliased horizontal progress gauge for Glance widgets.
 */
@Composable
fun GlanceProgressGauge(
    fraction: Float,
    activeTone: Color,
    bgTone: Color = Color(0xFF26231C),
    modifier: GlanceModifier = GlanceModifier,
    heightDp: Float = 5f,
) {
    val context = LocalContext.current
    val d = context.resources.displayMetrics
    val widthPx = (280 * d.density).toInt().coerceAtLeast(100)
    val heightPx = (heightDp * d.density).toInt().coerceAtLeast(6)
    val bitmap = GlanceTypographyEngine.renderProgressGaugeBitmap(
        context = context,
        fraction = fraction,
        activeTone = activeTone.toArgb(),
        bgTone = bgTone.toArgb(),
        widthPx = widthPx,
        heightPx = heightPx,
    )

    Image(
        provider = ImageProvider(bitmap),
        contentDescription = "پیشرفت مصرف بودجه",
        contentScale = ContentScale.FillBounds,
        modifier = modifier.height(heightDp.dp),
    )
}
