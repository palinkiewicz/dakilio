package pl.dakil.dakilio.feature.reminders.impl

import android.Manifest
import android.app.AlarmManager
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
 * Whether reminders can reach the user, and the ways to fix it. The notification permission (Android
 * 13+) is asked for only when a reminder is added or switched on: never at startup.
 *
 * The system screens open through an ActivityResult launcher, so the app lock treats them like a
 * picker and coming back doesn't lock.
 */
@Stable
internal class ReminderAccess(private val context: Context) {
    internal var permissionLauncher: ActivityResultLauncher<String>? = null
    internal var settingsLauncher: ActivityResultLauncher<Intent>? = null

    /** Notifications can show: the permission is granted and they're on in the system. */
    var notificationsAllowed by mutableStateOf(notificationsAllowed(context))
        private set

    /** Alarms can be exact ("Alarms & reminders", Android 12+; always below), so reminders come on time. */
    var exactTiming by mutableStateOf(exactTimingAllowed(context))
        private set

    internal fun refresh() {
        notificationsAllowed = notificationsAllowed(context)
        exactTiming = exactTimingAllowed(context)
    }

    /** Asks for the permission if it's missing; the reminder is saved either way. */
    fun request() {
        if (needsPermission(context)) permissionLauncher?.launch(Manifest.permission.POST_NOTIFICATIONS)
    }

    fun openNotificationSettings() {
        open(Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName))
    }

    fun openExactTimingSettings() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) return
        open(Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM, packageUri()))
    }

    private fun packageUri() = Uri.fromParts("package", context.packageName, null)

    private fun open(intent: Intent) {
        runCatching { settingsLauncher?.launch(intent) }
    }
}

@Composable
internal fun rememberReminderAccess(): ReminderAccess {
    val context = LocalContext.current
    val access = remember(context) { ReminderAccess(context) }
    val permission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { access.refresh() }
    val settings = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { access.refresh() }
    SideEffect {
        access.permissionLauncher = permission
        access.settingsLauncher = settings
    }
    // Back from the system settings, or something changed elsewhere.
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

private fun exactTimingAllowed(context: Context): Boolean =
    Build.VERSION.SDK_INT < Build.VERSION_CODES.S || context.getSystemService(AlarmManager::class.java).canScheduleExactAlarms()
