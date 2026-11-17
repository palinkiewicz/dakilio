package pl.dakil.dakilio.feature.insights.impl.tags

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialShapes
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation3.runtime.NavKey
import pl.dakil.dakilio.feature.insights.api.InsightsTagKey
import java.time.DayOfWeek
import java.time.LocalDate
import pl.dakil.dakilio.core.designsystem.component.rememberHaptics
import pl.dakil.dakilio.core.designsystem.icon.DakilioSymbols
import pl.dakil.dakilio.core.designsystem.theme.SegmentedShapes
import pl.dakil.dakilio.core.designsystem.theme.Spacing
import pl.dakil.dakilio.core.ui.DakilioFormats
import pl.dakil.dakilio.core.ui.component.SelectListItem
import pl.dakil.dakilio.core.ui.component.SelectOption
import pl.dakil.dakilio.core.ui.rememberFormats
import pl.dakil.dakilio.feature.insights.impl.InsightsDetailScaffold
import pl.dakil.dakilio.feature.insights.impl.R
import pl.dakil.dakilio.feature.insights.impl.logic.TagSort
import pl.dakil.dakilio.feature.insights.impl.logic.TagStat
import pl.dakil.dakilio.feature.insights.impl.overview.CardNote
import pl.dakil.dakilio.feature.insights.impl.overview.EmptyPeriodCard
import pl.dakil.dakilio.feature.insights.impl.overview.LinkRow
import pl.dakil.dakilio.feature.insights.impl.overview.TagIcon
import pl.dakil.dakilio.feature.insights.impl.overview.TagUseRow

@Composable
private fun sortLabel(sort: TagSort): String = stringResource(
    when (sort) {
        TagSort.MostUsed -> R.string.insights_sort_most_used
        TagSort.Brightest -> R.string.insights_sort_brightest
        TagSort.Heaviest -> R.string.insights_sort_heaviest
        TagSort.Change -> R.string.insights_sort_change
        TagSort.LastUsed -> R.string.insights_sort_last_used
    },
)

/** Every tag used in the period, in the order picked; a tag opens its own screen. */
@Composable
internal fun InsightsTagsScreen(viewModel: InsightsTagsViewModel, onBack: () -> Unit, onOpen: (NavKey) -> Unit) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val formats = rememberFormats()
    val haptics = rememberHaptics()
    val ready = state as? InsightsTagsUiState.Ready
    InsightsDetailScaffold(
        title = stringResource(R.string.insights_tags_title),
        header = ready?.header,
        moods = ready?.moods.orEmpty(),
        firstDay = ready?.firstDay ?: DayOfWeek.MONDAY,
        viewModel = viewModel,
        onBack = onBack,
        itemSpacing = 0.dp, // the list is one segmented group
    ) {
        val current = ready ?: return@InsightsDetailScaffold
        item(key = "sort") {
            SelectListItem(
                title = stringResource(R.string.insights_sort),
                body = null,
                symbol = DakilioSymbols.SwapVert,
                polygon = MaterialShapes.Cookie9Sided,
                selected = current.sort,
                // `map` is inline, so each label can be composed inside it.
                options = current.sorts.map { SelectOption(it, sortLabel(it)) },
                onSelect = viewModel::setSort,
                modifier = Modifier.padding(top = Spacing.xs),
            )
        }
        if (current.groups.size > 1) {
            item(key = "groups") {
                Row(
                    Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(top = Spacing.s),
                    horizontalArrangement = Arrangement.spacedBy(Spacing.s),
                ) {
                    GroupChip(stringResource(R.string.insights_tags_all_groups), current.selectedGroupId == null) {
                        haptics.tick()
                        viewModel.selectGroup(null)
                    }
                    current.groups.forEach { group ->
                        GroupChip(group.name, current.selectedGroupId == group.id) {
                            haptics.tick()
                            viewModel.selectGroup(group.id)
                        }
                    }
                }
            }
        }
        if (current.tags.isEmpty()) {
            item(key = "empty") {
                val note = when (current.sort) {
                    TagSort.Brightest, TagSort.Heaviest -> stringResource(R.string.insights_links_need)
                    else -> stringResource(R.string.insights_no_tags)
                }
                EmptyPeriodCardSpaced(note)
            }
        }
        itemsIndexed(current.tags, key = { _, stat -> stat.tag.id }) { index, stat ->
            Surface(
                onClick = { onOpen(InsightsTagKey(viewModel.scope, stat.tag.id)) },
                shape = SegmentedShapes.of(index, current.tags.size),
                color = MaterialTheme.colorScheme.surfaceContainer,
                modifier = Modifier.animateItem().padding(top = if (index == 0) Spacing.m else SegmentedShapes.gap).fillMaxWidth(),
            ) {
                Column(Modifier.padding(horizontal = Spacing.l, vertical = Spacing.m)) {
                    when (current.sort) {
                        TagSort.Brightest, TagSort.Heaviest -> LinkRow(stat, current.reach, current.moods, formats, onClick = null)
                        TagSort.LastUsed -> LastUsedRow(stat, current.today, formats)
                        TagSort.MostUsed, TagSort.Change -> TagUseRow(stat, current.maxDays.toFloat(), onClick = null)
                    }
                }
            }
        }
        if (current.withoutLink > 0 && current.tags.isNotEmpty()) {
            item(key = "without-link") {
                CardNote(
                    pluralStringResource(R.plurals.insights_tags_without_link, current.withoutLink, current.withoutLink),
                    Modifier.padding(top = Spacing.m, start = Spacing.xs),
                )
            }
        }
    }
}

@Composable
private fun EmptyPeriodCardSpaced(note: String) {
    Column(Modifier.padding(top = Spacing.m)) { EmptyPeriodCard(note, title = stringResource(R.string.insights_tags_none)) }
}

@Composable
private fun GroupChip(label: String, selected: Boolean, onClick: () -> Unit) {
    FilterChip(selected = selected, onClick = onClick, label = { Text(label, maxLines = 1) }, shape = CircleShape)
}

/** A tag with the day it was last on. */
@Composable
private fun LastUsedRow(stat: TagStat, today: LocalDate, formats: DakilioFormats) {
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
        TagIcon(stat.tag)
        Column(Modifier.padding(start = Spacing.m).weight(1f)) {
            Text(stat.tag.label, style = MaterialTheme.typography.labelLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(
                stringResource(R.string.insights_last_used, formats.fullDate(stat.lastUsed, today)),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Text(
            pluralStringResource(R.plurals.insights_days_count, stat.days, stat.days),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(start = Spacing.s),
        )
    }
}
