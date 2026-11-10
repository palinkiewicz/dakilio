package pl.dakil.dakilio.feature.export.impl

import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.navigation3.runtime.EntryProviderScope
import androidx.navigation3.runtime.NavKey
import pl.dakil.dakilio.core.navigation.Navigator
import pl.dakil.dakilio.feature.export.api.ExportKey

/** More → Export: the journal as a PDF or Markdown. */
fun EntryProviderScope<NavKey>.exportEntry(navigator: Navigator) {
    entry<ExportKey> {
        ExportScreen(viewModel = hiltViewModel(), onBack = navigator::goBack)
    }
}
