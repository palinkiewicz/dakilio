package pl.dakil.dakilio.feature.insights.impl

import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.navigation3.runtime.EntryProviderScope
import androidx.navigation3.runtime.NavKey
import pl.dakil.dakilio.core.navigation.Navigator
import pl.dakil.dakilio.core.ui.NavTransitions
import pl.dakil.dakilio.feature.entry.api.EntryEditorKey
import pl.dakil.dakilio.feature.insights.api.InsightsKey
import pl.dakil.dakilio.feature.insights.impl.overview.InsightsScreen

/** The Insights tab and the screens behind its cards. */
fun EntryProviderScope<NavKey>.insightsEntries(navigator: Navigator) {
    entry<InsightsKey>(metadata = NavTransitions.topLevel) {
        InsightsScreen(
            viewModel = hiltViewModel(),
            onOpenPost = { navigator.navigate(EntryEditorKey(postId = it)) },
        )
    }
}
