package pl.dakil.dakilio.core.security

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AutoLockPolicyTest {
    private fun lock(enabled: Boolean = true, away: Long?, delay: Long, fromPicker: Boolean = false) =
        AutoLockPolicy.shouldLock(enabled, away?.let { 1_000L }, 1_000L + (away ?: 0), delay, fromPicker)

    @Test
    fun `never locks while off or without leaving`() {
        assertFalse(lock(enabled = false, away = 999_999, delay = 0))
        assertFalse(lock(away = null, delay = 0))
    }

    @Test
    fun `immediately locks on any trip away`() {
        assertTrue(lock(away = 0, delay = 0))
        assertTrue(lock(away = 1, delay = 0))
    }

    @Test
    fun `a delay lets short trips through`() {
        assertFalse(lock(away = 29_999, delay = 30_000))
        assertTrue(lock(away = 30_000, delay = 30_000))
    }

    @Test
    fun `the app's own pickers get a grace period`() {
        assertFalse(lock(away = 60_000, delay = 0, fromPicker = true))
        assertTrue(lock(away = AutoLockPolicy.PICKER_GRACE, delay = 0, fromPicker = true))
        // A delay longer than the grace still applies.
        assertFalse(lock(away = AutoLockPolicy.PICKER_GRACE, delay = 15 * 60_000, fromPicker = true))
    }
}
