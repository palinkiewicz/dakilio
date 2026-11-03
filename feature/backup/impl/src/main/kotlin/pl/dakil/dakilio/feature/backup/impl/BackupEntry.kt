package pl.dakil.dakilio.feature.backup.impl

import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.navigation3.runtime.EntryProviderScope
import androidx.navigation3.runtime.NavKey
import pl.dakil.dakilio.feature.backup.api.BackupKey

/**
 * The backup screen. Used by the app shell (More → Backup & restore) and by the first-run intro,
 * which decide where Back and a finished restore lead. The intro passes [restoreOnly]: a fresh
 * install has nothing to back up.
 */
fun EntryProviderScope<NavKey>.backupEntry(onBack: () -> Unit, onRestored: () -> Unit, restoreOnly: Boolean = false) {
    entry<BackupKey> {
        BackupScreen(viewModel = hiltViewModel(), restoreOnly = restoreOnly, onBack = onBack, onRestored = onRestored)
    }
}
