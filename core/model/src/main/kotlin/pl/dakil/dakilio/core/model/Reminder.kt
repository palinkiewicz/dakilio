package pl.dakil.dakilio.core.model

import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime

/** What a reminder is about: writing in a diary, or keeping a habit going. */
sealed interface ReminderTarget {
    val id: Long

    data class Diary(override val id: Long) : ReminderTarget

    data class Habit(override val id: Long) : ReminderTarget
}

/**
 * A reminder (the Reminders extra): a nudge at [time] on each of [days], about a diary ("write
 * something") or a habit ("keep it going"). A diary or habit can have any number of them.
 *
 * @property enabled switched off, it stays in the list but never comes.
 */
data class Reminder(
    val id: Long = 0,
    val target: ReminderTarget,
    val time: LocalTime = DEFAULT_TIME,
    val days: Set<DayOfWeek> = ReminderDays.EVERY_DAY,
    val enabled: Boolean = true,
) {
    val minuteOfDay: Int get() = time.minuteOfDay()

    companion object {
        val DEFAULT_TIME: LocalTime = LocalTime.of(20, 0)

        /** [minuteOfDay] minutes after midnight, clamped to the day. */
        fun timeOf(minuteOfDay: Int): LocalTime =
            LocalTime.of((minuteOfDay / 60).coerceIn(0, 23), (minuteOfDay % 60).coerceIn(0, 59))
    }
}

fun LocalTime.minuteOfDay(): Int = hour * 60 + minute

/** The weekdays a reminder comes on, and how they're stored (a bit per day, Monday first). */
object ReminderDays {
    val EVERY_DAY: Set<DayOfWeek> = DayOfWeek.entries.toSet()
    val WORKDAYS: Set<DayOfWeek> = setOf(DayOfWeek.MONDAY, DayOfWeek.TUESDAY, DayOfWeek.WEDNESDAY, DayOfWeek.THURSDAY, DayOfWeek.FRIDAY)
    val WEEKEND: Set<DayOfWeek> = setOf(DayOfWeek.SATURDAY, DayOfWeek.SUNDAY)

    /** Every day: all seven bits. */
    const val EVERY_DAY_MASK = 0b111_1111

    fun toMask(days: Set<DayOfWeek>): Int = days.fold(0) { mask, day -> mask or (1 shl (day.value - 1)) }

    /** The days in [mask]; a mask with no day (never stored) reads as every day. */
    fun fromMask(mask: Int): Set<DayOfWeek> =
        DayOfWeek.entries.filter { mask and (1 shl (it.value - 1)) != 0 }.toSet().ifEmpty { EVERY_DAY }

    /** How to name a set of days: one of the usual names, or the days themselves. */
    fun kind(days: Set<DayOfWeek>): Kind = when (days) {
        EVERY_DAY -> Kind.EveryDay
        WORKDAYS -> Kind.Workdays
        WEEKEND -> Kind.Weekend
        else -> Kind.Custom
    }

    enum class Kind { EveryDay, Workdays, Weekend, Custom }
}

/** A reminder's diary or habit, with what the planner and the notification need to know. */
sealed interface ReminderSubject {
    /** The id notifications are kept by: one per diary or habit, however many reminders it has. */
    val id: Long

    data class Diary(val diaryId: Long, val name: String) : ReminderSubject {
        override val id: Long get() = diaryId
    }

    /** A habit; its name is its tag's label. */
    data class Habit(val habitId: Long, val tagId: Long, val label: String, val daysPerWeek: Int) : ReminderSubject {
        override val id: Long get() = habitId
    }
}

/** A reminder that's on, as the schedule sees it. */
data class PlannedReminder(
    val id: Long,
    val subject: ReminderSubject,
    val time: LocalTime,
    val days: Set<DayOfWeek> = ReminderDays.EVERY_DAY,
)

/**
 * Everything the planner needs, read for [today].
 *
 * @property reminders the reminders that are on.
 * @property writtenToday diaries with an entry on [today].
 * @property weekCounts days with each habit's tag in [today]'s week, by habit id; habits at 0 are absent.
 */
