package pl.dakil.dakilio.feature.insights.impl

import java.time.DayOfWeek
import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import pl.dakil.dakilio.core.model.Mood
import pl.dakil.dakilio.core.model.Post
import pl.dakil.dakilio.core.model.Tag

class InsightsCalculatorTest {
    private val today = LocalDate.of(2026, 10, 29) // a Thursday
    private val moods = (1..5).map { Mood(it.toLong(), 1, it, Mood.normalize(it, 5), "m$it", "mood_$it", 0) }
    private val walk = Tag(1, 1, "Walk", "directions_walk", 0)
    private val work = Tag(2, 1, "Work", "work", 1)
    private var nextId = 1L

    private fun post(daysAgo: Long, tier: Int?, vararg tags: Tag) = Post(
        id = nextId++,
        dateTime = today.minusDays(daysAgo).atTime(12, 0),
        mood = tier?.let { moods[it - 1] },
        title = null,
        note = null,
        tags = tags.toList(),
        diaryIds = setOf(1),
        photos = emptyList(),
    )

    @Test
    fun `counts, averages and distribution respect the range`() {
        val posts = listOf(post(0, 5, walk), post(1, 3, walk, work), post(1, null), post(40, 1))
        val month = calculateInsights(posts, moods, InsightsRange.Month, today)
        assertEquals(3, month.entryCount)
        assertEquals(2, month.daysJournaled)
        assertEquals(0.75f, month.averageMood!!, 0.0001f) // (1.0 + 0.5) / 2
        assertEquals(listOf(0, 0, 1, 0, 1), month.distribution.map { it.count })
        assertEquals(0f, month.previousAverageMood!!, 0.0001f) // the "awful" day 40 days ago
        assertEquals(listOf(walk to 2, work to 1), month.topTags.map { it.tag to it.count })

        val all = calculateInsights(posts, moods, InsightsRange.All, today)
        assertEquals(4, all.entryCount)
        assertNull(all.previousAverageMood)
    }

    @Test
    fun `streaks use all entries regardless of range`() {
        val posts = listOf(post(1, 4), post(2, 4), post(50, 4), post(51, 4), post(52, 4))
        val insights = calculateInsights(posts, moods, InsightsRange.Month, today)
        assertEquals(2, insights.currentStreak)
        assertEquals(3, insights.longestStreak)
    }

    @Test
    fun `heatmap covers twelve monday-first weeks ending today`() {
        val heat = calculateInsights(listOf(post(0, 4), post(0, 2)), moods, InsightsRange.All, today).heatmap
        assertEquals(DayOfWeek.MONDAY, heat.first().date.dayOfWeek)
        assertEquals(today, heat.last().date)
        assertEquals(11 * 7 + 4, heat.size) // 11 full weeks + Mon..Thu
        assertEquals(2, heat.last().entries)
        assertEquals(0.5f, heat.last().averageMood!!, 0.0001f)
    }

    @Test
    fun `an average maps to the nearest mood`() {
        assertEquals(4, nearestMood(moods, 0.7f)?.tier)
        assertEquals(1, nearestMood(moods, 0.1f)?.tier)
    }
}
