package pl.dakil.dakilio.core.security

/**
 * Whether the app locks again when it comes back to the foreground. Times are
 * `SystemClock.elapsedRealtime()` values, which changing the clock can't move.
 */
object AutoLockPolicy {
    /** How long a trip to a picker the app opened (photos, files) still counts as staying in the app. */
    const val PICKER_GRACE = 10 * 60_000L

    /**
     * @param backgroundedAt when the app left the foreground, or `null` if it didn't.
     * @param delay the user's auto-lock delay.
     * @param fromPicker the app left for a picker it opened itself.
     */
    fun shouldLock(enabled: Boolean, backgroundedAt: Long?, now: Long, delay: Long, fromPicker: Boolean): Boolean {
        if (!enabled || backgroundedAt == null) return false
        val allowed = if (fromPicker) maxOf(delay, PICKER_GRACE) else delay
        return allowed == 0L || now - backgroundedAt >= allowed
    }
}
