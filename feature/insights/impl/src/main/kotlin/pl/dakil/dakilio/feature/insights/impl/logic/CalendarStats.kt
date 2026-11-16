package pl.dakil.dakilio.feature.insights.impl.logic

import java.time.LocalDate
import java.time.YearMonth
import kotlin.math.roundToInt
import pl.dakil.dakilio.core.model.DateRange
import pl.dakil.dakilio.core.model.MOOD_LEVELS

/** How a period's days are laid out, by its length. */
enum class CalendarForm {
    /** Up to a week: one tile per day. */
    Strip,

    /** Up to two months: a calendar grid, a cell per day. */
    Grid,

    /** Up to a year: a column per month, a small cell per day. */
    Pixels,

    /** Longer: a row per year, a cell per month. */
    Years,
}

fun calendarForm(range: DateRange): CalendarForm = when {
    range.days <= 7 -> CalendarForm.Strip
    range.days <= 62 -> CalendarForm.Grid
    YearMonth.from(range.from).plusMonths(12).isAfter(YearMonth.from(range.to)) -> CalendarForm.Pixels
    else -> CalendarForm.Years
}

/** A day on a calendar: its entries and the level its average is nearest to (`null` = no mood). */
data class DayCell(val date: LocalDate, val entries: Int, val level: Int?)

/** The days with entries in [range], by date. */
fun dayCells(index: InsightsIndex, range: DateRange): Map<LocalDate, DayCell> =
    index.days(range).associate { day -> day.date to DayCell(day.date, day.entries, day.mood?.let(::nearestLevel)) }

/** The level an average is shown as. */
fun nearestLevel(level: Float): Int = level.roundToInt().coerceIn(1, MOOD_LEVELS)

/** The months [range] touches, oldest first. */
fun monthsOf(range: DateRange): List<YearMonth> =
    generateSequence(YearMonth.from(range.from)) { it.plusMonths(1) }.takeWhile { !it.isAfter(YearMonth.from(range.to)) }.toList()
