package pl.dakil.dakilio.feature.insights.impl.chart

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.height
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.unit.dp

/** A single-series bar: square at the baseline (the start), a 4dp rounded data end. */
@Composable
internal fun SingleBar(fraction: Float, modifier: Modifier = Modifier, color: Color = MaterialTheme.colorScheme.primary) {
    val shown by animateFloatAsState(fraction.coerceIn(0f, 1f), MaterialTheme.motionScheme.slowSpatialSpec(), label = "bar")
    Canvas(modifier.height(10.dp)) {
        val r = CornerRadius(4.dp.toPx())
        val path = Path().apply {
            addRoundRect(RoundRect(0f, 0f, (size.width * shown).coerceAtLeast(2.dp.toPx()), size.height, topRightCornerRadius = r, bottomRightCornerRadius = r))
        }
        drawPath(path, color)
    }
}

/**
 * A bar growing from a neutral middle line: to the right in [above] for a positive [fraction]
 * (-1..1), to the left in [below] for a negative one. The side says the direction, so the two
 * colors never carry it alone.
 */
@Composable
internal fun DivergingBar(fraction: Float, above: Color, below: Color, modifier: Modifier = Modifier) {
    val shown by animateFloatAsState(fraction.coerceIn(-1f, 1f), MaterialTheme.motionScheme.slowSpatialSpec(), label = "diverging")
    val middle = MaterialTheme.colorScheme.outline
    Canvas(modifier.height(10.dp)) {
        val center = size.width / 2
        val r = CornerRadius(4.dp.toPx())
        val zero = CornerRadius.Zero
        val reach = (center * kotlin.math.abs(shown)).coerceAtLeast(2.dp.toPx())
        val path = Path().apply {
            if (shown >= 0f) {
                addRoundRect(RoundRect(center, 0f, center + reach, size.height, zero, r, r, zero))
            } else {
                addRoundRect(RoundRect(center - reach, 0f, center, size.height, r, zero, zero, r))
            }
        }
        drawPath(path, if (shown >= 0f) above else below)
        drawLine(middle, Offset(center, -2.dp.toPx()), Offset(center, size.height + 2.dp.toPx()), 1.dp.toPx())
    }
}
