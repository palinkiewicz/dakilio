package pl.dakil.dakilio.core.security

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class LockoutPolicyTest {
    @Test
    fun `five tries before the first wait`() {
        for (failures in 0..4) assertEquals(0L, LockoutPolicy.delayAfter(failures))
        assertEquals(5, LockoutPolicy.attemptsLeft(0))
        // One left: the next failure is the fifth, which starts the wait.
        assertEquals(1, LockoutPolicy.attemptsLeft(4))
        assertEquals(0, LockoutPolicy.attemptsLeft(5))
        assertEquals(30_000L, LockoutPolicy.delayAfter(5))
    }

    @Test
    fun `delays grow then cap`() {
        assertEquals(60_000L, LockoutPolicy.delayAfter(6))
        assertEquals(300_000L, LockoutPolicy.delayAfter(7))
        assertEquals(LockoutPolicy.MAX_DELAY, LockoutPolicy.delayAfter(8))
        assertEquals(LockoutPolicy.MAX_DELAY, LockoutPolicy.delayAfter(50))
    }

    @Test
    fun `locked until the delay has passed`() {
        val at = 1_000_000L
        assertNull(LockoutPolicy.lockedUntil(failures = 4, lastFailureAt = at, now = at))
        assertEquals(at + 30_000, LockoutPolicy.lockedUntil(5, at, at + 10_000))
        assertNull(LockoutPolicy.lockedUntil(5, at, at + 30_000))
    }

    @Test
    fun `setting the clock back doesn't skip the wait`() {
        val at = 1_000_000L
        val now = at - 60_000
        assertEquals(now + LockoutPolicy.MAX_DELAY, LockoutPolicy.lockedUntil(5, at, now))
    }
}
