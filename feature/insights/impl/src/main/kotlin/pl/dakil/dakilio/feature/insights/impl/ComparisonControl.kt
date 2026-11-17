package pl.dakil.dakilio.feature.insights.impl

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.SegmentedListItem
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import java.time.DayOfWeek
import pl.dakil.dakilio.core.designsystem.component.rememberHaptics
import pl.dakil.dakilio.core.designsystem.component.segmentedItemColors
import pl.dakil.dakilio.core.designsystem.icon.DakilioIcon
import pl.dakil.dakilio.core.designsystem.icon.DakilioSymbol
import pl.dakil.dakilio.core.designsystem.icon.DakilioSymbols
import pl.dakil.dakilio.core.designsystem.theme.Spacing
import pl.dakil.dakilio.core.model.Mood
import pl.dakil.dakilio.core.ui.DakilioFormats
import pl.dakil.dakilio.feature.insights.impl.logic.Comparison
import pl.dakil.dakilio.feature.insights.impl.logic.InsightsPeriod
import pl.dakil.dakilio.feature.insights.impl.logic.comparisonPeriod
import pl.dakil.dakilio.feature.insights.impl.overview.comparisonLabel
import pl.dakil.dakilio.feature.insights.impl.overview.comparisonRangeOf

/**
 * What the period is measured against, as a chip: "Compared with October 2026 ▾", or "Compare…"
 * without a comparison. It names just the period; the sheet a tap opens tells the details. All time
 * has nothing before it, so it shows nothing.
 */
@Composable
internal fun ComparisonChip(
    header: PeriodHeaderState,
    moods: List<Mood>,
    firstDay: DayOfWeek,
    formats: DakilioFormats,
    onComparison: (Comparison) -> Unit,
    modifier: Modifier = Modifier,
    containerColor: Color = MaterialTheme.colorScheme.surfaceContainerHighest,
    contentColor: Color = MaterialTheme.colorScheme.onSurface,
) {
    val label = comparisonPeriod(header.period, header.comparison)?.let { periodLabel(it, header.bounds, formats) }
    ComparisonPicker(header, moods, firstDay, formats, onComparison) { open ->
        Surface(
            onClick = open,
            shape = CircleShape,
            color = containerColor,
            contentColor = contentColor,
            modifier = modifier.semantics { role = Role.DropdownList },
        ) {
            Row(
                Modifier.padding(start = Spacing.m, end = Spacing.s, top = Spacing.s, bottom = Spacing.s),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                DakilioIcon(DakilioSymbols.CompareArrows, contentDescription = null, modifier = Modifier.size(18.dp))
                Text(
                    if (header.comparison == Comparison.None || label == null) stringResource(R.string.insights_compare) else stringResource(R.string.insights_compared_with, label),
                    style = MaterialTheme.typography.labelLarge,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(start = Spacing.s).weight(1f, fill = false),
                )
                DakilioIcon(DakilioSymbols.ArrowDropDown, contentDescription = null, modifier = Modifier.size(20.dp))
            }
        }
    }
}

/**
 * The comparison choices behind any [chip]: [chip] gets the action that opens the sheet, which in turn
 * can open a picker of the period's own kind. All time has nothing before it, so it shows nothing.
 */
@Composable
internal fun ComparisonPicker(
    header: PeriodHeaderState,
    moods: List<Mood>,
    firstDay: DayOfWeek,
    formats: DakilioFormats,
    onComparison: (Comparison) -> Unit,
    chip: @Composable (open: () -> Unit) -> Unit,
) {
    if (header.period == InsightsPeriod.AllTime) return
    var choosing by rememberSaveable { mutableStateOf(false) }
    var picking by rememberSaveable { mutableStateOf(false) }
    val haptics = rememberHaptics()
    chip {
        haptics.tick()
        choosing = true
    }
    if (choosing) {
        ComparisonSheet(
            header = header,
            formats = formats,
            onPick = {
                choosing = false
                onComparison(it)
            },
            onPickPeriod = {
                choosing = false
                picking = true
            },
            onDismiss = { choosing = false },
        )
    }
    if (picking) {
        // A picker of the period's own kind, starting from the current comparison.
        val start = comparisonPeriod(header.period, header.comparison) ?: header.period.let { comparisonPeriod(it, Comparison.Previous) } ?: header.period
        PeriodPicker(
            state = header.copy(period = start),
            moods = moods,
            firstDay = firstDay,
            formats = formats,
            onPick = {
                picking = false
                onComparison(Comparison.Picked(it))
            },
            onDismiss = { picking = false },
        )
    }
}

