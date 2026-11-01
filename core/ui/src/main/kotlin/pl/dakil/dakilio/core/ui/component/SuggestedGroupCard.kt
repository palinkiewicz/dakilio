package pl.dakil.dakilio.core.ui.component

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialShapes
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.graphics.shapes.Morph
import pl.dakil.dakilio.core.designsystem.component.MorphShape
import pl.dakil.dakilio.core.designsystem.component.pressScale
import pl.dakil.dakilio.core.designsystem.icon.DakilioIcon
import pl.dakil.dakilio.core.designsystem.icon.DakilioSymbols
import pl.dakil.dakilio.core.designsystem.theme.Spacing
import pl.dakil.dakilio.core.model.SuggestedGroup

private val checkMorph by lazy { Morph(MaterialShapes.Circle, MaterialShapes.Cookie9Sided) }

/** A ready-made tag group and all its tags, as a card the user ticks to keep (first-run intro). */
@Composable
fun SuggestedGroupCard(
    group: SuggestedGroup,
    selected: Boolean,
    onToggle: () -> Unit,
    shape: RoundedCornerShape,
    modifier: Modifier = Modifier,
) {
    val progress by animateFloatAsState(
        if (selected) 1f else 0f,
        MaterialTheme.motionScheme.defaultSpatialSpec(),
        label = "suggestedGroup",
    )
    val colors = MaterialTheme.colorScheme
    val interaction = remember { MutableInteractionSource() }
    Surface(
        shape = shape,
        color = lerp(colors.surfaceContainer, colors.secondaryContainer, progress),
        modifier = modifier
            .fillMaxWidth()
            .pressScale(interaction, 0.98f)
            .toggleable(value = selected, interactionSource = interaction, indication = null, role = Role.Checkbox, onValueChange = { onToggle() }),
    ) {
        SuggestedGroupContent(group) {
            // An empty circle that blooms into a ticked cookie.
            Surface(
                shape = MorphShape(checkMorph, progress),
                color = lerp(colors.surfaceContainerHighest, colors.primary, progress),
                modifier = Modifier.size(32.dp).graphicsLayer { rotationZ = progress * 20f },
            ) {
                Row(horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
                    DakilioIcon(
                        DakilioSymbols.Check,
                        contentDescription = null,
                        tint = colors.onPrimary,
                        modifier = Modifier.size(20.dp).graphicsLayer { alpha = progress; rotationZ = -progress * 20f },
                    )
                }
            }
        }
    }
}

/** A suggested group's name with [trailing] next to it, then its tags as pills. */
@Composable
fun SuggestedGroupContent(group: SuggestedGroup, modifier: Modifier = Modifier, trailing: @Composable () -> Unit) {
    Column(modifier.padding(start = Spacing.xl, end = Spacing.l, top = Spacing.l, bottom = Spacing.xl)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(group.name, style = MaterialTheme.typography.titleMediumEmphasized, modifier = Modifier.weight(1f))
            trailing()
        }
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(Spacing.xs),
            verticalArrangement = Arrangement.spacedBy(Spacing.xs),
            modifier = Modifier.padding(top = Spacing.s, end = Spacing.s),
        ) {
            group.tags.forEach { TagPill(it.label, it.iconKey) }
        }
    }
}
