package pl.dakil.dakilio.feature.insights.impl.logic

import java.time.DayOfWeek
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import pl.dakil.dakilio.core.model.DateRange
import pl.dakil.dakilio.core.model.Habit

class HabitStatsTest {
    // TODAY is Thursday 29 October 2026; its week starts on Monday the 26th.
    private val walking = Habit(1, WALK, daysPerWeek = 2, position = 0)
    private val range = DateRange(TODAY.minusDays(27), TODAY) // 2 Oct .. 29 Oct

    @Test
    fun `a habit counts its tag's days across every diary`() {
        val posts = listOf(post(0, 4, WALK), post(1, 4, WALK, diaryId = 2), post(7, 3, WALK), post(8), post(40, 3, WALK))
        val stat = habitStats(listOf(walking), posts, range, null, DayOfWeek.MONDAY, TODAY).single()
        assertEquals(3, stat.days)
        // Weeks of 28 Sep (cut to the range), 5, 12, 19 and 26 Oct.
        assertEquals(listOf(0, 0, 0, 1, 2), stat.weeks.map { it.days })
        assertEquals(1, stat.met)
        assertEquals(5, stat.counted)
        assertEquals(1, stat.currentRun)
        assertEquals(TODAY.minusDays(3), stat.inProgress!!.start)
        assertNull(stat.metBefore)
    }

    @Test
    fun `the week in progress counts only once it's on target`() {
        val stat = habitStats(listOf(walking), listOf(post(0, 4, WALK), post(10, 4, WALK)), range, null, DayOfWeek.MONDAY, TODAY).single()
        assertEquals(0, stat.met)
        assertEquals(1, stat.counted) // the week of the first use; the week in progress isn't counted yet
    }

    @Test
    fun `mood is compared between weeks on and off target only with enough of each`() {
        val dates = DateRange(TODAY.minusWeeks(12), TODAY)
        // On target (2 walks, mood 5) every other week; off target (no walk, mood 2) the rest.
        val posts = (1L..12L).flatMap { week ->
            val monday = 3 + (week - 1) * 7 + 3 // a Monday in each finished week
            if (week % 2 == 0L) listOf(post(monday, 5, WALK), post(monday - 1, 5, WALK)) else listOf(post(monday, 2))
        } + (13L..14L).map { post(100 + it, 3, WALK) } // first used long before
        val stat = habitStats(listOf(walking), posts, dates, null, DayOfWeek.MONDAY, TODAY).single()
        assertEquals(5f, stat.moodOnTarget!!, 0.0001f)
        assertEquals(2f, stat.moodOffTarget!!, 0.0001f)
        val few = habitStats(listOf(walking), posts.filter { it.date > TODAY.minusWeeks(5) }, dates, null, DayOfWeek.MONDAY, TODAY).single()
        assertNull(few.moodOnTarget)
    }

    @Test
    fun `the comparison period gives last time's weeks on target`() {
        val posts = listOf(post(30, 4, WALK), post(31, 4, WALK), post(0, 4, WALK))
        val before = DateRange(TODAY.minusDays(55), TODAY.minusDays(28))
        assertEquals(1, habitStats(listOf(walking), posts, range, before, DayOfWeek.MONDAY, TODAY).single().metBefore)
    }
}
