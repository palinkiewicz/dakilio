package pl.dakil.dakilio.feature.importer.impl

import android.content.ActivityNotFoundException
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager

private const val DAYLIO_PACKAGE = "net.daylio"

// Daylio's "Advanced backup" screen, which has the export button. Not a public API, so it may move.
private const val DAYLIO_BACKUP_ACTIVITY = "net.daylio.activities.BackupAdvancedActivity"

/** Needs the `<queries>` entry in this module's manifest to see Daylio on Android 11+. */
internal fun isDaylioInstalled(context: Context): Boolean = try {
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
internal fun openDaylioBackup(context: Context): Boolean {
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
