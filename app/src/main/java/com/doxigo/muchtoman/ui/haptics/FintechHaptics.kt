package com.doxigo.muchtoman.ui.haptics

import android.annotation.SuppressLint
import android.content.Context
import android.os.Build
import android.os.SystemClock
import android.os.VibrationAttributes
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.provider.Settings
import android.view.HapticFeedbackConstants
import android.view.View
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ProvidableCompositionLocal
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView

/**
 * Studio Tactile Haptics interface for ChandToman / Dakh-o-kharj.
 */
interface FintechHaptics {
    /**
     * Tier 1: Micro-pulse (~4ms) for continuous scrubber gestures, chart scrubbing,
     * and slider ticks. Throttled internally to 20ms.
     */
    fun tick()

    /**
     * Tier 2: Sharp mechanical click (~12-15ms) for tab bar selections,
     * toggle switch flips, filter chips, and discrete button presses.
     */
    fun click()

    /**
     * Tier 3: Heavy single thud (~35-45ms) for destructive operations or limit alerts.
     */
    fun heavyClick()

    /**
     * Tier 3: Dual mechanical confirmation pulse for cheque registration,
     * transaction commitments, and biometric success.
     */
    fun doubleTap()
}

/**
 * High-performance, zero-latency Android haptics implementation supporting API 24 through 34+.
 */
