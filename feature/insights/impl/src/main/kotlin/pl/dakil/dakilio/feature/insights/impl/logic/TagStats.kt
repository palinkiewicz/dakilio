package pl.dakil.dakilio.feature.insights.impl.logic

import java.time.LocalDate
import pl.dakil.dakilio.core.model.Post
import pl.dakil.dakilio.core.model.Tag

/** A link needs at least this many days with a mood on each side: with the tag and without it. */
internal const val MIN_LINK_DAYS = 5

/** A lift under this many levels isn't called out as brighter or heavier. */
internal const val NOTABLE_LIFT = 0.25f

/**
 * How days with a tag compare with days without it. It describes what went together, never what
 * caused what.
 *
 * @property daysWith days with a mood that carry the tag; [averageWith] is their average level.
 * @property daysWithout the other days with a mood, and their average.
 */
data class TagLink(val daysWith: Int, val daysWithout: Int, val averageWith: Float, val averageWithout: Float) {
    /** Levels above (or below) the other days. */
    val lift: Float get() = averageWith - averageWithout

    /** [lift] weighed by how many days back it, so two lucky days can't top a list. */
    val score: Float get() = lift * daysWith / (daysWith + MIN_LINK_DAYS)

    val isNotable: Boolean get() = kotlin.math.abs(lift) >= NOTABLE_LIFT
}

/**
 * A tag in a period.
 *
 * @property entries entries carrying it; [days] the days it shows up on.
 * @property previousDays the same days count in the comparison period, `null` without one.
 * @property link its mood link, `null` with too few days on either side.
 */
data class TagStat(
    val tag: Tag,
    val entries: Int,
    val days: Int,
    val lastUsed: LocalDate,
    val previousDays: Int? = null,
    val link: TagLink? = null,
) {
    /** Days gained or lost against the comparison period. */
    val change: Int? get() = previousDays?.let { days - it }
}

/**
 * Every tag used on [days], the most used first (by days, then entries, then name).
 *
 * @param comparison the comparison period's days, to fill [TagStat.previousDays].
 */
fun tagStats(days: List<DayStat>, comparison: List<DayStat>? = null): List<TagStat> {
    class Acc(val tag: Tag) {
        var entries = 0
        var days = 0
        var moodDays = 0
        var moodSum = 0f
        var last: LocalDate = LocalDate.MIN
    }

    val byTag = HashMap<Long, Acc>()
    var moodDays = 0
    var moodSum = 0f
    for (day in days) {
        val mood = day.mood
        if (mood != null) {
            moodDays++
            moodSum += mood
        }
        val seenToday = HashSet<Long>()
        for (post in day.posts) {
            for (tag in post.tags.distinctBy { it.id }) {
                val acc = byTag.getOrPut(tag.id) { Acc(tag) }
                acc.entries++
                if (seenToday.add(tag.id)) {
                    acc.days++
                    if (day.date > acc.last) acc.last = day.date
                    if (mood != null) {
                        acc.moodDays++
                        acc.moodSum += mood
                    }
                }
            }
        }
    }
    val before = comparison?.let { other ->
        val counts = HashMap<Long, Int>()
        other.forEach { day -> day.tagIds.forEach { counts.merge(it, 1, Int::plus) } }
        counts
    }
    return byTag.values.map { acc ->
        val without = moodDays - acc.moodDays
        val link = if (acc.moodDays >= MIN_LINK_DAYS && without >= MIN_LINK_DAYS) {
            TagLink(acc.moodDays, without, acc.moodSum / acc.moodDays, (moodSum - acc.moodSum) / without)
        } else {
            null
        }
        TagStat(acc.tag, acc.entries, acc.days, acc.last, before?.let { it[acc.tag.id] ?: 0 }, link)
    }.sortedWith(compareByDescending<TagStat> { it.days }.thenByDescending { it.entries }.thenBy { it.tag.label.lowercase() })
}

/** Tags whose days were notably brighter than the others, the best backed first. */
fun List<TagStat>.brighter(limit: Int = Int.MAX_VALUE): List<TagStat> =
    filter { it.link?.let { link -> link.isNotable && link.lift > 0 } == true }.sortedByDescending { it.link!!.score }.take(limit)

/** Tags whose days were notably heavier than the others, the best backed first. */
fun List<TagStat>.heavier(limit: Int = Int.MAX_VALUE): List<TagStat> =
    filter { it.link?.let { link -> link.isNotable && link.lift < 0 } == true }.sortedBy { it.link!!.score }.take(limit)

data class TagCount(val tag: Tag, val count: Int)

/** Tags on the same entries as [tagId], with how many entries they share, the most common first. */
fun coOccurring(posts: List<Post>, tagId: Long, limit: Int = 8): List<TagCount> =
    posts.asSequence()
        .filter { post -> post.tags.any { it.id == tagId } }
        .flatMap { post -> post.tags.distinctBy { it.id } }
        .filter { it.id != tagId }
        .groupBy { it.id }
        .map { (_, tags) -> TagCount(tags.first(), tags.size) }
        .sortedWith(compareByDescending<TagCount> { it.count }.thenBy { it.tag.label.lowercase() })
        .take(limit)