@Composable
private fun ComparisonSheet(
    header: PeriodHeaderState,
    formats: DakilioFormats,
    onPick: (Comparison) -> Unit,
    onPickPeriod: () -> Unit,
    onDismiss: () -> Unit,
) {
    val haptics = rememberHaptics()
    val bounds = header.bounds
    val current = header.comparison
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
        Column(Modifier.fillMaxWidth().padding(horizontal = Spacing.gutter).padding(bottom = Spacing.xl)) {
            Text(
                stringResource(R.string.insights_compare_title),
                style = MaterialTheme.typography.titleLargeEmphasized,
                modifier = Modifier.padding(start = Spacing.xs, bottom = Spacing.m),
            )
            // Named as the summary names them: "October 2026, first 16 days" while the period runs.
            val previous = header.copy(comparison = Comparison.Previous).let { comparisonLabel(it, comparisonRangeOf(it), formats) }
            val yearBefore = header.copy(comparison = Comparison.YearBefore).let { comparisonLabel(it, comparisonRangeOf(it), formats) }
            val previousTitle = stringResource(R.string.insights_compare_previous)
            val yearBeforeTitle = stringResource(R.string.insights_compare_year_before)
            val pickTitle = stringResource(R.string.insights_compare_pick)
            val noneTitle = stringResource(R.string.insights_compare_none)
            val pickedLabel = (current as? Comparison.Picked)?.let { periodLabel(it.period, bounds, formats) }
            val options = buildList {
                if (previous != null) add(Option(Comparison.Previous, DakilioSymbols.History, previousTitle, previous))
                // For a year, the year before is the period before: one row is enough.
                val sameAsPrevious = comparisonPeriod(header.period, Comparison.YearBefore) == comparisonPeriod(header.period, Comparison.Previous)
                if (yearBefore != null && !sameAsPrevious) add(Option(Comparison.YearBefore, DakilioSymbols.EventRepeat, yearBeforeTitle, yearBefore))
                add(Option(current as? Comparison.Picked, DakilioSymbols.CalendarMonth, pickTitle, pickedLabel))
                add(Option(Comparison.None, DakilioSymbols.Block, noneTitle, null))
            }
            Column(verticalArrangement = Arrangement.spacedBy(ListItemDefaults.SegmentedGap)) {
                options.forEachIndexed { index, option ->
                    val pickRow = option.value == null || option.value is Comparison.Picked
                    val selected = if (pickRow) current is Comparison.Picked else current == option.value
                    SegmentedListItem(
                        onClick = {
                            haptics.confirm()
                            if (pickRow) onPickPeriod() else onPick(option.value!!)
                        },
                        shapes = ListItemDefaults.segmentedShapes(index, options.size),
                        colors = segmentedItemColors(),
                        leadingContent = { DakilioIcon(option.symbol, contentDescription = null, filled = selected) },
                        supportingContent = option.detail?.let { { Text(it) } },
                        trailingContent = if (selected) ({ DakilioIcon(DakilioSymbols.Check, contentDescription = null, tint = MaterialTheme.colorScheme.primary) }) else null,
                        modifier = Modifier.semantics { this.selected = selected },
                    ) { Text(option.title) }
                }
            }
        }
    }
}

private class Option(val value: Comparison?, val symbol: DakilioSymbol, val title: String, val detail: String?)
