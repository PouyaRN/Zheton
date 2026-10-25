package com.doxigo.muchtoman

import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.ExperimentalTextApi
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.Modifier
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import androidx.compose.ui.text.withStyle
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Shape
import com.doxigo.muchtoman.ui.haptics.ProvideFintechHaptics
import com.doxigo.muchtoman.ui.rikka.foundation.LocalContentColor
import com.doxigo.muchtoman.ui.rikka.foundation.LocalRikkaColors
import com.doxigo.muchtoman.ui.rikka.foundation.RikkaColors

/**
 * Modam, as one variable font file with two axes: wght 200–900 and wdth 70–100.
 *
 * `res/font/modam.ttf` is not the file the foundry ships. That one defaults to ExtraLight
 * Condensed, and `setFontVariationSettings` is API 26+ — so on API 24–25, where Compose
 * silently drops the settings below, every word in the app would come out hairline and
 * squeezed. The copy here has been retargeted so its default instance is Regular at normal
 * width: the axes still work where they can, and where they cannot the fallback is the
 * weight the text wanted anyway.
 */
@OptIn(ExperimentalTextApi::class)
private fun modam(weight: FontWeight, width: Float = 100f) = Font(
    R.font.modam,
    weight,
    variationSettings = FontVariation.Settings(
        FontVariation.weight(weight.weight),
        FontVariation.width(width),
    ),
)

val Modam = FontFamily(
    modam(FontWeight.Light),
    modam(FontWeight.Normal),
    modam(FontWeight.Medium),
    modam(FontWeight.SemiBold),
    modam(FontWeight.Bold),
    modam(FontWeight.ExtraBold),
    modam(FontWeight.Black),
)

/**
 * The face every figure is set in: same font, ten percent narrower.
 *
 * A Toman total is the longest string in the app and the one thing that must never wrap, and
 * the width axis is the only way to buy room without giving up size — which is the opposite
 * trade shrink-to-fit makes. Negative tracking would be the usual answer and is the wrong one
 * here: Persian is a connected script, and pulling glyphs together breaks the joins.
 */
val ModamFigures = FontFamily(
    modam(FontWeight.Medium, width = 90f),
    modam(FontWeight.SemiBold, width = 90f),
    modam(FontWeight.Bold, width = 90f),
    modam(FontWeight.ExtraBold, width = 90f),
    modam(FontWeight.Black, width = 90f),
)

/**
 * Tabular figures. Modam's default digits are proportional, so an unchanged ۱ next to a
 * changed ۸ shifts the whole number sideways every time a rate lands — visible as a twitch on
 * the one line she is watching. `tnum` pins every digit to the same advance.
 */
const val TABULAR = "tnum"

/** The hero total and any figure that stands on its own. */
fun figureStyle(color: Color, weight: FontWeight = FontWeight.ExtraBold) = TextStyle(
    fontFamily = ModamFigures,
    fontWeight = weight,
    fontFeatureSettings = TABULAR,
    color = color,
)

internal fun heroFigure(figure: String, tone: Color, unit: String = "تومان"): AnnotatedString =
    heroFigure(AnnotatedString(figure), tone, unit)

internal fun heroFigure(figure: AnnotatedString, tone: Color, unit: String = "تومان"): AnnotatedString = buildAnnotatedString {
    append(figure)
    withStyle(SpanStyle(fontSize = 0.9.em)) { append(" ") }
    withStyle(
        SpanStyle(
            fontSize = 0.42.em,
            fontWeight = FontWeight.Bold,
            color = tone.copy(alpha = 0.75f),
        ),
    ) { append(unit) }
}

/**
 * Studio-grade palette for the hero field. Each theme mode owns its own luxury gradient and accents.
 */
data class HeroPalette(
    val top: Color,
    val bottom: Color,
    val gold: Color,
    val muted: Color,
    val strong: Color,
    val mint: Color = Color(0xFF3BE0A8),
    val warn: Color = Color(0xFFFFB4A6),
    val well: Color = Color(0x1AFFFFFF),
    val hairline: Color = Color(0x1FFFFFFF),
)

val LocalHeroPalette = staticCompositionLocalOf {
    HeroPalette(
        top = Color(0xFF1E293B),
        bottom = Color(0xFF0F172A),
        gold = Color(0xFFF59E0B),
        muted = Color(0xFF94A3B8),
        strong = Color(0xFFF8FAFC),
    )
}

/**
 * The hero field adapts dynamically to whichever theme is chosen by the user.
 */
object Hero {
    val top: Color @Composable get() = LocalHeroPalette.current.top
    val bottom: Color @Composable get() = LocalHeroPalette.current.bottom
    val gold: Color @Composable get() = LocalHeroPalette.current.gold
    val muted: Color @Composable get() = LocalHeroPalette.current.muted
    val strong: Color @Composable get() = LocalHeroPalette.current.strong
    val mint: Color @Composable get() = LocalHeroPalette.current.mint
    val warn: Color @Composable get() = LocalHeroPalette.current.warn
    val well: Color @Composable get() = LocalHeroPalette.current.well
    val hairline: Color @Composable get() = LocalHeroPalette.current.hairline

    val defaultTop = Color(0xFF0C544A)
    val defaultBottom = Color(0xFF03201C)
    val defaultGold = Color(0xFFF7C948)
}

/** Theme choices: follow phone, light, dark, AMOLED, or iconic global fintech presets. */
enum class ThemeMode(val fa: String) {
    SYSTEM("خودکار"),
    ANDIS_TERMINAL("اندیس"),
    LIGHT("کلاسیک روشن"),
    DARK("تیره"),
    AMOLED("آمولد"),
    APPLE_TITANIUM("اپل تیتانیوم"),
    APPLE_SILVER("اپل سیلور"),
    REVOLUT_OBSIDIAN("رولوت ابسیدین"),
    NORDIC_FROST("نوردیک فراست"),
    CHAMPAGNE_GOLD("شامپاین"),
    PERSIAN_MINT("فیروزه روشن"),
    ROSE_QUARTZ("رز کوارتز"),
    CASH_APP_NEON("کش‌اپ نئون"),
    MONZO_OCEANIC("مانزو اقیانوسی"),
    DUBAI_GOLD("دبی گلد"),
    OBSIDIAN_EMERALD("زمرد"),
    ROYAL_AMETHYST("ارغوانی"),
    NORDIC_SLATE("نوردیک"),
    CYBER_GOLD("سایبرگلد");

    val isDark: Boolean
        get() = when (this) {
            LIGHT, APPLE_SILVER, NORDIC_FROST, CHAMPAGNE_GOLD, PERSIAN_MINT, ROSE_QUARTZ -> false
            else -> true
        }

    val isLight: Boolean
        get() = !isDark
}

/** Visual spec for theme swatch cards and interactive preview. */
data class ThemeVisualSpec(
    val mode: ThemeMode,
    val isDark: Boolean,
    val bgPreview: Color,
    val surfacePreview: Color,
    val accentPreview: Color,
    val heroTopPreview: Color,
    val heroBottomPreview: Color,
)

