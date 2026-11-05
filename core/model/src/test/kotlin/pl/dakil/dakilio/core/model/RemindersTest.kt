package pl.dakil.dakilio.core.model

import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class RemindersTest {
    // A Thursday.
    private val today = LocalDate.of(2026, 11, 5)
    private val evening = LocalTime.of(20, 0)

    private fun diary(id: Long, time: LocalTime = evening) = DiaryReminder(id, "Diary $id", time)

    private fun habit(id: Long, target: Int = 3, time: LocalTime = evening) = HabitReminder(id, id * 10, "Habit $id", target, time)

    private fun snapshot(
        diaries: List<DiaryReminder> = emptyList(),
        habits: List<HabitReminder> = emptyList(),
        writtenToday: Set<Long> = emptySet(),
        weekCounts: Map<Long, Int> = emptyMap(),
        firstDay: DayOfWeek = DayOfWeek.MONDAY,
    ) = ReminderSnapshot(today, firstDay, diaries, habits, writtenToday, weekCounts)

    private fun at(hour: Int, minute: Int = 0, date: LocalDate = today) = date.atTime(hour, minute)

    @Test fun `nothing on means no alarm`() = assertNull(Reminders.next(at(9), snapshot()))

    @Test fun `the earliest reminder wins and reminders at the same time share the alarm`() {
        val next = Reminders.next(
            at(9),
            snapshot(diaries = listOf(diary(1), diary(2, LocalTime.of(18, 30))), habits = listOf(habit(5, time = LocalTime.of(18, 30)))),
        )!!
        assertEquals(at(18, 30), next.at)
        assertEquals(setOf(2L), next.diaryIds)
        assertEquals(setOf(5L), next.habitIds)
    }

    @Test fun `a time already past today moves to tomorrow`() =
        assertEquals(at(20, date = today.plusDays(1)), Reminders.next(at(21), snapshot(diaries = listOf(diary(1))))!!.at)

    @Test fun `after is exclusive`() =
        assertEquals(at(20, date = today.plusDays(1)), Reminders.next(at(20), snapshot(diaries = listOf(diary(1))))!!.at)

    @Test fun `a reminder just after a late alarm still comes, at once`() {
        // The 20:00 alarm went off late; the 20:05 reminder is planned after 20:00, so it isn't skipped.
        val next = Reminders.next(at(20), snapshot(diaries = listOf(diary(1), diary(2, LocalTime.of(20, 5)))))!!
        assertEquals(at(20, 5), next.at)
        assertEquals(setOf(2L), next.diaryIds)
    }

    @Test fun `a diary written today waits until tomorrow`() {
        val next = Reminders.next(at(9), snapshot(diaries = listOf(diary(1)), writtenToday = setOf(1)))!!
        assertEquals(at(20, date = today.plusDays(1)), next.at)
    }

    @Test fun `a habit below its target comes every day`() =
        assertEquals(at(20), Reminders.next(at(9), snapshot(habits = listOf(habit(1, target = 3)), weekCounts = mapOf(1L to 2)))!!.at)

    @Test fun `a habit that met its target rests until the next week`() {
        val next = Reminders.next(at(9), snapshot(habits = listOf(habit(1, target = 2)), weekCounts = mapOf(1L to 2)))!!
        assertEquals(at(20, date = LocalDate.of(2026, 11, 9)), next.at) // Monday
    }

    @Test fun `with sunday weeks, a met habit comes back on sunday`() {
        val next = Reminders.next(
            at(9),
            snapshot(habits = listOf(habit(1, target = 1)), weekCounts = mapOf(1L to 1), firstDay = DayOfWeek.SUNDAY),
        )!!
        assertEquals(at(20, date = LocalDate.of(2026, 11, 8)), next.at)
    }

    @Test fun `the last day of the week rolls into a fresh week`() {
        // Sunday evening, past the reminder: tomorrow is a new week, so this week's count doesn't matter.
        val sunday = LocalDate.of(2026, 11, 8)
        val snapshot = ReminderSnapshot(sunday, DayOfWeek.MONDAY, emptyList(), listOf(habit(1, target = 3)), emptySet(), mapOf(1L to 1))
        assertEquals(at(20, date = sunday.plusDays(1)), Reminders.next(at(21, date = sunday), snapshot)!!.at)
    }

    @Test fun `due keeps only reminders that still apply`() {
        val snapshot = snapshot(
            diaries = listOf(diary(1), diary(2), diary(3, LocalTime.of(21, 0))),
            habits = listOf(habit(4, target = 1), habit(5, target = 2)),
            writtenToday = setOf(2),
            weekCounts = mapOf(4L to 1, 5L to 1),
        )
        val due = Reminders.due(at(20), snapshot)
        assertEquals(listOf(1L), due.diaries.map { it.diaryId })
        assertEquals(listOf(5L), due.habits.map { it.habitId })
    }

    @Test fun `an alarm from another day shows nothing`() =
        assertTrue(Reminders.due(at(20, date = today.minusDays(1)), snapshot(diaries = listOf(diary(1)))).isEmpty)

    @Test fun `texts rotate without repeating on consecutive days`() {
        val size = 10
        val week = (0L..6L).map { Reminders.textIndex(size, today.plusDays(it), id = 7) }
        week.zipWithNext().forEach { (a, b) -> assertTrue(a != b) }
        assertTrue(week.all { it in 0 until size })
        assertEquals(0, Reminders.textIndex(0, today, 1))
    }

    @Test fun `minutes round-trip`() {
        val reminder = Reminder.ofMinute(enabled = true, minuteOfDay = 7 * 60 + 45)
        assertEquals(LocalTime.of(7, 45), reminder.time)
        assertEquals(7 * 60 + 45, reminder.minuteOfDay)
    }
}
