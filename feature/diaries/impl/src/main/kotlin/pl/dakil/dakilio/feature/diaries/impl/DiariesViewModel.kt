package pl.dakil.dakilio.feature.diaries.impl

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import pl.dakil.dakilio.core.data.repository.DiaryRepository
import pl.dakil.dakilio.core.data.repository.TagRepository
import pl.dakil.dakilio.core.model.Diary

data class DiaryRow(val diary: Diary, val entryCount: Int, val suggestedCount: Int, val allGroupsSuggested: Boolean)

@HiltViewModel
class DiariesViewModel @Inject constructor(
    diaryRepository: DiaryRepository,
    tagRepository: TagRepository,
) : ViewModel() {
    /** `null` while loading. */
    val rows: StateFlow<List<DiaryRow>?> =
        combine(diaryRepository.diaries, diaryRepository.postCounts, tagRepository.groups) { diaries, counts, groups ->
            val activeGroups = groups.map { it.id }.toSet()
            diaries.map { diary ->
                val suggested = diary.suggestedGroupIds.count { it in activeGroups }
                DiaryRow(diary, counts[diary.id] ?: 0, suggested, suggested == 0 || suggested == activeGroups.size)
            }
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)
}
