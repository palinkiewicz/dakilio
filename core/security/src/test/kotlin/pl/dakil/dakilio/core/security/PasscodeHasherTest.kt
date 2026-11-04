package pl.dakil.dakilio.core.security

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PasscodeHasherTest {
    // Few iterations keep the tests fast; the hash stores the count.
    private val hasher = PasscodeHasher(iterations = 1_000)

    @Test
    fun `right secret verifies, wrong one doesn't`() {
        val stored = hasher.hash("1234".toCharArray())
        assertTrue(hasher.verify("1234".toCharArray(), stored))
        assertFalse(hasher.verify("1235".toCharArray(), stored))
        assertFalse(hasher.verify("".toCharArray(), stored))
    }

    @Test
    fun `same secret gets a different salt each time`() {
        val a = hasher.hash("zażółć".toCharArray())
        val b = hasher.hash("zażółć".toCharArray())
        assertNotEquals(a.encode(), b.encode())
        assertTrue(hasher.verify("zażółć".toCharArray(), b))
    }

    @Test
    fun `encode round-trips and keeps the iteration count`() {
        val stored = hasher.hash("hunter2".toCharArray())
        val decoded = PasscodeHash.decode(stored.encode())!!
        assertEquals(1_000, decoded.iterations)
        // A hasher with another default still checks against the stored count.
        assertTrue(PasscodeHasher(iterations = 5_000).verify("hunter2".toCharArray(), decoded))
    }

    @Test
    fun `malformed encodings decode to null`() {
        assertNull(PasscodeHash.decode(""))
        assertNull(PasscodeHash.decode("abc"))
        assertNull(PasscodeHash.decode("a:b:c"))
        assertNull(PasscodeHash.decode("AAAA:0:AAAA"))
        assertNull(PasscodeHash.decode(":1000:AAAA"))
    }
}
