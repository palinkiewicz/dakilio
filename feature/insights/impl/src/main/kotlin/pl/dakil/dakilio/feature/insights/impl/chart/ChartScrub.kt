package pl.dakil.dakilio.feature.insights.impl.chart

import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.IntSize
import kotlin.math.roundToInt

/** The slot under [x] when [width] is cut into [count] equal slots (columns of a bar chart); the ends catch what's outside. */
internal fun slotAt(x: Float, width: Float, count: Int): Int =
    if (count <= 0 || width <= 0f) 0 else (x / width * count).toInt().coerceIn(0, count - 1)

/** The point nearest to [x] when [count] points are spread from one edge of [width] to the other (a line chart). */
internal fun nearestPoint(x: Float, width: Float, count: Int): Int =
    if (count <= 1 || width <= 0f) 0 else (x / width * (count - 1)).roundToInt().coerceIn(0, count - 1)

/** The (column, row) under a touch in a grid of equal cells. */
internal fun cellAt(x: Float, y: Float, width: Float, height: Float, columns: Int, rows: Int): Pair<Int, Int> =
    slotAt(x, width, columns) to slotAt(y, height, rows)

/** The segment under [x] in a bar split by [weights] (a stacked bar), or `null` when all are zero. */
internal fun segmentAt(x: Float, width: Float, weights: List<Int>): Int? {
    val total = weights.sum()
    if (total <= 0 || width <= 0f) return null
    var end = 0f
    weights.forEachIndexed { index, weight ->
        if (weight <= 0) return@forEachIndexed
        end += width * weight / total
        if (x <= end) return index
    }
    return weights.indexOfLast { it > 0 }
}

/**
 * Reads a chart by touch: a tap or a sideways drag reports the finger's position (and the chart's
 * size), so the chart can mark the value under it. A sideways drag starts at once (the page only scrolls up and down);
 * [anyDirection] adds a drag in every direction after a long press, for grids.
 */
@Composable
internal fun Modifier.chartScrub(anyDirection: Boolean = false, onPosition: (position: Offset, size: IntSize) -> Unit): Modifier {
    val report by rememberUpdatedState(onPosition)
    val base = this
        .pointerInput(Unit) { detectTapGestures { report(it, size) } }
        .pointerInput(Unit) {
            detectHorizontalDragGestures(onDragStart = { report(it, size) }) { change, _ ->
                change.consume()
                report(change.position, size)
            }
        }
    return if (!anyDirection) {
        base
    } else {
        base.pointerInput(Unit) {
            detectDragGesturesAfterLongPress(onDragStart = { report(it, size) }) { change, _ ->
                change.consume()
                report(change.position, size)
            }
        }
    }
}
