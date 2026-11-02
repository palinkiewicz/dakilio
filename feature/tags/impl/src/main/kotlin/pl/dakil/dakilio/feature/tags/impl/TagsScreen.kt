package pl.dakil.dakilio.feature.tags.impl

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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.input.rememberTextFieldState
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.IconButton
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MediumFlexibleTopAppBar
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.launch
import pl.dakil.dakilio.core.designsystem.component.pressScale
import pl.dakil.dakilio.core.designsystem.component.rememberHaptics
import pl.dakil.dakilio.core.designsystem.icon.DakilioIcon
import pl.dakil.dakilio.core.designsystem.icon.DakilioSymbols
import pl.dakil.dakilio.core.designsystem.icon.symbolForKey
import pl.dakil.dakilio.core.designsystem.theme.Spacing
import pl.dakil.dakilio.core.model.GroupUsage
import pl.dakil.dakilio.core.model.Tag
import pl.dakil.dakilio.core.model.TagGroup
import pl.dakil.dakilio.core.model.TagUsage
import pl.dakil.dakilio.core.ui.Hint
import pl.dakil.dakilio.core.ui.color.TagColor
import pl.dakil.dakilio.core.ui.color.tagColors
import pl.dakil.dakilio.core.ui.component.DeleteGroupDialog
import pl.dakil.dakilio.core.ui.component.DeleteTagDialog
import pl.dakil.dakilio.core.ui.component.EmptyState

/** What the tag editor sheet is editing: an existing tag, or a new one in [groupId]. */
internal data class TagEdit(val tag: Tag?, val groupId: Long)

private sealed interface GroupDialog {
    data object Create : GroupDialog
    data class Rename(val group: TagGroup) : GroupDialog
}

