package pl.dakil.dakilio.feature.timeline.impl

import java.time.LocalDate
import java.time.YearMonth
import pl.dakil.dakilio.core.model.Diary
import pl.dakil.dakilio.core.model.Mood
import pl.dakil.dakilio.core.model.Post

/** One row of the timeline list. [key] is stable across updates for LazyColumn item animations. */
sealed interface TimelineItem {
    val key: String

    data class MonthDivider(val month: YearMonth) : TimelineItem {
        override val key = "month-$month"
    }

    data class DayHeader(val date: LocalDate, val moods: List<Mood>) : TimelineItem {
        override val key = "day-${date.toEpochDay()}"
    }

    /** "How's your journey today?" check-in card (today has nothing in the current filter). */
    data class TodayPrompt(val date: LocalDate) : TimelineItem {
        override val key = "today-prompt"
    }

    /** [diary] labels the card when the list mixes diaries. */
    data class PostItem(val post: Post, val index: Int, val count: Int, val diary: Diary?) : TimelineItem {
        override val key = "post-${post.id}"
    }

    /**
     * A compact, self-labelled row for a day without entries in the current filter. [otherDiaries]
     * hints at diaries that do have entries that day (only when filtering by one diary).
     */
    data class EmptyDay(val date: LocalDate, val otherDiaries: List<Diary> = emptyList()) : TimelineItem {
        override val key = "empty-${date.toEpochDay()}"
    }

    /**
     * Several empty days in a row, collapsed into one row (expandable into [EmptyDay]s).
     * [otherDiaries] is the union of its days' other diaries, so the collapsed row can hint at them.
     */
    data class QuietStretch(
        val newest: LocalDate,
        val oldest: LocalDate,
        val expanded: Boolean,
        val otherDiaries: List<Diary> = emptyList(),
    ) : TimelineItem {
        override val key = "quiet-${newest.toEpochDay()}"
        val days: Int get() = (newest.toEpochDay() - oldest.toEpochDay() + 1).toInt()
    }

    data class OtherDiaries(val date: LocalDate, val chips: List<DiaryCount>) : TimelineItem {
        override val key = "others-${date.toEpochDay()}"
    }
}

data class DiaryCount(val diary: Diary, val count: Int)

/** Where a day sits in a built timeline, for jumping to it. */
sealed interface DayLocation {
    /** The day's own row: its [TimelineItem.DayHeader], or its [TimelineItem.EmptyDay]. */
    data class At(val index: Int) : DayLocation

    /** Hidden inside a collapsed [TimelineItem.QuietStretch]; expand it, then locate again. */
    data class InStretch(val newestEpochDay: Long) : DayLocation

    /** Outside the timeline's range. */
    data object Missing : DayLocation
}

internal fun List<TimelineItem>.locateDay(date: LocalDate): DayLocation {
    val index = indexOfFirst {
        (it is TimelineItem.DayHeader && it.date == date) || (it is TimelineItem.EmptyDay && it.date == date)
    }
    if (index >= 0) return DayLocation.At(index)
    val stretch = firstOrNull { it is TimelineItem.QuietStretch && !it.expanded && date in it.oldest..it.newest }
        as TimelineItem.QuietStretch?
    return stretch?.let { DayLocation.InStretch(it.newest.toEpochDay()) } ?: DayLocation.Missing
}

/** The days the timeline covers: from the oldest entry (or today) to today (or a future-dated entry). */
internal fun timelineBounds(posts: List<Post>, today: LocalDate): ClosedRange<LocalDate> {
    var oldest = today
    var newest = today
    posts.forEach {
        if (it.date < oldest) oldest = it.date
        if (it.date > newest) newest = it.date
    }
    return oldest..newest
}

/** Runs of at least this many empty days collapse into a [TimelineItem.QuietStretch]. */
internal const val QUIET_STRETCH_MIN_DAYS = 3

/**
 * Lays out every day from the newest one (today, or a future-dated entry) back to the oldest entry.
 *
 * @param selectedDiaryId the timeline filter; `null` shows all diaries.
 * @param expandedStretches newest-day epoch days of quiet stretches the user expanded.
 */