fun getThemeVisualSpec(mode: ThemeMode): ThemeVisualSpec = when (mode) {
    ThemeMode.SYSTEM -> ThemeVisualSpec(
        mode = mode,
        isDark = false,
        bgPreview = Color(0xFF14181D),
        surfacePreview = Color(0xFF1E293B),
        accentPreview = Color(0xFF38BDF8),
        heroTopPreview = Color(0xFF0F1622),
        heroBottomPreview = Color(0xFF070A0F),
    )
    ThemeMode.LIGHT -> ThemeVisualSpec(
        mode = mode,
        isDark = false,
        bgPreview = Color(0xFFF8FAFC),
        surfacePreview = Color.White,
        accentPreview = Color(0xFFD97706),
        heroTopPreview = Color(0xFFF8FAFC),
        heroBottomPreview = Color(0xFFEDF2F7),
    )
    ThemeMode.APPLE_SILVER -> ThemeVisualSpec(
        mode = mode,
        isDark = false,
        bgPreview = Color(0xFFF5F5F7),
        surfacePreview = Color.White,
        accentPreview = Color(0xFF0071E3),
        heroTopPreview = Color(0xFFE8EEF5),
        heroBottomPreview = Color(0xFFF5F5F7),
    )
    ThemeMode.NORDIC_FROST -> ThemeVisualSpec(
        mode = mode,
        isDark = false,
        bgPreview = Color(0xFFF0F7FF),
        surfacePreview = Color.White,
        accentPreview = Color(0xFF0284C7),
        heroTopPreview = Color(0xFFE0F2FE),
        heroBottomPreview = Color(0xFFF0F7FF),
    )
    ThemeMode.CHAMPAGNE_GOLD -> ThemeVisualSpec(
        mode = mode,
        isDark = false,
        bgPreview = Color(0xFFFAF8F5),
        surfacePreview = Color.White,
        accentPreview = Color(0xFFD97706),
        heroTopPreview = Color(0xFFF5EFEB),
        heroBottomPreview = Color(0xFFFAF8F5),
    )
    ThemeMode.PERSIAN_MINT -> ThemeVisualSpec(
        mode = mode,
        isDark = false,
        bgPreview = Color(0xFFF0FDF4),
        surfacePreview = Color.White,
        accentPreview = Color(0xFF059669),
        heroTopPreview = Color(0xFFDCFCE7),
        heroBottomPreview = Color(0xFFF0FDF4),
    )
    ThemeMode.ROSE_QUARTZ -> ThemeVisualSpec(
        mode = mode,
        isDark = false,
        bgPreview = Color(0xFFFFF1F2),
        surfacePreview = Color.White,
        accentPreview = Color(0xFFE11D48),
        heroTopPreview = Color(0xFFFFE4E6),
        heroBottomPreview = Color(0xFFFFF1F2),
    )
    ThemeMode.ANDIS_TERMINAL, ThemeMode.DARK -> ThemeVisualSpec(
        mode = mode,
        isDark = true,
        bgPreview = Color(0xFF070A0F),
        surfacePreview = Color(0xFF0F1622),
        accentPreview = Color(0xFF38BDF8),
        heroTopPreview = Color(0xFF0F1622),
        heroBottomPreview = Color(0xFF070A0F),
    )
    ThemeMode.AMOLED -> ThemeVisualSpec(
        mode = mode,
        isDark = true,
        bgPreview = Color(0xFF000000),
        surfacePreview = Color(0xFF0A0D0F),
        accentPreview = Color(0xFFD4FF00),
        heroTopPreview = Color(0xFF000000),
        heroBottomPreview = Color(0xFF050607),
    )
    ThemeMode.APPLE_TITANIUM -> ThemeVisualSpec(
        mode = mode,
        isDark = true,
        bgPreview = Color(0xFF000000),
        surfacePreview = Color(0xFF141416),
        accentPreview = Color(0xFFE5E5EA),
        heroTopPreview = Color(0xFF141416),
        heroBottomPreview = Color(0xFF000000),
    )
    ThemeMode.REVOLUT_OBSIDIAN -> ThemeVisualSpec(
        mode = mode,
        isDark = true,
        bgPreview = Color(0xFF0E1114),
        surfacePreview = Color(0xFF14181D),
        accentPreview = Color(0xFF0075FF),
        heroTopPreview = Color(0xFF14181D),
        heroBottomPreview = Color(0xFF0E1114),
    )
    ThemeMode.CASH_APP_NEON -> ThemeVisualSpec(
        mode = mode,
        isDark = true,
        bgPreview = Color(0xFF000000),
        surfacePreview = Color(0xFF121212),
        accentPreview = Color(0xFF00D632),
        heroTopPreview = Color(0xFF121212),
        heroBottomPreview = Color(0xFF000000),
    )
    ThemeMode.MONZO_OCEANIC -> ThemeVisualSpec(
        mode = mode,
        isDark = true,
        bgPreview = Color(0xFF0E1927),
        surfacePreview = Color(0xFF14233C),
        accentPreview = Color(0xFFFF4D5A),
        heroTopPreview = Color(0xFF14233C),
        heroBottomPreview = Color(0xFF0E1927),
    )
    ThemeMode.DUBAI_GOLD -> ThemeVisualSpec(
        mode = mode,
        isDark = true,
        bgPreview = Color(0xFF0A0A0C),
        surfacePreview = Color(0xFF14171A),
        accentPreview = Color(0xFFF7C948),
        heroTopPreview = Color(0xFF1B1915),
        heroBottomPreview = Color(0xFF0A0A0C),
    )
    ThemeMode.OBSIDIAN_EMERALD -> ThemeVisualSpec(
        mode = mode,
        isDark = true,
        bgPreview = Color(0xFF060B08),
        surfacePreview = Color(0xFF0B1410),
        accentPreview = Color(0xFF10B981),
        heroTopPreview = Color(0xFF063326),
        heroBottomPreview = Color(0xFF021610),
    )
    ThemeMode.ROYAL_AMETHYST -> ThemeVisualSpec(
        mode = mode,
        isDark = true,
        bgPreview = Color(0xFF080612),
        surfacePreview = Color(0xFF100D22),
        accentPreview = Color(0xFFA78BFA),
        heroTopPreview = Color(0xFF3B0764),
        heroBottomPreview = Color(0xFF120324),
    )
    ThemeMode.NORDIC_SLATE -> ThemeVisualSpec(
        mode = mode,
        isDark = true,
        bgPreview = Color(0xFF060B11),
        surfacePreview = Color(0xFF0C1420),
        accentPreview = Color(0xFF38BDF8),
        heroTopPreview = Color(0xFF0C2B4E),
        heroBottomPreview = Color(0xFF051324),
    )
    ThemeMode.CYBER_GOLD -> ThemeVisualSpec(
        mode = mode,
        isDark = true,
        bgPreview = Color(0xFF09090B),
        surfacePreview = Color(0xFF131316),
        accentPreview = Color(0xFFFFD700),
        heroTopPreview = Color(0xFF2C1E03),
        heroBottomPreview = Color(0xFF0E0901),
    )
}

/** One spacing scale. Anything not on it is a decision someone has to defend. */
object Space {
    val xs = 4.dp
    val s = 8.dp
    val m = 12.dp
    val l = 16.dp
    val xl = 20.dp
    val xxl = 28.dp
    val huge = 40.dp
}

object Radius {
    /** Fully round. Pills, chips, action circles. */
    val pill = 999.dp
    val field = 10.dp
    val card = 12.dp

    /** The container a band of rows sits in, as one object rather than a stack of cards. */
    val group = 14.dp

    /** Only the two bottom corners; the field runs off the top of the screen. */
    val hero = 16.dp
    val sheet = 18.dp
}

/**
 * ANDIS Neobank & Fintech Radius Geometry (Revolut / N26 / Monzo style).
 */
object AndisRadius {
    val sharp = 0.dp
    val xs = 4.dp
    val sm = 8.dp
    val md = 12.dp
    val card = 16.dp
    val cardElevated = 20.dp
    val pill = 999.dp
}

/**
 * ANDIS Neobank Design System Colors — Revolut × N26 × Monzo × Apple Card.
 * Deep obsidian, midnight navy surfaces, electric cyan, emerald, and soft coral.
 */
data class AndisColors(
    val background: Color = Color(0xFF070A0F),
    val backgroundSecondary: Color = Color(0xFF0B1017),
    val surfacePrimary: Color = Color(0xFF0F1622),
    val surfaceElevated: Color = Color(0xFF162030),
    val surfaceSecondary: Color = Color(0xFF162030),
    val border: Color = Color(0xFF1E293B),
    val borderStrong: Color = Color(0xFF334155),
    val borderActive: Color = Color(0xFF38BDF8),
    val textPrimary: Color = Color(0xFFF8FAFC),
    val textSecondary: Color = Color(0xFF94A3B8),
    val textMuted: Color = Color(0xFF64748B),
    val disabled: Color = Color(0xFF475569),
    val accentCyan: Color = Color(0xFF00D2FF),
    val accentLime: Color = Color(0xFF38BDF8), // Electric cyan/refined blue accent
    val emerald: Color = Color(0xFF10B981),   // Income/positive
    val danger: Color = Color(0xFFF43F5E),    // Soft coral/red expense
    val warning: Color = Color(0xFFF59E0B),   // Warm amber
    val ghostLed: Color = Color(0x18F8FAFC),
    val ghostLedDark: Color = Color(0x121E293B),
)

