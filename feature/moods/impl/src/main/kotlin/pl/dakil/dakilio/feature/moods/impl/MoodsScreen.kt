package pl.dakil.dakilio.feature.moods.impl

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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.input.TextFieldLineLimits
import androidx.compose.foundation.text.input.rememberTextFieldState
import androidx.compose.material3.AlertDialog
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
import pl.dakil.dakilio.core.designsystem.component.pressScale
import pl.dakil.dakilio.core.designsystem.component.rememberHaptics
import pl.dakil.dakilio.core.designsystem.icon.DakilioIcon
import pl.dakil.dakilio.core.designsystem.icon.DakilioSymbols
import pl.dakil.dakilio.core.designsystem.theme.Spacing
import pl.dakil.dakilio.core.model.Mood
import pl.dakil.dakilio.core.ui.Hint
import pl.dakil.dakilio.core.ui.component.DeleteAliasDialog
import pl.dakil.dakilio.core.ui.component.ReplaceAliasDialog
import pl.dakil.dakilio.core.ui.mood.MoodBadge

private sealed interface MoodDialog {
    data class Rename(val mood: Mood) : MoodDialog
    data class AddAlias(val primary: Mood) : MoodDialog

    /** Deleting an alias: with no entries it's a plain confirmation; otherwise archive or replace. */
    data class Delete(val alias: Mood, val usage: Int) : MoodDialog
    data class Replace(val alias: Mood, val usage: Int) : MoodDialog
}

@Composable
internal fun MoodsScreen(viewModel: MoodsViewModel, onBack: () -> Unit) {
    val levels by viewModel.levels.collectAsStateWithLifecycle()
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()
    val snackbar = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val haptics = rememberHaptics()
    var dialog by remember { mutableStateOf<MoodDialog?>(null) }

    val undoLabel = stringResource(R.string.moods_undo)
    val archivedMessage = stringResource(R.string.moods_archived)
    val resources = LocalContext.current.resources

    Scaffold(
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        snackbarHost = { SnackbarHost(snackbar) },
        topBar = {
            MediumFlexibleTopAppBar(
                title = { Text(stringResource(R.string.moods_title)) },
                subtitle = { Text(stringResource(R.string.moods_subtitle)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        DakilioIcon(DakilioSymbols.ArrowBack, contentDescription = stringResource(R.string.moods_back))
                    }
                },
                scrollBehavior = scrollBehavior,
            )
        },
    ) { padding ->
        val current = levels
        Box(Modifier.fillMaxSize().padding(top = padding.calculateTopPadding()), contentAlignment = Alignment.TopCenter) {
            if (current == null) {
                LoadingIndicator(Modifier.align(Alignment.Center))
                return@Box
            }
            LazyColumn(
                modifier = Modifier.widthIn(max = Spacing.maxContentWidth).fillMaxSize(),
                contentPadding = PaddingValues(start = Spacing.gutter, end = Spacing.gutter, top = Spacing.s, bottom = padding.calculateBottomPadding() + Spacing.xl),
                verticalArrangement = Arrangement.spacedBy(Spacing.m),
            ) {
                item(key = "hint") {
                    Hint {
                        Text(
                            stringResource(R.string.moods_hint),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(horizontal = Spacing.xs),
                        )
                    }
                }
                items(current, key = { it.first().level }) { levelMoods ->
                    LevelCard(
                        moods = levelMoods,
                        onRename = { dialog = MoodDialog.Rename(it) },
                        onAddAlias = { dialog = MoodDialog.AddAlias(levelMoods.first()) },
                        modifier = Modifier.animateItem(),
                    )
                }
            }
        }
    }

    when (val d = dialog) {
        null -> Unit
        is MoodDialog.Rename -> NameDialog(
            title = stringResource(R.string.moods_rename_title),
            initial = d.mood.label,
            onDismiss = { dialog = null },
            onConfirm = {
                viewModel.rename(d.mood.id, it)
                dialog = null
            },
            onDelete = if (d.mood.isPrimary) {
                null
            } else {
                { viewModel.usageCount(d.mood) { usage -> dialog = MoodDialog.Delete(d.mood, usage) } }
            },
        )
        is MoodDialog.AddAlias -> NameDialog(
            title = stringResource(R.string.moods_new_alias_title, d.primary.label),
            initial = "",
            onDismiss = { dialog = null },
            onConfirm = {
                haptics.confirm()
                viewModel.addAlias(d.primary.level, it)
                dialog = null
            },
            onDelete = null,
        )
        is MoodDialog.Delete -> DeleteAliasDialog(
            alias = d.alias,
            usage = d.usage,
            onDismiss = { dialog = null },
            onDelete = {
                haptics.confirm()
                viewModel.deleteUnused(d.alias)
                dialog = null
            },
            onArchive = {
                haptics.confirm()
                viewModel.setArchived(d.alias, true)
                dialog = null
                scope.launch {
                    snackbar.currentSnackbarData?.dismiss()
                    val result = snackbar.showSnackbar(archivedMessage.format(d.alias.label), undoLabel, withDismissAction = true)
                    if (result == SnackbarResult.ActionPerformed) viewModel.setArchived(d.alias, false)
                }
            },
            onReplace = { dialog = MoodDialog.Replace(d.alias, d.usage) },
        )
        is MoodDialog.Replace -> ReplaceAliasDialog(
            alias = d.alias,
            moods = levels.orEmpty().flatten(),
            onDismiss = { dialog = null },
            onConfirm = { target ->
                haptics.confirm()
                dialog = null
                viewModel.replace(d.alias, target) { moved ->
                    scope.launch {
                        snackbar.currentSnackbarData?.dismiss()
                        snackbar.showSnackbar(resources.getQuantityString(R.plurals.moods_replaced, moved, moved, target.label), withDismissAction = true)
                    }
                }
            },
        )
    }
}

