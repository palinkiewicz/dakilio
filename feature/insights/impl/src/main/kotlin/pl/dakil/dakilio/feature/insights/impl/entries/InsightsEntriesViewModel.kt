package pl.dakil.dakilio.feature.insights.impl.entries

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
import pl.dakil.dakilio.core.model.Tag
import pl.dakil.dakilio.core.model.primaryOf
import pl.dakil.dakilio.feature.insights.api.InsightsEntriesKey
import pl.dakil.dakilio.feature.insights.impl.InsightsFrame
import pl.dakil.dakilio.feature.insights.impl.InsightsScopedViewModel
import pl.dakil.dakilio.feature.insights.impl.InsightsSource
import pl.dakil.dakilio.feature.insights.impl.PeriodHeaderState
import pl.dakil.dakilio.feature.insights.impl.logic.DayPart
import pl.dakil.dakilio.feature.insights.impl.logic.EntryDay
import pl.dakil.dakilio.feature.insights.impl.logic.EntryFilter
import pl.dakil.dakilio.feature.insights.impl.logic.entryDays

sealed interface InsightsEntriesUiState {
    data object Loading : InsightsEntriesUiState

    /**
     * @property mood, tag what the list is narrowed to, resolved for the title (`null` when the
     *   filter has none, or it's gone).
     * @property diaries by id, to label cards when the list mixes diaries; empty under a diary filter.
     */
    data class Ready(
        val header: PeriodHeaderState,
        val filter: EntryFilter,
        val mood: Mood?,
        val tag: Tag?,
        val days: List<EntryDay>,
        val count: Int,
        val moods: List<Mood>,
        val diaries: Map<Long, Diary>,
        val firstDay: DayOfWeek,
        val today: LocalDate,
    ) : InsightsEntriesUiState
}

/** The entries behind a number on another Insights screen. */
@HiltViewModel(assistedFactory = InsightsEntriesViewModel.Factory::class)
class InsightsEntriesViewModel @AssistedInject constructor(
    source: InsightsSource,
    savedStateHandle: SavedStateHandle,
    @Assisted key: InsightsEntriesKey,
) : InsightsScopedViewModel(source, savedStateHandle, key.scope) {
    private val filter = EntryFilter(
        moodLevel = key.moodLevel,
        tagId = key.tagId,
        weekday = key.weekday?.takeIf { it in 1..7 }?.let(DayOfWeek::of),
        dayPart = key.dayPart?.let { name -> DayPart.entries.firstOrNull { it.name == name } },
    )

    val uiState: StateFlow<InsightsEntriesUiState> = frames
        .map<InsightsFrame, InsightsEntriesUiState> { frame ->
            val data = frame.data
            val days = entryDays(data.index.posts(frame.range), filter)
            InsightsEntriesUiState.Ready(
                header = frame.header,
                filter = filter,
                mood = filter.moodLevel?.let(data.primaries::primaryOf),
                // The tag as its newest entry has it.
                tag = filter.tagId?.let { id -> data.index.posts.asReversed().firstNotNullOfOrNull { post -> post.tags.firstOrNull { it.id == id } } },
                days = days,
                count = days.sumOf { it.posts.size },
                moods = data.primaries,
                diaries = if (data.diaryId == null && data.diaries.size > 1) data.diaries.associateBy { it.id } else emptyMap(),
                firstDay = data.firstDay,
                today = data.today,
            )
        }
        .flowOn(source.default)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), InsightsEntriesUiState.Loading)

    @AssistedFactory
    interface Factory {
        fun create(key: InsightsEntriesKey): InsightsEntriesViewModel
    }
}
