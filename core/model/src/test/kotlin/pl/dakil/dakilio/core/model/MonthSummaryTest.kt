package pl.dakil.dakilio.core.model

import java.time.LocalDate
import java.time.YearMonth
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class MonthSummaryTest {
    private val today = LocalDate.of(2026, 10, 29)
    private val oct = YearMonth.of(2026, 10)
    private val sep = YearMonth.of(2026, 9)
    private var nextId = 1L

    private fun mood(level: Int) = Mood(level.toLong(), level, "m$level", true, 0)

    private fun post(daysAgo: Long, diary: Long = 1, mood: Mood? = null) = Post(
        id = nextId++,
        dateTime = today.minusDays(daysAgo).atTime(12, 0),
        mood = mood,
        title = "t",
        note = null,
        tags = emptyList(),
        diaryId = diary,
        photos = emptyList(),
    )

    @Test
    fun `month summaries count entries and average the mood level in the filter`() {
        val posts = listOf(
            post(1, mood = mood(5)),
            post(2, mood = mood(4)),
            post(3),
            post(4, diary = 2, mood = mood(1)),
            post(40),
        )
        val all = monthSummaries(posts, null)
        assertEquals(MonthSummary(4, 3), all[oct]) // (5 + 4 + 1) / 3 = 3.3
        assertEquals(MonthSummary(1, null), all[sep])
        assertEquals(MonthSummary(3, 5), monthSummaries(posts, 1)[oct]) // 4.5 rounds up
        assertNull(monthSummaries(posts, 2)[sep])
    }
}
