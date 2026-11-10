package pl.dakil.dakilio.feature.export.impl

import org.junit.Assert.assertEquals
import org.junit.Test

class PdfLayoutTest {
    private val bottoms = floatArrayOf(10f, 20f, 30f, 45f, 60f)

    @Test
    fun `fits whole lines only`() {
        assertEquals(2, linesThatFit(bottoms, start = 0, available = 25f))
        assertEquals(3, linesThatFit(bottoms, start = 0, available = 30f))
        assertEquals(5, linesThatFit(bottoms, start = 0, available = 100f))
    }

    @Test
    fun `counts from the given line`() {
        // From line 2 (top 20): 30 → 10, 45 → 25, 60 → 40.
        assertEquals(2, linesThatFit(bottoms, start = 2, available = 25f))
        assertEquals(0, linesThatFit(bottoms, start = 3, available = 10f))
        assertEquals(0, linesThatFit(bottoms, start = 5, available = 100f))
    }

    @Test
    fun `a line taller than the space is forced on an empty page`() {
        assertEquals(0, linesThatFit(floatArrayOf(900f), start = 0, available = 700f))
        assertEquals(1, linesThatFit(floatArrayOf(900f), start = 0, available = 700f, force = true))
    }

    @Test
    fun `photos fit the box and are never blown up`() {
        assertEquals(400f to 300f, photoBox(800, 600, maxWidth = 500f, maxHeight = 300f))
        assertEquals(150f to 300f, photoBox(600, 1200, maxWidth = 500f, maxHeight = 300f))
        assertEquals(75f to 45f, photoBox(100, 60, maxWidth = 500f, maxHeight = 300f, maxScale = 0.75f))
        assertEquals(0f to 0f, photoBox(0, 10, 500f, 300f))
    }

    @Test
    fun `sample size halves until near the target`() {
        assertEquals(1, sampleSize(800, 900))
        assertEquals(1, sampleSize(1700, 900))
        assertEquals(2, sampleSize(1800, 900))
        assertEquals(4, sampleSize(4000, 900))
    }
}
