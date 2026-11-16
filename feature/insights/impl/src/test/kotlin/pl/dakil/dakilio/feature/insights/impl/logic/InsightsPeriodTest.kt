package pl.dakil.dakilio.feature.insights.impl.logic

import java.time.DayOfWeek
import java.time.LocalDate
import java.time.YearMonth
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import pl.dakil.dakilio.core.model.DateRange

class InsightsPeriodTest {
    private val first = LocalDate.of(2025, 3, 10)
    private val bounds = Bounds(first, TODAY)
    private fun d(y: Int, m: Int, day: Int) = LocalDate.of(y, m, day)

    @Test
    fun `a period is found from a date and the first day of the week`() {
        assertEquals(InsightsPeriod.Week(d(2026, 10, 26)), InsightsPeriod.of(PeriodKind.Week, TODAY, DayOfWeek.MONDAY))
        assertEquals(InsightsPeriod.Week(d(2026, 10, 25)), InsightsPeriod.of(PeriodKind.Week, TODAY, DayOfWeek.SUNDAY))
        assertEquals(InsightsPeriod.Month(YearMonth.of(2026, 10)), InsightsPeriod.of(PeriodKind.Month, TODAY, DayOfWeek.MONDAY))
        assertEquals(InsightsPeriod.Year(2026), InsightsPeriod.of(PeriodKind.Year, TODAY, DayOfWeek.MONDAY))
    }

    @Test
    fun `a running period's numbers stop today`() {
        val month = InsightsPeriod.Month(YearMonth.of(2026, 10))
        assertEquals(DateRange(d(2026, 10, 1), d(2026, 10, 31)), month.calendarRange(bounds))
        assertEquals(DateRange(d(2026, 10, 1), TODAY), month.range(bounds))
        assertTrue(month.isInProgress(bounds))
        assertFalse(InsightsPeriod.Month(YearMonth.of(2026, 9)).isInProgress(bounds))
        assertFalse(InsightsPeriod.AllTime.isInProgress(bounds))
    }

    @Test
    fun `all time runs from the first entry to today`() {
        assertEquals(DateRange(first, TODAY), InsightsPeriod.AllTime.range(bounds))
        // No entries yet: a single day, never a reversed range.
        assertEquals(DateRange(TODAY, TODAY), InsightsPeriod.AllTime.range(Bounds(TODAY, TODAY)))
    }

    @Test
    fun `stepping moves by the period's own length`() {
        assertEquals(InsightsPeriod.Month(YearMonth.of(2025, 12)), InsightsPeriod.Month(YearMonth.of(2026, 1)).previous())
        assertEquals(InsightsPeriod.Week(d(2026, 11, 2)), InsightsPeriod.Week(d(2026, 10, 26)).next())
        assertEquals(InsightsPeriod.Year(2025), InsightsPeriod.Year(2026).previous())
        assertEquals(InsightsPeriod.Custom(d(2026, 9, 21), d(2026, 9, 30)), InsightsPeriod.Custom(d(2026, 10, 1), d(2026, 10, 10)).previous())
        assertNull(InsightsPeriod.AllTime.previous())
    }

    @Test
    fun `stepping stays between the first entry and today`() {
        val current = InsightsPeriod.Month(YearMonth.of(2026, 10))
        assertFalse(current.canGoForward(bounds))
        assertTrue(current.canGoBack(bounds))
        val firstMonth = InsightsPeriod.Month(YearMonth.of(2025, 3))
        assertFalse(firstMonth.canGoBack(bounds))
        assertTrue(firstMonth.canGoForward(bounds))
        assertFalse(InsightsPeriod.AllTime.canGoBack(bounds))
    }

    @Test
    fun `switching kind keeps the period's last day in view`() {
        val september = InsightsPeriod.Month(YearMonth.of(2026, 9))
        assertEquals(InsightsPeriod.Week(d(2026, 9, 28)), september.switchTo(PeriodKind.Week, bounds, DayOfWeek.MONDAY))
        assertEquals(InsightsPeriod.Year(2026), september.switchTo(PeriodKind.Year, bounds, DayOfWeek.MONDAY))
        // A running year lands on today's month, not December.
        assertEquals(InsightsPeriod.Month(YearMonth.of(2026, 10)), InsightsPeriod.Year(2026).switchTo(PeriodKind.Month, bounds, DayOfWeek.MONDAY))
    }

    @Test
    fun `a finished period is compared with the whole one before`() {
        val september = InsightsPeriod.Month(YearMonth.of(2026, 9))
        assertEquals(DateRange(d(2026, 8, 1), d(2026, 8, 31)), comparisonRange(september, Comparison.Previous, bounds))
        assertEquals(DateRange(d(2025, 9, 1), d(2025, 9, 30)), comparisonRange(september, Comparison.YearBefore, bounds))
        assertNull(comparisonRange(september, Comparison.None, bounds))
        assertNull(comparisonRange(InsightsPeriod.AllTime, Comparison.Previous, bounds))
    }

    @Test
    fun `a running period is compared with the same days of the one before`() {
        val october = InsightsPeriod.Month(YearMonth.of(2026, 10)) // 29 days so far
        assertEquals(DateRange(d(2026, 9, 1), d(2026, 9, 29)), comparisonRange(october, Comparison.Previous, bounds))
        // March so far (31 days on the 31st) against February: never past the earlier period's end.
        val march = Bounds(first, d(2026, 3, 31))
        val whole = comparisonRange(InsightsPeriod.Month(YearMonth.of(2026, 3)), Comparison.Previous, march)
        assertEquals(DateRange(d(2026, 2, 1), d(2026, 2, 28)), whole)
    }

