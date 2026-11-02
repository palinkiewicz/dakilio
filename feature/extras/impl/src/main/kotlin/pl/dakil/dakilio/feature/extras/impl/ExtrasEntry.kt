package pl.dakil.dakilio.feature.extras.impl

import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.navigation3.runtime.EntryProviderScope
import androidx.navigation3.runtime.NavKey
import pl.dakil.dakilio.core.navigation.Navigator
import pl.dakil.dakilio.feature.extras.api.ExtrasKey

fun EntryProviderScope<NavKey>.extrasEntry(navigator: Navigator) {
    entry<ExtrasKey> {
        ExtrasScreen(viewModel = hiltViewModel(), onBack = navigator::goBack, onOpen = navigator::navigate)
    }
}
