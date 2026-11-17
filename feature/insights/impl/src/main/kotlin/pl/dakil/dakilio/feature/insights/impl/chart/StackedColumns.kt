package pl.dakil.dakilio.feature.insights.impl.chart

import androidx.compose.animation.core.Animatable
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
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
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import pl.dakil.dakilio.core.designsystem.theme.Spacing
import pl.dakil.dakilio.core.designsystem.theme.decorativeSpec

/**
 * A whole split into ordered parts, slice after slice: every column is as tall as the plot and
 * shows the shares of its [colors] (the first at the bottom), with a surface gap between the parts.
 * A column whose counts are all zero stays empty.
 *
 * @param columns one list of counts per slice, each as long as [colors].
 */
@Composable
internal fun StackedColumns(
    columns: List<List<Int>>,
    colors: List<Color>,
    description: String,
    modifier: Modifier = Modifier,
    selected: Int? = null,
    onSelect: ((Int) -> Unit)? = null,
    startLabel: String? = null,
    endLabel: String? = null,
    height: Dp = 120.dp,
) {
    val scheme = MaterialTheme.colorScheme
    val slotFill = scheme.surfaceContainerHighest
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
            val barWidth = (slot - 2.dp.toPx()).coerceIn(1.dp.toPx(), 28.dp.toPx())
            // Thin columns can't spare the full 2dp between their parts.
            val gap = if (barWidth >= 6.dp.toPx()) 2.dp.toPx() else 1.dp.toPx()
            val radius = CornerRadius(minOf(4.dp.toPx(), barWidth / 2))
            selected?.takeIf { it in columns.indices }?.let { i ->
                drawRoundRect(slotFill, Offset(slot * i - 0f, 0f), Size(slot, size.height), CornerRadius(6.dp.toPx()))
            }
            columns.forEachIndexed { i, counts ->
                val total = counts.sum()
                if (total == 0) return@forEachIndexed
                val parts = counts.withIndex().filter { it.value > 0 }
                val usable = (size.height * grow.value - gap * (parts.size - 1)).coerceAtLeast(0f)
                val left = slot * i + (slot - barWidth) / 2
                var bottom = size.height
                parts.forEachIndexed { n, part ->
                    val partHeight = usable * part.value / total
                    val top = bottom - partHeight
                    val isTop = n == parts.lastIndex
                    val path = Path().apply {
                        addRoundRect(
                            RoundRect(
                                left = left, top = top, right = left + barWidth, bottom = bottom,
                                topLeftCornerRadius = if (isTop) radius else CornerRadius.Zero,
                                topRightCornerRadius = if (isTop) radius else CornerRadius.Zero,
                            ),
                        )
                    }
                    drawPath(path, colors.getOrElse(part.index) { Color.Unspecified })
                    bottom = top - gap
                }
            }
        }
        if (startLabel != null || endLabel != null) {
            Row(Modifier.fillMaxWidth().padding(top = Spacing.xs), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(startLabel.orEmpty(), style = MaterialTheme.typography.labelSmall, color = scheme.onSurfaceVariant)
                Text(endLabel.orEmpty(), style = MaterialTheme.typography.labelSmall, color = scheme.onSurfaceVariant)
            }
        }
    }
}
