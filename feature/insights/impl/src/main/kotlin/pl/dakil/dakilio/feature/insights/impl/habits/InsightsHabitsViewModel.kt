package pl.dakil.dakilio.feature.insights.impl.habits

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
import pl.dakil.dakilio.core.model.DateRange
import pl.dakil.dakilio.core.model.Mood
import pl.dakil.dakilio.feature.insights.api.InsightsHabitsKey
import pl.dakil.dakilio.feature.insights.impl.InsightsFrame
import pl.dakil.dakilio.feature.insights.impl.InsightsScopedViewModel
import pl.dakil.dakilio.feature.insights.impl.InsightsSource
import pl.dakil.dakilio.feature.insights.impl.PeriodHeaderState
import pl.dakil.dakilio.feature.insights.impl.logic.HabitStat
import pl.dakil.dakilio.feature.insights.impl.logic.habitStats

sealed interface InsightsHabitsUiState {
    data object Loading : InsightsHabitsUiState

    /**
     * @property stats the habits that had begun by the end of the period.
     * @property hasHabits whether there are habits at all (some may not have begun yet).
     */
    data class Ready(
        val header: PeriodHeaderState,
        val range: DateRange,
        val stats: List<HabitStat>,
        val hasHabits: Boolean,
        val hasComparison: Boolean,
        val moods: List<Mood>,
        val firstDay: DayOfWeek,
        val today: LocalDate,
    ) : InsightsHabitsUiState
}

@HiltViewModel(assistedFactory = InsightsHabitsViewModel.Factory::class)
class InsightsHabitsViewModel @AssistedInject constructor(
    source: InsightsSource,
    savedStateHandle: SavedStateHandle,
    @Assisted key: InsightsHabitsKey,
) : InsightsScopedViewModel(source, savedStateHandle, key.scope) {
    val uiState: StateFlow<InsightsHabitsUiState> = frames
        .map<InsightsFrame, InsightsHabitsUiState> { frame ->
            val data = frame.data
            val comparison = frame.comparisonRange
            InsightsHabitsUiState.Ready(
                header = frame.header,
                range = frame.range,
                stats = habitStats(data.habits, data.allPosts, frame.range, comparison, data.firstDay, data.today).filter { it.weeks.isNotEmpty() },
                hasHabits = data.habits.isNotEmpty(),
                hasComparison = comparison != null,
                moods = data.primaries,
                firstDay = data.firstDay,
                today = data.today,
            )
        }
        .flowOn(source.default)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), InsightsHabitsUiState.Loading)

    @AssistedFactory
    interface Factory {
        fun create(key: InsightsHabitsKey): InsightsHabitsViewModel
    }
}
