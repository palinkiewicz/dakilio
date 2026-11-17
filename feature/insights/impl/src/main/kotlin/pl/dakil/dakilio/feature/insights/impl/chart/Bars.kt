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
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import kotlin.math.abs

private val BarHeight = 8.dp

/**
 * A measure as a pill on a rounded track, like the Material 3 Expressive linear progress: both ends
 * fully round, the track in the quiet container tone behind it. The smallest value still shows as a
 * dot, so "little" reads apart from "nothing".
 */
@Composable
internal fun SingleBar(fraction: Float, modifier: Modifier = Modifier, color: Color = MaterialTheme.colorScheme.primary) {
    val shown by animateFloatAsState(fraction.coerceIn(0f, 1f), MaterialTheme.motionScheme.slowSpatialSpec(), label = "bar")
    val track = MaterialTheme.colorScheme.surfaceContainerHighest
    Canvas(modifier.height(BarHeight)) {
        val round = CornerRadius(size.height / 2)
        drawRoundRect(track, cornerRadius = round)
        drawRoundRect(color, size = Size((size.width * shown).coerceAtLeast(size.height), size.height), cornerRadius = round)
    }
}

/**
 * A measure that can go either way, on the same rounded track: a pill growing from the middle to
 * the right in [above] for a positive [fraction] (-1..1), to the left in [below] for a negative one.
 * The side says the direction, so the two colors never carry it alone; a small mark keeps the
 * middle visible.
 */
@Composable
internal fun DivergingBar(fraction: Float, above: Color, below: Color, modifier: Modifier = Modifier) {
    val shown by animateFloatAsState(fraction.coerceIn(-1f, 1f), MaterialTheme.motionScheme.slowSpatialSpec(), label = "diverging")
    val track = MaterialTheme.colorScheme.surfaceContainerHighest
    val middle = MaterialTheme.colorScheme.outline
    Canvas(modifier.height(BarHeight)) {
        val round = CornerRadius(size.height / 2)
        val center = size.width / 2
        drawRoundRect(track, cornerRadius = round)
        val reach = (center * abs(shown)).coerceAtLeast(size.height)
        val left = if (shown >= 0f) center else center - reach
        drawRoundRect(if (shown >= 0f) above else below, Offset(left, 0f), Size(reach, size.height), round)
        drawLine(middle, Offset(center, -3.dp.toPx()), Offset(center, size.height + 3.dp.toPx()), 2.dp.toPx(), cap = androidx.compose.ui.graphics.StrokeCap.Round)
    }
}