class AndroidFintechHaptics(
    private val context: Context,
    private val view: View?,
) : FintechHaptics {

    private val vibrator: Vibrator? = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        val manager = context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as? VibratorManager
        manager?.defaultVibrator
    } else {
        @Suppress("DEPRECATION")
        context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
    }

    // Pre-allocated vibration effects to guarantee 0 GC overhead during 120Hz gesture scrubbing
    private val tickEffect: VibrationEffect? = when {
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q -> {
            VibrationEffect.createPredefined(VibrationEffect.EFFECT_TICK)
        }
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.O -> {
            VibrationEffect.createOneShot(4L, 40)
        }
        else -> null
    }

    private val clickEffect: VibrationEffect? = when {
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q -> {
            VibrationEffect.createPredefined(VibrationEffect.EFFECT_CLICK)
        }
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.O -> {
            VibrationEffect.createOneShot(14L, 140)
        }
        else -> null
    }

    private val heavyEffect: VibrationEffect? = when {
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q -> {
            VibrationEffect.createPredefined(VibrationEffect.EFFECT_HEAVY_CLICK)
        }
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.O -> {
            VibrationEffect.createOneShot(38L, 220)
        }
        else -> null
    }

    private val doubleTapEffect: VibrationEffect? = when {
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q -> {
            VibrationEffect.createPredefined(VibrationEffect.EFFECT_DOUBLE_CLICK)
        }
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.O -> {
            VibrationEffect.createWaveform(
                longArrayOf(0L, 14L, 50L, 18L),
                intArrayOf(0, 160, 0, 230),
                -1,
            )
        }
        else -> null
    }

    // Timestamp tracking for gesture scrubbing throttle
    private var lastTickTimestamp: Long = 0L

    /**
     * Verifies if system haptics are globally enabled by the user.
     */
    private fun isHapticFeedbackEnabled(): Boolean {
        return try {
            Settings.System.getInt(
                context.contentResolver,
                Settings.System.HAPTIC_FEEDBACK_ENABLED,
                1,
            ) != 0
        } catch (_: Exception) {
            true
        }
    }

    @SuppressLint("MissingPermission")
    override fun tick() {
        if (!isHapticFeedbackEnabled()) return

        val now = SystemClock.elapsedRealtime()
        if (now - lastTickTimestamp < 20L) {
            // Drop excessive tick pulses to prevent HAL buffer saturation during rapid drags
            return
        }
        lastTickTimestamp = now

        // 1. Android 14+ (API 34): Use SEGMENT_TICK via View.performHapticFeedback
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE && view != null) {
            view.performHapticFeedback(
                HapticFeedbackConstants.SEGMENT_TICK,
                HapticFeedbackConstants.FLAG_IGNORE_VIEW_SETTING,
            )
            return
        }

        // 2. Android 12+ (API 31): Use hardware composition primitive
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && vibrator != null && vibrator.areAllPrimitivesSupported(VibrationEffect.Composition.PRIMITIVE_TICK)) {
            val composition = VibrationEffect.startComposition()
                .addPrimitive(VibrationEffect.Composition.PRIMITIVE_TICK, 0.6f)
                .compose()
            vibrator.vibrate(
                composition,
                VibrationAttributes.createForUsage(VibrationAttributes.USAGE_TOUCH),
            )
            return
        }

        // 3. Android 10+ (API 29): Predefined EFFECT_TICK
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q && vibrator != null && tickEffect != null) {
            vibrator.vibrate(tickEffect)
            return
        }

        // 4. Android 8.0-9.0 (API 26-28): 4ms OneShot
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && vibrator != null && tickEffect != null) {
            vibrator.vibrate(tickEffect)
            return
        }

        // 5. Fallback for API 24-25 (Android 7.0-7.1)
        if (view != null) {
            view.performHapticFeedback(
                HapticFeedbackConstants.CLOCK_TICK,
                HapticFeedbackConstants.FLAG_IGNORE_VIEW_SETTING,
            )
        } else if (vibrator != null && vibrator.hasVibrator()) {
            @Suppress("DEPRECATION")
            vibrator.vibrate(4L)
        }
    }

    @SuppressLint("MissingPermission")
    override fun click() {
        if (!isHapticFeedbackEnabled()) return

        // 1. Android 12+ (API 31): Primitive Click
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && vibrator != null && vibrator.areAllPrimitivesSupported(VibrationEffect.Composition.PRIMITIVE_CLICK)) {
            val composition = VibrationEffect.startComposition()
                .addPrimitive(VibrationEffect.Composition.PRIMITIVE_CLICK, 0.85f)
                .compose()
            vibrator.vibrate(
                composition,
                VibrationAttributes.createForUsage(VibrationAttributes.USAGE_TOUCH),
            )
            return
        }

        // 2. Android 10+ (API 29) & Android 8+ (API 26)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && vibrator != null && clickEffect != null) {
            vibrator.vibrate(clickEffect)
            return
        }

        // 3. Fallback for API 24-25
        if (view != null) {
            view.performHapticFeedback(
                HapticFeedbackConstants.KEYBOARD_TAP,
                HapticFeedbackConstants.FLAG_IGNORE_VIEW_SETTING,
            )
        } else if (vibrator != null && vibrator.hasVibrator()) {
            @Suppress("DEPRECATION")
            vibrator.vibrate(12L)
        }
    }

    @SuppressLint("MissingPermission")
    override fun heavyClick() {
        if (!isHapticFeedbackEnabled()) return

        // 1. Android 12+ (API 31): Primitive Thud
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && vibrator != null && vibrator.areAllPrimitivesSupported(VibrationEffect.Composition.PRIMITIVE_THUD)) {
            val composition = VibrationEffect.startComposition()
                .addPrimitive(VibrationEffect.Composition.PRIMITIVE_THUD, 1.0f)
                .compose()
            vibrator.vibrate(
                composition,
                VibrationAttributes.createForUsage(VibrationAttributes.USAGE_TOUCH),
            )
            return
        }

        // 2. Android 8.0+ (API 26+)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && vibrator != null && heavyEffect != null) {
            vibrator.vibrate(heavyEffect)
            return
        }

        // 3. Fallback for API 24-25
        if (view != null) {
            view.performHapticFeedback(
                HapticFeedbackConstants.LONG_PRESS,
                HapticFeedbackConstants.FLAG_IGNORE_VIEW_SETTING,
            )
        } else if (vibrator != null && vibrator.hasVibrator()) {
            @Suppress("DEPRECATION")
            vibrator.vibrate(35L)
        }
    }

    @SuppressLint("MissingPermission")
    override fun doubleTap() {
        if (!isHapticFeedbackEnabled()) return

        // 1. Android 12+ (API 31): Composed Dual Click with exact 50ms spacing
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && vibrator != null && vibrator.areAllPrimitivesSupported(VibrationEffect.Composition.PRIMITIVE_CLICK)) {
            val composition = VibrationEffect.startComposition()
                .addPrimitive(VibrationEffect.Composition.PRIMITIVE_CLICK, 0.7f, 0)
                .addPrimitive(VibrationEffect.Composition.PRIMITIVE_CLICK, 1.0f, 50)
                .compose()
            vibrator.vibrate(
                composition,
                VibrationAttributes.createForUsage(VibrationAttributes.USAGE_TOUCH),
            )
            return
        }

        // 2. Android 8.0+ (API 26+) Waveform / Predefined
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && vibrator != null && doubleTapEffect != null) {
            vibrator.vibrate(doubleTapEffect)
            return
        }

        // 3. Fallback for API 24-25
        if (vibrator != null && vibrator.hasVibrator()) {
            @Suppress("DEPRECATION")
            vibrator.vibrate(longArrayOf(0L, 12L, 50L, 18L), -1)
        } else if (view != null) {
            view.performHapticFeedback(
                HapticFeedbackConstants.CONTEXT_CLICK,
                HapticFeedbackConstants.FLAG_IGNORE_VIEW_SETTING,
            )
        }
    }
}

/**
 * CompositionLocal providing access to the current [FintechHaptics] instance.
 * Defaults to a safe no-op implementation so composable previews and tests don't crash.
 */
val LocalFintechHaptics: ProvidableCompositionLocal<FintechHaptics> = staticCompositionLocalOf {
    object : FintechHaptics {
        override fun tick() {}
        override fun click() {}
        override fun heavyClick() {}
        override fun doubleTap() {}
    }
}

/**
 * Provider composable to install [FintechHaptics] into the Compose hierarchy.
 */
@Composable
fun ProvideFintechHaptics(
    content: @Composable () -> Unit,
) {
    val context = LocalContext.current
    val view = LocalView.current
    val haptics = remember(context, view) {
        AndroidFintechHaptics(context = context, view = view)
    }

    androidx.compose.runtime.CompositionLocalProvider(
        LocalFintechHaptics provides haptics,
        content = content,
    )
}
