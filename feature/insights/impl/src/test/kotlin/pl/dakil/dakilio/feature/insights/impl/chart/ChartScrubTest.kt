package pl.dakil.dakilio.feature.insights.impl.chart

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ChartScrubTest {
    @Test
    fun `a touch lands in the slot under it and the ends catch the rest`() {
        assertEquals(0, slotAt(0f, 70f, 7))
        assertEquals(0, slotAt(9.9f, 70f, 7))
        assertEquals(1, slotAt(10f, 70f, 7))
        assertEquals(6, slotAt(69f, 70f, 7))
        assertEquals(6, slotAt(500f, 70f, 7))
        assertEquals(0, slotAt(-20f, 70f, 7))
        assertEquals(0, slotAt(10f, 0f, 7))
    }

    @Test
    fun `a touch picks the nearest point of a line`() {
        // 5 points across 100: at 0, 25, 50, 75, 100.
        assertEquals(0, nearestPoint(12f, 100f, 5))
        assertEquals(1, nearestPoint(13f, 100f, 5))
        assertEquals(4, nearestPoint(90f, 100f, 5))
        assertEquals(4, nearestPoint(400f, 100f, 5))
        assertEquals(0, nearestPoint(-5f, 100f, 5))
        assertEquals(0, nearestPoint(50f, 100f, 1))
    }

    @Test
    fun `a touch finds its cell in a grid`() {
        assertEquals(2 to 30, cellAt(25f, 309f, 120f, 310f, 12, 31))
        assertEquals(11 to 0, cellAt(119f, 0f, 120f, 310f, 12, 31))
    }

    @Test
    fun `a touch finds its segment in a split bar`() {
        val weights = listOf(1, 0, 2, 1) // 25 | - | 50 | 25
        assertEquals(0, segmentAt(10f, 100f, weights))
        assertEquals(2, segmentAt(26f, 100f, weights))
        assertEquals(3, segmentAt(99f, 100f, weights))
        assertEquals(3, segmentAt(150f, 100f, weights))
        assertNull(segmentAt(10f, 100f, listOf(0, 0)))
    }
}