val LocalAndisColors = staticCompositionLocalOf { AndisColors() }

/**
 * Editorial typography scale for ANDIS Financial OS.
 */
object AndisTypography {
    val displayMonumental = TextStyle(
        fontFamily = ModamFigures,
        fontWeight = FontWeight.Black,
        fontSize = 44.sp,
        fontFeatureSettings = TABULAR,
        letterSpacing = (-0.5).sp,
    )
    val displayLarge = TextStyle(
        fontFamily = ModamFigures,
        fontWeight = FontWeight.ExtraBold,
        fontSize = 30.sp,
        fontFeatureSettings = TABULAR,
    )
    val headline = TextStyle(
        fontFamily = Modam,
        fontWeight = FontWeight.Bold,
        fontSize = 18.sp,
    )
    val body = TextStyle(
        fontFamily = Modam,
        fontWeight = FontWeight.Normal,
        fontSize = 14.sp,
    )
    val technical = TextStyle(
        fontFamily = ModamFigures,
        fontWeight = FontWeight.SemiBold,
        fontSize = 10.sp,
        letterSpacing = 0.8.sp,
    )
}

/**
 * Thin hairline separator (1dp) replacing heavy card boundaries.
 */
@Composable
fun HairlineDivider(
    modifier: Modifier = Modifier,
    color: Color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.45f),
    thickness: Dp = 1.dp,
) {
    androidx.compose.foundation.Canvas(
        modifier = modifier
            .fillMaxWidth()
            .height(thickness)
    ) {
        drawLine(
            color = color,
            start = Offset(0f, size.height / 2f),
            end = Offset(size.width, size.height / 2f),
            strokeWidth = size.height
        )
    }
}

object AndisTheme {
    val colors: AndisColors
        @Composable
        get() = LocalAndisColors.current

    val space = Space
    val radius = AndisRadius
    val typography = AndisTypography
}

/**
 * Vanguard Obsidian Design System Tokens (Revolut 10 / Apple Card inspired).
 * Deep obsidian black canvas, graphite surfaces, and hairline borders.
 */
object VanguardTokens {
    val Canvas = Color(0xFF0A0A0C)
    val Surface = Color(0xFF14171A)
    val SurfaceElevated = Color(0xFF1A1E23)
    val BorderHairline = Color(0xFF22272E)
    val BorderStrong = Color(0xFF333B44)
    val TextPrimary = Color(0xFFF8FAFC)
    val TextSecondary = Color(0xFF94A3B8)
    val TextMuted = Color(0xFF64748B)

    // Standardized Radii Scale
    val rXs = 4.dp
    val rSm = 8.dp
    val rMd = 12.dp
    val rLg = 16.dp
    val rXl = 24.dp
    val rPill = 999.dp
}

/**
 * Adaptive Glassmorphism Surface supporting hardware acceleration and subtle micro-gradients.
 * Provides consistent frosted appearance without blurring child text or interactive elements.
 */
@Composable
fun GlassSurface(
    modifier: Modifier = Modifier,
    shape: Shape = RoundedCornerShape(16.dp),
    containerColor: Color = MaterialTheme.colorScheme.surface.copy(alpha = 0.75f),
    borderColor: Color = MaterialTheme.colorScheme.outline.copy(alpha = 0.45f),
    borderWidth: Dp = 1.dp,
    content: @Composable BoxScope.() -> Unit,
) {
    Box(
        modifier = modifier
            .clip(shape)
            .background(
                Brush.verticalGradient(
                    listOf(
                        containerColor.copy(alpha = 0.88f),
                        containerColor.copy(alpha = 0.72f),
                    )
                ),
                shape
            )
            .border(borderWidth, borderColor, shape),
        content = content
    )
}

/**
 * Material 3's expressive easing set. Everything that enters decelerates hard from an
 * already-visible state; everything that leaves accelerates away.
 */
object Motion {
    val enter = CubicBezierEasing(0.05f, 0.7f, 0.1f, 1f)
    val exit = CubicBezierEasing(0.3f, 0f, 0.8f, 0.15f)

    const val fast = 180
    const val medium = 340
}

/**
 * Every role is set on purpose. Leaving the container roles unset is what makes an app look
 * unfinished: Material's default baseline is violet, so dialogs, sheets and text fields come
 * out mauve no matter how carefully `primary` was chosen.
 */
private val LightColors = lightColorScheme(
    primary = Color(0xFF0F172A),            // Slate 900 — prestigious authoritative dark slate
    onPrimary = Color.White,
    primaryContainer = Color(0xFFE2E8F0),   // Slate 200
    onPrimaryContainer = Color(0xFF0F172A),

    secondary = Color(0xFF92400E),          // Amber 600 — classic warm gold
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFFEF3C7), // Amber 100
    onSecondaryContainer = Color(0xFF78350F),

    // Gain, and only gain.
    tertiary = Color(0xFF047857),
    onTertiary = Color.White,
    tertiaryContainer = Color(0xFFA7F3D0),
    onTertiaryContainer = Color(0xFF064E3B),

    background = Color(0xFFF8FAFC),         // Slate 50 — clean modern light canvas
    onBackground = Color(0xFF0F172A),

    surface = Color.White,
    onSurface = Color(0xFF0F172A),
    surfaceVariant = Color(0xFFF1F5F9),     // Slate 100
    onSurfaceVariant = Color(0xFF64748B),   // Slate 500 — clean neutral secondary text, no green tint

    surfaceContainerLowest = Color.White,
    surfaceContainerLow = Color(0xFFF8FAFC),
    surfaceContainer = Color(0xFFF1F5F9),
    surfaceContainerHigh = Color.White,     // dialogs and sheets
    surfaceContainerHighest = Color(0xFFE2E8F0),

    outline = Color(0xFFCBD5E1),            // Slate 300
    outlineVariant = Color(0xFFE2E8F0),     // Slate 200

    error = Color(0xFFDC2626),
    onError = Color.White,
    errorContainer = Color(0xFFFEE2E2),
    onErrorContainer = Color(0xFF7F1D1D),
    scrim = Color(0xFF000000),
)

private val DarkColors = darkColorScheme(
    primary = Color(0xFF38BDF8),            // Electric cyan / refined blue
    onPrimary = VanguardTokens.Canvas,
    primaryContainer = VanguardTokens.Surface,
    onPrimaryContainer = Color(0xFF38BDF8),

    secondary = Color(0xFFF8FAFC),
    onSecondary = VanguardTokens.Canvas,
    secondaryContainer = VanguardTokens.SurfaceElevated,
    onSecondaryContainer = Color(0xFFF8FAFC),

    // Gain stays emerald green
    tertiary = Color(0xFF10B981),
    onTertiary = VanguardTokens.Canvas,
    tertiaryContainer = VanguardTokens.Surface,
    onTertiaryContainer = Color(0xFF10B981),

    background = VanguardTokens.Canvas,
    onBackground = VanguardTokens.TextPrimary,

    surface = VanguardTokens.Surface,
    onSurface = VanguardTokens.TextPrimary,
    surfaceVariant = VanguardTokens.SurfaceElevated,
    onSurfaceVariant = VanguardTokens.TextSecondary,

    surfaceContainerLowest = VanguardTokens.Canvas,
    surfaceContainerLow = Color(0xFF0E1114),
    surfaceContainer = VanguardTokens.Surface,
    surfaceContainerHigh = VanguardTokens.SurfaceElevated,
    surfaceContainerHighest = VanguardTokens.BorderHairline,

    outline = VanguardTokens.BorderHairline,
    outlineVariant = VanguardTokens.BorderStrong,

    error = Color(0xFFF43F5E),
    onError = VanguardTokens.Canvas,
    errorContainer = Color(0xFF2A0F17),
    onErrorContainer = Color(0xFFFDA4AF),
    scrim = Color(0xFF000000),
)

