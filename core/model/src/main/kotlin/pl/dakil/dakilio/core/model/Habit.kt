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

    /** Weeks start on Monday, like the Insights heatmap. */
    fun weekStart(date: LocalDate): LocalDate = date.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))

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
}
