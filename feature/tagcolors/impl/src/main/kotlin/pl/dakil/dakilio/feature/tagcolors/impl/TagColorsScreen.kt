package pl.dakil.dakilio.feature.tagcolors.impl

import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.IconButton
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MediumFlexibleTopAppBar
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import pl.dakil.dakilio.core.designsystem.component.pressScale
import pl.dakil.dakilio.core.designsystem.component.rememberHaptics
import pl.dakil.dakilio.core.designsystem.icon.DakilioIcon
import pl.dakil.dakilio.core.designsystem.icon.DakilioSymbols
import pl.dakil.dakilio.core.designsystem.theme.Spacing
import pl.dakil.dakilio.core.model.Tag
import pl.dakil.dakilio.core.model.TagGroup
import pl.dakil.dakilio.core.ui.Hint
import pl.dakil.dakilio.core.ui.color.TagColor
import pl.dakil.dakilio.core.ui.component.EmptyState
import pl.dakil.dakilio.core.ui.component.TagPill

/** Which color the sheet is editing, by id, so it follows live changes (and closes if it's gone). */
private sealed interface ColorTarget {
    val id: Long

    data class Group(override val id: Long) : ColorTarget
    data class Of(override val id: Long) : ColorTarget
}

/**
 * The Tag colors extra: a default color per group, which a tag can override with its own. There's
 * no on/off switch; with no colors set the extra is simply unused.
 */
@Composable
internal fun TagColorsScreen(viewModel: TagColorsViewModel, onBack: () -> Unit, onOpenTags: () -> Unit) {
    val groups by viewModel.groups.collectAsStateWithLifecycle()
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()
    // Saved as plain ids (negative = a group) so the sheet survives configuration changes.
    var target by rememberSaveable(stateSaver = ColorTargetSaver) { mutableStateOf<ColorTarget?>(null) }

    Scaffold(
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        topBar = {
            MediumFlexibleTopAppBar(
                title = { Text(stringResource(R.string.tag_colors_title)) },
                subtitle = { Text(stringResource(R.string.tag_colors_subtitle)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        DakilioIcon(DakilioSymbols.ArrowBack, contentDescription = stringResource(R.string.tag_colors_back))
                    }
                },
                scrollBehavior = scrollBehavior,
            )
        },
    ) { padding ->
        val current = groups
        Box(Modifier.fillMaxSize().padding(top = padding.calculateTopPadding()), contentAlignment = Alignment.TopCenter) {
            when {
                current == null -> LoadingIndicator(Modifier.align(Alignment.Center))
                current.isEmpty() -> EmptyState(
                    symbol = DakilioSymbols.Palette,
                    title = stringResource(R.string.tag_colors_empty_title),
                    body = stringResource(R.string.tag_colors_empty_body),
                    modifier = Modifier.align(Alignment.Center),
                    action = { Button(onClick = onOpenTags) { Text(stringResource(R.string.tag_colors_open_tags)) } },
                )
                else -> LazyColumn(
                    modifier = Modifier.widthIn(max = Spacing.maxContentWidth).fillMaxSize(),
                    contentPadding = PaddingValues(
                        start = Spacing.gutter,
                        end = Spacing.gutter,
                        top = Spacing.s,
                        bottom = padding.calculateBottomPadding() + Spacing.xl,
                    ),
                    verticalArrangement = Arrangement.spacedBy(Spacing.m),
                ) {
                    item(key = "hint") {
                        Hint {
                            Text(
                                stringResource(R.string.tag_colors_hint),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(horizontal = Spacing.xs),
                            )
                        }
                    }
                    items(current, key = { it.id }) { group ->
                        GroupCard(
                            group = group,
                            onEditGroup = { target = ColorTarget.Group(group.id) },
                            onEditTag = { tag -> target = ColorTarget.Of(tag.id) },
                            modifier = Modifier.animateItem(),
                        )
                    }
                }
            }
        }
    }

    val all = groups
    val sheet = target
    if (all != null && sheet != null) {
        val group = when (sheet) {
            is ColorTarget.Group -> all.firstOrNull { it.id == sheet.id }
            is ColorTarget.Of -> all.firstOrNull { g -> g.tags.any { it.id == sheet.id } }
        }
        val tag = group?.tags?.firstOrNull { sheet is ColorTarget.Of && it.id == sheet.id }
        when {
            // The group or tag went away (deleted or archived elsewhere).
            group == null -> LaunchedEffect(Unit) { target = null }
            sheet is ColorTarget.Group -> TagColorSheet(
                kind = stringResource(R.string.tag_colors_sheet_group),
                name = group.name,
                preview = group.tags,
                selectedKey = group.colorKey,
                noneLabel = stringResource(R.string.tag_colors_no_color),
                noneColorKey = null,
                onSelect = { viewModel.setGroupColor(group.id, it) },
                onDismiss = { target = null },
            )
            tag != null -> TagColorSheet(
                kind = stringResource(R.string.tag_colors_sheet_tag),
                name = tag.label,
                preview = listOf(tag),
                selectedKey = tag.colorKey,
                noneLabel = stringResource(R.string.tag_colors_use_group),
                noneColorKey = group.colorKey,
                onSelect = { viewModel.setTagColor(tag.id, it) },
                onDismiss = { target = null },
            )
        }
    }
}

