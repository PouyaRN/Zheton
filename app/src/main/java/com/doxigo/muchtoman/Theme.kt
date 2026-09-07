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
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.ExperimentalTextApi
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import androidx.compose.ui.text.withStyle

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

/** Theme choices: follow phone, light, dark, AMOLED, or modern studio palettes. */
enum class ThemeMode(val fa: String) {
    SYSTEM("خودکار"),
    LIGHT("روشن"),
    DARK("تیره"),
    AMOLED("آمولد"),
    OBSIDIAN_EMERALD("زمرد"),
    ROYAL_AMETHYST("ارغوانی"),
    NORDIC_SLATE("نوردیک"),
    CYBER_GOLD("سایبرگلد"),
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
    val field = 18.dp
    val card = 24.dp

    /** The container a band of rows sits in, as one object rather than a stack of cards. */
    val group = 28.dp

    /** Only the two bottom corners; the field runs off the top of the screen. */
    val hero = 36.dp
    val sheet = 32.dp
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
    primary = Color(0xFF0A423B),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFD7E8E2),
    onPrimaryContainer = Color(0xFF04231F),

    secondary = Color(0xFF8A5E12),          // gold dark enough to read as text on paper
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFFBE7BE),
    onSecondaryContainer = Color(0xFF33230A),

    // Gain, and only gain. Borrowing primary for "بیشتر" made the same meaning green in
    // light mode and gold in dark — and dark gold is also every button on screen.
    tertiary = Color(0xFF12795A),
    onTertiary = Color.White,
    tertiaryContainer = Color(0xFFBDEEDA),
    onTertiaryContainer = Color(0xFF04291D),

    background = Color(0xFFF4F1E9),         // warm paper, not clinical white
    onBackground = Color(0xFF101A18),

    surface = Color.White,
    onSurface = Color(0xFF101A18),
    surfaceVariant = Color(0xFFE4EAE6),
    onSurfaceVariant = Color(0xFF4A5C57),   // green-tinted muted text, never flat grey

    surfaceContainerLowest = Color.White,
    surfaceContainerLow = Color(0xFFFBF9F4),
    surfaceContainer = Color(0xFFF0EDE5),
    surfaceContainerHigh = Color.White,     // dialogs and sheets
    surfaceContainerHighest = Color(0xFFEAE7DF),

    outline = Color(0xFF6D807B),
    outlineVariant = Color(0xFFD5DCD8),

    error = Color(0xFFB3261E),
    onError = Color.White,
    errorContainer = Color(0xFFFBDAD6),
    onErrorContainer = Color(0xFF410E0B),
    scrim = Color(0xFF06100E),
)

private val DarkColors = darkColorScheme(
    primary = Color(0xFFF2C64D),
    onPrimary = Color(0xFF2C2004),
    primaryContainer = Color(0xFF14453E),
    onPrimaryContainer = Color(0xFFCFE7E1),

    secondary = Color(0xFFE8C77E),
    onSecondary = Color(0xFF2E1F00),
    secondaryContainer = Color(0xFF4A3A12),
    onSecondaryContainer = Color(0xFFF8E8C4),

    // Gain stays green in the dark too.
    tertiary = Color(0xFF3BE0A8),
    onTertiary = Color(0xFF00311F),
    tertiaryContainer = Color(0xFF064832),
    onTertiaryContainer = Color(0xFFB6F2D8),

    background = Color(0xFF070C0B),
    onBackground = Color(0xFFE8EEEC),

    surface = Color(0xFF121A18),
    onSurface = Color(0xFFE8EEEC),
    surfaceVariant = Color(0xFF26302E),
    onSurfaceVariant = Color(0xFFA9BCB7),

    surfaceContainerLowest = Color(0xFF050908),
    surfaceContainerLow = Color(0xFF0E1615),
    surfaceContainer = Color(0xFF141D1B),
    surfaceContainerHigh = Color(0xFF1A2422),
    surfaceContainerHighest = Color(0xFF212C2A),

    outline = Color(0xFF849691),
    outlineVariant = Color(0xFF33403D),

    error = Color(0xFFFFB4AB),
    onError = Color(0xFF5F1410),
    errorContainer = Color(0xFF7A2820),
    onErrorContainer = Color(0xFFFFDAD5),
    scrim = Color(0xFF000000),
)

