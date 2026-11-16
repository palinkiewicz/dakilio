package pl.dakil.dakilio.feature.insights.impl.logic

import java.time.DayOfWeek
import java.time.YearMonth
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import pl.dakil.dakilio.core.model.Post
import pl.dakil.dakilio.core.model.monthSummaries

class RecapTest {
    private fun recapFor(posts: List<Post>, period: InsightsPeriod): Recap {
        val index = InsightsIndex(posts)
        val bounds = Bounds(index.first ?: TODAY, TODAY)
        val overview = buildOverview(index, period, bounds, Comparison.Previous, MOODS, DayOfWeek.MONDAY, allPosts = posts)
        return recap(overview, index, period, monthSummaries(posts, null))
    }

    private val september = InsightsPeriod.Month(YearMonth.of(2026, 9))

    @Test
    fun `a thin month gets only the pages it can fill`() {
        // Five entries, two with a mood, no tags: title, numbers and the end.
        val posts = listOf(post(30, 4), post(31, 3), post(32), post(33), post(34))
        assertEquals(listOf(RecapPage.Title, RecapPage.Numbers, RecapPage.End), recapFor(posts, september).pages)
    }

    @Test
    fun `a full month tells mood, its brightest day, tags and the change`() {
        val august = (60L..70).map { post(it, 2) }
        val sept = (30L..44).map { post(it, if (it == 35L) 5 else 3, WALK, note = "x".repeat(it.toInt())) } + post(35, 5, note = "a much longer note with many words in it")
        val recap = recapFor(august + sept, september)
        assertEquals(
            listOf(RecapPage.Title, RecapPage.Numbers, RecapPage.Mood, RecapPage.BrightestDay, RecapPage.Tags, RecapPage.Rhythm, RecapPage.Change, RecapPage.End),
            recap.pages,
        )
        // Of the brightest day's two great entries, the one with more words.
        assertEquals("a much longer note with many words in it", recap.brightest!!.note)
        assertEquals(5f, recap.brightestMood!!, 0.0001f)
        assertEquals(emptyList<Any>(), recap.months)
    }

    @Test
    fun `a year adds its months and records`() {
        val posts = (0L..300 step 3).map { post(it, (it % 5 + 1).toInt()) }
        val recap = recapFor(posts, InsightsPeriod.Year(2026))
        assertEquals(12, recap.months.size)
        assertNull(recap.months.last().second) // December hasn't come yet
        assert(RecapPage.Months in recap.pages && RecapPage.Records in recap.pages)
    }
}
