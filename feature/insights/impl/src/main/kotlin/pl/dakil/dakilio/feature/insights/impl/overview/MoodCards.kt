package pl.dakil.dakilio.feature.insights.impl.overview

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
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
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.YearMonth
import kotlin.math.roundToInt
import pl.dakil.dakilio.core.designsystem.component.rememberHaptics
import pl.dakil.dakilio.core.designsystem.icon.DakilioIcon
import pl.dakil.dakilio.core.designsystem.icon.DakilioSymbols
import pl.dakil.dakilio.core.designsystem.theme.Spacing
import pl.dakil.dakilio.core.model.DateRange
import pl.dakil.dakilio.core.model.MonthSummary
import pl.dakil.dakilio.core.model.Mood
import pl.dakil.dakilio.core.model.primaryOf
import pl.dakil.dakilio.core.ui.DakilioFormats
import pl.dakil.dakilio.core.ui.hintOrNull
import pl.dakil.dakilio.core.ui.mood.MoodBadge
import pl.dakil.dakilio.core.ui.mood.moodColors
import pl.dakil.dakilio.feature.insights.impl.R
import pl.dakil.dakilio.feature.insights.impl.chart.BarSegment
import pl.dakil.dakilio.feature.insights.impl.chart.DayStrip
import pl.dakil.dakilio.feature.insights.impl.chart.DaysGrid
import pl.dakil.dakilio.feature.insights.impl.chart.MoodLineChart
import pl.dakil.dakilio.feature.insights.impl.chart.PixelGrid
import pl.dakil.dakilio.feature.insights.impl.chart.SegmentedBar
import pl.dakil.dakilio.feature.insights.impl.chart.YearsGrid
import pl.dakil.dakilio.feature.insights.impl.logic.CalendarData
import pl.dakil.dakilio.feature.insights.impl.logic.CalendarForm
import pl.dakil.dakilio.feature.insights.impl.logic.DayCell
import pl.dakil.dakilio.feature.insights.impl.logic.Granularity
import pl.dakil.dakilio.feature.insights.impl.logic.InsightsPeriod
import pl.dakil.dakilio.feature.insights.impl.logic.MoodFlow
import pl.dakil.dakilio.feature.insights.impl.logic.MoodSummary
import pl.dakil.dakilio.feature.insights.impl.logic.monthsOf
import pl.dakil.dakilio.feature.insights.impl.logic.nearestMood
import pl.dakil.dakilio.feature.insights.impl.rangeLabel

/** A slice of a chart by name: a day's full date, a week's range, a month, a year. */
@Composable
internal fun bucketLabel(range: DateRange, granularity: Granularity, today: LocalDate, formats: DakilioFormats): String = when (granularity) {
    Granularity.Days -> formats.fullDate(range.from, today)
    Granularity.Weeks -> rangeLabel(range, today, formats)
    Granularity.Months -> formats.monthYear(range.from)
    Granularity.Years -> range.from.year.toString()
}

/** The short label under a chart's first and last slice. */
internal fun axisLabel(range: DateRange, granularity: Granularity, formats: DakilioFormats): String = when (granularity) {
    Granularity.Days, Granularity.Weeks -> formats.dayMonthShort(range.from)
    Granularity.Months -> formats.monthYear(range.from)
    Granularity.Years -> range.from.year.toString()
}

/** "Good 4.2", or "no mood". */
@Composable
internal fun moodValueLabel(average: Float?, moods: List<Mood>, formats: DakilioFormats): String {
    val mood = average?.let { nearestMood(moods, it) } ?: return stringResource(R.string.insights_point_no_mood)
    return stringResource(R.string.insights_point_mood, mood.label, formats.decimal(average))
}

/**
 * Mood over the period. Dragging across the chart reads each point into the caption, with a tick
 * per point. When the points are days of a long period, the line is their 7-day average, which the
 * legend names and the caption reads out next to the day's own mood.
 *
 * @param periodKey what the touched point is remembered under, so it resets when the period changes.
 */
