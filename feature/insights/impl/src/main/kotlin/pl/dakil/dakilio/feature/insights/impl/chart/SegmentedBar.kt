package pl.dakil.dakilio.feature.insights.impl.chart

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

@Immutable
internal data class BarSegment(val weight: Int, val color: Color)

/**
 * One whole split into parts on an ordered scale: a single bar with 2dp surface gaps between the
 * segments and rounded outer ends. A legend next to it carries the names and the figures.
 */
@Composable
internal fun SegmentedBar(
    segments: List<BarSegment>,
    description: String,
    modifier: Modifier = Modifier,
    height: Dp = 24.dp,
    onSelect: ((Int) -> Unit)? = null,
) {
    val select by rememberUpdatedState(onSelect)
    val tap = if (onSelect == null) {
        Modifier
    } else {
        Modifier.pointerInput(segments) {
            detectTapGestures { offset -> segmentAt(offset.x, size.width.toFloat(), segments.map { it.weight })?.let { select?.invoke(it) } }
        }
    }
    Canvas(modifier.fillMaxWidth().height(height).clearAndSetSemantics { contentDescription = description }.then(tap)) {
        val shown = segments.filter { it.weight > 0 }
        val total = shown.sumOf { it.weight }
        if (total == 0) return@Canvas
        val gap = 2.dp.toPx()
        val usable = (size.width - gap * (shown.size - 1)).coerceAtLeast(0f)
        val radius = CornerRadius(4.dp.toPx())
        val square = CornerRadius.Zero
        var x = 0f
        shown.forEachIndexed { i, segment ->
            val w = usable * segment.weight / total
            val path = Path().apply {
                addRoundRect(
                    RoundRect(
                        left = x, top = 0f, right = x + w, bottom = size.height,
                        topLeftCornerRadius = if (i == 0) radius else square,
                        bottomLeftCornerRadius = if (i == 0) radius else square,
                        topRightCornerRadius = if (i == shown.lastIndex) radius else square,
                        bottomRightCornerRadius = if (i == shown.lastIndex) radius else square,
                    ),
                )
            }
            drawPath(path, segment.color)
            x += w + gap
        }
    }
}
