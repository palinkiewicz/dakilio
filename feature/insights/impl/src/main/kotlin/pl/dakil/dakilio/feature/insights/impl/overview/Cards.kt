package pl.dakil.dakilio.feature.insights.impl.overview

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.layout.layout
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.offset
import androidx.compose.ui.unit.dp
import pl.dakil.dakilio.core.designsystem.component.RollingNumber
import pl.dakil.dakilio.core.designsystem.icon.DakilioIcon
import pl.dakil.dakilio.core.designsystem.icon.DakilioSymbols
import pl.dakil.dakilio.core.designsystem.icon.symbolForKey
import pl.dakil.dakilio.core.designsystem.theme.Spacing
import pl.dakil.dakilio.core.model.Mood
import pl.dakil.dakilio.core.model.Tag
import pl.dakil.dakilio.core.ui.color.tagColors
import pl.dakil.dakilio.core.ui.mood.MoodBadge
import pl.dakil.dakilio.feature.insights.impl.R

internal val CardShape = RoundedCornerShape(28.dp)

private val RowBleed = 8.dp

/**
 * Makes a row inside a card a button. Its rounded ripple reaches [RowBleed] past the row on both
 * sides, so the row's content stays in line with the rest of the card and the rounding never clips
 * text at the row's edge. Without [onClick] the row is left as it is.
 */
internal fun Modifier.cardRowClick(onClick: (() -> Unit)?): Modifier {
    onClick ?: return this
    return this
        .layout { measurable, constraints ->
            val bleed = RowBleed.roundToPx()
            val placeable = measurable.measure(constraints.offset(horizontal = 2 * bleed))
            layout(placeable.width - 2 * bleed, placeable.height) { placeable.place(-bleed, 0) }
        }
        .clip(RoundedCornerShape(12.dp))
        .clickable(role = Role.Button, onClick = onClick)
        .padding(horizontal = RowBleed, vertical = Spacing.xs)
}

/** One topic of the overview (or a section of a deep-dive screen) on a tonal card. */
@Composable
internal fun InsightCard(modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    Surface(shape = CardShape, color = MaterialTheme.colorScheme.surfaceContainer, modifier = modifier.fillMaxWidth()) {
        Column(Modifier.padding(Spacing.xl), content = content)
    }
}

/**
 * A card's heading. With [onMore] the whole heading is a button to the screen behind the card, marked
 * by a chevron.
 */
@Composable
internal fun CardTitle(title: String, subtitle: String? = null, onMore: (() -> Unit)? = null) {
    val colors = MaterialTheme.colorScheme
    Row(
        modifier = Modifier.fillMaxWidth().cardRowClick(onMore),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleMediumEmphasized)
            subtitle?.let {
                Text(it, style = MaterialTheme.typography.bodySmall, color = colors.onSurfaceVariant, modifier = Modifier.padding(top = Spacing.xxs))
            }
        }
        if (onMore != null) {
            Box(Modifier.size(32.dp).background(colors.surfaceContainerHighest, CircleShape), contentAlignment = Alignment.Center) {
                DakilioIcon(DakilioSymbols.ChevronRight, contentDescription = null, tint = colors.onSurfaceVariant, modifier = Modifier.size(20.dp))
            }
        }
    }
}

/** A line of muted text inside a card: an empty state, or a note under a chart. */
@Composable
internal fun CardNote(text: String, modifier: Modifier = Modifier) {
    Text(text, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = modifier)
}

/** What the touched point of a chart says, under the chart. */
@Composable
internal fun ChartCaption(text: String, modifier: Modifier = Modifier) {
    Text(text, style = MaterialTheme.typography.labelLarge, modifier = modifier)
}

/**
 * A headline number with its label.
 *
 * @param detail a small note next to the value ("of 30").
 */
@Composable
internal fun StatTile(
    label: String,
    value: String,
    shape: Shape,
    modifier: Modifier = Modifier,
    detail: String? = null,
    color: Color = MaterialTheme.colorScheme.surfaceContainer,
) {
    Surface(shape = shape, color = color, modifier = modifier.fillMaxHeight()) {
        Column(Modifier.padding(Spacing.l)) {
            Row(verticalAlignment = Alignment.Bottom) {
                RollingNumber(value, style = MaterialTheme.typography.headlineMediumEmphasized)
                detail?.let {
                    Text(
                        it,
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(start = Spacing.xs, bottom = Spacing.xs),
                        maxLines = 1,
                    )
                }
            }
            Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 2)
        }
    }
}

/** A tag's icon on a small round of its color. */
@Composable
internal fun TagIcon(tag: Tag, modifier: Modifier = Modifier, size: Dp = 36.dp) {
    val colors = tagColors(tag.color)
    Box(
        modifier.size(size).background(colors?.soft ?: MaterialTheme.colorScheme.secondaryContainer, CircleShape),
        contentAlignment = Alignment.Center,
    ) {
        DakilioIcon(
            symbolForKey(tag.iconKey),
            contentDescription = null,
            tint = colors?.accent ?: MaterialTheme.colorScheme.onSecondaryContainer,
            modifier = Modifier.size(size * 0.55f),
        )
    }
}

/** The mood faces with their names, so a chart's colors are never the only key; plus the "no entries" swatch. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun MoodLegend(moods: List<Mood>, modifier: Modifier = Modifier, withNoEntries: Boolean = true) {
    val colors = MaterialTheme.colorScheme
    FlowRow(
        modifier.clearAndSetSemantics { },
        horizontalArrangement = Arrangement.spacedBy(Spacing.m),
        verticalArrangement = Arrangement.spacedBy(Spacing.xs),
    ) {
        moods.sortedBy { it.level }.forEach { mood ->
            Row(verticalAlignment = Alignment.CenterVertically) {
                MoodBadge(mood, size = 16.dp)
                Text(
                    mood.label,
                    style = MaterialTheme.typography.labelSmall,
                    color = colors.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(start = Spacing.xs),
                )
            }
        }
        if (withNoEntries) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(14.dp).background(colors.surfaceContainerHighest, RoundedCornerShape(4.dp)))
                Text(
                    stringResource(R.string.insights_no_entries_day),
                    style = MaterialTheme.typography.labelSmall,
                    color = colors.onSurfaceVariant,
                    modifier = Modifier.padding(start = Spacing.xs),
                )
            }
        }
    }
}
