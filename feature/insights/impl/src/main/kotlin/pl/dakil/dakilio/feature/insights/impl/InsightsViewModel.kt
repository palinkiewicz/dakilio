package pl.dakil.dakilio.feature.insights.impl

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import java.time.DayOfWeek
import java.time.LocalDate
import javax.inject.Inject
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import pl.dakil.dakilio.core.model.Diary
import pl.dakil.dakilio.core.model.Mood
import pl.dakil.dakilio.feature.insights.impl.logic.Overview
import pl.dakil.dakilio.feature.insights.impl.logic.buildOverview

sealed interface InsightsUiState {
    data object Loading : InsightsUiState

    /**
     * @property moods one primary per level.
     * @property hasAnyEntries whether the journal has entries at all (in any diary or period).
     */
    data class Ready(
        val header: PeriodHeaderState,
        val overview: Overview,
        val moods: List<Mood>,
        val diaries: List<Diary>,
        val selectedDiaryId: Long?,
        val firstDay: DayOfWeek,
        val today: LocalDate,
        val hasAnyEntries: Boolean,
    ) : InsightsUiState
}

/** The Insights tab: one period at a glance. */
@HiltViewModel
class InsightsViewModel @Inject constructor(
    source: InsightsSource,
    savedStateHandle: SavedStateHandle,
) : InsightsScopedViewModel(source, savedStateHandle) {
    val uiState: StateFlow<InsightsUiState> = frames
        .map<InsightsFrame, InsightsUiState> { frame ->
            val data = frame.data
            InsightsUiState.Ready(
                header = frame.header,
                overview = buildOverview(
                    index = data.index,
                    period = frame.period,
                    bounds = frame.bounds,
                    comparison = frame.comparison,
                    primaries = data.primaries,
                    firstDay = data.firstDay,
                    diaries = data.diaries.takeIf { data.diaryId == null },
                ),
                moods = data.primaries,
                diaries = data.diaries,
                selectedDiaryId = data.diaryId,
                firstDay = data.firstDay,
                today = data.today,
                hasAnyEntries = data.hasAnyEntries,
            )
        }
        .flowOn(source.default)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), InsightsUiState.Loading)
}
