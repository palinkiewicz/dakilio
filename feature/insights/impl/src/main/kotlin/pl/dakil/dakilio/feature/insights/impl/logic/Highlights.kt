package pl.dakil.dakilio.feature.insights.impl.logic

import java.time.DayOfWeek
import java.time.YearMonth
import kotlin.math.abs
import pl.dakil.dakilio.core.model.DateRange
import pl.dakil.dakilio.core.model.Habit
import pl.dakil.dakilio.core.model.Streaks
import pl.dakil.dakilio.core.model.Tag

/**
 * One thing worth telling about a period, in a sentence. Each is backed by enough days to mean
 * something; [subject] keeps two highlights from telling the same story.
 */
sealed interface Highlight {
    val subject: String
    val score: Float

    data class TagBrighter(val tag: Tag, val lift: Float, val days: Int, override val score: Float) : Highlight {
        override val subject get() = "tag-${tag.id}"
    }

    data class TagHeavier(val tag: Tag, val lift: Float, val days: Int, override val score: Float) : Highlight {
        override val subject get() = "tag-${tag.id}"
    }

    /** The weekday whose average stands out above the others. */
    data class BestWeekday(val day: DayOfWeek, val average: Float, override val score: Float) : Highlight {
        override val subject get() = "weekday"
    }

    /** The average moved by [change] levels against the comparison period. */
    data class MoodChange(val change: Float, override val score: Float) : Highlight {
        override val subject get() = "mood"
    }

    /** The highest monthly average since the journal began. */
    data class BestMonthYet(val month: YearMonth, val average: Float, override val score: Float) : Highlight {
        override val subject get() = "mood"
    }

    /** The current streak is the longest there has been. */
    data class StreakRecord(val days: Int, override val score: Float) : Highlight {
        override val subject get() = "streak"
    }

    /** Good days in a row. */
    data class BrightRun(val run: DateRange, override val score: Float) : Highlight {
        override val subject get() = "bright-run"
    }

    /** A habit on target for [weeks] weeks in a row, up to now. */
    data class HabitRun(val habit: Habit, val weeks: Int, override val score: Float) : Highlight {
        override val subject get() = "tag-${habit.tag.id}"
    }

    data class WordsWritten(val words: Int, override val score: Float) : Highlight {
        override val subject get() = "words"
    }
}

/** How many highlights the overview shows. */
internal const val MAX_HIGHLIGHTS = 6

private const val NOTABLE_CHANGE = 0.3f
private const val NOTABLE_WEEKDAY_LEAD = 0.3f
private const val MIN_RECORD_STREAK = 7
private const val MIN_BRIGHT_RUN = 4
private const val MIN_HABIT_RUN = 3
private const val MIN_WORDS = 2000
private const val MIN_MONTH_MOODS = 10
private const val MIN_EARLIER_MONTHS = 3

/**
 * The period's highlights, the most telling first, at most [MAX_HIGHLIGHTS], never two on the same
 * subject.
 *
 * @param index every entry under the diary filter, for the records that need history.
 * @param period the period, to tell a month from other kinds.
 */
fun highlights(overview: Overview, index: InsightsIndex, period: InsightsPeriod, bounds: Bounds): List<Highlight> {
    val found = ArrayList<Highlight>()
    overview.tags.brighter(3).forEach { stat ->
        val link = stat.link!!
        if (link.lift >= 0.4f) found += Highlight.TagBrighter(stat.tag, link.lift, link.daysWith, 2f * link.score)
    }
    overview.tags.heavier(2).forEach { stat ->
        val link = stat.link!!
        if (link.lift <= -0.4f) found += Highlight.TagHeavier(stat.tag, link.lift, link.daysWith, 1.6f * abs(link.score))
    }
    overview.rhythm.bestWeekday?.let { day ->
        val best = overview.rhythm.weekdays.getValue(day).average!!
        val others = overview.rhythm.weekdays.filterKeys { it != day }.values.mapNotNull { it.average }
        val lead = best - (others.average().toFloat().takeIf { others.isNotEmpty() } ?: best)
        if (lead >= NOTABLE_WEEKDAY_LEAD) found += Highlight.BestWeekday(day, best, 2f * lead + 0.4f)
    }
    overview.moodChange?.let { change ->
        if (abs(change) >= NOTABLE_CHANGE) found += Highlight.MoodChange(change, 3f * abs(change))
    }
    bestMonthYet(index, period, bounds)?.let { found += it }
    if (overview.streakIsCurrent && overview.streak >= MIN_RECORD_STREAK && overview.streak >= Streaks.longest(index.dates)) {
        found += Highlight.StreakRecord(overview.streak, 2.4f)
    }
    brightRun(index.days(overview.range))?.takeIf { it.days >= MIN_BRIGHT_RUN }?.let { found += Highlight.BrightRun(it, 0.35f * it.days) }
    overview.habits.forEach { stat ->
        if (stat.currentRun >= MIN_HABIT_RUN) found += Highlight.HabitRun(stat.habit, stat.currentRun, 1f + 0.3f * stat.currentRun)
    }
    if (overview.writing.words >= MIN_WORDS) found += Highlight.WordsWritten(overview.writing.words, 0.8f)

    return found.sortedByDescending { it.score }.distinctBy { it.subject }.take(MAX_HIGHLIGHTS)
}

/**
 * A month (finished or not) whose average beats every earlier month, when it has enough moods and
 * there are enough earlier months to beat.
 */
private fun bestMonthYet(index: InsightsIndex, period: InsightsPeriod, bounds: Bounds): Highlight.BestMonthYet? {
    if (period !is InsightsPeriod.Month) return null
    val levelsByMonth = index.posts.mapNotNull { post -> post.mood?.let { YearMonth.from(post.date) to it.level } }
        .groupBy({ it.first }, { it.second })
        .filterValues { it.size >= MIN_MONTH_MOODS }
        .mapValues { (_, levels) -> levels.average().toFloat() }
    val now = levelsByMonth[period.month] ?: return null
    val earlier = levelsByMonth.filterKeys { it < period.month }.values
    if (earlier.size < MIN_EARLIER_MONTHS || earlier.any { it >= now }) return null
    if (period.month.atDay(1).isAfter(bounds.today)) return null
    // Rarer and warmer than a change against last month, which it replaces (they share a subject).
    return Highlight.BestMonthYet(period.month, now, 7f)
}
