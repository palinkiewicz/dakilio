package pl.dakil.dakilio.feature.insights.impl

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialShapes
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.graphics.shapes.RoundedPolygon
import java.text.NumberFormat
import pl.dakil.dakilio.core.designsystem.component.PolygonShape
import pl.dakil.dakilio.core.designsystem.icon.DakilioSymbol
import pl.dakil.dakilio.core.designsystem.icon.DakilioSymbols
import pl.dakil.dakilio.core.model.Milestone
import pl.dakil.dakilio.core.model.MilestoneKind

/** Each kind of milestone stamps with its own shape. */
internal val MilestoneKind.polygon: RoundedPolygon
    get() = when (this) {
        MilestoneKind.Streak -> MaterialShapes.Sunny
        MilestoneKind.Entries -> MaterialShapes.Cookie9Sided
        MilestoneKind.Days -> MaterialShapes.Cookie12Sided
        MilestoneKind.Photos -> MaterialShapes.Clover8Leaf
        MilestoneKind.Words -> MaterialShapes.Flower
    }

internal val MilestoneKind.symbol: DakilioSymbol
    get() = when (this) {
        MilestoneKind.Streak -> DakilioSymbols.LocalFireDepartment
        MilestoneKind.Entries -> DakilioSymbols.EditNote
        MilestoneKind.Days -> DakilioSymbols.CalendarToday
        MilestoneKind.Photos -> DakilioSymbols.PhotoLibrary
        MilestoneKind.Words -> DakilioSymbols.Notes
    }

@Composable
internal fun MilestoneKind.title(): String = stringResource(
    when (this) {
        MilestoneKind.Streak -> R.string.milestones_kind_streak
        MilestoneKind.Entries -> R.string.milestones_kind_entries
        MilestoneKind.Days -> R.string.milestones_kind_days
        MilestoneKind.Photos -> R.string.milestones_kind_photos
        MilestoneKind.Words -> R.string.milestones_kind_words
    },
)

/** What a stamp stands for, in words: "7 days in a row", "1,000 words". */
@Composable
internal fun Milestone.label(): String = pluralStringResource(
    when (kind) {
        MilestoneKind.Streak -> R.plurals.milestones_streak
        MilestoneKind.Entries -> R.plurals.milestones_entries
        MilestoneKind.Days -> R.plurals.milestones_days
        MilestoneKind.Photos -> R.plurals.milestones_photos
        MilestoneKind.Words -> R.plurals.milestones_words
    },
    target,
    remember(target) { NumberFormat.getIntegerInstance().format(target) },
)

/** The figure printed on a stamp: the target, shortened from a thousand up (1k, 25k). */
internal fun stampFigure(target: Int): String = when {
    target >= 1_000 && target % 1_000 == 0 -> "${target / 1_000}k"
    else -> target.toString()
}

/** How far each stamp is turned, like a stamp pressed by hand: a few degrees either way, fixed per stamp. */
internal fun stampTilt(milestone: Milestone): Float = ((milestone.kind.ordinal * 7 + milestone.target.toString().length * 5 + milestone.target % 7) % 13 - 6).toFloat()

/**
 * A milestone's stamp: its kind's shape with the target on it. Collected ones are inked in the
 * primary container (the newest of a kind in the primary itself); the ones ahead are a faint outline of the
 * same shape.
 */
@Composable
internal fun Stamp(
    milestone: Milestone,
    reached: Boolean,
    modifier: Modifier = Modifier,
    size: Dp = 72.dp,
    emphasized: Boolean = false,
) {
    val colors = MaterialTheme.colorScheme
    val shape = remember(milestone.kind) { PolygonShape(milestone.kind.polygon) }
    val container: Color
    val content: Color
    when {
        reached && emphasized -> {
            container = colors.primary
            content = colors.onPrimary
        }
        reached -> {
            container = colors.primaryContainer
            content = colors.onPrimaryContainer
        }
        else -> {
            container = colors.surfaceContainerHighest
            content = colors.onSurfaceVariant.copy(alpha = 0.6f)
        }
    }
    Box(modifier.size(size), contentAlignment = Alignment.Center) {
        Box(Modifier.matchParentSize().graphicsLayer { rotationZ = stampTilt(milestone) }.background(container, shape))
        Text(
            stampFigure(milestone.target),
            style = if (size >= 64.dp) MaterialTheme.typography.titleLargeEmphasized else MaterialTheme.typography.labelLargeEmphasized,
            color = content,
            maxLines = 1,
        )
    }
}
