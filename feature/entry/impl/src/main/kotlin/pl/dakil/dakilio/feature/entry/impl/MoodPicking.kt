package pl.dakil.dakilio.feature.entry.impl

import pl.dakil.dakilio.core.model.Mood
import pl.dakil.dakilio.core.model.MoodDisplayOrder

/** What a tap on a level's face means, given what is selected now. */
internal sealed interface MoodTap {
    /** Pick the level's primary (first tap on a level). */
    data class Select(val id: Long) : MoodTap

    /** The level is already selected and has aliases: show them. */
    data object OpenAliases : MoodTap

    /** The level is already selected and has no aliases: tapping again clears the mood. */
    data object Clear : MoodTap
}

/**
 * @param levelMoods one level's moods, primary first (see [MoodDisplayOrder]).
 * @param selected the entry's current mood, if any.
 */
internal fun moodTap(levelMoods: List<Mood>, selected: Mood?): MoodTap {
    val primary = levelMoods.first()
    return when {
        selected == null || selected.level != primary.level -> MoodTap.Select(primary.id)
        levelMoods.size > 1 -> MoodTap.OpenAliases
        else -> MoodTap.Clear
    }
}
