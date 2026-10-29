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

    fun longest(days: Set<LocalDate>): Int {
        var best = 0
        for (day in days) {
            if (day.minusDays(1) in days) continue // only count from the start of each run
            var length = 1
            var next = day.plusDays(1)
            while (next in days) {
                length++
                next = next.plusDays(1)
            }
            best = maxOf(best, length)
        }
        return best
    }
}
