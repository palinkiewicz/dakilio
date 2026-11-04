package pl.dakil.dakilio.core.model

/** What unlocks the app. [None] means the app lock is off. */
enum class LockMethod { None, Pin, Password }

/**
 * Which biometrics may unlock the app. [Standard] accepts Class 2 sensors too (camera-only face
 * unlock on many phones); [Strong] only Class 3, backed by a Keystore key that a newly enrolled
 * fingerprint or face invalidates.
 */
enum class BiometricStrength { Standard, Strong }

/** How a forgotten PIN or password can be got past. Exactly one is active. */
enum class RecoveryMethod { None, DeviceCredential, Question }

/** How long the app may stay in the background before it locks again. */
enum class AutoLockDelay(val millis: Long) {
    Immediately(0),
    After30s(30_000),
    After1m(60_000),
    After5m(5 * 60_000),
    After15m(15 * 60_000),
}

/**
 * The app lock's settings. The credential itself (a salted hash) never leaves the data layer.
 *
 * @property pinLength digits of the PIN, so the lock screen can show its dots and check a PIN as
 *   soon as it's complete; 0 for a password or no lock.
 * @property biometrics biometrics may unlock too. Only meaningful while [method] isn't [LockMethod.None].
 * @property biometricsInvalidated biometric unlock was switched off because it stopped working
 *   (a newly enrolled fingerprint or face, or a move to another phone); the lock screen says why.
 * @property secureScreen `FLAG_SECURE`: blank in recent apps, no screenshots. Works with the lock off too.
 */
data class LockSettings(
    val method: LockMethod = LockMethod.None,
    val pinLength: Int = 0,
    val biometrics: Boolean = false,
    val biometricStrength: BiometricStrength = BiometricStrength.Standard,
    val biometricsInvalidated: Boolean = false,
    val recovery: RecoveryMethod = RecoveryMethod.None,
    val recoveryQuestion: String? = null,
    val autoLock: AutoLockDelay = AutoLockDelay.Immediately,
    val secureScreen: Boolean = false,
    val shuffleKeypad: Boolean = false,
) {
    val enabled: Boolean get() = method != LockMethod.None
}
