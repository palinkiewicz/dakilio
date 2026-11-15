package pl.dakil.dakilio.core.model

import kotlin.random.Random

/**
 * A writing prompt (the Prompts extra): a question the note step offers while the note is still
 * empty, to get past the blank page.
 *
 * @property levels the mood levels the prompt suits; all five means any mood, and no mood at all.
 */
data class Prompt(
    val id: Long,
    val text: String,
    val levels: Set<Int> = Prompts.ALL_LEVELS,
    val position: Int = 0,
) {
    val anyMood: Boolean get() = levels.size == MOOD_LEVELS
}

/** What the prompt editor saves: the question and the mood levels it suits (empty or all five = any mood). */
data class PromptDraft(val text: String, val levels: Set<Int> = Prompts.ALL_LEVELS)

object Prompts {
    val ALL_LEVELS: Set<Int> = (1..MOOD_LEVELS).toSet()

    /** Long enough for a real question, short enough to stay one. */
    const val MAX_LENGTH = 160

    /** A bit per level, level 1 lowest. */
    fun toMask(levels: Set<Int>): Int = levels.filter { it in ALL_LEVELS }.fold(0) { mask, level -> mask or (1 shl (level - 1)) }

    /** The levels of [mask]; a mask without any known level means every level. */
    fun fromMask(mask: Int): Set<Int> = ALL_LEVELS.filter { mask and (1 shl (it - 1)) != 0 }.toSet().ifEmpty { ALL_LEVELS }

    /** [text] as it's stored: one line, single spaces, at most [MAX_LENGTH] characters. */
    fun clean(text: String): String = text.trim().replace(WHITESPACE, " ").take(MAX_LENGTH).trim()

    /** The same question, whatever its case or spacing. */
    fun key(text: String): String = clean(text).lowercase()

    /**
     * The prompts that suit an entry with a mood of [level]. Without a mood only the any-mood
     * prompts do: a question for a heavy day shouldn't greet an entry that says nothing about the day.
     */
    fun matching(prompts: List<Prompt>, level: Int?): List<Prompt> =
        prompts.filter { if (level == null) it.anyMood else level in it.levels }

    /**
     * The prompt to offer on the [turn]-th look (0 first, one more for every "another one"). Prompts
     * written for this mood come before the any-mood ones, each lot in an order fixed by [seed] (one
     * per editor session), and the turns wrap around. Null when nothing matches.
     */
    fun pick(prompts: List<Prompt>, level: Int?, seed: Int, turn: Int): Prompt? {
        val (any, specific) = matching(prompts, level).sortedBy { it.id }.partition { it.anyMood }
        val order = specific.shuffled(Random(seed)) + any.shuffled(Random(seed))
        return if (order.isEmpty()) null else order[Math.floorMod(turn, order.size)]
    }

    private val WHITESPACE = Regex("\\s+")
}
