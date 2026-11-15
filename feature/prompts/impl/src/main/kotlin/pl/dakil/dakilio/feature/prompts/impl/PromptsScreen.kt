package pl.dakil.dakilio.feature.prompts.impl

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.Button
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialShapes
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MediumFlexibleTopAppBar
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedListItem
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.launch
import pl.dakil.dakilio.core.designsystem.component.ShapedIcon
import pl.dakil.dakilio.core.designsystem.component.segmentedItemColors
import pl.dakil.dakilio.core.designsystem.icon.DakilioIcon
import pl.dakil.dakilio.core.designsystem.icon.DakilioSymbols
import pl.dakil.dakilio.core.designsystem.theme.Spacing
import pl.dakil.dakilio.core.model.Mood
import pl.dakil.dakilio.core.model.Prompt
import pl.dakil.dakilio.core.ui.Hint
import pl.dakil.dakilio.core.ui.component.EmptyState
import pl.dakil.dakilio.core.ui.mood.MoodBadge

/** What the prompt sheet is open for. Editing goes by id, so the sheet follows live changes. */
private sealed interface SheetTarget {
    data object New : SheetTarget
    data class Edit(val promptId: Long) : SheetTarget
    data object Starters : SheetTarget
}

/**
 * More → Extras → Prompts: the user's questions for the blank page, each for any mood or for
 * chosen ones. Ready-made starter prompts are an action away.
 */
@Composable
internal fun PromptsScreen(viewModel: PromptsViewModel, onBack: () -> Unit) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()
    val listState = rememberLazyListState()
    val snackbar = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    var sheet by remember { mutableStateOf<SheetTarget?>(null) }

    val deletedMessage = stringResource(R.string.prompts_deleted)
    val undoLabel = stringResource(R.string.prompts_undo)

    Scaffold(
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        snackbarHost = { SnackbarHost(snackbar) },
        topBar = {
            MediumFlexibleTopAppBar(
                title = { Text(stringResource(R.string.prompts_title)) },
                subtitle = { Text(stringResource(R.string.prompts_subtitle)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        DakilioIcon(DakilioSymbols.ArrowBack, contentDescription = stringResource(R.string.prompts_back))
                    }
                },
                actions = {
                    IconButton(onClick = { sheet = SheetTarget.Starters }) {
                        DakilioIcon(DakilioSymbols.TipsAndUpdates, contentDescription = stringResource(R.string.prompts_starters))
                    }
                },
                scrollBehavior = scrollBehavior,
            )
        },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = { sheet = SheetTarget.New },
                expanded = !listState.canScrollBackward,
                icon = { DakilioIcon(DakilioSymbols.Add, contentDescription = null) },
                text = { Text(stringResource(R.string.prompts_new)) },
            )
        },
    ) { padding ->
        val current = state
        Box(Modifier.fillMaxSize().padding(top = padding.calculateTopPadding()), contentAlignment = Alignment.TopCenter) {
            when {
                current == null -> LoadingIndicator(Modifier.align(Alignment.Center))
                current.prompts.isEmpty() -> EmptyState(
                    symbol = DakilioSymbols.Lightbulb,
                    title = stringResource(R.string.prompts_empty_title),
                    body = stringResource(R.string.prompts_empty_body),
                    modifier = Modifier.align(Alignment.Center),
                    action = {
                        Button(onClick = { sheet = SheetTarget.Starters }) {
                            DakilioIcon(DakilioSymbols.TipsAndUpdates, contentDescription = null, modifier = Modifier.padding(end = Spacing.s))
                            Text(stringResource(R.string.prompts_starters))
                        }
                    },
                )
                else -> LazyColumn(
                    state = listState,
                    modifier = Modifier.widthIn(max = Spacing.maxContentWidth).fillMaxSize(),
                    contentPadding = PaddingValues(
                        start = Spacing.gutter,
                        end = Spacing.gutter,
                        top = Spacing.s,
                        // Room for the FAB.
                        bottom = padding.calculateBottomPadding() + 96.dp,
                    ),
                    verticalArrangement = Arrangement.spacedBy(ListItemDefaults.SegmentedGap),
                ) {
                    item(key = "hint") {
                        Hint {
                            Text(
                                stringResource(R.string.prompts_hint),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(start = Spacing.xs, end = Spacing.xs, bottom = Spacing.m),
                            )
                        }
                    }
                    itemsIndexed(current.prompts, key = { _, it -> it.id }) { index, prompt ->
                        PromptItem(
                            prompt = prompt,
                            moods = current.moods,
                            index = index,
                            count = current.prompts.size,
                            onClick = { sheet = SheetTarget.Edit(prompt.id) },
                            modifier = Modifier.animateItem(),
                        )
                    }
                }
            }
        }
    }

    val current = state ?: return
    when (val target = sheet) {
        null -> Unit
        SheetTarget.New -> PromptSheet(
            prompt = null,
            moods = current.moods,
            onSave = { draft ->
                viewModel.add(listOf(draft))
                sheet = null
            },
            onDelete = {},
            onDismiss = { sheet = null },
        )
        is SheetTarget.Edit -> {
            val prompt = current.prompts.firstOrNull { it.id == target.promptId }
            // Gone (a restore replaced the prompts meanwhile): close.
            LaunchedEffect(prompt == null) { if (prompt == null) sheet = null }
            if (prompt != null) {
                PromptSheet(
                    prompt = prompt,
                    moods = current.moods,
                    onSave = { draft ->
                        viewModel.update(prompt.id, draft)
                        sheet = null
                    },
                    onDelete = {
                        sheet = null
                        viewModel.delete(prompt)
                        scope.launch {
                            snackbar.currentSnackbarData?.dismiss()
                            val result = snackbar.showSnackbar(deletedMessage, undoLabel, withDismissAction = true)
                            if (result == SnackbarResult.ActionPerformed) viewModel.restore(prompt)
                        }
                    },
                    onDismiss = { sheet = null },
                )
            }
        }
        SheetTarget.Starters -> StarterPromptsSheet(
            existing = current.keys,
            moods = current.moods,
            onAdd = { drafts ->
                viewModel.add(drafts)
                sheet = null
            },
            onDismiss = { sheet = null },
        )
    }
}

@Composable
private fun PromptItem(prompt: Prompt, moods: List<Mood>, index: Int, count: Int, onClick: () -> Unit, modifier: Modifier = Modifier) {
    SegmentedListItem(
        onClick = onClick,
        shapes = ListItemDefaults.segmentedShapes(index, count),
        colors = segmentedItemColors(),
        modifier = modifier,
        leadingContent = { ShapedIcon(DakilioSymbols.Lightbulb, polygon = MaterialShapes.Cookie9Sided) },
        supportingContent = { PromptMoods(prompt.levels, moods) },
    ) { Text(prompt.text) }
}

/** Which moods a prompt is for: "Any mood", or the faces of its levels. */
@Composable
internal fun PromptMoods(levels: Set<Int>, moods: List<Mood>, modifier: Modifier = Modifier) {
    val shown = moods.filter { it.level in levels }
    if (shown.size == moods.size) {
        Text(stringResource(R.string.prompts_any_mood), modifier = modifier)
    } else {
        Row(modifier.padding(top = Spacing.xs), horizontalArrangement = Arrangement.spacedBy(Spacing.xs)) {
            shown.forEach { MoodBadge(it, size = 20.dp) }
        }
    }
}
