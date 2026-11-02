package pl.dakil.dakilio.core.ui.color

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class TagColorTest {
    @Test
    fun keysAreUniqueAndRoundTrip() {
        assertEquals(TagColor.entries.size, TagColor.entries.map { it.key }.toSet().size)
        TagColor.entries.forEach { assertEquals(it, TagColor.fromKey(it.key)) }
    }

    @Test
    fun noOrUnknownKeyHasNoColor() {
        assertNull(TagColor.fromKey(null))
        assertNull(TagColor.fromKey("ultraviolet"))
    }

    @Test
    fun everyColorHasDistinctToneInBothThemes() {
        listOf(false, true).forEach { dark ->
            val fills = TagColor.entries.map { deriveAccentColors(it.seed, dark).container }
            assertEquals(fills.size, fills.toSet().size)
            fills.forEach { fill -> assertTrue(fill.alpha == 1f) }
        }
    }
}
