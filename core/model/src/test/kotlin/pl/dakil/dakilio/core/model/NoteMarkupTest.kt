package pl.dakil.dakilio.core.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import pl.dakil.dakilio.core.model.NoteStyle.Bold
import pl.dakil.dakilio.core.model.NoteStyle.Heading
import pl.dakil.dakilio.core.model.NoteStyle.Italic

class NoteMarkupTest {
    private fun parse(note: String) = NoteMarkup.parse(note)

    /** The formatted pieces as "style:text", in text order. */
    private fun pieces(note: String): List<String> = parse(note).let { formatted ->
        formatted.spans.sortedWith(compareBy({ it.start }, { it.style })).map { "${it.style}:${formatted.text.substring(it.start, it.end)}" }
    }

    @Test
    fun `plain text comes back untouched`() {
        val note = "Just a day.\nNothing special, 2 + 2 = 4."
        assertEquals(FormattedNote(note, emptyList()), parse(note))
        assertTrue(parse(note).isPlain)
        assertEquals(note, NoteMarkup.plain(note))
    }

    @Test
    fun `bold and italic lose their markers`() {
        val formatted = parse("A **bold** word and an *italic* one.")
        assertEquals("A bold word and an italic one.", formatted.text)
        assertEquals(listOf(NoteSpan(2, 6, Bold), NoteSpan(19, 25, Italic)), formatted.spans)
    }

    @Test
    fun `a heading is a line that starts with a hash and a space`() {
        val formatted = parse("# Morning\nCoffee first.\n## Later\n#NoSpace\n#")
        assertEquals("Morning\nCoffee first.\nLater\n#NoSpace\n#", formatted.text)
        assertEquals(listOf("Heading:Morning", "Heading:Later"), pieces("# Morning\nCoffee first.\n## Later\n#NoSpace\n#"))
        // Not at the start of the line, or more than three hashes: just text.
        assertTrue(parse("Issue # 4\n#### Deep").isPlain)
    }

    @Test
    fun `formatting nests`() {
        assertEquals(listOf("Bold:both", "Italic:both"), pieces("***both***"))
        assertEquals(listOf("Bold:bold italic", "Italic:italic"), pieces("**bold *italic***"))
        assertEquals(listOf("Bold:italic bold", "Italic:italic"), pieces("***italic* bold**"))
        assertEquals(listOf("Italic:a b c", "Bold:b"), pieces("*a **b** c*"))
        assertEquals(listOf("Bold:Big", "Heading:Big day"), pieces("# **Big** day"))
        assertEquals("a b c", parse("*a **b** c*").text)
    }

    @Test
    fun `pairs must hug their text and stay on one line`() {
        assertTrue(parse("** not bold **").isPlain)
        assertTrue(parse("* not italic *").isPlain)
        assertTrue(parse("**open\nclose**").isPlain)
        assertTrue(parse("*open\nclose*").isPlain)
        assertEquals("** not bold **", parse("** not bold **").text)
        assertTrue(parse("****").isPlain)
        assertTrue(parse("**").isPlain)
    }

    @Test
    fun `a single asterisk only works around whole words`() {
        assertTrue(parse("2*3*4 = 24").isPlain)
        assertTrue(parse("a * b * c").isPlain)
        assertTrue(parse("5* rating, 4* hotel").isPlain)
        assertTrue(parse("footnote* and another*").isPlain)
        assertEquals(listOf("Italic:really"), pieces("It was *really* good."))
        assertEquals(listOf("Italic:so"), pieces("(*so*) tired"))
        assertEquals("pre*fix* stays", parse("pre*fix* stays").text)
    }

    @Test
    fun `unmatched markers stay as typed`() {
        assertEquals("a ** b", parse("a ** b").text)
        assertEquals("**bold without end", parse("**bold without end").text)
        assertEquals(listOf("Bold:one"), pieces("**one** and **two"))
        assertEquals("one and **two", parse("**one** and **two").text)
    }

