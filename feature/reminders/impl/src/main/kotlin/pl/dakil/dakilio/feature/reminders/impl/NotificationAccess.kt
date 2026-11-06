package pl.dakil.dakilio.feature.reminders.impl

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LifecycleResumeEffect

/**
 * Whether reminders can show up, and a way to ask. The permission (Android 13+) is asked for only
 * when a reminder is added or switched on: never at startup.
 */
@Stable
internal class NotificationAccess(private val context: Context) {
    internal var launcher: ActivityResultLauncher<String>? = null

    /** Notifications can show: the permission is granted and they're on in the system. */
    var allowed by mutableStateOf(notificationsAllowed(context))
        private set

    internal fun refresh() {
        allowed = notificationsAllowed(context)
    }

    /** Asks for the permission if it's missing; the reminder is saved either way. */
    fun request() {
        if (needsPermission(context)) launcher?.launch(Manifest.permission.POST_NOTIFICATIONS)
    }

    fun openSettings() {
        val intent = Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
            .putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        runCatching { context.startActivity(intent) }
    }
}

@Composable
internal fun rememberNotificationAccess(): NotificationAccess {
    val context = LocalContext.current
    val access = remember(context) { NotificationAccess(context) }
    val permission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { access.refresh() }
    SideEffect { access.launcher = permission }
    // Back from the system settings, or the permission changed elsewhere.
    LifecycleResumeEffect(access) {
        access.refresh()
        onPauseOrDispose { }
    }
    return access
}

private fun needsPermission(context: Context): Boolean =
    Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
        ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED

private fun notificationsAllowed(context: Context): Boolean =
    !needsPermission(context) && NotificationManagerCompat.from(context).areNotificationsEnabled()
