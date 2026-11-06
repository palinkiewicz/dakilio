package pl.dakil.dakilio.core.ui.component

import org.junit.Assert.assertEquals
import org.junit.Test

class EntryShareTest {
    @Test
    fun shortEntryUsesTheFullWidth() {
        assertEquals(3f, shareDensity(widthPx = 1080, layoutWidthDp = 360f, measuredHeightPx = 2_000, maxHeightPx = 8_000), 0.0001f)
    }

    @Test
    fun notMeasuredYetUsesTheFullWidth() {
        assertEquals(3f, shareDensity(1080, 360f, measuredHeightPx = 0, maxHeightPx = 8_000), 0.0001f)
    }

    @Test
    fun exactlyAtTheCapStaysFull() {
        assertEquals(3f, shareDensity(1080, 360f, measuredHeightPx = 8_000, maxHeightPx = 8_000), 0.0001f)
    }

    @Test
    fun tallEntryShrinksToFitTheCap() {
        val density = shareDensity(1080, 360f, measuredHeightPx = 16_000, maxHeightPx = 8_000)
        assertEquals(1.5f, density, 0.0001f)
        // Height scales with the density, so the redrawn entry lands on the cap.
        assertEquals(8_000f, 16_000f * density / 3f, 0.5f)
    }
}
