package pl.dakil.dakilio.feature.insights.impl

import java.time.DayOfWeek
import java.time.LocalDate
import java.time.YearMonth
import java.time.temporal.ChronoUnit
import java.time.temporal.TemporalAdjusters
import pl.dakil.dakilio.core.model.Post
import pl.dakil.dakilio.core.model.Tag

/** How long one point of the mood trend covers. */
enum class TrendBucket { Day, Week, Month }

/**
 * One point of the mood trend: the days [start]..[end] (clipped to the range), how many entries
 * they hold and their average normalized mood (null = no mood logged).
 */
data class TrendPoint(val start: LocalDate, val end: LocalDate, val entries: Int, val averageMood: Float?)

/** The average mood over time in the selected range, oldest point first. */
data class MoodTrend(val bucket: TrendBucket, val points: List<TrendPoint>) {
    val hasMood: Boolean get() = points.any { it.averageMood != null }

    companion object {
        val Empty = MoodTrend(TrendBucket.Day, emptyList())
    }
}

/** A weekday's entries and their average normalized mood (null = no mood logged on it). */
data class WeekdayMood(val day: DayOfWeek, val entries: Int, val averageMood: Float?)

/** The part of the day an entry was written in, by its wall-clock hour. Nights run past midnight. */
enum class DayPart(val fromHour: Int) {
    Morning(5),
    Afternoon(12),
    Evening(17),
    Night(22),
    ;

    companion object {
        fun of(hour: Int): DayPart = when {
            hour < Morning.fromHour -> Night
            hour < Afternoon.fromHour -> Morning
            hour < Evening.fromHour -> Afternoon
            hour < Night.fromHour -> Evening
            else -> Night
        }
    }
}

data class DayPartMood(val part: DayPart, val entries: Int, val averageMood: Float?)

/**
 * How entries with a tag compare with the entries without it.
 *
 * @property entries entries with the tag and a mood.
 * @property averageMood their average normalized mood.
 * @property delta that average minus the average of the mood entries without the tag (-1..1).
 */
data class TagInfluence(val tag: Tag, val entries: Int, val averageMood: Float, val delta: Float)

/** The tags that come with the brightest and the heaviest entries, strongest first in each. */
data class TagInfluences(val lifts: List<TagInfluence>, val dips: List<TagInfluence>) {
    val isEmpty: Boolean get() = lifts.isEmpty() && dips.isEmpty()

    companion object {
        val Empty = TagInfluences(emptyList(), emptyList())
    }
}

/** A tag needs this many mood entries (and as many without it) before it's compared. */
internal const val MIN_TAG_ENTRIES = 3

/** A difference smaller than this (on the 0..1 scale) is noise, not an influence. */
internal const val MIN_TAG_DELTA = 0.04f

private const val MAX_INFLUENCES = 5

/** More weeks than this are shown month by month. */
private const val MAX_WEEK_POINTS = 26

internal fun List<Float>.averageOrNull(): Float? = if (isEmpty()) null else average().toFloat()

/** The posts of [range] ending [today] (every post up to today for the unbounded range). */
fun List<Post>.inRange(range: InsightsRange, today: LocalDate): List<Post> {
    val from = range.days?.let { today.minusDays(it - 1) }
    return filter { !it.date.isAfter(today) && (from == null || !it.date.isBefore(from)) }
}

/**
 * The average mood over time. 30 days go day by day, 90 days week by week (weeks start on
 * [firstDay]), and everything since the first entry by week, or by month once that would be more
 * than [MAX_WEEK_POINTS] weeks. Buckets without entries are kept (as gaps), so the points are
 * evenly spaced in time.
 */
fun moodTrend(posts: List<Post>, range: InsightsRange, today: LocalDate, firstDay: DayOfWeek = DayOfWeek.MONDAY): MoodTrend {
    val shown = posts.inRange(range, today)
    val from = range.days?.let { today.minusDays(it - 1) } ?: shown.minOfOrNull { it.date } ?: return MoodTrend.Empty
    val bucket = when (range) {
        InsightsRange.Month -> TrendBucket.Day
        InsightsRange.Quarter -> TrendBucket.Week
        InsightsRange.All -> if (ChronoUnit.WEEKS.between(from, today) >= MAX_WEEK_POINTS) TrendBucket.Month else TrendBucket.Week
    }
    fun bucketStart(date: LocalDate): LocalDate = when (bucket) {
        TrendBucket.Day -> date
        TrendBucket.Week -> date.with(TemporalAdjusters.previousOrSame(firstDay))
        TrendBucket.Month -> date.withDayOfMonth(1)
    }
    fun next(start: LocalDate): LocalDate = when (bucket) {
        TrendBucket.Day -> start.plusDays(1)
        TrendBucket.Week -> start.plusWeeks(1)
        TrendBucket.Month -> start.plusMonths(1)
    }
    val byBucket = shown.groupBy { bucketStart(it.date) }
    val points = generateSequence(bucketStart(from), ::next)
        .takeWhile { !it.isAfter(today) }
        .map { start ->
            val entries = byBucket[start].orEmpty()
            TrendPoint(
                start = maxOf(start, from),
                end = minOf(next(start).minusDays(1), today),
                entries = entries.size,
                averageMood = entries.mapNotNull { it.mood?.value }.averageOrNull(),
            )
        }
        .toList()
    return MoodTrend(bucket, points)
}

