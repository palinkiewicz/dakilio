package pl.dakil.dakilio.feature.keywords.impl

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MediumFlexibleTopAppBar
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedListItem
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import java.time.LocalDate
import java.time.YearMonth
import pl.dakil.dakilio.core.designsystem.component.SectionHeader
import pl.dakil.dakilio.core.designsystem.component.rememberHaptics
import pl.dakil.dakilio.core.designsystem.component.segmentedItemColors
import pl.dakil.dakilio.core.designsystem.icon.DakilioIcon
import pl.dakil.dakilio.core.designsystem.icon.DakilioSymbols
import pl.dakil.dakilio.core.designsystem.theme.Spacing
import pl.dakil.dakilio.core.model.Excerpt
import pl.dakil.dakilio.core.ui.Hint
import pl.dakil.dakilio.core.ui.component.EmptyState
import pl.dakil.dakilio.core.ui.mood.MoodBadge
import pl.dakil.dakilio.core.ui.rememberFormats
import pl.dakil.dakilio.core.ui.sharedContainer
import pl.dakil.dakilio.feature.entry.api.EntryEditorKey

/**
 * Older entries that mention a tag's keywords but lack the tag: each as a short excerpt around the
 * match, with a checkbox to opt it in (nothing is ticked at first) and a tap that opens the entry.
 */
@Composable
internal fun KeywordPastScreen(
    viewModel: KeywordPastViewModel,
    onBack: () -> Unit,
    onOpenEntry: (postId: Long) -> Unit,
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val finished by viewModel.finished.collectAsStateWithLifecycle()
    val haptics = rememberHaptics()
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()
    var confirm by rememberSaveable { mutableStateOf(false) }

    LaunchedEffect(finished) { if (finished) onBack() }
    val current = state
    // The tag was deleted or archived meanwhile: nothing left to add.
    LaunchedEffect(current != null && current.tag == null) { if (current != null && current.tag == null) onBack() }

    Scaffold(
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        topBar = {
            MediumFlexibleTopAppBar(
                title = { Text(stringResource(R.string.keywords_review_title)) },
                subtitle = current?.tag?.let { tag ->
                    { Text(pluralStringResource(R.plurals.keywords_review_subtitle, current.rows.size, current.rows.size, tag.label)) }
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        DakilioIcon(DakilioSymbols.ArrowBack, contentDescription = stringResource(R.string.keywords_back))
                    }
                },
                actions = {
                    if (current != null && current.selectable.isNotEmpty()) {
                        TextButton(onClick = {
                            haptics.toggle(!current.allSelected)
                            viewModel.toggleAll()
                        }) {
                            Text(stringResource(if (current.allSelected) R.string.keywords_review_select_none else R.string.keywords_review_select_all))
                        }
                    }
                },
                scrollBehavior = scrollBehavior,
            )
        },
        bottomBar = {
            if (current != null && current.selectable.isNotEmpty()) {
                Surface(color = MaterialTheme.colorScheme.surfaceContainer) {
                    Button(
                        onClick = {
                            haptics.tick()
                            confirm = true
                        },
                        enabled = current.selected.isNotEmpty(),
                        modifier = Modifier
                            .fillMaxWidth()
                            .navigationBarsPadding()
                            .padding(horizontal = Spacing.gutter, vertical = Spacing.m),
                    ) {
                        DakilioIcon(DakilioSymbols.History, contentDescription = null, modifier = Modifier.size(18.dp))
                        Text(
                            if (current.selected.isEmpty()) {
                                stringResource(R.string.keywords_review_add_none)
                            } else {
                                pluralStringResource(R.plurals.keywords_review_add, current.selected.size, current.selected.size)
                            },
                            modifier = Modifier.padding(start = Spacing.s),
                        )
                    }
                }
            }
        },
    ) { padding ->
        Box(Modifier.fillMaxSize().padding(top = padding.calculateTopPadding()), contentAlignment = Alignment.TopCenter) {
            val tag = current?.tag
            when {
                current == null || tag == null -> LoadingIndicator(Modifier.align(Alignment.Center))
                current.rows.isEmpty() -> EmptyState(
                    symbol = DakilioSymbols.MatchWord,
                    title = stringResource(R.string.keywords_review_empty),
                    body = stringResource(R.string.keywords_sheet_past_none, tag.label),
                    modifier = Modifier.align(Alignment.Center),
                )
                else -> PastList(
                    rows = current.rows,
                    selected = current.selected,
                    bottomPadding = padding.calculateBottomPadding(),
                    onToggle = { postId, checked ->
                        haptics.toggle(checked)
                        viewModel.toggle(postId)
                    },
                    onOpen = onOpenEntry,
                )
            }
        }
    }

    val ready = current?.tag
    if (confirm && ready != null) {
        val count = current.selected.size
        AlertDialog(
            onDismissRequest = { confirm = false },
            icon = { DakilioIcon(DakilioSymbols.History, contentDescription = null) },
            title = { Text(stringResource(R.string.keywords_past_title)) },
            text = { Text(pluralStringResource(R.plurals.keywords_review_confirm_body, count, count, ready.label)) },
            confirmButton = {
                TextButton(onClick = {
                    confirm = false
                    haptics.confirm()
                    viewModel.addTag()
                }) { Text(stringResource(R.string.keywords_past_confirm)) }
            },
            dismissButton = {
                TextButton(onClick = { confirm = false }) { Text(stringResource(R.string.keywords_past_skip)) }
            },
        )
    }
}

