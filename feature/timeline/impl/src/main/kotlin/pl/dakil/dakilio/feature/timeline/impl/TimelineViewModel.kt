package pl.dakil.dakilio.feature.timeline.impl

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import java.time.Clock
import java.time.LocalDate
import java.time.LocalTime
import java.time.YearMonth
import javax.inject.Inject
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import pl.dakil.dakilio.core.common.DakilioDispatchers
import pl.dakil.dakilio.core.common.Dispatcher
import pl.dakil.dakilio.core.data.repository.DiaryRepository
import pl.dakil.dakilio.core.data.repository.MoodRepository
import pl.dakil.dakilio.core.data.repository.PostRepository
import pl.dakil.dakilio.core.data.repository.SettingsRepository
import pl.dakil.dakilio.core.model.Diary
import pl.dakil.dakilio.core.model.Mood
import pl.dakil.dakilio.core.model.PreviewLength
import pl.dakil.dakilio.core.model.Streaks

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
        /** Entry counts and average moods per month in the filter, for the month picker. */
        val months: Map<YearMonth, MonthSummary>,
        /** The month navigator is on (a view option). */
        val monthNavigator: Boolean,
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
    private val settingsRepository: SettingsRepository,
    private val clock: Clock,
    @Dispatcher(DakilioDispatchers.Default) default: CoroutineDispatcher,
) : ViewModel() {
    // Persisted, so the timeline reopens on the diary picked last time.
    private val selectedDiaryId = settingsRepository.settings.map { it.timelineDiaryId }.distinctUntilChanged()
    private val today = MutableStateFlow(LocalDate.now(clock))
    private val expandedStretches = MutableStateFlow(emptySet<Long>())

    private val inputs = combine(postRepository.posts, diaryRepository.diaries, moodRepository.moods, ::Triple)
    private val monthNavigator = settingsRepository.settings.map { it.monthNavigator }.distinctUntilChanged()

    val uiState: StateFlow<TimelineUiState> =
        combine(inputs, selectedDiaryId, today, expandedStretches, monthNavigator) { (posts, diaries, moods), selected, today, expanded, showMonths ->
            // A filter pointing at a deleted diary falls back to "All".
            val filter = selected?.takeIf { id -> diaries.any { it.id == id } }
            val visibleDays = posts.asSequence()
                .filter { filter == null || it.diaryId == filter }
                .map { it.date }
                .toSet()
            val bounds = timelineBounds(posts, today)
            TimelineUiState.Ready(
                items = buildTimeline(posts, diaries, filter, today, expanded),
                diaries = diaries,
                selectedDiaryId = filter,
                moods = moods, // the check-in card: one face per level, aliases on tap
                today = today,
                firstDay = bounds.start,
                lastDay = bounds.endInclusive,
                months = monthSummaries(posts, filter),
                monthNavigator = showMonths,
                greeting = greetingFor(LocalTime.now(clock)),
                streak = Streaks.current(visibleDays, today),
                isEmpty = posts.isEmpty(),
            )
        }
            .flowOn(default)
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), TimelineUiState.Loading)

    fun selectDiary(id: Long?) {
        viewModelScope.launch { settingsRepository.setTimelineDiaryId(id) }
    }

    fun toggleStretch(newestEpochDay: Long) {
        expandedStretches.update { if (newestEpochDay in it) it - newestEpochDay else it + newestEpochDay }
    }

    /** Idempotent, unlike [toggleStretch]: a jump into a stretch may ask more than once. */
    fun expandStretch(newestEpochDay: Long) {
        expandedStretches.update { it + newestEpochDay }
    }

    // View options. Their current values reach the UI through `LocalEntryDisplay`, like every other
    // PostCard, so only the setters live here.
    fun setPreviewLength(length: PreviewLength) {
        viewModelScope.launch { settingsRepository.setPreviewLength(length) }
    }

    fun setCompactTags(compact: Boolean) {
        viewModelScope.launch { settingsRepository.setCompactTags(compact) }
    }

    fun setMonthNavigator(show: Boolean) {
        viewModelScope.launch { settingsRepository.setMonthNavigator(show) }
    }

    fun setTimelineTextScale(scale: Float) {
        viewModelScope.launch { settingsRepository.setTimelineTextScale(scale) }
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
}
