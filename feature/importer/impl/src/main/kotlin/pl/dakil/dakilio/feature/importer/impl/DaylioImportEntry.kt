package pl.dakil.dakilio.feature.importer.impl

import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.navigation3.runtime.EntryProviderScope
import androidx.navigation3.runtime.NavKey
import pl.dakil.dakilio.feature.importer.api.DaylioImportKey

/**
 * The import screen. Used by the app shell (More → Import from Daylio) and by the first-run intro,
 * which decide where Back and the finished import lead.
 */
fun EntryProviderScope<NavKey>.daylioImportEntry(onBack: () -> Unit, onDone: () -> Unit) {
    entry<DaylioImportKey> {
        DaylioImportScreen(viewModel = hiltViewModel(), onBack = onBack, onDone = onDone)
    }
}
