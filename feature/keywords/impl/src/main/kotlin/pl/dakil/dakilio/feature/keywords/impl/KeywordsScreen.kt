package pl.dakil.dakilio.feature.keywords.impl

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
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
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.launch
import pl.dakil.dakilio.core.designsystem.component.ConnectedToggleRow
import pl.dakil.dakilio.core.designsystem.component.ShapedIcon
import pl.dakil.dakilio.core.designsystem.component.ToggleOption
import pl.dakil.dakilio.core.designsystem.component.segmentedItemColors
import pl.dakil.dakilio.core.designsystem.icon.DakilioIcon
import pl.dakil.dakilio.core.designsystem.icon.DakilioSymbols
import pl.dakil.dakilio.core.designsystem.icon.symbolForKey
import pl.dakil.dakilio.core.designsystem.theme.Spacing
import pl.dakil.dakilio.core.model.Keyword
import pl.dakil.dakilio.core.model.KeywordDraft
import pl.dakil.dakilio.core.ui.Hint
import pl.dakil.dakilio.core.ui.color.tagColors
import pl.dakil.dakilio.core.ui.component.EmptyState
import pl.dakil.dakilio.core.ui.component.CountPill
import pl.dakil.dakilio.feature.keywords.api.KeywordPastKey
import pl.dakil.dakilio.feature.keywords.api.KeywordWord

/** What the keyword sheet is open for. Editing goes by tag id, so the sheet follows live changes. */
private sealed interface SheetTarget {
    data object New : SheetTarget
    data class Edit(val tagId: Long) : SheetTarget
}

/** Keeps the sheet open across a trip to the older-entries review and back. */
private val SheetTargetSaver = Saver<SheetTarget?, Long>(
    save = { target -> if (target is SheetTarget.Edit) target.tagId else NEW_SHEET },
    restore = { id -> if (id == NEW_SHEET) SheetTarget.New else SheetTarget.Edit(id) },
)

private const val NEW_SHEET = -1L

/**
 * More → Extras → Keywords: words that suggest a tag when they show up in an entry's title or note,
 * and whether the editor suggests the tag or adds it at once.
 */
