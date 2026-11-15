package pl.dakil.dakilio.feature.insights.impl

import java.time.DayOfWeek
import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import pl.dakil.dakilio.core.model.Mood
import pl.dakil.dakilio.core.model.Post
import pl.dakil.dakilio.core.model.Tag

class EchoesTest {
    private val today = LocalDate.of(2026, 10, 29) // a Thursday
    private val moods = (1..5).map { Mood(it.toLong(), it, "m$it", isPrimary = true, position = 0) }
    private val walk = Tag(1, 1, "Walk", "directions_walk", 0)
    private val work = Tag(2, 1, "Work", "work", 1)
    private var nextId = 1L

    private fun post(daysAgo: Long, level: Int?, vararg tags: Tag, hour: Int = 12) = Post(
        id = nextId++,
        dateTime = today.minusDays(daysAgo).atTime(hour, 0),
        mood = level?.let { moods[it - 1] },
        title = null,
        note = null,
        tags = tags.toList(),
        diaryId = 1,
        photos = emptyList(),
    )

    @Test
    fun `a few entries say nothing yet`() {
        val posts = (0L..6L).map { post(it, 5, walk) }
        assertTrue(echoes(posts, InsightsRange.Month, today).isEmpty())
    }

    @Test
    fun `entries outside the period don't count`() {
        val old = (40L..60L).map { post(it, if (it % 2 == 0L) 5 else 1, walk) }
        assertTrue(echoes(old, InsightsRange.Month, today).isEmpty())
        assertFalse(echoes(old, InsightsRange.All, today).isEmpty())
    }

    @Test
    fun `tags that come with brighter and heavier entries are named`() {
        // Spread evenly over the weekdays and both halves, so only the tags stand out.
        val posts = (0L..13L).flatMap { day ->
            listOf(post(day, 5, walk, hour = 8), post(day, 1, work, hour = 9), post(day, 3, hour = 10))
        }
        val found = echoes(posts, InsightsRange.Month, today)
        val lift = found.filterIsInstance<Echo.TagLift>().single()
        assertEquals(walk, lift.tag)
        assertEquals(0.75f, lift.delta, 0.001f) // 1.0 against (0 + .5) / 2
        val dip = found.filterIsInstance<Echo.TagDip>().single()
        assertEquals(work, dip.tag)
        assertEquals(0.75f, dip.delta, 0.001f) // reported as a positive distance
        // The strongest lift leads.
        assertTrue(found.first() is Echo.TagLift)
    }

    @Test
    fun `the brightest and the heaviest weekday stand out from the others`() {
        // Four weeks: Saturdays great, Mondays rough, the rest in the middle.
        val posts = (0L..27L).map { day ->
            val date = today.minusDays(day)
            post(
                day,
                when (date.dayOfWeek) {
                    DayOfWeek.SATURDAY -> 5
                    DayOfWeek.MONDAY -> 1
                    else -> 3
                },
            )
        }
        val found = echoes(posts, InsightsRange.Month, today)
        assertEquals(DayOfWeek.SATURDAY, found.filterIsInstance<Echo.BrightWeekday>().single().day)
        assertEquals(DayOfWeek.MONDAY, found.filterIsInstance<Echo.HeavyWeekday>().single().day)
    }

    @Test
    fun `even weekdays give no weekday echo`() {
        val posts = (0L..27L).map { post(it, 3) }
        val found = echoes(posts, InsightsRange.Month, today)
        assertTrue(found.none { it is Echo.BrightWeekday || it is Echo.HeavyWeekday })
    }

    @Test
    fun `a part of the day can be the brightest and the busiest`() {
        // Three evenings for every morning; evenings feel better.
        val posts = (0L..11L).flatMap { day ->
            listOf(post(day, 5, hour = 19), post(day, 4, hour = 20), post(day, 5, hour = 21), post(day, 2, hour = 8))
        }
        val found = echoes(posts, InsightsRange.Month, today)
        assertEquals(DayPart.Evening, found.filterIsInstance<Echo.BrightDayPart>().single().part)
        assertEquals(DayPart.Evening, found.filterIsInstance<Echo.BusyDayPart>().single().part)
    }

    @Test
    fun `the weekday with a quarter of the entries is when you write`() {
        // One entry a day for four weeks, plus four more on each Thursday.
        val posts = (0L..27L).flatMap { day ->
            val extra = if (today.minusDays(day).dayOfWeek == DayOfWeek.THURSDAY) 4 else 0
            List(1 + extra) { post(day, 3) }
        }
        val found = echoes(posts, InsightsRange.Month, today)
        assertEquals(DayOfWeek.THURSDAY, found.filterIsInstance<Echo.BusyWeekday>().single().day)
    }

    @Test
    fun `halves of the period show where the mood is heading`() {
        val rising = (0L..29L).map { post(it, if (it < 15) 4 else 2) }
        val up = echoes(rising, InsightsRange.Month, today).filterIsInstance<Echo.MoodRising>().single()
        assertEquals(0.5f, up.delta, 0.001f)

        val falling = (0L..29L).map { post(it, if (it < 15) 2 else 5) }
        val down = echoes(falling, InsightsRange.Month, today).filterIsInstance<Echo.MoodFalling>().single()
        assertEquals(0.75f, down.delta, 0.001f)

        val steady = (0L..29L).map { post(it, 3) }
        assertTrue(echoes(steady, InsightsRange.Month, today).none { it is Echo.MoodRising || it is Echo.MoodFalling })
    }

    @Test
    fun `all time halves run from the first entry to today`() {
        val posts = (0L..99L).map { post(it, if (it < 50) 5 else 3) }
        assertTrue(echoes(posts, InsightsRange.All, today).any { it is Echo.MoodRising })
    }

    @Test
    fun `at most four echoes are shown`() {
        val posts = (0L..27L).flatMap { day ->
            val date = today.minusDays(day)
            val level = when (date.dayOfWeek) {
                DayOfWeek.SATURDAY -> 5
                DayOfWeek.MONDAY -> 1
                else -> if (day < 14) 4 else 2
            }
            listOf(post(day, level, walk, hour = 19), post(day, 5, walk, hour = 20), post(day, 1, work, hour = 8))
        }
        assertEquals(4, echoes(posts, InsightsRange.Month, today).size)
    }
}
