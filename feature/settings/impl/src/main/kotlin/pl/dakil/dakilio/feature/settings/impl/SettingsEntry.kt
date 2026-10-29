package pl.dakil.dakilio.feature.settings.impl

import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.navigation3.runtime.EntryProviderScope
import androidx.navigation3.runtime.NavKey
import pl.dakil.dakilio.core.navigation.Navigator
import pl.dakil.dakilio.feature.settings.api.SettingsKey

fun EntryProviderScope<NavKey>.settingsEntry(navigator: Navigator) {
    entry<SettingsKey> {
        SettingsScreen(viewModel = hiltViewModel(), onBack = navigator::goBack)
    }
}
