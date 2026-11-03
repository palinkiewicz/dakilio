package pl.dakil.dakilio.core.daylio

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class DaylioTagIconsTest {

    @Test
    fun daylioIconWinsOverTheLabel() {
        // Polish labels match no keyword; the icons the user picked in Daylio still carry over.
        assertEquals("directions_run", daylioTagIcon("sport", iconId = 67)) // running
        assertEquals("rainy", daylioTagIcon("deszcz", iconId = 245)) // rain
        assertEquals("groups", daylioTagIcon("Zuzia", iconId = 94)) // user_group_man_man
    }

    @Test
    fun iconWithoutCounterpartFallsBackToTheLabel() {
        assertEquals("menu_book", daylioTagIcon("reading", iconId = 323)) // question_mark
        assertEquals(DEFAULT_TAG_ICON, daylioTagIcon("Sprawdzian", iconId = 323))
        assertEquals("menu_book", daylioTagIcon("reading"))
    }

    @Test
    fun everyIconIdIsMappedOnce() {
        val repeated = daylioMappedIconIds.groupingBy { it }.eachCount().filterValues { it > 1 }.keys
        assertTrue("Mapped more than once: $repeated", repeated.isEmpty())
    }
}
