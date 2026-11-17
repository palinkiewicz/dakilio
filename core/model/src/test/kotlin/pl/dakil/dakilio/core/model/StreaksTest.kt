package pl.dakil.dakilio.core.model

import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Test

class StreaksTest {
    private val today = LocalDate.of(2026, 10, 29)
    private fun daysAgo(vararg n: Long) = n.map { today.minusDays(it) }.toSet()

    @Test fun `streak counts back from today`() = assertEquals(3, Streaks.current(daysAgo(0, 1, 2, 4), today))

    @Test fun `today not logged yet keeps yesterday's streak`() = assertEquals(2, Streaks.current(daysAgo(1, 2, 5), today))

    @Test fun `a gap before yesterday breaks the streak`() = assertEquals(0, Streaks.current(daysAgo(2, 3), today))

    @Test fun `no entries means no streak`() {
        assertEquals(0, Streaks.current(emptySet(), today))
        assertEquals(0, Streaks.longest(emptySet()))
    }

    @Test fun `longest finds the longest run anywhere`() = assertEquals(4, Streaks.longest(daysAgo(0, 1, 5, 6, 7, 8, 10)))

    @Test fun `the longest run comes with its dates, the latest of equals`() {
        assertEquals(DateRange(today.minusDays(8), today.minusDays(5)), Streaks.longestRun(daysAgo(0, 1, 5, 6, 7, 8, 10)))
        assertEquals(DateRange(today.minusDays(1), today), Streaks.longestRun(daysAgo(0, 1, 5, 6)))
        assertEquals(null, Streaks.longestRun(emptySet()))
    }

    @Test fun `a run is cut at the range it's asked for`() {
        val range = DateRange(today.minusDays(6), today)
        assertEquals(DateRange(today.minusDays(6), today.minusDays(5)), Streaks.longestWithin(daysAgo(5, 6, 7, 8, 10), range))
    }
}