val AmoledDarkColors = darkColorScheme(
    primary = Color(0xFFD4FF00),
    onPrimary = Color(0xFF000000),
    primaryContainer = Color(0xFF0A0D0F),
    onPrimaryContainer = Color(0xFFD4FF00),

    secondary = Color(0xFFF3F4F4),
    onSecondary = Color(0xFF000000),
    secondaryContainer = Color(0xFF0A0D0F),
    onSecondaryContainer = Color(0xFFF3F4F4),

    tertiary = Color(0xFFD4FF00),
    onTertiary = Color(0xFF000000),
    tertiaryContainer = Color(0xFF0A0D0F),
    onTertiaryContainer = Color(0xFFD4FF00),

    background = Color(0xFF000000),
    onBackground = Color(0xFFF3F4F4),

    surface = Color(0xFF000000),
    onSurface = Color(0xFFF3F4F4),
    surfaceVariant = Color(0xFF0A0D0F),
    onSurfaceVariant = Color(0xFFA0A7AB),

    surfaceContainerLowest = Color(0xFF000000),
    surfaceContainerLow = Color(0xFF060708),
    surfaceContainer = Color(0xFF0A0D0F),
    surfaceContainerHigh = Color(0xFF0E1214),
    surfaceContainerHighest = Color(0xFF1C2327),

    outline = Color(0xFF1C2327),
    outlineVariant = Color(0xFF283136),

    error = Color(0xFFFF4D4D),
    onError = Color(0xFF000000),
    errorContainer = Color(0xFF2A0F0F),
    onErrorContainer = Color(0xFFFF8B85),
    scrim = Color(0xFF000000),
)

val ObsidianEmeraldColors = darkColorScheme(
    primary = Color(0xFF10B981),
    onPrimary = Color(0xFF042F20),
    primaryContainer = Color(0xFF064E3B),
    onPrimaryContainer = Color(0xFFA7F3D0),

    secondary = Color(0xFF34D399),
    onSecondary = Color(0xFF022B1E),
    secondaryContainer = Color(0xFF065F46),
    onSecondaryContainer = Color(0xFFD1FAE5),

    tertiary = Color(0xFFFBBF24),
    onTertiary = Color(0xFF3B2400),
    tertiaryContainer = Color(0xFF6B4500),
    onTertiaryContainer = Color(0xFFFDE68A),

    background = Color(0xFF060B08),
    onBackground = Color(0xFFE6F4EA),

    surface = Color(0xFF0B1410),
    onSurface = Color(0xFFE6F4EA),
    surfaceVariant = Color(0xFF13221C),
    onSurfaceVariant = Color(0xFFA3C4B8),

    surfaceContainerLowest = Color(0xFF040806),
    surfaceContainerLow = Color(0xFF08100C),
    surfaceContainer = Color(0xFF0E1A15),
    surfaceContainerHigh = Color(0xFF152620),
    surfaceContainerHighest = Color(0xFF1C3129),

    outline = Color(0xFF2E594A),
    outlineVariant = Color(0xFF1A352B),

    error = Color(0xFFF87171),
    onError = Color(0xFF450A0A),
    errorContainer = Color(0xFF7F1D1D),
    onErrorContainer = Color(0xFFFECACA),
    scrim = Color(0xFF000000),
)

val RoyalAmethystColors = darkColorScheme(
    primary = Color(0xFFA78BFA),
    onPrimary = Color(0xFF2E1065),
    primaryContainer = Color(0xFF4C1D95),
    onPrimaryContainer = Color(0xFFDDD6FE),

    secondary = Color(0xFFFBBF24),
    onSecondary = Color(0xFF451A03),
    secondaryContainer = Color(0xFF78350F),
    onSecondaryContainer = Color(0xFFFEF3C7),

    tertiary = Color(0xFF34D399),
    onTertiary = Color(0xFF022C22),
    tertiaryContainer = Color(0xFF065F46),
    onTertiaryContainer = Color(0xFFA7F3D0),

    background = Color(0xFF080612),
    onBackground = Color(0xFFEDE9FE),

    surface = Color(0xFF100D22),
    onSurface = Color(0xFFEDE9FE),
    surfaceVariant = Color(0xFF1C1738),
    onSurfaceVariant = Color(0xFFC4B5FD),

    surfaceContainerLowest = Color(0xFF05040B),
    surfaceContainerLow = Color(0xFF0D0A1C),
    surfaceContainer = Color(0xFF16122E),
    surfaceContainerHigh = Color(0xFF211C42),
    surfaceContainerHighest = Color(0xFF2B2554),

    outline = Color(0xFF5B4D8A),
    outlineVariant = Color(0xFF2E264E),

    error = Color(0xFFF87171),
    onError = Color(0xFF450A0A),
    errorContainer = Color(0xFF7F1D1D),
    onErrorContainer = Color(0xFFFECACA),
    scrim = Color(0xFF000000),
)

val NordicSlateColors = darkColorScheme(
    primary = Color(0xFF38BDF8),
    onPrimary = Color(0xFF082F49),
    primaryContainer = Color(0xFF0369A1),
    onPrimaryContainer = Color(0xFFBAE6FD),

    secondary = Color(0xFF818CF8),
    onSecondary = Color(0xFF1E1B4B),
    secondaryContainer = Color(0xFF3730A3),
    onSecondaryContainer = Color(0xFFC7D2FE),

    tertiary = Color(0xFF34D399),
    onTertiary = Color(0xFF022C22),
    tertiaryContainer = Color(0xFF065F46),
    onTertiaryContainer = Color(0xFFA7F3D0),

    background = Color(0xFF060B11),
    onBackground = Color(0xFFE0F2FE),

    surface = Color(0xFF0C1420),
    onSurface = Color(0xFFE0F2FE),
    surfaceVariant = Color(0xFF142032),
    onSurfaceVariant = Color(0xFF94A3B8),

    surfaceContainerLowest = Color(0xFF04070B),
    surfaceContainerLow = Color(0xFF080F18),
    surfaceContainer = Color(0xFF101B2B),
    surfaceContainerHigh = Color(0xFF18283E),
    surfaceContainerHighest = Color(0xFF213652),

    outline = Color(0xFF334E68),
    outlineVariant = Color(0xFF1C2D3D),

    error = Color(0xFFF87171),
    onError = Color(0xFF450A0A),
    errorContainer = Color(0xFF7F1D1D),
    onErrorContainer = Color(0xFFFECACA),
    scrim = Color(0xFF000000),
)

val CyberGoldColors = darkColorScheme(
    primary = Color(0xFFFACC15),
    onPrimary = Color(0xFF3A2E00),
    primaryContainer = Color(0xFF715B00),
    onPrimaryContainer = Color(0xFFFEF08A),

    secondary = Color(0xFFE2E8F0),
    onSecondary = Color(0xFF1E293B),
    secondaryContainer = Color(0xFF334155),
    onSecondaryContainer = Color(0xFFF1F5F9),

    tertiary = Color(0xFF38BDF8),
    onTertiary = Color(0xFF082F49),
    tertiaryContainer = Color(0xFF075985),
    onTertiaryContainer = Color(0xFFBAE6FD),

    background = Color(0xFF09090B),
    onBackground = Color(0xFFFAFAFA),

    surface = Color(0xFF131316),
    onSurface = Color(0xFFFAFAFA),
    surfaceVariant = Color(0xFF222227),
    onSurfaceVariant = Color(0xFFA1A1AA),

    surfaceContainerLowest = Color(0xFF060608),
    surfaceContainerLow = Color(0xFF0E0E10),
    surfaceContainer = Color(0xFF18181C),
    surfaceContainerHigh = Color(0xFF222228),
    surfaceContainerHighest = Color(0xFF2C2C33),

    outline = Color(0xFF52525B),
    outlineVariant = Color(0xFF27272A),

    error = Color(0xFFF87171),
    onError = Color(0xFF450A0A),
    errorContainer = Color(0xFF7F1D1D),
    onErrorContainer = Color(0xFFFECACA),
    scrim = Color(0xFF000000),
)

