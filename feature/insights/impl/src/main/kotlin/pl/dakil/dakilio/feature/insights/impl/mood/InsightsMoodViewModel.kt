package pl.dakil.dakilio.feature.insights.impl.mood

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
import pl.dakil.dakilio.core.model.Diary
import pl.dakil.dakilio.core.model.Mood
import pl.dakil.dakilio.feature.insights.api.InsightsMoodKey
import pl.dakil.dakilio.feature.insights.impl.InsightsScopedViewModel
import pl.dakil.dakilio.feature.insights.impl.InsightsSource
import pl.dakil.dakilio.feature.insights.impl.PeriodHeaderState
import pl.dakil.dakilio.feature.insights.impl.logic.Granularity
import pl.dakil.dakilio.feature.insights.impl.logic.MoodCount
import pl.dakil.dakilio.feature.insights.impl.logic.MoodDay
import pl.dakil.dakilio.feature.insights.impl.logic.MoodFlow
import pl.dakil.dakilio.feature.insights.impl.logic.MoodSummary
import pl.dakil.dakilio.feature.insights.impl.logic.Steadiness
import pl.dakil.dakilio.feature.insights.impl.logic.averageOrNull
import pl.dakil.dakilio.feature.insights.impl.logic.brightestDays
import pl.dakil.dakilio.feature.insights.impl.logic.defaultGranularity
import pl.dakil.dakilio.feature.insights.impl.logic.granularities
import pl.dakil.dakilio.feature.insights.impl.logic.moodFlow
import pl.dakil.dakilio.feature.insights.impl.logic.rollingAverage
import pl.dakil.dakilio.feature.insights.impl.logic.moodNames
import pl.dakil.dakilio.feature.insights.impl.logic.moodSummary
import pl.dakil.dakilio.feature.insights.impl.logic.steadiness
import pl.dakil.dakilio.feature.insights.impl.logic.toughestDays

/** Standout days are listed from this many days with a mood, so the two lists aren't the same days. */
private const val STANDOUT_FROM_DAYS = 6
private const val STANDOUT_DAYS = 3

/** A period's headline numbers, for Now against Then. */
data class PeriodNumbers(val average: Float?, val entries: Int, val days: Int)

sealed interface InsightsMoodUiState {
    data object Loading : InsightsMoodUiState

    /**
     * @property granularities the slices the period can be cut into; [flow] uses the chosen one.
     * @property names the exact moods picked, aliases apart, the most used first.
     * @property brightest, toughest empty while the period has too few days with a mood.
     * @property diaries by id, for the day sheet's cards; empty under a diary filter.
     * @property comparisonFlow the comparison period's points in the same slices, `null` without one.
     * @property then the comparison period's numbers, `null` without a comparison.
     */
    data class Ready(
        val header: PeriodHeaderState,
        val flow: MoodFlow,
        val granularities: List<Granularity>,
        val summary: MoodSummary,
        val steadiness: Steadiness?,
        val names: List<MoodCount>,
        val brightest: List<MoodDay>,
        val toughest: List<MoodDay>,
        val comparisonFlow: List<Float?>?,
        val now: PeriodNumbers,
        val then: PeriodNumbers?,
        val moods: List<Mood>,
        val diaries: Map<Long, Diary>,
        val selectedDiaryId: Long?,
        val firstDay: DayOfWeek,
        val today: LocalDate,
    ) : InsightsMoodUiState
}

@HiltViewModel(assistedFactory = InsightsMoodViewModel.Factory::class)
class InsightsMoodViewModel @AssistedInject constructor(
    source: InsightsSource,
    private val savedStateHandle: SavedStateHandle,
    @Assisted key: InsightsMoodKey,
) : InsightsScopedViewModel(source, savedStateHandle, key.scope) {
    /** The slice picked by hand; `null` (or one the period can't use) falls back to the period's own. */
    private val granularity = savedStateHandle.getStateFlow<String?>(KEY_GRANULARITY, null)

    val uiState: StateFlow<InsightsMoodUiState> =
        combine(frames, granularity) { frame, picked ->
            val data = frame.data
            val range = frame.range
            val days = data.index.days(range)
            val posts = days.flatMap { it.posts }
            val allowed = granularities(range)
            val slice = Granularity.entries.firstOrNull { it.name == picked && it in allowed } ?: defaultGranularity(range)
            val enoughDays = days.count { it.mood != null } >= STANDOUT_FROM_DAYS
            val compared = frame.comparisonRange
            val comparedDays = compared?.let(data.index::days)
            val flow = moodFlow(data.index, range, slice, data.firstDay)
            InsightsMoodUiState.Ready(
                header = frame.header,
                flow = flow,
                comparisonFlow = compared?.takeIf { comparedDays!!.any { it.mood != null } }
                    ?.let { other ->
                        val points = moodFlow(data.index, other, slice, data.firstDay).buckets.map { bucket -> bucket.average }
                        // Like for like: when this period's line is the 7-day average, so is the other's.
                        if (flow.trend != null) rollingAverage(points) else points
                    },
                now = PeriodNumbers(days.flatMap { it.levels }.averageOrNull(), posts.size, days.size),
                then = comparedDays?.let { other -> PeriodNumbers(other.flatMap { it.levels }.averageOrNull(), other.sumOf { it.entries }, other.size) },
                granularities = allowed,
                summary = moodSummary(posts, data.primaries),
                steadiness = steadiness(days.mapNotNull { it.mood }),
                names = moodNames(posts),
                brightest = if (enoughDays) brightestDays(days, STANDOUT_DAYS) else emptyList(),
                toughest = if (enoughDays) toughestDays(days, STANDOUT_DAYS) else emptyList(),
                moods = data.primaries,
                diaries = if (data.diaryId == null && data.diaries.size > 1) data.diaries.associateBy { it.id } else emptyMap(),
                selectedDiaryId = data.diaryId,
                firstDay = data.firstDay,
                today = data.today,
            ) as InsightsMoodUiState
        }
            .flowOn(source.default)
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), InsightsMoodUiState.Loading)

    fun setGranularity(value: Granularity) {
        savedStateHandle[KEY_GRANULARITY] = value.name
    }

    @AssistedFactory
    interface Factory {
        fun create(key: InsightsMoodKey): InsightsMoodViewModel
    }

    private companion object {
        const val KEY_GRANULARITY = "granularity"
    }
}
