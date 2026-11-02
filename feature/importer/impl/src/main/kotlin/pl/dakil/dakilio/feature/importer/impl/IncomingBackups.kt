package pl.dakil.dakilio.feature.importer.impl

import android.content.Intent
import android.net.Uri
import androidx.core.content.IntentCompat
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.getAndUpdate

/**
 * A backup file handed to Dakilio from outside (Daylio's share sheet, or "Open with" in a file
 * manager). The app's receiver activity forwards it to MainActivity, which drops it here; the app
 * shell opens the import screen while one is waiting, and the import ViewModel takes it.
 */
@Singleton
class IncomingBackups @Inject constructor() {
    private val _pending = MutableStateFlow<Uri?>(null)
    val pending: StateFlow<Uri?> = _pending.asStateFlow()

    /** Returns true if [intent] carried a backup file. */
    fun deliver(intent: Intent?): Boolean {
        val uri = uriFrom(intent) ?: return false
        _pending.value = uri
        return true
    }

    internal fun take(): Uri? = _pending.getAndUpdate { null }

    companion object {
        /** The file in a share (`SEND`) or "Open with" (`VIEW`) intent, or null. */
        fun uriFrom(intent: Intent?): Uri? = when (intent?.action) {
            Intent.ACTION_SEND -> IntentCompat.getParcelableExtra(intent, Intent.EXTRA_STREAM, Uri::class.java)
            Intent.ACTION_VIEW -> intent.data
            else -> null
        }
    }
}
