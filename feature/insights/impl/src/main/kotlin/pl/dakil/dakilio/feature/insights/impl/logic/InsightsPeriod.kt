package pl.dakil.dakilio.feature.insights.impl.logic

import java.time.DayOfWeek
import java.time.LocalDate
import java.time.YearMonth
import java.time.temporal.TemporalAdjusters
import pl.dakil.dakilio.core.model.DateRange

enum class PeriodKind { Week, Month, Year, All, Custom }

/** How far the journal reaches: from its first entry to today. Periods never leave it. */
data class Bounds(val first: LocalDate, val today: LocalDate) {
    val range: DateRange get() = DateRange(minOf(first, today), today)
}

/**
 * The stretch of time Insights looks at. Calendar periods have a name ("March 2025") and can be
 * stepped through; a [Custom] one steps by its own length.
 */
sealed interface InsightsPeriod {
    val kind: PeriodKind

    /** Seven days from [start], which is the user's first day of the week. */
    data class Week(val start: LocalDate) : InsightsPeriod {
        override val kind get() = PeriodKind.Week
    }

    data class Month(val month: YearMonth) : InsightsPeriod {
        override val kind get() = PeriodKind.Month
    }

    data class Year(val year: Int) : InsightsPeriod {
        override val kind get() = PeriodKind.Year
    }

    data object AllTime : InsightsPeriod {
        override val kind get() = PeriodKind.All
    }

    data class Custom(val from: LocalDate, val to: LocalDate) : InsightsPeriod {
        override val kind get() = PeriodKind.Custom

        companion object {
            fun of(a: LocalDate, b: LocalDate) = Custom(minOf(a, b), maxOf(a, b))
        }
    }

    companion object {
        /** The [kind] of period that holds [date]. [PeriodKind.Custom] gives that single day. */
        fun of(kind: PeriodKind, date: LocalDate, firstDay: DayOfWeek): InsightsPeriod = when (kind) {
            PeriodKind.Week -> Week(date.with(TemporalAdjusters.previousOrSame(firstDay)))
            PeriodKind.Month -> Month(YearMonth.from(date))
            PeriodKind.Year -> Year(date.year)
            PeriodKind.All -> AllTime
            PeriodKind.Custom -> Custom(date, date)
        }
    }
}

/** The period's own days, which may run past today (the rest of this month). */
fun InsightsPeriod.calendarRange(bounds: Bounds): DateRange = when (this) {
    is InsightsPeriod.Week -> DateRange(start, start.plusDays(6))
    is InsightsPeriod.Month -> DateRange(month.atDay(1), month.atEndOfMonth())
    is InsightsPeriod.Year -> DateRange(LocalDate.of(year, 1, 1), LocalDate.of(year, 12, 31))
    InsightsPeriod.AllTime -> bounds.range
    is InsightsPeriod.Custom -> DateRange(from, to)
}

/** The days the period's numbers cover: its calendar days up to today. */
fun InsightsPeriod.range(bounds: Bounds): DateRange {
    val full = calendarRange(bounds)
    val to = minOf(full.to, bounds.today)
    return DateRange(minOf(full.from, to), to)
}

/** Whether the period is still running, so its numbers are "so far". */
fun InsightsPeriod.isInProgress(bounds: Bounds): Boolean =
    this != InsightsPeriod.AllTime && calendarRange(bounds).to.isAfter(bounds.today)

/** The period right before this one, of the same kind and length. */
fun InsightsPeriod.previous(): InsightsPeriod? = step(-1)

fun InsightsPeriod.next(): InsightsPeriod? = step(1)

private fun InsightsPeriod.step(by: Long): InsightsPeriod? = when (this) {
    is InsightsPeriod.Week -> InsightsPeriod.Week(start.plusWeeks(by))
    is InsightsPeriod.Month -> InsightsPeriod.Month(month.plusMonths(by))
    is InsightsPeriod.Year -> InsightsPeriod.Year(year + by.toInt())
    InsightsPeriod.AllTime -> null
    is InsightsPeriod.Custom -> {
        val length = to.toEpochDay() - from.toEpochDay() + 1
        InsightsPeriod.Custom(from.plusDays(by * length), to.plusDays(by * length))
    }
}

/** Stepping back is offered while the earlier period still reaches the first entry. */
fun InsightsPeriod.canGoBack(bounds: Bounds): Boolean =
    previous()?.let { !it.calendarRange(bounds).to.isBefore(bounds.first) } ?: false

/** Stepping forward is offered while the later period has begun. */
fun InsightsPeriod.canGoForward(bounds: Bounds): Boolean =
    next()?.let { !it.calendarRange(bounds).from.isAfter(bounds.today) } ?: false

/**
 * The same moment seen as another [kind] of period: the one holding this period's last day (so
 * November 2026 becomes its last week, or 2026). A custom range can't be derived, so it stays.
 */
fun InsightsPeriod.switchTo(kind: PeriodKind, bounds: Bounds, firstDay: DayOfWeek): InsightsPeriod =
    if (kind == PeriodKind.Custom) this else InsightsPeriod.of(kind, range(bounds).to, firstDay)

/** What a period's numbers are measured against. */
sealed interface Comparison {
    /** The period right before. */
    data object Previous : Comparison

    /** The same period a year earlier. */
    data object YearBefore : Comparison

    data class Picked(val period: InsightsPeriod) : Comparison

    data object None : Comparison
}

