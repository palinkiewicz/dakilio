package pl.dakil.dakilio.feature.insights.impl.logic

import java.time.LocalDate
import java.util.TreeMap
import pl.dakil.dakilio.core.model.DateRange
import pl.dakil.dakilio.core.model.Post

/** One day of the journal. Only days with entries exist. */
class DayStat(val date: LocalDate, val posts: List<Post>) {
    val entries: Int get() = posts.size

    /** The mood levels (1..5) logged that day, one per entry with a mood. */
    val levels: List<Int> = posts.mapNotNull { it.mood?.level }

    /** The day's average level, or `null` when no entry has a mood. */
    val mood: Float? = if (levels.isEmpty()) null else levels.average().toFloat()

    val tagIds: Set<Long> = posts.flatMapTo(HashSet()) { post -> post.tags.map { it.id } }
}

/**
 * The entries sorted into days once, so stepping through periods only takes a slice. Build a new one
 * whenever the entries (or the diary filter) change.
 */
class InsightsIndex(posts: List<Post>) {
    /** Oldest first. */
    val posts: List<Post> = posts.sortedBy { it.dateTime }

    private val byDay: TreeMap<Long, DayStat> = TreeMap<Long, DayStat>().apply {
        this@InsightsIndex.posts.groupBy { it.date }.forEach { (date, dayPosts) -> put(date.toEpochDay(), DayStat(date, dayPosts)) }
    }

    val isEmpty: Boolean get() = byDay.isEmpty()
    val first: LocalDate? get() = byDay.firstEntry()?.value?.date
    val last: LocalDate? get() = byDay.lastEntry()?.value?.date

    /** Every day with an entry. */
    val dates: Set<LocalDate> by lazy { byDay.values.mapTo(HashSet()) { it.date } }

    fun day(date: LocalDate): DayStat? = byDay[date.toEpochDay()]

    /** The days with entries inside [range], oldest first. */
    fun days(range: DateRange): List<DayStat> =
        byDay.subMap(range.from.toEpochDay(), true, range.to.toEpochDay(), true).values.toList()

    fun posts(range: DateRange): List<Post> = days(range).flatMap { it.posts }
}

private val WHITESPACE = "\\s+".toRegex()

/** Words in the entry's title and note. */
fun Post.wordCount(): Int = listOfNotNull(title, note).sumOf { text -> text.split(WHITESPACE).count { it.isNotEmpty() } }
