package pl.dakil.dakilio.core.designsystem.icon

import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * A 100px box with 80px type, laid out with its baseline 77px down (Google Sans Flex's ascent is
 * 0.966 em). Capitals are 0.716 em tall: 57.28px.
 */
class GlyphPlacementTest {
    private val box = Size(100f, 100f)
    private val fontSize = 80f
    private val baseline = 77f
    private val capTop = baseline - GlyphMetrics.CAP_HEIGHT * fontSize

    private fun place(ink: Rect) = glyphTopLeft(box, ink, baseline, fontSize)

    @Test
    fun `a capital is centered both ways`() {
        val ink = Rect(left = 6f, top = capTop, right = 50f, bottom = baseline)
        val at = place(ink)
        assertEquals(50f, at.x + ink.center.x, 0.001f)
        assertEquals(50f, at.y + ink.center.y, 0.001f)
    }

    @Test
    fun `lower case shares the capitals' baseline`() {
        val capital = place(Rect(6f, capTop, 50f, baseline))
        val xHeight = place(Rect(4f, baseline - 41f, 40f, baseline))
        val descender = place(Rect(4f, baseline - 41f, 40f, baseline + 18f))
        assertEquals(capital.y, xHeight.y, 0.001f)
        assertEquals(capital.y, descender.y, 0.001f)
    }

    @Test
    fun `ink is centered sideways whatever its side bearings`() {
        val ink = Rect(left = 20f, top = capTop, right = 30f, bottom = baseline)
        assertEquals(50f, place(ink).x + ink.center.x, 0.001f)
    }

    @Test
    fun `an accent above the box is pushed down to its top`() {
        val ink = Rect(6f, capTop - 25f, 50f, baseline)
        assertEquals(0f, place(ink).y + ink.top, 0.001f)
    }

    @Test
    fun `a descender below the box is pushed up to its bottom`() {
        val ink = Rect(4f, baseline - 41f, 40f, baseline + 30f)
        assertEquals(100f, place(ink).y + ink.bottom, 0.001f)
    }

    @Test
    fun `ink taller than the box is centered`() {
        val ink = Rect(6f, baseline - 90f, 50f, baseline + 30f)
        assertEquals(50f, place(ink).y + ink.center.y, 0.001f)
    }

    @Test
    fun `type that fits keeps its size`() {
        assertEquals(80f, glyphFontSizeToFit(fontSize = 80f, inkWidth = 60f, boxWidth = 100f), 0f)
        assertEquals(80f, glyphFontSizeToFit(fontSize = 80f, inkWidth = 0f, boxWidth = 100f), 0f)
    }

    @Test
    fun `type too wide is set smaller, so its ink fills the allowed width`() {
        val fitted = glyphFontSizeToFit(fontSize = 80f, inkWidth = 184f, boxWidth = 100f)
        assertEquals(40f, fitted, 0.001f)
        assertEquals(100f * GlyphMetrics.MAX_INK_WIDTH, 184f * fitted / 80f, 0.001f)
    }
}
