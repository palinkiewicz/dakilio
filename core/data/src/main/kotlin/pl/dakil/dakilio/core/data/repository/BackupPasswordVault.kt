package pl.dakil.dakilio.core.data.repository

import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Log
import java.nio.ByteBuffer
import java.nio.CharBuffer
import java.security.GeneralSecurityException
import java.security.KeyStore
import java.util.Base64
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec
import javax.inject.Inject
import javax.inject.Singleton
import pl.dakil.dakilio.core.datastore.SealedPassword

/**
 * Keeps the automatic backups' password, encrypted with an Android Keystore key, so the background
 * job can use it without asking.
 *
 * The key needs no user authentication and no unlocked device, since backups run while the phone
 * charges, usually locked. It never leaves this device: on a new phone (or after the key is lost)
 * [open] returns null and the user enters the password again.
 */
@Singleton
internal class BackupPasswordVault @Inject constructor() {
    fun seal(password: CharArray): SealedPassword {
        val bytes = password.toUtf8()
        try {
            val cipher = Cipher.getInstance(TRANSFORMATION)
            cipher.init(Cipher.ENCRYPT_MODE, key())
            val encoder = Base64.getEncoder()
            return SealedPassword(encoder.encodeToString(cipher.doFinal(bytes)), encoder.encodeToString(cipher.iv))
        } finally {
            bytes.fill(0)
        }
    }

    /** The password, or null when it can't be read any more. The caller clears the array. */
    fun open(sealed: SealedPassword): CharArray? {
        val key = existingKey() ?: return null
        return try {
            val decoder = Base64.getDecoder()
            val cipher = Cipher.getInstance(TRANSFORMATION)
            cipher.init(Cipher.DECRYPT_MODE, key, GCMParameterSpec(TAG_BITS, decoder.decode(sealed.iv)))
            val bytes = cipher.doFinal(decoder.decode(sealed.ciphertext))
            try {
                bytes.toChars()
            } finally {
                bytes.fill(0)
            }
        } catch (e: GeneralSecurityException) {
            Log.w(TAG, "Can't open the stored password", e)
            null
        } catch (e: IllegalArgumentException) {
            Log.w(TAG, "Can't open the stored password", e)
            null
        }
    }

    fun forget() {
        runCatching { keyStore().deleteEntry(KEY_ALIAS) }
    }

    private fun keyStore(): KeyStore = KeyStore.getInstance(KEYSTORE).apply { load(null) }

    private fun existingKey(): SecretKey? = runCatching { keyStore().getKey(KEY_ALIAS, null) as? SecretKey }.getOrNull()

    private fun key(): SecretKey = existingKey() ?: KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, KEYSTORE).run {
        init(
            KeyGenParameterSpec.Builder(KEY_ALIAS, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(256)
                .build(),
        )
        generateKey()
    }

    private fun CharArray.toUtf8(): ByteArray {
        val buffer = Charsets.UTF_8.encode(CharBuffer.wrap(this))
        val bytes = ByteArray(buffer.remaining()).also { buffer.get(it) }
        if (buffer.hasArray()) buffer.array().fill(0)
        return bytes
    }

    private fun ByteArray.toChars(): CharArray {
        val buffer = Charsets.UTF_8.decode(ByteBuffer.wrap(this))
        val chars = CharArray(buffer.remaining()).also { buffer.get(it) }
        if (buffer.hasArray()) buffer.array().fill('\u0000')
        return chars
    }

    private companion object {
        const val TAG = "DakilioAutoBackup"
        const val KEYSTORE = "AndroidKeyStore"
        const val KEY_ALIAS = "dakilio_autobackup"
        const val TRANSFORMATION = "AES/GCM/NoPadding"
        const val TAG_BITS = 128
    }
}
