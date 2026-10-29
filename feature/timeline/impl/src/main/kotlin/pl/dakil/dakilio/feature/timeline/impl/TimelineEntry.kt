package pl.dakil.dakilio.feature.timeline.impl

import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.navigation3.runtime.EntryProviderScope
import androidx.navigation3.runtime.NavKey
import pl.dakil.dakilio.core.navigation.Navigator
import pl.dakil.dakilio.core.ui.NavTransitions
import pl.dakil.dakilio.feature.search.api.SearchKey
import pl.dakil.dakilio.feature.timeline.api.TimelineKey

fun EntryProviderScope<NavKey>.timelineEntry(navigator: Navigator) {
    entry<TimelineKey>(metadata = NavTransitions.topLevel) {
        TimelineRoute(
            onOpenEntry = navigator::navigate,
            onOpenSearch = { navigator.navigate(SearchKey) },
            viewModel = hiltViewModel(),
        )
    }
}
