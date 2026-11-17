package pl.dakil.dakilio.feature.insights.impl.overview

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import java.time.DayOfWeek
import pl.dakil.dakilio.core.designsystem.component.ConnectedToggleRow
import pl.dakil.dakilio.core.designsystem.component.ToggleOption
import pl.dakil.dakilio.core.designsystem.component.rememberHaptics
import pl.dakil.dakilio.core.designsystem.icon.DakilioIcon
import pl.dakil.dakilio.core.designsystem.icon.DakilioSymbol
import pl.dakil.dakilio.core.designsystem.icon.DakilioSymbols
import pl.dakil.dakilio.core.designsystem.theme.Spacing
import pl.dakil.dakilio.core.model.MOOD_LEVELS
import pl.dakil.dakilio.core.model.Mood
import pl.dakil.dakilio.core.ui.DakilioFormats
import pl.dakil.dakilio.core.ui.weekdaysFrom
import pl.dakil.dakilio.feature.insights.impl.R
import pl.dakil.dakilio.feature.insights.impl.chart.ChartColumn
import pl.dakil.dakilio.feature.insights.impl.chart.ColumnChart
import pl.dakil.dakilio.feature.insights.impl.logic.DayPart
import pl.dakil.dakilio.feature.insights.impl.logic.Rhythm
import pl.dakil.dakilio.feature.insights.impl.logic.SlotStat

/** What a rhythm chart measures. Mood and entry counts never share a plot. */
internal enum class RhythmMeasure { Mood, Entries }

/** A slot's height on a chart: its average on the 1–5 scale, or its share of the busiest slot. */
internal fun SlotStat.fraction(measure: RhythmMeasure, maxEntries: Int): Float? = when (measure) {
    RhythmMeasure.Mood -> average?.let { (it - 1f) / (MOOD_LEVELS - 1) }
    RhythmMeasure.Entries -> if (maxEntries == 0) null else entries.toFloat() / maxEntries
}

@Composable
internal fun dayPartLabel(part: DayPart): String = stringResource(
    when (part) {
        DayPart.Morning -> R.string.insights_part_morning
        DayPart.Afternoon -> R.string.insights_part_afternoon
        DayPart.Evening -> R.string.insights_part_evening
        DayPart.Night -> R.string.insights_part_night
    },
)

internal fun dayPartSymbol(part: DayPart): DakilioSymbol = when (part) {
    DayPart.Morning -> DakilioSymbols.WbTwilight
    DayPart.Afternoon -> DakilioSymbols.Sunny
    DayPart.Evening -> DakilioSymbols.NightsStay
    DayPart.Night -> DakilioSymbols.Bedtime
}

/** "Good 4.2 · 12 entries", or just the entries when no mood was logged. */
@Composable
internal fun slotLabel(slot: SlotStat, moods: List<Mood>, formats: DakilioFormats): String {
    val entries = pluralStringResource(R.plurals.insights_heat_entries, slot.entries, slot.entries)
    return if (slot.average == null) entries else moodValueLabel(slot.average, moods, formats) + " · " + entries
}

@Composable
internal fun RhythmMeasureToggle(measure: RhythmMeasure, onMeasure: (RhythmMeasure) -> Unit, modifier: Modifier = Modifier) {
    ConnectedToggleRow(
        options = listOf(
            ToggleOption(RhythmMeasure.Mood, stringResource(R.string.insights_rhythm_mood)),
            ToggleOption(RhythmMeasure.Entries, stringResource(R.string.insights_rhythm_entries)),
        ),
        selected = measure,
        onSelect = onMeasure,
        modifier = modifier,
    )
}

/**
 * The week's shape: a column per weekday, by mood or by entries, with the best (or busiest) day
 * standing out, and the four parts of the day under it.
 */
