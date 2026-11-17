package pl.dakil.dakilio.feature.insights.impl

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import java.time.Clock
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.YearMonth
import javax.inject.Inject
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import pl.dakil.dakilio.core.common.DakilioDispatchers
import pl.dakil.dakilio.core.common.Dispatcher
import pl.dakil.dakilio.core.data.repository.DiaryRepository
import pl.dakil.dakilio.core.data.repository.HabitRepository
import pl.dakil.dakilio.core.data.repository.MoodRepository
import pl.dakil.dakilio.core.data.repository.PostRepository
import pl.dakil.dakilio.core.data.repository.SettingsRepository
import pl.dakil.dakilio.core.model.DateRange
import pl.dakil.dakilio.core.model.Diary
import pl.dakil.dakilio.core.model.Habit
import pl.dakil.dakilio.core.model.Mood
import pl.dakil.dakilio.core.model.MonthSummary
import pl.dakil.dakilio.core.model.Post
import pl.dakil.dakilio.core.model.monthSummaries
import pl.dakil.dakilio.core.model.primaries
import pl.dakil.dakilio.feature.insights.api.InsightsScope
import pl.dakil.dakilio.feature.insights.impl.logic.Bounds
import pl.dakil.dakilio.feature.insights.impl.logic.Comparison
import pl.dakil.dakilio.feature.insights.impl.logic.InsightsIndex
import pl.dakil.dakilio.feature.insights.impl.logic.InsightsPeriod
import pl.dakil.dakilio.feature.insights.impl.logic.PeriodKind
import pl.dakil.dakilio.feature.insights.impl.logic.canGoBack
import pl.dakil.dakilio.feature.insights.impl.logic.canGoForward
import pl.dakil.dakilio.feature.insights.impl.logic.comparisonRange
import pl.dakil.dakilio.feature.insights.impl.logic.decodeComparison
import pl.dakil.dakilio.feature.insights.impl.logic.decodePeriod
import pl.dakil.dakilio.feature.insights.impl.logic.encode
import pl.dakil.dakilio.feature.insights.impl.logic.isInProgress
import pl.dakil.dakilio.feature.insights.impl.logic.next
import pl.dakil.dakilio.feature.insights.impl.logic.previous
import pl.dakil.dakilio.feature.insights.impl.logic.range
import pl.dakil.dakilio.feature.insights.impl.logic.switchTo

/**
 * The journal as every Insights screen needs it, under one diary filter.
 *
 * @property index the entries of the selected diary (or all), sorted into days.
 * @property allPosts every entry of every diary: habits count them all.
 * @property primaries one mood per level; charts work per level.
 * @property diaryId the diary filter, already checked against [diaries].
 * @property months per-month summaries of [index], for the pickers and the years grid.
 * @property habits every habit (the Habits extra), in the user's order.
 */
class InsightsData(
    val index: InsightsIndex,
    val allPosts: List<Post>,
    val primaries: List<Mood>,
    val diaries: List<Diary>,
    val diaryId: Long?,
    val firstDay: DayOfWeek,
    val today: LocalDate,
    val months: Map<YearMonth, MonthSummary>,
    val habits: List<Habit>,
) {
    val hasAnyEntries: Boolean get() = allPosts.isNotEmpty()

    /** From the first entry under the filter to today. */
    val bounds: Bounds get() = Bounds(index.first ?: today, today)
}

/** [InsightsData] with the period and comparison a screen is set to. */
class InsightsFrame(val data: InsightsData, val period: InsightsPeriod, val comparison: Comparison) {
    val bounds: Bounds get() = data.bounds
    val range: DateRange get() = period.range(bounds)
    val comparisonRange: DateRange? get() = comparisonRange(period, comparison, bounds)

    val header: PeriodHeaderState
        get() = PeriodHeaderState(
            period = period,
            bounds = bounds,
            inProgress = period.isInProgress(bounds),
            canGoBack = period.canGoBack(bounds),
            canGoForward = period.canGoForward(bounds),
            months = data.months,
            comparison = comparison,
        )
}

