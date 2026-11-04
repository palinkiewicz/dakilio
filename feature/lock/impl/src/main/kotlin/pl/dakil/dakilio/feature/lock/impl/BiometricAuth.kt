package pl.dakil.dakilio.feature.lock.impl

import android.app.KeyguardManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.hardware.biometrics.BiometricManager
import android.hardware.biometrics.BiometricManager.Authenticators
import android.hardware.biometrics.BiometricPrompt
import android.os.Build
import android.os.CancellationSignal
import android.provider.Settings
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyPermanentlyInvalidatedException
import android.security.keystore.KeyProperties
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.inject.Inject
import javax.inject.Singleton
import dagger.hilt.android.qualifiers.ApplicationContext
import pl.dakil.dakilio.core.data.repository.AppLock
import pl.dakil.dakilio.core.model.BiometricStrength

internal enum class BiometricAvailability { Available, NoneEnrolled, Unavailable }

/** Which sensor the device has, for labels and icons; [Generic] when it has several or says nothing. */
internal enum class BiometricKind { Fingerprint, Face, Iris, Generic }

internal sealed interface BiometricResult {
    data object Success : BiometricResult

    /** The user backed out or chose the PIN instead; nothing to report. */
    data object Cancelled : BiometricResult

    /** Strong unlock's key is gone: a new fingerprint or face was enrolled, or the app moved phones. */
    data object Invalidated : BiometricResult

    data class Error(val message: CharSequence) : BiometricResult
}

/**
 * Biometric and screen-lock prompts through the framework [BiometricPrompt] (API 28+), so the app
 * needs neither `androidx.biometric` nor a `FragmentActivity`.
 *
 * [BiometricStrength.Standard] allows Class 2 sensors without a key. [BiometricStrength.Strong] wraps
 * the prompt around a Keystore key that needs a Class 3 biometric for every use and is invalidated
 * when a new biometric is enrolled, so someone who adds their own fingerprint can't get in with it.
 */
