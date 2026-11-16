package pl.dakil.dakilio.core.model

import java.time.LocalDate

object Streaks {
    /**
     * Consecutive days with at least one entry, ending today — or yesterday, so a streak isn't
     * "lost" in the morning before today's entry is written.
     */
    fun current(days: Set<LocalDate>, today: LocalDate): Int {
        var day = if (today in days) today else today.minusDays(1)
        var streak = 0
        while (day in days) {
            streak++
            day = day.minusDays(1)
        }
        return streak
    }

    fun longest(days: Set<LocalDate>): Int = longestRun(days)?.days ?: 0

    /** The longest run of consecutive days, as its dates; of equally long runs, the latest. */
    fun longestRun(days: Set<LocalDate>): DateRange? {
        var best: DateRange? = null
        for (day in days) {
            if (day.minusDays(1) in days) continue // only count from the start of each run
            var end = day
            while (end.plusDays(1) in days) end = end.plusDays(1)
            val run = DateRange(day, end)
            val current = best
            if (current == null || run.days > current.days || (run.days == current.days && run.from > current.from)) best = run
        }
        return best
    }

    /** The longest run inside [range]: a run that began earlier counts only from the range's start. */
    fun longestWithin(days: Set<LocalDate>, range: DateRange): DateRange? = longestRun(days.filterTo(HashSet()) { it in range })
}
