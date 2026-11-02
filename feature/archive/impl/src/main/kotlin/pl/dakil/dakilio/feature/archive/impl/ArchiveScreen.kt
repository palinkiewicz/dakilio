package pl.dakil.dakilio.feature.archive.impl

import androidx.compose.animation.animateContentSize
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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MediumFlexibleTopAppBar
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedListItem
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.launch
import pl.dakil.dakilio.core.designsystem.component.SectionHeader
import pl.dakil.dakilio.core.designsystem.component.pressScale
import pl.dakil.dakilio.core.designsystem.component.rememberHaptics
import pl.dakil.dakilio.core.designsystem.component.segmentedItemColors
import pl.dakil.dakilio.core.designsystem.icon.DakilioIcon
import pl.dakil.dakilio.core.designsystem.icon.DakilioSymbols
import pl.dakil.dakilio.core.designsystem.icon.symbolForKey
import pl.dakil.dakilio.core.designsystem.theme.Spacing
import pl.dakil.dakilio.core.model.GroupUsage
import pl.dakil.dakilio.core.model.Mood
import pl.dakil.dakilio.core.model.Tag
import pl.dakil.dakilio.core.model.TagGroup
import pl.dakil.dakilio.core.model.TagUsage
import pl.dakil.dakilio.core.ui.Hint
import pl.dakil.dakilio.core.ui.component.DeleteAliasDialog
import pl.dakil.dakilio.core.ui.component.DeleteGroupDialog
import pl.dakil.dakilio.core.ui.component.DeleteTagDialog
import pl.dakil.dakilio.core.ui.component.EmptyState
import pl.dakil.dakilio.core.ui.component.ReplaceAliasDialog
import pl.dakil.dakilio.core.ui.mood.MoodBadge

private sealed interface ArchiveDialog {
    data class DeleteMood(val mood: Mood, val usage: Int) : ArchiveDialog
    data class ReplaceMood(val mood: Mood) : ArchiveDialog
    data class DeleteGroup(val group: TagGroup, val usage: GroupUsage) : ArchiveDialog
    data class DeleteTag(val tag: Tag, val usage: TagUsage) : ArchiveDialog
}