@Composable
internal fun KeywordsScreen(viewModel: KeywordsViewModel, onBack: () -> Unit, onReviewPast: (KeywordPastKey) -> Unit) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val pastPreview by viewModel.pastPreview.collectAsStateWithLifecycle()
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()
    val listState = rememberLazyListState()
    val snackbar = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    var sheet by rememberSaveable(stateSaver = SheetTargetSaver) { mutableStateOf<SheetTarget?>(null) }

    val clearedMessage = stringResource(R.string.keywords_cleared)
    val undoLabel = stringResource(R.string.keywords_undo)

    Scaffold(
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        snackbarHost = { SnackbarHost(snackbar) },
        topBar = {
            MediumFlexibleTopAppBar(
                title = { Text(stringResource(R.string.keywords_title)) },
                subtitle = { Text(stringResource(R.string.keywords_subtitle)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        DakilioIcon(DakilioSymbols.ArrowBack, contentDescription = stringResource(R.string.keywords_back))
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
                text = { Text(stringResource(R.string.keywords_new)) },
            )
        },
    ) { padding ->
        val current = state
        Box(Modifier.fillMaxSize().padding(top = padding.calculateTopPadding()), contentAlignment = Alignment.TopCenter) {
            when {
                current == null -> LoadingIndicator(Modifier.align(Alignment.Center))
                current.tags.isEmpty() -> EmptyState(
                    symbol = DakilioSymbols.MatchWord,
                    title = stringResource(R.string.keywords_empty_title),
                    body = stringResource(R.string.keywords_empty_body),
                    modifier = Modifier.align(Alignment.Center),
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
                    item(key = "mode") {
                        ModePicker(
                            autoAdd = current.autoAdd,
                            onAutoAdd = viewModel::setAutoAdd,
                            modifier = Modifier.padding(bottom = Spacing.m),
                        )
                    }
                    item(key = "hint") {
                        Hint {
                            Text(
                                stringResource(R.string.keywords_hint),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(start = Spacing.xs, end = Spacing.xs, bottom = Spacing.m),
                            )
                        }
                    }
                    itemsIndexed(current.tags, key = { _, it -> it.tag.id }) { index, item ->
                        TagKeywordsItem(
                            item = item,
                            index = index,
                            count = current.tags.size,
                            onClick = { sheet = SheetTarget.Edit(item.tag.id) },
                            modifier = Modifier.animateItem(),
                        )
                    }
                }
            }
        }
    }


    LaunchedEffect(sheet) { if (sheet !is SheetTarget.Edit) viewModel.queryPast(null, emptyList()) }

    val current = state ?: return
    when (val target = sheet) {
        null -> Unit
        SheetTarget.New -> KeywordSheet(
            existing = null,
            groups = current.groups,
            taggedIds = current.taggedIds,
            onSave = { tag, drafts ->
                viewModel.save(tag, drafts)
                sheet = null
            },
            onClear = {},
            onDismiss = { sheet = null },
            past = null,
            onQueryPast = {},
            onReviewPast = {},
        )
        is SheetTarget.Edit -> {
            val item = current.tags.firstOrNull { it.tag.id == target.tagId }
            // Gone (its tag was deleted or archived elsewhere): close.
            LaunchedEffect(item == null) { if (item == null) sheet = null }
            if (item != null) {
                KeywordSheet(
                    existing = item,
                    groups = current.groups,
                    taggedIds = current.taggedIds,
                    onSave = { tag, drafts ->
                        val unchanged = drafts == item.keywords.map { KeywordDraft(it.word, it.approximate) }
                        if (!unchanged) viewModel.save(tag, drafts)
                        sheet = null
                    },
                    onClear = {
                        sheet = null
                        val removed: List<Keyword> = item.keywords
                        viewModel.clear(item.tag)
                        scope.launch {
                            snackbar.currentSnackbarData?.dismiss()
                            val result = snackbar.showSnackbar(clearedMessage, undoLabel, withDismissAction = true)
                            if (result == SnackbarResult.ActionPerformed) viewModel.restore(item.tag, removed)
                        }
                    },
                    onDismiss = { sheet = null },
                    past = pastPreview,
                    onQueryPast = { drafts -> viewModel.queryPast(item.tag.id, drafts) },
                    onReviewPast = { drafts ->
                        onReviewPast(KeywordPastKey(item.tag.id, drafts.map { KeywordWord(it.word, it.approximate) }))
                    },
                )
            }
        }
    }
}

/** Suggest the tag, or add it at once. */
@Composable
private fun ModePicker(autoAdd: Boolean, onAutoAdd: (Boolean) -> Unit, modifier: Modifier = Modifier) {
    Column(modifier.fillMaxWidth()) {
        Text(
            stringResource(R.string.keywords_mode_title),
            style = MaterialTheme.typography.titleSmall,
            modifier = Modifier.padding(start = Spacing.xs, bottom = Spacing.s),
        )
        ConnectedToggleRow(
            options = listOf(
                ToggleOption(false, stringResource(R.string.keywords_mode_suggest)),
                ToggleOption(true, stringResource(R.string.keywords_mode_auto)),
            ),
            selected = autoAdd,
            onSelect = onAutoAdd,
        )
        Text(
            stringResource(if (autoAdd) R.string.keywords_mode_auto_body else R.string.keywords_mode_suggest_body),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(start = Spacing.xs, end = Spacing.xs, top = Spacing.s),
        )
    }
}

@Composable
private fun TagKeywordsItem(
    item: TagKeywords,
    index: Int,
    count: Int,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = tagColors(item.tag.color)
    SegmentedListItem(
        onClick = onClick,
        shapes = ListItemDefaults.segmentedShapes(index, count),
        colors = segmentedItemColors(),
        modifier = modifier,
        leadingContent = {
            ShapedIcon(
                symbolForKey(item.tag.iconKey),
                polygon = MaterialShapes.Circle,
                size = 48.dp,
                containerColor = colors?.container ?: MaterialTheme.colorScheme.secondaryContainer,
                contentColor = colors?.onContainer ?: MaterialTheme.colorScheme.onSecondaryContainer,
            )
        },
        supportingContent = {
            Column(Modifier.padding(top = Spacing.xs)) {
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    item.keywords.forEach { keyword ->
                        CountPill(keywordLabel(keyword.word, keyword.approximate))
                    }
                }
            }
        },
    ) { Text(item.tag.label) }
}

/** An approximate keyword reads as the start of a word: "rower…". */
internal fun keywordLabel(word: String, approximate: Boolean): String = if (approximate) "$word…" else word