@Composable
internal fun TagsScreen(viewModel: TagsViewModel, onBack: () -> Unit) {
    val groups by viewModel.groups.collectAsStateWithLifecycle()
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()
    val listState = rememberLazyListState()
    val snackbar = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val haptics = rememberHaptics()
    var editing by remember { mutableStateOf<TagEdit?>(null) }
    var groupDialog by remember { mutableStateOf<GroupDialog?>(null) }
    var deleting by remember { mutableStateOf<Pair<Tag, TagUsage>?>(null) }
    var deletingGroup by remember { mutableStateOf<Pair<TagGroup, GroupUsage>?>(null) }
    var suggesting by rememberSaveable { mutableStateOf(false) }
    // The group whose color sheet is open, by id so it follows live changes (and closes if it's gone).
    var coloring by rememberSaveable { mutableStateOf<Long?>(null) }

    val undoLabel = stringResource(R.string.tags_undo)
    val tagDeleted = stringResource(R.string.tags_tag_deleted)
    val tagArchived = stringResource(R.string.tags_tag_archived)
    val groupDeleted = stringResource(R.string.tags_group_deleted)
    val groupArchived = stringResource(R.string.tags_group_archived)
    fun offerUndo(message: String, undo: () -> Unit) {
        scope.launch {
            snackbar.currentSnackbarData?.dismiss()
            if (snackbar.showSnackbar(message, undoLabel, withDismissAction = true) == SnackbarResult.ActionPerformed) undo()
        }
    }

    Scaffold(
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        snackbarHost = { SnackbarHost(snackbar) },
        topBar = {
            MediumFlexibleTopAppBar(
                title = { Text(stringResource(R.string.tags_title)) },
                subtitle = { Text(stringResource(R.string.tags_subtitle)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        DakilioIcon(DakilioSymbols.ArrowBack, contentDescription = stringResource(R.string.tags_back))
                    }
                },
                actions = {
                    IconButton(onClick = { suggesting = true }) {
                        DakilioIcon(DakilioSymbols.AutoAwesome, contentDescription = stringResource(R.string.tags_suggested))
                    }
                },
                scrollBehavior = scrollBehavior,
            )
        },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = { groupDialog = GroupDialog.Create },
                expanded = !listState.canScrollBackward,
                icon = { DakilioIcon(DakilioSymbols.Add, contentDescription = null) },
                text = { Text(stringResource(R.string.tags_new_group)) },
            )
        },
    ) { padding ->
        val current = groups
        Box(Modifier.fillMaxSize().padding(top = padding.calculateTopPadding()), contentAlignment = Alignment.TopCenter) {
            when {
                current == null -> LoadingIndicator(Modifier.align(Alignment.Center))
                current.isEmpty() -> EmptyState(
                    symbol = DakilioSymbols.Sell,
                    title = stringResource(R.string.tags_empty_title),
                    body = stringResource(R.string.tags_empty_body),
                    modifier = Modifier.align(Alignment.Center),
                    action = {
                        FilledTonalButton(onClick = { suggesting = true }) {
                            DakilioIcon(DakilioSymbols.AutoAwesome, contentDescription = null, modifier = Modifier.padding(end = Spacing.s))
                            Text(stringResource(R.string.tags_suggested_browse))
                        }
                    },
                )
                else -> LazyColumn(
                    state = listState,
                    modifier = Modifier.widthIn(max = Spacing.maxContentWidth).fillMaxSize(),
                    contentPadding = PaddingValues(start = Spacing.gutter, end = Spacing.gutter, top = Spacing.s, bottom = padding.calculateBottomPadding() + 112.dp),
                    verticalArrangement = Arrangement.spacedBy(Spacing.m),
                ) {
                    item(key = "hint") {
                        Hint {
                            Text(
                                stringResource(R.string.tags_color_hint),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(horizontal = Spacing.xs).animateItem(),
                            )
                        }
                    }
                    items(current, key = { it.id }) { group ->
                        GroupCard(
                            group = group,
                            onColor = {
                                haptics.tick()
                                coloring = group.id
                            },
                            onEditTag = { editing = TagEdit(it, group.id) },
                            onAddTag = { editing = TagEdit(null, group.id) },
                            onRename = { groupDialog = GroupDialog.Rename(group) },
                            onDelete = { viewModel.groupUsage(group.id) { usage -> deletingGroup = group to usage } },
                            modifier = Modifier.animateItem(),
                        )
                    }
                    item(key = "note") {
                        Hint {
                            Text(
                                stringResource(R.string.tags_archived_note),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(horizontal = Spacing.xs, vertical = Spacing.s).animateItem(),
                            )
                        }
                    }
                }
            }
        }
    }

    if (suggesting) {
        SuggestedGroupsSheet(
            suggestions = viewModel.suggestions,
            groups = groups.orEmpty(),
            onAdd = { viewModel.addSuggestedGroup(it.key) },
            onDismiss = { suggesting = false },
        )
    }

    coloring?.let { id ->
        val group = groups?.firstOrNull { it.id == id }
        when {
            groups == null -> Unit
            // The group went away (deleted or archived).
            group == null -> LaunchedEffect(id) { coloring = null }
            else -> GroupColorSheet(
                group = group,
                onSelect = { viewModel.setGroupColor(group.id, it) },
                onDismiss = { coloring = null },
            )
        }
    }

    editing?.let { edit ->
        TagEditorSheet(
            edit = edit,
            groups = groups.orEmpty(),
            onDismiss = { editing = null },
            onSave = { groupId, label, icon, color ->
                haptics.confirm()
                viewModel.saveTag(edit.tag?.id, groupId, label, icon, color)
                editing = null
            },
            onDelete = edit.tag?.let { tag ->
                { viewModel.tagUsage(tag.id) { usage -> deleting = tag to usage } }
            },
        )
    }

    deleting?.let { (tag, usage) ->
        DeleteTagDialog(
            tag = tag,
            usage = usage,
            onDismiss = { deleting = null },
            onArchive = {
                haptics.confirm()
                viewModel.archiveTag(tag.id)
                deleting = null
                editing = null
                offerUndo(tagArchived.format(tag.label)) { viewModel.restoreTag(tag.id) }
            },
            onDelete = {
                haptics.confirm()
                viewModel.deleteTag(tag.id)
                deleting = null
                editing = null
                scope.launch {
                    snackbar.currentSnackbarData?.dismiss()
                    snackbar.showSnackbar(tagDeleted.format(tag.label), withDismissAction = true)
                }
            },
        )
    }

    deletingGroup?.let { (group, usage) ->
        DeleteGroupDialog(
            group = group,
            usage = usage,
            onDismiss = { deletingGroup = null },
            onArchive = {
                haptics.confirm()
                viewModel.archiveGroup(group.id)
                deletingGroup = null
                offerUndo(groupArchived.format(group.name)) { viewModel.restoreGroup(group.id) }
            },
            onDelete = {
                haptics.confirm()
                viewModel.deleteGroup(group.id)
                deletingGroup = null
                scope.launch {
                    snackbar.currentSnackbarData?.dismiss()
                    snackbar.showSnackbar(groupDeleted.format(group.name), withDismissAction = true)
                }
            },
        )
    }

    groupDialog?.let { dialog ->
        GroupNameDialog(
            initial = (dialog as? GroupDialog.Rename)?.group?.name.orEmpty(),
            title = stringResource(if (dialog is GroupDialog.Rename) R.string.tags_group_rename_title else R.string.tags_group_new_title),
            onDismiss = { groupDialog = null },
            onConfirm = { name ->
                when (dialog) {
                    GroupDialog.Create -> viewModel.createGroup(name)
                    is GroupDialog.Rename -> viewModel.renameGroup(dialog.group.id, name)
                }
                groupDialog = null
            },
        )
    }
}

