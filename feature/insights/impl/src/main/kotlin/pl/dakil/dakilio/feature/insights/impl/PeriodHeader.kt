package pl.dakil.dakilio.feature.insights.impl

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.SelectableDates
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import java.time.Instant
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneOffset
import pl.dakil.dakilio.core.designsystem.component.ConnectedToggleRow
import pl.dakil.dakilio.core.designsystem.component.ToggleOption
import pl.dakil.dakilio.core.designsystem.component.rememberHaptics
import pl.dakil.dakilio.core.designsystem.icon.DakilioIcon
import pl.dakil.dakilio.core.designsystem.icon.DakilioSymbols
import pl.dakil.dakilio.core.designsystem.theme.Spacing
import pl.dakil.dakilio.core.model.DateRange
import pl.dakil.dakilio.core.model.MonthSummary
import pl.dakil.dakilio.core.model.Mood
import pl.dakil.dakilio.core.ui.DakilioFormats
import pl.dakil.dakilio.core.ui.component.MonthPickerSheet
import pl.dakil.dakilio.feature.insights.impl.logic.Bounds
import pl.dakil.dakilio.feature.insights.impl.logic.Comparison
import pl.dakil.dakilio.feature.insights.impl.logic.InsightsPeriod
import pl.dakil.dakilio.feature.insights.impl.logic.PeriodKind
import pl.dakil.dakilio.feature.insights.impl.logic.calendarRange
import pl.dakil.dakilio.feature.insights.impl.logic.encode

/** What the period header shows and allows, shared by the overview and every deep-dive screen. */
@Immutable
data class PeriodHeaderState(
    val period: InsightsPeriod,
    val bounds: Bounds,
    val inProgress: Boolean,
    val canGoBack: Boolean,
    val canGoForward: Boolean,
    val months: Map<YearMonth, MonthSummary>,
    val comparison: Comparison,
)

/** "26 Oct – 1 Nov", with the year when the range isn't in [today]'s. A single day is just its date. */
@Composable
internal fun rangeLabel(range: DateRange, today: LocalDate, formats: DakilioFormats): String {
    val withYear = range.to.year != today.year
    val end = if (withYear) formats.dayMonthYear(range.to) else formats.dayMonthShort(range.to)
    if (range.from == range.to) return end
    val start = if (range.from.year != range.to.year) formats.dayMonthYear(range.from) else formats.dayMonthShort(range.from)
    return stringResource(R.string.insights_range, start, end)
}

/** The period's name: "November 2026", "2026", "26 Oct – 1 Nov", "All time". */
@Composable
internal fun periodLabel(period: InsightsPeriod, bounds: Bounds, formats: DakilioFormats): String = when (period) {
    is InsightsPeriod.Month -> formats.monthYear(period.month.atDay(1))
    is InsightsPeriod.Year -> period.year.toString()
    InsightsPeriod.AllTime -> stringResource(R.string.insights_all_time)
    is InsightsPeriod.Week, is InsightsPeriod.Custom -> rangeLabel(period.calendarRange(bounds), bounds.today, formats)
}

/** Week · Month · Year · All. A custom range leaves all four unchecked. */
@Composable
internal fun PeriodKindToggle(kind: PeriodKind, onKind: (PeriodKind) -> Unit, modifier: Modifier = Modifier) {
    ConnectedToggleRow(
        options = listOf(
            ToggleOption(PeriodKind.Week, stringResource(R.string.insights_period_week)),
            ToggleOption(PeriodKind.Month, stringResource(R.string.insights_period_month)),
            ToggleOption(PeriodKind.Year, stringResource(R.string.insights_period_year)),
            ToggleOption(PeriodKind.All, stringResource(R.string.insights_period_all)),
        ),
        selected = kind,
        onSelect = onKind,
        modifier = modifier,
    )
}

/**
 * ◀ the period's name ▶. The name slides in the direction of the step and opens the picker; under
 * it, "so far" marks a period that's still running. The arrows are disabled at the journal's ends.
 */
