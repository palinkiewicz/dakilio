package pl.dakil.dakilio.feature.insights.impl.logic

import java.time.DayOfWeek
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import pl.dakil.dakilio.core.model.DateRange

class MoodStatsTest {
    @Test
    fun `the average is on the level scale and aliases count under their level`() {
        val posts = listOf(post(0, 5), post(1, 3), post(1), post(2, mood = SAD))
        val summary = moodSummary(posts, MOODS)
        assertEquals(10f / 3, summary.average!!, 0.0001f)
        assertEquals(listOf(0, 1, 1, 0, 1), summary.distribution.map { it.count })
        assertEquals(3, summary.moodEntries)
        assertNull(moodSummary(listOf(post(0)), MOODS).average)
    }

    @Test
    fun `an average is shown as the nearest level's face`() {
        assertEquals(4, nearestMood(MOODS, 4.4f)!!.level)
        assertEquals(5, nearestMood(MOODS, 4.6f)!!.level)
        assertNull(nearestMood(emptyList(), 3f))
    }

    @Test
    fun `days are sliced with their entries`() {
        val index = InsightsIndex(listOf(post(0, 5), post(0, 3), post(3), post(40, 1)))
        assertEquals(TODAY.minusDays(40), index.first)
        val week = index.days(DateRange(TODAY.minusDays(6), TODAY))
        assertEquals(listOf(TODAY.minusDays(3), TODAY), week.map { it.date })
        assertEquals(4f, week.last().mood!!, 0.0001f)
        assertNull(week.first().mood)
        assertEquals(3, index.posts(DateRange(TODAY.minusDays(6), TODAY)).size)
    }

    @Test
    fun `the flow has a point per slice and none where no mood was logged`() {
        val index = InsightsIndex(listOf(post(0, 5), post(0, 3), post(1), post(9, 2)))
        val range = DateRange(TODAY.minusDays(13), TODAY)
        val flow = moodFlow(index, buckets(range, Granularity.Weeks, DayOfWeek.MONDAY))
        // 15–18 Oct, 19–25 Oct, 26–29 Oct.
        assertEquals(listOf(0, 1, 3), flow.map { it.entries })
        assertEquals(listOf(null, 2f, 4f), flow.map { it.average })
        assertEquals(listOf(0, 0, 1, 0, 1), flow.last().levelCounts)
    }

    @Test
    fun `the rolling line breaks over gaps`() {
        val values = listOf(4f, null, 2f, null, null, null, null, null, null, null, 5f)
        val line = rollingAverage(values, window = 3)
        assertEquals(listOf(null, null, 3f, null, null), line.take(5))
        assertNull(line.last()) // one value in its window isn't a trend
    }

    @Test
    fun `steadiness is read from the day to day steps`() {
        assertNull(steadiness(listOf(3f, 4f, 3f)))
        assertEquals(Steadiness.Steady, steadiness(listOf(4f, 4f, 4f, 3.5f, 4f, 4f)))
        assertEquals(Steadiness.Varied, steadiness(listOf(4f, 3f, 4f, 4f, 3f, 3.5f)))
        assertEquals(Steadiness.UpAndDown, steadiness(listOf(5f, 1f, 4f, 2f, 5f)))
    }

    @Test
    fun `mood names keep aliases apart`() {
        val names = moodNames(listOf(post(0, 2), post(1, mood = SAD), post(2, mood = SAD), post(3, 5), post(4)))
        assertEquals(listOf("Sad" to 2, "m5" to 1, "m2" to 1), names.map { it.mood.label to it.count })
    }

    @Test
    fun `brightest and toughest days go by the day's average`() {
        val days = InsightsIndex(listOf(post(0, 5), post(0, 1), post(1, 4), post(2, 2), post(3), post(5, 4)))
            .days(DateRange(TODAY.minusDays(10), TODAY))
        assertEquals(listOf(TODAY.minusDays(1), TODAY.minusDays(5)), brightestDays(days, 2).map { it.date })
        assertEquals(listOf(TODAY.minusDays(2), TODAY), toughestDays(days, 2).map { it.date })
    }

    @Test
    fun `a bright run needs consecutive bright days`() {
        val days = InsightsIndex(listOf(post(9, 4), post(8, 5), post(7, 4), post(6, 2), post(4, 5), post(2, 5), post(1, 4)))
            .days(DateRange(TODAY.minusDays(20), TODAY))
        assertEquals(DateRange(TODAY.minusDays(9), TODAY.minusDays(7)), brightRun(days))
        assertNull(brightRun(emptyList()))
    }
}
