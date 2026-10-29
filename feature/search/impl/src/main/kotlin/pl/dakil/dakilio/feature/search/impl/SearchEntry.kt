package pl.dakil.dakilio.feature.search.impl

import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.navigation3.runtime.EntryProviderScope
import androidx.navigation3.runtime.NavKey
import pl.dakil.dakilio.core.navigation.Navigator
import pl.dakil.dakilio.core.ui.NavTransitions
import pl.dakil.dakilio.feature.entry.api.EntryEditorKey
import pl.dakil.dakilio.feature.search.api.SearchKey

fun EntryProviderScope<NavKey>.searchEntry(navigator: Navigator) {
    entry<SearchKey>(metadata = NavTransitions.sharedContainer) {
        SearchScreen(
            viewModel = hiltViewModel(),
            onBack = navigator::goBack,
            onOpenPost = { navigator.navigate(EntryEditorKey(postId = it)) },
        )
    }
}
