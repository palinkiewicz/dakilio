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

    private fun excerptOf(text: String, word: String): Excerpt {
        val start = text.indexOf(word)
        return keywordExcerpt(text, listOf(start until start + word.length))
    }

    private fun Excerpt.marked(): List<String> = ranges.map { text.substring(it) }

    @Test
    fun keywordExcerptKeepsAnEarlyMatchFromTheStart() {
        val excerpt = excerptOf("We rode a bike\nto the lake", "bike")
        assertEquals("We rode a bike to the lake", excerpt.text)
        assertEquals(listOf("bike"), excerpt.marked())
    }

    @Test
    fun keywordExcerptStartsShortlyBeforeALateMatch() {
        val excerpt = excerptOf("$filler and finally the waterfall", "waterfall")
        assertTrue(excerpt.text, excerpt.text.startsWith("…"))
        assertTrue(excerpt.text, excerpt.text.length < 60)
        assertEquals(listOf("waterfall"), excerpt.marked())
    }

    @Test
    fun keywordExcerptCutsALongTailAtAWord() {
        val excerpt = keywordExcerpt("bike $filler $filler", listOf(0..3), maxLength = 30)
        assertTrue(excerpt.text, excerpt.text.endsWith("…"))
        assertTrue(excerpt.text, excerpt.text.length <= 32)
        assertTrue(excerpt.text, !excerpt.text.dropLast(1).endsWith(" "))
        assertEquals(listOf("bike"), excerpt.marked())
    }

    @Test
    fun keywordExcerptCollapsesWhitespaceAndShiftsLaterRanges() {
        val text = "a\n\n  bike   and   rain"
        val excerpt = keywordExcerpt(text, listOf(text.indexOf("bike").let { it until it + 4 }, text.indexOf("rain").let { it until it + 4 }))
        assertEquals("a bike and rain", excerpt.text)
        assertEquals(listOf("bike", "rain"), excerpt.marked())
    }

    @Test
    fun keywordExcerptWithoutRangesIsTheStart() {
        val excerpt = keywordExcerpt("just text", emptyList())
        assertEquals("just text", excerpt.text)
        assertTrue(excerpt.ranges.isEmpty())
    }
}
