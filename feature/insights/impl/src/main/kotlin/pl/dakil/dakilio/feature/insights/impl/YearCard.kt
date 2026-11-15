package pl.dakil.dakilio.feature.insights.impl

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import java.time.LocalDate
import java.time.YearMonth
import pl.dakil.dakilio.core.designsystem.component.rememberHaptics
import pl.dakil.dakilio.core.designsystem.icon.DakilioIcon
import pl.dakil.dakilio.core.designsystem.icon.DakilioSymbols
import pl.dakil.dakilio.core.designsystem.theme.Spacing
import pl.dakil.dakilio.core.model.Mood
import pl.dakil.dakilio.core.ui.DakilioFormats
import pl.dakil.dakilio.core.ui.hintOrNull
import pl.dakil.dakilio.core.ui.mood.moodColors

private const val DAY_COLUMNS = 31
private val CellGap = 2.dp
private val MonthLabelWidth = 30.dp
private val DayLabelHeight = 16.dp
private val MaxCell = 18.dp

/** The day-of-month columns that get a number above the grid. */
private val LabeledDays = listOf(1, 10, 20, 31)

/**
 * The whole year at a glance: a row per month, a small square per day, colored like the journey map
 * (the day's average mood). It ignores the period filter and has its own year stepper. One Canvas
 * draws it all; a tap or a slide picks the day read out underneath.
 */
@Composable
internal fun YearCard(
    year: YearPixels,
    years: IntRange,
    moods: List<Mood>,
    today: LocalDate,
    formats: DakilioFormats,
    onYear: (Int) -> Unit,
) {
    val colors = MaterialTheme.colorScheme
    val haptics = rememberHaptics()
    val moodFill = moods.associate { it.id to moodColors(it).container }
    val noMoodFill = colors.outlineVariant
    val emptyFill = colors.surfaceContainerHighest
    val ring = colors.onSurface
    val labelColor = colors.onSurfaceVariant
    val labelStyle = MaterialTheme.typography.labelSmall
    val measurer = rememberTextMeasurer()
    var selected by rememberSaveable(year.year) { mutableStateOf(if (year.year == today.year) today.toEpochDay() else null) }
    val title = stringResource(R.string.insights_year)
    val summary = pluralStringResource(R.plurals.insights_year_days, year.daysJournaled, year.daysJournaled)
    val pick = { date: LocalDate? ->
        if (date != null && date.toEpochDay() != selected) {
            selected = date.toEpochDay()
            haptics.tick()
        }
    }
    InsightCard {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) { CardTitle(title, summary) }
            IconButton(onClick = { onYear(year.year - 1) }, enabled = year.year > years.first) {
                DakilioIcon(DakilioSymbols.ChevronLeft, contentDescription = stringResource(R.string.insights_year_previous))
            }
            Text(year.year.toString(), style = MaterialTheme.typography.titleMedium)
            IconButton(onClick = { onYear(year.year + 1) }, enabled = year.year < years.last) {
                DakilioIcon(DakilioSymbols.ChevronRight, contentDescription = stringResource(R.string.insights_year_next))
            }
        }
        BoxWithConstraints(Modifier.padding(top = Spacing.s).fillMaxWidth()) {
            val cell: Dp = minOf((maxWidth - MonthLabelWidth - CellGap * (DAY_COLUMNS - 1)) / DAY_COLUMNS, MaxCell)
            val months = year.months
            Canvas(
                Modifier
                    .fillMaxWidth()
                    .height(DayLabelHeight + (cell + CellGap) * months.size)
                    .semantics { contentDescription = "$title, ${year.year}, $summary" }
                    .pointerInput(year.year) {
                        detectTapGestures { pick(dayAt(it, year.year, today, cell.toPx(), CellGap.toPx(), MonthLabelWidth.toPx(), DayLabelHeight.toPx())) }
                    }
                    .pointerInput(year.year) {
                        detectHorizontalDragGestures { change, _ ->
                            pick(dayAt(change.position, year.year, today, cell.toPx(), CellGap.toPx(), MonthLabelWidth.toPx(), DayLabelHeight.toPx()))
                        }
                    },
            ) {
                val step = cell.toPx() + CellGap.toPx()
                val left = MonthLabelWidth.toPx()
                val top = DayLabelHeight.toPx()
                val corner = CornerRadius(cell.toPx() * 0.28f)
                LabeledDays.forEach { day ->
                    val text = measurer.measure(day.toString(), labelStyle)
                    val x = left + (day - 1) * step + (cell.toPx() - text.size.width) / 2
                    drawText(text, labelColor, Offset(x.coerceAtMost(size.width - text.size.width), 0f))
                }
                months.forEachIndexed { row, month ->
                    val y = top + row * step
                    val label = measurer.measure(formats.monthShort(month), labelStyle, maxLines = 1)
                    drawText(label, labelColor, Offset(0f, y + (cell.toPx() - label.size.height) / 2))
                    for (dayOfMonth in 1..month.lengthOfMonth()) {
                        val date = month.atDay(dayOfMonth)
                        // Days still ahead stay blank, so the year fills in as it goes.
                        if (date.isAfter(today)) break
                        val day = year.days[date]
                        val mood = day?.averageMood?.let { nearestMood(moods, it) }
                        val fill = when {
                            day == null -> emptyFill
                            mood == null -> noMoodFill
                            else -> moodFill[mood.id] ?: noMoodFill
                        }
                        val origin = Offset(left + (dayOfMonth - 1) * step, y)
                        drawRoundRect(fill, origin, Size(cell.toPx(), cell.toPx()), corner)
                        if (date.toEpochDay() == selected) {
                            drawRoundRect(ring, origin, Size(cell.toPx(), cell.toPx()), corner, style = Stroke(1.5.dp.toPx()))
                        }
                    }
                }
            }
        }
        val picked = selected?.let(LocalDate::ofEpochDay)?.takeIf { it.year == year.year }
        if (picked != null) {
            val day = year.days[picked] ?: HeatDay(picked, 0, null)
            Text(
                heatCaption(day, day.averageMood?.let { nearestMood(moods, it) }, formats, today),
                style = MaterialTheme.typography.labelLarge,
                modifier = Modifier.padding(top = Spacing.m),
            )
        } else {
            hintOrNull(stringResource(R.string.insights_year_hint))?.let {
                Text(it, style = MaterialTheme.typography.bodySmall, color = labelColor, modifier = Modifier.padding(top = Spacing.m))
            }
        }
        MoodLegend(moods, Modifier.padding(top = Spacing.m))
    }
}

/**
 * The day of [year] drawn at [position] in the grid (a row per month, a column per day of the
 * month), or null between months' ends, in the labels or after [today].
 */
internal fun dayAt(position: Offset, year: Int, today: LocalDate, cell: Float, gap: Float, left: Float, top: Float): LocalDate? {
    val step = cell + gap
    if (step <= 0f || position.x < left || position.y < top) return null
    val row = ((position.y - top) / step).toInt()
    val column = ((position.x - left) / step).toInt()
    if (row !in 0..11 || column !in 0 until DAY_COLUMNS) return null
    val month = YearMonth.of(year, row + 1)
    if (column >= month.lengthOfMonth()) return null
    return month.atDay(column + 1).takeIf { !it.isAfter(today) }
}