val AppleTitaniumColors = darkColorScheme(
    primary = Color(0xFFE5E5EA),
    onPrimary = Color(0xFF000000),
    primaryContainer = Color(0xFF1C1C1E),
    onPrimaryContainer = Color(0xFFFFFFFF),

    secondary = Color(0xFFD1D1D6),
    onSecondary = Color(0xFF000000),
    secondaryContainer = Color(0xFF2C2C2E),
    onSecondaryContainer = Color(0xFFFFFFFF),

    tertiary = Color(0xFF30D158),
    onTertiary = Color(0xFF000000),
    tertiaryContainer = Color(0xFF1C1C1E),
    onTertiaryContainer = Color(0xFF30D158),

    background = Color(0xFF000000),
    onBackground = Color(0xFFFFFFFF),

    surface = Color(0xFF141416),
    onSurface = Color(0xFFFFFFFF),
    surfaceVariant = Color(0xFF1C1C1E),
    onSurfaceVariant = Color(0xFF8E8E93),

    surfaceContainerLowest = Color(0xFF000000),
    surfaceContainerLow = Color(0xFF0C0C0E),
    surfaceContainer = Color(0xFF141416),
    surfaceContainerHigh = Color(0xFF1C1C1E),
    surfaceContainerHighest = Color(0xFF2C2C2E),

    outline = Color(0xFF2C2C2E),
    outlineVariant = Color(0xFF3A3A3C),

    error = Color(0xFFFF453A),
    onError = Color(0xFF000000),
)

val RevolutObsidianColors = darkColorScheme(
    // 4.21:1 against white was below AA; 0066E6 keeps the Revolut blue and clears 5.1:1.
    primary = Color(0xFF5AA9FF),
    onPrimary = Color(0xFF0A0F14),
    primaryContainer = Color(0xFF14181D),
    onPrimaryContainer = Color(0xFF38BDF8),

    secondary = Color(0xFFF8FAFC),
    onSecondary = Color(0xFF0E1114),
    secondaryContainer = Color(0xFF1B2026),
    onSecondaryContainer = Color(0xFFF8FAFC),

    tertiary = Color(0xFF10B981),
    onTertiary = Color(0xFF0E1114),
    tertiaryContainer = Color(0xFF14181D),
    onTertiaryContainer = Color(0xFF10B981),

    background = Color(0xFF0E1114),
    onBackground = Color(0xFFF8FAFC),

    surface = Color(0xFF14181D),
    onSurface = Color(0xFFF8FAFC),
    surfaceVariant = Color(0xFF1B2026),
    onSurfaceVariant = Color(0xFF94A3B8),

    surfaceContainerLowest = Color(0xFF0A0C0E),
    surfaceContainerLow = Color(0xFF0E1114),
    surfaceContainer = Color(0xFF14181D),
    surfaceContainerHigh = Color(0xFF1B2026),
    surfaceContainerHighest = Color(0xFF262D35),

    outline = Color(0xFF262D35),
    outlineVariant = Color(0xFF363E48),

    error = Color(0xFFF43F5E),
    onError = Color(0xFFFFFFFF),
)

val CashAppNeonColors = darkColorScheme(
    primary = Color(0xFF00D632),
    onPrimary = Color(0xFF000000),
    primaryContainer = Color(0xFF121212),
    onPrimaryContainer = Color(0xFF00D632),

    secondary = Color(0xFFFFFFFF),
    onSecondary = Color(0xFF000000),
    secondaryContainer = Color(0xFF1E1E1E),
    onSecondaryContainer = Color(0xFFFFFFFF),

    tertiary = Color(0xFF00D632),
    onTertiary = Color(0xFF000000),
    tertiaryContainer = Color(0xFF121212),
    onTertiaryContainer = Color(0xFF00D632),

    background = Color(0xFF000000),
    onBackground = Color(0xFFFFFFFF),

    surface = Color(0xFF121212),
    onSurface = Color(0xFFFFFFFF),
    surfaceVariant = Color(0xFF1E1E1E),
    onSurfaceVariant = Color(0xFFAAAAAA),

    surfaceContainerLowest = Color(0xFF000000),
    surfaceContainerLow = Color(0xFF0A0A0A),
    surfaceContainer = Color(0xFF121212),
    surfaceContainerHigh = Color(0xFF1E1E1E),
    surfaceContainerHighest = Color(0xFF2A2A2A),

    outline = Color(0xFF2A2A2A),
    outlineVariant = Color(0xFF383838),

    error = Color(0xFFFF3B30),
    onError = Color(0xFF000000),
)

val MonzoOceanicColors = darkColorScheme(
    // bright coral + white read 3.25:1; ink on the same coral clears 5.6:1.
    primary = Color(0xFFFF4D5A),
    onPrimary = Color(0xFF1A0A0C),
    primaryContainer = Color(0xFF14233C),
    onPrimaryContainer = Color(0xFFFF4D5A),

    secondary = Color(0xFFF8FAFC),
    onSecondary = Color(0xFF0E1927),
    secondaryContainer = Color(0xFF1B2F50),
    onSecondaryContainer = Color(0xFFF8FAFC),

    tertiary = Color(0xFF00D2A0),
    onTertiary = Color(0xFF0E1927),
    tertiaryContainer = Color(0xFF14233C),
    onTertiaryContainer = Color(0xFF00D2A0),

    background = Color(0xFF0E1927),
    onBackground = Color(0xFFF8FAFC),

    surface = Color(0xFF14233C),
    onSurface = Color(0xFFF8FAFC),
    surfaceVariant = Color(0xFF1B2F50),
    onSurfaceVariant = Color(0xFF8FA5C4),

    surfaceContainerLowest = Color(0xFF080F18),
    surfaceContainerLow = Color(0xFF0E1927),
    surfaceContainer = Color(0xFF14233C),
    surfaceContainerHigh = Color(0xFF1B2F50),
    surfaceContainerHighest = Color(0xFF233D66),

    outline = Color(0xFF233D66),
    outlineVariant = Color(0xFF325285),

    error = Color(0xFFFF4D5A),
    onError = Color(0xFFFFFFFF),
)

val DubaiGoldColors = darkColorScheme(
    primary = Color(0xFFF7C948),
    onPrimary = Color(0xFF1B1915),
    primaryContainer = Color(0xFF1F1C16),
    onPrimaryContainer = Color(0xFFF7C948),

    secondary = Color(0xFFFAF9F6),
    onSecondary = Color(0xFF0A0A0C),
    secondaryContainer = Color(0xFF26221A),
    onSecondaryContainer = Color(0xFFFAF9F6),

    tertiary = Color(0xFF10B981),
    onTertiary = Color(0xFF0A0A0C),
    tertiaryContainer = Color(0xFF14171A),
    onTertiaryContainer = Color(0xFF10B981),

    background = Color(0xFF0A0A0C),
    onBackground = Color(0xFFFAF9F6),

    surface = Color(0xFF14171A),
    onSurface = Color(0xFFFAF9F6),
    surfaceVariant = Color(0xFF1E2226),
    onSurfaceVariant = Color(0xFF9E998E),

    surfaceContainerLowest = Color(0xFF060708),
    surfaceContainerLow = Color(0xFF0A0A0C),
    surfaceContainer = Color(0xFF14171A),
    surfaceContainerHigh = Color(0xFF1E2226),
    surfaceContainerHighest = Color(0xFF2A2822),

    outline = Color(0xFF2A2822),
    outlineVariant = Color(0xFF423D33),

    error = Color(0xFFFF6B6B),
    onError = Color(0xFF0A0A0C),
)

