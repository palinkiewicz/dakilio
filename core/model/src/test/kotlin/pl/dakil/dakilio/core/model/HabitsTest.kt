package pl.dakil.dakilio.core.model

import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Test

class HabitsTest {
    private val monday = LocalDate.of(2026, 11, 2)
    private fun habit(id: Long, target: Int = 3) =
        Habit(id = id, tag = Tag(id = id * 10, groupId = 1, label = "Tag $id", iconKey = "label", position = 0), daysPerWeek = target, position = id.toInt())

    @Test fun `monday starts its own week`() = assertEquals(monday, Habits.weekStart(monday))

    @Test fun `sunday belongs to the week before`() = assertEquals(monday, Habits.weekStart(monday.plusDays(6)))

    @Test fun `the next monday starts a new week`() = assertEquals(monday.plusWeeks(1), Habits.weekStart(monday.plusDays(7)))

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
