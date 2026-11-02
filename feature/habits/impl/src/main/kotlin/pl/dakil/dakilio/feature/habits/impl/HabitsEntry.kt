package pl.dakil.dakilio.feature.habits.impl

import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.navigation3.runtime.EntryProviderScope
import androidx.navigation3.runtime.NavKey
import pl.dakil.dakilio.core.navigation.Navigator
import pl.dakil.dakilio.feature.habits.api.HabitsKey

fun EntryProviderScope<NavKey>.habitsEntry(navigator: Navigator) {
    entry<HabitsKey> {
        HabitsScreen(viewModel = hiltViewModel(), onBack = navigator::goBack)
    }
}
