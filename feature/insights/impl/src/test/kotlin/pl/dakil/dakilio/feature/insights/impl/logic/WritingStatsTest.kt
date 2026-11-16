package pl.dakil.dakilio.feature.insights.impl.logic

import java.time.YearMonth
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import pl.dakil.dakilio.core.model.DateRange

class WritingStatsTest {
    @Test
    fun `words count titles and notes`() {
        val long = post(1, note = "one two  three\nfour", title = "A title")
        val stats = writingStats(listOf(post(0, 4), long, post(2, note = "short", photos = 2), post(3, photos = 1)))
        assertEquals(4, stats.entries)
        assertEquals(2, stats.entriesWithText)
        assertEquals(7, stats.words)
        assertEquals(4, stats.averageWords) // 3.5 rounds up
        assertEquals(long, stats.longest)
        assertEquals(6, stats.longestWords)
        assertEquals(3, stats.photos)
        assertEquals(2, stats.entriesWithPhotos)
    }

    @Test
    fun `nothing written means no longest entry`() {
        val stats = writingStats(listOf(post(0, 4)))
        assertNull(stats.longest)
        assertEquals(0, stats.averageWords)
    }

    @Test
    fun `records pick out the standout months and runs`() {
        // October: 5 entries at 4 over 3 days in a row plus two more. September: 6 entries at 5.
        val october = listOf(post(0, 4), post(0, 4), post(1, 4), post(2, 4), post(10, 4))
        val september = (30L..35).map { post(it, 5) }
        val days = InsightsIndex(october + september + post(60, 1)).days(DateRange(TODAY.minusDays(100), TODAY))
        val records = records(days)
        assertEquals(TODAY.minusDays(60), records.firstEntry)
        assertEquals(YearMonth.of(2026, 9) to 5f, records.bestMonth)
        assertEquals(YearMonth.of(2026, 9) to 6, records.busiestMonth)
        assertEquals(DateRange(TODAY.minusDays(35), TODAY.minusDays(30)), records.longestStreak)
        assertEquals(DateRange(TODAY.minusDays(35), TODAY.minusDays(30)), records.brightRun)
        assertEquals(TODAY to 2, records.busiestDay)
    }
}
