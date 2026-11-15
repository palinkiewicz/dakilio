package pl.dakil.dakilio.feature.insights.impl

import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.navigation3.runtime.EntryProviderScope
import androidx.navigation3.runtime.NavKey
import pl.dakil.dakilio.core.navigation.Navigator
import pl.dakil.dakilio.core.ui.NavTransitions
import pl.dakil.dakilio.feature.insights.api.InsightsKey
import pl.dakil.dakilio.feature.insights.api.MilestonesKey

fun EntryProviderScope<NavKey>.insightsEntry(navigator: Navigator) {
    entry<InsightsKey>(metadata = NavTransitions.topLevel) {
        InsightsScreen(viewModel = hiltViewModel(), onOpenMilestones = { navigator.navigate(MilestonesKey) })
    }
    entry<MilestonesKey> {
        MilestonesScreen(viewModel = hiltViewModel(), onBack = navigator::goBack)
    }
}
