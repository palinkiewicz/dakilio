package pl.dakil.dakilio.feature.insights.impl.logic

import java.time.LocalDate
import java.time.YearMonth
import pl.dakil.dakilio.core.model.Post

/** A month or year in the recap archive: its entries and average level, and whether it has a recap. */
data class ArchivedPeriod(val entries: Int, val average: Float?, val finished: Boolean) {
    val playable: Boolean get() = finished && entries >= RECAP_MIN_ENTRIES
}

/** A year of the archive with its twelve months. */
data class ArchivedYear(val year: Int, val summary: ArchivedPeriod, val months: List<Pair<YearMonth, ArchivedPeriod>>)

/**
 * Every year from the first entry to today with at least one recap to play, newest first, each with
 * its months. Every diary counts.
 */
fun recapArchive(posts: List<Post>, today: LocalDate): List<ArchivedYear> {
    val first = posts.minOfOrNull { it.date } ?: return emptyList()
    val byMonth = posts.groupBy { YearMonth.from(it.date) }
    val thisMonth = YearMonth.from(today)
    fun summarize(group: List<Post>, finished: Boolean) =
        ArchivedPeriod(group.size, group.mapNotNull { it.mood?.level }.takeIf { it.isNotEmpty() }?.average()?.toFloat(), finished)
    return (today.year downTo first.year).map { year ->
        val months = (1..12).map { YearMonth.of(year, it) }.map { month -> month to summarize(byMonth[month].orEmpty(), month < thisMonth) }
        ArchivedYear(year, summarize(posts.filter { it.date.year == year }, year < today.year), months)
    }.filter { year -> year.summary.playable || year.months.any { it.second.playable } }
}
