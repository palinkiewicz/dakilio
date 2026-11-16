package pl.dakil.dakilio.feature.insights.impl.logic

import java.time.DayOfWeek
import java.time.LocalDate
import pl.dakil.dakilio.core.model.Post

/** What narrows a period's entries down to the ones behind a number. Every part left `null` matches all. */
data class EntryFilter(
    val moodLevel: Int? = null,
    val tagId: Long? = null,
    val weekday: DayOfWeek? = null,
    val dayPart: DayPart? = null,
) {
    fun matches(post: Post): Boolean =
        (moodLevel == null || post.mood?.level == moodLevel) &&
            (tagId == null || post.tags.any { it.id == tagId }) &&
            (weekday == null || post.dateTime.dayOfWeek == weekday) &&
            (dayPart == null || DayPart.of(post.dateTime.hour) == dayPart)
}

/** A day's entries in a list, earliest first within the day (as on the timeline). */
data class EntryDay(val date: LocalDate, val posts: List<Post>)

/** The [posts] matching [filter], by day, the newest day first. */
fun entryDays(posts: List<Post>, filter: EntryFilter = EntryFilter()): List<EntryDay> =
    posts.filter(filter::matches)
        .groupBy { it.date }
        .map { (date, dayPosts) -> EntryDay(date, dayPosts.sortedBy { it.dateTime }) }
        .sortedByDescending { it.date }