/** The period [comparison] means for [period], or `null` when there's none (all time, or [Comparison.None]). */
fun comparisonPeriod(period: InsightsPeriod, comparison: Comparison): InsightsPeriod? = when (comparison) {
    Comparison.Previous -> period.previous()
    Comparison.YearBefore -> when (period) {
        is InsightsPeriod.Week -> InsightsPeriod.Week(period.start.minusWeeks(52))
        is InsightsPeriod.Month -> InsightsPeriod.Month(period.month.minusYears(1))
        is InsightsPeriod.Year -> InsightsPeriod.Year(period.year - 1)
        InsightsPeriod.AllTime -> null
        is InsightsPeriod.Custom -> InsightsPeriod.Custom(period.from.minusYears(1), period.to.minusYears(1))
    }
    is Comparison.Picked -> comparison.period.takeIf { period != InsightsPeriod.AllTime }
    Comparison.None -> null
}

/**
 * The days to compare [period] with. While the period is still running, an earlier period is cut to
 * the same number of days from its start (the first 16 days of October against 16 days of November),
 * so counts stay fair. A picked period is taken whole.
 */
fun comparisonRange(period: InsightsPeriod, comparison: Comparison, bounds: Bounds): DateRange? {
    val other = comparisonPeriod(period, comparison) ?: return null
    if (comparison is Comparison.Picked) return other.range(bounds)
    val full = other.calendarRange(bounds)
    if (!period.isInProgress(bounds)) return full
    val elapsed = period.range(bounds).days
    return DateRange(full.from, minOf(full.to, full.from.plusDays(elapsed - 1L)))
}

/** The slices a period's charts are drawn in. */
enum class Granularity { Days, Weeks, Months, Years }

/** The slice a range of this length reads best in. */
fun defaultGranularity(range: DateRange): Granularity = when {
    range.days <= 45 -> Granularity.Days
    range.days <= 400 -> Granularity.Weeks
    range.days <= 3700 -> Granularity.Months
    else -> Granularity.Years
}

/** The slices that make sense for a range: no hundreds of points, and at least two of each. */
fun granularities(range: DateRange): List<Granularity> = buildList {
    if (range.days <= 92) add(Granularity.Days)
    if (range.days in 14..800) add(Granularity.Weeks)
    if (range.days >= 60) add(Granularity.Months)
    if (range.days >= 730) add(Granularity.Years)
}

/**
 * [range] cut into [granularity] slices, oldest first. Slices follow the calendar (weeks from
 * [firstDay]), and the first and last are cut to the range.
 */
fun buckets(range: DateRange, granularity: Granularity, firstDay: DayOfWeek): List<DateRange> {
    val result = ArrayList<DateRange>()
    var start = range.from
    while (!start.isAfter(range.to)) {
        val end = when (granularity) {
            Granularity.Days -> start
            Granularity.Weeks -> start.with(TemporalAdjusters.previousOrSame(firstDay)).plusDays(6)
            Granularity.Months -> YearMonth.from(start).atEndOfMonth()
            Granularity.Years -> LocalDate.of(start.year, 12, 31)
        }
        result += DateRange(start, minOf(end, range.to))
        start = end.plusDays(1)
    }
    return result
}

/**
 * A period as a short string, for saved state and navigation keys: `week:<epoch day>`,
 * `month:<epoch day of its first>`, `year:<year>`, `all`, `custom:<from>:<to>`.
 */
fun InsightsPeriod.encode(): String = when (this) {
    is InsightsPeriod.Week -> "week:${start.toEpochDay()}"
    is InsightsPeriod.Month -> "month:${month.atDay(1).toEpochDay()}"
    is InsightsPeriod.Year -> "year:$year"
    InsightsPeriod.AllTime -> "all"
    is InsightsPeriod.Custom -> "custom:${from.toEpochDay()}:${to.toEpochDay()}"
}

/** Reads [encode]'s output; anything else is `null`. */
fun decodePeriod(text: String?): InsightsPeriod? {
    val parts = text?.split(':') ?: return null
    fun day(i: Int) = parts.getOrNull(i)?.toLongOrNull()?.let(LocalDate::ofEpochDay)
    return when (parts[0]) {
        "week" -> day(1)?.let(InsightsPeriod::Week)
        "month" -> day(1)?.let { InsightsPeriod.Month(YearMonth.from(it)) }
        "year" -> parts.getOrNull(1)?.toIntOrNull()?.let(InsightsPeriod::Year)
        "all" -> InsightsPeriod.AllTime
        "custom" -> day(1)?.let { from -> day(2)?.let { to -> InsightsPeriod.Custom.of(from, to) } }
        else -> null
    }
}

/** A comparison as a short string: `prev`, `year`, `none` or `pick:<period>`. */
fun Comparison.encode(): String = when (this) {
    Comparison.Previous -> "prev"
    Comparison.YearBefore -> "year"
    Comparison.None -> "none"
    is Comparison.Picked -> "pick:${period.encode()}"
}

/** Reads [encode]'s output; anything else is the default, [Comparison.Previous]. */
fun decodeComparison(text: String?): Comparison = when {
    text == "year" -> Comparison.YearBefore
    text == "none" -> Comparison.None
    text != null && text.startsWith("pick:") -> decodePeriod(text.removePrefix("pick:"))?.let(Comparison::Picked) ?: Comparison.Previous
    else -> Comparison.Previous
}
