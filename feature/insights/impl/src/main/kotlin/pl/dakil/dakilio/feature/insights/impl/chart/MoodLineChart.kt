package pl.dakil.dakilio.feature.insights.impl.chart

import androidx.compose.animation.core.Animatable
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlin.math.roundToInt
import pl.dakil.dakilio.core.designsystem.theme.Spacing
import pl.dakil.dakilio.core.designsystem.theme.decorativeSpec
import pl.dakil.dakilio.core.model.MOOD_LEVELS
import pl.dakil.dakilio.core.model.Mood
import pl.dakil.dakilio.core.ui.mood.MoodBadge
import pl.dakil.dakilio.core.ui.mood.moodColors

private val FaceSize = 16.dp
private val EdgePadding = 8.dp

/**
 * Mood over time on the 1–5 scale, with the level faces as the y-axis.
 *
 * [values] has a point per slice (`null` = no mood there), joined by a line that breaks over gaps.
 * Dots wear their level's color; their height against the faces says the same thing without it.
 *
 * @param comparison another period's values, drawn as a lighter line of the same hue stretched over
 *   the same width.
 * @param moods one primary per level.
 */
@Composable
internal fun MoodLineChart(
    values: List<Float?>,
    moods: List<Mood>,
    description: String,
    modifier: Modifier = Modifier,
    comparison: List<Float?>? = null,
    selected: Int? = null,
    onSelect: ((Int) -> Unit)? = null,
    startLabel: String? = null,
    endLabel: String? = null,
    height: Dp = 148.dp,
) {
    val colors = MaterialTheme.colorScheme
    val byLevel = moods.sortedBy { it.level }
    val levelColors = byLevel.map { moodColors(it).container }
    val line = colors.primary
    val faint = colors.primary.copy(alpha = 0.32f)
    val grid = chartGridColor()
    val ring = colors.surfaceContainer
    val cursor = colors.outline
    val showDots = values.size <= 62
    val edgePx = with(LocalDensity.current) { EdgePadding.toPx() }

    // The line draws itself in from the left when the data changes (decorative).
    val reveal = remember { Animatable(1f) }
    val revealSpec = decorativeSpec(MaterialTheme.motionScheme.slowEffectsSpec<Float>())
    LaunchedEffect(values) {
        reveal.snapTo(0f)
        reveal.animateTo(1f, revealSpec)
    }

    Column(modifier.clearAndSetSemantics { contentDescription = description }) {
        Row(Modifier.fillMaxWidth().height(height)) {
            // The y-axis: best on top.
            Column(Modifier.fillMaxHeight(), verticalArrangement = Arrangement.SpaceBetween) {
                byLevel.asReversed().forEach { MoodBadge(it, size = FaceSize) }
            }
            val scrub = if (onSelect == null) {
                Modifier
            } else {
                Modifier.chartScrub { position, size ->
                    onSelect(nearestPoint(position.x - edgePx, size.width - 2 * edgePx, values.size))
                }
            }
            Canvas(Modifier.padding(start = Spacing.s).weight(1f).fillMaxHeight().then(scrub)) {
                val pad = EdgePadding.toPx()
                fun x(i: Int, count: Int) = if (count <= 1) size.width / 2 else pad + (size.width - 2 * pad) * i / (count - 1)
                fun y(level: Float) = pad + (MOOD_LEVELS - level) / (MOOD_LEVELS - 1) * (size.height - 2 * pad)

                for (level in 1..MOOD_LEVELS) {
                    drawLine(grid, Offset(0f, y(level.toFloat())), Offset(size.width, y(level.toFloat())), 1.dp.toPx())
                }
                clipRect(right = size.width * reveal.value) {
                    comparison?.let { drawSeries(it, faint, 2.dp.toPx()) { i -> Offset(x(i, it.size), y(it[i]!!)) } }
                    drawSeries(values, line, 2.dp.toPx()) { i -> Offset(x(i, values.size), y(values[i]!!)) }
                    if (showDots) {
                        values.forEachIndexed { i, value ->
                            if (value == null) return@forEachIndexed
                            val center = Offset(x(i, values.size), y(value))
                            drawCircle(ring, 5.5.dp.toPx(), center)
                            drawCircle(levelColors.forLevel(value) ?: line, 3.5.dp.toPx(), center)
                        }
                    }
                }
                selected?.takeIf { it in values.indices }?.let { i ->
                    val cx = x(i, values.size)
                    drawLine(cursor, Offset(cx, 0f), Offset(cx, size.height), 1.dp.toPx())
                    values[i]?.let { value ->
                        drawCircle(ring, 8.dp.toPx(), Offset(cx, y(value)))
                        drawCircle(levelColors.forLevel(value) ?: line, 6.dp.toPx(), Offset(cx, y(value)))
                    }
                }
            }
        }
        if (startLabel != null || endLabel != null) {
            Row(
                Modifier.fillMaxWidth().padding(start = FaceSize + Spacing.s, top = Spacing.xs),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text(startLabel.orEmpty(), style = MaterialTheme.typography.labelSmall, color = colors.onSurfaceVariant)
                Text(endLabel.orEmpty(), style = MaterialTheme.typography.labelSmall, color = colors.onSurfaceVariant)
            }
        }
    }
}

/** The color of the level nearest to [value]. */
private fun List<Color>.forLevel(value: Float): Color? = getOrNull((value.roundToInt() - 1).coerceIn(0, MOOD_LEVELS - 1))

/** A polyline through the non-null [values], broken wherever one is missing. */
private fun DrawScope.drawSeries(values: List<Float?>, color: Color, width: Float, point: (Int) -> Offset) {
    val path = Path()
    var drawing = false
    values.forEachIndexed { i, value ->
        if (value == null) {
            drawing = false
            return@forEachIndexed
        }
        val p = point(i)
        if (drawing) path.lineTo(p.x, p.y) else path.moveTo(p.x, p.y)
        drawing = true
    }
    drawPath(path, color, style = Stroke(width, cap = StrokeCap.Round, join = StrokeJoin.Round))
}
