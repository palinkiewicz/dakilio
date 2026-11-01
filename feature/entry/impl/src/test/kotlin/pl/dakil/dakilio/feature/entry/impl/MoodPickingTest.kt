package pl.dakil.dakilio.feature.entry.impl

import org.junit.Assert.assertEquals
import org.junit.Test
import pl.dakil.dakilio.core.model.Mood

class MoodPickingTest {
    private val bad = Mood(2, 2, "Bad", isPrimary = true, position = 0)
    private val sad = Mood(7, 2, "Sad", isPrimary = false, position = 0)
    private val good = Mood(4, 4, "Good", isPrimary = true, position = 0)

    @Test
    fun `the first tap on a level selects its primary`() {
        assertEquals(MoodTap.Select(bad.id), moodTap(listOf(bad, sad), selected = null))
        assertEquals(MoodTap.Select(bad.id), moodTap(listOf(bad, sad), selected = good))
    }

    @Test
    fun `tapping the selected level opens its aliases, or clears when it has none`() {
        assertEquals(MoodTap.OpenAliases, moodTap(listOf(bad, sad), selected = bad))
        assertEquals(MoodTap.OpenAliases, moodTap(listOf(bad, sad), selected = sad))
        assertEquals(MoodTap.Clear, moodTap(listOf(good), selected = good))
    }
}
