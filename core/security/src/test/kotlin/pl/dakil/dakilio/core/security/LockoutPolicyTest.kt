package pl.dakil.dakilio.core.security

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class LockoutPolicyTest {
    @Test
    fun `first five failures are free`() {
        for (failures in 0..5) assertEquals(0L, LockoutPolicy.delayAfter(failures))
        assertEquals(5, LockoutPolicy.attemptsLeft(0))
        assertEquals(1, LockoutPolicy.attemptsLeft(4))
        assertEquals(0, LockoutPolicy.attemptsLeft(7))
    }

    @Test
    fun `delays grow then cap`() {
        assertEquals(30_000L, LockoutPolicy.delayAfter(6))
        assertEquals(60_000L, LockoutPolicy.delayAfter(7))
        assertEquals(300_000L, LockoutPolicy.delayAfter(8))
        assertEquals(LockoutPolicy.MAX_DELAY, LockoutPolicy.delayAfter(9))
        assertEquals(LockoutPolicy.MAX_DELAY, LockoutPolicy.delayAfter(50))
    }

    @Test
    fun `locked until the delay has passed`() {
        val at = 1_000_000L
        assertNull(LockoutPolicy.lockedUntil(failures = 5, lastFailureAt = at, now = at))
        assertEquals(at + 30_000, LockoutPolicy.lockedUntil(6, at, at + 10_000))
        assertNull(LockoutPolicy.lockedUntil(6, at, at + 30_000))
    }

    @Test
    fun `setting the clock back doesn't skip the wait`() {
        val at = 1_000_000L
        val now = at - 60_000
        assertEquals(now + LockoutPolicy.MAX_DELAY, LockoutPolicy.lockedUntil(6, at, now))
    }
}
