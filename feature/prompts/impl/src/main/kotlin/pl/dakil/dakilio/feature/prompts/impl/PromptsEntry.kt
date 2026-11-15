package pl.dakil.dakilio.feature.prompts.impl

import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.navigation3.runtime.EntryProviderScope
import androidx.navigation3.runtime.NavKey
import pl.dakil.dakilio.core.navigation.Navigator
import pl.dakil.dakilio.feature.prompts.api.PromptsKey

fun EntryProviderScope<NavKey>.promptsEntry(navigator: Navigator) {
    entry<PromptsKey> {
        PromptsScreen(viewModel = hiltViewModel(), onBack = navigator::goBack)
    }
}
