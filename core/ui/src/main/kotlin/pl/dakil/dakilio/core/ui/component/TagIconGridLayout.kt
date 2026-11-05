package pl.dakil.dakilio.core.ui.component

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.dp
import pl.dakil.dakilio.core.designsystem.theme.Spacing

private val IconCellMinWidth = 64.dp

/**
 * The Daylio-style tag grid: each child gets an even column (at least 5 per row), and a row that
 * isn't full (the last one, or the only one of a small group) is centered on the same column pitch.
 * Used by the entry editor's tag picker and the Tags screen, so both look the same.
 */
@Composable
fun TagIconGridLayout(modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    // A plain Layout rather than BoxWithConstraints: subcomposing during measure misbehaves inside
    // the editor's container transform (lookahead), making groups fade in late.
    Layout(content = content, modifier = modifier.fillMaxWidth()) { measurables, constraints ->
        val width = constraints.maxWidth
        val columns = maxOf(5, width / IconCellMinWidth.roundToPx())
        val cell = width / columns
        val gap = Spacing.s.roundToPx()
        val rows = measurables.map { it.measure(Constraints.fixedWidth(cell)) }.chunked(columns)
        val rowHeights = rows.map { row -> row.maxOf { it.height } }
        val height = rowHeights.sum() + gap * (rows.size - 1).coerceAtLeast(0)
        layout(width, height) {
            var y = 0
            rows.forEachIndexed { r, row ->
                // Centers a short row on the same column pitch, plus the width left over by the division.
                val start = ((columns - row.size) * cell + width - columns * cell) / 2
                row.forEachIndexed { c, placeable -> placeable.placeRelative(start + c * cell, y) }
                y += rowHeights[r] + gap
            }
        }
    }
}
