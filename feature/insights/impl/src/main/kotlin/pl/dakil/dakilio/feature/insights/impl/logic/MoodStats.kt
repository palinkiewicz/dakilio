package pl.dakil.dakilio.feature.insights.impl.logic

import java.time.LocalDate
import kotlin.math.abs
import pl.dakil.dakilio.core.model.DateRange
import pl.dakil.dakilio.core.model.MOOD_LEVELS
import pl.dakil.dakilio.core.model.Mood
import pl.dakil.dakilio.core.model.Post

data class MoodCount(val mood: Mood, val count: Int)

/**
 * The moods of a set of entries.
 *
 * @property average the average level (1..5) over entries with a mood, or `null` without any.
 * @property distribution every level's primary, worst to best, with how many entries are on it
 *   (aliases count under their level).
 */
data class MoodSummary(val average: Float?, val distribution: List<MoodCount>) {
    val moodEntries: Int get() = distribution.sumOf { it.count }
}

/**
 * The level (as its primary mood) closest to [level]: how an average is shown as a face. A value
 * halfway between two levels goes up, the same as [nearestLevel] on the calendars.
 */
fun nearestMood(primaries: List<Mood>, level: Float): Mood? {
    val nearest = nearestLevel(level)
    return primaries.firstOrNull { it.level == nearest } ?: primaries.minByOrNull { abs(it.level - level) }
}

fun moodSummary(posts: List<Post>, primaries: List<Mood>): MoodSummary {
    val levels = posts.mapNotNull { it.mood?.level }
    val counts = levels.groupingBy { it }.eachCount()
    return MoodSummary(
        average = levels.averageOrNull(),
        distribution = primaries.sortedBy { it.level }.map { MoodCount(it, counts[it.level] ?: 0) },
    )
}

/**
 * One slice of the mood flow chart.
 *
 * @property average the average level of the slice's entries, `null` when none has a mood.
 * @property levelCounts entries per level, index 0 = level 1.
 */
data class MoodBucket(val range: DateRange, val entries: Int, val average: Float?, val levelCounts: List<Int>) {
    val moodEntries: Int get() = levelCounts.sum()
}

fun moodBuckets(index: InsightsIndex, buckets: List<DateRange>): List<MoodBucket> = buckets.map { range ->
    val days = index.days(range)
    val levels = days.flatMap { it.levels }
    val counts = IntArray(MOOD_LEVELS)
    levels.forEach { counts[(it - 1).coerceIn(0, MOOD_LEVELS - 1)]++ }
    MoodBucket(range, days.sumOf { it.entries }, levels.averageOrNull(), counts.toList())
}

/**
 * Mood over a period, ready to draw.
 *
 * @property trend a calmer line through daily points ([rollingAverage]); only for a run of days long
 *   enough to be noisy, otherwise `null` and the points themselves are joined.
 */
data class MoodFlow(val granularity: Granularity, val buckets: List<MoodBucket>, val trend: List<Float?>?) {
    /** Slices with a mood. A line needs two. */
    val points: Int get() = buckets.count { it.average != null }
}

/** Daily points get a trend line from this many days on. */
private const val TREND_FROM_DAYS = 14

fun moodFlow(index: InsightsIndex, range: DateRange, granularity: Granularity, firstDay: java.time.DayOfWeek): MoodFlow {
    val slices = moodBuckets(index, buckets(range, granularity, firstDay))
    val trend = if (granularity == Granularity.Days && slices.size >= TREND_FROM_DAYS) rollingAverage(slices.map { it.average }) else null
    return MoodFlow(granularity, slices, trend)
}

/**
 * A calmer line through daily values: each point is the average of the values in the [window] days
 * ending on it. A point stays `null` when its window holds fewer than [minValues] values, so the
 * line breaks over a gap instead of drawing through it.
 */
fun rollingAverage(values: List<Float?>, window: Int = 7, minValues: Int = 2): List<Float?> = values.indices.map { i ->
    val seen = values.subList(maxOf(0, i - window + 1), i + 1).filterNotNull()
    if (seen.size < minValues) null else seen.average().toFloat()
}

/** How much the mood moved from one day to the next, in words rather than a number. */
enum class Steadiness { Steady, Varied, UpAndDown }

internal const val STEADINESS_MIN_DAYS = 5
private const val STEADY_BELOW = 0.5f
private const val VARIED_BELOW = 1.0f

/**
 * From the average step between consecutive days with a mood (in levels): under half a level is
 * steady, under a whole one varied. `null` with fewer than [STEADINESS_MIN_DAYS] days.
 */
fun steadiness(dayMoods: List<Float>): Steadiness? {
    if (dayMoods.size < STEADINESS_MIN_DAYS) return null
    val step = dayMoods.zipWithNext { a, b -> abs(b - a) }.average().toFloat()
    return when {
        step < STEADY_BELOW -> Steadiness.Steady
        step < VARIED_BELOW -> Steadiness.Varied
        else -> Steadiness.UpAndDown
    }
}

/** The exact moods picked (aliases apart from their primaries), the most used first. */
fun moodNames(posts: List<Post>): List<MoodCount> =
    posts.mapNotNull { it.mood }.groupBy { it.id }
        .map { (_, moods) -> MoodCount(moods.first(), moods.size) }
        .sortedWith(compareByDescending<MoodCount> { it.count }.thenByDescending { it.mood.level }.thenBy { it.mood.label })

/** A day and its average level. */
data class MoodDay(val date: LocalDate, val mood: Float, val entries: Int)

/** The [limit] days with the highest average level; of equals, the latest first. */
fun brightestDays(days: List<DayStat>, limit: Int): List<MoodDay> =
    moodDays(days).sortedWith(compareByDescending<MoodDay> { it.mood }.thenByDescending { it.date }).take(limit)

/** The [limit] days with the lowest average level; of equals, the latest first. */
fun toughestDays(days: List<DayStat>, limit: Int): List<MoodDay> =
    moodDays(days).sortedWith(compareBy<MoodDay> { it.mood }.thenByDescending { it.date }).take(limit)

private fun moodDays(days: List<DayStat>): List<MoodDay> = days.mapNotNull { day -> day.mood?.let { MoodDay(day.date, it, day.entries) } }

/** The longest run of consecutive days whose average level is at least [level]. */
fun brightRun(days: List<DayStat>, level: Float = 4f): DateRange? {
    var best: DateRange? = null
    var start: LocalDate? = null
    var previous: LocalDate? = null
    fun close() {
        val from = start ?: return
        val run = DateRange(from, previous!!)
        if (best == null || run.days >= best!!.days) best = run
        start = null
    }
    for (day in days) {
        val bright = (day.mood ?: 0f) >= level
        if (start != null && (!bright || previous?.plusDays(1) != day.date)) close()
        if (bright) {
            if (start == null) start = day.date
            previous = day.date
        }
    }
    close()
    return best
}

internal fun List<Int>.averageOrNull(): Float? = if (isEmpty()) null else average().toFloat()

@JvmName("averageOrNullOfFloat")
internal fun List<Float>.averageOrNull(): Float? = if (isEmpty()) null else average().toFloat()
