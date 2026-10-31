package pl.dakil.dakilio.feature.entry.impl

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Badge
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import pl.dakil.dakilio.core.designsystem.component.pressScale
import pl.dakil.dakilio.core.designsystem.component.rememberHaptics
import pl.dakil.dakilio.core.designsystem.icon.DakilioIcon
import pl.dakil.dakilio.core.designsystem.icon.DakilioSymbols
import pl.dakil.dakilio.core.designsystem.icon.symbolForKey
import pl.dakil.dakilio.core.designsystem.theme.Spacing
import pl.dakil.dakilio.core.model.Tag
import pl.dakil.dakilio.core.model.TagGroup

/**
 * Tag groups suggested by the selected diaries are laid out open; the rest wait collapsed under
 * "More groups" (with a count of what's selected inside), so nothing is ever out of reach.
 */
@Composable
internal fun TagStep(
    suggested: List<TagGroup>,
    others: List<TagGroup>,
    selectedIds: Set<Long>,
    onToggle: (Long) -> Unit,
    onManageTags: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var expanded by rememberSaveable { mutableStateOf(emptySet<Long>()) }
    LazyColumn(
        modifier = modifier,
        contentPadding = PaddingValues(horizontal = Spacing.gutter, vertical = Spacing.s),
        verticalArrangement = Arrangement.spacedBy(Spacing.s),
    ) {
        item(key = "header") {
            StepHeader(stringResource(R.string.entry_tags_title), stringResource(R.string.entry_tags_body))
        }
        tagGroupItems(
            suggested = suggested,
            others = others,
            selectedIds = selectedIds,
            expanded = expanded,
            onExpandedChange = { id, open -> expanded = if (open) expanded + id else expanded - id },
            onToggle = onToggle,
            onManageTags = onManageTags,
        )
    }
}

/**
 * The tag groups as lazy items: suggested groups open, the rest collapsed under "More groups",
 * then a Manage tags button. [expanded] holds the ids of collapsed groups the user opened.
 */
internal fun LazyListScope.tagGroupItems(
    suggested: List<TagGroup>,
    others: List<TagGroup>,
    selectedIds: Set<Long>,
    expanded: Set<Long>,
    onExpandedChange: (groupId: Long, expanded: Boolean) -> Unit,
    onToggle: (Long) -> Unit,
    onManageTags: () -> Unit,
    /** Side padding per item, for lists whose content padding doesn't already provide it. */
    horizontalPadding: Dp = 0.dp,
) {
    val sides = Modifier.padding(horizontal = horizontalPadding)
    if (suggested.isEmpty() && others.isEmpty()) {
        item(key = "empty") {
            Text(stringResource(R.string.entry_no_tags), color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = sides)
        }
    }
    items(suggested, key = { "s-${it.id}" }) { group ->
        Column(Modifier.animateItem().then(sides).padding(bottom = Spacing.s)) {
            Text(
                group.name,
                style = MaterialTheme.typography.labelLargeEmphasized,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(start = Spacing.xs, bottom = Spacing.s),
            )
            TagChips(group.tags, selectedIds, onToggle)
        }
    }
    if (others.isNotEmpty()) {
        item(key = "more-header") {
            Text(
                stringResource(R.string.entry_more_groups),
                style = MaterialTheme.typography.titleSmallEmphasized,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.animateItem().then(sides).padding(start = Spacing.xs, top = Spacing.m),
            )
        }
        items(others, key = { "o-${it.id}" }) { group ->
            CollapsedGroup(
                group = group,
                selectedIds = selectedIds,
                expanded = group.id in expanded,
                onExpandedChange = { onExpandedChange(group.id, it) },
                onToggle = onToggle,
                modifier = Modifier.animateItem().then(sides),
            )
        }
    }
    item(key = "manage") {
        TextButton(onClick = onManageTags, modifier = Modifier.animateItem().then(sides)) {
            DakilioIcon(DakilioSymbols.Tune, contentDescription = null, modifier = Modifier.size(18.dp))
            Text(stringResource(R.string.entry_manage_tags), modifier = Modifier.padding(start = Spacing.s))
        }
    }
}

