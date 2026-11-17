package pl.dakil.dakilio.feature.insights.impl.logic

import java.time.DayOfWeek
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class RhythmStatsTest {
    @Test
    fun `hours fall into four parts of the day`() {
        assertEquals(DayPart.Night, DayPart.of(4))
        assertEquals(DayPart.Morning, DayPart.of(5))
        assertEquals(DayPart.Afternoon, DayPart.of(12))
        assertEquals(DayPart.Evening, DayPart.of(21))
        assertEquals(DayPart.Night, DayPart.of(22))
        assertEquals(DayPart.Night, DayPart.of(0))
    }

    @Test
    fun `entries are counted by weekday, part of day and hour`() {
        // Today is a Thursday.
        val posts = listOf(post(0, 5, hour = 8), post(0, 3, hour = 20), post(7, 4, hour = 9), post(1, 2, hour = 23), post(2, hour = 9))
        val rhythm = rhythm(posts)
        assertEquals(3, rhythm.weekdays.getValue(DayOfWeek.THURSDAY).entries)
        assertEquals(4f, rhythm.weekdays.getValue(DayOfWeek.THURSDAY).average!!, 0.0001f)
        assertEquals(0, rhythm.weekdays.getValue(DayOfWeek.MONDAY).entries)
        assertNull(rhythm.weekdays.getValue(DayOfWeek.TUESDAY).average) // an entry without a mood
        assertEquals(3, rhythm.dayParts.getValue(DayPart.Morning).entries)
        assertEquals(2, rhythm.matrix.getValue(DayOfWeek.THURSDAY to DayPart.Morning).entries)
        assertEquals(2, rhythm.hours[9])
        assertEquals(24, rhythm.hours.size)
        assertEquals(DayPart.Morning, rhythm.busiestDayPart)
    }

    @Test
    fun `a best slot needs enough moods and a clear lead`() {
        val thursdays = (0L..2).map { post(it * 7, 5) }
        val wednesdays = (0L..2).map { post(it * 7 + 1, 3) }
        assertEquals(DayOfWeek.THURSDAY, rhythm(thursdays + wednesdays).bestWeekday)
        // Only one weekday with enough moods: nothing to be the best of.
        assertNull(rhythm(thursdays + wednesdays.take(2)).bestWeekday)
        // A tie.
        assertNull(rhythm(thursdays + (0L..2).map { post(it * 7 + 1, 5) }).bestWeekday)
        // A weekday with too few moods to trust sits higher: the chart's tallest column isn't the best, so nothing is.
        val fridays = (0L..2).map { post(it * 7 + 6, 4) }
        assertEquals(DayOfWeek.FRIDAY, rhythm(fridays + wednesdays).bestWeekday)
        assertNull(rhythm(fridays + wednesdays + post(0, 5)).bestWeekday)
    }

    @Test
    fun `the matrix names its standout slots`() {
        // Thursday mornings are bright and busy, Wednesday evenings heavy.
        val posts = (0L..3).map { post(it * 7, 5, hour = 8) } + (0L..2).map { post(it * 7 + 1, 2, hour = 20) } + post(2, 3, hour = 13)
        val rhythm = rhythm(posts)
        assertEquals(DayOfWeek.THURSDAY to DayPart.Morning, rhythm.brightestSlot)
        assertEquals(DayOfWeek.WEDNESDAY to DayPart.Evening, rhythm.heaviestSlot)
        assertEquals(DayOfWeek.THURSDAY to DayPart.Morning, rhythm.busiestSlot)
        // One trusted slot is nothing to compare; equal counts are a tie.
        val thin = rhythm(posts.take(4) + post(1, 2, hour = 20))
        assertNull(thin.brightestSlot)
        assertNull(rhythm(listOf(post(0, 5, hour = 8), post(1, 2, hour = 20))).busiestSlot)
        assertNull(rhythm(emptyList()).busiestSlot)
    }
}
