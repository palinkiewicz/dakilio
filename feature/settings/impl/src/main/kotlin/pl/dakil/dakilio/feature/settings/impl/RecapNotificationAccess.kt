package pl.dakil.dakilio.feature.settings.impl

import android.Manifest
import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.ManagedActivityResultLauncher
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.ActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.LifecycleResumeEffect
import pl.dakil.dakilio.core.data.repository.ReminderChannels

/**
 * Whether recap notifications can show, and the ways to fix it: the permission (Android 13+, asked
 * only when the switch is turned on), the app's notifications, and the recap channel.
 */
@Stable
internal class RecapNotificationAccess(private val context: Context) {
    internal var permissionLauncher: ManagedActivityResultLauncher<String, Boolean>? = null
    internal var settingsLauncher: ManagedActivityResultLauncher<Intent, ActivityResult>? = null

    var blocked by mutableStateOf(blocked(context))
        private set

    internal fun refresh() {
        blocked = blocked(context)
    }

    /** Asks for the permission if it's missing; the switch stays on either way. */
    fun request() {
        if (needsPermission(context)) permissionLauncher?.launch(Manifest.permission.POST_NOTIFICATIONS)
    }

    /** The recap channel's settings when only it is off, else the app's notification settings. */
    fun openSettings() {
        val appOff = needsPermission(context) || !NotificationManagerCompat.from(context).areNotificationsEnabled()
        val intent = if (appOff) {
            Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
        } else {
            Intent(Settings.ACTION_CHANNEL_NOTIFICATION_SETTINGS).putExtra(Settings.EXTRA_CHANNEL_ID, ReminderChannels.RECAP)
        }
        runCatching { settingsLauncher?.launch(intent.putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)) }
    }
}

@Composable
internal fun rememberRecapNotificationAccess(): RecapNotificationAccess {
    val context = LocalContext.current
    val access = remember(context) { RecapNotificationAccess(context) }
    access.permissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { access.refresh() }
    access.settingsLauncher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { access.refresh() }
    LifecycleResumeEffect(access) {
        access.refresh()
        onPauseOrDispose { }
    }
    return access
}

private fun needsPermission(context: Context): Boolean =
    Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
        ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED

private fun blocked(context: Context): Boolean =
    needsPermission(context) ||
        !NotificationManagerCompat.from(context).areNotificationsEnabled() ||
        context.getSystemService(NotificationManager::class.java).getNotificationChannel(ReminderChannels.RECAP)?.importance == NotificationManager.IMPORTANCE_NONE