fun buildTimeline(
    posts: List<Post>,
    diaries: List<Diary>,
    selectedDiaryId: Long?,
    today: LocalDate,
    expandedStretches: Set<Long> = emptySet(),
): List<TimelineItem> {
    val diaryById = diaries.associateBy { it.id }
    val labelDiaries = selectedDiaryId == null && diaries.size > 1
    val postsByDate = posts.groupBy { it.date }
    val bounds = timelineBounds(posts, today)
    val newest = bounds.endInclusive
    val oldest = bounds.start

    val items = ArrayList<TimelineItem>(posts.size + 32)
    /** Days without entries in the filter, each with the other diaries that have entries that day. */
    val emptyRun = ArrayList<Pair<LocalDate, List<Diary>>>()
    var lastMonth: YearMonth? = null

    fun monthDivider(date: LocalDate) {
        val month = YearMonth.from(date)
        if (lastMonth != null && month != lastMonth) items += TimelineItem.MonthDivider(month)
        lastMonth = month
    }

    fun flushEmptyRun() {
        if (emptyRun.isEmpty()) return
        if (emptyRun.size >= QUIET_STRETCH_MIN_DAYS) {
            val newestDay = emptyRun.first().first
            val stretch = TimelineItem.QuietStretch(
                newest = newestDay,
                oldest = emptyRun.last().first,
                expanded = newestDay.toEpochDay() in expandedStretches,
                otherDiaries = emptyRun.flatMap { it.second }.distinct().sortedBy { it.position },
            )
            monthDivider(stretch.newest)
            items += stretch
            if (stretch.expanded) emptyRun.forEach { (day, others) -> items += TimelineItem.EmptyDay(day, others) }
        } else {
            emptyRun.forEach { (day, others) ->
                monthDivider(day)
                items += TimelineItem.EmptyDay(day, others)
            }
        }
        emptyRun.clear()
    }

    var date = newest
    while (!date.isBefore(oldest)) {
        val dayPosts = postsByDate[date].orEmpty()
        val (visible, hidden) = dayPosts.partition { selectedDiaryId == null || it.diaryId == selectedDiaryId }
        if (visible.isEmpty() && date != today) {
            emptyRun += date to hidden.map { it.diaryId }.distinct().mapNotNull(diaryById::get).sortedBy { it.position }
        } else {
            flushEmptyRun()
            monthDivider(date)
            val sorted = visible.sortedByDescending { it.dateTime }
            items += TimelineItem.DayHeader(date, sorted.mapNotNull { it.mood })
            when {
                sorted.isNotEmpty() -> sorted.forEachIndexed { index, post ->
                    items += TimelineItem.PostItem(post, index, sorted.size, if (labelDiaries) diaryById[post.diaryId] else null)
                }
                else -> items += TimelineItem.TodayPrompt(date) // only today gets here without posts
            }
            if (hidden.isNotEmpty()) {
                val counts = hidden
                    .groupingBy { it.diaryId }
                    .eachCount()
                    .mapNotNull { (id, count) -> diaryById[id]?.let { DiaryCount(it, count) } }
                    .sortedBy { it.diary.position }
                if (counts.isNotEmpty()) items += TimelineItem.OtherDiaries(date, counts)
            }
        }
        date = date.minusDays(1)
    }
    flushEmptyRun()
    return items
}

/** The day an item belongs to (a quiet stretch counts as its newest day); `null` for a month divider. */
internal fun TimelineItem.dayOrNull(): LocalDate? = when (this) {
    is TimelineItem.DayHeader -> date
    is TimelineItem.TodayPrompt -> date
    is TimelineItem.PostItem -> post.date
    is TimelineItem.EmptyDay -> date
    is TimelineItem.QuietStretch -> newest
    is TimelineItem.OtherDiaries -> date
    is TimelineItem.MonthDivider -> null
}

/** The month the row at [index] belongs to, for the month navigator; `null` for an empty list. */
internal fun List<TimelineItem>.monthAt(index: Int): YearMonth? {
    val item = getOrNull(index.coerceIn(0, (size - 1).coerceAtLeast(0))) ?: return null
    if (item is TimelineItem.MonthDivider) return item.month
    return item.dayOrNull()?.let(YearMonth::from)
}

/**
 * The row a jump to [month] lands on: the top of its section, i.e. its divider. The newest month has
 * no divider, so it's the list's first row. A month swallowed whole by a collapsed quiet stretch that
 * started in a newer month has no section of its own, so it lands on that stretch. `null` if the
 * month is outside the timeline.
 */
internal fun List<TimelineItem>.monthTopIndex(month: YearMonth): Int? {
    if (isEmpty()) return null
    if (monthAt(0) == month) return 0
    indexOfFirst { it is TimelineItem.MonthDivider && it.month == month }.takeIf { it >= 0 }?.let { return it }
    return indexOfFirst {
        it is TimelineItem.QuietStretch && YearMonth.from(it.oldest) <= month && month <= YearMonth.from(it.newest)
    }.takeIf { it >= 0 }
}

/** The months that have a section of their own (a divider, or the top of the list), newest first. */
internal fun List<TimelineItem>.sectionMonths(): List<YearMonth> {
    val first = monthAt(0) ?: return emptyList()
    return listOf(first) + filterIsInstance<TimelineItem.MonthDivider>().map { it.month }
}

/** A month at a glance, for the month picker. [averageLevel] is `null` when no entry has a mood. */
data class MonthSummary(val entries: Int, val averageLevel: Int?)

/** Per-month entry counts and average mood level, for the posts shown under [selectedDiaryId]. */
internal fun monthSummaries(posts: List<Post>, selectedDiaryId: Long?): Map<YearMonth, MonthSummary> =
    posts.asSequence()
        .filter { selectedDiaryId == null || it.diaryId == selectedDiaryId }
        .groupBy { YearMonth.from(it.date) }
        .mapValues { (_, monthPosts) ->
            val levels = monthPosts.mapNotNull { it.mood?.level }
            MonthSummary(
                entries = monthPosts.size,
                averageLevel = if (levels.isEmpty()) null else Math.round(levels.average()).toInt(),
            )
        }