@Composable
private fun CollapsedGroup(
    group: TagGroup,
    selectedIds: Set<Long>,
    expanded: Boolean,
    onExpandedChange: (Boolean) -> Unit,
    onToggle: (Long) -> Unit,
    modifier: Modifier = Modifier,
) {
    val haptics = rememberHaptics()
    val selectedCount = group.tags.count { it.id in selectedIds }
    val rotation by animateFloatAsState(if (expanded) 180f else 0f, MaterialTheme.motionScheme.defaultSpatialSpec(), label = "groupChevron")
    Surface(
        shape = RoundedCornerShape(24.dp),
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        modifier = modifier.fillMaxWidth(),
    ) {
        Column {
            Surface(
                onClick = {
                    haptics.tick()
                    onExpandedChange(!expanded)
                },
                color = MaterialTheme.colorScheme.surfaceContainerLow,
                shape = RoundedCornerShape(24.dp),
            ) {
                Row(
                    Modifier.fillMaxWidth().heightIn(min = 56.dp).padding(horizontal = Spacing.l),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(group.name, style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f))
                    if (selectedCount > 0) {
                        Badge(containerColor = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(end = Spacing.s)) {
                            Text(selectedCount.toString())
                        }
                    }
                    DakilioIcon(DakilioSymbols.ExpandMore, contentDescription = null, modifier = Modifier.graphicsLayer { rotationZ = rotation })
                }
            }
            AnimatedVisibility(
                visible = expanded,
                enter = expandVertically(MaterialTheme.motionScheme.defaultSpatialSpec()) + fadeIn(),
                exit = shrinkVertically(MaterialTheme.motionScheme.fastSpatialSpec()) + fadeOut(),
            ) {
                TagChips(group.tags, selectedIds, onToggle, Modifier.padding(start = Spacing.m, end = Spacing.m, bottom = Spacing.l))
            }
        }
    }
}

@Composable
internal fun TagChips(tags: List<Tag>, selectedIds: Set<Long>, onToggle: (Long) -> Unit, modifier: Modifier = Modifier) {
    FlowRow(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(Spacing.s),
        verticalArrangement = Arrangement.spacedBy(Spacing.s),
    ) {
        tags.forEach { tag -> TagToggleChip(tag, tag.id in selectedIds, onToggle = { onToggle(tag.id) }) }
    }
}

/** Expressive toggle chip: selecting fills it, swaps to the filled glyph and squares off the pill. */
@Composable
private fun TagToggleChip(tag: Tag, selected: Boolean, onToggle: () -> Unit) {
    val haptics = rememberHaptics()
    val motion = MaterialTheme.motionScheme
    val colors = MaterialTheme.colorScheme
    val interaction = remember { MutableInteractionSource() }
    val corner by animateDpAsState(if (selected) 12.dp else 20.dp, motion.fastSpatialSpec(), label = "chipCorner")
    val container by animateColorAsState(if (selected) colors.primary else colors.surfaceContainerHigh, motion.fastEffectsSpec(), label = "chipColor")
    val content by animateColorAsState(if (selected) colors.onPrimary else colors.onSurfaceVariant, motion.fastEffectsSpec(), label = "chipContent")
    Surface(
        onClick = {
            haptics.toggle(!selected)
            onToggle()
        },
        shape = RoundedCornerShape(corner),
        color = container,
        contentColor = content,
        interactionSource = interaction,
        modifier = Modifier
            .heightIn(min = 40.dp)
            .pressScale(interaction, 0.92f)
            .semantics {
                role = Role.Checkbox
                this.selected = selected
            },
    ) {
        Row(
            Modifier.padding(start = 12.dp, end = 16.dp, top = 9.dp, bottom = 9.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            DakilioIcon(symbolForKey(tag.iconKey), contentDescription = null, filled = selected, modifier = Modifier.size(20.dp))
            Text(tag.label, style = MaterialTheme.typography.labelLarge, modifier = Modifier.padding(start = Spacing.s))
        }
    }
}
