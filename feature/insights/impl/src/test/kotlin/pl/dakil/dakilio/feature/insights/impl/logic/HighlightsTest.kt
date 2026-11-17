package pl.dakil.dakilio.feature.insights.impl.logic

import java.time.DayOfWeek
import java.time.YearMonth
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import pl.dakil.dakilio.core.model.Habit
import pl.dakil.dakilio.core.model.Post

class HighlightsTest {
    private val october = InsightsPeriod.Month(YearMonth.of(2026, 10))

    private fun highlightsFor(posts: List<Post>, period: InsightsPeriod = october, habits: List<Habit> = emptyList()): List<Highlight> {
        val index = InsightsIndex(posts)
        val bounds = Bounds(index.first ?: TODAY, TODAY)
        val overview = buildOverview(index, period, bounds, Comparison.Previous, MOODS, DayOfWeek.MONDAY, habits = habits, allPosts = posts)
        return highlights(overview, index, period, bounds)
    }

    @Test
    fun `a thin period has nothing to highlight`() {
        assertEquals(emptyList<Highlight>(), highlightsFor(listOf(post(0, 4), post(1, 3))))
    }

    @Test
    fun `a strong tag link and a mood change are highlighted, best backed first`() {
        // October: walks on bright days. September: heavier.
        val october = (0L..9).map { post(it, 5, WALK) } + (10L..19).map { post(it, 3) }
        val september = (30L..45).map { post(it, 2) }
        val result = highlightsFor(october + september)
        assertTrue(result.any { it is Highlight.TagBrighter && it.tag == WALK })
        val change = result.filterIsInstance<Highlight.MoodChange>().single()
        assertEquals(2f, change.change, 0.0001f)
        assertEquals(result.sortedByDescending { it.score }, result)
    }

    @Test
    fun `two highlights never tell the same story`() {
        // A walking habit on a run, and walks on bright days: one highlight about walking.
        val walking = Habit(1, WALK, daysPerWeek = 1, position = 0)
        val posts = (0L..27).map { if (it % 3 == 0L) post(it, 5, WALK) else post(it, 3) } + (60L..70).map { post(it, 3, WALK) }
        val result = highlightsFor(posts, habits = listOf(walking))
        assertEquals(1, result.count { it.subject == "tag-${WALK.id}" })
        assertEquals(result.map { it.subject }.distinct(), result.map { it.subject })
    }

    @Test
    fun `the longest streak yet is noticed`() {
        val posts = (0L..9).map { post(it, 4) } + listOf(post(20, 4), post(21, 4))
        val streak = highlightsFor(posts).filterIsInstance<Highlight.StreakRecord>().single()
        assertEquals(10, streak.days)
        // Not when an earlier streak was longer.
        assertTrue(highlightsFor(posts + (40L..55).map { post(it, 4) }).none { it is Highlight.StreakRecord })
    }

    @Test
    fun `the brightest month yet needs enough months to beat`() {
        val earlier = (1..3).flatMap { months -> (0L..11).map { post(30L * months + it + 1, 3) } }
        val now = (0L..11).map { post(it, 5) }
        assertTrue(highlightsFor(now + earlier).any { it is Highlight.BestMonthYet })
        assertTrue(highlightsFor(now + earlier.drop(12)).none { it is Highlight.BestMonthYet })
    }

    @Test
    fun `at most six are shown`() {
        val tags = (10L..19L).map { pl.dakil.dakilio.core.model.Tag(it, 1, "T$it", "label", 0) }
        val posts = (0L..27).map { day -> post(day, if (day % 2 == 0L) 5 else 2, *(if (day % 2 == 0L) tags.toTypedArray() else emptyArray())) } +
            (30L..58).map { post(it, 1) }
        assertTrue(highlightsFor(posts).size <= MAX_HIGHLIGHTS)
    }
}
