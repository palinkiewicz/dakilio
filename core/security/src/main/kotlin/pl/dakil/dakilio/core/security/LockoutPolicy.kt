package pl.dakil.dakilio.core.security

/**
 * How long input waits after failed unlock attempts: [FREE_ATTEMPTS] tries, then a wait after the
 * last of them and after every failure from then on, growing each time. PINs and recovery answers
 * share one failure count.
 */
object LockoutPolicy {
    const val FREE_ATTEMPTS = 5
    private val DELAYS = longArrayOf(30_000, 60_000, 5 * 60_000)
    const val MAX_DELAY = 15 * 60_000L

    /** The wait after the [failures]-th failure in a row; 0 for the ones before the free tries run out. */
    fun delayAfter(failures: Int): Long {
        val step = failures - FREE_ATTEMPTS + 1
        return when {
            step <= 0 -> 0
            step <= DELAYS.size -> DELAYS[step - 1]
            else -> MAX_DELAY
        }
    }

    /** Tries left before a wait; 0 once waits apply. */
    fun attemptsLeft(failures: Int): Int = (FREE_ATTEMPTS - failures).coerceAtLeast(0)

    /**
     * Until when input is blocked (wall-clock millis), or `null` if it isn't. A clock set back to
     * before the last failure can't skip the wait: it then counts the longest delay from now.
     */
    fun lockedUntil(failures: Int, lastFailureAt: Long, now: Long): Long? {
        val delay = delayAfter(failures)
        if (delay == 0L) return null
        if (now < lastFailureAt) return now + MAX_DELAY
        return (lastFailureAt + delay).takeIf { it > now }
    }
}
