package pl.dakil.dakilio.feature.insights.impl.logic

import java.time.DayOfWeek
import java.time.LocalDate
import pl.dakil.dakilio.core.model.DateRange
import pl.dakil.dakilio.core.model.Habit
import pl.dakil.dakilio.core.model.HabitWeek
import pl.dakil.dakilio.core.model.Habits
import pl.dakil.dakilio.core.model.Post

/** Mood is compared between weeks on and off target only with at least this many finished weeks of each. */
internal const val MIN_HABIT_MOOD_WEEKS = 3

/**
 * A habit in a period. Entries of every diary count.
 *
 * @property weeks the period's weeks since the tag was first used.
 * @property days days with the tag in the period.
 * @property metBefore weeks on target in the comparison period, `null` without one.
 * @property moodOnTarget, moodOffTarget the average mood of weeks on and off target, `null` with
 *   too few weeks of either, or too small a difference, to say.
 */
data class HabitStat(
    val habit: Habit,
    val weeks: List<HabitWeek>,
    val days: Int,
    val currentRun: Int,
    val longestRun: Int,
    val metBefore: Int? = null,
    val moodOnTarget: Float? = null,
    val moodOffTarget: Float? = null,
) {
    val met: Int get() = weeks.count { it.met }

    /** Finished weeks, plus the week in progress once it's on target: what [met] is out of. */
    val counted: Int get() = weeks.count { !it.inProgress || it.met }

    val inProgress: HabitWeek? get() = weeks.lastOrNull()?.takeIf { it.inProgress }
}

/** The days each of [habits]' tags is on, across every diary. */
fun habitDays(posts: List<Post>, habits: List<Habit>): Map<Long, Set<LocalDate>> {
    val tagIds = habits.mapTo(HashSet()) { it.tag.id }
    val days = HashMap<Long, MutableSet<LocalDate>>()
    for (post in posts) {
        for (tag in post.tags) if (tag.id in tagIds) days.getOrPut(tag.id) { HashSet() } += post.date
    }
    return days
}

/**
 * @param posts every entry of every diary.
 * @param comparison the comparison period's days, for [HabitStat.metBefore].
 */
fun habitStats(
    habits: List<Habit>,
    posts: List<Post>,
    range: DateRange,
    comparison: DateRange?,
    firstDay: DayOfWeek,
    today: LocalDate,
): List<HabitStat> {
    val days = habitDays(posts, habits)
    val moodByWeek = posts.filter { it.mood != null && it.date in range }
        .groupBy { Habits.weekStart(it.date, firstDay) }
        .mapValues { (_, weekPosts) -> weekPosts.map { it.mood!!.level }.average().toFloat() }
    return habits.map { habit ->
        val tagDays = days[habit.tag.id].orEmpty()
        val weeks = Habits.weeks(tagDays, habit.daysPerWeek, range, firstDay, today)
        val on = weeks.filter { it.met && !it.inProgress }.mapNotNull { moodByWeek[it.start] }
        val off = weeks.filter { it.missed }.mapNotNull { moodByWeek[it.start] }
        val onAverage = on.average().toFloat()
        val offAverage = off.average().toFloat()
        // Worth telling only with enough weeks of each, and a difference that isn't noise.
        val enough = on.size >= MIN_HABIT_MOOD_WEEKS && off.size >= MIN_HABIT_MOOD_WEEKS && kotlin.math.abs(onAverage - offAverage) >= NOTABLE_LIFT
        HabitStat(
            habit = habit,
            weeks = weeks,
            days = tagDays.count { it in range },
            currentRun = Habits.currentRun(weeks),
            longestRun = Habits.longestRun(weeks),
            metBefore = comparison?.let { Habits.weeks(tagDays, habit.daysPerWeek, it, firstDay, today).count { week -> week.met } },
            moodOnTarget = if (enough) onAverage else null,
            moodOffTarget = if (enough) offAverage else null,
        )
    }
}
