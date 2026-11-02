package pl.dakil.dakilio.feature.search.impl

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonGroupDefaults
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.DateRangePicker
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.SegmentedListItem
import androidx.compose.material3.SelectableDates
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.ToggleButton
import androidx.compose.material3.rememberDateRangePickerState
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import java.time.DayOfWeek
import java.time.Instant
import java.time.ZoneOffset
import pl.dakil.dakilio.core.designsystem.component.ConnectedToggleRow
import pl.dakil.dakilio.core.designsystem.component.SectionHeader
import pl.dakil.dakilio.core.designsystem.component.ToggleOption
import pl.dakil.dakilio.core.designsystem.component.pressScale
import pl.dakil.dakilio.core.designsystem.component.raisedToggleColors
import pl.dakil.dakilio.core.designsystem.component.rememberHaptics
import pl.dakil.dakilio.core.designsystem.component.segmentedItemColors
import pl.dakil.dakilio.core.designsystem.icon.DakilioIcon
import pl.dakil.dakilio.core.designsystem.icon.DakilioSymbols
import pl.dakil.dakilio.core.designsystem.icon.symbolForKey
import pl.dakil.dakilio.core.designsystem.theme.Spacing
import pl.dakil.dakilio.core.model.Mood
import pl.dakil.dakilio.core.model.Tag
import pl.dakil.dakilio.core.ui.color.tagColors
import pl.dakil.dakilio.core.ui.mood.MoodBadge
import pl.dakil.dakilio.core.ui.rememberFormats

/**
 * The sheet of one [facet]. Picks apply at once, and the header counts the matching entries live,
 * so the sheet doubles as a preview; Done only closes it.
 */
@Composable
internal fun FilterSheet(facet: Facet, state: SearchUiState, viewModel: SearchViewModel, onDismiss: () -> Unit) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val active = facetValues(facet, state, rememberFormats()).isNotEmpty()
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState) {
        Column(
            Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(horizontal = Spacing.xl)
                .padding(bottom = Spacing.l),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(facetName(facet), style = MaterialTheme.typography.headlineSmallEmphasized)
                    Text(
                        pluralStringResource(R.plurals.search_results, state.resultCount, state.resultCount),
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.animateContentSize(),
                    )
                }
                AnimatedVisibility(active) {
                    TextButton(onClick = { viewModel.clearFacet(facet) }) { Text(stringResource(R.string.search_clear)) }
                }
            }
            Spacer(Modifier.size(Spacing.m))
            when (facet) {
                Facet.Mood -> MoodFacet(state.moods, state.filters.moodLevels, viewModel::toggleMoodLevel)
                Facet.Tags -> TagsFacet(state, viewModel)
                Facet.Diary -> DiaryFacet(state, viewModel::toggleDiary)
                Facet.When -> WhenFacet(state, viewModel)
                Facet.Has -> HasFacet(state.filters.has, viewModel::toggleHas)
            }
            Button(onClick = onDismiss, modifier = Modifier.align(Alignment.End).padding(top = Spacing.l)) {
                Text(stringResource(R.string.search_sheet_done))
            }
        }
    }
}

/** The five faces, best first. Picked faces spring into their lively shape; the rest dim. */
@Composable
private fun MoodFacet(moods: List<Mood>, levels: Set<Int>, onToggle: (Int) -> Unit) {
    val haptics = rememberHaptics()
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        moods.asReversed().forEach { mood ->
            val selected = mood.level in levels
            val interaction = remember { MutableInteractionSource() }
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier
                    .weight(1f)
                    .pressScale(interaction, pressedScale = 0.9f)
                    .clip(MaterialTheme.shapes.large)
                    .clickable(interaction, indication = null, role = Role.Checkbox) {
                        haptics.toggle(!selected)
                        onToggle(mood.level)
                    }
                    .semantics { this.selected = selected }
                    .padding(vertical = Spacing.s),
            ) {
                MoodBadge(mood, size = 52.dp, active = selected, dimmed = if (levels.isNotEmpty() && !selected) 0.6f else 0f)
                Text(
                    mood.label,
                    style = if (selected) MaterialTheme.typography.labelLargeEmphasized else MaterialTheme.typography.labelLarge,
                    color = if (selected) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                    maxLines = 1,
                    modifier = Modifier.padding(top = Spacing.s),
                )
            }
        }
    }
}

