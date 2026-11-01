package pl.dakil.dakilio.core.ui

import android.content.ActivityNotFoundException
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.LifecycleResumeEffect

private const val DAYLIO_PACKAGE = "net.daylio"

// Daylio's "Advanced backup" screen, which has the export button. Not a public API, so it may move.
private const val DAYLIO_BACKUP_ACTIVITY = "net.daylio.activities.BackupAdvancedActivity"

/** Needs the `<queries>` entry in core:ui's manifest to see Daylio on Android 11+. */
fun isDaylioInstalled(context: Context): Boolean = try {
    context.packageManager.getPackageInfo(DAYLIO_PACKAGE, 0)
    true
} catch (_: PackageManager.NameNotFoundException) {
    false
}

/**
 * Opens Daylio's backup screen, falling back to Daylio's main screen if that one can't be started.
 * Returns false if neither worked.
 *
 * Daylio gets its own task (FLAG_ACTIVITY_NEW_TASK). If its screen sat on top of Dakilio's task, sharing
 * the backup back would not bring that task forward: Android would start a second MainActivity inside
 * Daylio's flow instead of delivering the file to the running one.
 */
fun openDaylioBackup(context: Context): Boolean {
    val backup = Intent()
        .setComponent(ComponentName(DAYLIO_PACKAGE, DAYLIO_BACKUP_ACTIVITY))
        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    if (tryStart(context, backup)) return true
    val launch = context.packageManager.getLaunchIntentForPackage(DAYLIO_PACKAGE) ?: return false
    return tryStart(context, launch.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
}

private fun tryStart(context: Context, intent: Intent): Boolean = try {
    context.startActivity(intent)
    true
} catch (_: ActivityNotFoundException) {
    false
} catch (_: SecurityException) {
    false
}

/**
 * Whether Daylio is installed, re-checked on every resume so installing or removing it meanwhile
 * shows or hides whatever depends on it. Writable, so a failed [openDaylioBackup] can clear it.
 */
@Composable
fun rememberDaylioInstalled(): MutableState<Boolean> {
    val context = LocalContext.current
    val installed = remember { mutableStateOf(isDaylioInstalled(context)) }
    LifecycleResumeEffect(Unit) {
        installed.value = isDaylioInstalled(context)
        onPauseOrDispose { }
    }
    return installed
}
