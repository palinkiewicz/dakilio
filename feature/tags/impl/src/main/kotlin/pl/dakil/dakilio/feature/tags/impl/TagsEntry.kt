package pl.dakil.dakilio.feature.tags.impl

import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.navigation3.runtime.EntryProviderScope
import androidx.navigation3.runtime.NavKey
import pl.dakil.dakilio.core.navigation.Navigator
import pl.dakil.dakilio.feature.tags.api.TagsKey

fun EntryProviderScope<NavKey>.tagsEntry(navigator: Navigator) {
    entry<TagsKey> {
        TagsScreen(viewModel = hiltViewModel(), onBack = navigator::goBack)
    }
}
