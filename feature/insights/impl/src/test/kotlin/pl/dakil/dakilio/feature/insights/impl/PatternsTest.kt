package pl.dakil.dakilio.feature.insights.impl

import androidx.compose.ui.geometry.Offset
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalDateTime
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import pl.dakil.dakilio.core.model.Mood
import pl.dakil.dakilio.core.model.Post
import pl.dakil.dakilio.core.model.Tag

class PatternsTest {
    private val today = LocalDate.of(2026, 10, 29) // a Thursday
    private val moods = (1..5).map { Mood(it.toLong(), it, "m$it", isPrimary = true, position = 0) }
    private val walk = Tag(1, 1, "Walk", "directions_walk", 0)
    private val work = Tag(2, 1, "Work", "work", 1)
    private val rare = Tag(3, 1, "Rare", "star", 2)
    private var nextId = 1L

    private fun post(daysAgo: Long, level: Int?, vararg tags: Tag, hour: Int = 12) =
        at(today.minusDays(daysAgo).atTime(hour, 0), level, *tags)

    private fun at(dateTime: LocalDateTime, level: Int?, vararg tags: Tag) = Post(
        id = nextId++,
        dateTime = dateTime,
        mood = level?.let { moods[it - 1] },
        title = null,
        note = null,
        tags = tags.toList(),
        diaryId = 1,
        photos = emptyList(),
    )

    @Test
    fun `thirty days trend goes day by day and keeps empty days as gaps`() {
        val trend = moodTrend(listOf(post(0, 5), post(0, 3), post(2, null), post(40, 1)), InsightsRange.Month, today)
        assertEquals(TrendBucket.Day, trend.bucket)
        assertEquals(30, trend.points.size)
        assertEquals(today.minusDays(29), trend.points.first().start)
        val last = trend.points.last()
        assertEquals(today, last.start)
        assertEquals(2, last.entries)
        assertEquals(0.75f, last.averageMood!!, 0.0001f)
        // An entry without a mood counts as an entry, not as a mood.
        val noMood = trend.points[27]
        assertEquals(1, noMood.entries)
        assertNull(noMood.averageMood)
        assertEquals(0, trend.points[28].entries)
        assertTrue(trend.hasMood)
    }

    @Test
    fun `ninety days trend goes week by week from the first day of the week`() {
        val trend = moodTrend(listOf(post(0, 4), post(3, 2), post(4, 5)), InsightsRange.Quarter, today, DayOfWeek.MONDAY)
        assertEquals(TrendBucket.Week, trend.bucket)
        // The first and last weeks are clipped to the range.
        assertEquals(today.minusDays(89), trend.points.first().start)
        assertEquals(today, trend.points.last().end)
        assertEquals(LocalDate.of(2026, 10, 26), trend.points.last().start)
        // Mon 26 – Thu 29: today and Monday (3 days ago); Sunday the 25th is the week before.
        assertEquals(2, trend.points.last().entries)
        assertEquals(0.5f, trend.points.last().averageMood!!, 0.0001f)
        assertEquals(1, trend.points[trend.points.size - 2].entries)

        val sunday = moodTrend(listOf(post(0, 4), post(3, 2), post(4, 5)), InsightsRange.Quarter, today, DayOfWeek.SUNDAY)
        assertEquals(LocalDate.of(2026, 10, 25), sunday.points.last().start)
        assertEquals(3, sunday.points.last().entries)
    }

    @Test
    fun `all time trend starts at the first entry and turns monthly once it is long`() {
        val short = moodTrend(listOf(post(0, 4), post(20, 2)), InsightsRange.All, today)
        assertEquals(TrendBucket.Week, short.bucket)
        assertEquals(today.minusDays(20), short.points.first().start)

        val long = moodTrend(listOf(post(0, 4), post(400, 2)), InsightsRange.All, today)
        assertEquals(TrendBucket.Month, long.bucket)
        assertEquals(today.minusDays(400), long.points.first().start)
        assertEquals(LocalDate.of(2026, 10, 1), long.points.last().start)
        assertEquals(today, long.points.last().end)
        assertEquals(14, long.points.size) // Sep 2025 … Oct 2026
    }

    @Test
    fun `trend of nothing is empty`() {
        assertEquals(MoodTrend.Empty, moodTrend(emptyList(), InsightsRange.All, today))
        assertFalse(moodTrend(listOf(post(1, null)), InsightsRange.Month, today).hasMood)
    }

    @Test
    fun `weekdays start on the chosen day and average their moods`() {
        // Today is a Thursday; 7 days ago too.
        val days = weekdayMoods(listOf(post(0, 5), post(7, 3), post(1, 1), post(2, null)), DayOfWeek.SUNDAY)
        assertEquals(DayOfWeek.SUNDAY, days.first().day)
        assertEquals(DayOfWeek.SATURDAY, days.last().day)
        val thursday = days.first { it.day == DayOfWeek.THURSDAY }
        assertEquals(2, thursday.entries)
        assertEquals(0.75f, thursday.averageMood!!, 0.0001f)
        val tuesday = days.first { it.day == DayOfWeek.TUESDAY }
        assertEquals(1, tuesday.entries)
        assertNull(tuesday.averageMood)
        assertEquals(0, days.first { it.day == DayOfWeek.MONDAY }.entries)
    }