@Composable
internal fun MoodFlowCard(
    flow: MoodFlow,
    moods: List<Mood>,
    today: LocalDate,
    formats: DakilioFormats,
    periodKey: String,
    onMore: (() -> Unit)? = null,
    comparison: List<Float?>? = null,
    comparisonLabel: String? = null,
) {
    val haptics = rememberHaptics()
    var selected by rememberSaveable(periodKey, flow.granularity) { mutableStateOf<Int?>(null) }
    val first = flow.buckets.first()
    val last = flow.buckets.last()
    InsightCard {
        CardTitle(stringResource(R.string.insights_mood_flow), onMore = onMore)
        MoodLineChart(
            values = flow.buckets.map { it.average },
            trend = flow.trend,
            comparison = comparison,
            moods = moods,
            description = stringResource(
                R.string.insights_mood_flow_cd,
                bucketLabel(first.range, flow.granularity, today, formats),
                bucketLabel(last.range, flow.granularity, today, formats),
            ),
            selected = selected,
            onSelect = { index ->
                if (index != selected) {
                    haptics.tick()
                    selected = index
                }
            },
            startLabel = axisLabel(first.range, flow.granularity, formats),
            endLabel = axisLabel(last.range, flow.granularity, formats),
            modifier = Modifier.padding(top = Spacing.l),
        )
        val compared = comparisonLabel?.takeIf { comparison != null }
        if (flow.trend != null || compared != null) {
            FlowLegend(trend = flow.trend != null, comparisonLabel = compared, modifier = Modifier.padding(top = Spacing.m))
        }
        val bucket = selected?.let(flow.buckets::getOrNull)
        val caption = if (bucket != null) {
            val point = stringResource(
                R.string.insights_point,
                bucketLabel(bucket.range, flow.granularity, today, formats),
                moodValueLabel(bucket.average, moods, formats),
            )
            val average = selected?.let { flow.trend?.getOrNull(it) }
            if (average != null) {
                stringResource(R.string.insights_point, point, stringResource(R.string.insights_flow_trend_value, formats.decimal(average)))
            } else {
                point
            }
        } else {
            null
        }
        if (caption != null) {
            ChartCaption(caption, Modifier.padding(top = Spacing.m))
        } else {
            hintOrNull(stringResource(R.string.insights_mood_flow_hint))?.let { CardNote(it, Modifier.padding(top = Spacing.m)) }
        }
    }
}

/** "Tuesday, 3 November · Good · 2 entries", or "… · no entries". */
@Composable
internal fun dayCaption(date: LocalDate, cell: DayCell?, moods: List<Mood>, today: LocalDate, formats: DakilioFormats): String {
    val day = formats.fullDate(date, today)
    if (cell == null) return stringResource(R.string.insights_heat_empty, day)
    val mood = cell.level?.let(moods::primaryOf)?.label ?: stringResource(R.string.insights_heat_no_mood)
    val entries = pluralStringResource(R.plurals.insights_heat_entries, cell.entries, cell.entries)
    return stringResource(R.string.insights_heat_day, day, "$mood · $entries")
}

/**
 * The period's days, drawn by its length: day tiles for a week, a calendar for a month, a pixel
 * grid for a year and a grid of months for longer. A touched day is read into the caption; a month
 * or a year in the longest form opens that period.
 *
 * @param onDay opens a day (its entries); `null` while there's nothing to open.
 */
