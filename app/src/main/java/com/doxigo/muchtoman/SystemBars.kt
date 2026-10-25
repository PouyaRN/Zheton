package com.doxigo.muchtoman

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.platform.LocalContext
import androidx.core.view.WindowCompat

/**
 * One policy for the system bars, derived from the palette that is actually drawn.
 *
 * Before this existed, every screen improvised: Home had a luminance check for the hero, the
 * feature screens either called `enableEdgeToEdge()` with no style (polarity then followed the
 * phone's dark mode, not the chosen theme) or never set it at all — which painted white status
 * icons over a cream page in the light palettes.
 */
@Composable
fun ApplySystemBars() {
    val context = LocalContext.current
    val activity = context.findActivity() ?: return
    val lightIcons = MaterialTheme.colorScheme.background.luminance() > 0.5f
    SideEffect {
        WindowCompat.getInsetsController(activity.window, activity.window.decorView).apply {
            isAppearanceLightStatusBars = lightIcons
            isAppearanceLightNavigationBars = lightIcons
        }
    }
}

private tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}
