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
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import java.time.YearMonth
import pl.dakil.dakilio.core.data.repository.SettingsRepository
import pl.dakil.dakilio.core.model.RecapOffer
import pl.dakil.dakilio.core.model.Recaps
import pl.dakil.dakilio.core.model.UserSettings
import pl.dakil.dakilio.feature.insights.impl.logic.Bounds
import pl.dakil.dakilio.feature.insights.impl.logic.InsightsPeriod
import pl.dakil.dakilio.feature.insights.impl.logic.RECAP_MIN_ENTRIES
import pl.dakil.dakilio.feature.insights.impl.logic.calendarRange
import kotlinx.coroutines.flow.stateIn
import pl.dakil.dakilio.core.model.Diary
import pl.dakil.dakilio.core.model.Mood
import pl.dakil.dakilio.feature.insights.impl.logic.Overview
import pl.dakil.dakilio.feature.insights.impl.logic.Highlight
import pl.dakil.dakilio.feature.insights.impl.logic.buildOverview
import pl.dakil.dakilio.feature.insights.impl.logic.highlights
import pl.dakil.dakilio.feature.insights.impl.logic.latestWithEntries

sealed interface InsightsUiState {
    data object Loading : InsightsUiState

    /**
     * @property moods one primary per level.
     * @property hasAnyEntries whether the journal has entries at all (in any diary or period).
     */
    data class Ready(
        val header: PeriodHeaderState,
        val overview: Overview,
        val highlights: List<Highlight>,
        /** For an empty period: the latest one of its kind with entries, to jump back to. */
        val latestWithEntries: InsightsPeriod? = null,
        val moods: List<Mood>,
        val diaries: List<Diary>,
        val selectedDiaryId: Long?,
        val firstDay: DayOfWeek,
        val today: LocalDate,
        val hasAnyEntries: Boolean,
        val offers: List<OfferedRecap> = emptyList(),
    ) : InsightsUiState
}

/** A recap on offer, with the average mood of its period (every diary) for the card's face. */
data class OfferedRecap(val offer: RecapOffer, val period: InsightsPeriod, val average: Float?)

/** The Insights tab: one period at a glance. */
@HiltViewModel
class InsightsViewModel @Inject constructor(
    source: InsightsSource,
    private val settingsRepository: SettingsRepository,
    savedStateHandle: SavedStateHandle,
) : InsightsScopedViewModel(source, savedStateHandle) {
    val uiState: StateFlow<InsightsUiState> = combine(frames, settingsRepository.settings) { frame, settings -> frame to settings }
        .map<Pair<InsightsFrame, UserSettings>, InsightsUiState> { (frame, settings) ->
            val data = frame.data
            val overview = buildOverview(
                index = data.index,
                period = frame.period,
                bounds = frame.bounds,
                comparison = frame.comparison,
                primaries = data.primaries,
                firstDay = data.firstDay,
                diaries = data.diaries.takeIf { data.diaryId == null },
                habits = data.habits,
                allPosts = data.allPosts,
            )
            InsightsUiState.Ready(
                header = frame.header,
                overview = overview,
                highlights = highlights(overview, data.index, frame.period, frame.bounds),
                latestWithEntries = if (overview.isEmpty) latestWithEntries(data.index, frame.period, frame.bounds, data.firstDay) else null,
                moods = data.primaries,
                diaries = data.diaries,
                selectedDiaryId = data.diaryId,
                firstDay = data.firstDay,
                today = data.today,
                hasAnyEntries = data.hasAnyEntries,
                offers = if (settings.recaps) offeredRecaps(data, settings) else emptyList(),
            )
        }
        .flowOn(source.default)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), InsightsUiState.Loading)

    /** Hides [offer] for good; the next month's or year's recap still comes. */
    fun dismiss(offer: RecapOffer) {
        viewModelScope.launch {
            when (offer) {
                is RecapOffer.Month -> settingsRepository.dismissMonthRecap(offer.month.toString())
                is RecapOffer.Year -> settingsRepository.dismissYearRecap(offer.year)
            }
        }
    }
}

/** The recaps on offer today that have enough entries (in every diary) to be worth playing. */
private fun offeredRecaps(data: InsightsData, settings: UserSettings): List<OfferedRecap> {
    val dismissedMonth = settings.recapDismissedMonth?.let { runCatching { YearMonth.parse(it) }.getOrNull() }
    return Recaps.offers(data.today, dismissedMonth, settings.recapDismissedYear).mapNotNull { offer ->
        val period = when (offer) {
            is RecapOffer.Month -> InsightsPeriod.Month(offer.month)
            is RecapOffer.Year -> InsightsPeriod.Year(offer.year)
        }
        val range = period.calendarRange(Bounds(data.today, data.today))
        val posts = data.allPosts.filter { it.date in range }
        if (posts.size < RECAP_MIN_ENTRIES) return@mapNotNull null
        OfferedRecap(offer, period, posts.mapNotNull { it.mood?.level }.takeIf { it.isNotEmpty() }?.average()?.toFloat())
    }
}