@Composable
internal fun CalendarCard(
    calendar: CalendarData,
    months: Map<YearMonth, MonthSummary>,
    moods: List<Mood>,
    firstDay: DayOfWeek,
    today: LocalDate,
    formats: DakilioFormats,
    periodKey: String,
    onPeriod: (InsightsPeriod) -> Unit,
    onDay: ((LocalDate) -> Unit)? = null,
) {
    val haptics = rememberHaptics()
    var selectedDay by rememberSaveable(periodKey) { mutableStateOf<Long?>(null) }
    val selected = selectedDay?.let(LocalDate::ofEpochDay)
    val select: (LocalDate) -> Unit = { date ->
        if (date != selected) {
            haptics.tick()
            selectedDay = date.toEpochDay()
        }
    }
    val describe: @Composable (LocalDate) -> String = { dayCaption(it, calendar.cells[it], moods, today, formats) }
    InsightCard {
        when (calendar.form) {
            CalendarForm.Strip -> {
                CardTitle(stringResource(R.string.insights_calendar), hintOrNull(stringResource(R.string.insights_calendar_hint)))
                DayStrip(
                    range = calendar.layoutRange,
                    cells = calendar.cells,
                    moods = moods,
                    today = today,
                    selected = selected,
                    onSelect = { select(it); onDay?.invoke(it) },
                    formats = formats,
                    describe = describe,
                    modifier = Modifier.padding(top = Spacing.l),
                )
            }

            CalendarForm.Grid -> {
                CardTitle(stringResource(R.string.insights_calendar), hintOrNull(stringResource(R.string.insights_calendar_hint)))
                Box(Modifier.fillMaxWidth().padding(top = Spacing.l), contentAlignment = Alignment.Center) {
                    DaysGrid(
                        range = calendar.layoutRange,
                        cells = calendar.cells,
                        moods = moods,
                        firstDay = firstDay,
                        today = today,
                        selected = selected,
                        onSelect = { select(it); onDay?.invoke(it) },
                        formats = formats,
                        describe = describe,
                    )
                }
            }

            CalendarForm.Pixels -> {
                CardTitle(stringResource(R.string.insights_pixels), hintOrNull(stringResource(R.string.insights_pixels_hint)))
                PixelGrid(
                    months = monthsOf(calendar.layoutRange),
                    range = calendar.layoutRange,
                    today = today,
                    cells = calendar.cells,
                    moods = moods,
                    selected = selected,
                    onSelect = select,
                    formats = formats,
                    description = stringResource(R.string.insights_pixels_cd),
                    modifier = Modifier.padding(top = Spacing.l),
                )
            }

            CalendarForm.Years -> {
                CardTitle(stringResource(R.string.insights_years), hintOrNull(stringResource(R.string.insights_years_hint)))
                YearsGrid(
                    range = calendar.range,
                    months = months,
                    moods = moods,
                    onMonth = { onPeriod(InsightsPeriod.Month(it)) },
                    onYear = { onPeriod(InsightsPeriod.Year(it)) },
                    formats = formats,
                    describe = { month, summary -> monthCaption(month, summary, moods, formats) },
                    modifier = Modifier.padding(top = Spacing.l),
                )
            }
        }
        if (selected != null && calendar.form != CalendarForm.Years) {
            val caption = dayCaption(selected, calendar.cells[selected], moods, today, formats)
            if (calendar.form == CalendarForm.Pixels && onDay != null) {
                // The grid's cells are too small to open a day by a tap, so the caption does it.
                Row(
                    Modifier.padding(top = Spacing.s).fillMaxWidth().cardRowClick { onDay(selected) },
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    ChartCaption(caption, Modifier.weight(1f))
                    DakilioIcon(
                        DakilioSymbols.ChevronRight,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            } else {
                ChartCaption(caption, Modifier.padding(top = Spacing.m))
            }
        }
        MoodLegend(moods, Modifier.padding(top = Spacing.m))
    }
}

/** "March 2026 · Good · 14 entries". */
@Composable
internal fun monthCaption(month: YearMonth, summary: MonthSummary?, moods: List<Mood>, formats: DakilioFormats): String {
    val name = formats.monthYear(month.atDay(1))
    if (summary == null) return stringResource(R.string.insights_heat_empty, name)
    val mood = summary.averageLevel?.let(moods::primaryOf)?.label ?: stringResource(R.string.insights_heat_no_mood)
    return stringResource(R.string.insights_heat_day, name, "$mood · " + pluralStringResource(R.plurals.insights_heat_entries, summary.entries, summary.entries))
}

/**
 * How the period's moods split over the five levels: one segmented bar, worst to best, and a legend
 * that carries the faces, the counts and the shares.
 *
 * @param onLevel opens the entries of a level.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun MoodMixCard(mood: MoodSummary, onMore: (() -> Unit)? = null, onLevel: ((Int) -> Unit)? = null) {
    val total = mood.moodEntries
    InsightCard {
        CardTitle(stringResource(R.string.insights_distribution), pluralStringResource(R.plurals.insights_moods_logged, total, total), onMore = onMore)
        if (total == 0) {
            CardNote(stringResource(R.string.insights_no_mood), Modifier.padding(top = Spacing.s))
            return@InsightCard
        }
        val shown = mood.distribution.filter { it.count > 0 }
        SegmentedBar(
            segments = shown.map { BarSegment(it.count, moodColors(it.mood).container) },
            description = mood.distribution.joinToString { "${it.mood.label} ${it.count}" },
            onSelect = onLevel?.let { open -> { index -> open(shown[index].mood.level) } },
            modifier = Modifier.padding(top = Spacing.l),
        )
        FlowRow(
            modifier = Modifier.padding(top = Spacing.l).fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(Spacing.l),
            verticalArrangement = Arrangement.spacedBy(Spacing.s),
        ) {
            mood.distribution.forEach { item ->
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .then(if (onLevel != null && item.count > 0) Modifier.clickable { onLevel(item.mood.level) } else Modifier),
                ) {
                    MoodBadge(item.mood, size = 22.dp)
                    Text(
                        stringResource(R.string.insights_mood_share, item.mood.label, item.count, (item.count * 100f / total).roundToInt()),
                        style = MaterialTheme.typography.labelLarge,
                        color = if (item.count > 0) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(start = Spacing.s),
                    )
                }
            }
        }
    }
}

/** Which line is which: the 7-day average when the dots are days, and the comparison as a faint line. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun FlowLegend(trend: Boolean, comparisonLabel: String?, modifier: Modifier = Modifier) {
    val line = MaterialTheme.colorScheme.primary
    FlowRow(modifier, horizontalArrangement = Arrangement.spacedBy(Spacing.l), verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
        LegendLine(line, stringResource(if (trend) R.string.insights_flow_trend else R.string.insights_this_period))
        comparisonLabel?.let { LegendLine(line.copy(alpha = 0.32f), it) }
    }
}

@Composable
private fun LegendLine(color: androidx.compose.ui.graphics.Color, label: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(width = 16.dp, height = 3.dp).background(color, RoundedCornerShape(2.dp)))
        Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(start = Spacing.xs))
    }
}
