package pl.dakil.dakilio.core.model

import java.time.DayOfWeek
import java.time.LocalDate
import java.util.Locale
import org.junit.Assert.assertEquals
import org.junit.Test

class HabitsTest {
    private val monday = LocalDate.of(2026, 11, 2)
    private fun habit(id: Long, target: Int = 3) =
        Habit(id = id, tag = Tag(id = id * 10, groupId = 1, label = "Tag $id", iconKey = "label", position = 0), daysPerWeek = target, position = id.toInt())

    @Test fun `monday starts its own week`() = assertEquals(monday, Habits.weekStart(monday, DayOfWeek.MONDAY))

    @Test fun `sunday belongs to the week before`() = assertEquals(monday, Habits.weekStart(monday.plusDays(6), DayOfWeek.MONDAY))

    @Test fun `the next monday starts a new week`() =
        assertEquals(monday.plusWeeks(1), Habits.weekStart(monday.plusDays(7), DayOfWeek.MONDAY))

    @Test fun `with sunday weeks, sunday starts a new week`() {
        val sunday = monday.minusDays(1)
        assertEquals(sunday, Habits.weekStart(monday, DayOfWeek.SUNDAY))
        assertEquals(sunday.plusWeeks(1), Habits.weekStart(sunday.plusWeeks(1), DayOfWeek.SUNDAY))
        assertEquals(sunday, Habits.weekStart(sunday.plusDays(6), DayOfWeek.SUNDAY))
    }

    @Test fun `with saturday weeks, friday ends the week`() {
        val saturday = monday.minusDays(2)
        assertEquals(saturday, Habits.weekStart(saturday.plusDays(6), DayOfWeek.SATURDAY))
        assertEquals(saturday.plusWeeks(1), Habits.weekStart(saturday.plusDays(7), DayOfWeek.SATURDAY))
    }

    @Test fun `explicit week starts ignore the region`() {
        assertEquals(DayOfWeek.MONDAY, WeekStart.Monday.resolve(Locale.US))
        assertEquals(DayOfWeek.SUNDAY, WeekStart.Sunday.resolve(Locale.forLanguageTag("pl-PL")))
        assertEquals(DayOfWeek.SATURDAY, WeekStart.Saturday.resolve(Locale.UK))
    }

    @Test fun `the region default follows the region`() {
        assertEquals(DayOfWeek.SUNDAY, WeekStart.Region.resolve(Locale.US))
        assertEquals(DayOfWeek.MONDAY, WeekStart.Region.resolve(Locale.forLanguageTag("pl-PL")))
    }

    @Test fun `only habits whose count went up bump`() {
        val habits = listOf(habit(1), habit(2), habit(3))
        val bumps = Habits.bumps(habits, before = mapOf(1L to 1, 2L to 2), after = mapOf(1L to 2, 2L to 2, 3L to 1), weekStart = monday)
        assertEquals(listOf(1L to (1 to 2), 3L to (0 to 1)), bumps.map { it.habit.id to (it.before to it.after) })
    }

    @Test fun `a day already counted doesn't bump again`() {
        val bumps = Habits.bumps(listOf(habit(1)), before = mapOf(1L to 2), after = mapOf(1L to 2), weekStart = monday)
        assertEquals(emptyList<HabitBump>(), bumps)
    }

    @Test fun `removing a tag doesn't bump`() {
        val bumps = Habits.bumps(listOf(habit(1)), before = mapOf(1L to 2), after = mapOf(1L to 1), weekStart = monday)
        assertEquals(emptyList<HabitBump>(), bumps)
    }

    private fun days(vararg offsets: Long) = offsets.map { monday.plusDays(it) }.toSet()

    @Test fun `a habit's weeks start at the tag's first use`() {
        val today = monday.plusWeeks(3).plusDays(2) // Wednesday of the 4th week
        val range = DateRange(monday.minusWeeks(4), today)
        // First used on Tuesday of week 1; 3 days in week 1, 1 in week 2, none in week 3, 2 so far in week 4.
        val weeks = Habits.weeks(days(1, 3, 5, 8, 21, 22), target = 2, range, DayOfWeek.MONDAY, today)
        assertEquals(listOf(monday, monday.plusWeeks(1), monday.plusWeeks(2), monday.plusWeeks(3)), weeks.map { it.start })
        assertEquals(listOf(3, 1, 0, 2), weeks.map { it.days })
        assertEquals(listOf(true, false, false, true), weeks.map { it.met })
        assertEquals(listOf(false, true, true, false), weeks.map { it.missed })
        assertEquals(true, weeks.last().inProgress)
    }

    @Test fun `a habit never used has no weeks`() =
        assertEquals(emptyList<HabitWeek>(), Habits.weeks(emptySet(), 3, DateRange(monday, monday.plusWeeks(2)), DayOfWeek.MONDAY, monday.plusWeeks(2)))

    @Test fun `runs count weeks on target in a row`() {
        fun week(n: Long, days: Int, inProgress: Boolean = false) = HabitWeek(monday.plusWeeks(n), days, 2, inProgress)
        val weeks = listOf(week(0, 2), week(1, 3), week(2, 2), week(3, 0), week(4, 2), week(5, 2), week(6, 1, inProgress = true))
        // The week in progress, not on target yet, doesn't break the run.
        assertEquals(2, Habits.currentRun(weeks))
        assertEquals(3, Habits.longestRun(weeks))
        assertEquals(3, Habits.currentRun(weeks.dropLast(1) + week(6, 2, inProgress = true)))
        assertEquals(0, Habits.currentRun(weeks.dropLast(2) + week(5, 1)))
        assertEquals(0, Habits.longestRun(emptyList()))
    }
}
