package pl.dakil.dakilio.feature.insights.impl.logic

import java.time.DayOfWeek
import java.time.LocalDate
import java.time.YearMonth
import pl.dakil.dakilio.core.model.DateRange
import pl.dakil.dakilio.core.model.Diary
import pl.dakil.dakilio.core.model.Habit
import pl.dakil.dakilio.core.model.Mood
import pl.dakil.dakilio.core.model.MonthSummary
import pl.dakil.dakilio.core.model.Post
import pl.dakil.dakilio.core.model.Streaks

/** The numbers a period is measured against, from its comparison period. */
data class ComparisonStats(val range: DateRange, val entries: Int, val averageMood: Float?)

/**
 * How a period's days are drawn on the overview's calendar card.
 *
 * @property range the days that can hold entries (up to today).
 * @property layoutRange the days laid out: a week, a month or a year is drawn whole, with the days
 *   still to come dimmed; anything else ends today.
 */
data class CalendarData(val form: CalendarForm, val range: DateRange, val layoutRange: DateRange, val cells: Map<LocalDate, DayCell>)

/** A diary's share of a period. */
data class DiaryStat(val diary: Diary, val entries: Int, val averageMood: Float?)

/** Something the overview can't show yet, and how far along it is (0..1). */
data class Gathering(val kind: Kind, val progress: Float) {
    enum class Kind { MoodFlow, TagLinks, BestDay }
}

/**
 * Everything the overview shows for one period.
 *
 * @property range the days the numbers cover (the period up to today).
 * @property streak the current streak when the period includes today, otherwise the longest run
 *   inside the period ([streakIsCurrent] says which).
 * @property comparison `null` without a comparison period, or when that period has no entries.
 * @property diaries `null` unless all diaries are shown and there's more than one.
 * @property records only for a year or longer.
 * @property habits the habits that had begun by the end of the period (entries of every diary count).
 */
data class Overview(
    val range: DateRange,
    val entries: Int,
    val daysJournaled: Int,
    val streak: Int,
    val streakIsCurrent: Boolean,
    val mood: MoodSummary,
    val steadiness: Steadiness?,
    val comparison: ComparisonStats?,
    val flow: MoodFlow,
    val calendar: CalendarData,
    val tags: List<TagStat>,
    val rhythm: Rhythm,
    val writing: WritingStats,
    val diaries: List<DiaryStat>?,
    val records: Records?,
    val gathering: List<Gathering>,
    val habits: List<HabitStat> = emptyList(),
) {
    val isEmpty: Boolean get() = entries == 0

    /** Level change against the comparison period, `null` when either side has no mood. */
    val moodChange: Float?
        get() {
            val now = mood.average ?: return null
            val before = comparison?.averageMood ?: return null
            return now - before
        }
}

/** A period long enough for records: a calendar year, all time, or a custom range of half a year. */
private const val RECORDS_FROM_DAYS = 180

/**
 * @param index the entries under the diary filter.
 * @param diaries every diary, when all of them are shown (for the diary split); `null` under a filter.
 */
