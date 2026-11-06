package pl.dakil.dakilio.feature.reminders.impl

import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.navigation3.runtime.EntryProviderScope
import androidx.navigation3.runtime.NavKey
import pl.dakil.dakilio.core.navigation.Navigator
import pl.dakil.dakilio.feature.reminders.api.RemindersKey

fun EntryProviderScope<NavKey>.remindersEntry(navigator: Navigator) {
    entry<RemindersKey> {
        RemindersScreen(viewModel = hiltViewModel(), onBack = navigator::goBack)
    }
}
