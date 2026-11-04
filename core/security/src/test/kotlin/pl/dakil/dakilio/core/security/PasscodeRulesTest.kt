package pl.dakil.dakilio.core.security

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PasscodeRulesTest {
    @Test
    fun `pins are 4 to 12 digits`() {
        assertTrue(PasscodeRules.isValidPin("0000"))
        assertTrue(PasscodeRules.isValidPin("123456789012"))
        assertFalse(PasscodeRules.isValidPin("123"))
        assertFalse(PasscodeRules.isValidPin("1234567890123"))
        assertFalse(PasscodeRules.isValidPin("12a4"))
        assertFalse(PasscodeRules.isValidPin("١٢٣٤")) // Non-ASCII digits aren't on the keypad.
    }

    @Test
    fun `passwords need 4 characters that aren't all spaces`() {
        assertTrue(PasscodeRules.isValidPassword("abcd"))
        assertTrue(PasscodeRules.isValidPassword("a b "))
        assertFalse(PasscodeRules.isValidPassword("abc"))
        assertFalse(PasscodeRules.isValidPassword("    "))
    }

    @Test
    fun `answers are normalized before hashing`() {
        assertEquals("burek", PasscodeRules.normalizeRecoveryAnswer("  Burek "))
        assertEquals("ul. długa 5", PasscodeRules.normalizeRecoveryAnswer("Ul.  Długa\t5"))
        assertTrue(PasscodeRules.isValidAnswer(" ab "))
        assertFalse(PasscodeRules.isValidAnswer(" a  "))
    }

    @Test
    fun `questions can't be blank or too long`() {
        assertTrue(PasscodeRules.isValidQuestion("First pet?"))
        assertFalse(PasscodeRules.isValidQuestion("   "))
        assertFalse(PasscodeRules.isValidQuestion("x".repeat(PasscodeRules.QUESTION_MAX + 1)))
    }
}
