package pl.dakil.dakilio.core.ui.component

import androidx.compose.ui.geometry.Rect
import org.junit.Assert.assertEquals
import org.junit.Test

/** Rows 48px tall with an 8px gap: row i spans [i * 56, i * 56 + 48]. */
class ReorderableTest {
    private fun top(i: Int) = i * 56f
    private fun center(i: Int) = top(i) + 24f

    /** The direction for row [i] drawn [shift] px away from its place, among [count] rows. */
    private fun direction(i: Int, shift: Float, count: Int) = swapDirection(
        ownTop = top(i) + shift,
        ownBottom = top(i) + 48f + shift,
        previousCenter = if (i > 0) center(i - 1) else null,
        nextCenter = if (i < count - 1) center(i + 1) else null,
    )

    @Test
    fun secondRowClampedOverTheFirstSwapsUp() {
        // The clamp stops the held row exactly over the first one: equal centers, yet it must swap.
        assertEquals(-1, direction(1, shift = top(0) - top(1), count = 4))
    }

    @Test
    fun penultimateRowClampedOverTheLastSwapsDown() {
        assertEquals(1, direction(2, shift = top(3) - top(2), count = 4))
    }

    @Test
    fun twoRowsSwapBothWays() {
        assertEquals(1, direction(0, shift = top(1) - top(0), count = 2))
        assertEquals(-1, direction(1, shift = top(0) - top(1), count = 2))
    }

    @Test
    fun smallMovesDontSwap() {
        // Leading edge not yet past the neighbour's middle (8px gap + 24px).
        assertEquals(0, direction(1, shift = 31f, count = 4))
        assertEquals(0, direction(1, shift = -31f, count = 4))
        assertEquals(1, direction(1, shift = 33f, count = 4))
        assertEquals(-1, direction(1, shift = -33f, count = 4))
    }

    @Test
    fun endsHaveNothingToSwapWith() {
        assertEquals(0, direction(0, shift = -100f, count = 4))
        assertEquals(0, direction(3, shift = 100f, count = 4))
    }

    @Test
    fun noSwapBackRightAfterASwap() {
        // Row 1 held 33px down swaps with row 2; it's now in slot 2, drawn at the same place.
        val drawnTop = top(1) + 33f
        val shiftInNewSlot = drawnTop - top(2)
        assertEquals(0, direction(2, shiftInNewSlot, count = 4))
    }

    @Test
    fun swappingDownMovesHeldRowToTheNeighboursSlot() {
        val own = Rect(0f, top(0), 100f, top(0) + 48f)
        val other = Rect(0f, top(1), 100f, top(1) + 48f)
        val (ownAfter, otherAfter) = slotsAfterSwap(own, other, down = true)
        assertEquals(other, ownAfter)
        assertEquals(own, otherAfter)
    }

    @Test
    fun swappingUpKeepsUnequalHeightsInTheSameSpan() {
        // A 48px row above a 96px held row: afterwards the held row is on top, the short one below.
        val other = Rect(0f, 0f, 100f, 48f)
        val own = Rect(0f, 56f, 100f, 152f)
        val (ownAfter, otherAfter) = slotsAfterSwap(own, other, down = false)
        assertEquals(Rect(0f, 0f, 100f, 96f), ownAfter)
        assertEquals(Rect(0f, 104f, 100f, 152f), otherAfter)
    }
}
