package pl.dakil.dakilio.core.ui.component

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class EntryPeekScrollTest {
    @Test
    fun gainIsOneToOneForSmallOverflow() {
        assertEquals(1f, peekScrollGain(maxScroll = 300f, travel = 1200f), 0f)
    }

    @Test
    fun gainLetsOneSweepReachTheEnd() {
        assertEquals(2f, peekScrollGain(maxScroll = 2000f, travel = 1000f), 0.0001f)
    }

    @Test
    fun gainIsCapped() {
        assertEquals(PEEK_MAX_GAIN, peekScrollGain(maxScroll = 50_000f, travel = 1000f), 0f)
        assertEquals(PEEK_MAX_GAIN, peekScrollGain(maxScroll = 500f, travel = 0f), 0f)
    }

    @Test
    fun gainForContentThatFits() {
        assertEquals(1f, peekScrollGain(maxScroll = 0f, travel = 800f), 0f)
    }

    @Test
    fun noEdgeScrollInTheMiddle() {
        assertEquals(0f, edgeScrollSpeed(y = 1200f, height = 2400f, maxSpeed = 2500f), 0f)
        // Just inside the 12 % boundary still counts as the middle.
        assertEquals(0f, edgeScrollSpeed(y = 288f, height = 2400f, maxSpeed = 2500f), 0f)
    }

    @Test
    fun topZoneReadsFurtherAndRampsWithDepth() {
        val shallow = edgeScrollSpeed(y = 250f, height = 2400f, maxSpeed = 2500f)
        val deep = edgeScrollSpeed(y = 50f, height = 2400f, maxSpeed = 2500f)
        assertTrue(shallow > 0f)
        assertTrue(deep > shallow)
        assertEquals(2500f, edgeScrollSpeed(y = 0f, height = 2400f, maxSpeed = 2500f), 0f)
        assertEquals(2500f, edgeScrollSpeed(y = -40f, height = 2400f, maxSpeed = 2500f), 0f)
    }

    @Test
    fun bottomZoneScrollsBack() {
        assertTrue(edgeScrollSpeed(y = 2300f, height = 2400f, maxSpeed = 2500f) < 0f)
        assertEquals(-2500f, edgeScrollSpeed(y = 2400f, height = 2400f, maxSpeed = 2500f), 0f)
    }

    @Test
    fun noSizeNoScroll() {
        assertEquals(0f, edgeScrollSpeed(y = 0f, height = 0f, maxSpeed = 2500f), 0f)
    }
}
