package pl.dakil.dakilio.feature.insights.impl

import java.time.DayOfWeek
import java.time.LocalDate
import pl.dakil.dakilio.core.model.Post
import pl.dakil.dakilio.core.model.Tag

/**
 * A pattern worth saying out loud ("Entries with Hiking tend to feel brighter"). Echoes are
 * observations about the selected period, never advice, and only show once there's enough to go on.
 */
sealed interface Echo {
    /** Entries with [tag] average [delta] (0..1) above the rest. */
    data class TagLift(val tag: Tag, val delta: Float) : Echo

    /** Entries with [tag] average [delta] (0..1, positive) below the rest. */
    data class TagDip(val tag: Tag, val delta: Float) : Echo

    /** The weekday whose entries feel brightest. */
    data class BrightWeekday(val day: DayOfWeek) : Echo

    /** The weekday whose entries feel heaviest. */
    data class HeavyWeekday(val day: DayOfWeek) : Echo

    /** The part of the day whose entries feel brightest. */
    data class BrightDayPart(val part: DayPart) : Echo

    /** The weekday most entries are written on. */
    data class BusyWeekday(val day: DayOfWeek) : Echo

    /** The part of the day most entries are written in. */
    data class BusyDayPart(val part: DayPart) : Echo

    /** The later half of the period feels [delta] (0..1) brighter than the earlier half. */
    data class MoodRising(val delta: Float) : Echo

    /** The later half of the period feels [delta] (0..1, positive) heavier than the earlier half. */
    data class MoodFalling(val delta: Float) : Echo
}

/** Fewer entries than this in the period say nothing yet. */
internal const val MIN_ECHO_ENTRIES = 8

/** Entries a tag, weekday, part of the day or half of the period needs before it's compared. */
private const val MIN_GROUP_ENTRIES = 4

/** How far apart two averages must be (0..1 scale) to be worth a sentence: about a third of a level. */
private const val MIN_ECHO_DELTA = 0.08f

/** Share of all entries a weekday needs to be "when you write" (an even spread is 1/7). */
private const val BUSY_WEEKDAY_SHARE = 0.25f

/** Share of all entries a part of the day needs to be "when you write" (an even spread is 1/4). */
private const val BUSY_DAY_PART_SHARE = 0.5f

private const val MAX_ECHOES = 4

/**
 * The echoes of [posts] within [range], most telling first, at most four. Empty until the period
 * holds [MIN_ECHO_ENTRIES] entries.
 */
fun echoes(posts: List<Post>, range: InsightsRange, today: LocalDate): List<Echo> {
    val shown = posts.inRange(range, today)
    if (shown.size < MIN_ECHO_ENTRIES) return emptyList()
    val influences = allTagInfluences(shown).filter { it.entries >= MIN_GROUP_ENTRIES }
    val lift = influences.filter { it.delta >= MIN_ECHO_DELTA }.maxWithOrNull(compareBy<TagInfluence> { it.delta }.thenBy { it.entries })
    val dip = influences.filter { it.delta <= -MIN_ECHO_DELTA }.minWithOrNull(compareBy<TagInfluence> { it.delta }.thenByDescending { it.entries })

    // Only entries with a mood say how a weekday or a part of the day feels.
    val withMood = shown.filter { it.mood != null }
    val weekdays = weekdayMoods(withMood).filter { it.entries >= MIN_GROUP_ENTRIES }.map { it.day to it.averageMood!! }
    val brightDay = standout(weekdays, brightest = true)
    val heavyDay = standout(weekdays, brightest = false)
    val parts = dayPartMoods(withMood).filter { it.entries >= MIN_GROUP_ENTRIES }.map { it.part to it.averageMood!! }
    val brightPart = standout(parts, brightest = true, minGroups = 2)

    val busyDay = weekdayMoods(shown).maxByOrNull { it.entries }
        ?.takeIf { it.entries >= MIN_GROUP_ENTRIES && it.entries >= shown.size * BUSY_WEEKDAY_SHARE }
    val busyPart = dayPartMoods(shown).maxByOrNull { it.entries }?.takeIf { it.entries >= shown.size * BUSY_DAY_PART_SHARE }

    return listOfNotNull(
        lift?.let { Echo.TagLift(it.tag, it.delta) },
        brightDay?.let(Echo::BrightWeekday),
        trendEcho(shown, range, today),
        brightPart?.let(Echo::BrightDayPart),
        dip?.let { Echo.TagDip(it.tag, -it.delta) },
        heavyDay?.let(Echo::HeavyWeekday),
        busyDay?.let { Echo.BusyWeekday(it.day) },
        busyPart?.let { Echo.BusyDayPart(it.part) },
    ).take(MAX_ECHOES)
}

/**
 * The group that stands out from the others' average by [MIN_ECHO_DELTA], on the bright or the
 * heavy side; null when fewer than [minGroups] groups qualify or none stands out.
 */
private fun <T> standout(groups: List<Pair<T, Float>>, brightest: Boolean, minGroups: Int = 3): T? {
    if (groups.size < minGroups) return null
    val pick = (if (brightest) groups.maxByOrNull { it.second } else groups.minByOrNull { it.second }) ?: return null
    val others = groups.filter { it !== pick }.map { it.second }.average().toFloat()
    val delta = if (brightest) pick.second - others else others - pick.second
    return pick.first.takeIf { delta >= MIN_ECHO_DELTA }
}

/** Compares the two halves of the period (of a bounded range, or of the time since the first entry). */
private fun trendEcho(shown: List<Post>, range: InsightsRange, today: LocalDate): Echo? {
    val from = range.days?.let { today.minusDays(it - 1) } ?: shown.minOfOrNull { it.date } ?: return null
    val middle = from.plusDays((today.toEpochDay() - from.toEpochDay() + 1) / 2)
    val (later, earlier) = shown.filter { it.mood != null }.partition { !it.date.isBefore(middle) }
    if (earlier.size < MIN_GROUP_ENTRIES || later.size < MIN_GROUP_ENTRIES) return null
    val delta = later.map { it.mood!!.value }.average().toFloat() - earlier.map { it.mood!!.value }.average().toFloat()
    return when {
        delta >= MIN_ECHO_DELTA -> Echo.MoodRising(delta)
        delta <= -MIN_ECHO_DELTA -> Echo.MoodFalling(-delta)
        else -> null
    }
}