@Composable
private fun PastList(
    rows: List<PastRow>,
    selected: Set<Long>,
    bottomPadding: androidx.compose.ui.unit.Dp,
    onToggle: (postId: Long, checked: Boolean) -> Unit,
    onOpen: (postId: Long) -> Unit,
) {
    val formats = rememberFormats()
    val today = remember { LocalDate.now() }
    val months = remember(rows) { rows.groupBy { YearMonth.from(it.date) }.toList() }

    LazyColumn(
        modifier = Modifier.widthIn(max = Spacing.maxContentWidth).fillMaxSize(),
        contentPadding = PaddingValues(
            start = Spacing.gutter,
            end = Spacing.gutter,
            top = Spacing.s,
            bottom = bottomPadding + Spacing.l,
        ),
        verticalArrangement = Arrangement.spacedBy(ListItemDefaults.SegmentedGap),
    ) {
        item(key = "hint") {
            Hint {
                Text(
                    stringResource(R.string.keywords_review_hint),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(start = Spacing.xs, end = Spacing.xs, bottom = Spacing.s),
                )
            }
        }
        months.forEachIndexed { monthIndex, (month, monthRows) ->
            item(key = "month-$month", contentType = "month") {
                SectionHeader(
                    formats.monthYear(month.atDay(1)),
                    Modifier.padding(start = Spacing.xs, top = if (monthIndex == 0) 0.dp else Spacing.m, bottom = Spacing.xs),
                )
            }
            itemsIndexed(monthRows, key = { _, row -> row.postId }, contentType = { _, _ -> "row" }) { index, row ->
                val checked = row.postId in selected
                val date = if (row.date.year == today.year) formats.dayMonthShort(row.date) else formats.dayMonthYear(row.date)
                SegmentedListItem(
                    onClick = { onOpen(row.postId) },
                    shapes = ListItemDefaults.segmentedShapes(index, monthRows.size),
                    colors = segmentedItemColors(),
                    modifier = Modifier.sharedContainer(EntryEditorKey.postSharedElementKey(row.postId), RoundedCornerShape(12.dp)),
                    leadingContent = {
                        Checkbox(
                            checked = checked || row.tagged,
                            onCheckedChange = { onToggle(row.postId, it) },
                            enabled = !row.tagged,
                        )
                    },
                    trailingContent = row.mood?.let { mood -> { MoodBadge(mood, size = 28.dp) } },
                    supportingContent = {
                        Text(
                            highlighted(row.excerpt),
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                        )
                    },
                ) {
                    Text(
                        if (row.tagged) stringResource(R.string.keywords_review_tagged, date) else date,
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

/** The excerpt with its matched words marked, like search marks its query. */
@Composable
private fun highlighted(excerpt: Excerpt): AnnotatedString {
    val colors = MaterialTheme.colorScheme
    return remember(excerpt, colors.tertiaryContainer) {
        buildAnnotatedString {
            append(excerpt.text)
            excerpt.ranges.forEach { range ->
                addStyle(
                    SpanStyle(background = colors.tertiaryContainer, color = colors.onTertiaryContainer),
                    range.first,
                    range.last + 1,
                )
            }
        }
    }
}
