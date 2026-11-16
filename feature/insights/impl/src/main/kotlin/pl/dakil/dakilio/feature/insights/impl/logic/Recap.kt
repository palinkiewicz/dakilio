package pl.dakil.dakilio.feature.insights.impl.logic

import java.time.YearMonth
import pl.dakil.dakilio.core.model.MonthSummary
import pl.dakil.dakilio.core.model.Post

/** The pages a recap can have, in the order they're shown. */
enum class RecapPage { Title, Numbers, Mood, BrightestDay, Tags, Rhythm, Habits, Months, Records, Change, End }

/** A period earns a recap from this many entries. */
const val RECAP_MIN_ENTRIES = 5

/** Pages that need a mood need this many days with one, so they aren't built on a day or two. */
private const val MIN_MOOD_DAYS = 3

/**
 * Everything a recap shows.
 *
 * @property brightest the brightest day's most telling entry (the one with the best mood, then the
 *   most words), `null` without enough days with a mood.
 * @property months each month of a year with its summary (`null` without entries); empty otherwise.
 */
data class Recap(
    val overview: Overview,
    val pages: List<RecapPage>,
    val brightest: Post?,
    val brightestMood: Float?,
    val months: List<Pair<YearMonth, MonthSummary?>>,
)

fun recap(overview: Overview, index: InsightsIndex, period: InsightsPeriod, months: Map<YearMonth, MonthSummary>): Recap {
    val days = index.days(overview.range)
    val moodDays = days.count { it.mood != null }
    val best = if (moodDays >= MIN_MOOD_DAYS) brightestDays(days, 1).firstOrNull() else null
    val bestPost = best?.let { day ->
        index.day(day.date)?.posts?.sortedWith(compareByDescending<Post> { it.mood?.level ?: 0 }.thenByDescending { it.wordCount() })?.firstOrNull()
    }
    val yearMonths = (period as? InsightsPeriod.Year)?.let { year -> (1..12).map { YearMonth.of(year.year, it) to months[YearMonth.of(year.year, it)] } }.orEmpty()
    val pages = buildList {
        add(RecapPage.Title)
        add(RecapPage.Numbers)
        if (moodDays >= MIN_MOOD_DAYS) add(RecapPage.Mood)
        if (bestPost != null) add(RecapPage.BrightestDay)
        if (overview.tags.isNotEmpty()) add(RecapPage.Tags)
        if (overview.rhythm.bestWeekday != null || (overview.entries >= 7 && overview.rhythm.busiestDayPart != null)) add(RecapPage.Rhythm)
        if (overview.habits.isNotEmpty()) add(RecapPage.Habits)
        if (yearMonths.count { it.second != null } >= 2) add(RecapPage.Months)
        if (period is InsightsPeriod.Year && overview.records != null) add(RecapPage.Records)
        // A shift of a tenth reads as "the same"; only one worth a page gets one.
        if (overview.moodChange?.let { kotlin.math.abs(it) >= NOTABLE_LIFT } == true) add(RecapPage.Change)
        add(RecapPage.End)
    }
    return Recap(overview, pages, bestPost, best?.mood, yearMonths)
}
