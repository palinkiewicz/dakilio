package pl.dakil.dakilio.feature.more.impl

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import pl.dakil.dakilio.core.data.repository.HabitRepository
import pl.dakil.dakilio.core.data.repository.KeywordRepository
import pl.dakil.dakilio.core.data.repository.PostRepository
import pl.dakil.dakilio.core.data.repository.SettingsRepository

@HiltViewModel
class MoreViewModel @Inject constructor(
    postRepository: PostRepository,
    habitRepository: HabitRepository,
    keywordRepository: KeywordRepository,
    private val settingsRepository: SettingsRepository,
) : ViewModel() {
    /** Habits set up (the Habits extra); 0 means it isn't used yet. */
    val habitCount: StateFlow<Int> =
        habitRepository.habitCount.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), 0)

    /** Tags with keywords (the Keywords extra); 0 means it isn't used yet. */
    val keywordTagCount: StateFlow<Int> =
        keywordRepository.keywords.map { keywords -> keywords.map { it.tagId }.distinct().size }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), 0)

    /** Whether to show the "these are samples" hint. */
    val showSampleHint: StateFlow<Boolean> =
        combine(postRepository.samplePostCount, settingsRepository.settings) { count, settings ->
            count > 0 && !settings.sampleHintDismissed
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), false)

    fun dismissSampleHint() {
        viewModelScope.launch { settingsRepository.setSampleHintDismissed(true) }
    }
}