@Composable
internal fun RhythmCard(
    rhythm: Rhythm,
    moods: List<Mood>,
    firstDay: DayOfWeek,
    formats: DakilioFormats,
    periodKey: String,
    onMore: (() -> Unit)? = null,
    /** The measure set by the screen around the card; `null` lets the card keep its own, with its toggle. */
    fixedMeasure: RhythmMeasure? = null,
    title: String = stringResource(R.string.insights_rhythm),
) {
    val haptics = rememberHaptics()
    var ownMeasure by rememberSaveable { mutableStateOf(RhythmMeasure.Mood) }
    val measure = fixedMeasure ?: ownMeasure
    var selected by rememberSaveable(periodKey, measure) { mutableStateOf<Int?>(null) }
    val weekdays = weekdaysFrom(firstDay)
    val maxEntries = rhythm.weekdays.values.maxOf { it.entries }
    val standout = when (measure) {
        RhythmMeasure.Mood -> rhythm.bestWeekday
        RhythmMeasure.Entries -> rhythm.weekdays.entries.maxByOrNull { it.value.entries }
            ?.takeIf { top -> top.value.entries > 0 && rhythm.weekdays.values.count { it.entries == top.value.entries } == 1 }?.key
    }
    // `map` is inline, so the labels can be composed here; a joinToString lambda can't.
    val slotLabels = weekdays.map { day -> formats.weekday(day) + " " + slotLabel(rhythm.weekdays.getValue(day), moods, formats) }
    InsightCard {
        CardTitle(title, onMore = onMore)
        if (fixedMeasure == null) RhythmMeasureToggle(measure, { ownMeasure = it }, Modifier.padding(top = Spacing.m))
        ColumnChart(
            columns = weekdays.map { day -> ChartColumn(rhythm.weekdays.getValue(day).fraction(measure, maxEntries), formats.weekdayShort(day)) },
            description = stringResource(R.string.insights_rhythm_cd, slotLabels.joinToString()),
            emphasized = standout?.let(weekdays::indexOf),
            selected = selected,
            onSelect = { index ->
                if (index != selected) {
                    haptics.tick()
                    selected = index
                }
            },
            modifier = Modifier.padding(top = Spacing.l),
        )
        val day = selected?.let(weekdays::getOrNull)
        when {
            day != null -> ChartCaption(
                stringResource(R.string.insights_point, formats.weekday(day), slotLabel(rhythm.weekdays.getValue(day), moods, formats)),
                Modifier.padding(top = Spacing.m),
            )
            rhythm.bestWeekday != null -> ChartCaption(
                stringResource(R.string.insights_best_day, formats.weekday(rhythm.bestWeekday!!)),
                Modifier.padding(top = Spacing.m),
            )
        }
        Row(Modifier.fillMaxWidth().padding(top = Spacing.l), horizontalArrangement = Arrangement.spacedBy(Spacing.xs)) {
            DayPart.entries.forEach { part ->
                DayPartTile(part, rhythm.dayParts.getValue(part), measure, part == rhythm.busiestDayPart, moods, formats, Modifier.weight(1f))
            }
        }
    }
}

@Composable
private fun DayPartTile(
    part: DayPart,
    slot: SlotStat,
    measure: RhythmMeasure,
    busiest: Boolean,
    moods: List<Mood>,
    formats: DakilioFormats,
    modifier: Modifier,
) {
    val colors = MaterialTheme.colorScheme
    val label = dayPartLabel(part)
    val description = label + ", " + slotLabel(slot, moods, formats)
    Surface(
        shape = RoundedCornerShape(16.dp),
        color = if (busiest) colors.secondaryContainer else colors.surfaceContainerHigh,
        contentColor = if (busiest) colors.onSecondaryContainer else colors.onSurface,
        modifier = modifier.semantics(mergeDescendants = true) { contentDescription = description },
    ) {
        Column(Modifier.padding(vertical = Spacing.m, horizontal = Spacing.xs), horizontalAlignment = Alignment.CenterHorizontally) {
            DakilioIcon(dayPartSymbol(part), contentDescription = null, modifier = Modifier.size(20.dp))
            Text(
                when (measure) {
                    RhythmMeasure.Mood -> slot.average?.let(formats::decimal) ?: "–"
                    RhythmMeasure.Entries -> formats.count(slot.entries)
                },
                style = MaterialTheme.typography.titleMediumEmphasized,
                modifier = Modifier.padding(top = Spacing.xs),
            )
            Text(label, style = MaterialTheme.typography.labelSmall, maxLines = 1)
        }
    }
}
