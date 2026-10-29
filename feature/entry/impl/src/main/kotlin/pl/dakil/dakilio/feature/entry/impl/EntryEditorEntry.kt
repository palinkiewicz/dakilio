package pl.dakil.dakilio.feature.entry.impl

import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.navigation3.runtime.EntryProviderScope
import androidx.navigation3.runtime.NavKey
import pl.dakil.dakilio.core.navigation.Navigator
import pl.dakil.dakilio.core.ui.NavTransitions
import pl.dakil.dakilio.feature.entry.api.EntryEditorKey
import pl.dakil.dakilio.feature.tags.api.TagsKey

fun EntryProviderScope<NavKey>.entryEditorEntry(navigator: Navigator) {
    entry<EntryEditorKey>(metadata = NavTransitions.sharedContainer) { key ->
        EntryEditorScreen(
            viewModel = hiltViewModel<EntryEditorViewModel, EntryEditorViewModel.Factory>(
                creationCallback = { factory -> factory.create(key) },
            ),
            sharedElementKey = key.sharedElementKey,
            onClose = navigator::goBack,
            onManageTags = { navigator.navigate(TagsKey) },
        )
    }
}
