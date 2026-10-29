package pl.dakil.dakilio.feature.diaries.impl

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
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
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import pl.dakil.dakilio.core.designsystem.component.ShapedIcon
import pl.dakil.dakilio.core.designsystem.component.segmentedItemColors
import pl.dakil.dakilio.core.designsystem.icon.DakilioIcon
import pl.dakil.dakilio.core.designsystem.icon.DakilioSymbols
import pl.dakil.dakilio.core.designsystem.icon.symbolForKey
import pl.dakil.dakilio.core.designsystem.theme.Spacing

@Composable
internal fun DiariesScreen(viewModel: DiariesViewModel, onBack: () -> Unit, onOpenDiary: (Long?) -> Unit) {
    val rows by viewModel.rows.collectAsStateWithLifecycle()
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()
    val listState = rememberLazyListState()
    Scaffold(
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        topBar = {
            MediumFlexibleTopAppBar(
                title = { Text(stringResource(R.string.diaries_title)) },
                subtitle = { Text(stringResource(R.string.diaries_subtitle)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        DakilioIcon(DakilioSymbols.ArrowBack, contentDescription = stringResource(R.string.diaries_back))
                    }
                },
                scrollBehavior = scrollBehavior,
            )
        },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = { onOpenDiary(null) },
                expanded = !listState.canScrollBackward,
                icon = { DakilioIcon(DakilioSymbols.Add, contentDescription = null) },
                text = { Text(stringResource(R.string.diaries_new)) },
            )
        },
    ) { padding ->
        val current = rows
        Box(Modifier.fillMaxSize().padding(top = padding.calculateTopPadding()), contentAlignment = Alignment.TopCenter) {
            if (current == null) {
                LoadingIndicator(Modifier.align(Alignment.Center))
                return@Box
            }
            LazyColumn(
                state = listState,
                modifier = Modifier.widthIn(max = Spacing.maxContentWidth).fillMaxSize(),
                contentPadding = PaddingValues(start = Spacing.gutter, end = Spacing.gutter, top = Spacing.s, bottom = padding.calculateBottomPadding() + 112.dp),
                verticalArrangement = Arrangement.spacedBy(ListItemDefaults.SegmentedGap),
            ) {
                itemsIndexed(current, key = { _, row -> row.diary.id }) { index, row ->
                    SegmentedListItem(
                        onClick = { onOpenDiary(row.diary.id) },
                        shapes = ListItemDefaults.segmentedShapes(index, current.size),
                        colors = segmentedItemColors(),
                        modifier = Modifier.animateItem(),
                        leadingContent = {
                            ShapedIcon(
                                symbolForKey(row.diary.iconKey),
                                polygon = MaterialShapes.Cookie9Sided,
                                size = 48.dp,
                                containerColor = MaterialTheme.colorScheme.primaryContainer,
                                contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                            )
                        },
                        supportingContent = {
                            val suggested = if (row.allGroupsSuggested) {
                                stringResource(R.string.diaries_all_groups)
                            } else {
                                pluralStringResource(R.plurals.diaries_suggested, row.suggestedCount, row.suggestedCount)
                            }
                            Text(pluralStringResource(R.plurals.diaries_entries, row.entryCount, row.entryCount) + " · " + suggested)
                        },
                        trailingContent = { DakilioIcon(DakilioSymbols.ChevronRight, contentDescription = null) },
                    ) { Text(row.diary.name, style = MaterialTheme.typography.titleMediumEmphasized) }
                }
                item(key = "hint") {
                    Text(
                        stringResource(R.string.diaries_hint),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = Spacing.xs, vertical = Spacing.l).animateItem(),
                    )
                }
            }
        }
    }
}
