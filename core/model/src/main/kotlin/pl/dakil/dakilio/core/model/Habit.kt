package pl.dakil.dakilio.core.model

import java.time.DayOfWeek
import java.time.LocalDate
import java.time.temporal.TemporalAdjusters

/**
 * A habit (the Habits extra): a tag the user wants on their entries on [daysPerWeek] days a week.
 * Entries of every diary count. Its name is the tag's label.
 */
data class Habit(
    val id: Long,
    val tag: Tag,
    val daysPerWeek: Int,
    val position: Int,
)

/**
 * A habit with its progress.
 *
 * @property thisWeekDays days of the current week with the tag.
 * @property lastWeekCount days of the previous week with the tag.
 */
data class HabitStatus(
    val habit: Habit,
    val thisWeekDays: Set<LocalDate>,
    val lastWeekCount: Int,
) {
    val thisWeekCount: Int get() = thisWeekDays.size
}

/**
 * A habit that just gained a day, for the progress popup.
 *
 * @property before days of [weekStart]'s week with the tag before the entry was saved.
 * @property after the same, after it was saved.
 */
data class HabitBump(
    val habit: Habit,
    val before: Int,
    val after: Int,
    val weekStart: LocalDate,
)

object Habits {
    /** Targets a habit can have, in days per week. */
    val DAYS_RANGE: IntRange = 1..7

    const val DEFAULT_DAYS: Int = 3

    /** The first day of [date]'s week, for weeks starting on [firstDay] (the user's setting). */
    fun weekStart(date: LocalDate, firstDay: DayOfWeek): LocalDate = date.with(TemporalAdjusters.previousOrSame(firstDay))

    /**
     * The habits whose week count went up between [before] and [after] (counts by habit id, for the
     * week starting [weekStart]). A day already counted doesn't count again, so it never bumps twice.
     */
    fun bumps(habits: List<Habit>, before: Map<Long, Int>, after: Map<Long, Int>, weekStart: LocalDate): List<HabitBump> =
        habits.mapNotNull { habit ->
            val was = before[habit.id] ?: 0
            val now = after[habit.id] ?: 0
            if (now > was) HabitBump(habit, was, now, weekStart) else null
        }

    /**
     * The habit's weeks that touch [range], oldest first, each with the days that carry its tag
     * ([days], every diary). Weeks before the tag's first use are left out, since the habit can't
     * have been kept before it existed. Today's target applies to every week.
     */
    fun weeks(days: Set<LocalDate>, target: Int, range: DateRange, firstDay: DayOfWeek, today: LocalDate): List<HabitWeek> {
        val firstUse = days.minOrNull() ?: return emptyList()
        val start = weekStart(maxOf(range.from, firstUse), firstDay)
        val end = minOf(range.to, today)
        if (end.isBefore(start)) return emptyList()
        return generateSequence(start) { it.plusWeeks(1) }
            .takeWhile { !it.isAfter(end) }
            .map { week ->
                val count = (0L..6L).count { week.plusDays(it) in days }
                HabitWeek(week, count, target, inProgress = !week.plusDays(6).isBefore(today))
            }
            .toList()
    }

    /**
     * Weeks on target in a row, ending with the newest finished week. The week in progress adds to
     * the run once it's on target, and never breaks it while it isn't yet.
     */
    fun currentRun(weeks: List<HabitWeek>): Int {
        var run = 0
        for (week in weeks.asReversed()) {
            when {
                week.met -> run++
                week.inProgress -> continue
                else -> break
            }
        }
        return run
    }

    /** The most weeks on target in a row. */
    fun longestRun(weeks: List<HabitWeek>): Int {
        var best = 0
        var run = 0
        for (week in weeks) {
            if (week.met) {
                run++
                best = maxOf(best, run)
            } else if (!week.inProgress) {
                run = 0
            }
        }
        return best
    }
}

/**
 * One week of a habit.
 *
 * @property days days of the week with the habit's tag.
 * @property inProgress the current week, which can still reach its target.
 */
data class HabitWeek(val start: LocalDate, val days: Int, val target: Int, val inProgress: Boolean) {
    val met: Boolean get() = days >= target

    /** A finished week that fell short; the week in progress never counts as missed. */
    val missed: Boolean get() = !met && !inProgress
}
