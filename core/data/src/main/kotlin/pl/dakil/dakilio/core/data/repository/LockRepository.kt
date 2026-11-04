package pl.dakil.dakilio.core.data.repository

import kotlinx.coroutines.flow.Flow
import pl.dakil.dakilio.core.model.AutoLockDelay
import pl.dakil.dakilio.core.model.BiometricStrength
import pl.dakil.dakilio.core.model.LockMethod
import pl.dakil.dakilio.core.model.LockSettings

/** The outcome of checking a PIN, password or recovery answer. */
sealed interface VerifyResult {
    data object Ok : VerifyResult

    /**
     * @property attemptsLeft free tries before delays start; 0 once they apply.
     * @property lockedUntil wall-clock millis input is blocked until, if this failure started a wait.
     */
    data class Wrong(val attemptsLeft: Int, val lockedUntil: Long?) : VerifyResult

    /** Input is blocked after earlier failures; nothing was checked. */
    data class LockedOut(val until: Long) : VerifyResult
}

/**
 * The app lock's settings and credential. Secrets go in as `CharArray`s, are hashed off the main
 * thread and are cleared afterwards; only salted hashes are stored.
 */
interface LockRepository {
    val settings: Flow<LockSettings>

    /** Until when input is blocked after failed attempts (wall-clock millis); `null` when it isn't. */
    val lockedUntil: Flow<Long?>

    /** Sets a PIN or password and turns the lock on. [secret] is cleared. */
    suspend fun setCredential(method: LockMethod, secret: CharArray)

    /** Checks the PIN or password. [secret] is cleared. Failures count toward [lockedUntil]. */
    suspend fun verify(secret: CharArray): VerifyResult

    /** Checks the recovery answer; failures count the same as wrong PINs. */
    suspend fun verifyRecoveryAnswer(answer: String): VerifyResult

    /** A successful unlock some other way (biometrics, the phone's screen lock): failures start over. */
    suspend fun recordSuccess()

    suspend fun disable()

    suspend fun setBiometrics(enabled: Boolean)

    suspend fun invalidateBiometrics()

    suspend fun clearBiometricsInvalidated()

    suspend fun setBiometricStrength(strength: BiometricStrength)

    suspend fun setNoRecovery()

    suspend fun setDeviceCredentialRecovery()

    suspend fun setRecoveryQuestion(question: String, answer: String)

    suspend fun setAutoLock(delay: AutoLockDelay)

    suspend fun setSecureScreen(secure: Boolean)

    suspend fun setShuffleKeypad(shuffle: Boolean)
}