@Singleton
internal class BiometricAuth @Inject constructor(
    @ApplicationContext private val context: Context,
    private val appLock: AppLock,
) {
    private val manager: BiometricManager? = context.getSystemService(BiometricManager::class.java)

    fun availability(strength: BiometricStrength): BiometricAvailability {
        val manager = manager ?: return BiometricAvailability.Unavailable
        val code = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            manager.canAuthenticate(authenticators(strength))
        } else {
            @Suppress("DEPRECATION")
            manager.canAuthenticate()
        }
        return when (code) {
            BiometricManager.BIOMETRIC_SUCCESS -> BiometricAvailability.Available
            BiometricManager.BIOMETRIC_ERROR_NONE_ENROLLED -> BiometricAvailability.NoneEnrolled
            else -> BiometricAvailability.Unavailable
        }
    }

    val kind: BiometricKind by lazy {
        val pm = context.packageManager
        val kinds = buildList {
            if (pm.hasSystemFeature(PackageManager.FEATURE_FINGERPRINT)) add(BiometricKind.Fingerprint)
            if (pm.hasSystemFeature(PackageManager.FEATURE_FACE)) add(BiometricKind.Face)
            if (pm.hasSystemFeature(PackageManager.FEATURE_IRIS)) add(BiometricKind.Iris)
        }
        kinds.singleOrNull() ?: BiometricKind.Generic
    }

    /** Whether the phone itself has a PIN, pattern or password, which screen-lock recovery needs. */
    val deviceSecure: Boolean
        get() = context.getSystemService(KeyguardManager::class.java)?.isDeviceSecure == true

    /** The system screen for adding a fingerprint or face. */
    fun enrollIntent(strength: BiometricStrength): Intent =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            Intent(Settings.ACTION_BIOMETRIC_ENROLL).putExtra(Settings.EXTRA_BIOMETRIC_AUTHENTICATORS_ALLOWED, authenticators(strength))
        } else {
            Intent(Settings.ACTION_SECURITY_SETTINGS)
        }

    /** Call before opening [enrollIntent]: a trip to the system settings shouldn't lock the app. */
    fun expectReturn() = appLock.expectReturnFromPicker()

    /** Creates a fresh key for strong unlock. Needs an enrolled Class 3 biometric; false if that failed. */
    fun prepareStrongKey(): Boolean = runCatching {
        deleteStrongKey()
        val builder = KeyGenParameterSpec.Builder(KEY_ALIAS, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
            .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
            .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
            .setUserAuthenticationRequired(true)
            .setInvalidatedByBiometricEnrollment(true)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            builder.setUserAuthenticationParameters(0, KeyProperties.AUTH_BIOMETRIC_STRONG)
        }
        KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, KEYSTORE).run {
            init(builder.build())
            generateKey()
        }
    }.isSuccess

    fun deleteStrongKey() {
        runCatching { keyStore().deleteEntry(KEY_ALIAS) }
    }

    /**
     * Shows the biometric prompt and reports once through [onResult] on the main thread. Returns a
     * function that cancels it. [context] should be the activity.
     */
    fun authenticate(
        context: Context,
        strength: BiometricStrength,
        title: CharSequence,
        subtitle: CharSequence?,
        negative: CharSequence,
        onResult: (BiometricResult) -> Unit,
    ): () -> Unit {
        val report = once(onResult)
        val crypto = if (strength == BiometricStrength.Strong) {
            when (val cipher = strongCipher()) {
                null -> {
                    report(BiometricResult.Invalidated)
                    return {}
                }
                else -> BiometricPrompt.CryptoObject(cipher)
            }
        } else {
            null
        }
        val cancel = CancellationSignal()
        val builder = BiometricPrompt.Builder(context)
            .setTitle(title)
            .setNegativeButton(negative, context.mainExecutor) { _, _ -> report(BiometricResult.Cancelled) }
            .setConfirmationRequired(false)
        subtitle?.let(builder::setSubtitle)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) builder.setAllowedAuthenticators(authenticators(strength))
        val callback = object : BiometricPrompt.AuthenticationCallback() {
            override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                // Using the key proves the prompt really unlocked it, not just that a callback ran.
                val ok = crypto == null || runCatching { result.cryptoObject?.cipher?.doFinal(ByteArray(16)) != null }.getOrDefault(false)
                report(if (ok) BiometricResult.Success else BiometricResult.Invalidated)
            }

            override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                report(
                    when (errorCode) {
                        BiometricPrompt.BIOMETRIC_ERROR_CANCELED,
                        BiometricPrompt.BIOMETRIC_ERROR_USER_CANCELED,
                        -> BiometricResult.Cancelled
                        else -> BiometricResult.Error(errString)
                    },
                )
            }
        }
        appLock.expectReturnFromPicker()
        if (crypto != null) {
            builder.build().authenticate(crypto, cancel, context.mainExecutor, callback)
        } else {
            builder.build().authenticate(cancel, context.mainExecutor, callback)
        }
        return { cancel.cancel() }
    }

    /** Asks for the phone's own PIN, pattern or password (screen-lock recovery). */
    fun authenticateDeviceCredential(
        context: Context,
        title: CharSequence,
        subtitle: CharSequence?,
        onResult: (BiometricResult) -> Unit,
    ): () -> Unit {
        val cancel = CancellationSignal()
        val builder = BiometricPrompt.Builder(context).setTitle(title)
        subtitle?.let(builder::setSubtitle)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            builder.setAllowedAuthenticators(Authenticators.DEVICE_CREDENTIAL)
        } else {
            @Suppress("DEPRECATION")
            builder.setDeviceCredentialAllowed(true)
        }
        builder.build().authenticate(
            cancel,
            context.mainExecutor,
            object : BiometricPrompt.AuthenticationCallback() {
                override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) = onResult(BiometricResult.Success)

                override fun onAuthenticationError(errorCode: Int, errString: CharSequence) = onResult(
                    if (errorCode == BiometricPrompt.BIOMETRIC_ERROR_USER_CANCELED || errorCode == BiometricPrompt.BIOMETRIC_ERROR_CANCELED) {
                        BiometricResult.Cancelled
                    } else {
                        BiometricResult.Error(errString)
                    },
                )
            },
        )
        return { cancel.cancel() }
    }

    /** The negative button and a cancellation can both arrive; callers hear only the first. */
    private fun once(onResult: (BiometricResult) -> Unit): (BiometricResult) -> Unit {
        var done = false
        return { result ->
            if (!done) {
                done = true
                onResult(result)
            }
        }
    }

    /** A cipher on the strong key, or `null` if the key is missing or was invalidated. */
    private fun strongCipher(): Cipher? = try {
        val key = keyStore().getKey(KEY_ALIAS, null) as? SecretKey ?: return null
        Cipher.getInstance("AES/GCM/NoPadding").apply { init(Cipher.ENCRYPT_MODE, key) }
    } catch (_: KeyPermanentlyInvalidatedException) {
        null
    } catch (_: Exception) {
        null
    }

    private fun keyStore(): KeyStore = KeyStore.getInstance(KEYSTORE).apply { load(null) }

    private fun authenticators(strength: BiometricStrength): Int = when (strength) {
        BiometricStrength.Standard -> Authenticators.BIOMETRIC_WEAK
        BiometricStrength.Strong -> Authenticators.BIOMETRIC_STRONG
    }

    private companion object {
        const val KEYSTORE = "AndroidKeyStore"
        const val KEY_ALIAS = "dakilio_biometric_unlock"
    }
}
