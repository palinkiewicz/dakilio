package pl.dakil.dakilio.core.model

/** How often automatic backups run. */
enum class AutoBackupFrequency(val days: Int) {
    Daily(1),
    Weekly(7),
}

/**
 * Automatic backups: a `.dakilio` file written now and then into a folder the user picked. Off while
 * [folderUri] is null.
 */
data class AutoBackupConfig(
    /** The folder's tree URI (Storage Access Framework), with a persisted read and write grant. */
    val folderUri: String? = null,
    val folderName: String? = null,
    val frequency: AutoBackupFrequency = AutoBackupFrequency.Daily,
    /** Wait for the charger, so a backup never costs battery. */
    val onlyWhileCharging: Boolean = true,
    /** How many automatic backups stay in the folder; older ones are deleted. */
    val keep: Int = DEFAULT_KEEP,
    /** A password is stored (Keystore-encrypted) and every backup is encrypted with it. */
    val hasPassword: Boolean = false,
    /** When the current folder was picked (epoch millis): "overdue" counts from it before the first backup. */
    val enabledAt: Long? = null,
) {
    val isOn: Boolean get() = folderUri != null

    companion object {
        const val DEFAULT_KEEP = 5
        val KEEP_CHOICES = listOf(3, 5, 10)
    }
}

enum class AutoBackupFailure {
    /** The folder was deleted, its storage removed, or its permission taken away. */
    FolderGone,
    NoSpace,

    /** The stored password can't be read any more (its Keystore key is gone). */
    PasswordUnavailable,
    Other,
}

sealed interface AutoBackupResult {
    /** A new backup was written. */
    data class Written(val entries: Int, val photos: Int, val bytes: Long) : AutoBackupResult

    /** Nothing changed since the last backup, so nothing was written. */
    data object Unchanged : AutoBackupResult

    data class Failed(val reason: AutoBackupFailure) : AutoBackupResult
}

/** What the last runs did. Times are epoch millis. */
data class AutoBackupStatus(
    /** The last backup actually written (or found unchanged, which is just as good). */
    val lastSuccessAt: Long? = null,
    /** The last run, whatever its result. */
    val lastRunAt: Long? = null,
    val lastResult: AutoBackupResult? = null,
    /** The counts of the backup the last success left in the folder. */
    val entries: Int = 0,
    val photos: Int = 0,
    val bytes: Long = 0,
)

/** A running automatic backup: [total] photos to write, [done] so far. */
data class AutoBackupProgress(val done: Int, val total: Int)
