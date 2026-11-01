package pl.dakil.dakilio.feature.importer.impl

import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.navigation3.runtime.EntryProviderScope
import androidx.navigation3.runtime.NavKey
import pl.dakil.dakilio.core.navigation.Navigator
import pl.dakil.dakilio.feature.importer.api.DaylioImportKey
import pl.dakil.dakilio.feature.timeline.api.TimelineKey

fun EntryProviderScope<NavKey>.daylioImportEntry(navigator: Navigator) {
    entry<DaylioImportKey> {
        DaylioImportScreen(
            viewModel = hiltViewModel(),
            onBack = navigator::goBack,
            onDone = {
                navigator.goBack()
                navigator.navigate(TimelineKey)
            },
        )
    }
}
