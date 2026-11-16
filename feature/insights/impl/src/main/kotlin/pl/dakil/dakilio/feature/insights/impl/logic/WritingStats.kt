package pl.dakil.dakilio.feature.insights.impl.logic

import java.time.LocalDate
import java.time.YearMonth
import pl.dakil.dakilio.core.model.DateRange
import pl.dakil.dakilio.core.model.Post
import pl.dakil.dakilio.core.model.Streaks

/**
 * What was written in a period.
 *
 * @property words words in titles and notes; [averageWords] per entry that has text.
 * @property longest the entry with the most words, `null` when nothing was written.
 */
data class WritingStats(
    val entries: Int,
    val entriesWithText: Int,
    val words: Int,
    val longest: Post?,
    val longestWords: Int,
    val photos: Int,
    val entriesWithPhotos: Int,
) {
    val averageWords: Int get() = if (entriesWithText == 0) 0 else Math.round(words.toFloat() / entriesWithText)
}

fun writingStats(posts: List<Post>): WritingStats {
    var words = 0
    var withText = 0
    var longest: Post? = null
    var longestWords = 0
    for (post in posts) {
        val count = post.wordCount()
        if (count == 0) continue
        words += count
        withText++
        if (count > longestWords) {
            longest = post
            longestWords = count
        }
    }
    return WritingStats(
        entries = posts.size,
        entriesWithText = withText,
        words = words,
        longest = longest,
        longestWords = longestWords,
        photos = posts.sumOf { it.photos.size },
        entriesWithPhotos = posts.count { it.photos.isNotEmpty() },
    )
}

/** A month's average needs this many entries with a mood to be called the best. */
internal const val MIN_BEST_MONTH_MOODS = 5

/** The standout moments of a long stretch (a year, or all time). */
data class Records(
    val firstEntry: LocalDate?,
    val bestMonth: Pair<YearMonth, Float>?,
    val busiestMonth: Pair<YearMonth, Int>?,
    val longestStreak: DateRange?,
    val brightRun: DateRange?,
    val busiestDay: Pair<LocalDate, Int>?,
)

fun records(days: List<DayStat>): Records {
    val byMonth = days.groupBy { YearMonth.from(it.date) }
    val best = byMonth.mapNotNull { (month, monthDays) ->
        val levels = monthDays.flatMap { it.levels }
        if (levels.size < MIN_BEST_MONTH_MOODS) null else month to levels.average().toFloat()
    }.sortedWith(compareByDescending<Pair<YearMonth, Float>> { it.second }.thenByDescending { it.first }).firstOrNull()
    val busiest = byMonth.map { (month, monthDays) -> month to monthDays.sumOf { it.entries } }
        .sortedWith(compareByDescending<Pair<YearMonth, Int>> { it.second }.thenByDescending { it.first }).firstOrNull()
    val busiestDay = days.sortedWith(compareByDescending<DayStat> { it.entries }.thenByDescending { it.date }).firstOrNull()
    return Records(
        firstEntry = days.firstOrNull()?.date,
        bestMonth = best,
        busiestMonth = busiest,
        longestStreak = Streaks.longestRun(days.mapTo(HashSet()) { it.date }),
        brightRun = brightRun(days)?.takeIf { it.days >= 2 },
        busiestDay = busiestDay?.takeIf { it.entries >= 2 }?.let { it.date to it.entries },
    )
}
