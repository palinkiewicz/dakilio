package pl.dakil.dakilio.feature.diaries.impl

import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.navigation3.runtime.EntryProviderScope
import androidx.navigation3.runtime.NavKey
import pl.dakil.dakilio.core.navigation.Navigator
import pl.dakil.dakilio.feature.diaries.api.DiariesKey
import pl.dakil.dakilio.feature.diaries.api.DiaryEditorKey

fun EntryProviderScope<NavKey>.diariesEntries(navigator: Navigator) {
    entry<DiariesKey> {
        DiariesScreen(
            viewModel = hiltViewModel(),
            onBack = navigator::goBack,
            onOpenDiary = { navigator.navigate(DiaryEditorKey(it)) },
        )
    }
    entry<DiaryEditorKey> { key ->
        DiaryEditorScreen(
            viewModel = hiltViewModel<DiaryEditorViewModel, DiaryEditorViewModel.Factory>(creationCallback = { it.create(key) }),
            onClose = navigator::goBack,
        )
    }
}
