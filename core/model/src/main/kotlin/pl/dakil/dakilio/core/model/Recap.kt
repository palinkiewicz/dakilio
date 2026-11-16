package pl.dakil.dakilio.core.model

import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.YearMonth

/** A finished period whose recap is offered. */
sealed interface RecapOffer {
    data class Month(val month: YearMonth) : RecapOffer

    data class Year(val year: Int) : RecapOffer
}

/** When recaps are offered and announced. Whether a period has enough entries is the caller's to check. */
object Recaps {
    /** A month's recap is offered for this many days after it ends. */
    const val MONTH_OFFER_DAYS = 7

    /** A year's recap is offered for this many days after it ends. */
    const val YEAR_OFFER_DAYS = 14

    /** The time of day a recap notification comes. */
    val NOTIFY_AT: LocalTime = LocalTime.of(9, 0)

    /**
     * The recaps on offer on [today], the year first: the month just ended for its first
     * [MONTH_OFFER_DAYS] days, the year just ended for its first [YEAR_OFFER_DAYS], unless that very
     * month or year was dismissed. Early January offers both.
     */
    fun offers(today: LocalDate, dismissedMonth: YearMonth?, dismissedYear: Int?): List<RecapOffer> = buildList {
        val lastYear = today.year - 1
        if (today.dayOfYear <= YEAR_OFFER_DAYS && dismissedYear != lastYear) add(RecapOffer.Year(lastYear))
        val lastMonth = YearMonth.from(today).minusMonths(1)
        if (today.dayOfMonth <= MONTH_OFFER_DAYS && dismissedMonth != lastMonth) add(RecapOffer.Month(lastMonth))
    }

    /** The finished months from [first]'s to the one before [today]'s, newest first. */
    fun finishedMonths(first: LocalDate, today: LocalDate): List<YearMonth> {
        val start = YearMonth.from(first)
        return generateSequence(YearMonth.from(today).minusMonths(1)) { it.minusMonths(1) }.takeWhile { !it.isBefore(start) }.toList()
    }

    /**
     * When the next recap notification is due after [now]: 9:00 on the first of a month. A run a
     * little after 9:00 on the first itself (a late job) still counts as today's.
     */
    fun nextNotification(now: LocalDateTime): LocalDateTime {
        val today = now.toLocalDate()
        val thisMonth = today.withDayOfMonth(1).atTime(NOTIFY_AT)
        return if (now.isBefore(thisMonth)) thisMonth else today.withDayOfMonth(1).plusMonths(1).atTime(NOTIFY_AT)
    }

    /** What a notification on [date] (the first of a month) announces: the year too on 1 January. */
    fun announced(date: LocalDate): List<RecapOffer> = buildList {
        if (date.dayOfYear == 1) add(RecapOffer.Year(date.year - 1))
        add(RecapOffer.Month(YearMonth.from(date).minusMonths(1)))
    }
}
