package pl.dakil.dakilio.feature.insights.impl.tags

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.viewModelScope
import dagger.assisted.Assisted
import dagger.assisted.AssistedFactory
import dagger.assisted.AssistedInject
import dagger.hilt.android.lifecycle.HiltViewModel
import java.time.DayOfWeek
import java.time.LocalDate
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.stateIn
import pl.dakil.dakilio.core.data.repository.TagRepository
import pl.dakil.dakilio.core.model.Mood
import pl.dakil.dakilio.feature.insights.api.InsightsTagKey
import pl.dakil.dakilio.feature.insights.impl.InsightsScopedViewModel
import pl.dakil.dakilio.feature.insights.impl.InsightsSource
import pl.dakil.dakilio.feature.insights.impl.PeriodHeaderState
import pl.dakil.dakilio.feature.insights.impl.logic.TagDetail
import pl.dakil.dakilio.feature.insights.impl.logic.tagDetail

sealed interface InsightsTagUiState {
    data object Loading : InsightsTagUiState

    /** @property groupName the tag's group, `null` when it's gone. */
    data class Ready(
        val header: PeriodHeaderState,
        val detail: TagDetail,
        val groupName: String?,
        val moods: List<Mood>,
        val firstDay: DayOfWeek,
        val today: LocalDate,
    ) : InsightsTagUiState
}

@HiltViewModel(assistedFactory = InsightsTagViewModel.Factory::class)
class InsightsTagViewModel @AssistedInject constructor(
    source: InsightsSource,
    tagRepository: TagRepository,
    savedStateHandle: SavedStateHandle,
    @Assisted private val key: InsightsTagKey,
) : InsightsScopedViewModel(source, savedStateHandle, key.scope) {
    val tagId: Long get() = key.tagId

    val uiState: StateFlow<InsightsTagUiState> =
        combine(frames, tagRepository.groupNames()) { frame, groupNames ->
            val data = frame.data
            val detail = tagDetail(data.index, frame.range, frame.comparisonRange, key.tagId, data.primaries, data.firstDay)
            InsightsTagUiState.Ready(
                header = frame.header,
                detail = detail,
                groupName = detail.tag?.let { groupNames[it.groupId] },
                moods = data.primaries,
                firstDay = data.firstDay,
                today = data.today,
            ) as InsightsTagUiState
        }
            .flowOn(source.default)
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), InsightsTagUiState.Loading)

    @AssistedFactory
    interface Factory {
        fun create(key: InsightsTagKey): InsightsTagViewModel
    }
}
