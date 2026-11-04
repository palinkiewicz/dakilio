package pl.dakil.dakilio.feature.lock.impl

import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.navigation3.runtime.EntryProviderScope
import androidx.navigation3.runtime.NavKey
import pl.dakil.dakilio.core.navigation.Navigator
import pl.dakil.dakilio.feature.lock.api.AppLockKey

fun EntryProviderScope<NavKey>.appLockEntry(navigator: Navigator) {
    entry<AppLockKey> {
        AppLockScreen(viewModel = hiltViewModel(), onBack = navigator::goBack)
    }
}
