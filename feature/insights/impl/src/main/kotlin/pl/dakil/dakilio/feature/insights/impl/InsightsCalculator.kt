package pl.dakil.dakilio.feature.insights.impl

import java.time.DayOfWeek
import java.time.LocalDate
import java.time.temporal.TemporalAdjusters
import kotlin.math.abs
import pl.dakil.dakilio.core.model.Mood
import pl.dakil.dakilio.core.model.Post
import pl.dakil.dakilio.core.model.Streaks
import pl.dakil.dakilio.core.model.Tag

enum class InsightsRange(val days: Long?) { Month(30), Quarter(90), All(null) }

data class MoodCount(val mood: Mood, val count: Int)

data class TagCount(val tag: Tag, val count: Int)

/** One heatmap cell: a day, its entry count and its average normalized mood (null = no mood logged). */
data class HeatDay(val date: LocalDate, val entries: Int, val averageMood: Float?)

data class Insights(
    val entryCount: Int,
    val daysJournaled: Int,
    val currentStreak: Int,
    val longestStreak: Int,
    /** Average normalized mood (0..1) in the range, null when no entry in it has a mood. */
    val averageMood: Float?,
    /** Same, for the equally long period right before (only for bounded ranges). */
    val previousAverageMood: Float?,
    /** Every mood of the scale, worst to best, with its count in the range. */
    val distribution: List<MoodCount>,
    val topTags: List<TagCount>,
    /** The last [HEATMAP_WEEKS] weeks, Monday-first, oldest first; ends today. */
    val heatmap: List<HeatDay>,
) {
    val moodEntryCount: Int get() = distribution.sumOf { it.count }
}

internal const val HEATMAP_WEEKS = 12
private const val TOP_TAGS = 5

/** The level (as its primary mood) closest to [value]: how an average is shown as a face. */
fun nearestMood(primaries: List<Mood>, value: Float): Mood? = primaries.minByOrNull { abs(it.value - value) }

/**
 * @param primaries one mood per level; aliases are counted under their level's primary, so the
 *   distribution always has one segment per level.
 */
fun calculateInsights(posts: List<Post>, primaries: List<Mood>, range: InsightsRange, today: LocalDate): Insights {
    val from = range.days?.let { today.minusDays(it - 1) }
    val inRange = if (from == null) posts else posts.filter { !it.date.isBefore(from) && !it.date.isAfter(today) }
    val previous = range.days?.let { days ->
        val prevFrom = from!!.minusDays(days)
        posts.filter { !it.date.isBefore(prevFrom) && it.date.isBefore(from) }
    }
    val allDays = posts.map { it.date }.toSet()

    val levelCounts = inRange.mapNotNull { it.mood?.level }.groupingBy { it }.eachCount()
    val tagCounts = inRange.flatMap { it.tags }.groupBy { it.id }
        .map { (_, tags) -> TagCount(tags.first(), tags.size) }
        .sortedWith(compareByDescending<TagCount> { it.count }.thenBy { it.tag.label })
        .take(TOP_TAGS)

    val heatStart = today.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY)).minusWeeks(HEATMAP_WEEKS - 1L)
    val byDate = posts.filter { !it.date.isBefore(heatStart) && !it.date.isAfter(today) }.groupBy { it.date }
    val heatmap = generateSequence(heatStart) { it.plusDays(1) }
        .takeWhile { !it.isAfter(today) }
        .map { date ->
            val day = byDate[date].orEmpty()
            HeatDay(date, day.size, day.mapNotNull { it.mood?.value }.averageOrNull())
        }
        .toList()

    return Insights(
        entryCount = inRange.size,
        daysJournaled = inRange.map { it.date }.toSet().size,
        currentStreak = Streaks.current(allDays, today),
        longestStreak = Streaks.longest(allDays),
        averageMood = inRange.mapNotNull { it.mood?.value }.averageOrNull(),
        previousAverageMood = previous?.mapNotNull { it.mood?.value }?.averageOrNull(),
        distribution = primaries.sortedBy { it.level }.map { MoodCount(it, levelCounts[it.level] ?: 0) },
        topTags = tagCounts,
        heatmap = heatmap,
    )
}

private fun List<Float>.averageOrNull(): Float? = if (isEmpty()) null else average().toFloat()
