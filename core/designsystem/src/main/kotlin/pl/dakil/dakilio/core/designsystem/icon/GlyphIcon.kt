package pl.dakil.dakilio.core.designsystem.icon

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import pl.dakil.dakilio.core.designsystem.theme.RoundedDisplayFamily

/**
 * How a glyph sits in its icon box. The numbers were tuned next to the Material Symbols (Rounded,
 * weight 400): at this size and weight a letter's stems are about as thick as their 2 dp strokes
 * and a capital is about as tall as a typical symbol.
 */
internal object GlyphMetrics {
    /** Font size as a share of the box's shorter side: 20 dp of type in a 24 dp icon. */
    const val FONT_SIZE = 0.84f

    /** Google Sans Flex's cap height in em (its OS/2 table; see tools/subset-font.sh for the font). */
    const val CAP_HEIGHT = 0.716f

    /** The widest ink allowed, as a share of the box's width; wider text is set smaller. */
    const val MAX_INK_WIDTH = 0.92f

    /** Stems as thick as an outlined symbol's strokes, and the bolder look that stands in for "filled". */
    val Resting = FontWeight.Medium
    val Emphasized = FontWeight.Bold
}

// A layout of its own, in pixels: an icon keeps its size whatever the user's font scale is.
private val PixelDensity = Density(density = 1f, fontScale = 1f)
private val DefaultSize = 24.dp

/** The text of a [GlyphSymbol], drawn to fill an icon's box. See [DakilioIcon]. */
@Composable
internal fun GlyphIcon(
    text: String,
    contentDescription: String?,
    modifier: Modifier,
    emphasized: Boolean,
    tint: Color,
) {
    // drawWithCache below already keeps the one layout this icon needs.
    val measurer = rememberTextMeasurer(cacheSize = 0)
    // Read only while drawing, so an animated tint doesn't lay the text out again every frame.
    val color by rememberUpdatedState(tint)
    val weight = if (emphasized) GlyphMetrics.Emphasized else GlyphMetrics.Resting
    val semantics = if (contentDescription != null) {
        Modifier.semantics {
            this.contentDescription = contentDescription
            role = Role.Image
        }
    } else {
        Modifier
    }
    Box(
        modifier
            // After the caller's modifier, so it's only the default: a size set there wins.
            .size(DefaultSize)
            .drawWithCache {
                val glyph = layoutGlyph(measurer, text, weight, size)
                onDrawBehind {
                    if (glyph != null) drawText(glyph.layout, color = color, topLeft = glyph.topLeft)
                }
            }
            .then(semantics),
    )
}

private class PlacedGlyph(val layout: TextLayoutResult, val topLeft: Offset)

private fun layoutGlyph(measurer: TextMeasurer, text: String, weight: FontWeight, box: Size): PlacedGlyph? {
    if (box.minDimension <= 0f) return null
    fun measure(fontSize: Float): Pair<TextLayoutResult, Rect> {
        val layout = measurer.measure(
            text = text,
            style = TextStyle(fontFamily = RoundedDisplayFamily, fontWeight = weight, fontSize = fontSize.sp),
            softWrap = false,
            maxLines = 1,
            layoutDirection = LayoutDirection.Ltr,
            density = PixelDensity,
        )
        // The outline's bounds, not the line's: a letter is centered by what is actually drawn.
        return layout to layout.getPathForRange(0, text.length).getBounds()
    }

    var fontSize = box.minDimension * GlyphMetrics.FONT_SIZE
    var (layout, ink) = measure(fontSize)
    val fitted = glyphFontSizeToFit(fontSize, ink.width, box.width)
    if (fitted < fontSize) {
        fontSize = fitted
        measure(fontSize).let { (fittedLayout, fittedInk) ->
            layout = fittedLayout
            ink = fittedInk
        }
    }
    return PlacedGlyph(layout, glyphTopLeft(box, ink, layout.firstBaseline, fontSize))
}

/** [fontSize], or the smaller size at which ink [inkWidth] wide fits a box [boxWidth] wide. */
internal fun glyphFontSizeToFit(fontSize: Float, inkWidth: Float, boxWidth: Float): Float {
    val room = boxWidth * GlyphMetrics.MAX_INK_WIDTH
    return if (inkWidth > room && inkWidth > 0f) fontSize * room / inkWidth else fontSize
}

/**
 * Where a text layout's top left corner goes in [box], given the [ink] its glyphs cover and its
 * [baseline] (both relative to the layout) at [fontSize].
 *
 * Sideways, the ink is centered. Up and down, capitals are centered and everything else shares
 * their baseline, like centered text: "a" sits lower than "A" and "g" hangs below, so lower case
 * reads as lower case and a letter lines up with the label next to it. Ink that would leave the
 * box (an accent on a capital, a deep descender) is pushed back in.
 */
internal fun glyphTopLeft(box: Size, ink: Rect, baseline: Float, fontSize: Float): Offset {
    val x = (box.width - ink.width) / 2f - ink.left
    var y = (box.height + GlyphMetrics.CAP_HEIGHT * fontSize) / 2f - baseline
    when {
        ink.height >= box.height -> y = (box.height - ink.height) / 2f - ink.top
        y + ink.top < 0f -> y = -ink.top
        y + ink.bottom > box.height -> y = box.height - ink.bottom
    }
    return Offset(x, y)
}