val AppleSilverColors = lightColorScheme(
    primary = Color(0xFF0066CC),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFDCEBFF),
    onPrimaryContainer = Color(0xFF003366),

    secondary = Color(0xFF1D1D1F),
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFE5E5EA),
    onSecondaryContainer = Color(0xFF1D1D1F),

    tertiary = Color(0xFF1E7A3C),
    onTertiary = Color.White,
    tertiaryContainer = Color(0xFFD1F2D9),
    onTertiaryContainer = Color(0xFF0C511F),

    background = Color(0xFFF5F5F7),
    onBackground = Color(0xFF1D1D1F),

    surface = Color.White,
    onSurface = Color(0xFF1D1D1F),
    surfaceVariant = Color(0xFFE8E8ED),
    onSurfaceVariant = Color(0xFF6E6E73),

    surfaceContainerLowest = Color.White,
    surfaceContainerLow = Color(0xFFFBFBFD),
    surfaceContainer = Color(0xFFF5F5F7),
    surfaceContainerHigh = Color.White,
    surfaceContainerHighest = Color(0xFFE5E5EA),

    outline = Color(0xFFD2D2D7),
    outlineVariant = Color(0xFFE5E5EA),

    error = Color(0xFFD70015),
    onError = Color.White,
)

val NordicFrostColors = lightColorScheme(
    // 4.10:1 with white was below AA; sky-700 clears 5.6:1.
    primary = Color(0xFF0369A1),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFBAE6FD),
    onPrimaryContainer = Color(0xFF075985),

    secondary = Color(0xFF475569),
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFE2E8F0),
    onSecondaryContainer = Color(0xFF0F172A),

    tertiary = Color(0xFF047857),
    onTertiary = Color.White,
    tertiaryContainer = Color(0xFFA7F3D0),
    onTertiaryContainer = Color(0xFF065F46),

    background = Color(0xFFF0F7FF),
    onBackground = Color(0xFF0F172A),

    surface = Color.White,
    onSurface = Color(0xFF0F172A),
    surfaceVariant = Color(0xFFE0F2FE),
    onSurfaceVariant = Color(0xFF475569),

    surfaceContainerLowest = Color.White,
    surfaceContainerLow = Color(0xFFF8FAFC),
    surfaceContainer = Color(0xFFF0F7FF),
    surfaceContainerHigh = Color.White,
    surfaceContainerHighest = Color(0xFFBAE6FD),

    outline = Color(0xFFBAE6FD),
    outlineVariant = Color(0xFFE2E8F0),

    error = Color(0xFFB91C1C),
    onError = Color.White,
)

val ChampagneGoldColors = lightColorScheme(
    // amber-600 with white read 3.19:1; amber-700 clears 5.0:1.
    primary = Color(0xFFB45309),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFFDE68A),
    onPrimaryContainer = Color(0xFF78350F),

    secondary = Color(0xFF57534E),
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFE7E0D6),
    onSecondaryContainer = Color(0xFF1C1917),

    tertiary = Color(0xFF047857),
    onTertiary = Color.White,
    tertiaryContainer = Color(0xFFA7F3D0),
    onTertiaryContainer = Color(0xFF064E3B),

    background = Color(0xFFFAF8F5),
    onBackground = Color(0xFF1C1917),

    surface = Color.White,
    onSurface = Color(0xFF1C1917),
    surfaceVariant = Color(0xFFF5EFEB),
    onSurfaceVariant = Color(0xFF57534E),

    surfaceContainerLowest = Color.White,
    surfaceContainerLow = Color(0xFFFCFBF9),
    surfaceContainer = Color(0xFFFAF8F5),
    surfaceContainerHigh = Color.White,
    surfaceContainerHighest = Color(0xFFE7E0D6),

    outline = Color(0xFFDDD4C5),
    outlineVariant = Color(0xFFE7E0D6),

    error = Color(0xFFDC2626),
    onError = Color.White,
)

val PersianMintColors = lightColorScheme(
    // emerald-600 with white read 3.77:1; emerald-700 clears 5.3:1.
    primary = Color(0xFF047857),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFA7F3D0),
    onPrimaryContainer = Color(0xFF064E3B),

    secondary = Color(0xFF0F766E),
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFCCFBF1),
    onSecondaryContainer = Color(0xFF115E59),

    tertiary = Color(0xFF0369A1),
    onTertiary = Color.White,
    tertiaryContainer = Color(0xFFBAE6FD),
    onTertiaryContainer = Color(0xFF0369A1),

    background = Color(0xFFF0FDF4),
    onBackground = Color(0xFF064E3B),

    surface = Color.White,
    onSurface = Color(0xFF064E3B),
    surfaceVariant = Color(0xFFDCFCE7),
    onSurfaceVariant = Color(0xFF374151),

    surfaceContainerLowest = Color.White,
    surfaceContainerLow = Color(0xFFF7FEE7),
    surfaceContainer = Color(0xFFF0FDF4),
    surfaceContainerHigh = Color.White,
    surfaceContainerHighest = Color(0xFFBBF7D0),

    outline = Color(0xFFA7F3D0),
    outlineVariant = Color(0xFFE5E7EB),

    error = Color(0xFFBE123C),
    onError = Color.White,
)

val RoseQuartzColors = lightColorScheme(
    primary = Color(0xFFBE123C),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFFECDD3),
    onPrimaryContainer = Color(0xFF881337),

    secondary = Color(0xFF9333EA),
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFF3E8FF),
    onSecondaryContainer = Color(0xFF581C87),

    tertiary = Color(0xFF047857),
    onTertiary = Color.White,
    tertiaryContainer = Color(0xFFA7F3D0),
    onTertiaryContainer = Color(0xFF064E3B),

    background = Color(0xFFFFF1F2),
    onBackground = Color(0xFF4C0519),

    surface = Color.White,
    onSurface = Color(0xFF4C0519),
    surfaceVariant = Color(0xFFFFE4E6),
    onSurfaceVariant = Color(0xFF701A75),

    surfaceContainerLowest = Color.White,
    surfaceContainerLow = Color(0xFFFFF7F8),
    surfaceContainer = Color(0xFFFFF1F2),
    surfaceContainerHigh = Color.White,
    surfaceContainerHighest = Color(0xFFFECDD3),

    outline = Color(0xFFFECDD3),
    outlineVariant = Color(0xFFFCE7F3),

    error = Color(0xFFBE123C),
    onError = Color.White,
)

/**
 * True when the current palette's page is light. Every money colour follows this one switch so a
 * palette can never make a figure unreadable: the dark-tuned green that used to be hardcoded read
 * 3:1 on paper and 5.8:1 on ink — legible in exactly one of the two.
 */
@Composable
fun isLightPage(): Boolean = MaterialTheme.colorScheme.background.luminance() > 0.5f

/** Income/positive money: deep green on paper, mint on ink. */
@Composable
fun incomeColor(): Color = if (isLightPage()) Color(0xFF167A44) else Color(0xFF5BE39A)

/** Amber warning (a cap approaching its limit): dark ochre on paper, warm amber on ink. */
@Composable
fun warningColor(): Color = if (isLightPage()) Color(0xFF8A5A00) else Color(0xFFFBBF24)

/** Gold accent for gold holdings: dark gold on paper, the classic gold on ink. */
@Composable
fun goldMoneyColor(): Color = if (isLightPage()) Color(0xFF7A5E12) else Color(0xFFF7C948)

fun getColorScheme(mode: ThemeMode, systemDark: Boolean = false): androidx.compose.material3.ColorScheme = when (mode) {
    ThemeMode.SYSTEM -> if (systemDark) DarkColors else LightColors
    ThemeMode.ANDIS_TERMINAL -> DarkColors
    ThemeMode.LIGHT -> LightColors
    ThemeMode.DARK -> DarkColors
    ThemeMode.AMOLED -> AmoledDarkColors
    ThemeMode.APPLE_TITANIUM -> AppleTitaniumColors
    ThemeMode.APPLE_SILVER -> AppleSilverColors
    ThemeMode.REVOLUT_OBSIDIAN -> RevolutObsidianColors
    ThemeMode.NORDIC_FROST -> NordicFrostColors
    ThemeMode.CHAMPAGNE_GOLD -> ChampagneGoldColors
    ThemeMode.PERSIAN_MINT -> PersianMintColors
    ThemeMode.ROSE_QUARTZ -> RoseQuartzColors
    ThemeMode.CASH_APP_NEON -> CashAppNeonColors
    ThemeMode.MONZO_OCEANIC -> MonzoOceanicColors
    ThemeMode.DUBAI_GOLD -> DubaiGoldColors
    ThemeMode.OBSIDIAN_EMERALD -> ObsidianEmeraldColors
    ThemeMode.ROYAL_AMETHYST -> RoyalAmethystColors
    ThemeMode.NORDIC_SLATE -> NordicSlateColors
    ThemeMode.CYBER_GOLD -> CyberGoldColors
}