    @Test
    fun `a backslash keeps a marker as text`() {
        assertEquals("*not italic*", parse("\\*not italic\\*").text)
        assertTrue(parse("\\*not italic\\*").isPlain)
        assertEquals("# not a heading", parse("\\# not a heading").text)
        assertEquals(listOf("Bold:2*3"), pieces("**2\\*3**"))
        assertEquals("C:\\dir", parse("C:\\dir").text)
    }

    @Test
    fun `plain drops every marker`() {
        assertEquals("Morning\nA bold and italic day", NoteMarkup.plain("# Morning\nA **bold** and *italic* day"))
    }

    @Test
    fun `bold wraps the selection and unwraps it again`() {
        val wrapped = NoteMarkup.toggleInline("a good day", 2, 6, NoteMarkup.BOLD)
        assertEquals(NoteEdit("a **good** day", 4, 8), wrapped)
        assertEquals(NoteEdit("a good day", 2, 6), NoteMarkup.toggleInline(wrapped.text, wrapped.selectionStart, wrapped.selectionEnd, NoteMarkup.BOLD))
    }

    @Test
    fun `markers hug the text when the selection has spaces at its ends`() {
        assertEquals(NoteEdit("a **good** day", 4, 8), NoteMarkup.toggleInline("a good day", 1, 7, NoteMarkup.BOLD))
        // Only spaces selected: nothing to wrap.
        assertEquals(NoteEdit("a   b", 1, 4), NoteMarkup.toggleInline("a   b", 1, 4, NoteMarkup.BOLD))
    }

    @Test
    fun `nothing selected puts the cursor inside an empty pair`() {
        assertEquals(NoteEdit("so **** far", 5, 5), NoteMarkup.toggleInline("so  far", 3, 3, NoteMarkup.BOLD))
        assertEquals(NoteEdit("**", 1, 1), NoteMarkup.toggleInline("", 0, 0, NoteMarkup.ITALIC))
    }

    @Test
    fun `a selection over several lines wraps each line`() {
        val edit = NoteMarkup.toggleInline("one\n\ntwo words\nthree", 0, 14, NoteMarkup.ITALIC)
        assertEquals("*one*\n\n*two words*\nthree", edit.text)
        assertEquals(0, edit.selectionStart)
        assertEquals(18, edit.selectionEnd)
        assertEquals(listOf("Italic:one", "Italic:two words"), pieces(edit.text))
    }

    @Test
    fun `italic and bold toggle separately on the same text`() {
        // Bold text made italic too, then bold taken off again.
        val both = NoteMarkup.toggleInline("**word**", 2, 6, NoteMarkup.ITALIC)
        assertEquals(NoteEdit("***word***", 3, 7), both)
        assertEquals(NoteEdit("*word*", 1, 5), NoteMarkup.toggleInline(both.text, 3, 7, NoteMarkup.BOLD))
        assertEquals(NoteEdit("**word**", 2, 6), NoteMarkup.toggleInline(both.text, 3, 7, NoteMarkup.ITALIC))
    }

    @Test
    fun `heading toggles on the line of the cursor`() {
        val text = "first\nsecond line\nthird"
        val on = NoteMarkup.toggleHeading(text, 9, 9)
        assertEquals(NoteEdit("first\n# second line\nthird", 11, 11), on)
        assertEquals(NoteEdit(text, 9, 9), NoteMarkup.toggleHeading(on.text, 11, 11))
        // The cursor in the marker itself lands at the start of the line.
        assertEquals(NoteEdit(text, 6, 6), NoteMarkup.toggleHeading(on.text, 7, 7))
        assertEquals(NoteEdit("# ", 2, 2), NoteMarkup.toggleHeading("", 0, 0))
        assertEquals(NoteEdit("title", 0, 5), NoteMarkup.toggleHeading("## title", 3, 8))
    }
}
