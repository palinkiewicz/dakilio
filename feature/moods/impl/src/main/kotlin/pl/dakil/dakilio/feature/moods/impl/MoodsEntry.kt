package pl.dakil.dakilio.feature.moods.impl

import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.navigation3.runtime.EntryProviderScope
import androidx.navigation3.runtime.NavKey
import pl.dakil.dakilio.core.navigation.Navigator
import pl.dakil.dakilio.feature.moods.api.MoodsKey

fun EntryProviderScope<NavKey>.moodsEntry(navigator: Navigator) {
    entry<MoodsKey> {
        MoodsScreen(viewModel = hiltViewModel(), onBack = navigator::goBack)
    }
}