fun getAndisColors(colorScheme: androidx.compose.material3.ColorScheme, isDark: Boolean, heroPalette: HeroPalette): AndisColors =
    if (isDark) {
        AndisColors(
            background = colorScheme.background,
            backgroundSecondary = colorScheme.surfaceContainerLow,
            surfacePrimary = colorScheme.surface,
            surfaceElevated = colorScheme.surfaceVariant,
            surfaceSecondary = colorScheme.surfaceContainerHigh,
            border = colorScheme.outline,
            borderStrong = colorScheme.outlineVariant,
            borderActive = colorScheme.primary,
            textPrimary = colorScheme.onSurface,
            textSecondary = colorScheme.onSurfaceVariant,
            textMuted = colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
            disabled = colorScheme.onSurface.copy(alpha = 0.38f),
            accentCyan = heroPalette.gold,
            accentLime = heroPalette.gold,
            emerald = Color(0xFF10B981),
            danger = colorScheme.error,
            warning = Color(0xFFF59E0B),
            ghostLed = Color(0x18F8FAFC),
            ghostLedDark = Color(0x121E293B),
        )
    } else {
        AndisColors(
            background = colorScheme.background,
            backgroundSecondary = colorScheme.surfaceContainerLow,
            surfacePrimary = colorScheme.surface,
            surfaceElevated = colorScheme.surfaceVariant,
            surfaceSecondary = colorScheme.surfaceContainerHigh,
            border = colorScheme.outlineVariant,
            borderStrong = colorScheme.outline,
            borderActive = colorScheme.primary,
            textPrimary = colorScheme.onSurface,
            textSecondary = colorScheme.onSurfaceVariant,
            textMuted = colorScheme.onSurfaceVariant.copy(alpha = 0.75f),
            disabled = colorScheme.onSurface.copy(alpha = 0.38f),
            accentCyan = heroPalette.gold,
            accentLime = heroPalette.gold,
            emerald = Color(0xFF10B981),
            danger = colorScheme.error,
            warning = Color(0xFFF59E0B),
            ghostLed = Color(0x0A000000),
            ghostLedDark = Color(0x14000000),
        )
    }

fun getHeroPalette(mode: ThemeMode, systemDark: Boolean = false): HeroPalette = when (mode) {
    ThemeMode.SYSTEM -> if (systemDark) {
        HeroPalette(
            top = Color(0xFF060708),
            bottom = Color(0xFF0A0D0F),
            gold = Color(0xFFD4FF00),
            muted = Color(0xFFA0A7AB),
            strong = Color(0xFFF3F4F4),
            mint = Color(0xFFD4FF00),
            warn = Color(0xFFFF4D4D),
            well = Color(0xFF0E1214),
            hairline = Color(0xFF1C2327),
        )
    } else {
        HeroPalette(
            top = Color(0xFFF8FAFC),
            bottom = Color(0xFFEDF2F7),
            gold = Color(0xFFB45309),
            muted = Color(0xFF64748B),
            strong = Color(0xFF0F172A),
            mint = Color(0xFF047857),
            warn = Color(0xFFB91C1C),
            well = Color(0x140F172A),
            hairline = Color(0x1A0F172A),
        )
    }
    ThemeMode.ANDIS_TERMINAL,
    ThemeMode.DARK -> HeroPalette(
        top = Color(0xFF0F1622),
        bottom = Color(0xFF070A0F),
        gold = Color(0xFF38BDF8),
        muted = Color(0xFF94A3B8),
        strong = Color(0xFFF8FAFC),
        mint = Color(0xFF10B981),
        warn = Color(0xFFF43F5E),
        well = Color(0xFF162030),
        hairline = Color(0xFF1E293B),
    )
    ThemeMode.LIGHT -> HeroPalette(
        top = Color(0xFFF8FAFC),
        bottom = Color(0xFFEDF2F7),
        gold = Color(0xFFB45309),
        muted = Color(0xFF64748B),
        strong = Color(0xFF0F172A),
        mint = Color(0xFF047857),
        warn = Color(0xFFB91C1C),
        well = Color(0x140F172A),
        hairline = Color(0x1A0F172A),
    )
    ThemeMode.APPLE_SILVER -> HeroPalette(
        top = Color(0xFFE8EEF5),
        bottom = Color(0xFFF5F5F7),
        gold = Color(0xFF0056B3),
        muted = Color(0xFF6E6E73),
        strong = Color(0xFF1D1D1F),
        mint = Color(0xFF1E7A3C),
        warn = Color(0xFFB91C1C),
        well = Color(0x140071E3),
        hairline = Color(0x200071E3),
    )
    ThemeMode.NORDIC_FROST -> HeroPalette(
        top = Color(0xFFE0F2FE),
        bottom = Color(0xFFF0F7FF),
        gold = Color(0xFF0369A1),
        muted = Color(0xFF475569),
        strong = Color(0xFF0F172A),
        mint = Color(0xFF047857),
        warn = Color(0xFFB91C1C),
        well = Color(0x140284C7),
        hairline = Color(0x200284C7),
    )
    ThemeMode.CHAMPAGNE_GOLD -> HeroPalette(
        top = Color(0xFFF5EFEB),
        bottom = Color(0xFFFAF8F5),
        gold = Color(0xFF92400E),
        muted = Color(0xFF57534E),
        strong = Color(0xFF1C1917),
        mint = Color(0xFF047857),
        warn = Color(0xFFB91C1C),
        well = Color(0x18D97706),
        hairline = Color(0x24D97706),
    )
    ThemeMode.PERSIAN_MINT -> HeroPalette(
        top = Color(0xFFDCFCE7),
        bottom = Color(0xFFF0FDF4),
        gold = Color(0xFF047857),
        muted = Color(0xFF374151),
        strong = Color(0xFF064E3B),
        mint = Color(0xFF047857),
        warn = Color(0xFF9F1239),
        well = Color(0x18059669),
        hairline = Color(0x24059669),
    )
    ThemeMode.ROSE_QUARTZ -> HeroPalette(
        top = Color(0xFFFFE4E6),
        bottom = Color(0xFFFFF1F2),
        gold = Color(0xFFBE123C),
        muted = Color(0xFF701A75),
        strong = Color(0xFF4C0519),
        mint = Color(0xFF047857),
        warn = Color(0xFFBE123C),
        well = Color(0x18E11D48),
        hairline = Color(0x24E11D48),
    )
    ThemeMode.AMOLED -> HeroPalette(
        top = Color(0xFF000000),
        bottom = Color(0xFF050607),
        gold = Color(0xFFD4FF00),
        muted = Color(0xFF8A9298),
        strong = Color(0xFFF1F3F4),
        mint = Color(0xFFD4FF00),
        warn = Color(0xFFFF443A),
        well = Color(0x1FFFFFFF),
        hairline = Color(0x28FFFFFF),
    )
    ThemeMode.OBSIDIAN_EMERALD -> HeroPalette(
        top = Color(0xFF063326),
        bottom = Color(0xFF021610),
        gold = Color(0xFF34D399),
        muted = Color(0xFF6EE7B7).copy(alpha = 0.85f),
        strong = Color(0xFFECFDF5),
        mint = Color(0xFF10B981),
        well = Color(0x2210B981),
        hairline = Color(0x3334D399),
    )
    ThemeMode.ROYAL_AMETHYST -> HeroPalette(
        top = Color(0xFF3B0764),
        bottom = Color(0xFF120324),
        gold = Color(0xFFFDE047),
        muted = Color(0xFFD8B4FE),
        strong = Color(0xFFFAF5FF),
        mint = Color(0xFFA78BFA),
        warn = Color(0xFFFCA5A5),
        well = Color(0x22A855F7),
        hairline = Color(0x33C084FC),
    )
    ThemeMode.NORDIC_SLATE -> HeroPalette(
        top = Color(0xFF0C2B4E),
        bottom = Color(0xFF051324),
        gold = Color(0xFF38BDF8),
        muted = Color(0xFF94A3B8),
        strong = Color(0xFFF0F9FF),
        mint = Color(0xFF38BDF8),
        warn = Color(0xFFFCA5A5),
        well = Color(0x220284C7),
        hairline = Color(0x3338BDF8),
    )
    ThemeMode.APPLE_TITANIUM -> HeroPalette(
        top = Color(0xFF141416),
        bottom = Color(0xFF000000),
        gold = Color(0xFFE5E5EA),
        muted = Color(0xFF8E8E93),
        strong = Color(0xFFFFFFFF),
        mint = Color(0xFF30D158),
        warn = Color(0xFFFF453A),
        well = Color(0x18FFFFFF),
        hairline = Color(0x28FFFFFF),
    )
    ThemeMode.REVOLUT_OBSIDIAN -> HeroPalette(
        top = Color(0xFF14181D),
        bottom = Color(0xFF0E1114),
        gold = Color(0xFF0A84FF),
        muted = Color(0xFF94A3B8),
        strong = Color(0xFFF8FAFC),
        mint = Color(0xFF10B981),
        warn = Color(0xFFF43F5E),
        well = Color(0x1F0075FF),
        hairline = Color(0x330075FF),
    )
    ThemeMode.CASH_APP_NEON -> HeroPalette(
        top = Color(0xFF121212),
        bottom = Color(0xFF000000),
        gold = Color(0xFF00D632),
        muted = Color(0xFFAAAAAA),
        strong = Color(0xFFFFFFFF),
        mint = Color(0xFF00D632),
        warn = Color(0xFFFF3B30),
        well = Color(0x1F00D632),
        hairline = Color(0x3300D632),
    )
    ThemeMode.MONZO_OCEANIC -> HeroPalette(
        top = Color(0xFF14233C),
        bottom = Color(0xFF0E1927),
        gold = Color(0xFFFF4D5A),
        muted = Color(0xFF8FA5C4),
        strong = Color(0xFFF8FAFC),
        mint = Color(0xFF00D2A0),
        warn = Color(0xFFFF4D5A),
        well = Color(0x1FFF4D5A),
        hairline = Color(0x33FF4D5A),
    )
    ThemeMode.DUBAI_GOLD -> HeroPalette(
        top = Color(0xFF1B1915),
        bottom = Color(0xFF0A0A0C),
        gold = Color(0xFFF7C948),
        muted = Color(0xFF9E998E),
        strong = Color(0xFFFAF9F6),
        mint = Color(0xFF10B981),
        warn = Color(0xFFFF6B6B),
        well = Color(0x1FF7C948),
        hairline = Color(0x33F7C948),
    )
    ThemeMode.CYBER_GOLD -> HeroPalette(
        top = Color(0xFF2C1E03),
        bottom = Color(0xFF0E0901),
        gold = Color(0xFFFFD700),
        muted = Color(0xFFEAB308),
        strong = Color(0xFFFEFCE8),
        mint = Color(0xFFFACC15),
        warn = Color(0xFFF87171),
        well = Color(0x22CA8A04),
        hairline = Color(0x33FACC15),
    )
}

