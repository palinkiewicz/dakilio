package pl.dakil.dakilio.feature.keywords.impl

import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.navigation3.runtime.EntryProviderScope
import androidx.navigation3.runtime.NavKey
import pl.dakil.dakilio.core.navigation.Navigator
import pl.dakil.dakilio.feature.entry.api.EntryEditorKey
import pl.dakil.dakilio.feature.keywords.api.KeywordPastKey
import pl.dakil.dakilio.feature.keywords.api.KeywordsKey

fun EntryProviderScope<NavKey>.keywordsEntry(navigator: Navigator) {
    entry<KeywordsKey> {
        KeywordsScreen(viewModel = hiltViewModel(), onBack = navigator::goBack, onReviewPast = navigator::navigate)
    }
    entry<KeywordPastKey> { key ->
        KeywordPastScreen(
            viewModel = hiltViewModel<KeywordPastViewModel, KeywordPastViewModel.Factory>(creationCallback = { it.create(key) }),
            onBack = navigator::goBack,
            onOpenEntry = { navigator.navigate(EntryEditorKey(postId = it)) },
        )
    }
}
