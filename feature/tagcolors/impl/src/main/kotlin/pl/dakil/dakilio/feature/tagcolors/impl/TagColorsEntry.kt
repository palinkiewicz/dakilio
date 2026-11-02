package pl.dakil.dakilio.feature.tagcolors.impl

import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.navigation3.runtime.EntryProviderScope
import androidx.navigation3.runtime.NavKey
import pl.dakil.dakilio.core.navigation.Navigator
import pl.dakil.dakilio.feature.tagcolors.api.TagColorsKey
import pl.dakil.dakilio.feature.tags.api.TagsKey

fun EntryProviderScope<NavKey>.tagColorsEntry(navigator: Navigator) {
    entry<TagColorsKey> {
        TagColorsScreen(viewModel = hiltViewModel(), onBack = navigator::goBack, onOpenTags = { navigator.navigate(TagsKey) })
    }
}
