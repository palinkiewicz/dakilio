package pl.dakil.dakilio.core.security

import java.util.Locale

/** What a PIN, password or recovery question has to be. */
object PasscodeRules {
    const val PIN_MIN = 4
    const val PIN_MAX = 12
    const val PASSWORD_MIN = 4
    const val PASSWORD_MAX = 128
    const val QUESTION_MAX = 200
    const val ANSWER_MIN = 2

    fun isValidPin(pin: CharSequence): Boolean = pin.length in PIN_MIN..PIN_MAX && pin.all { it in '0'..'9' }

    /** Any characters, but not only spaces; length counted as typed. */
    fun isValidPassword(password: CharSequence): Boolean =
        password.length in PASSWORD_MIN..PASSWORD_MAX && password.isNotBlank()

    fun isValidQuestion(question: CharSequence): Boolean = question.trim().length in 1..QUESTION_MAX

    fun isValidAnswer(answer: CharSequence): Boolean = normalizeRecoveryAnswer(answer).length >= ANSWER_MIN

    /**
     * A recovery answer as it's hashed: trimmed, lowercased and with runs of whitespace collapsed,
     * so "  Burek " and "burek" match. Nobody remembers the exact spelling years later.
     */
    fun normalizeRecoveryAnswer(answer: CharSequence): String =
        answer.trim().split(Regex("\\s+")).joinToString(" ").lowercase(Locale.ROOT)
}
