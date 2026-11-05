package pl.dakil.dakilio.feature.insights.impl

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import java.time.Clock
import java.time.LocalDate
import javax.inject.Inject
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.stateIn
import pl.dakil.dakilio.core.common.DakilioDispatchers
import pl.dakil.dakilio.core.common.Dispatcher
import pl.dakil.dakilio.core.data.repository.DiaryRepository
import pl.dakil.dakilio.core.data.repository.MoodRepository
import pl.dakil.dakilio.core.data.repository.PostRepository
import pl.dakil.dakilio.core.data.repository.SettingsRepository
import pl.dakil.dakilio.core.model.Diary
import pl.dakil.dakilio.core.model.Mood
import pl.dakil.dakilio.core.model.primaries

sealed interface InsightsUiState {
    data object Loading : InsightsUiState

    data class Ready(
        val insights: Insights,
        val moods: List<Mood>,
        val range: InsightsRange,
        val diaries: List<Diary>,
        val selectedDiaryId: Long?,
        val today: LocalDate,
        val hasAnyEntries: Boolean,
    ) : InsightsUiState
}

@HiltViewModel
class InsightsViewModel @Inject constructor(
    postRepository: PostRepository,
    diaryRepository: DiaryRepository,
    moodRepository: MoodRepository,
    settingsRepository: SettingsRepository,
    private val clock: Clock,
    private val savedStateHandle: SavedStateHandle,
    @Dispatcher(DakilioDispatchers.Default) default: CoroutineDispatcher,
) : ViewModel() {
    private val range = savedStateHandle.getStateFlow(KEY_RANGE, InsightsRange.Month)
    private val selectedDiaryId = savedStateHandle.getStateFlow<Long?>(KEY_DIARY, null)

    val uiState: StateFlow<InsightsUiState> =
        combine(
            combine(postRepository.posts, settingsRepository.firstDayOfWeek, ::Pair),
            diaryRepository.diaries,
            moodRepository.moods,
            range,
            selectedDiaryId,
        ) { (posts, firstDay), diaries, moods, range, selected ->
            // Charts work per level: aliases fold into their level's primary.
            val primaries = moods.primaries()
            val filter = selected?.takeIf { id -> diaries.any { it.id == id } }
            val filtered = if (filter == null) posts else posts.filter { it.diaryId == filter }
            val today = LocalDate.now(clock)
            InsightsUiState.Ready(
                insights = calculateInsights(filtered, primaries, range, today, firstDay),
                moods = primaries,
                range = range,
                diaries = diaries,
                selectedDiaryId = filter,
                today = today,
                hasAnyEntries = posts.isNotEmpty(),
            )
        }
            .flowOn(default)
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), InsightsUiState.Loading)

    fun selectRange(value: InsightsRange) {
        savedStateHandle[KEY_RANGE] = value
    }

    fun selectDiary(id: Long?) {
        savedStateHandle[KEY_DIARY] = id
    }

    private companion object {
        const val KEY_RANGE = "range"
        const val KEY_DIARY = "diary"
    }
}
