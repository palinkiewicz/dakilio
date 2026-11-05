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
}
