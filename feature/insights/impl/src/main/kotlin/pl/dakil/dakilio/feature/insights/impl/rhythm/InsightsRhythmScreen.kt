package pl.dakil.dakilio.feature.insights.impl.rhythm

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation3.runtime.NavKey
import java.time.DayOfWeek
import java.time.LocalTime
import pl.dakil.dakilio.core.designsystem.component.rememberHaptics
import pl.dakil.dakilio.core.designsystem.icon.DakilioIcon
import pl.dakil.dakilio.core.designsystem.theme.Spacing
import pl.dakil.dakilio.core.model.Mood
import pl.dakil.dakilio.core.ui.DakilioFormats
import pl.dakil.dakilio.core.ui.hintOrNull
import pl.dakil.dakilio.core.ui.mood.moodColors
import pl.dakil.dakilio.core.ui.rememberFormats
import pl.dakil.dakilio.core.ui.weekdaysFrom
import pl.dakil.dakilio.feature.insights.api.InsightsEntriesKey
import pl.dakil.dakilio.feature.insights.impl.InsightsDetailScaffold
import pl.dakil.dakilio.feature.insights.impl.R
import pl.dakil.dakilio.feature.insights.impl.chart.ChartColumn
import pl.dakil.dakilio.feature.insights.impl.chart.ColumnChart
import pl.dakil.dakilio.feature.insights.impl.key
import pl.dakil.dakilio.feature.insights.impl.logic.DayPart
import pl.dakil.dakilio.feature.insights.impl.logic.Rhythm
import pl.dakil.dakilio.feature.insights.impl.logic.nearestMood
import pl.dakil.dakilio.feature.insights.impl.overview.CardNote
import pl.dakil.dakilio.feature.insights.impl.overview.CardTitle
import pl.dakil.dakilio.feature.insights.impl.overview.ChartCaption
import pl.dakil.dakilio.feature.insights.impl.overview.EmptyPeriodCard
import pl.dakil.dakilio.feature.insights.impl.overview.InsightCard
import pl.dakil.dakilio.feature.insights.impl.overview.RhythmCard
import pl.dakil.dakilio.feature.insights.impl.overview.RhythmMeasure
import pl.dakil.dakilio.feature.insights.impl.overview.RhythmMeasureToggle
import pl.dakil.dakilio.feature.insights.impl.overview.dayPartLabel
import pl.dakil.dakilio.feature.insights.impl.overview.dayPartSymbol
import pl.dakil.dakilio.feature.insights.impl.overview.slotLabel

/** When entries are written and how they feel, for one period. */
@Composable
internal fun InsightsRhythmScreen(viewModel: InsightsRhythmViewModel, onBack: () -> Unit, onOpen: (NavKey) -> Unit) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val formats = rememberFormats()
    val ready = state as? InsightsRhythmUiState.Ready
    var measure by rememberSaveable { mutableStateOf(RhythmMeasure.Mood) }
    InsightsDetailScaffold(
        title = stringResource(R.string.insights_when_title),
        header = ready?.header,
        moods = ready?.moods.orEmpty(),
        firstDay = ready?.firstDay ?: DayOfWeek.MONDAY,
        viewModel = viewModel,
        onBack = onBack,
    ) {
        val current = ready ?: return@InsightsDetailScaffold
        if (current.entries == 0) {
            item(key = "empty") { EmptyPeriodCard(hintOrNull(stringResource(R.string.insights_period_empty_body))) }
            return@InsightsDetailScaffold
        }
        item(key = "measure") { RhythmMeasureToggle(measure, { measure = it }) }
        item(key = "matrix") {
            MatrixCard(current.rhythm, measure, current.moods, current.firstDay, formats) { day, part ->
                onOpen(InsightsEntriesKey(viewModel.scope, weekday = day.value, dayPart = part.name))
            }
        }
        item(key = "weekdays") {
            RhythmCard(
                rhythm = current.rhythm,
                moods = current.moods,
                firstDay = current.firstDay,
                formats = formats,
                periodKey = current.header.key,
                fixedMeasure = measure,
                title = stringResource(R.string.insights_when_weekdays),
            )
        }
        item(key = "hours") { HoursCard(current.rhythm, formats, current.header.key) }
    }
}

/**
 * Weekdays against the parts of the day. By entries, a cell is darker the more entries it has (one
 * hue); by mood, it wears its average's level color. Either way the figure is written in the cell,
 * and a tap opens the cell's entries.
 */