    @Test
    fun `parts of the day follow the wall clock and nights run past midnight`() {
        assertEquals(DayPart.Night, DayPart.of(0))
        assertEquals(DayPart.Night, DayPart.of(4))
        assertEquals(DayPart.Morning, DayPart.of(5))
        assertEquals(DayPart.Morning, DayPart.of(11))
        assertEquals(DayPart.Afternoon, DayPart.of(12))
        assertEquals(DayPart.Evening, DayPart.of(17))
        assertEquals(DayPart.Evening, DayPart.of(21))
        assertEquals(DayPart.Night, DayPart.of(22))

        val parts = dayPartMoods(listOf(post(0, 5, hour = 8), post(1, 3, hour = 9), post(0, 1, hour = 23), post(1, null, hour = 2)))
        assertEquals(DayPart.entries, parts.map { it.part })
        assertEquals(listOf(2, 0, 0, 2), parts.map { it.entries })
        assertEquals(0.75f, parts[0].averageMood!!, 0.0001f)
        assertNull(parts[1].averageMood)
        assertEquals(0f, parts[3].averageMood!!, 0.0001f)
    }

    @Test
    fun `tag influences compare entries with a tag against the rest`() {
        val posts = listOf(
            post(0, 5, walk), post(1, 5, walk), post(2, 4, walk),
            post(3, 2, work), post(4, 1, work), post(5, 2, work),
            post(6, 3), post(7, 3, rare), post(8, null, walk),
        )
        val influences = tagInfluences(posts)
        // Walk: (1 + 1 + .75) / 3 = .9167 against (.25 + 0 + .25 + .5 + .5) / 5 = .3
        val lift = influences.lifts.single()
        assertEquals(walk, lift.tag)
        assertEquals(3, lift.entries)
        assertEquals(0.9167f, lift.averageMood, 0.001f)
        assertEquals(0.6167f, lift.delta, 0.001f)
        // Work: (.25 + 0 + .25) / 3 = .1667 against (1 + 1 + .75 + .5 + .5) / 5 = .75
        val dip = influences.dips.single()
        assertEquals(work, dip.tag)
        assertEquals(-0.5833f, dip.delta, 0.001f)
        // A tag with too few entries is never compared.
        assertTrue((influences.lifts + influences.dips).none { it.tag == rare })
    }

    @Test
    fun `tag influences need entries on both sides and a real difference`() {
        assertTrue(tagInfluences(listOf(post(0, 5, walk), post(1, 5, walk), post(2, 5, walk))).isEmpty)
        // Same average with and without the tag: nothing to say.
        val even = listOf(post(0, 3, walk), post(1, 3, walk), post(2, 3, walk), post(3, 3), post(4, 3), post(5, 3))
        assertTrue(tagInfluences(even).isEmpty)
    }

    @Test
    fun `year pixels hold the days of that year only`() {
        val posts = listOf(
            at(LocalDateTime.of(2026, 1, 1, 9, 0), 5),
            at(LocalDateTime.of(2026, 1, 1, 21, 0), 3),
            at(LocalDateTime.of(2026, 2, 28, 9, 0), null),
            at(LocalDateTime.of(2025, 12, 31, 9, 0), 1),
        )
        val year = yearPixels(posts, 2026)
        assertEquals(2, year.daysJournaled)
        val first = year.days.getValue(LocalDate.of(2026, 1, 1))
        assertEquals(2, first.entries)
        assertEquals(0.75f, first.averageMood!!, 0.0001f)
        assertNull(year.days.getValue(LocalDate.of(2026, 2, 28)).averageMood)
        assertEquals(0.75f, year.averageMood!!, 0.0001f)
        assertEquals(12, year.months.size)
        assertEquals(2025..2026, pixelYears(posts, today))
        assertEquals(2026..2026, pixelYears(emptyList(), today))
    }

    @Test
    fun `a position in the year grid maps to its day`() {
        // 10 px cells with a 2 px gap, 30 px of month labels, 16 px of day labels.
        fun day(x: Float, y: Float) = dayAt(Offset(x, y), 2026, today, cell = 10f, gap = 2f, left = 30f, top = 16f)
        assertEquals(LocalDate.of(2026, 1, 1), day(31f, 17f))
        assertEquals(LocalDate.of(2026, 1, 31), day(30f + 30 * 12 + 5f, 20f))
        assertEquals(LocalDate.of(2026, 3, 2), day(30f + 12 + 1f, 16f + 2 * 12 + 1f))
        // February has no 30th, and the labels aren't days.
        assertNull(day(30f + 29 * 12 + 1f, 16f + 12 + 1f))
        assertNull(day(10f, 20f))
        assertNull(day(40f, 5f))
        // Nothing after today (29 October).
        assertEquals(today, day(30f + 28 * 12 + 1f, 16f + 9 * 12 + 1f))
        assertNull(day(30f + 29 * 12 + 1f, 16f + 9 * 12 + 1f))
        assertNull(day(31f, 16f + 11 * 12 + 1f))
    }

    @Test
    fun `plot points spread evenly and map back`() {
        assertEquals(50f, pointX(0, 100f, 1, 8f), 0.001f)
        assertEquals(8f, pointX(0, 100f, 5, 8f), 0.001f)
        assertEquals(92f, pointX(4, 100f, 5, 8f), 0.001f)
        assertEquals(50f, pointX(2, 100f, 5, 8f), 0.001f)
        assertEquals(0, nearestPoint(0f, 100f, 5, 8f))
        assertEquals(2, nearestPoint(55f, 100f, 5, 8f))
        assertEquals(4, nearestPoint(500f, 100f, 5, 8f))
        assertEquals(0, nearestPoint(40f, 100f, 1, 8f))
    }

    @Test
    fun `bars for the worst mood still show`() {
        assertEquals(0.2f, moodBarFraction(0f), 0.0001f)
        assertEquals(0.6f, moodBarFraction(0.5f), 0.0001f)
        assertEquals(1f, moodBarFraction(1f), 0.0001f)
    }
}
