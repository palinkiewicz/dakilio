package pl.dakil.dakilio.core.model

import java.time.LocalDate
import java.time.LocalDateTime
import java.time.YearMonth
import org.junit.Assert.assertEquals
import org.junit.Test

class RecapsTest {
    private val oct = YearMonth.of(2026, 10)

    @Test fun `a month's recap is offered for its first week after`() {
        assertEquals(listOf(RecapOffer.Month(oct)), Recaps.offers(LocalDate.of(2026, 11, 1), null, null))
        assertEquals(listOf(RecapOffer.Month(oct)), Recaps.offers(LocalDate.of(2026, 11, 7), null, null))
        assertEquals(emptyList<RecapOffer>(), Recaps.offers(LocalDate.of(2026, 11, 8), null, null))
    }

    @Test fun `early January offers the year and December together, the year first`() {
        val offers = Recaps.offers(LocalDate.of(2027, 1, 3), null, null)
        assertEquals(listOf(RecapOffer.Year(2026), RecapOffer.Month(YearMonth.of(2026, 12))), offers)
        // The year stays a week longer than December.
        assertEquals(listOf(RecapOffer.Year(2026)), Recaps.offers(LocalDate.of(2027, 1, 14), null, null))
        assertEquals(emptyList<RecapOffer>(), Recaps.offers(LocalDate.of(2027, 1, 15), null, null))
    }

    @Test fun `dismissing hides only that month or year`() {
        val jan3 = LocalDate.of(2027, 1, 3)
        assertEquals(listOf(RecapOffer.Year(2026)), Recaps.offers(jan3, YearMonth.of(2026, 12), null))
        assertEquals(listOf(RecapOffer.Month(YearMonth.of(2026, 12))), Recaps.offers(jan3, null, 2026))
        // An old dismissal doesn't hide the next month.
        assertEquals(listOf(RecapOffer.Month(oct)), Recaps.offers(LocalDate.of(2026, 11, 2), YearMonth.of(2026, 9), null))
    }

    @Test fun `finished months run from the first entry, newest first`() {
        assertEquals(listOf(oct, YearMonth.of(2026, 9)), Recaps.finishedMonths(LocalDate.of(2026, 9, 20), LocalDate.of(2026, 11, 16)))
        assertEquals(emptyList<YearMonth>(), Recaps.finishedMonths(LocalDate.of(2026, 11, 2), LocalDate.of(2026, 11, 16)))
    }

    @Test fun `the next notification is at nine on the first`() {
        assertEquals(LocalDateTime.of(2026, 12, 1, 9, 0), Recaps.nextNotification(LocalDateTime.of(2026, 11, 16, 12, 0)))
        assertEquals(LocalDateTime.of(2026, 11, 1, 9, 0), Recaps.nextNotification(LocalDateTime.of(2026, 11, 1, 8, 59)))
        assertEquals(LocalDateTime.of(2026, 12, 1, 9, 0), Recaps.nextNotification(LocalDateTime.of(2026, 11, 1, 9, 0)))
        assertEquals(LocalDateTime.of(2027, 1, 1, 9, 0), Recaps.nextNotification(LocalDateTime.of(2026, 12, 31, 23, 0)))
    }

    @Test fun `the first of January announces the year too`() {
        assertEquals(listOf(RecapOffer.Year(2026), RecapOffer.Month(YearMonth.of(2026, 12))), Recaps.announced(LocalDate.of(2027, 1, 1)))
        assertEquals(listOf(RecapOffer.Month(oct)), Recaps.announced(LocalDate.of(2026, 11, 1)))
    }
}
