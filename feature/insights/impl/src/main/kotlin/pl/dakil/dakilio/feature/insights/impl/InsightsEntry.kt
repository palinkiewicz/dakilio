package pl.dakil.dakilio.feature.insights.impl

import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.navigation3.runtime.EntryProviderScope
import androidx.navigation3.runtime.NavKey
import pl.dakil.dakilio.core.navigation.Navigator
import pl.dakil.dakilio.core.ui.NavTransitions
import pl.dakil.dakilio.feature.entry.api.EntryEditorKey
import pl.dakil.dakilio.feature.insights.api.InsightsEntriesKey
import pl.dakil.dakilio.feature.insights.api.InsightsKey
import pl.dakil.dakilio.feature.insights.api.InsightsMoodKey
import pl.dakil.dakilio.feature.insights.impl.mood.InsightsMoodScreen
import pl.dakil.dakilio.feature.insights.impl.mood.InsightsMoodViewModel
import pl.dakil.dakilio.feature.insights.impl.entries.InsightsEntriesScreen
import pl.dakil.dakilio.feature.insights.impl.entries.InsightsEntriesViewModel
import pl.dakil.dakilio.feature.insights.impl.overview.InsightsScreen

/** The Insights tab and the screens behind its cards. */
fun EntryProviderScope<NavKey>.insightsEntries(navigator: Navigator) {
    val openPost: (Long) -> Unit = { navigator.navigate(EntryEditorKey(postId = it)) }
    entry<InsightsKey>(metadata = NavTransitions.topLevel) {
        InsightsScreen(
            viewModel = hiltViewModel(),
            onOpenPost = openPost,
            onOpen = navigator::navigate,
        )
    }
    entry<InsightsMoodKey> { key ->
        InsightsMoodScreen(
            viewModel = hiltViewModel<InsightsMoodViewModel, InsightsMoodViewModel.Factory>(creationCallback = { it.create(key) }),
            onBack = navigator::goBack,
            onOpenPost = openPost,
            onOpen = navigator::navigate,
        )
    }
    entry<InsightsEntriesKey> { key ->
        InsightsEntriesScreen(
            viewModel = hiltViewModel<InsightsEntriesViewModel, InsightsEntriesViewModel.Factory>(creationCallback = { it.create(key) }),
            onBack = navigator::goBack,
            onOpenPost = openPost,
        )
    }
}