val AmoledDarkColors = darkColorScheme(
    primary = Color(0xFFF2C64D),
    onPrimary = Color(0xFF2C2004),
    primaryContainer = Color(0xFF0E3832),
    onPrimaryContainer = Color(0xFFCFE7E1),

    secondary = Color(0xFFE8C77E),
    onSecondary = Color(0xFF2E1F00),
    secondaryContainer = Color(0xFF382C0A),
    onSecondaryContainer = Color(0xFFF8E8C4),

    tertiary = Color(0xFF3BE0A8),
    onTertiary = Color(0xFF00311F),
    tertiaryContainer = Color(0xFF043826),
    onTertiaryContainer = Color(0xFFB6F2D8),

    background = Color(0xFF000000),
    onBackground = Color(0xFFF0F5F3),

    surface = Color(0xFF000000),
    onSurface = Color(0xFFF0F5F3),
    surfaceVariant = Color(0xFF161E1C),
    onSurfaceVariant = Color(0xFFB0C4BF),

    surfaceContainerLowest = Color(0xFF000000),
    surfaceContainerLow = Color(0xFF080D0C),
    surfaceContainer = Color(0xFF101715),
    surfaceContainerHigh = Color(0xFF17201E),
    surfaceContainerHighest = Color(0xFF1E2826),

    outline = Color(0xFF8E9F9B),
    outlineVariant = Color(0xFF2A3633),

    error = Color(0xFFFFB4AB),
    onError = Color(0xFF5F1410),
    errorContainer = Color(0xFF7A2820),
    onErrorContainer = Color(0xFFFFDAD5),
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

@Composable
fun MuchTomanTheme(mode: ThemeMode = ThemeMode.SYSTEM, content: @Composable () -> Unit) {
    val systemDark = isSystemInDarkTheme()
    val colorScheme = when (mode) {
        ThemeMode.SYSTEM -> if (systemDark) DarkColors else LightColors
        ThemeMode.LIGHT -> LightColors
        ThemeMode.DARK -> DarkColors
        ThemeMode.AMOLED -> AmoledDarkColors
        ThemeMode.OBSIDIAN_EMERALD -> ObsidianEmeraldColors
        ThemeMode.ROYAL_AMETHYST -> RoyalAmethystColors
        ThemeMode.NORDIC_SLATE -> NordicSlateColors
        ThemeMode.CYBER_GOLD -> CyberGoldColors
    }
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

    val heroPalette = when (mode) {
        ThemeMode.SYSTEM -> if (systemDark) {
            HeroPalette(
                top = Color(0xFF16221E),
                bottom = Color(0xFF090E0C),
                gold = Color(0xFFFBBF24),
                muted = Color(0xFF8FA8A1),
                strong = Color(0xFFF0FDF4),
            )
        } else {
            HeroPalette(
                top = Color(0xFF1E293B),
                bottom = Color(0xFF0F172A),
                gold = Color(0xFFF59E0B),
                muted = Color(0xFF94A3B8),
                strong = Color(0xFFF8FAFC),
            )
        }
        ThemeMode.LIGHT -> HeroPalette(
            top = Color(0xFF1E293B),
            bottom = Color(0xFF0F172A),
            gold = Color(0xFFF59E0B),
            muted = Color(0xFF94A3B8),
            strong = Color(0xFFF8FAFC),
        )
        ThemeMode.DARK -> HeroPalette(
            top = Color(0xFF16221E),
            bottom = Color(0xFF090E0C),
            gold = Color(0xFFFBBF24),
            muted = Color(0xFF8FA8A1),
            strong = Color(0xFFF0FDF4),
        )
        ThemeMode.AMOLED -> HeroPalette(
            top = Color(0xFF000000),
            bottom = Color(0xFF0A0A0A),
            gold = Color(0xFFFFD700),
            muted = Color(0xFF71717A),
            strong = Color(0xFFFFFFFF),
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
            well = Color(0x22A855F7),
            hairline = Color(0x33C084FC),
        )
        ThemeMode.NORDIC_SLATE -> HeroPalette(
            top = Color(0xFF0C2B4E),
            bottom = Color(0xFF051324),
            gold = Color(0xFF38BDF8),
            muted = Color(0xFF94A3B8),
            strong = Color(0xFFF0F9FF),
            well = Color(0x220284C7),
            hairline = Color(0x3338BDF8),
        )
        ThemeMode.CYBER_GOLD -> HeroPalette(
            top = Color(0xFF2C1E03),
            bottom = Color(0xFF0E0901),
            gold = Color(0xFFFFD700),
            muted = Color(0xFFEAB308),
            strong = Color(0xFFFEFCE8),
            well = Color(0x22CA8A04),
            hairline = Color(0x33FACC15),
        )
    }

    CompositionLocalProvider(
        LocalHeroPalette provides heroPalette,
    ) {
        MaterialTheme(
            colorScheme = colorScheme,
            typography = base.persian(),
            content = content,
        )
    }
}
