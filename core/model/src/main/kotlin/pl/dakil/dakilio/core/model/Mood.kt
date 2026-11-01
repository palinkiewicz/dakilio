package pl.dakil.dakilio.core.model

/** The scale always has this many levels: 1 = worst … 5 = best. */
const val MOOD_LEVELS = 5

/**
 * A mood the user can pick. Every [level] has exactly one [isPrimary] mood (renamable, never removed)
 * and any number of aliases: other names for the same level ("Sad" next to "Bad"). Analytics only
 * look at the level, through [value]; entries keep the exact mood so they show the chosen name.
 */
data class Mood(
    val id: Long,
    val level: Int,
    val label: String,
    val isPrimary: Boolean,
    /** Order among the level's aliases (the primary always comes first). */
    val position: Int,
) {
    /** [level] normalized to 0.0–1.0, for averages and the heatmap. */
    val value: Float get() = (level - 1f) / (MOOD_LEVELS - 1)
}

/** The level's moods in display order: primary first, then aliases by position. */
val MoodDisplayOrder: Comparator<Mood> = compareBy<Mood> { it.level }.thenByDescending { it.isPrimary }.thenBy { it.position }

/** One primary per level, worst first. */
fun List<Mood>.primaries(): List<Mood> = filter { it.isPrimary }.sortedBy { it.level }

fun List<Mood>.primaryOf(level: Int): Mood? = firstOrNull { it.isPrimary && it.level == level }

/** Every level's moods, primary first, then aliases. Levels without moods are absent. */
fun List<Mood>.byLevel(): Map<Int, List<Mood>> = sortedWith(MoodDisplayOrder).groupBy { it.level }