/** Builds [InsightsData] from the repositories; one per ViewModel. */
class InsightsSource @Inject constructor(
    private val postRepository: PostRepository,
    private val diaryRepository: DiaryRepository,
    private val moodRepository: MoodRepository,
    private val settingsRepository: SettingsRepository,
    private val habitRepository: HabitRepository,
    private val clock: Clock,
    @Dispatcher(DakilioDispatchers.Default) val default: CoroutineDispatcher,
) {
    fun data(diaryId: Flow<Long?>): Flow<InsightsData> =
        combine(
            combine(postRepository.posts, habitRepository.habits.map { list -> list.map { it.habit } }, ::Pair),
            diaryRepository.diaries,
            moodRepository.moods,
            settingsRepository.firstDayOfWeek,
            diaryId,
        ) { (posts, habits), diaries, moods, firstDay, selected ->
            val filter = selected?.takeIf { id -> diaries.any { it.id == id } }
            val filtered = if (filter == null) posts else posts.filter { it.diaryId == filter }
            InsightsData(
                index = InsightsIndex(filtered),
                allPosts = posts,
                primaries = moods.primaries(),
                diaries = diaries,
                diaryId = filter,
                firstDay = firstDay,
                today = LocalDate.now(clock),
                months = monthSummaries(filtered, null),
                habits = habits,
            )
        }.flowOn(default)
}

/**
 * The base of every Insights screen's ViewModel: it keeps the period, the comparison and the diary
 * filter in saved state (starting from the [InsightsScope] the screen was opened with) and offers
 * [frames] to build the screen's state from.
 */
abstract class InsightsScopedViewModel(
    source: InsightsSource,
    private val savedStateHandle: SavedStateHandle,
    initial: InsightsScope = InsightsScope(),
) : ViewModel() {
    private val period: StateFlow<String?> = savedStateHandle.getStateFlow(KEY_PERIOD, initial.period)
    private val comparison: StateFlow<String?> = savedStateHandle.getStateFlow(KEY_COMPARISON, initial.comparison)
    private val diaryId: StateFlow<Long?> = savedStateHandle.getStateFlow(KEY_DIARY, initial.diaryId)

    @Volatile
    private var last: InsightsFrame? = null

    /** The data under the current scope; each emission is the latest for [step] and friends. */
    protected val frames: Flow<InsightsFrame> =
        combine(source.data(diaryId), period, comparison) { data, encodedPeriod, encodedComparison ->
            val current = decodePeriod(encodedPeriod) ?: InsightsPeriod.Month(YearMonth.from(data.today))
            InsightsFrame(data, current, decodeComparison(encodedComparison))
        }.onEach { last = it }

    /** The scope to open another Insights screen with. */
    val scope: InsightsScope
        get() = InsightsScope(period.value ?: last?.period?.encode(), last?.data?.diaryId ?: diaryId.value, comparison.value)

    /** The entries of one day under the diary filter, earliest first: what the day sheet lists. */
    fun postsOn(date: LocalDate): List<Post> = last?.data?.index?.day(date)?.posts.orEmpty()

    /** To the period after (`forward`) or before, when there is one inside the journal. */
    fun step(forward: Boolean) {
        val frame = last ?: return
        val allowed = if (forward) frame.period.canGoForward(frame.bounds) else frame.period.canGoBack(frame.bounds)
        if (!allowed) return
        (if (forward) frame.period.next() else frame.period.previous())?.let(::setPeriod)
    }

    /** Week, month, year or all time, keeping the same moment in view. */
    fun setKind(kind: PeriodKind) {
        val frame = last ?: return
        if (frame.period.kind != kind) setPeriod(frame.period.switchTo(kind, frame.bounds, frame.data.firstDay))
    }

    fun setPeriod(value: InsightsPeriod) {
        savedStateHandle[KEY_PERIOD] = value.encode()
    }

    fun setComparison(value: Comparison) {
        savedStateHandle[KEY_COMPARISON] = value.encode()
    }

    fun selectDiary(id: Long?) {
        savedStateHandle[KEY_DIARY] = id
    }

    private companion object {
        const val KEY_PERIOD = "period"
        const val KEY_COMPARISON = "comparison"
        const val KEY_DIARY = "diary"
    }
}