data class ReminderSnapshot(
    val today: LocalDate,
    val firstDay: DayOfWeek,
    val reminders: List<PlannedReminder>,
    val writtenToday: Set<Long>,
    val weekCounts: Map<Long, Int>,
) {
    val isEmpty: Boolean get() = reminders.isEmpty()

    fun weekCount(habit: ReminderSubject.Habit): Int = weekCounts[habit.habitId] ?: 0

    /** The habit has met its target for [today]'s week, so it rests until the next one. */
    fun isMet(habit: ReminderSubject.Habit): Boolean = weekCount(habit) >= habit.daysPerWeek

    /** Whether a notification for [subject] still applies today. */
    fun applies(subject: ReminderSubject): Boolean = when (subject) {
        is ReminderSubject.Diary -> subject.diaryId !in writtenToday
        is ReminderSubject.Habit -> !isMet(subject)
    }
}

/** The next alarm: the reminders due at [at] (all of them share the time). */
data class NextAlarm(val at: LocalDateTime, val reminderIds: Set<Long>)

/** What to show now, when an alarm set for some time today goes off: one per diary or habit. */
data class DueReminders(val diaries: List<ReminderSubject.Diary>, val habits: List<ReminderSubject.Habit>) {
    val isEmpty: Boolean get() = diaries.isEmpty() && habits.isEmpty()
}

/**
 * The reminder schedule. One alarm at a time: the earliest reminder still to come. When it goes off,
 * the reminders due then are checked again against fresh data, shown, and the next alarm is planned.
 *
 * - A reminder comes only on its weekdays.
 * - A diary reminder is skipped on a day that diary already has an entry.
 * - A habit reminder comes until the week's target is met, then rests until the next week (starting
 *   on the user's first day of the week).
 */
object Reminders {
    /** Far enough to reach any weekday of next week, from any day of this one. */
    private const val LOOKAHEAD_DAYS = 14L

    /**
     * The earliest reminder strictly after [after], with every other reminder due at the same moment;
     * `null` when none is on. [after] must not be before [ReminderSnapshot.today]: the snapshot only
     * knows about today's entries and this week's counts.
     */
    fun next(after: LocalDateTime, snapshot: ReminderSnapshot): NextAlarm? {
        val nextWeek = Habits.weekStart(snapshot.today, snapshot.firstDay).plusWeeks(1)
        val times = snapshot.reminders.mapNotNull { reminder ->
            val at = (0..LOOKAHEAD_DAYS).asSequence()
                .map { after.toLocalDate().plusDays(it).atTime(reminder.time) }
                .firstOrNull { at -> at > after && at.dayOfWeek in reminder.days && !skipped(reminder.subject, at.toLocalDate(), snapshot, nextWeek) }
            at?.let { reminder.id to it }
        }.toMap()
        val earliest = times.values.minOrNull() ?: return null
        return NextAlarm(earliest, times.filterValues { it == earliest }.keys)
    }

    /**
     * What's due at [at] and still applies: reminders on at that time and weekday, [at] is today, and
     * the diary has no entry today or the habit hasn't met its target this week. A diary or habit with
     * several reminders at that moment shows once.
     */
    fun due(at: LocalDateTime, snapshot: ReminderSnapshot): DueReminders {
        if (at.toLocalDate() != snapshot.today) return DueReminders(emptyList(), emptyList())
        val subjects = snapshot.reminders
            .filter { it.time == at.toLocalTime() && at.dayOfWeek in it.days && snapshot.applies(it.subject) }
            .map { it.subject }
            .distinct()
        return DueReminders(subjects.filterIsInstance<ReminderSubject.Diary>(), subjects.filterIsInstance<ReminderSubject.Habit>())
    }

    /**
     * Which of [size] texts a reminder shows on [date]: it walks through all of them day by day, and
     * different diaries or habits ([id]) start at different places.
     */
    fun textIndex(size: Int, date: LocalDate, id: Long): Int =
        if (size <= 0) 0 else Math.floorMod(date.toEpochDay() + id * 3, size.toLong()).toInt()

    private fun skipped(subject: ReminderSubject, date: LocalDate, snapshot: ReminderSnapshot, nextWeek: LocalDate): Boolean =
        when (subject) {
            is ReminderSubject.Diary -> date == snapshot.today && subject.diaryId in snapshot.writtenToday
            // The counts are this week's; a later week starts from zero.
            is ReminderSubject.Habit -> date < nextWeek && snapshot.isMet(subject)
        }
}