@Composable
private fun MatrixCard(
    rhythm: Rhythm,
    measure: RhythmMeasure,
    moods: List<Mood>,
    firstDay: DayOfWeek,
    formats: DakilioFormats,
    onSlot: (DayOfWeek, DayPart) -> Unit,
) {
    val colors = MaterialTheme.colorScheme
    val maxEntries = rhythm.matrix.values.maxOf { it.entries }.coerceAtLeast(1)
    val labelWidth = 40.dp
    InsightCard {
        CardTitle(stringResource(R.string.insights_when_matrix), hintOrNull(stringResource(R.string.insights_when_matrix_hint)))
        Row(Modifier.fillMaxWidth().padding(top = Spacing.l, start = labelWidth), horizontalArrangement = Arrangement.spacedBy(Spacing.xs)) {
            DayPart.entries.forEach { part ->
                Column(Modifier.weight(1f).clearAndSetSemantics { }, horizontalAlignment = Alignment.CenterHorizontally) {
                    DakilioIcon(dayPartSymbol(part), contentDescription = null, tint = colors.onSurfaceVariant, modifier = Modifier.size(18.dp))
                    Text(dayPartLabel(part), style = MaterialTheme.typography.labelSmall, color = colors.onSurfaceVariant, maxLines = 1)
                }
            }
        }
        Column(Modifier.padding(top = Spacing.s), verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
            weekdaysFrom(firstDay).forEach { day ->
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Spacing.xs)) {
                    Text(
                        formats.weekdayShort(day),
                        style = MaterialTheme.typography.labelMedium,
                        color = colors.onSurfaceVariant,
                        maxLines = 1,
                        modifier = Modifier.width(labelWidth - Spacing.xs).clearAndSetSemantics { },
                    )
                    DayPart.entries.forEach { part ->
                        val slot = rhythm.matrix.getValue(day to part)
                        val mood = slot.average?.let { nearestMood(moods, it) }
                        val fill = when {
                            slot.entries == 0 -> colors.surfaceContainerHighest
                            measure == RhythmMeasure.Entries -> lerp(colors.primaryContainer, colors.primary, slot.entries.toFloat() / maxEntries)
                            mood != null -> moodColors(mood).container
                            else -> colors.outlineVariant
                        }
                        val content = when {
                            slot.entries == 0 -> colors.onSurfaceVariant
                            measure == RhythmMeasure.Entries -> if (slot.entries.toFloat() / maxEntries > 0.5f) colors.onPrimary else colors.onPrimaryContainer
                            mood != null -> moodColors(mood).onContainer
                            else -> colors.onSurface
                        }
                        val description = formats.weekday(day) + ", " + dayPartLabel(part) + ": " + slotLabel(slot, moods, formats)
                        Box(
                            contentAlignment = Alignment.Center,
                            modifier = Modifier
                                .weight(1f)
                                .height(40.dp)
                                .clip(RoundedCornerShape(12.dp))
                                .background(fill)
                                .clickable(enabled = slot.entries > 0) { onSlot(day, part) }
                                .clearAndSetSemantics {
                                    contentDescription = description
                                    if (slot.entries > 0) role = Role.Button
                                },
                        ) {
                            Text(
                                when {
                                    slot.entries == 0 -> "–"
                                    measure == RhythmMeasure.Entries -> formats.count(slot.entries)
                                    else -> slot.average?.let(formats::decimal) ?: "–"
                                },
                                style = MaterialTheme.typography.labelLarge,
                                color = content,
                            )
                        }
                    }
                }
            }
        }
        val standouts = listOfNotNull(
            rhythm.brightestSlot?.let { (day, part) -> stringResource(R.string.insights_when_brightest, formats.weekday(day), dayPartLabel(part)) },
            rhythm.heaviestSlot?.let { (day, part) -> stringResource(R.string.insights_when_heaviest, formats.weekday(day), dayPartLabel(part)) },
            rhythm.busiestSlot?.let { (day, part) -> stringResource(R.string.insights_when_busiest, formats.weekday(day), dayPartLabel(part)) },
        )
        if (standouts.isNotEmpty()) {
            Column(Modifier.padding(top = Spacing.m), verticalArrangement = Arrangement.spacedBy(Spacing.xxs)) {
                standouts.forEach { CardNote(it) }
            }
        }
    }
}

/** Entries by the hour they were written in. */
@Composable
private fun HoursCard(rhythm: Rhythm, formats: DakilioFormats, periodKey: String) {
    val haptics = rememberHaptics()
    var selected by rememberSaveable(periodKey) { mutableStateOf<Int?>(null) }
    val max = rhythm.hours.max().coerceAtLeast(1)
    val colors = MaterialTheme.colorScheme
    InsightCard {
        CardTitle(stringResource(R.string.insights_when_hours))
        ColumnChart(
            columns = rhythm.hours.map { ChartColumn(it.toFloat() / max) },
            description = stringResource(R.string.insights_when_hours),
            selected = selected,
            onSelect = { index ->
                if (index != selected) {
                    haptics.tick()
                    selected = index
                }
            },
            modifier = Modifier.padding(top = Spacing.l),
        )
        Row(Modifier.fillMaxWidth().padding(top = Spacing.xs), horizontalArrangement = Arrangement.SpaceBetween) {
            listOf(0, 12, 23).forEach { hour ->
                Text(formats.time(LocalTime.of(hour, 0)), style = MaterialTheme.typography.labelSmall, color = colors.onSurfaceVariant)
            }
        }
        selected?.let { hour ->
            val entries = rhythm.hours.getOrElse(hour) { 0 }
            ChartCaption(
                stringResource(
                    R.string.insights_point,
                    stringResource(R.string.insights_range, formats.time(LocalTime.of(hour, 0)), formats.time(LocalTime.of(hour, 59))),
                    pluralStringResource(R.plurals.insights_heat_entries, entries, entries),
                ),
                Modifier.padding(top = Spacing.m),
            )
        }
    }
}
