package pl.dakil.dakilio.feature.insights.impl

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import kotlin.math.abs
import kotlin.math.roundToInt
import pl.dakil.dakilio.core.designsystem.icon.DakilioIcon
import pl.dakil.dakilio.core.designsystem.icon.symbolForKey
import pl.dakil.dakilio.core.designsystem.theme.Spacing
import pl.dakil.dakilio.core.model.Mood
import pl.dakil.dakilio.core.ui.mood.levelColors
import pl.dakil.dakilio.core.ui.mood.moodColors

private val RowHeight = 40.dp
private val BarThickness = 10.dp

/** The mood levels whose colors stand for "brighter" and "heavier": a cool and a warm pole of the scale. */
private const val LIFT_LEVEL = 5
private const val DIP_LEVEL = 2

/**
 * Which tags come with brighter or heavier entries: diverging bars around the average of the
 * entries without the tag. The two poles borrow the mood scale's own colors, and each bar's
 * direction and signed figure say the same as its color.
 */
@Composable
internal fun TagInfluenceCard(influences: TagInfluences, moods: List<Mood>) {
    InsightCard {
        CardTitle(stringResource(R.string.insights_influence), stringResource(R.string.insights_influence_body))
        if (influences.isEmpty) {
            CardEmptyText(stringResource(R.string.insights_influence_empty))
            return@InsightCard
        }
        val lift = moods.firstOrNull { it.level == LIFT_LEVEL }?.let { moodColors(it) }?.container ?: levelColors(LIFT_LEVEL).container
        val dip = moods.firstOrNull { it.level == DIP_LEVEL }?.let { moodColors(it) }?.container ?: levelColors(DIP_LEVEL).container
        // Brightest first, down to the heaviest.
        val rows = influences.lifts + influences.dips.reversed()
        val scale = rows.maxOf { abs(it.delta) }
        Row(Modifier.padding(top = Spacing.m), horizontalArrangement = Arrangement.spacedBy(Spacing.l)) {
            LegendItem(lift, stringResource(R.string.insights_influence_brighter))
            LegendItem(dip, stringResource(R.string.insights_influence_heavier))
        }
        Column(Modifier.padding(top = Spacing.s)) {
            rows.forEach { item -> InfluenceRow(item, scale, lift, dip) }
        }
    }
}

@Composable
private fun LegendItem(color: Color, label: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(12.dp).background(color, RoundedCornerShape(4.dp)))
        Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(start = Spacing.xs))
    }
}

@Composable
private fun InfluenceRow(item: TagInfluence, scale: Float, lift: Color, dip: Color) {
    val colors = MaterialTheme.colorScheme
    val points = (item.delta * 100).roundToInt()
    val figure = if (points > 0) "+$points" else points.toString()
    val entries = pluralStringResource(R.plurals.insights_heat_entries, item.entries, item.entries)
    val fraction by animateFloatAsState(item.delta / scale, MaterialTheme.motionScheme.slowSpatialSpec(), label = "influenceBar")
    val axis = colors.outlineVariant
    Row(
        Modifier.fillMaxWidth().height(RowHeight).clearAndSetSemantics { contentDescription = "${item.tag.label}, $figure, $entries" },
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(28.dp).background(colors.secondaryContainer, CircleShape), contentAlignment = Alignment.Center) {
            DakilioIcon(symbolForKey(item.tag.iconKey), contentDescription = null, tint = colors.onSecondaryContainer, modifier = Modifier.size(16.dp))
        }
        Text(
            item.tag.label,
            style = MaterialTheme.typography.labelLarge,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(start = Spacing.s).weight(0.42f),
        )
        // The rows touch, so their axis segments join into one center line.
        Canvas(Modifier.weight(0.58f).fillMaxHeight()) {
            val center = size.width / 2
            drawLine(axis, Offset(center, 0f), Offset(center, size.height), strokeWidth = 1.dp.toPx())
            val length = abs(fraction) * (center - 2.dp.toPx())
            if (length > 0.5f) {
                val top = (size.height - BarThickness.toPx()) / 2
                val tip = CornerRadius(4.dp.toPx())
                val square = CornerRadius.Zero
                val rect = if (fraction > 0) {
                    RoundRect(center, top, center + length, top + BarThickness.toPx(), topLeftCornerRadius = square, bottomLeftCornerRadius = square, topRightCornerRadius = tip, bottomRightCornerRadius = tip)
                } else {
                    RoundRect(center - length, top, center, top + BarThickness.toPx(), topLeftCornerRadius = tip, bottomLeftCornerRadius = tip, topRightCornerRadius = square, bottomRightCornerRadius = square)
                }
                drawPath(Path().apply { addRoundRect(rect) }, if (fraction > 0) lift else dip)
            }
        }
        Text(
            figure,
            style = MaterialTheme.typography.labelLarge,
            color = colors.onSurfaceVariant,
            textAlign = TextAlign.End,
            maxLines = 1,
            modifier = Modifier.padding(start = Spacing.s).widthIn(min = 32.dp),
        )
    }
}
