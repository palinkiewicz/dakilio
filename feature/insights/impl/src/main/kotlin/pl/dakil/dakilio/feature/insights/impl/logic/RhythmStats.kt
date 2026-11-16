package pl.dakil.dakilio.feature.insights.impl.logic

import java.time.DayOfWeek
import pl.dakil.dakilio.core.model.Post

/** The four parts of a day entries are sorted into, by the hour they were written. */
enum class DayPart {
    /** 5:00–11:59. */
    Morning,

    /** 12:00–16:59. */
    Afternoon,

    /** 17:00–21:59. */
    Evening,

    /** 22:00–4:59, across midnight. */
    Night,
    ;

    companion object {
        fun of(hour: Int): DayPart = when (hour) {
            in 5..11 -> Morning
            in 12..16 -> Afternoon
            in 17..21 -> Evening
            else -> Night
        }
    }
}

/** Entries in a slot (a weekday, a part of the day, or both) and their average level. */
data class SlotStat(val entries: Int = 0, val average: Float? = null, val moodEntries: Int = 0)

/** A slot's average is trusted from this many entries with a mood. */
internal const val MIN_SLOT_MOODS = 3

/**
 * When entries are written and how they feel.
 *
 * @property weekdays every weekday, Monday first; order them for display with the first day of the week.
 * @property matrix every weekday × part-of-day slot.
 * @property hours entries per hour of the day, 24 values.
 */
data class Rhythm(
    val weekdays: Map<DayOfWeek, SlotStat>,
    val dayParts: Map<DayPart, SlotStat>,
    val matrix: Map<Pair<DayOfWeek, DayPart>, SlotStat>,
    val hours: List<Int>,
) {
    /** The weekday with the highest average among those with enough moods; `null` when fewer than two qualify. */
    val bestWeekday: DayOfWeek? get() = best(weekdays)

    val bestDayPart: DayPart? get() = best(dayParts)

    /** The part of the day most entries are written in. */
    val busiestDayPart: DayPart? get() = dayParts.filterValues { it.entries > 0 }.maxByOrNull { it.value.entries }?.key

    private fun <K> best(slots: Map<K, SlotStat>): K? {
        val trusted = slots.filterValues { it.moodEntries >= MIN_SLOT_MOODS && it.average != null }
        if (trusted.size < 2) return null
        val top = trusted.maxByOrNull { it.value.average!! } ?: return null
        // A tie for first place isn't a "best".
        return top.key.takeIf { trusted.values.count { it.average == top.value.average } == 1 }
    }
}

fun rhythm(posts: List<Post>): Rhythm {
    fun slot(group: List<Post>): SlotStat {
        val levels = group.mapNotNull { it.mood?.level }
        return SlotStat(group.size, levels.averageOrNull(), levels.size)
    }
    val byWeekday = posts.groupBy { it.dateTime.dayOfWeek }
    val byPart = posts.groupBy { DayPart.of(it.dateTime.hour) }
    val byBoth = posts.groupBy { it.dateTime.dayOfWeek to DayPart.of(it.dateTime.hour) }
    val hours = IntArray(24)
    posts.forEach { hours[it.dateTime.hour]++ }
    return Rhythm(
        weekdays = DayOfWeek.entries.associateWith { slot(byWeekday[it].orEmpty()) },
        dayParts = DayPart.entries.associateWith { slot(byPart[it].orEmpty()) },
        matrix = DayOfWeek.entries.flatMap { day -> DayPart.entries.map { part -> day to part } }.associateWith { slot(byBoth[it].orEmpty()) },
        hours = hours.toList(),
    )
}
