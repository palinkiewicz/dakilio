package pl.dakil.dakilio.feature.insights.impl.chart

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import pl.dakil.dakilio.core.model.HabitWeek

/**
 * A habit's weeks side by side, each a small pill filled as far as the week got towards its target:
 * full in [color] when on target. A week before the habit began is left as a faint track, and the
 * week in progress is outlined. The row describes itself through its parent's semantics.
 *
 * @param weeks one slot per week of the period, `null` where the habit hadn't started.
 */
@Composable
internal fun WeekPills(weeks: List<HabitWeek?>, color: Color, modifier: Modifier = Modifier, height: Dp = 24.dp) {
    val track = MaterialTheme.colorScheme.surfaceContainerHighest
    val partial = color.copy(alpha = 0.45f)
    val outline = MaterialTheme.colorScheme.outline
    Canvas(modifier.fillMaxWidth().height(height).clearAndSetSemantics { }) {
        if (weeks.isEmpty()) return@Canvas
        // A steady rhythm from the start: a few weeks sit together instead of spreading out.
        val slot = minOf(size.width / weeks.size, 22.dp.toPx())
        val gap = if (slot > 10.dp.toPx()) 6.dp.toPx() else 2.dp.toPx()
        val width = slot - gap
        val radius = CornerRadius(width / 2)
        weeks.forEachIndexed { i, week ->
            val left = slot * i
            if (week == null) {
                drawRoundRect(track.copy(alpha = 0.4f), Offset(left, 0f), Size(width, size.height), radius)
                return@forEachIndexed
            }
            drawRoundRect(track, Offset(left, 0f), Size(width, size.height), radius)
            val fraction = (week.days.toFloat() / week.target).coerceIn(0f, 1f)
            if (fraction > 0f) {
                // A sliver still shows as a round dot at the bottom.
                val filled = (size.height * fraction).coerceAtLeast(width)
                drawRoundRect(if (week.met) color else partial, Offset(left, size.height - filled), Size(width, filled), radius)
            }
            if (week.inProgress) {
                drawRoundRect(outline, Offset(left, 0f), Size(width, size.height), radius, style = Stroke(1.5.dp.toPx()))
            }
        }
    }
}
