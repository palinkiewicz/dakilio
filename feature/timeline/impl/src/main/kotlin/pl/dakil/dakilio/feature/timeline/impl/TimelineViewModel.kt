package pl.dakil.dakilio.feature.timeline.impl

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import java.time.Clock
import java.time.LocalDate
import java.time.LocalTime
import javax.inject.Inject
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import pl.dakil.dakilio.core.common.DakilioDispatchers
import pl.dakil.dakilio.core.common.Dispatcher
import pl.dakil.dakilio.core.data.repository.DiaryRepository
import pl.dakil.dakilio.core.data.repository.MoodRepository
import pl.dakil.dakilio.core.data.repository.PostRepository
import pl.dakil.dakilio.core.model.Diary
import pl.dakil.dakilio.core.model.Mood
import pl.dakil.dakilio.core.model.Streaks
import pl.dakil.dakilio.core.model.primaries

enum class Greeting { Morning, Afternoon, Evening, Night }

sealed interface TimelineUiState {
    data object Loading : TimelineUiState

    data class Ready(
        val items: List<TimelineItem>,
        val diaries: List<Diary>,
        val selectedDiaryId: Long?,
        val moods: List<Mood>,
        val today: LocalDate,
        /** The range [buildTimeline] covers; the jump-to-date picker is limited to it. */
        val firstDay: LocalDate,
        val lastDay: LocalDate,
        val greeting: Greeting,
        val streak: Int,
        val isEmpty: Boolean,
    ) : TimelineUiState
}

@HiltViewModel
class TimelineViewModel @Inject constructor(
    postRepository: PostRepository,
    diaryRepository: DiaryRepository,
    moodRepository: MoodRepository,
    private val clock: Clock,
    private val savedStateHandle: SavedStateHandle,
    @Dispatcher(DakilioDispatchers.Default) default: CoroutineDispatcher,
) : ViewModel() {
    private val selectedDiaryId = savedStateHandle.getStateFlow<Long?>(KEY_DIARY, null)
    private val today = MutableStateFlow(LocalDate.now(clock))
    private val expandedStretches = MutableStateFlow(emptySet<Long>())

    private val inputs = combine(postRepository.posts, diaryRepository.diaries, moodRepository.moods, ::Triple)

    val uiState: StateFlow<TimelineUiState> =
        combine(inputs, selectedDiaryId, today, expandedStretches) { (posts, diaries, moods), selected, today, expanded ->
            // A filter pointing at a deleted diary falls back to "All".
            val filter = selected?.takeIf { id -> diaries.any { it.id == id } }
            val visibleDays = posts.asSequence()
                .filter { filter == null || filter in it.diaryIds }
                .map { it.date }
                .toSet()
            val bounds = timelineBounds(posts, today)
            TimelineUiState.Ready(
                items = buildTimeline(posts, diaries, filter, today, expanded),
                diaries = diaries,
                selectedDiaryId = filter,
                moods = moods.primaries(), // the check-in card offers one face per level
                today = today,
                firstDay = bounds.start,
                lastDay = bounds.endInclusive,
                greeting = greetingFor(LocalTime.now(clock)),
                streak = Streaks.current(visibleDays, today),
                isEmpty = posts.isEmpty(),
            )
        }
            .flowOn(default)
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), TimelineUiState.Loading)

    fun selectDiary(id: Long?) {
        savedStateHandle[KEY_DIARY] = id
    }

    fun toggleStretch(newestEpochDay: Long) {
        expandedStretches.update { if (newestEpochDay in it) it - newestEpochDay else it + newestEpochDay }
    }

    /** Idempotent, unlike [toggleStretch]: a jump into a stretch may ask more than once. */
    fun expandStretch(newestEpochDay: Long) {
        expandedStretches.update { it + newestEpochDay }
    }

    /** Called on resume so the list rolls over to a new day without restarting the app. */
    fun refreshToday() {
        today.value = LocalDate.now(clock)
    }

    private fun greetingFor(time: LocalTime) = when (time.hour) {
        in 5..11 -> Greeting.Morning
        in 12..16 -> Greeting.Afternoon
        in 17..21 -> Greeting.Evening
        else -> Greeting.Night
    }

    private companion object {
        const val KEY_DIARY = "selected_diary"
    }
}
