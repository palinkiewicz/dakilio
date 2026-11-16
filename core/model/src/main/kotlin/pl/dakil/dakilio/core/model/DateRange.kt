package pl.dakil.dakilio.core.model

import java.time.LocalDate

/** A span of whole days, both ends included. */
data class DateRange(val from: LocalDate, val to: LocalDate) {
    init {
        require(!to.isBefore(from)) { "A range can't end ($to) before it starts ($from)" }
    }

    /** How many days it covers; a single day is 1. */
    val days: Int get() = (to.toEpochDay() - from.toEpochDay()).toInt() + 1

    operator fun contains(date: LocalDate): Boolean = !date.isBefore(from) && !date.isAfter(to)

    /** The part shared with [other], or `null` when they don't touch. */
    fun clip(other: DateRange): DateRange? {
        val start = maxOf(from, other.from)
        val end = minOf(to, other.to)
        return if (end.isBefore(start)) null else DateRange(start, end)
    }

    /** Every day of the range, oldest first. */
    fun dates(): Sequence<LocalDate> = generateSequence(from) { it.plusDays(1) }.takeWhile { !it.isAfter(to) }
}
