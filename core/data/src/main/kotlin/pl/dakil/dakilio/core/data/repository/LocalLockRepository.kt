package pl.dakil.dakilio.core.data.repository

import javax.inject.Inject
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.transformLatest
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import pl.dakil.dakilio.core.common.DakilioDispatchers
import pl.dakil.dakilio.core.common.Dispatcher
import pl.dakil.dakilio.core.datastore.LockDataSource
import pl.dakil.dakilio.core.datastore.LockFailures
import pl.dakil.dakilio.core.model.AutoLockDelay
import pl.dakil.dakilio.core.model.BiometricStrength
import pl.dakil.dakilio.core.model.LockMethod
import pl.dakil.dakilio.core.model.LockSettings
import pl.dakil.dakilio.core.model.RecoveryMethod
import pl.dakil.dakilio.core.security.LockoutPolicy
import pl.dakil.dakilio.core.security.PasscodeHash
import pl.dakil.dakilio.core.security.PasscodeHasher
import pl.dakil.dakilio.core.security.PasscodeRules

internal class LocalLockRepository @Inject constructor(
    private val dataSource: LockDataSource,
    @Dispatcher(DakilioDispatchers.Default) private val default: CoroutineDispatcher,
) : LockRepository {
    private val hasher = PasscodeHasher()

    override val settings: Flow<LockSettings> = dataSource.settings

    // Emits the end of a wait, then null once it's over, so the UI needn't compare clocks itself.
    override val lockedUntil: Flow<Long?> = dataSource.failures.transformLatest { failures ->
        val until = failures.lockedUntil(System.currentTimeMillis())
        emit(until)
        if (until != null) {
            delay(until - System.currentTimeMillis())
            emit(null)
        }
    }

    override suspend fun setCredential(method: LockMethod, secret: CharArray) {
        try {
            require(method != LockMethod.None)
            val valid = if (method == LockMethod.Pin) PasscodeRules.isValidPin(String(secret)) else PasscodeRules.isValidPassword(String(secret))
            require(valid) { "Invalid ${method.name}" }
            val hash = withContext(default) { hasher.hash(secret) }
            dataSource.setCredential(method, if (method == LockMethod.Pin) secret.size else 0, hash.encode())
        } finally {
            secret.fill(' ')
        }
    }

    override suspend fun verify(secret: CharArray): VerifyResult {
        try {
            val stored = dataSource.credentialHash()?.let(PasscodeHash::decode)
            return check(stored) { hasher.verify(secret, it) }
        } finally {
            secret.fill(' ')
        }
    }

    override suspend fun verifyRecoveryAnswer(answer: String): VerifyResult {
        if (dataSource.settings.first().recovery != RecoveryMethod.Question) return VerifyResult.Wrong(0, null)
        val stored = dataSource.recoveryAnswerHash()?.let(PasscodeHash::decode)
        val normalized = PasscodeRules.normalizeRecoveryAnswer(answer).toCharArray()
        return try {
            check(stored) { hasher.verify(normalized, it) }
        } finally {
            normalized.fill(' ')
        }
    }

    /** Checks against [stored] unless input is blocked, and records the outcome. */
    private suspend fun check(stored: PasscodeHash?, matches: (PasscodeHash) -> Boolean): VerifyResult {
        val now = System.currentTimeMillis()
        dataSource.failures.first().lockedUntil(now)?.let { return VerifyResult.LockedOut(it) }
        val ok = stored != null && withContext(default) { matches(stored) }
        if (ok) {
            dataSource.resetFailures()
            return VerifyResult.Ok
        }
        val failures = dataSource.recordFailure(now)
        return VerifyResult.Wrong(LockoutPolicy.attemptsLeft(failures.count), failures.lockedUntil(now))
    }

    override suspend fun recordSuccess() = dataSource.resetFailures()

    override suspend fun disable() = dataSource.disable()

    override suspend fun setBiometrics(enabled: Boolean) = dataSource.setBiometrics(enabled)

    override suspend fun invalidateBiometrics() = dataSource.invalidateBiometrics()

    override suspend fun clearBiometricsInvalidated() = dataSource.clearBiometricsInvalidated()

    override suspend fun setBiometricStrength(strength: BiometricStrength) = dataSource.setBiometricStrength(strength)

    override suspend fun setNoRecovery() = dataSource.setRecovery(RecoveryMethod.None)

    override suspend fun setDeviceCredentialRecovery() = dataSource.setRecovery(RecoveryMethod.DeviceCredential)

    override suspend fun setRecoveryQuestion(question: String, answer: String) {
        require(PasscodeRules.isValidQuestion(question) && PasscodeRules.isValidAnswer(answer))
        val normalized = PasscodeRules.normalizeRecoveryAnswer(answer).toCharArray()
        val hash = try {
            withContext(default) { hasher.hash(normalized) }
        } finally {
            normalized.fill(' ')
        }
        dataSource.setRecovery(RecoveryMethod.Question, question.trim(), hash.encode())
    }

    override suspend fun setAutoLock(delay: AutoLockDelay) = dataSource.setAutoLock(delay)

    override suspend fun setSecureScreen(secure: Boolean) = dataSource.setSecureScreen(secure)

    override suspend fun setShuffleKeypad(shuffle: Boolean) = dataSource.setShuffleKeypad(shuffle)

    private fun LockFailures.lockedUntil(now: Long): Long? = LockoutPolicy.lockedUntil(count, lastAt, now)
}
