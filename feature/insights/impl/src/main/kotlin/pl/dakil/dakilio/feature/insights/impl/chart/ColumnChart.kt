package pl.dakil.dakilio.feature.insights.impl.chart

import androidx.compose.animation.core.Animatable
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import pl.dakil.dakilio.core.designsystem.theme.Spacing
import pl.dakil.dakilio.core.designsystem.theme.decorativeSpec

/**
 * One column: how tall it is (0..1 of the plot; `null` = nothing to show) and the label under it
 * (`null` leaves the slot's label empty, for charts that only label some columns).
 */
@Immutable
internal data class ChartColumn(val fraction: Float?, val label: String? = null)

/**
 * Single-series columns on one baseline: thin, 2dp apart, rounded only at the data end.
 *
 * @param emphasized the one column that's the point (the best weekday); it takes the accent and the
 *   rest step back. Without one, every column is the accent.
 * @param target a hairline across the plot at this fraction (a habit's weekly target).
 * @param color the accent; the primary color unless the chart belongs to something with its own.
 */
@Composable
internal fun ColumnChart(
    columns: List<ChartColumn>,
    description: String,
    modifier: Modifier = Modifier,
    emphasized: Int? = null,
    selected: Int? = null,
    onSelect: ((Int) -> Unit)? = null,
    target: Float? = null,
    height: Dp = 104.dp,
    color: Color = MaterialTheme.colorScheme.primary,
    /** Several columns that are the point (weeks on target); like [emphasized], the rest step back. */
    strong: Set<Int>? = null,
) {
    val colors = MaterialTheme.colorScheme
    val rest = if (emphasized == null && strong == null) color else colors.secondaryContainer
    fun isStrong(i: Int) = (emphasized == null && strong == null) || i == emphasized || (strong != null && i in strong)
    val slotFill = colors.surfaceContainerHighest
    val baseline = colors.outlineVariant
    val targetLine = colors.outline

    val grow = remember { Animatable(1f) }
    val growSpec = decorativeSpec(MaterialTheme.motionScheme.slowSpatialSpec<Float>())
    LaunchedEffect(columns) {
        grow.snapTo(0f)
        grow.animateTo(1f, growSpec)
    }

    Column(modifier.clearAndSetSemantics { contentDescription = description }) {
        val scrub = if (onSelect == null) Modifier else Modifier.chartScrub { position, size -> onSelect(slotAt(position.x, size.width.toFloat(), columns.size)) }
        Canvas(Modifier.fillMaxWidth().height(height).then(scrub)) {
            if (columns.isEmpty()) return@Canvas
            val slot = size.width / columns.size
            val gap = 2.dp.toPx()
            val barWidth = (slot - gap).coerceIn(1.dp.toPx(), 28.dp.toPx())
            val radius = minOf(4.dp.toPx(), barWidth / 2)
            val plot = size.height - 1.dp.toPx()
            selected?.takeIf { it in columns.indices }?.let { i ->
                drawRoundRect(slotFill, Offset(slot * i, 0f), Size(slot, size.height), CornerRadius(6.dp.toPx()))
            }
            columns.forEachIndexed { i, column ->
                val fraction = column.fraction ?: return@forEachIndexed
                // A value of zero still leaves a stub, so "none" reads apart from "no data".
                val barHeight = (plot * fraction.coerceIn(0f, 1f) * grow.value).coerceAtLeast(2.dp.toPx())
                val left = slot * i + (slot - barWidth) / 2
                val path = Path().apply {
                    addRoundRect(
                        RoundRect(
                            left = left, top = plot - barHeight, right = left + barWidth, bottom = plot,
                            topLeftCornerRadius = CornerRadius(radius),
                            topRightCornerRadius = CornerRadius(radius),
                        ),
                    )
                }
                drawPath(path, if (isStrong(i)) color else rest)
            }
            drawLine(baseline, Offset(0f, plot), Offset(size.width, plot), 1.dp.toPx())
            target?.let {
                val y = plot - plot * it.coerceIn(0f, 1f)
                drawLine(targetLine, Offset(0f, y), Offset(size.width, y), 1.dp.toPx())
            }
        }
        if (columns.any { it.label != null }) {
            Row(Modifier.fillMaxWidth().padding(top = Spacing.xs)) {
                columns.forEachIndexed { i, column ->
                    Text(
                        column.label.orEmpty(),
                        style = if (i == emphasized) MaterialTheme.typography.labelSmallEmphasized else MaterialTheme.typography.labelSmall,
                        color = if (i == emphasized || i == selected) colors.onSurface else colors.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                        maxLines = 1,
                        softWrap = false,
                        overflow = TextOverflow.Visible,
                        modifier = Modifier.weight(1f),
                    )
                }
            }
        }
    }
}