@Composable
private fun GroupCard(
    group: TagGroup,
    onColor: () -> Unit,
    onEditTag: (Tag) -> Unit,
    onAddTag: () -> Unit,
    onRename: () -> Unit,
    onDelete: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var menu by remember { mutableStateOf(false) }
    Surface(
        shape = RoundedCornerShape(28.dp),
        color = MaterialTheme.colorScheme.surfaceContainer,
        modifier = modifier.fillMaxWidth().animateContentSize(MaterialTheme.motionScheme.defaultSpatialSpec()),
    ) {
        Column(Modifier.padding(start = Spacing.xl, end = Spacing.s, top = Spacing.s, bottom = Spacing.xl)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(group.name, style = MaterialTheme.typography.titleMediumEmphasized)
                    Text(
                        pluralStringResource(R.plurals.tags_count, group.tags.size, group.tags.size),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                GroupColorButton(group, onClick = onColor)
                Box {
                    IconButton(onClick = { menu = true }) {
                        DakilioIcon(DakilioSymbols.MoreVert, contentDescription = stringResource(R.string.tags_group_menu))
                    }
                    DropdownMenu(expanded = menu, onDismissRequest = { menu = false }, shape = RoundedCornerShape(20.dp)) {
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.tags_rename_group)) },
                            leadingIcon = { DakilioIcon(DakilioSymbols.Edit, contentDescription = null) },
                            onClick = {
                                menu = false
                                onRename()
                            },
                        )
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.tags_delete_group)) },
                            leadingIcon = { DakilioIcon(DakilioSymbols.Delete, contentDescription = null) },
                            onClick = {
                                menu = false
                                onDelete()
                            },
                        )
                    }
                }
            }
            FlowRow(
                modifier = Modifier.padding(top = Spacing.m, end = Spacing.m),
                horizontalArrangement = Arrangement.spacedBy(Spacing.s),
                verticalArrangement = Arrangement.spacedBy(Spacing.s),
            ) {
                group.tags.forEach { tag ->
                    val tones = tagColors(tag.color)
                    EditableChip(
                        label = tag.label,
                        icon = { DakilioIcon(symbolForKey(tag.iconKey), contentDescription = null, modifier = Modifier.size(18.dp)) },
                        onClick = { onEditTag(tag) },
                        containerColor = tones?.soft ?: MaterialTheme.colorScheme.surfaceContainerHighest,
                        contentColor = tones?.accent ?: MaterialTheme.colorScheme.onSurface,
                    )
                }
                EditableChip(
                    label = stringResource(R.string.tags_add_tag),
                    icon = { DakilioIcon(DakilioSymbols.Add, contentDescription = null, modifier = Modifier.size(18.dp)) },
                    onClick = onAddTag,
                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                    contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                )
            }
        }
    }
}

/** The group's default tag color as a dot; opens its color sheet. */
@Composable
private fun GroupColorButton(group: TagGroup, onClick: () -> Unit) {
    val color = TagColor.fromKey(group.colorKey)
    val description = stringResource(R.string.tags_color_change_group, group.name)
    IconButton(onClick = onClick, modifier = Modifier.semantics { contentDescription = description }) {
        if (color == null) {
            DakilioIcon(DakilioSymbols.Palette, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
        } else {
            ColorDot(color, size = 24.dp)
        }
    }
}

/** A tag (in its color, as on entries) or the "Add tag" action. */
@Composable
private fun EditableChip(label: String, icon: @Composable () -> Unit, onClick: () -> Unit, containerColor: Color, contentColor: Color) {
    val interaction = remember { MutableInteractionSource() }
    Surface(
        onClick = onClick,
        shape = CircleShape,
        color = containerColor,
        contentColor = contentColor,
        interactionSource = interaction,
        modifier = Modifier.heightIn(min = 40.dp).pressScale(interaction, 0.92f),
    ) {
        Row(Modifier.padding(start = 12.dp, end = 16.dp, top = 9.dp, bottom = 9.dp), verticalAlignment = Alignment.CenterVertically) {
            icon()
            Text(label, style = MaterialTheme.typography.labelLarge, modifier = Modifier.padding(start = Spacing.s))
        }
    }
}

@Composable
private fun GroupNameDialog(initial: String, title: String, onDismiss: () -> Unit, onConfirm: (String) -> Unit) {
    val name = rememberTextFieldState(initial)
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            OutlinedTextField(
                state = name,
                label = { Text(stringResource(R.string.tags_group_name)) },
                lineLimits = androidx.compose.foundation.text.input.TextFieldLineLimits.SingleLine,
                modifier = Modifier.fillMaxWidth(),
            )
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(name.text.toString()) }, enabled = name.text.isNotBlank()) {
                Text(stringResource(R.string.tags_save))
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.tags_cancel)) } },
    )
}