/** Each weekday's entries and average mood, the week starting on [firstDay]. */
fun weekdayMoods(posts: List<Post>, firstDay: DayOfWeek = DayOfWeek.MONDAY): List<WeekdayMood> {
    val byDay = posts.groupBy { it.date.dayOfWeek }
    return List(7) { firstDay.plus(it.toLong()) }.map { day ->
        val entries = byDay[day].orEmpty()
        WeekdayMood(day, entries.size, entries.mapNotNull { it.mood?.value }.averageOrNull())
    }
}

/** Each part of the day's entries and average mood, morning first. */
fun dayPartMoods(posts: List<Post>): List<DayPartMood> {
    val byPart = posts.groupBy { DayPart.of(it.dateTime.hour) }
    return DayPart.entries.map { part ->
        val entries = byPart[part].orEmpty()
        DayPartMood(part, entries.size, entries.mapNotNull { it.mood?.value }.averageOrNull())
    }
}

/**
 * Every tag with at least [MIN_TAG_ENTRIES] mood entries (and as many without it), with how its
 * entries' average mood differs from the rest's. Unsorted.
 */
internal fun allTagInfluences(posts: List<Post>): List<TagInfluence> {
    val withMood = posts.filter { it.mood != null }
    if (withMood.size < MIN_TAG_ENTRIES * 2) return emptyList()
    val total = withMood.sumOf { it.mood!!.value.toDouble() }
    val byTag = HashMap<Long, MutableList<Post>>()
    val tags = HashMap<Long, Tag>()
    withMood.forEach { post ->
        post.tags.forEach { tag ->
            byTag.getOrPut(tag.id) { mutableListOf() } += post
            tags.putIfAbsent(tag.id, tag)
        }
    }
    return byTag.mapNotNull { (id, tagged) ->
        val without = withMood.size - tagged.size
        if (tagged.size < MIN_TAG_ENTRIES || without < MIN_TAG_ENTRIES) return@mapNotNull null
        val sum = tagged.sumOf { it.mood!!.value.toDouble() }
        val average = (sum / tagged.size).toFloat()
        TagInfluence(tags.getValue(id), tagged.size, average, average - ((total - sum) / without).toFloat())
    }
}

/**
 * The tags whose entries feel brighter ([TagInfluences.lifts]) or heavier ([TagInfluences.dips])
 * than the rest, up to five each, strongest first. Ties go to the tag with more entries, then by name.
 */
fun tagInfluences(posts: List<Post>): TagInfluences {
    val all = allTagInfluences(posts)
    val order = compareByDescending<TagInfluence> { it.entries }.thenBy { it.tag.label }
    return TagInfluences(
        lifts = all.filter { it.delta >= MIN_TAG_DELTA }.sortedWith(compareByDescending<TagInfluence> { it.delta }.then(order)).take(MAX_INFLUENCES),
        dips = all.filter { it.delta <= -MIN_TAG_DELTA }.sortedWith(compareBy<TagInfluence> { it.delta }.then(order)).take(MAX_INFLUENCES),
    )
}

/**
 * A calendar year, one cell per day: the "year in pixels".
 *
 * @property days the days of [year] that have entries, by date.
 */
data class YearPixels(val year: Int, val days: Map<LocalDate, HeatDay>) {
    val months: List<YearMonth> get() = (1..12).map { YearMonth.of(year, it) }
    val daysJournaled: Int get() = days.size

    /** The year's average normalized mood, day by day (null when no day has a mood). */
    val averageMood: Float? get() = days.values.mapNotNull { it.averageMood }.averageOrNull()
}

fun yearPixels(posts: List<Post>, year: Int): YearPixels =
    YearPixels(
        year,
        posts.filter { it.date.year == year }.groupBy { it.date }.mapValues { (date, day) ->
            HeatDay(date, day.size, day.mapNotNull { it.mood?.value }.averageOrNull())
        },
    )

/** The years the year view can show: from the first entry's year to [today]'s. */
fun pixelYears(posts: List<Post>, today: LocalDate): IntRange =
    minOf(posts.minOfOrNull { it.date.year } ?: today.year, today.year)..today.year
