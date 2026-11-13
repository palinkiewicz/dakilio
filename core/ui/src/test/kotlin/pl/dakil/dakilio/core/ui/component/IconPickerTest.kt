package pl.dakil.dakilio.core.ui.component

import org.junit.Assert.assertEquals
import org.junit.Test
import pl.dakil.dakilio.core.designsystem.icon.GlyphSymbol
import pl.dakil.dakilio.core.designsystem.icon.SymbolSection

class IconPickerTest {
    private val sections = listOf(
        SymbolSection("upper", 0, GlyphSymbol.each("ABC")),
        SymbolSection("lower", 0, GlyphSymbol.each("abc")),
        SymbolSection("digits", 0, GlyphSymbol.each("12")),
    )

    @Test
    fun `the picker opens at the header of the selected symbol's section`() {
        assertEquals(0, sectionStartIndex(sections, "glyph:B"))
        // After the first header and its three letters.
        assertEquals(4, sectionStartIndex(sections, "glyph:c"))
        assertEquals(8, sectionStartIndex(sections, "glyph:2"))
    }

    @Test
    fun `a symbol of no section opens at the top`() {
        assertEquals(0, sectionStartIndex(sections, "label"))
        assertEquals(0, sectionStartIndex(emptyList(), "glyph:A"))
    }
}
