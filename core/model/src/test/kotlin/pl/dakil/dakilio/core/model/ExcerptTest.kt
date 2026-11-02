package pl.dakil.dakilio.core.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ExcerptTest {
    private val filler = "Lorem ipsum dolor sit amet consectetur adipiscing elit sed do eiusmod tempor incididunt ut labore et dolore magna aliqua"

    @Test
    fun earlyMatchKeepsTheWholeText() {
        val text = "We walked to the lake and then $filler"
        assertEquals(text, noteExcerpt(text, "lake"))
    }

    @Test
    fun noMatchKeepsTheWholeText() {
        val text = "$filler and more"
        assertEquals(text, noteExcerpt(text, "pizza"))
    }

    @Test
    fun blankQueryKeepsTheWholeText() {
        assertEquals(filler, noteExcerpt(filler, " "))
    }

    @Test
    fun lateMatchStartsShortlyBeforeItAtAWord() {
        val text = "$filler and finally we found the hidden waterfall nearby"
        val excerpt = noteExcerpt(text, "waterfall")
        assertTrue(excerpt, excerpt.startsWith("…"))
        assertTrue("waterfall" in excerpt)
        assertTrue(excerpt, excerpt.indexOf("waterfall") <= 45)
        // Starts at a whole word that occurs in the original text.
        val firstWord = excerpt.removePrefix("…").substringBefore(' ')
        assertTrue(excerpt, Regex("\\b${Regex.escape(firstWord)}\\b").containsMatchIn(text))
    }

    @Test
    fun matchRightAfterANewlineStartsAtThatLine() {
        val text = "$filler\nWaterfall day was the best"
        assertEquals("…Waterfall day was the best", noteExcerpt(text, "waterfall"))
    }

    @Test
    fun matchingIgnoresCase() {
        val text = "$filler then WATERFALL"
        assertTrue(noteExcerpt(text, "waterfall").endsWith("WATERFALL"))
        assertTrue(noteExcerpt(text, "waterfall").startsWith("…"))
    }
}
