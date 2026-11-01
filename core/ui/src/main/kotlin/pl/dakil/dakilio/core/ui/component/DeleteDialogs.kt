package pl.dakil.dakilio.core.ui.component

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.selection.selectable
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import pl.dakil.dakilio.core.designsystem.icon.DakilioIcon
import pl.dakil.dakilio.core.designsystem.icon.DakilioSymbols
import pl.dakil.dakilio.core.designsystem.theme.Spacing
import pl.dakil.dakilio.core.model.GroupUsage
import pl.dakil.dakilio.core.model.Mood
import pl.dakil.dakilio.core.model.Tag
import pl.dakil.dakilio.core.model.TagGroup
import pl.dakil.dakilio.core.model.TagUsage
import pl.dakil.dakilio.core.ui.R
import pl.dakil.dakilio.core.ui.mood.MoodBadge

/**
 * Deleting a tag. With no entries it's a plain confirmation. With entries it says how many, and
 * offers to archive the tag (when [onArchive] is set) or delete it from every entry.
 */
@Composable
fun DeleteTagDialog(tag: Tag, usage: TagUsage, onDismiss: () -> Unit, onArchive: (() -> Unit)?, onDelete: () -> Unit) {
    val used = usage.entries > 0
    DeleteDialog(
        title = stringResource(R.string.ui_delete_title, tag.label),
        body = when {
            !used -> stringResource(R.string.ui_delete_tag_unused_body)
            onArchive != null -> pluralStringResource(R.plurals.ui_delete_tag_used_body, usage.entries, tag.label, usage.entries)
            else -> pluralStringResource(R.plurals.ui_delete_tag_used_body_final, usage.entries, tag.label, usage.entries)
        } + emptiedLine(R.plurals.ui_delete_tag_emptied, usage.emptied),
        onDismiss = onDismiss,
        onArchive = onArchive?.takeIf { used },
        onDelete = onDelete,
    )
}

/**
 * Deleting a tag group. With no entries using its tags it's a plain confirmation. Otherwise it says
 * how many entries, and offers to archive the group (when [onArchive] is set) or delete it with its tags.
 */
@Composable
fun DeleteGroupDialog(group: TagGroup, usage: GroupUsage, onDismiss: () -> Unit, onArchive: (() -> Unit)?, onDelete: () -> Unit) {
    val used = usage.entries > 0
    DeleteDialog(
        title = stringResource(R.string.ui_delete_group_title, group.name),
        body = when {
            !used -> stringResource(R.string.ui_delete_group_unused_body)
            onArchive != null -> pluralStringResource(R.plurals.ui_delete_group_used_body, usage.entries, group.name, usage.entries)
            else -> pluralStringResource(R.plurals.ui_delete_group_used_body_final, usage.entries, group.name, usage.entries)
        } + emptiedLine(R.plurals.ui_delete_group_emptied, usage.emptied),
        onDismiss = onDismiss,
        onArchive = onArchive?.takeIf { used },
        onDelete = onDelete,
    )
}

/**
 * Deleting a mood alias. With no entries it's a plain confirmation. With entries it can't simply be
 * deleted: it can be archived (when [onArchive] is set) or replaced by another mood.
 */
@Composable
fun DeleteAliasDialog(
    alias: Mood,
    usage: Int,
    onDismiss: () -> Unit,
    onDelete: () -> Unit,
    onArchive: (() -> Unit)?,
    onReplace: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        icon = { DakilioIcon(DakilioSymbols.Delete, contentDescription = null) },
        title = { Text(stringResource(R.string.ui_delete_title, alias.label)) },
        text = {
            Text(
                when {
                    usage == 0 -> stringResource(R.string.ui_delete_alias_unused_body)
                    onArchive != null -> pluralStringResource(R.plurals.ui_delete_alias_used_body, usage, alias.label, usage)
                    else -> pluralStringResource(R.plurals.ui_delete_alias_used_body_final, usage, alias.label, usage)
                },
            )
        },
        confirmButton = {
            if (usage == 0) {
                TextButton(onClick = onDelete) { Text(stringResource(R.string.ui_delete), color = MaterialTheme.colorScheme.error) }
            } else {
                Row {
                    if (onArchive != null) TextButton(onClick = onArchive) { Text(stringResource(R.string.ui_archive)) }
                    TextButton(onClick = onReplace) { Text(stringResource(R.string.ui_replace)) }
                }
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.ui_cancel)) } },
    )
}

/** Pick where an alias's entries go, from [moods] (the alias itself is left out); defaults to its level's primary. */
@Composable
fun ReplaceAliasDialog(alias: Mood, moods: List<Mood>, onDismiss: () -> Unit, onConfirm: (Mood) -> Unit) {
    val choices = moods.filter { it.id != alias.id }
    var target by remember { mutableStateOf(choices.firstOrNull { it.isPrimary && it.level == alias.level }) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.ui_replace_title, alias.label)) },
        text = {
            LazyColumn(Modifier.heightIn(max = 420.dp)) {
                items(choices, key = { it.id }) { mood ->
                    val selected = mood.id == target?.id
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .selectable(selected = selected, role = Role.RadioButton, onClick = { target = mood })
                            .padding(vertical = Spacing.xs),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        RadioButton(selected = selected, onClick = null)
                        MoodBadge(mood, size = 32.dp, modifier = Modifier.padding(start = Spacing.s))
                        Text(
                            mood.label,
                            style = if (mood.isPrimary) MaterialTheme.typography.bodyLargeEmphasized else MaterialTheme.typography.bodyLarge,
                            modifier = Modifier.padding(start = Spacing.m),
                        )
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { target?.let(onConfirm) }, enabled = target != null) { Text(stringResource(R.string.ui_replace_confirm)) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.ui_cancel)) } },
    )
}

@Composable
private fun emptiedLine(plural: Int, emptied: Int): String =
    if (emptied == 0) "" else "\n\n" + pluralStringResource(plural, emptied, emptied)

@Composable
private fun DeleteDialog(title: String, body: String, onDismiss: () -> Unit, onArchive: (() -> Unit)?, onDelete: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        icon = { DakilioIcon(DakilioSymbols.Delete, contentDescription = null) },
        title = { Text(title) },
        text = { Text(body) },
        confirmButton = {
            Row {
                if (onArchive != null) TextButton(onClick = onArchive) { Text(stringResource(R.string.ui_archive)) }
                TextButton(onClick = onDelete) { Text(stringResource(R.string.ui_delete), color = MaterialTheme.colorScheme.error) }
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.ui_cancel)) } },
    )
}
