package pl.dakil.dakilio.core.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PromptsTest {
    private val heavy = Prompt(1, "What felt heaviest?", setOf(1, 2))
    private val heavy2 = Prompt(2, "What would help?", setOf(1, 2))
    private val bright = Prompt(3, "What made today good?", setOf(4, 5))
    private val any = Prompt(4, "What do you want to remember?")
    private val any2 = Prompt(5, "What surprised you?")
    private val all = listOf(heavy, heavy2, bright, any, any2)

    @Test
    fun `levels round trip through the mask`() {
        assertEquals(0b00011, Prompts.toMask(setOf(1, 2)))
        assertEquals(0b11111, Prompts.toMask(Prompts.ALL_LEVELS))
        assertEquals(setOf(4, 5), Prompts.fromMask(0b11000))
        assertEquals(setOf(1, 3), Prompts.fromMask(Prompts.toMask(setOf(1, 3, 9))))
        // Nothing known in the mask: any mood rather than a prompt that never shows.
        assertEquals(Prompts.ALL_LEVELS, Prompts.fromMask(0))
        assertEquals(Prompts.ALL_LEVELS, Prompts.fromMask(0b100000))
    }

    @Test
    fun `text is stored as one tidy line`() {
        assertEquals("What went well today?", Prompts.clean("  What went\n well   today? "))
        assertEquals(Prompts.MAX_LENGTH, Prompts.clean("x".repeat(500)).length)
        assertEquals("", Prompts.clean(" \n "))
        assertEquals(Prompts.key("What Went well?"), Prompts.key(" what  went WELL? "))
    }

    @Test
    fun `a mood gets its own prompts and the any-mood ones`() {
        assertEquals(listOf(heavy, heavy2, any, any2), Prompts.matching(all, 1))
        assertEquals(listOf(any, any2), Prompts.matching(all, 3))
        assertEquals(listOf(bright, any, any2), Prompts.matching(all, 5))
    }

    @Test
    fun `no mood gets only the any-mood prompts`() {
        assertEquals(listOf(any, any2), Prompts.matching(all, null))
        assertTrue(Prompts.matching(listOf(heavy, bright), null).isEmpty())
    }

    @Test
    fun `prompts for the mood come first, and turns wrap around`() {
        val picks = (0..3).map { Prompts.pick(all, 2, seed = 42, turn = it) }
        assertEquals(setOf(heavy, heavy2), picks.take(2).toSet())
        assertEquals(setOf(any, any2), picks.drop(2).toSet())
        assertEquals(picks[0], Prompts.pick(all, 2, seed = 42, turn = 4))
        assertEquals(picks[3], Prompts.pick(all, 2, seed = 42, turn = -1))
    }

    @Test
    fun `a session keeps its order`() {
        val first = (0..4).map { Prompts.pick(all, 1, seed = 7, turn = it) }
        val again = (0..4).map { Prompts.pick(all.reversed(), 1, seed = 7, turn = it) }
        assertEquals(first, again)
    }

    @Test
    fun `nothing to offer is null`() {
        assertNull(Prompts.pick(emptyList(), 3, seed = 1, turn = 0))
        assertNull(Prompts.pick(listOf(heavy), 5, seed = 1, turn = 0))
        assertNull(Prompts.pick(listOf(bright), null, seed = 1, turn = 0))
    }
}
