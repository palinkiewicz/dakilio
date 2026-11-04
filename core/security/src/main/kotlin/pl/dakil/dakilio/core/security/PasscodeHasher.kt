package pl.dakil.dakilio.core.security

import java.security.MessageDigest
import java.security.SecureRandom
import java.util.Base64
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.PBEKeySpec

/**
 * A salted PBKDF2 hash of a PIN, password or recovery answer. The iteration count is stored with it,
 * so a later version can raise it without invalidating what's saved.
 */
class PasscodeHash(val salt: ByteArray, val iterations: Int, val hash: ByteArray) {
    /** `salt:iterations:hash`, salt and hash in Base64, for storing as one string. */
    fun encode(): String {
        val b64 = Base64.getEncoder()
        return "${b64.encodeToString(salt)}:$iterations:${b64.encodeToString(hash)}"
    }

    companion object {
        /** Reads [encode]'s output; `null` for anything malformed. */
        fun decode(value: String): PasscodeHash? {
            val parts = value.split(':')
            if (parts.size != 3) return null
            return runCatching {
                val b64 = Base64.getDecoder()
                PasscodeHash(b64.decode(parts[0]), parts[1].toInt(), b64.decode(parts[2]))
            }.getOrNull()?.takeIf { it.salt.isNotEmpty() && it.hash.isNotEmpty() && it.iterations > 0 }
        }
    }
}

/**
 * Hashes and checks passcodes with PBKDF2-HMAC-SHA256, using only JDK crypto.
 *
 * This guards the app's UI, not the data: the database itself isn't encrypted, so the hash only keeps
 * the PIN or password itself from being read back (people reuse them). Every unlock pays the cost,
 * and Android's PBKDF2 runs in Java, so it's set to a few tenths of a second on a phone rather than
 * the far higher counts meant for server-side password storage.
 */
class PasscodeHasher(
    private val iterations: Int = DEFAULT_ITERATIONS,
    private val random: SecureRandom = SecureRandom(),
) {
    fun hash(secret: CharArray): PasscodeHash {
        val salt = ByteArray(SALT_BYTES).also(random::nextBytes)
        return PasscodeHash(salt, iterations, derive(secret, salt, iterations))
    }

    /** Whether [secret] matches [stored], compared in constant time. */
    fun verify(secret: CharArray, stored: PasscodeHash): Boolean =
        MessageDigest.isEqual(derive(secret, stored.salt, stored.iterations), stored.hash)

    private fun derive(secret: CharArray, salt: ByteArray, iterations: Int): ByteArray {
        val spec = PBEKeySpec(secret, salt, iterations, HASH_BITS)
        try {
            return SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(spec).encoded
        } finally {
            spec.clearPassword()
        }
    }

    companion object {
        const val DEFAULT_ITERATIONS = 50_000
        private const val SALT_BYTES = 16
        private const val HASH_BITS = 256
    }
}
