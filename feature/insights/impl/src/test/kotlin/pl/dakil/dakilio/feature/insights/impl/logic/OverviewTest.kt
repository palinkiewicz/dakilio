package pl.dakil.dakilio.feature.insights.impl.logic

import java.time.DayOfWeek
import java.time.YearMonth
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import pl.dakil.dakilio.core.model.DateRange
import pl.dakil.dakilio.core.model.Diary
import pl.dakil.dakilio.core.model.Post

class OverviewTest {
    private val october = InsightsPeriod.Month(YearMonth.of(2026, 10))
    private val september = InsightsPeriod.Month(YearMonth.of(2026, 9))

    private fun overview(posts: List<Post>, period: InsightsPeriod = october, comparison: Comparison = Comparison.Previous, diaries: List<Diary>? = null) =
        InsightsIndex(posts).let { index ->
            buildOverview(index, period, Bounds(index.first ?: TODAY, TODAY), comparison, MOODS, DayOfWeek.MONDAY, diaries)
        }

    @Test
    fun `a period counts only its own days`() {
        val result = overview(listOf(post(0, 5, WALK), post(0, 3), post(2, 4), post(40, 1)))
        assertEquals(DateRange(TODAY.withDayOfMonth(1), TODAY), result.range)
        assertEquals(3, result.entries)
        assertEquals(2, result.daysJournaled)
        assertEquals(4f, result.mood.average!!, 0.0001f)
        assertEquals(CalendarForm.Grid, result.calendar.form)
        assertEquals(2, result.calendar.cells.size)
        assertEquals(Granularity.Days, result.flow.granularity)
        assertNull(result.records)
        assertNull(result.diaries)
    }

    @Test
    fun `the streak is the current one only while the period includes today`() {
        val posts = listOf(post(0), post(1), post(2), post(33), post(34), post(35), post(36), post(60))
        val now = overview(posts)
        assertEquals(3, now.streak)
        assertTrue(now.streakIsCurrent)
        val before = overview(posts, september)
        assertEquals(4, before.streak)
        assertFalse(before.streakIsCurrent)
    }

    @Test
    fun `a period is measured against the one before`() {
        // October so far (29 days) against the first 29 days of September.
        val posts = listOf(post(0, 5), post(1, 4), post(29, 3), post(35, 2), post(58, 1))
        val result = overview(posts)
        assertEquals(DateRange(TODAY.minusMonths(1).withDayOfMonth(1), TODAY.minusMonths(1)), result.comparison!!.range)
        assertEquals(2, result.comparison!!.entries) // 30 Sep is past the 29th
        assertEquals(1.5f, result.comparison!!.averageMood!!, 0.0001f)
        assertEquals(3f, result.moodChange!!, 0.0001f)
        assertNull(overview(posts, comparison = Comparison.None).comparison)
        // Nothing to compare with: no comparison rather than zeros.
        assertNull(overview(listOf(post(0, 5))).comparison)
    }

    @Test
    fun `diaries are split only when all of them are shown`() {
        val journey = Diary(1, "Journey", "explore", 0, emptySet())
        val work = Diary(2, "Work", "work", 1, emptySet())
        val dreams = Diary(3, "Dreams", "bedtime", 2, emptySet())
        val posts = listOf(post(0, 5), post(1, 3, diaryId = 2), post(2, 1, diaryId = 2))
        val split = overview(posts, diaries = listOf(journey, work, dreams)).diaries!!
        assertEquals(listOf("Work" to 2, "Journey" to 1), split.map { it.diary.name to it.entries })
        assertEquals(2f, split.first().averageMood!!, 0.0001f)
        assertNull(overview(posts, diaries = listOf(journey)).diaries)
    }

    @Test
    fun `long periods get records`() {
        val result = overview(listOf(post(0, 5), post(400, 3)), InsightsPeriod.AllTime)
        assertEquals(TODAY.minusDays(400), result.records!!.firstEntry)
        assertEquals(CalendarForm.Years, result.calendar.form)
        assertNull(result.comparison)
    }

    @Test
    fun `what's still gathering says how far along it is`() {
        // One mood day, tagged: no line yet, no link, no best day.
        val thin = overview(listOf(post(0, 4, WALK)))
        assertEquals(
            listOf(Gathering.Kind.MoodFlow to 0.5f, Gathering.Kind.TagLinks to 0.1f, Gathering.Kind.BestDay to 1f / 6),
            thin.gathering.map { it.kind to it.progress },
        )
        // Enough of everything: nothing left to gather.
        val rich = (0L..13).map { post(it, if (it % 2 == 0L) 5 else 3, *(if (it < 6) arrayOf(WALK) else emptyArray())) } +
            (14L..27).map { post(it, 4) }
        assertEquals(emptyList<Gathering>(), overview(rich).gathering)
        assertEquals(emptyList<Gathering>(), overview(emptyList()).gathering)
    }
}
