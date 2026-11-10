package pl.dakil.dakilio.core.model

import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalTime
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class RemindersTest {
    // A Thursday.
    private val today = LocalDate.of(2026, 11, 5)
    private val evening = LocalTime.of(20, 0)

    private fun diary(id: Long, time: LocalTime = evening, days: Set<DayOfWeek> = ReminderDays.EVERY_DAY, reminderId: Long = id) =
        PlannedReminder(reminderId, ReminderSubject.Diary(id, "Diary $id"), time, days)

    private fun habit(id: Long, target: Int = 3, time: LocalTime = evening, days: Set<DayOfWeek> = ReminderDays.EVERY_DAY) =
        PlannedReminder(100 + id, ReminderSubject.Habit(id, id * 10, "Habit $id", target), time, days)

    private fun snapshot(
        vararg reminders: PlannedReminder,
        writtenToday: Set<Long> = emptySet(),
        weekCounts: Map<Long, Int> = emptyMap(),
        firstDay: DayOfWeek = DayOfWeek.MONDAY,
        today: LocalDate = this.today,
    ) = ReminderSnapshot(today, firstDay, reminders.toList(), writtenToday, weekCounts)

    private fun at(hour: Int, minute: Int = 0, date: LocalDate = today) = date.atTime(hour, minute)

    @Test fun `nothing on means no alarm`() = assertNull(Reminders.next(at(9), snapshot()))

    @Test fun `the earliest reminder wins and reminders at the same time share the alarm`() {
        val next = Reminders.next(at(9), snapshot(diary(1), diary(2, LocalTime.of(18, 30)), habit(5, time = LocalTime.of(18, 30))))!!
        assertEquals(at(18, 30), next.at)
        assertEquals(setOf(2L, 105L), next.reminderIds)
    }

    @Test fun `a time already past today moves to tomorrow`() =
        assertEquals(at(20, date = today.plusDays(1)), Reminders.next(at(21), snapshot(diary(1)))!!.at)

    @Test fun `after is exclusive`() =
        assertEquals(at(20, date = today.plusDays(1)), Reminders.next(at(20), snapshot(diary(1)))!!.at)

    @Test fun `a reminder just after a late alarm still comes, at once`() {
        // The 20:00 alarm went off late; the 20:05 reminder is planned after 20:00, so it isn't skipped.
        val next = Reminders.next(at(20), snapshot(diary(1), diary(2, LocalTime.of(20, 5))))!!
        assertEquals(at(20, 5), next.at)
        assertEquals(setOf(2L), next.reminderIds)
    }

    @Test fun `a diary written today waits until tomorrow`() =
        assertEquals(at(20, date = today.plusDays(1)), Reminders.next(at(9), snapshot(diary(1), writtenToday = setOf(1)))!!.at)

    @Test fun `a reminder comes only on its weekdays`() {
        // Thursday morning; only Mondays and Saturdays.
        val next = Reminders.next(at(9), snapshot(diary(1, days = setOf(DayOfWeek.MONDAY, DayOfWeek.SATURDAY))))!!
        assertEquals(at(20, date = LocalDate.of(2026, 11, 7)), next.at)
    }

    @Test fun `a diary written today skips to its next weekday, not to tomorrow`() {
        val thursdays = setOf(DayOfWeek.THURSDAY)
        val next = Reminders.next(at(9), snapshot(diary(1, days = thursdays), writtenToday = setOf(1)))!!
        assertEquals(at(20, date = today.plusWeeks(1)), next.at)
    }

    @Test fun `a habit below its target comes every day`() =
        assertEquals(at(20), Reminders.next(at(9), snapshot(habit(1, target = 3), weekCounts = mapOf(1L to 2)))!!.at)

    @Test fun `a habit that met its target rests until the next week`() {
        val next = Reminders.next(at(9), snapshot(habit(1, target = 2), weekCounts = mapOf(1L to 2)))!!
        assertEquals(at(20, date = LocalDate.of(2026, 11, 9)), next.at) // Monday
    }

    @Test fun `a met habit comes back on its first weekday of the next week`() {
        // Met this week; it reminds only on Wednesdays, so the next one is next week's Wednesday.
        val next = Reminders.next(at(9), snapshot(habit(1, target = 1, days = setOf(DayOfWeek.WEDNESDAY)), weekCounts = mapOf(1L to 1)))!!
        assertEquals(at(20, date = LocalDate.of(2026, 11, 11)), next.at)
    }

    @Test fun `with sunday weeks, a met habit comes back on sunday`() {
        val next = Reminders.next(at(9), snapshot(habit(1, target = 1), weekCounts = mapOf(1L to 1), firstDay = DayOfWeek.SUNDAY))!!
        assertEquals(at(20, date = LocalDate.of(2026, 11, 8)), next.at)
    }

    @Test fun `the last day of the week rolls into a fresh week`() {
        // Sunday evening, past the reminder: tomorrow is a new week, so this week's count doesn't matter.
        val sunday = LocalDate.of(2026, 11, 8)
        val snapshot = snapshot(habit(1, target = 3), weekCounts = mapOf(1L to 1), today = sunday)
        assertEquals(at(20, date = sunday.plusDays(1)), Reminders.next(at(21, date = sunday), snapshot)!!.at)
    }

    @Test fun `due keeps only reminders that still apply`() {
        val snapshot = snapshot(
            diary(1),
            diary(2),
            diary(3, LocalTime.of(21, 0)),
            diary(4, days = setOf(DayOfWeek.FRIDAY)),
            habit(4, target = 1),
            habit(5, target = 2),
            writtenToday = setOf(2),
            weekCounts = mapOf(4L to 1, 5L to 1),
        )
        val due = Reminders.due(at(20), snapshot)
        assertEquals(listOf(1L), due.diaries.map { it.diaryId })
        assertEquals(listOf(5L), due.habits.map { it.habitId })
    }

    @Test fun `two reminders of one diary at the same moment show once`() {
        val due = Reminders.due(at(20), snapshot(diary(1, reminderId = 1), diary(1, reminderId = 2)))
        assertEquals(listOf(1L), due.diaries.map { it.diaryId })
    }

    @Test fun `an alarm from another day shows nothing`() =
        assertTrue(Reminders.due(at(20, date = today.minusDays(1)), snapshot(diary(1))).isEmpty)

    @Test fun `an alarm later on its own day still shows`() =
        assertTrue(Reminders.stillDue(at(20), at(23, 50)))

    @Test fun `an alarm that slipped past midnight shows within the grace`() {
        val lateEvening = at(23, 30, date = today.minusDays(1))
        assertTrue(Reminders.stillDue(lateEvening, at(0, 40)))
        assertTrue(Reminders.stillDue(lateEvening, at(2, 30)))
        assertFalse(Reminders.stillDue(lateEvening, at(2, 31)))
    }

    @Test fun `an alarm from yesterday's evening isn't due the next morning`() =
        assertFalse(Reminders.stillDue(at(20, date = today.minusDays(1)), at(9)))

    @Test fun `a late alarm sets off the reminders it passed, one by one`() {
        // The 23:00 alarm came at 00:50; the 23:30 reminder is past but still due.
        val yesterday = today.minusDays(1)
        val snapshot = snapshot(diary(1, LocalTime.of(23, 0)), diary(2, LocalTime.of(23, 30)), today = yesterday)
        val missed = Reminders.missed(at(23, date = yesterday), at(0, 50), snapshot)!!
        assertEquals(at(23, 30, date = yesterday), missed.at)
        assertEquals(setOf(2L), missed.reminderIds)
        // After that one, the next is tonight's 23:00: still to come, so nothing is missed.
        assertNull(Reminders.missed(missed.at, at(0, 50), snapshot))
    }

    @Test fun `a passed reminder beyond the grace isn't set off`() {
        val yesterday = today.minusDays(1)
        val snapshot = snapshot(diary(1, LocalTime.of(20, 0)), diary(2, LocalTime.of(21, 0)), today = yesterday)
        assertNull(Reminders.missed(at(20, date = yesterday), at(9), snapshot))
    }

    @Test fun `the reminder after a late alarm checks entries of the alarm's own day`() {
        val yesterday = today.minusDays(1)
        val snapshot = snapshot(diary(1, LocalTime.of(23, 0)), diary(2, LocalTime.of(23, 30)), writtenToday = setOf(2), today = yesterday)
        assertTrue(Reminders.due(at(23, 30, date = yesterday), snapshot).isEmpty)
    }

    @Test fun `texts rotate without repeating on consecutive days`() {
        val size = 10
        val week = (0L..6L).map { Reminders.textIndex(size, today.plusDays(it), id = 7) }
        week.zipWithNext().forEach { (a, b) -> assertTrue(a != b) }
        assertTrue(week.all { it in 0 until size })
        assertEquals(0, Reminders.textIndex(0, today, 1))
    }

    @Test fun `minutes round-trip`() {
        val time = Reminder.timeOf(7 * 60 + 45)
        assertEquals(LocalTime.of(7, 45), time)
        assertEquals(7 * 60 + 45, time.minuteOfDay())
    }

    @Test fun `days round-trip through the mask`() {
        val days = setOf(DayOfWeek.MONDAY, DayOfWeek.WEDNESDAY, DayOfWeek.SUNDAY)
        assertEquals(0b100_0101, ReminderDays.toMask(days))
        assertEquals(days, ReminderDays.fromMask(ReminderDays.toMask(days)))
        assertEquals(ReminderDays.EVERY_DAY_MASK, ReminderDays.toMask(ReminderDays.EVERY_DAY))
        assertEquals(ReminderDays.EVERY_DAY, ReminderDays.fromMask(0))
    }

    @Test fun `usual sets of days have names`() {
        assertEquals(ReminderDays.Kind.EveryDay, ReminderDays.kind(ReminderDays.EVERY_DAY))
        assertEquals(ReminderDays.Kind.Workdays, ReminderDays.kind(ReminderDays.WORKDAYS))
        assertEquals(ReminderDays.Kind.Weekend, ReminderDays.kind(ReminderDays.WEEKEND))
        assertEquals(ReminderDays.Kind.Custom, ReminderDays.kind(setOf(DayOfWeek.MONDAY)))
    }
}
