package pl.dakil.dakilio.feature.insights.impl.logic

import java.time.DayOfWeek
import kotlin.math.abs
import pl.dakil.dakilio.core.model.DateRange
import pl.dakil.dakilio.core.model.Mood
import pl.dakil.dakilio.core.model.Tag

/** How the Tags screen orders its list. */
enum class TagSort {
    /** By the days a tag is on, most first. */
    MostUsed,

    /** Only tags with a mood link, the brightest days first. */
    Brightest,

    /** Only tags with a mood link, the heaviest days first. */
    Heaviest,

    /** By how much their days changed against the comparison period; needs one. */
    Change,

    /** By last use, the longest unused first. */
    LastUsed,
}

/** [this] in the order of [sort]. The two link orders keep only tags that have a link. */
fun List<TagStat>.sortedFor(sort: TagSort): List<TagStat> = when (sort) {
    TagSort.MostUsed -> this
    TagSort.Brightest -> filter { it.link != null }.sortedByDescending { it.link!!.lift }
    TagSort.Heaviest -> filter { it.link != null }.sortedBy { it.link!!.lift }
    TagSort.Change -> filter { it.change != null }.sortedWith(compareByDescending<TagStat> { abs(it.change!!) }.thenByDescending { it.days })
    TagSort.LastUsed -> sortedBy { it.lastUsed }
}

/** One slice of a tag's use over time: the days in [range] that carry it. */
data class TagUse(val range: DateRange, val days: Int)

/**
 * One tag in a period.
 *
 * @property tag as its newest entry has it; `null` when no entry ever carried it.
 * @property stat its numbers in the period; `null` when it isn't used there.
 * @property journaledDays days with any entry in the period, to put [TagStat.days] against.
 * @property use its days per slice, and [weekdays] its days per weekday.
 * @property together tags on the same entries.
 * @property mood the moods of the entries carrying it.
 */
data class TagDetail(
    val tag: Tag?,
    val stat: TagStat?,
    val journaledDays: Int,
    val granularity: Granularity,
    val use: List<TagUse>,
    val weekdays: Map<DayOfWeek, Int>,
    val together: List<TagCount>,
    val mood: MoodSummary,
)

fun tagDetail(
    index: InsightsIndex,
    range: DateRange,
    comparison: DateRange?,
    tagId: Long,
    primaries: List<Mood>,
    firstDay: DayOfWeek,
): TagDetail {
    val days = index.days(range)
    val tagDays = days.filter { tagId in it.tagIds }
    val posts = tagDays.flatMap { day -> day.posts.filter { post -> post.tags.any { it.id == tagId } } }
    val granularity = defaultGranularity(range)
    val byWeekday = tagDays.groupingBy { it.date.dayOfWeek }.eachCount()
    return TagDetail(
        tag = index.posts.asReversed().firstNotNullOfOrNull { post -> post.tags.firstOrNull { it.id == tagId } },
        stat = tagStats(days, comparison?.let(index::days)).firstOrNull { it.tag.id == tagId },
        journaledDays = days.size,
        granularity = granularity,
        use = buckets(range, granularity, firstDay).map { slice -> TagUse(slice, tagDays.count { it.date in slice }) },
        weekdays = DayOfWeek.entries.associateWith { byWeekday[it] ?: 0 },
        together = coOccurring(posts, tagId),
        mood = moodSummary(posts, primaries),
    )
}