/** One level: its primary (renamable, never removed) and its aliases, plus a chip to add one. */
@Composable
private fun LevelCard(moods: List<Mood>, onRename: (Mood) -> Unit, onAddAlias: () -> Unit, modifier: Modifier = Modifier) {
    val primary = moods.first()
    val aliases = moods.drop(1)
    Surface(
        shape = RoundedCornerShape(28.dp),
        color = MaterialTheme.colorScheme.surfaceContainer,
        modifier = modifier.fillMaxWidth().animateContentSize(MaterialTheme.motionScheme.defaultSpatialSpec()),
    ) {
        Column(Modifier.padding(start = Spacing.l, end = Spacing.s, top = Spacing.m, bottom = Spacing.l)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                MoodBadge(primary, size = 48.dp)
                Column(Modifier.weight(1f).padding(start = Spacing.l)) {
                    Text(primary.label, style = MaterialTheme.typography.titleMediumEmphasized)
                    Text(
                        if (aliases.isEmpty()) {
                            stringResource(R.string.moods_no_aliases)
                        } else {
                            pluralStringResource(R.plurals.moods_alias_count, aliases.size, aliases.size)
                        },
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                IconButton(onClick = { onRename(primary) }) {
                    DakilioIcon(DakilioSymbols.Edit, contentDescription = stringResource(R.string.moods_rename_primary, primary.label))
                }
            }
            FlowRow(
                modifier = Modifier.padding(top = Spacing.m, end = Spacing.s),
                horizontalArrangement = Arrangement.spacedBy(Spacing.s),
                verticalArrangement = Arrangement.spacedBy(Spacing.s),
            ) {
                aliases.forEach { alias ->
                    AliasChip(label = alias.label, onClick = { onRename(alias) })
                }
                AliasChip(label = stringResource(R.string.moods_add_alias), onClick = onAddAlias, add = true)
            }
        }
    }
}

@Composable
private fun AliasChip(label: String, onClick: () -> Unit, add: Boolean = false) {
    val interaction = remember { MutableInteractionSource() }
    Surface(
        onClick = onClick,
        shape = CircleShape,
        color = if (add) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainerHighest,
        contentColor = if (add) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurface,
        interactionSource = interaction,
        modifier = Modifier.heightIn(min = 40.dp).pressScale(interaction, 0.92f),
    ) {
        Row(Modifier.padding(start = 12.dp, end = 16.dp, top = 9.dp, bottom = 9.dp), verticalAlignment = Alignment.CenterVertically) {
            DakilioIcon(if (add) DakilioSymbols.Add else DakilioSymbols.Edit, contentDescription = null, modifier = Modifier.size(18.dp))
            Text(label, style = MaterialTheme.typography.labelLarge, modifier = Modifier.padding(start = Spacing.s))
        }
    }
}

@Composable
private fun NameDialog(title: String, initial: String, onDismiss: () -> Unit, onConfirm: (String) -> Unit, onDelete: (() -> Unit)?) {
    val name = rememberTextFieldState(initial)
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            OutlinedTextField(
                state = name,
                label = { Text(stringResource(R.string.moods_name)) },
                lineLimits = TextFieldLineLimits.SingleLine,
                modifier = Modifier.fillMaxWidth(),
            )
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(name.text.toString()) }, enabled = name.text.isNotBlank()) {
                Text(stringResource(R.string.moods_save))
            }
        },
        dismissButton = {
            Row {
                if (onDelete != null) {
                    TextButton(onClick = onDelete) { Text(stringResource(R.string.moods_delete), color = MaterialTheme.colorScheme.error) }
                }
                TextButton(onClick = onDismiss) { Text(stringResource(R.string.moods_cancel)) }
            }
        },
    )
}
