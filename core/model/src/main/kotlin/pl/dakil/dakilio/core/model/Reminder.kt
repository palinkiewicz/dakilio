package pl.dakil.dakilio.core.model

import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime

/**
 * A daily nudge at [time], for a diary ("write something") or a habit ("keep it going"). Off by
 * default; the time is kept while it's off, so turning it back on brings the old time back.
 */
data class Reminder(
    val enabled: Boolean = false,
    val time: LocalTime = DEFAULT_TIME,
) {
    val minuteOfDay: Int get() = time.hour * 60 + time.minute

    companion object {
        val DEFAULT_TIME: LocalTime = LocalTime.of(20, 0)

        fun ofMinute(enabled: Boolean, minuteOfDay: Int): Reminder =
            Reminder(enabled, LocalTime.of((minuteOfDay / 60).coerceIn(0, 23), (minuteOfDay % 60).coerceIn(0, 59)))
    }
}

/** A diary whose reminder is on. */
data class DiaryReminder(val diaryId: Long, val name: String, val time: LocalTime)

/** A habit whose reminder is on. Its name is its tag's label. */
data class HabitReminder(val habitId: Long, val tagId: Long, val label: String, val daysPerWeek: Int, val time: LocalTime)

/**
 * Everything the planner needs, read for [today].
 *
 * @property writtenToday diaries with an entry on [today].
 * @property weekCounts days with each habit's tag in [today]'s week, by habit id; habits at 0 are absent.
 */
data class ReminderSnapshot(
    val today: LocalDate,
    val firstDay: DayOfWeek,
    val diaries: List<DiaryReminder>,
    val habits: List<HabitReminder>,
    val writtenToday: Set<Long>,
    val weekCounts: Map<Long, Int>,
) {
    val isEmpty: Boolean get() = diaries.isEmpty() && habits.isEmpty()

    fun weekCount(habit: HabitReminder): Int = weekCounts[habit.habitId] ?: 0

    /** The habit has met its target for [today]'s week, so it rests until the next one. */
    fun isMet(habit: HabitReminder): Boolean = weekCount(habit) >= habit.daysPerWeek
}

/** The next alarm: the reminders due at [at] (all of them share the time). */
data class NextAlarm(val at: LocalDateTime, val diaryIds: Set<Long>, val habitIds: Set<Long>)

/** The reminders to show now, when an alarm set for some time today goes off. */
data class DueReminders(val diaries: List<DiaryReminder>, val habits: List<HabitReminder>) {
    val isEmpty: Boolean get() = diaries.isEmpty() && habits.isEmpty()
}

/**
 * The reminder schedule. One alarm at a time: the earliest reminder still to come. When it goes off,
 * the reminders due then are checked again against fresh data, shown, and the next alarm is planned.
 *
 * - A diary reminder is skipped on a day that diary already has an entry.
 * - A habit reminder comes every day until the week's target is met, then rests until the next week
 *   (starting on the user's first day of the week).
 */
object Reminders {
    /**
     * The earliest reminder strictly after [after], with every other reminder due at the same moment;
     * `null` when none is on. [after] must not be before [ReminderSnapshot.today]: the snapshot only
     * knows about today's entries and this week's counts.
     */
    fun next(after: LocalDateTime, snapshot: ReminderSnapshot): NextAlarm? {
        val weekStart = Habits.weekStart(snapshot.today, snapshot.firstDay)
        val nextWeek = weekStart.plusWeeks(1)

        val diaryTimes = snapshot.diaries.associate { reminder ->
            var at = firstAfter(after, reminder.time)
            if (at.toLocalDate() == snapshot.today && reminder.diaryId in snapshot.writtenToday) at = at.plusDays(1)
            reminder.diaryId to at
        }
        val habitTimes = snapshot.habits.associate { reminder ->
            var at = firstAfter(after, reminder.time)
            // The counts are this week's; a later week starts from zero.
            if (at.toLocalDate() < nextWeek && snapshot.isMet(reminder)) at = nextWeek.atTime(reminder.time)
            reminder.habitId to at
        }
        val earliest = (diaryTimes.values + habitTimes.values).minOrNull() ?: return null
        return NextAlarm(
            at = earliest,
            diaryIds = diaryTimes.filterValues { it == earliest }.keys,
            habitIds = habitTimes.filterValues { it == earliest }.keys,
        )
    }

    /**
     * The reminders due at [at] that still apply: still on at that time, [at] is today, and the diary
     * has no entry today or the habit hasn't met its target this week.
     */
    fun due(at: LocalDateTime, snapshot: ReminderSnapshot): DueReminders {
        if (at.toLocalDate() != snapshot.today) return DueReminders(emptyList(), emptyList())
        val time = at.toLocalTime()
        return DueReminders(
            diaries = snapshot.diaries.filter { it.time == time && it.diaryId !in snapshot.writtenToday },
            habits = snapshot.habits.filter { it.time == time && !snapshot.isMet(it) },
        )
    }

    /**
     * Which of [size] texts a reminder shows on [date]: it walks through all of them day by day, and
     * different diaries or habits ([id]) start at different places.
     */
    fun textIndex(size: Int, date: LocalDate, id: Long): Int =
        if (size <= 0) 0 else Math.floorMod(date.toEpochDay() + id * 3, size.toLong()).toInt()

    private fun firstAfter(after: LocalDateTime, time: LocalTime): LocalDateTime {
        val sameDay = after.toLocalDate().atTime(time)
        return if (sameDay > after) sameDay else sameDay.plusDays(1)
    }
}
