package pl.dakil.dakilio.feature.entry.impl

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Badge
import androidx.compose.material3.MaterialShapes
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.graphics.shapes.Morph
import pl.dakil.dakilio.core.designsystem.component.MorphShape
import pl.dakil.dakilio.core.designsystem.component.pressScale
import pl.dakil.dakilio.core.designsystem.component.rememberHaptics
import pl.dakil.dakilio.core.designsystem.icon.DakilioIcon
import pl.dakil.dakilio.core.designsystem.icon.DakilioSymbols
import pl.dakil.dakilio.core.designsystem.icon.symbolForKey
import pl.dakil.dakilio.core.designsystem.theme.Spacing
import pl.dakil.dakilio.core.model.Tag
import pl.dakil.dakilio.core.model.TagGroup
import pl.dakil.dakilio.core.model.TagStyle

/**
 * The tag groups as lazy items, each a collapsible panel: groups suggested by the selected diaries
 * start open, the rest start closed under "More groups". [toggledGroups] holds the ids the user
 * flipped away from that default during this editor session. A Manage tags button closes the list.
 */
internal fun LazyListScope.tagGroupItems(
    suggested: List<TagGroup>,
    others: List<TagGroup>,
    selectedIds: Set<Long>,
    style: TagStyle,
    toggledGroups: Set<Long>,
    onToggleGroup: (Long) -> Unit,
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
        TagGroupPanel(
            group = group,
            selectedIds = selectedIds,
            style = style,
            expanded = group.id !in toggledGroups,
            onExpandedChange = { onToggleGroup(group.id) },
            onToggle = onToggle,
            modifier = Modifier.animateItem().then(sides),
        )
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
            TagGroupPanel(
                group = group,
                selectedIds = selectedIds,
                style = style,
                expanded = group.id in toggledGroups,
                onExpandedChange = { onToggleGroup(group.id) },
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

/** A tag group as a panel: tap the header to fold it away; a badge counts what's selected inside. */
@Composable
private fun TagGroupPanel(
    group: TagGroup,
    selectedIds: Set<Long>,
    style: TagStyle,
    expanded: Boolean,
    onExpandedChange: (Boolean) -> Unit,
    onToggle: (Long) -> Unit,
    modifier: Modifier = Modifier,
) {
    val haptics = rememberHaptics()
    val selectedCount = group.tags.count { it.id in selectedIds }
    val rotation by animateFloatAsState(if (expanded) 180f else 0f, MaterialTheme.motionScheme.defaultSpatialSpec(), label = "groupChevron")
    val stateLabel = stringResource(if (expanded) R.string.entry_group_expanded else R.string.entry_group_collapsed)
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
                modifier = Modifier.semantics {
                    role = Role.Button
                    stateDescription = stateLabel
                },
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
                when (style) {
                    TagStyle.Icons -> TagIconGrid(group.tags, selectedIds, onToggle, Modifier.padding(start = Spacing.s, end = Spacing.s, bottom = Spacing.m))
                    TagStyle.Chips -> TagChips(group.tags, selectedIds, onToggle, Modifier.padding(start = Spacing.m, end = Spacing.m, bottom = Spacing.l))
                }
            }
        }
    }
}

private val IconCellMinWidth = 64.dp
private val IconSize = 48.dp
private val tagMorph by lazy { Morph(MaterialShapes.Circle, MaterialShapes.Cookie9Sided) }

/** Daylio-style dense grid: an icon button per tag with its label underneath, in even columns. */
@Composable
private fun TagIconGrid(tags: List<Tag>, selectedIds: Set<Long>, onToggle: (Long) -> Unit, modifier: Modifier = Modifier) {
    // A plain Layout rather than BoxWithConstraints: subcomposing during measure misbehaves inside
    // the editor's container transform (lookahead), making groups fade in late.
    Layout(
        content = { tags.forEach { tag -> TagIconButton(tag, tag.id in selectedIds, onToggle = { onToggle(tag.id) }) } },
        modifier = modifier.fillMaxWidth(),
    ) { measurables, constraints ->
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
                row.forEachIndexed { c, placeable -> placeable.placeRelative(c * cell, y) }
                y += rowHeights[r] + gap
            }
        }
    }
}

/** Selecting fills the circle and morphs it into a cookie, swapping to the filled glyph. */
@Composable
private fun TagIconButton(tag: Tag, selected: Boolean, onToggle: () -> Unit, modifier: Modifier = Modifier) {
    val haptics = rememberHaptics()
    val colors = MaterialTheme.colorScheme
    val interaction = remember { MutableInteractionSource() }
    val progress by animateFloatAsState(if (selected) 1f else 0f, MaterialTheme.motionScheme.fastSpatialSpec(), label = "tagMorph")
    val label by animateColorAsState(
        if (selected) colors.primary else colors.onSurfaceVariant,
        MaterialTheme.motionScheme.fastEffectsSpec(),
        label = "tagLabel",
    )
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = modifier
            .pressScale(interaction, 0.9f)
            .clickable(interactionSource = interaction, indication = null) {
                haptics.toggle(!selected)
                onToggle()
            }
            .semantics {
                role = Role.Checkbox
                this.selected = selected
            }
            .padding(vertical = Spacing.xs),
    ) {
        Box(
            Modifier
                .size(IconSize)
                .graphicsLayer { rotationZ = progress * 20f }
                .background(lerp(colors.surfaceContainerHigh, colors.primary, progress), MorphShape(tagMorph, progress)),
            contentAlignment = Alignment.Center,
        ) {
            DakilioIcon(
                symbolForKey(tag.iconKey),
                contentDescription = null,
                filled = selected,
                tint = lerp(colors.onSurfaceVariant, colors.onPrimary, progress),
                modifier = Modifier.size(24.dp).graphicsLayer { rotationZ = -progress * 20f },
            )
        }
        Text(
            tag.label,
            style = if (selected) MaterialTheme.typography.labelMediumEmphasized else MaterialTheme.typography.labelMedium,
            color = label,
            textAlign = TextAlign.Center,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(top = Spacing.xs, start = 2.dp, end = 2.dp),
        )
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