private val ColorTargetSaver = Saver<ColorTarget?, Long>(
    save = { target ->
        when (target) {
            is ColorTarget.Group -> -target.id
            is ColorTarget.Of -> target.id
            null -> 0L
        }
    },
    restore = { id ->
        when {
            id < 0 -> ColorTarget.Group(-id)
            id > 0 -> ColorTarget.Of(id)
            else -> null
        }
    },
)

/** A group: its name and default color, then its tags as they look on entries; each part opens the sheet. */
@Composable
private fun GroupCard(group: TagGroup, onEditGroup: () -> Unit, onEditTag: (Tag) -> Unit, modifier: Modifier = Modifier) {
    val haptics = rememberHaptics()
    Surface(shape = RoundedCornerShape(28.dp), color = MaterialTheme.colorScheme.surfaceContainer, modifier = modifier.fillMaxWidth()) {
        Column(Modifier.padding(start = Spacing.l, end = Spacing.m, top = Spacing.m, bottom = Spacing.l)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    group.name,
                    style = MaterialTheme.typography.titleMediumEmphasized,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
                GroupColorButton(
                    group = group,
                    onClick = {
                        haptics.tick()
                        onEditGroup()
                    },
                )
            }
            if (group.tags.isEmpty()) {
                Text(
                    stringResource(R.string.tag_colors_group_empty),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = Spacing.s),
                )
            } else {
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(Spacing.xs),
                    modifier = Modifier.padding(top = Spacing.xs),
                ) {
                    group.tags.forEach { tag ->
                        TappablePill(
                            tag = tag,
                            onClick = {
                                haptics.tick()
                                onEditTag(tag)
                            },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun GroupColorButton(group: TagGroup, onClick: () -> Unit) {
    val color = TagColor.fromKey(group.colorKey)
    val description = stringResource(R.string.tag_colors_change_group, group.name)
    Surface(
        onClick = onClick,
        shape = CircleShape,
        color = MaterialTheme.colorScheme.surfaceContainerHighest,
        modifier = Modifier.heightIn(min = 40.dp).semantics { contentDescription = description },
    ) {
        Row(
            Modifier.padding(start = Spacing.s, end = Spacing.l, top = Spacing.s, bottom = Spacing.s),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            ColorDot(color, size = 24.dp)
            Text(
                if (color == null) stringResource(R.string.tag_colors_no_color) else stringResource(R.string.tag_colors_group_color),
                style = MaterialTheme.typography.labelLarge,
                modifier = Modifier.padding(start = Spacing.s),
            )
        }
    }
}

/** A [TagPill] with a full-height touch target around it. */
@Composable
private fun TappablePill(tag: Tag, onClick: () -> Unit) {
    val interaction = remember { MutableInteractionSource() }
    val description = stringResource(R.string.tag_colors_change_tag, tag.label)
    Box(
        Modifier
            .pressScale(interaction, 0.9f)
            .clickable(interactionSource = interaction, indication = null, onClick = onClick)
            .semantics {
                role = Role.Button
                contentDescription = description
            }
            .heightIn(min = 40.dp),
        contentAlignment = Alignment.Center,
    ) {
        TagPill(tag)
    }
}