@Composable
internal fun PeriodStepper(
    state: PeriodHeaderState,
    formats: DakilioFormats,
    onStep: (forward: Boolean) -> Unit,
    onPick: (() -> Unit)?,
    modifier: Modifier = Modifier,
) {
    val haptics = rememberHaptics()
    val colors = MaterialTheme.colorScheme
    Row(
        modifier.fillMaxWidth().background(colors.surface).padding(vertical = Spacing.s),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Spacing.s),
    ) {
        FilledTonalIconButton(
            onClick = {
                haptics.tick()
                onStep(false)
            },
            enabled = state.canGoBack,
        ) {
            DakilioIcon(DakilioSymbols.ChevronRight, contentDescription = stringResource(R.string.insights_period_earlier), modifier = Modifier.rotate(180f))
        }
        val label = periodLabel(state.period, state.bounds, formats)
        val description = stringResource(R.string.insights_period_choose, label)
        Surface(
            onClick = { onPick?.invoke() },
            enabled = onPick != null,
            shape = CircleShape,
            color = colors.surfaceContainerHigh,
            modifier = Modifier.weight(1f).heightIn(min = 48.dp).semantics { contentDescription = description },
        ) {
            val spatial = MaterialTheme.motionScheme.fastSpatialSpec<IntOffset>()
            val effects = MaterialTheme.motionScheme.fastEffectsSpec<Float>()
            AnimatedContent(
                targetState = state.period,
                transitionSpec = {
                    val forward = targetState.order(state.bounds) >= initialState.order(state.bounds)
                    (slideInHorizontally(spatial) { if (forward) it / 3 else -it / 3 } + fadeIn(effects))
                        .togetherWith(slideOutHorizontally(spatial) { if (forward) -it / 3 else it / 3 } + fadeOut(effects))
                },
                contentAlignment = Alignment.Center,
                label = "period",
            ) { shown ->
                Column(
                    Modifier.fillMaxWidth().padding(horizontal = Spacing.l, vertical = Spacing.xs),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center,
                ) {
                    Text(
                        periodLabel(shown, state.bounds, formats),
                        style = MaterialTheme.typography.titleMediumEmphasized,
                        maxLines = 1,
                        textAlign = TextAlign.Center,
                    )
                    val sub = when {
                        shown == InsightsPeriod.AllTime -> stringResource(R.string.insights_since, formats.dayMonthYear(state.bounds.first))
                        shown == state.period && state.inProgress -> stringResource(R.string.insights_so_far)
                        else -> null
                    }
                    sub?.let { Text(it, style = MaterialTheme.typography.labelSmall, color = colors.onSurfaceVariant, maxLines = 1) }
                }
            }
        }
        FilledTonalIconButton(
            onClick = {
                haptics.tick()
                onStep(true)
            },
            enabled = state.canGoForward,
        ) {
            DakilioIcon(DakilioSymbols.ChevronRight, contentDescription = stringResource(R.string.insights_period_later))
        }
    }
}

/** Where a period sits in time, to tell which way a step went. */
private fun InsightsPeriod.order(bounds: Bounds): Long = calendarRange(bounds).from.toEpochDay()

/**
 * The picker behind the stepper's label: the month grid for a month, a list of years for a year and
 * a date picker for a week (the week holding the picked day).
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun PeriodPicker(
    state: PeriodHeaderState,
    moods: List<Mood>,
    firstDay: java.time.DayOfWeek,
    formats: DakilioFormats,
    onPick: (InsightsPeriod) -> Unit,
    onDismiss: () -> Unit,
) {
    val bounds = state.bounds
    when (val period = state.period) {
        is InsightsPeriod.Month -> MonthPickerSheet(
            current = period.month,
            firstDay = bounds.first,
            lastDay = bounds.today,
            months = state.months,
            moods = moods,
            formats = formats,
            onPick = { onPick(InsightsPeriod.Month(it)) },
            onDismiss = onDismiss,
        )

        is InsightsPeriod.Year -> ModalBottomSheet(onDismissRequest = onDismiss, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
            val haptics = rememberHaptics()
            FlowRow(
                Modifier.fillMaxWidth().padding(horizontal = Spacing.gutter).padding(bottom = Spacing.xl),
                horizontalArrangement = Arrangement.spacedBy(Spacing.s),
                verticalArrangement = Arrangement.spacedBy(Spacing.s),
            ) {
                (bounds.today.year downTo bounds.first.year).forEach { year ->
                    val selected = year == period.year
                    Surface(
                        onClick = {
                            haptics.confirm()
                            onPick(InsightsPeriod.Year(year))
                        },
                        shape = RoundedCornerShape(16.dp),
                        color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceContainerHigh,
                        contentColor = if (selected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface,
                    ) {
                        Text(
                            year.toString(),
                            style = if (selected) MaterialTheme.typography.titleMediumEmphasized else MaterialTheme.typography.titleMedium,
                            modifier = Modifier.padding(horizontal = Spacing.xl, vertical = Spacing.m),
                        )
                    }
                }
            }
        }

        is InsightsPeriod.Week -> {
            val first = bounds.first.atStartOfDay().toInstant(ZoneOffset.UTC).toEpochMilli()
            val last = bounds.today.atStartOfDay().toInstant(ZoneOffset.UTC).toEpochMilli()
            val selectable = remember(first, last) {
                object : SelectableDates {
                    override fun isSelectableDate(utcTimeMillis: Long) = utcTimeMillis in first..last
                    override fun isSelectableYear(year: Int) = year in bounds.first.year..bounds.today.year
                }
            }
            val initial = minOf(period.start.plusDays(6), bounds.today).atStartOfDay().toInstant(ZoneOffset.UTC).toEpochMilli()
            val pickerState = rememberDatePickerState(initialSelectedDateMillis = initial, selectableDates = selectable)
            DatePickerDialog(
                onDismissRequest = onDismiss,
                confirmButton = {
                    TextButton(
                        onClick = {
                            pickerState.selectedDateMillis?.let { millis ->
                                val date = Instant.ofEpochMilli(millis).atZone(ZoneOffset.UTC).toLocalDate()
                                onPick(InsightsPeriod.of(PeriodKind.Week, date, firstDay))
                            } ?: onDismiss()
                        },
                    ) { Text(stringResource(R.string.insights_ok)) }
                },
                dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(pl.dakil.dakilio.core.ui.R.string.ui_cancel)) } },
            ) { DatePicker(pickerState) }
        }

        InsightsPeriod.AllTime, is InsightsPeriod.Custom -> onDismiss()
    }
}

/** Whether tapping the stepper's label has anything to open. */
internal val InsightsPeriod.hasPicker: Boolean get() = this is InsightsPeriod.Week || this is InsightsPeriod.Month || this is InsightsPeriod.Year

/** A stable key for remembering things per period. */
internal val PeriodHeaderState.key: String get() = period.encode()
