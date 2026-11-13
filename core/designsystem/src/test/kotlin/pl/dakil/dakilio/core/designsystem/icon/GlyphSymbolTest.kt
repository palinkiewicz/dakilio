package pl.dakil.dakilio.core.designsystem.icon

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Test

class GlyphSymbolTest {
    @Test
    fun `a key is the prefix and the text, case kept`() {
        assertEquals("glyph:A", GlyphSymbol("A").key)
        assertEquals("glyph:a", GlyphSymbol("a").key)
        assertEquals(GlyphSymbol("A"), GlyphSymbol.fromKey("glyph:A"))
        assertEquals(GlyphSymbol("a"), GlyphSymbol.fromKey("glyph:a"))
    }

    @Test
    fun `letters outside ASCII and pairs are glyphs too`() {
        assertEquals("Ł", GlyphSymbol.fromKey("glyph:Ł")?.text)
        assertEquals("AB", GlyphSymbol.fromKey("glyph:AB")?.text)
        // One code point, two chars.
        assertEquals("𝔸", GlyphSymbol.fromKey("glyph:𝔸")?.text)
    }

    @Test
    fun `other keys are not glyphs`() {
        assertNull(GlyphSymbol.fromKey("label"))
        assertNull(GlyphSymbol.fromKey("A"))
        assertNull(GlyphSymbol.fromKey("Glyph:A"))
        assertNull(GlyphSymbol.fromKey("emoji:A"))
    }

    @Test
    fun `empty, long, blank and control text is refused`() {
        assertNull(GlyphSymbol.fromKey("glyph:"))
        assertNull(GlyphSymbol.fromKey("glyph:ABC"))
        assertNull(GlyphSymbol.fromKey("glyph: "))
        assertNull(GlyphSymbol.fromKey("glyph:A "))
        assertNull(GlyphSymbol.fromKey("glyph:\n"))
        assertNull(GlyphSymbol.fromKey("glyph:​"))
        assertNull(GlyphSymbol.fromKey("glyph:\uD835"))
        assertThrows(IllegalArgumentException::class.java) { GlyphSymbol("") }
        assertThrows(IllegalArgumentException::class.java) { GlyphSymbol("ABC") }
    }

    @Test
    fun `symbolForKey finds icons and letters and falls back to the label`() {
        assertEquals(DakilioSymbols.Favorite, symbolForKey("favorite"))
        assertEquals(GlyphSymbol("q"), symbolForKey("glyph:q"))
        assertEquals(DakilioSymbols.Label, symbolForKey("glyph:"))
        assertEquals(DakilioSymbols.Label, symbolForKey("no_such_icon"))
        assertEquals(DakilioSymbols.Label, symbolForKey(null))
    }

    @Test
    fun `no icon name can be mistaken for a glyph key`() {
        DakilioSymbols.all.forEach { assertNull(it.key, GlyphSymbol.fromKey(it.key)) }
    }

    @Test
    fun `an alphabet gives one symbol per letter, in order`() {
        assertEquals(listOf("A", "Ą", "B", "Ż"), GlyphSymbol.each("AĄBŻ").map { it.text })
        assertEquals(listOf("a", "b", "c"), GlyphSymbol.each(" a b\nc ").map { it.text })
        assertEquals(listOf("a", "b"), GlyphSymbol.each("aba").map { it.text })
        assertEquals(listOf("𝔸", "b"), GlyphSymbol.each("𝔸b").map { it.text })
        assertEquals(emptyList<GlyphSymbol>(), GlyphSymbol.each(""))
    }
}
