package pl.dakil.dakilio.core.model

import org.junit.Assert.assertEquals
import org.junit.Test

class MoodTest {
    private val bad = Mood(2, 2, "Bad", isPrimary = true, position = 0)
    private val sad = Mood(7, 2, "Sad", isPrimary = false, position = 1)
    private val low = Mood(8, 2, "Low", isPrimary = false, position = 0)
    private val great = Mood(5, 5, "Great", isPrimary = true, position = 0)
    private val awful = Mood(1, 1, "Awful", isPrimary = true, position = 0)

    @Test
    fun `value spans the fixed scale`() {
        assertEquals(0f, awful.value)
        assertEquals(0.25f, sad.value)
        assertEquals(1f, great.value)
    }

    @Test
    fun `primaries are one per level, worst first`() {
        assertEquals(listOf(awful, bad, great), listOf(great, sad, bad, awful).primaries())
        assertEquals(bad, listOf(sad, bad).primaryOf(2))
    }

    @Test
    fun `byLevel puts the primary first, then aliases by position`() {
        val levels = listOf(sad, great, low, bad).byLevel()
        assertEquals(listOf(bad, low, sad), levels[2])
        assertEquals(listOf(great), levels[5])
    }
}
