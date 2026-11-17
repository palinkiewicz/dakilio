package pl.dakil.dakilio.feature.insights.impl.recap

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
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import pl.dakil.dakilio.core.model.Diary
import pl.dakil.dakilio.core.model.Mood
import pl.dakil.dakilio.feature.insights.api.InsightsRecapKey
import pl.dakil.dakilio.feature.insights.impl.InsightsFrame
import pl.dakil.dakilio.feature.insights.impl.InsightsScopedViewModel
import pl.dakil.dakilio.feature.insights.impl.InsightsSource
import pl.dakil.dakilio.feature.insights.impl.PeriodHeaderState
import pl.dakil.dakilio.feature.insights.impl.logic.Recap
import pl.dakil.dakilio.feature.insights.impl.logic.buildOverview
import pl.dakil.dakilio.feature.insights.impl.logic.recap

sealed interface InsightsRecapUiState {
    data object Loading : InsightsRecapUiState

    /** @property diary the diary the recap is about, `null` for all of them. */
    data class Ready(
        val header: PeriodHeaderState,
        val recap: Recap,
        val diary: Diary?,
        val moods: List<Mood>,
        val firstDay: DayOfWeek,
        val today: LocalDate,
    ) : InsightsRecapUiState
}

@HiltViewModel(assistedFactory = InsightsRecapViewModel.Factory::class)
class InsightsRecapViewModel @AssistedInject constructor(
    source: InsightsSource,
    savedStateHandle: SavedStateHandle,
    @Assisted key: InsightsRecapKey,
) : InsightsScopedViewModel(source, savedStateHandle, key.scope.copy(comparison = null)) {
    val uiState: StateFlow<InsightsRecapUiState> = frames
        .map<InsightsFrame, InsightsRecapUiState> { frame ->
            val data = frame.data
            val overview = buildOverview(
                index = data.index,
                period = frame.period,
                bounds = frame.bounds,
                comparison = frame.comparison,
                primaries = data.primaries,
                firstDay = data.firstDay,
                habits = data.habits,
                allPosts = data.allPosts,
            )
            InsightsRecapUiState.Ready(
                header = frame.header,
                recap = recap(overview, data.index, frame.period, data.months),
                diary = data.diaries.firstOrNull { it.id == data.diaryId },
                moods = data.primaries,
                firstDay = data.firstDay,
                today = data.today,
            )
        }
        .flowOn(source.default)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), InsightsRecapUiState.Loading)

    @AssistedFactory
    interface Factory {
        fun create(key: InsightsRecapKey): InsightsRecapViewModel
    }
}
