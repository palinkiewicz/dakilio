package pl.dakil.dakilio.feature.insights.impl

import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.navigation3.runtime.EntryProviderScope
import androidx.navigation3.runtime.NavKey
import pl.dakil.dakilio.core.ui.NavTransitions
import pl.dakil.dakilio.feature.insights.api.InsightsKey

fun EntryProviderScope<NavKey>.insightsEntry() {
    entry<InsightsKey>(metadata = NavTransitions.topLevel) {
        InsightsScreen(viewModel = hiltViewModel())
    }
}
