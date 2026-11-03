package pl.dakil.dakilio.core.data.repository

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.util.Log
import androidx.core.content.IntentCompat
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import pl.dakil.dakilio.core.backup.BACKUP_HEAD_BYTES
import pl.dakil.dakilio.core.backup.BackupKind
import pl.dakil.dakilio.core.backup.detectBackupKind
import pl.dakil.dakilio.core.common.ApplicationScope
import pl.dakil.dakilio.core.common.DakilioDispatchers
import pl.dakil.dakilio.core.common.Dispatcher

/** A file handed to Dakilio from outside, and which screen it's for. */
data class IncomingBackup(val uri: Uri, val kind: BackupKind) {
    /** A Dakilio backup (for the Backup screen); anything else goes to the Daylio importer. */
    val isDakilio: Boolean get() = kind != BackupKind.Other
}

/**
 * A backup file handed to Dakilio from outside (a share sheet, or "Open with" in a file manager).
 * The app's receiver activity forwards it to MainActivity, which drops it here. Its first bytes tell
 * a Dakilio backup from a Daylio one; the app shell then opens the matching screen while one is
 * waiting, and that screen's ViewModel takes it.
 */
@Singleton
class IncomingBackups @Inject constructor(
    @ApplicationContext private val context: Context,
    @ApplicationScope private val scope: CoroutineScope,
    @Dispatcher(DakilioDispatchers.IO) private val io: CoroutineDispatcher,
) {
    private val _pending = MutableStateFlow<IncomingBackup?>(null)
    val pending: StateFlow<IncomingBackup?> = _pending.asStateFlow()

    /** Returns true if [intent] carried a file (which shows up in [pending] once it's been sniffed). */
    fun deliver(intent: Intent?): Boolean {
        val uri = uriFrom(intent) ?: return false
        deliver(uri)
        return true
    }

    /** A file picked inside the app (the intro's import step), routed the same way as a shared one. */
    fun deliver(uri: Uri) {
        scope.launch(io) { _pending.value = IncomingBackup(uri, sniff(uri)) }
    }

    /** Takes the waiting file if it's a Dakilio backup ([dakilio]) or not. */
    fun take(dakilio: Boolean): Uri? {
        while (true) {
            val current = _pending.value ?: return null
            if (current.isDakilio != dakilio) return null
            if (_pending.compareAndSet(current, null)) return current.uri
        }
    }

    private fun sniff(uri: Uri): BackupKind = runCatching {
        context.contentResolver.openInputStream(uri)?.use { input ->
            val head = ByteArray(BACKUP_HEAD_BYTES)
            var filled = 0
            while (filled < head.size) {
                val read = input.read(head, filled, head.size - filled)
                if (read < 0) break
                filled += read
            }
            detectBackupKind(head.copyOf(filled))
        }
    }.onFailure { Log.w(TAG, "Can't read the incoming file", it) }.getOrNull() ?: BackupKind.Other

    companion object {
        private const val TAG = "IncomingBackups"

        /** The file in a share (`SEND`) or "Open with" (`VIEW`) intent, or null. */
        fun uriFrom(intent: Intent?): Uri? = when (intent?.action) {
            Intent.ACTION_SEND -> IntentCompat.getParcelableExtra(intent, Intent.EXTRA_STREAM, Uri::class.java)
            Intent.ACTION_VIEW -> intent.data
            else -> null
        }
    }
}