@Composable
private fun TagsFacet(state: SearchUiState, viewModel: SearchViewModel) {
    val filters = state.filters
    AnimatedVisibility(filters.tagIds.size >= 2) {
        ConnectedToggleRow(
            options = listOf(
                ToggleOption(TagMatch.Any, stringResource(R.string.search_tag_match_any)),
                ToggleOption(TagMatch.All, stringResource(R.string.search_tag_match_all)),
            ),
            selected = filters.tagMatch,
            onSelect = viewModel::setTagMatch,
            modifier = Modifier.padding(bottom = Spacing.m),
        )
    }
    val groups = state.groups.filter { it.tags.isNotEmpty() }
    if (groups.isEmpty()) {
        Text(stringResource(R.string.search_no_tags), color = MaterialTheme.colorScheme.onSurfaceVariant)
        return
    }
    Column(
        Modifier.heightIn(max = 400.dp).verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(Spacing.s),
    ) {
        groups.forEach { group ->
            Text(group.name, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                group.tags.forEach { tag -> TagFilterChip(tag, tag.id in filters.tagIds) { viewModel.toggleTag(tag.id) } }
            }
        }
    }
}

/** A tag as a filter chip; once selected it fills with the tag's own color. */
@Composable
internal fun TagFilterChip(tag: Tag, selected: Boolean, modifier: Modifier = Modifier, trailing: String? = null, onClick: () -> Unit) {
    val haptics = rememberHaptics()
    val colors = tagColors(tag.color)
    FilterChip(
        selected = selected,
        onClick = {
            haptics.toggle(!selected)
            onClick()
        },
        label = { Text(if (trailing == null) tag.label else "${tag.label}  $trailing", maxLines = 1) },
        leadingIcon = {
            DakilioIcon(symbolForKey(tag.iconKey), contentDescription = null, filled = selected, modifier = Modifier.size(FilterChipDefaults.IconSize))
        },
        colors = if (colors == null) {
            FilterChipDefaults.filterChipColors()
        } else {
            FilterChipDefaults.filterChipColors(
                iconColor = colors.accent,
                selectedContainerColor = colors.container,
                selectedLabelColor = colors.onContainer,
                selectedLeadingIconColor = colors.onContainer,
            )
        },
        shape = CircleShape,
        modifier = modifier,
    )
}

@Composable
private fun DiaryFacet(state: SearchUiState, onToggle: (Long) -> Unit) {
    val haptics = rememberHaptics()
    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        state.diaries.forEach { diary ->
            val selected = diary.id in state.filters.diaryIds
            FilterChip(
                selected = selected,
                onClick = {
                    haptics.toggle(!selected)
                    onToggle(diary.id)
                },
                label = { Text(diary.name) },
                leadingIcon = {
                    DakilioIcon(symbolForKey(diary.iconKey), contentDescription = null, filled = selected, modifier = Modifier.size(FilterChipDefaults.IconSize))
                },
                shape = CircleShape,
            )
        }
    }
}

@Composable
private fun WhenFacet(state: SearchUiState, viewModel: SearchViewModel) {
    val haptics = rememberHaptics()
    val formats = rememberFormats()
    val period = state.filters.period
    var pickRange by rememberSaveable { mutableStateOf(false) }

    SectionHeader(stringResource(R.string.search_period))
    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        val presets = listOf(null, Period.Last7Days, Period.Last30Days, Period.ThisYear, Period.OnThisDay)
        presets.forEach { preset ->
            val selected = period == preset
            FilterChip(
                selected = selected,
                onClick = {
                    haptics.tick()
                    viewModel.setPeriod(preset)
                },
                label = { Text(if (preset == null) stringResource(R.string.search_period_any) else periodLabel(preset, formats)) },
                leadingIcon = if (preset == Period.OnThisDay) {
                    { DakilioIcon(DakilioSymbols.EventRepeat, contentDescription = null, filled = selected, modifier = Modifier.size(FilterChipDefaults.IconSize)) }
                } else {
                    null
                },
                shape = CircleShape,
            )
        }
        val custom = period as? Period.Custom
        FilterChip(
            selected = custom != null,
            onClick = {
                haptics.tick()
                pickRange = true
            },
            label = { Text(if (custom != null) periodLabel(custom, formats) else stringResource(R.string.search_period_custom)) },
            leadingIcon = {
                DakilioIcon(DakilioSymbols.CalendarToday, contentDescription = null, filled = custom != null, modifier = Modifier.size(FilterChipDefaults.IconSize))
            },
            shape = CircleShape,
        )
    }

    SectionHeader(stringResource(R.string.search_weekdays))
    WeekdayToggles(state.filters.weekdays, viewModel::toggleWeekday)

    if (pickRange) {
        val todayUtc = state.today.atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()
        val custom = period as? Period.Custom
        val pickerState = rememberDateRangePickerState(
            initialSelectedStartDateMillis = custom?.from?.atStartOfDay(ZoneOffset.UTC)?.toInstant()?.toEpochMilli(),
            initialSelectedEndDateMillis = custom?.to?.atStartOfDay(ZoneOffset.UTC)?.toInstant()?.toEpochMilli(),
            selectableDates = object : SelectableDates {
                override fun isSelectableDate(utcTimeMillis: Long) = utcTimeMillis <= todayUtc
                override fun isSelectableYear(year: Int) = year <= state.today.year
            },
        )
        DatePickerDialog(
            onDismissRequest = { pickRange = false },
            confirmButton = {
                TextButton(
                    enabled = pickerState.selectedStartDateMillis != null,
                    onClick = {
                        val from = pickerState.selectedStartDateMillis?.toUtcDate()
                        if (from != null) {
                            haptics.confirm()
                            viewModel.setPeriod(Period.Custom.of(from, pickerState.selectedEndDateMillis?.toUtcDate() ?: from))
                        }
                        pickRange = false
                    },
                ) { Text(stringResource(R.string.search_period_ok)) }
            },
            dismissButton = { TextButton(onClick = { pickRange = false }) { Text(stringResource(R.string.search_period_cancel)) } },
        ) {
            DateRangePicker(state = pickerState, modifier = Modifier.weight(1f))
        }
    }
}

