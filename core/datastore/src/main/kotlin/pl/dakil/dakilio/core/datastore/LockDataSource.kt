package pl.dakil.dakilio.core.datastore

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.MutablePreferences
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import javax.inject.Inject
import javax.inject.Qualifier
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import pl.dakil.dakilio.core.model.AutoLockDelay
import pl.dakil.dakilio.core.model.BiometricStrength
import pl.dakil.dakilio.core.model.LockMethod
import pl.dakil.dakilio.core.model.LockSettings
import pl.dakil.dakilio.core.model.RecoveryMethod

/** The app lock's own DataStore file, apart from `settings`: it's never part of a `.dakilio` backup. */
@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class LockStore

/** Failed unlock attempts in a row and when the last one was (wall-clock millis). */
data class LockFailures(val count: Int, val lastAt: Long)

/**
 * The app lock: its settings, the credential and recovery-answer hashes (encoded `PasscodeHash`es)
 * and the failure count. Hashing happens in the data layer; this class only stores strings.
 */
class LockDataSource @Inject constructor(
    @LockStore private val dataStore: DataStore<Preferences>,
) {
    val settings: Flow<LockSettings> = dataStore.data
        .map { prefs ->
            val method = prefs[METHOD].toEnum(LockMethod.None)
            LockSettings(
                method = method,
                pinLength = prefs[PIN_LENGTH] ?: 0,
                biometrics = method != LockMethod.None && (prefs[BIOMETRICS] ?: false),
                biometricStrength = prefs[BIOMETRIC_STRENGTH].toEnum(BiometricStrength.Standard),
                biometricsInvalidated = prefs[BIOMETRICS_INVALIDATED] ?: false,
                recovery = prefs[RECOVERY].toEnum(RecoveryMethod.None),
                recoveryQuestion = prefs[RECOVERY_QUESTION],
                autoLock = prefs[AUTO_LOCK].toEnum(AutoLockDelay.Immediately),
                secureScreen = prefs[SECURE_SCREEN] ?: false,
                shuffleKeypad = prefs[SHUFFLE_KEYPAD] ?: false,
            )
        }
        .distinctUntilChanged()

    val failures: Flow<LockFailures> = dataStore.data
        .map { LockFailures(it[FAILURES] ?: 0, it[LAST_FAILURE_AT] ?: 0L) }
        .distinctUntilChanged()

    suspend fun credentialHash(): String? = dataStore.data.first()[CREDENTIAL]

    suspend fun recoveryAnswerHash(): String? = dataStore.data.first()[RECOVERY_ANSWER]

    /** Sets the credential, turning the lock on if it was off. Failures start over. */
    suspend fun setCredential(method: LockMethod, pinLength: Int, hash: String) {
        dataStore.edit {
            it[METHOD] = method.name
            it[PIN_LENGTH] = pinLength
            it[CREDENTIAL] = hash
            it.clearFailures()
        }
    }

    /** Turns the lock off and forgets the credential, biometrics and recovery. Display options stay. */
    suspend fun disable() {
        dataStore.edit {
            it.remove(METHOD)
            it.remove(PIN_LENGTH)
            it.remove(CREDENTIAL)
            it.remove(BIOMETRICS)
            it.remove(BIOMETRICS_INVALIDATED)
            it.remove(RECOVERY)
            it.remove(RECOVERY_QUESTION)
            it.remove(RECOVERY_ANSWER)
            it.clearFailures()
        }
    }

    suspend fun setBiometrics(enabled: Boolean) {
        dataStore.edit {
            it[BIOMETRICS] = enabled
            it.remove(BIOMETRICS_INVALIDATED)
        }
    }

    /** Biometric unlock stopped working (a new fingerprint, a moved install): off, with a reason to show. */
    suspend fun invalidateBiometrics() {
        dataStore.edit {
            it[BIOMETRICS] = false
            it[BIOMETRICS_INVALIDATED] = true
        }
    }

    suspend fun clearBiometricsInvalidated() {
        dataStore.edit { it.remove(BIOMETRICS_INVALIDATED) }
    }

    suspend fun setBiometricStrength(strength: BiometricStrength) {
        dataStore.edit { it[BIOMETRIC_STRENGTH] = strength.name }
    }

    /** Sets the recovery method; [question] and [answerHash] only for [RecoveryMethod.Question]. */
    suspend fun setRecovery(method: RecoveryMethod, question: String? = null, answerHash: String? = null) {
        dataStore.edit {
            it[RECOVERY] = method.name
            if (method == RecoveryMethod.Question && question != null && answerHash != null) {
                it[RECOVERY_QUESTION] = question
                it[RECOVERY_ANSWER] = answerHash
            } else {
                it.remove(RECOVERY_QUESTION)
                it.remove(RECOVERY_ANSWER)
            }
        }
    }

    suspend fun setAutoLock(delay: AutoLockDelay) {
        dataStore.edit { it[AUTO_LOCK] = delay.name }
    }

    suspend fun setSecureScreen(secure: Boolean) {
        dataStore.edit { it[SECURE_SCREEN] = secure }
    }

    suspend fun setShuffleKeypad(shuffle: Boolean) {
        dataStore.edit { it[SHUFFLE_KEYPAD] = shuffle }
    }

    suspend fun recordFailure(at: Long): LockFailures {
        var result = LockFailures(0, at)
        dataStore.edit {
            val count = (it[FAILURES] ?: 0) + 1
            it[FAILURES] = count
            it[LAST_FAILURE_AT] = at
            result = LockFailures(count, at)
        }
        return result
    }

    suspend fun resetFailures() {
        dataStore.edit { it.clearFailures() }
    }

    private fun MutablePreferences.clearFailures() {
        remove(FAILURES)
        remove(LAST_FAILURE_AT)
    }

    private inline fun <reified T : Enum<T>> String?.toEnum(default: T): T =
        this?.let { name -> enumValues<T>().firstOrNull { it.name == name } } ?: default

    private companion object {
        val METHOD = stringPreferencesKey("method")
        val PIN_LENGTH = intPreferencesKey("pin_length")
        val CREDENTIAL = stringPreferencesKey("credential")
        val BIOMETRICS = booleanPreferencesKey("biometrics")
        val BIOMETRIC_STRENGTH = stringPreferencesKey("biometric_strength")
        val BIOMETRICS_INVALIDATED = booleanPreferencesKey("biometrics_invalidated")
        val RECOVERY = stringPreferencesKey("recovery")
        val RECOVERY_QUESTION = stringPreferencesKey("recovery_question")
        val RECOVERY_ANSWER = stringPreferencesKey("recovery_answer")
        val AUTO_LOCK = stringPreferencesKey("auto_lock")
        val SECURE_SCREEN = booleanPreferencesKey("secure_screen")
        val SHUFFLE_KEYPAD = booleanPreferencesKey("shuffle_keypad")
        val FAILURES = intPreferencesKey("failures")
        val LAST_FAILURE_AT = longPreferencesKey("last_failure_at")
    }
}