@Composable
fun MuchTomanTheme(mode: ThemeMode = ThemeMode.SYSTEM, content: @Composable () -> Unit) {
    val systemDark = isSystemInDarkTheme()
    val colorScheme = getColorScheme(mode, systemDark)
    val base = Typography()
    // Roboto has no real Persian coverage, so every style gets Modam — and zero tracking.
    // Persian is a connected script: Material's default letterSpacing (0.5sp on body, more on
    // labels) opens gaps between glyphs that are supposed to be joined. Tightness, where the
    // design wants it, comes off the width axis instead.
    fun Typography.persian() = Typography(
        displayLarge = displayLarge.copy(fontFamily = ModamFigures, fontWeight = FontWeight.Black, letterSpacing = 0.sp),
        displayMedium = displayMedium.copy(fontFamily = ModamFigures, fontWeight = FontWeight.Black, letterSpacing = 0.sp),
        displaySmall = displaySmall.copy(fontFamily = ModamFigures, fontWeight = FontWeight.ExtraBold, letterSpacing = 0.sp),
        headlineLarge = headlineLarge.copy(fontFamily = Modam, fontWeight = FontWeight.ExtraBold, letterSpacing = 0.sp),
        headlineMedium = headlineMedium.copy(fontFamily = Modam, fontWeight = FontWeight.ExtraBold, letterSpacing = 0.sp),
        headlineSmall = headlineSmall.copy(fontFamily = Modam, fontWeight = FontWeight.Bold, letterSpacing = 0.sp),
        titleLarge = titleLarge.copy(fontFamily = Modam, fontWeight = FontWeight.Bold, letterSpacing = 0.sp),
        titleMedium = titleMedium.copy(fontFamily = Modam, fontWeight = FontWeight.Bold, letterSpacing = 0.sp),
        titleSmall = titleSmall.copy(fontFamily = Modam, fontWeight = FontWeight.SemiBold, letterSpacing = 0.sp),
        bodyLarge = bodyLarge.copy(fontFamily = Modam, letterSpacing = 0.sp),
        bodyMedium = bodyMedium.copy(fontFamily = Modam, letterSpacing = 0.sp),
        bodySmall = bodySmall.copy(fontFamily = Modam, letterSpacing = 0.sp),
        labelLarge = labelLarge.copy(fontFamily = Modam, fontWeight = FontWeight.SemiBold, letterSpacing = 0.sp),
        labelMedium = labelMedium.copy(fontFamily = Modam, fontWeight = FontWeight.SemiBold, letterSpacing = 0.sp),
        labelSmall = labelSmall.copy(fontFamily = Modam, fontWeight = FontWeight.Medium, letterSpacing = 0.sp),
    )

    val heroPalette = getHeroPalette(mode, systemDark)
    val isDark = mode.isDark || (mode == ThemeMode.SYSTEM && systemDark)
    val andisColors = getAndisColors(colorScheme, isDark, heroPalette)

    val rikkaColors = RikkaColors(
        background = colorScheme.background,
        onBackground = colorScheme.onBackground,
        surface = colorScheme.surface,
        surfaceElevated = colorScheme.surfaceVariant,
        onSurface = colorScheme.onSurface,
        primary = colorScheme.primary,
        onPrimary = colorScheme.onPrimary,
        secondary = colorScheme.secondary,
        onSecondary = colorScheme.onSecondary,
        muted = colorScheme.surfaceVariant,
        onMuted = colorScheme.onSurfaceVariant,
        destructive = colorScheme.error,
        onDestructive = colorScheme.onError,
        success = Color(0xFF10B981),
        onSuccess = Color.White,
        warning = Color(0xFFF59E0B),
        onWarning = Color.Black,
        border = colorScheme.outlineVariant,
        ring = colorScheme.primary,
    )

    CompositionLocalProvider(
        LocalHeroPalette provides heroPalette,
        LocalAndisColors provides andisColors,
        LocalRikkaColors provides rikkaColors,
        LocalContentColor provides colorScheme.onSurface,
    ) {
        ProvideFintechHaptics {
            MaterialTheme(
                colorScheme = colorScheme,
                typography = base.persian(),
                content = content,
            )
        }
    }
}

/**
 * ANDIS Financial OS Root Theme wrapper.
 */
@Composable
fun AndisTheme(content: @Composable () -> Unit) {
    MuchTomanTheme(mode = ThemeMode.ANDIS_TERMINAL, content = content)
}