/** Archived mood aliases, tag groups and tags, each of which can be restored or deleted for good. */
@Composable
internal fun ArchiveScreen(viewModel: ArchiveViewModel, onBack: () -> Unit) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()
    val snackbar = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val haptics = rememberHaptics()
    val resources = LocalContext.current.resources
    var dialog by remember { mutableStateOf<ArchiveDialog?>(null) }

    val restoredMessage = stringResource(R.string.archive_restored)
    val deletedMessage = stringResource(R.string.archive_deleted)
    fun notify(message: String) {
        scope.launch {
            snackbar.currentSnackbarData?.dismiss()
            snackbar.showSnackbar(message, withDismissAction = true)
        }
    }

    Scaffold(
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        snackbarHost = { SnackbarHost(snackbar) },
        topBar = {
            MediumFlexibleTopAppBar(
                title = { Text(stringResource(R.string.archive_title)) },
                subtitle = { Text(stringResource(R.string.archive_subtitle)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        DakilioIcon(DakilioSymbols.ArrowBack, contentDescription = stringResource(R.string.archive_back))
                    }
                },
                scrollBehavior = scrollBehavior,
            )
        },
    ) { padding ->
        val current = state
        Box(Modifier.fillMaxSize().padding(top = padding.calculateTopPadding()), contentAlignment = Alignment.TopCenter) {
            when {
                current == null -> LoadingIndicator(Modifier.align(Alignment.Center))
                current.isEmpty -> EmptyState(
                    symbol = DakilioSymbols.Inventory2,
                    title = stringResource(R.string.archive_empty_title),
                    body = stringResource(R.string.archive_empty_body),
                    modifier = Modifier.align(Alignment.Center),
                )
                else -> LazyColumn(
                    modifier = Modifier.widthIn(max = Spacing.maxContentWidth).fillMaxSize(),
                    contentPadding = PaddingValues(start = Spacing.gutter, end = Spacing.gutter, bottom = padding.calculateBottomPadding() + Spacing.xl),
                ) {
                    moodSection(
                        moods = current.moods,
                        primaries = current.activeMoods.filter { it.isPrimary }.associateBy { it.level },
                        onRestore = { mood ->
                            haptics.confirm()
                            viewModel.setMoodArchived(mood, false)
                            notify(restoredMessage.format(mood.label))
                        },
                        onDelete = { mood -> viewModel.moodUsage(mood) { dialog = ArchiveDialog.DeleteMood(mood, it) } },
                    )
                    if (current.groups.isNotEmpty()) {
                        item(key = "groups_header") { SectionHeader(stringResource(R.string.archive_section_groups), Modifier.animateItem()) }
                        current.groups.forEach { group ->
                            item(key = "group_${group.id}") {
                                ArchivedCard(
                                    title = group.name,
                                    subtitle = pluralStringResource(R.plurals.archive_tag_count, group.tags.size, group.tags.size),
                                    menu = {
                                        RestoreDeleteMenu(
                                            contentDescription = stringResource(R.string.archive_group_menu, group.name),
                                            onRestore = {
                                                haptics.confirm()
                                                viewModel.setGroupArchived(group.id, false)
                                                notify(restoredMessage.format(group.name))
                                            },
                                            onDelete = { viewModel.groupUsage(group.id) { dialog = ArchiveDialog.DeleteGroup(group, it) } },
                                        )
                                    },
                                    modifier = Modifier.padding(bottom = Spacing.m).animateItem(),
                                ) {
                                    group.tags.forEach { tag -> TagChip(tag, onClick = null) }
                                }
                            }
                        }
                    }
                    if (current.tags.isNotEmpty()) {
                        item(key = "tags_header") { SectionHeader(stringResource(R.string.archive_section_tags), Modifier.animateItem()) }
                        item(key = "tags_hint") {
                            Hint {
                                Text(
                                    stringResource(R.string.archive_tags_hint),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.padding(start = Spacing.xs, end = Spacing.xs, bottom = Spacing.s).animateItem(),
                                )
                            }
                        }
                        current.tags.forEach { group ->
                            item(key = "tags_${group.id}") {
                                ArchivedCard(
                                    title = group.name,
                                    subtitle = pluralStringResource(R.plurals.archive_archived_tag_count, group.tags.size, group.tags.size),
                                    menu = null,
                                    modifier = Modifier.padding(bottom = Spacing.m).animateItem(),
                                ) {
                                    group.tags.forEach { tag ->
                                        TagChip(tag, onClick = { expanded, dismiss ->
                                            RestoreDeleteDropdown(
                                                expanded = expanded,
                                                onDismiss = dismiss,
                                                onRestore = {
                                                    haptics.confirm()
                                                    viewModel.setTagArchived(tag.id, false)
                                                    notify(restoredMessage.format(tag.label))
                                                },
                                                onDelete = { viewModel.tagUsage(tag.id) { dialog = ArchiveDialog.DeleteTag(tag, it) } },
                                            )
                                        })
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    when (val d = dialog) {
        null -> Unit
        is ArchiveDialog.DeleteMood -> DeleteAliasDialog(
            alias = d.mood,
            usage = d.usage,
            onDismiss = { dialog = null },
            onDelete = {
                haptics.confirm()
                viewModel.deleteUnusedMood(d.mood)
                dialog = null
                notify(deletedMessage.format(d.mood.label))
            },
            onArchive = null,
            onReplace = { dialog = ArchiveDialog.ReplaceMood(d.mood) },
        )
        is ArchiveDialog.ReplaceMood -> ReplaceAliasDialog(
            alias = d.mood,
            moods = state?.activeMoods.orEmpty(),
            onDismiss = { dialog = null },
            onConfirm = { target ->
                haptics.confirm()
                dialog = null
                viewModel.replaceMood(d.mood, target) { moved ->
                    notify(resources.getQuantityString(R.plurals.archive_mood_replaced, moved, moved, target.label))
                }
            },
        )
        is ArchiveDialog.DeleteGroup -> DeleteGroupDialog(
            group = d.group,
            usage = d.usage,
            onDismiss = { dialog = null },
            onArchive = null,
            onDelete = {
                haptics.confirm()
                viewModel.deleteGroup(d.group.id)
                dialog = null
                notify(deletedMessage.format(d.group.name))
            },
        )
        is ArchiveDialog.DeleteTag -> DeleteTagDialog(
            tag = d.tag,
            usage = d.usage,
            onDismiss = { dialog = null },
            onArchive = null,
            onDelete = {
                haptics.confirm()
                viewModel.deleteTag(d.tag.id)
                dialog = null
                notify(deletedMessage.format(d.tag.label))
            },
        )
    }
}

/** Archived aliases as one segmented list: face, name, which level they belong to, and a menu. */
private fun LazyListScope.moodSection(
    moods: List<Mood>,
    primaries: Map<Int, Mood>,
    onRestore: (Mood) -> Unit,
    onDelete: (Mood) -> Unit,
) {
    if (moods.isEmpty()) return
    item(key = "moods_header") { SectionHeader(stringResource(R.string.archive_section_moods), Modifier.animateItem()) }
    itemsIndexed(moods, key = { _, mood -> "mood_${mood.id}" }) { index, mood ->
        SegmentedListItem(
            shapes = ListItemDefaults.segmentedShapes(index, moods.size),
            colors = segmentedItemColors(),
            leadingContent = { MoodBadge(mood, size = 40.dp) },
            supportingContent = primaries[mood.level]?.let { primary -> { Text(stringResource(R.string.archive_alias_of, primary.label)) } },
            trailingContent = {
                RestoreDeleteMenu(
                    contentDescription = stringResource(R.string.archive_mood_menu, mood.label),
                    onRestore = { onRestore(mood) },
                    onDelete = { onDelete(mood) },
                )
            },
            modifier = Modifier
                .padding(bottom = if (index == moods.lastIndex) Spacing.m else ListItemDefaults.SegmentedGap)
                .animateItem(),
        ) { Text(mood.label) }
    }
}

@Composable
private fun ArchivedCard(
    title: String,
    subtitle: String,
    menu: (@Composable () -> Unit)?,
    modifier: Modifier = Modifier,
    chips: @Composable () -> Unit,
) {
    Surface(
        shape = RoundedCornerShape(28.dp),
        color = MaterialTheme.colorScheme.surfaceContainer,
        modifier = modifier.fillMaxWidth().animateContentSize(MaterialTheme.motionScheme.defaultSpatialSpec()),
    ) {
        Column(Modifier.padding(start = Spacing.xl, end = Spacing.s, top = if (menu != null) Spacing.s else Spacing.l, bottom = Spacing.xl)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(title, style = MaterialTheme.typography.titleMediumEmphasized)
                    Text(subtitle, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                menu?.invoke()
            }
            FlowRow(
                modifier = Modifier.padding(top = Spacing.m, end = Spacing.m),
                horizontalArrangement = Arrangement.spacedBy(Spacing.s),
                verticalArrangement = Arrangement.spacedBy(Spacing.s),
            ) { chips() }
        }
    }
}

/**
 * A tag chip. With [onClick] it's tappable and opens the menu that lambda draws (given whether it's
 * expanded and how to close it); without, it's read-only.
 */
@Composable
private fun TagChip(tag: Tag, onClick: (@Composable (expanded: Boolean, dismiss: () -> Unit) -> Unit)?) {
    val interaction = remember { MutableInteractionSource() }
    var menu by remember { mutableStateOf(false) }
    val content: @Composable () -> Unit = {
        Row(Modifier.padding(start = 12.dp, end = 16.dp, top = 9.dp, bottom = 9.dp), verticalAlignment = Alignment.CenterVertically) {
            DakilioIcon(symbolForKey(tag.iconKey), contentDescription = null, modifier = Modifier.size(18.dp))
            Text(tag.label, style = MaterialTheme.typography.labelLarge, modifier = Modifier.padding(start = Spacing.s))
        }
    }
    val shape = CircleShape
    val color = MaterialTheme.colorScheme.surfaceContainerHighest
    val chipModifier = Modifier.heightIn(min = 40.dp)
    if (onClick == null) {
        Surface(shape = shape, color = color, contentColor = MaterialTheme.colorScheme.onSurfaceVariant, modifier = chipModifier) { content() }
    } else {
        Box {
            Surface(
                onClick = { menu = true },
                shape = shape,
                color = color,
                contentColor = MaterialTheme.colorScheme.onSurface,
                interactionSource = interaction,
                modifier = chipModifier.pressScale(interaction, 0.92f),
            ) { content() }
            onClick(menu) { menu = false }
        }
    }
}

@Composable
private fun RestoreDeleteMenu(contentDescription: String, onRestore: () -> Unit, onDelete: () -> Unit) {
    var menu by remember { mutableStateOf(false) }
    Box {
        IconButton(onClick = { menu = true }) {
            DakilioIcon(DakilioSymbols.MoreVert, contentDescription = contentDescription)
        }
        RestoreDeleteDropdown(expanded = menu, onDismiss = { menu = false }, onRestore = onRestore, onDelete = onDelete)
    }
}

@Composable
private fun RestoreDeleteDropdown(expanded: Boolean, onDismiss: () -> Unit, onRestore: () -> Unit, onDelete: () -> Unit) {
    DropdownMenu(expanded = expanded, onDismissRequest = onDismiss, shape = RoundedCornerShape(20.dp)) {
        DropdownMenuItem(
            text = { Text(stringResource(R.string.archive_restore)) },
            leadingIcon = { DakilioIcon(DakilioSymbols.Unarchive, contentDescription = null) },
            onClick = {
                onDismiss()
                onRestore()
            },
        )
        DropdownMenuItem(
            text = { Text(stringResource(R.string.archive_delete)) },
            leadingIcon = { DakilioIcon(DakilioSymbols.Delete, contentDescription = null) },
            onClick = {
                onDismiss()
                onDelete()
            },
        )
    }
}
