package com.doxigo.muchtoman.features.reminders

import android.Manifest
import android.app.Activity
import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat

/**
 * The notification permission, asked for at the only moment it makes sense: when she turns
 * reminders on, or presses «اعلان آزمایشی».
 *
 * Android 13's POST_NOTIFICATIONS is not like the SMS permission. Nothing is read, nothing is
 * uploaded; the app is asking for the right to put a sentence on her lock screen. Asking on first
 * launch, before she has any idea what this app is, is how that permission gets denied forever —
 * and on Android the denial is sticky, the dialog stops appearing after a couple of refusals.
 *
 * Three checks, because "allowed" has three separate switches the user can flip:
 *
 *  1. the runtime permission itself (API 33+),
 *  2. the app's notification switch,
 *  3. the reminder channel's own importance (API 26+). A user can mute one channel without
 *     touching the app switch, and `areNotificationsEnabled()` still returns true.
 *
 * The last one is why the channel is created before the permission dialog is requested: a muted
 * channel that does not exist yet cannot be checked, and a fresh install would look fine.
 */
object NotificationPermission {

    /** Whether a reminder posted right now would actually reach the shade. */
    fun isGranted(context: Context): Boolean {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val granted = ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.POST_NOTIFICATIONS,
            ) == PackageManager.PERMISSION_GRANTED
            if (!granted) return false
        }
        if (!NotificationManagerCompat.from(context).areNotificationsEnabled()) return false
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager
            val channel = manager?.getNotificationChannel(ReminderNotifications.CHANNEL_ID)
            if (channel != null && channel.importance == NotificationManager.IMPORTANCE_NONE) {
                return false
            }
        }
        return true
    }

    /**
     * Whether the system would show its own explanation before the dialog.
     *
     * Only used to decide which sentence to put above the button; a "no" here does not mean the
     * user refused permanently, only that Android thinks she does not need the explanation.
     */
    fun shouldShowRationale(activity: Activity): Boolean =
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(
                activity,
                Manifest.permission.POST_NOTIFICATIONS,
            ) != PackageManager.PERMISSION_GRANTED &&
            activity.shouldShowRequestPermissionRationale(
                Manifest.permission.POST_NOTIFICATIONS,
            )

    /** Where to send her when the dialog can no longer be shown (denied twice, or muted). */
    fun openNotificationSettings(context: Context) {
        val intent = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
                .putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
        } else {
            Intent(
                Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                Uri.fromParts("package", context.packageName, null),
            )
        }
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        // A settings page that does not exist on some OEM build is not a crash; the button has
        // simply done all it can.
        runCatching { context.startActivity(intent) }
    }

    /**
     * Ask for the permission, or report the current answer when there is nothing to ask.
     *
     * Kept here rather than in the screen so the next feature that needs an opt-in notification
     * cannot invent a fourth way to spell the same `when (Build.VERSION.SDK_INT)`.
     */
    fun request(launcher: ActivityResultLauncher<String>, onUnavailable: () -> Unit) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            launcher.launch(Manifest.permission.POST_NOTIFICATIONS)
        } else {
            // Below Android 13 the switch is the app's own notification setting; there is no
            // runtime permission to launch.
            onUnavailable()
        }
    }
}

/**
 * A composable wrapper around [NotificationPermission.request] so a screen does not have to
 * repeat the launcher plumbing. Returns a lambda that asks.
 */
@Composable
fun rememberNotificationPermissionRequest(onResult: (Boolean) -> Unit): () -> Unit {
    val context = LocalContext.current
    val launcher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { granted -> onResult(granted) }
    return {
        NotificationPermission.request(launcher) {
            // No runtime permission on this API level: the result is simply what the app switch
            // already says, and the screen can show the banner if it is off.
            onResult(NotificationPermission.isGranted(context))
        }
    }
}