fun buildOverview(
    index: InsightsIndex,
    period: InsightsPeriod,
    bounds: Bounds,
    comparison: Comparison,
    primaries: List<Mood>,
    firstDay: DayOfWeek,
    diaries: List<Diary>? = null,
    habits: List<Habit> = emptyList(),
    allPosts: List<Post> = emptyList(),
): Overview {
    val range = period.range(bounds)
    val days = index.days(range)
    val posts = days.flatMap { it.posts }
    val includesToday = bounds.today in range
    val streak = if (includesToday) Streaks.current(index.dates, bounds.today) else Streaks.longestWithin(index.dates, range)?.days ?: 0

    val compared = comparisonRange(period, comparison, bounds)
    val comparedDays = compared?.let(index::days)
    val comparisonStats = if (compared != null && !comparedDays.isNullOrEmpty()) {
        ComparisonStats(compared, comparedDays.sumOf { it.entries }, comparedDays.flatMap { it.levels }.averageOrNull())
    } else {
        null
    }

    val mood = moodSummary(posts, primaries)
    val flow = moodFlow(index, range, defaultGranularity(range), firstDay)
    val tags = tagStats(days, comparedDays?.takeIf { it.isNotEmpty() })
    val rhythm = rhythm(posts)
    return Overview(
        range = range,
        entries = posts.size,
        daysJournaled = days.size,
        streak = streak,
        streakIsCurrent = includesToday,
        mood = mood,
        steadiness = steadiness(days.mapNotNull { it.mood }),
        comparison = comparisonStats,
        flow = flow,
        calendar = CalendarData(
            form = calendarForm(range),
            range = range,
            layoutRange = if (period == InsightsPeriod.AllTime || period is InsightsPeriod.Custom) range else period.calendarRange(bounds),
            cells = dayCells(index, range),
        ),
        tags = tags,
        rhythm = rhythm,
        writing = writingStats(posts),
        diaries = diaries?.takeIf { it.size > 1 }?.let { diaryStats(posts, it) },
        // The first entry of a year is just its first day; only all time makes it worth telling.
        records = if (range.days >= RECORDS_FROM_DAYS) records(days).let { if (period == InsightsPeriod.AllTime) it else it.copy(firstEntry = null) } else null,
        gathering = gathering(days, flow, tags, rhythm),
        habits = habitStats(habits, allPosts, range, compared, firstDay, bounds.today).filter { it.weeks.isNotEmpty() },
    )
}

/** Every diary with its entries in [posts], the busiest first; diaries without any are left out. */
fun diaryStats(posts: List<Post>, diaries: List<Diary>): List<DiaryStat> {
    val byDiary = posts.groupBy { it.diaryId }
    return diaries.mapNotNull { diary ->
        val diaryPosts = byDiary[diary.id] ?: return@mapNotNull null
        DiaryStat(diary, diaryPosts.size, diaryPosts.mapNotNull { it.mood?.level }.averageOrNull())
    }.sortedWith(compareByDescending<DiaryStat> { it.entries }.thenBy { it.diary.position })
}

/** What's still locked for a period with entries, each with its progress; nothing once all is shown. */
internal fun gathering(days: List<DayStat>, flow: MoodFlow, tags: List<TagStat>, rhythm: Rhythm): List<Gathering> {
    if (days.isEmpty()) return emptyList()
    val result = ArrayList<Gathering>()
    if (flow.points < 2) result += Gathering(Gathering.Kind.MoodFlow, flow.points / 2f)
    if (tags.none { it.link != null }) {
        val moodDays = days.filter { it.mood != null }
        // The tag closest to having enough days on both sides.
        val best = tags.maxOfOrNull { stat ->
            val with = moodDays.count { stat.tag.id in it.tagIds }
            minOf(with, MIN_LINK_DAYS) + minOf(moodDays.size - with, MIN_LINK_DAYS)
        } ?: minOf(moodDays.size, MIN_LINK_DAYS)
        result += Gathering(Gathering.Kind.TagLinks, best / (2f * MIN_LINK_DAYS))
    }
    if (rhythm.weekdays.values.count { it.moodEntries >= MIN_SLOT_MOODS } < 2) {
        val top = rhythm.weekdays.values.map { minOf(it.moodEntries, MIN_SLOT_MOODS) }.sortedDescending().take(2).sum()
        result += Gathering(Gathering.Kind.BestDay, top / (2f * MIN_SLOT_MOODS))
    }
    return result
}

/** The month summaries a years grid shows, from the entries under the diary filter. */
fun InsightsIndex.monthSummaries(): Map<YearMonth, MonthSummary> = pl.dakil.dakilio.core.model.monthSummaries(posts, null)

/**
 * For a period with no entries: the latest period of the same kind, before it, that has some, so an
 * empty view can offer a way back. `null` when there's none earlier (or for all time and custom ranges).
 */
fun latestWithEntries(index: InsightsIndex, period: InsightsPeriod, bounds: Bounds, firstDay: DayOfWeek): InsightsPeriod? {
    if (period == InsightsPeriod.AllTime || period is InsightsPeriod.Custom) return null
    val start = period.calendarRange(bounds).from
    val latest = index.posts.lastOrNull { it.date < start }?.date ?: return null
    return InsightsPeriod.of(period.kind, latest, firstDay)
}