private fun Long.toUtcDate() = Instant.ofEpochMilli(this).atZone(ZoneOffset.UTC).toLocalDate()

/** Seven connected toggles, Monday first; unlike [ConnectedToggleRow] any number can be on. */
@Composable
private fun WeekdayToggles(days: Set<Int>, onToggle: (Int) -> Unit) {
    val haptics = rememberHaptics()
    val formats = rememberFormats()
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(ButtonGroupDefaults.ConnectedSpaceBetween)) {
        DayOfWeek.entries.forEachIndexed { index, day ->
            val checked = day.value in days
            ToggleButton(
                checked = checked,
                onCheckedChange = {
                    haptics.toggle(it)
                    onToggle(day.value)
                },
                shapes = when (index) {
                    0 -> ButtonGroupDefaults.connectedLeadingButtonShapes()
                    DayOfWeek.entries.lastIndex -> ButtonGroupDefaults.connectedTrailingButtonShapes()
                    else -> ButtonGroupDefaults.connectedMiddleButtonShapes()
                },
                colors = raisedToggleColors(),
                contentPadding = PaddingValues(0.dp),
                modifier = Modifier.weight(1f).semantics { role = Role.Checkbox },
            ) { Text(formats.weekdayNarrow(day), maxLines = 1) }
        }
    }
}

@Composable
private fun HasFacet(has: Set<Has>, onToggle: (Has) -> Unit) {
    val haptics = rememberHaptics()
    Column(verticalArrangement = Arrangement.spacedBy(ListItemDefaults.SegmentedGap)) {
        Has.entries.forEachIndexed { index, kind ->
            val checked = kind in has
            SegmentedListItem(
                onClick = {
                    haptics.toggle(!checked)
                    onToggle(kind)
                },
                shapes = ListItemDefaults.segmentedShapes(index, Has.entries.size),
                colors = segmentedItemColors(),
                leadingContent = {
                    DakilioIcon(
                        when (kind) {
                            Has.Photos -> DakilioSymbols.Image
                            Has.Text -> DakilioSymbols.Notes
                            Has.LongNote -> DakilioSymbols.EditNote
                        },
                        contentDescription = null,
                        filled = checked,
                    )
                },
                supportingContent = {
                    Text(
                        when (kind) {
                            Has.Photos -> stringResource(R.string.search_has_photos_body)
                            Has.Text -> stringResource(R.string.search_has_text_body)
                            Has.LongNote -> pluralStringResource(R.plurals.search_has_long_body, LONG_NOTE_WORDS, LONG_NOTE_WORDS)
                        },
                    )
                },
                trailingContent = {
                    Switch(
                        checked = checked,
                        onCheckedChange = {
                            haptics.toggle(it)
                            onToggle(kind)
                        },
                        thumbContent = if (checked) {
                            { DakilioIcon(DakilioSymbols.Check, contentDescription = null, modifier = Modifier.size(SwitchDefaults.IconSize)) }
                        } else {
                            null
                        },
                    )
                },
            ) { Text(hasLabel(kind)) }
        }
    }
}