    @Test
    fun `a picked period is compared whole`() {
        val october = InsightsPeriod.Month(YearMonth.of(2026, 10))
        val picked = Comparison.Picked(InsightsPeriod.Month(YearMonth.of(2026, 1)))
        assertEquals(DateRange(d(2026, 1, 1), d(2026, 1, 31)), comparisonRange(october, picked, bounds))
    }

    @Test
    fun `the slice follows the range's length`() {
        assertEquals(Granularity.Days, defaultGranularity(DateRange(d(2026, 10, 1), d(2026, 10, 31))))
        assertEquals(Granularity.Weeks, defaultGranularity(DateRange(d(2026, 1, 1), d(2026, 12, 31))))
        assertEquals(Granularity.Months, defaultGranularity(DateRange(d(2023, 1, 1), d(2026, 12, 31))))
        assertEquals(listOf(Granularity.Days, Granularity.Weeks), granularities(DateRange(d(2026, 10, 1), d(2026, 10, 31))))
        assertEquals(listOf(Granularity.Weeks, Granularity.Months), granularities(DateRange(d(2026, 1, 1), d(2026, 12, 31))))
    }

    @Test
    fun `slices follow the calendar and are cut to the range`() {
        val range = DateRange(d(2026, 10, 1), d(2026, 10, 29)) // Thursday to Thursday
        val weeks = buckets(range, Granularity.Weeks, DayOfWeek.MONDAY)
        assertEquals(DateRange(d(2026, 10, 1), d(2026, 10, 4)), weeks.first())
        assertEquals(DateRange(d(2026, 10, 5), d(2026, 10, 11)), weeks[1])
        assertEquals(DateRange(d(2026, 10, 26), d(2026, 10, 29)), weeks.last())
        assertEquals(5, weeks.size)
        assertEquals(29, buckets(range, Granularity.Days, DayOfWeek.MONDAY).size)
        val months = buckets(DateRange(d(2025, 11, 15), d(2026, 2, 10)), Granularity.Months, DayOfWeek.MONDAY)
        assertEquals(listOf(d(2025, 11, 15), d(2025, 12, 1), d(2026, 1, 1), d(2026, 2, 1)), months.map { it.from })
        assertEquals(d(2026, 2, 10), months.last().to)
        assertEquals(2, buckets(DateRange(d(2025, 11, 15), d(2026, 2, 10)), Granularity.Years, DayOfWeek.MONDAY).size)
    }
}

class PeriodCodecTest {
    private val periods = listOf(
        InsightsPeriod.Week(LocalDate.of(2026, 10, 26)),
        InsightsPeriod.Month(YearMonth.of(2026, 2)),
        InsightsPeriod.Year(2025),
        InsightsPeriod.AllTime,
        InsightsPeriod.Custom(LocalDate.of(2026, 1, 5), LocalDate.of(2026, 3, 9)),
    )

    @Test
    fun `periods survive being written down`() {
        periods.forEach { assertEquals(it, decodePeriod(it.encode())) }
        assertNull(decodePeriod(null))
        assertNull(decodePeriod("month:soon"))
        assertNull(decodePeriod("fortnight:1"))
    }

    @Test
    fun `comparisons survive being written down`() {
        val all = listOf(Comparison.Previous, Comparison.YearBefore, Comparison.None) + periods.map(Comparison::Picked)
        all.forEach { assertEquals(it, decodeComparison(it.encode())) }
        assertEquals(Comparison.Previous, decodeComparison(null))
        assertEquals(Comparison.Previous, decodeComparison("pick:nonsense"))
    }
}

class CalendarStatsTest {
    private fun d(y: Int, m: Int, day: Int) = LocalDate.of(y, m, day)

    @Test
    fun `the calendar's form follows the range`() {
        assertEquals(CalendarForm.Strip, calendarForm(DateRange(d(2026, 10, 26), d(2026, 11, 1))))
        assertEquals(CalendarForm.Grid, calendarForm(DateRange(d(2026, 10, 1), d(2026, 10, 31))))
        assertEquals(CalendarForm.Pixels, calendarForm(DateRange(d(2026, 1, 1), d(2026, 12, 31))))
        assertEquals(CalendarForm.Pixels, calendarForm(DateRange(d(2025, 11, 15), d(2026, 10, 29))))
        assertEquals(CalendarForm.Years, calendarForm(DateRange(d(2025, 10, 15), d(2026, 10, 29))))
    }

    @Test
    fun `a day's cell shows the level nearest to its average`() {
        val index = InsightsIndex(listOf(post(0, 5), post(0, 4), post(1), post(2, 2)))
        val cells = dayCells(index, DateRange(TODAY.minusDays(6), TODAY))
        assertEquals(DayCell(TODAY, 2, 5), cells[TODAY]) // 4.5 rounds up
        assertEquals(DayCell(TODAY.minusDays(1), 1, null), cells[TODAY.minusDays(1)])
        assertNull(cells[TODAY.minusDays(3)])
        assertEquals(listOf(YearMonth.of(2026, 9), YearMonth.of(2026, 10)), monthsOf(DateRange(d(2026, 9, 30), d(2026, 10, 1))))
    }
}
