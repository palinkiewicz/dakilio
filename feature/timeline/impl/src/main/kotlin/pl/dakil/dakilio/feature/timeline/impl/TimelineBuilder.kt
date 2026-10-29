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

    data class PostItem(val post: Post, val index: Int, val count: Int, val diaries: List<Diary>) : TimelineItem {
        override val key = "post-${post.id}"
    }

    /** A day that has a header (it has entries in other diaries) but nothing in the current filter. */
    data class NoEntries(val date: LocalDate) : TimelineItem {
        override val key = "none-${date.toEpochDay()}"
    }

    /** A compact, self-labelled row for a day without any entries. */
    data class EmptyDay(val date: LocalDate) : TimelineItem {
        override val key = "empty-${date.toEpochDay()}"
    }

    /** Several empty days in a row, collapsed into one row (expandable into [EmptyDay]s). */
    data class QuietStretch(val newest: LocalDate, val oldest: LocalDate, val expanded: Boolean) : TimelineItem {
        override val key = "quiet-${newest.toEpochDay()}"
        val days: Int get() = (newest.toEpochDay() - oldest.toEpochDay() + 1).toInt()
    }

    data class OtherDiaries(val date: LocalDate, val chips: List<DiaryCount>) : TimelineItem {
        override val key = "others-${date.toEpochDay()}"
    }
}

data class DiaryCount(val diary: Diary, val count: Int)

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
    val newest = maxOf(today, postsByDate.keys.maxOrNull() ?: today)
    val oldest = minOf(today, postsByDate.keys.minOrNull() ?: today)

    val items = ArrayList<TimelineItem>(posts.size + 32)
    val emptyRun = ArrayList<LocalDate>()
    var lastMonth: YearMonth? = null

    fun monthDivider(date: LocalDate) {
        val month = YearMonth.from(date)
        if (lastMonth != null && month != lastMonth) items += TimelineItem.MonthDivider(month)
        lastMonth = month
    }

    fun flushEmptyRun() {
        if (emptyRun.isEmpty()) return
        if (emptyRun.size >= QUIET_STRETCH_MIN_DAYS) {
            val stretch = TimelineItem.QuietStretch(
                newest = emptyRun.first(),
                oldest = emptyRun.last(),
                expanded = emptyRun.first().toEpochDay() in expandedStretches,
            )
            monthDivider(stretch.newest)
            items += stretch
            if (stretch.expanded) emptyRun.forEach { items += TimelineItem.EmptyDay(it) }
        } else {
            emptyRun.forEach {
                monthDivider(it)
                items += TimelineItem.EmptyDay(it)
            }
        }
        emptyRun.clear()
    }

    var date = newest
    while (!date.isBefore(oldest)) {
        val dayPosts = postsByDate[date].orEmpty()
        val (visible, hidden) = dayPosts.partition { selectedDiaryId == null || selectedDiaryId in it.diaryIds }
        if (visible.isEmpty() && hidden.isEmpty() && date != today) {
            emptyRun += date
        } else {
            flushEmptyRun()
            monthDivider(date)
            val sorted = visible.sortedByDescending { it.dateTime }
            items += TimelineItem.DayHeader(date, sorted.mapNotNull { it.mood })
            when {
                sorted.isNotEmpty() -> sorted.forEachIndexed { index, post ->
                    val postDiaries = if (labelDiaries) post.diaryIds.mapNotNull(diaryById::get).sortedBy { it.position } else emptyList()
                    items += TimelineItem.PostItem(post, index, sorted.size, postDiaries)
                }
                date == today -> items += TimelineItem.TodayPrompt(date)
                else -> items += TimelineItem.NoEntries(date)
            }
            if (hidden.isNotEmpty()) {
                val counts = hidden
                    .flatMap { post -> post.diaryIds.filter { it != selectedDiaryId } }
                    .groupingBy { it }
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
