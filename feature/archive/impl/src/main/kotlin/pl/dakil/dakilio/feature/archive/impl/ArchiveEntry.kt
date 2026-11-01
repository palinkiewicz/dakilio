package pl.dakil.dakilio.feature.archive.impl

import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.navigation3.runtime.EntryProviderScope
import androidx.navigation3.runtime.NavKey
import pl.dakil.dakilio.core.navigation.Navigator
import pl.dakil.dakilio.feature.archive.api.ArchiveKey

fun EntryProviderScope<NavKey>.archiveEntry(navigator: Navigator) {
    entry<ArchiveKey> {
        ArchiveScreen(viewModel = hiltViewModel(), onBack = navigator::goBack)
    }
}
