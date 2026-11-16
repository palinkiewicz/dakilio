package pl.dakil.dakilio.feature.insights.impl.rhythm

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
import pl.dakil.dakilio.core.model.Mood
import pl.dakil.dakilio.feature.insights.api.InsightsRhythmKey
import pl.dakil.dakilio.feature.insights.impl.InsightsFrame
import pl.dakil.dakilio.feature.insights.impl.InsightsScopedViewModel
import pl.dakil.dakilio.feature.insights.impl.InsightsSource
import pl.dakil.dakilio.feature.insights.impl.PeriodHeaderState
import pl.dakil.dakilio.feature.insights.impl.logic.Rhythm
import pl.dakil.dakilio.feature.insights.impl.logic.rhythm

sealed interface InsightsRhythmUiState {
    data object Loading : InsightsRhythmUiState

    data class Ready(
        val header: PeriodHeaderState,
        val rhythm: Rhythm,
        val entries: Int,
        val moods: List<Mood>,
        val firstDay: DayOfWeek,
        val today: LocalDate,
    ) : InsightsRhythmUiState
}

@HiltViewModel(assistedFactory = InsightsRhythmViewModel.Factory::class)
class InsightsRhythmViewModel @AssistedInject constructor(
    source: InsightsSource,
    savedStateHandle: SavedStateHandle,
    @Assisted key: InsightsRhythmKey,
) : InsightsScopedViewModel(source, savedStateHandle, key.scope) {
    val uiState: StateFlow<InsightsRhythmUiState> = frames
        .map<InsightsFrame, InsightsRhythmUiState> { frame ->
            val posts = frame.data.index.posts(frame.range)
            InsightsRhythmUiState.Ready(frame.header, rhythm(posts), posts.size, frame.data.primaries, frame.data.firstDay, frame.data.today)
        }
        .flowOn(source.default)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), InsightsRhythmUiState.Loading)

    @AssistedFactory
    interface Factory {
        fun create(key: InsightsRhythmKey): InsightsRhythmViewModel
    }
}
