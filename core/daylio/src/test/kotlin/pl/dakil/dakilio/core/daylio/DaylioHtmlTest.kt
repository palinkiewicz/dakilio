package pl.dakil.dakilio.core.daylio

import org.junit.Assert.assertEquals
import org.junit.Test

class DaylioHtmlTest {
    @Test
    fun plainTextPassesThrough() {
        assertEquals("Just text 🥀", daylioHtmlToText("  Just text 🥀 "))
    }

    @Test
    fun formattedSampleNote() {
        val note = Fixtures.backup.dayEntries.first { it.id == 6 }.note
        val expected = """
            This one will be very formatted...

            **Bold**

            *Some italic **and bold***

            1. list item
            2. List item another
            • Bullet
            • Bullet another and emoji 🥀🔥
            Underlined

            Strike through
        """.trimIndent()
        assertEquals(expected, daylioHtmlToText(note))
    }

    @Test
    fun boldAndItalicBecomeMarkers() {
        assertEquals("A **bold** and *italic* day", daylioHtmlToText("A <b>bold</b> and <i>italic</i> day"))
        assertEquals("**strong** *em*", daylioHtmlToText("<strong>strong</strong> <em>em</em>"))
        // Markers hug the text, and each line gets its own pair.
        assertEquals("a **b** c", daylioHtmlToText("a<b> b </b>c"))
        assertEquals("**one**\n**two**", daylioHtmlToText("<b>one<br>two</b>"))
        // Nothing to format, a stray closing tag, and tags Dakilio has no marker for.
        assertEquals("a b", daylioHtmlToText("a<b> </b>b"))
        assertEquals("plain", daylioHtmlToText("plain</b>"))
        assertEquals("under strike", daylioHtmlToText("<u>under</u> <strike>strike</strike>"))
    }

    @Test
    fun divsAndEmptyLines() {
        assertEquals("One\n\nTwo\nThree", daylioHtmlToText("<div>One</div><div><br></div><div>Two</div><div>Three</div>"))
    }

    @Test
    fun manyBreaksCollapseToOneEmptyLine() {
        assertEquals("A\n\nB", daylioHtmlToText("A<br><br><br><br/><br />B"))
    }

    @Test
    fun entitiesAreDecoded() {
        assertEquals("Fish & chips <3 \"x\" 'y' z é", daylioHtmlToText("Fish &amp; chips &lt;3 &quot;x&quot; &#39;y&#39;&nbsp;z &#xE9;"))
    }

    @Test
    fun unknownEntityIsKept() {
        assertEquals("a &bogus; b", daylioHtmlToText("a &bogus; b"))
    }

    @Test
    fun orderedListsCountSeparately() {
        assertEquals(
            "1. a\n2. b\nmid\n1. c",
            daylioHtmlToText("<ol><li>a</li><li>b</li></ol>mid<ol><li>c</li></ol>"),
        )
    }
}
