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
    /**
     * The weekday with the highest average. It's only called the best when it has enough moods, at
     * least one other weekday has enough to compare with, and no weekday (trusted or not) is level
     * with it or above: so it's always the tallest column of the chart, or nothing.
     */
    val bestWeekday: DayOfWeek? get() = best(weekdays)

    val bestDayPart: DayPart? get() = best(dayParts)

    /** The weekday × part-of-day slot with the highest average among those with enough moods; `null` on a tie or with fewer than two. */
    val brightestSlot: Pair<DayOfWeek, DayPart>? get() = extreme(1)

    /** The same for the lowest average. */
    val heaviestSlot: Pair<DayOfWeek, DayPart>? get() = extreme(-1)

    /** The slot with the most entries; `null` on a tie or without entries. */
    val busiestSlot: Pair<DayOfWeek, DayPart>?
        get() {
            val top = matrix.maxByOrNull { it.value.entries }?.takeIf { it.value.entries > 0 } ?: return null
            return top.key.takeIf { matrix.values.count { it.entries == top.value.entries } == 1 }
        }

    private fun extreme(sign: Int): Pair<DayOfWeek, DayPart>? {
        val trusted = matrix.filterValues { it.moodEntries >= MIN_SLOT_MOODS && it.average != null }
        if (trusted.size < 2) return null
        val top = trusted.maxByOrNull { sign * it.value.average!! } ?: return null
        return top.key.takeIf { trusted.values.count { it.average == top.value.average } == 1 }
    }

    /** The part of the day most entries are written in. */
    val busiestDayPart: DayPart? get() = dayParts.filterValues { it.entries > 0 }.maxByOrNull { it.value.entries }?.key

    private fun <K> best(slots: Map<K, SlotStat>): K? {
        val trusted = slots.filterValues { it.moodEntries >= MIN_SLOT_MOODS && it.average != null }
        if (trusted.size < 2) return null
        val top = trusted.maxByOrNull { it.value.average!! } ?: return null
        val rivals = slots.count { (key, slot) -> key != top.key && slot.average != null && slot.average >= top.value.average!! }
        return top.key.takeIf { rivals == 0 }
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
