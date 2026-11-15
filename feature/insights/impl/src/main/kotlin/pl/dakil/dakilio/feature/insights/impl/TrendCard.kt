package pl.dakil.dakilio.feature.insights.impl

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import java.time.LocalDate
import kotlin.math.roundToInt
import pl.dakil.dakilio.core.designsystem.component.rememberHaptics
import pl.dakil.dakilio.core.designsystem.theme.Spacing
import pl.dakil.dakilio.core.model.Mood
import pl.dakil.dakilio.core.ui.DakilioFormats
import pl.dakil.dakilio.core.ui.hintOrNull
import pl.dakil.dakilio.core.ui.mood.MoodBadge

private val PlotHeight = 148.dp
private val AxisFace = 16.dp

/** More points than this are drawn as a plain line, without a dot on each. */
private const val MAX_DOTTED_POINTS = 16

/**
 * The average mood over the selected period as one line. The y-axis is the mood scale itself, so
 * its ticks are the five faces; tapping or sliding along the plot reads out a single point.
 */
@Composable
internal fun TrendCard(trend: MoodTrend, moods: List<Mood>, today: LocalDate, formats: DakilioFormats) {
    InsightCard {
        val title = stringResource(R.string.insights_trend)
        CardTitle(
            title,
            stringResource(
                when (trend.bucket) {
                    TrendBucket.Day -> R.string.insights_trend_days
                    TrendBucket.Week -> R.string.insights_trend_weeks
                    TrendBucket.Month -> R.string.insights_trend_months
                },
            ),
        )
        if (!trend.hasMood) {
            CardEmptyText(stringResource(R.string.insights_no_mood))
            return@InsightCard
        }
        val points = trend.points
        var selected by remember(trend) { mutableIntStateOf(points.indexOfLast { it.averageMood != null }) }
        val haptics = rememberHaptics()
        val colors = MaterialTheme.colorScheme
        val line = colors.primary
        val grid = colors.outlineVariant
        val ring = colors.surfaceContainer
        val crosshair = colors.onSurfaceVariant
        val select = { index: Int ->
            if (index != selected) {
                selected = index
                haptics.tick()
            }
        }
        Row(Modifier.padding(top = Spacing.l)) {
            // The scale's faces, best on top: each sits on its gridline.
            Column(Modifier.height(PlotHeight), verticalArrangement = Arrangement.SpaceBetween) {
                moods.sortedByDescending { it.level }.forEach { MoodBadge(it, size = AxisFace) }
            }
            Canvas(
                Modifier
                    .padding(start = Spacing.s)
                    .weight(1f)
                    .height(PlotHeight)
                    .semantics { contentDescription = title }
                    .pointerInput(trend) {
                        detectTapGestures { select(nearestPoint(it.x, size.width.toFloat(), points.size, plotInset())) }
                    }
                    .pointerInput(trend) {
                        detectHorizontalDragGestures { change, _ ->
                            select(nearestPoint(change.position.x, size.width.toFloat(), points.size, plotInset()))
                        }
                    },
            ) {
                val inset = plotInset()
                val top = AxisFace.toPx() / 2
                val bottom = size.height - AxisFace.toPx() / 2
                fun x(index: Int) = pointX(index, size.width, points.size, inset)
                fun y(value: Float) = bottom - value * (bottom - top)

                // One hairline per mood level.
                repeat(5) { level ->
                    val gy = y(level / 4f)
                    drawLine(grid, Offset(0f, gy), Offset(size.width, gy), strokeWidth = 1.dp.toPx())
                }
                val data = points.mapIndexedNotNull { index, point -> point.averageMood?.let { Offset(x(index), y(it)) } }
                if (data.size > 1) {
                    val path = Path().apply {
                        moveTo(data.first().x, data.first().y)
                        data.drop(1).forEach { lineTo(it.x, it.y) }
                    }
                    // A wash under the line, then the line itself.
                    val area = Path().apply {
                        addPath(path)
                        lineTo(data.last().x, bottom)
                        lineTo(data.first().x, bottom)
                        close()
                    }
                    drawPath(area, line.copy(alpha = 0.1f))
                    drawPath(path, line, style = Stroke(2.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round))
                }
                if (data.size <= MAX_DOTTED_POINTS) {
                    data.forEach {
                        drawCircle(ring, 5.dp.toPx(), it)
                        drawCircle(line, 3.dp.toPx(), it)
                    }
                } else {
                    // Long series: only where the line ends.
                    drawCircle(ring, 6.dp.toPx(), data.last())
                    drawCircle(line, 4.dp.toPx(), data.last())
                }
                points.getOrNull(selected)?.let { point ->
                    val sx = x(selected)
                    drawLine(crosshair, Offset(sx, top), Offset(sx, bottom), strokeWidth = 1.dp.toPx())
                    point.averageMood?.let {
                        drawCircle(ring, 7.dp.toPx(), Offset(sx, y(it)))
                        drawCircle(line, 5.dp.toPx(), Offset(sx, y(it)))
                    }
                }
            }
        }
        // The ends of the time axis, under the plot (past the column of faces).
        Row(Modifier.padding(start = AxisFace + Spacing.s, top = Spacing.xs).fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            listOf(points.first(), points.last()).forEach { point ->
                Text(
                    if (trend.bucket == TrendBucket.Month) formats.monthYear(point.start) else formats.dayMonthShort(point.start),
                    style = MaterialTheme.typography.labelSmall,
                    color = colors.onSurfaceVariant,
                )
            }
        }
        points.getOrNull(selected)?.let { point ->
            val period = when {
                trend.bucket == TrendBucket.Month -> formats.monthYear(point.start)
                point.start == point.end -> formats.fullDate(point.start, today)
                else -> stringResource(R.string.insights_trend_span, formats.dayMonthShort(point.start), formats.dayMonthShort(point.end))
            }
            Text(
                periodCaption(period, point.entries, point.averageMood?.let { nearestMood(moods, it) }),
                style = MaterialTheme.typography.labelLarge,
                modifier = Modifier.padding(top = Spacing.m),
            )
        }
        hintOrNull(stringResource(R.string.insights_trend_hint))?.let {
            Text(it, style = MaterialTheme.typography.bodySmall, color = colors.onSurfaceVariant, modifier = Modifier.padding(top = Spacing.xs))
        }
    }
}

/** Room at both ends of the plot so the first and last dots aren't cut. */
private fun Density.plotInset(): Float = 8.dp.toPx()

/** The x of point [index] of [count], spread evenly over [width] minus [inset] at both ends. */
internal fun pointX(index: Int, width: Float, count: Int, inset: Float): Float =
    if (count <= 1) width / 2 else inset + index * (width - 2 * inset) / (count - 1)

/** The point nearest to [x]: the inverse of [pointX]. */
internal fun nearestPoint(x: Float, width: Float, count: Int, inset: Float): Int {
    if (count <= 1 || width <= 2 * inset) return 0
    return ((x - inset) / (width - 2 * inset) * (count - 1)).roundToInt().coerceIn(0, count - 1)
}
