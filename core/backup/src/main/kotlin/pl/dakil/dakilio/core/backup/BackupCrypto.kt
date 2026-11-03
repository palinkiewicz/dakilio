package pl.dakil.dakilio.core.backup

import java.io.ByteArrayOutputStream
import java.io.EOFException
import java.io.InputStream
import java.io.OutputStream
import java.nio.ByteBuffer
import java.security.SecureRandom
import java.util.Base64
import javax.crypto.AEADBadTagException
import javax.crypto.Cipher
import javax.crypto.SecretKey
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec
import kotlinx.serialization.Serializable
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json

/**
 * The header line of an encrypted backup, right after [BackupCrypto.MAGIC]. It holds everything
 * needed to derive the key and read the chunks, and it's readable, so the file says what it is.
 */
@Serializable
data class EncryptionHeader(
    val version: Int,
    val kdf: String,
    val iterations: Int,
    val salt: String,
    val cipher: String,
    val chunkSize: Int,
    val noncePrefix: String,
)

/**
 * Password encryption for backups: a key from PBKDF2-HMAC-SHA256, then the plain backup in
 * AES-256-GCM chunks (the STREAM construction, as in Tink and age).
 *
 * The file is `MAGIC`, one line of [EncryptionHeader] JSON, then the chunks. Each chunk is up to
 * `chunkSize` bytes of plaintext sealed with its own 16-byte tag. Its nonce is the header's 7-byte
 * prefix, a 4-byte big-endian counter and a final byte that is 1 only for the last chunk. Every
 * chunk authenticates the whole header too. So a changed header, reordered or dropped chunks and a
 * file cut short after any chunk all fail, and nothing has to be held in memory but one chunk.
 */
object BackupCrypto {
    const val MAGIC = "DAKILIO-ENCRYPTED\n"
    const val HEADER_VERSION = 1
    const val KDF = "PBKDF2-HMAC-SHA256"
    const val CIPHER = "AES-256-GCM-STREAM"
    const val DEFAULT_ITERATIONS = 600_000
    const val DEFAULT_CHUNK_SIZE = 64 * 1024

    internal const val SALT_BYTES = 16
    internal const val PREFIX_BYTES = 7
    internal const val TAG_BYTES = 16
    private const val NONCE_BYTES = 12
    private const val KEY_BITS = 256
    private const val MAX_HEADER_BYTES = 4096
    private val ITERATION_RANGE = 1_000..10_000_000
    private val CHUNK_RANGE = 1024..(16 * 1024 * 1024)

    private val headerJson = Json { ignoreUnknownKeys = true }

    /** Derives the AES key. The [password] array isn't changed; clearing it is the caller's job. */
    internal fun deriveKey(password: CharArray, salt: ByteArray, iterations: Int): SecretKey {
        val spec = PBEKeySpec(password, salt, iterations, KEY_BITS)
        try {
            val bytes = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(spec).encoded
            return SecretKeySpec(bytes, "AES").also { bytes.fill(0) }
        } finally {
            spec.clearPassword()
        }
    }

    internal fun nonce(prefix: ByteArray, counter: Int, last: Boolean): ByteArray =
        ByteBuffer.allocate(NONCE_BYTES).put(prefix).putInt(counter).put(if (last) 1 else 0).array()

    internal fun headerBytes(header: EncryptionHeader): ByteArray =
        (MAGIC + headerJson.encodeToString(EncryptionHeader.serializer(), header) + "\n").toByteArray(Charsets.UTF_8)

    /**
     * Decrypts an encrypted backup from [input] into [output]. Throws a [BackupException]:
     * [BackupError.WrongPassword] when the password doesn't fit, [BackupError.Corrupted] when it
     * does but the file is damaged or incomplete, [BackupError.NotABackup] for a foreign file.
     */
    fun decrypt(input: InputStream, output: OutputStream, password: CharArray) {
        val (header, aad) = readHeader(input)
        val key = deriveKey(password, header.saltBytes(), header.iterations)
        val prefix = header.prefixBytes()
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        val block = header.chunkSize + TAG_BYTES
        var current = readBlock(input, block)
        if (current.isEmpty()) throw BackupException(BackupError.Corrupted)
        var counter = 0
        while (true) {
            // A full block may or may not be the last one; only the next read tells.
            val next = if (current.size == block) readBlock(input, block) else ByteArray(0)
            val last = next.isEmpty()
            val plain = try {
                open(cipher, key, prefix, counter, last, aad, current)
            } catch (e: AEADBadTagException) {
                // The first chunk tells a wrong password from a damaged file: with the right key it
                // still opens under the other "last" flag (which means chunks are missing or extra).
                val rightKey = counter == 0 && runCatching { open(cipher, key, prefix, 0, !last, aad, current) }.isSuccess
                throw BackupException(if (counter == 0 && !rightKey) BackupError.WrongPassword else BackupError.Corrupted, e)
            }
            output.write(plain)
            if (last) return
            if (counter == Int.MAX_VALUE) throw BackupException(BackupError.Corrupted)
            counter++
            current = next
        }
    }

    /** Reads and checks the magic and the header line; returns the header and its exact bytes. */
    fun readHeader(input: InputStream): Pair<EncryptionHeader, ByteArray> {
        val magic = MAGIC.toByteArray(Charsets.US_ASCII)
        val start = readBlock(input, magic.size)
        if (!start.contentEquals(magic)) throw BackupException(BackupError.NotABackup)
        val line = ByteArrayOutputStream()
        while (true) {
            val b = input.read()
            if (b < 0) throw BackupException(BackupError.Corrupted)
            if (b == '\n'.code) break
            if (line.size() >= MAX_HEADER_BYTES) throw BackupException(BackupError.Corrupted)
            line.write(b)
        }
        val header = try {
            headerJson.decodeFromString(EncryptionHeader.serializer(), line.toString(Charsets.UTF_8.name()))
        } catch (e: SerializationException) {
            throw BackupException(BackupError.Corrupted, e)
        } catch (e: IllegalArgumentException) {
            throw BackupException(BackupError.Corrupted, e)
        }
        if (header.version > HEADER_VERSION) throw BackupException(BackupError.NewerVersion)
        val valid = header.version == HEADER_VERSION && header.kdf == KDF && header.cipher == CIPHER &&
            header.iterations in ITERATION_RANGE && header.chunkSize in CHUNK_RANGE &&
            runCatching { header.saltBytes().size == SALT_BYTES && header.prefixBytes().size == PREFIX_BYTES }.getOrDefault(false)
        if (!valid) throw BackupException(BackupError.Corrupted)
        return header to (start + line.toByteArray() + '\n'.code.toByte())
    }

    private fun open(cipher: Cipher, key: SecretKey, prefix: ByteArray, counter: Int, last: Boolean, aad: ByteArray, block: ByteArray): ByteArray {
        cipher.init(Cipher.DECRYPT_MODE, key, GCMParameterSpec(TAG_BYTES * 8, nonce(prefix, counter, last)))
        cipher.updateAAD(aad)
        return cipher.doFinal(block)
    }

    private fun EncryptionHeader.saltBytes(): ByteArray = Base64.getDecoder().decode(salt)

    private fun EncryptionHeader.prefixBytes(): ByteArray = Base64.getDecoder().decode(noncePrefix)

    /** Up to [size] bytes; fewer only at the end of the stream. */
    private fun readBlock(input: InputStream, size: Int): ByteArray {
        val buffer = ByteArray(size)
        var filled = 0
        while (filled < size) {
            val read = input.read(buffer, filled, size - filled)
            if (read < 0) break
            filled += read
        }
        return if (filled == size) buffer else buffer.copyOf(filled)
    }
}

/**
 * Encrypts everything written to it into [out] (see [BackupCrypto]). The header is written right
 * away; [close] seals the last chunk, so a stream that is never closed leaves an unreadable file.
 */
class EncryptingOutputStream(
    private val out: OutputStream,
    password: CharArray,
    iterations: Int = BackupCrypto.DEFAULT_ITERATIONS,
    private val chunkSize: Int = BackupCrypto.DEFAULT_CHUNK_SIZE,
    random: SecureRandom = SecureRandom(),
) : OutputStream() {
    private val key: SecretKey
    private val prefix = ByteArray(BackupCrypto.PREFIX_BYTES).also(random::nextBytes)
    private val aad: ByteArray
    private val cipher = Cipher.getInstance("AES/GCM/NoPadding")
    private val buffer = ByteArray(chunkSize)
    private var filled = 0
    private var counter = 0
    private var closed = false

    init {
        val salt = ByteArray(BackupCrypto.SALT_BYTES).also(random::nextBytes)
        key = BackupCrypto.deriveKey(password, salt, iterations)
        val encoder = Base64.getEncoder()
        aad = BackupCrypto.headerBytes(
            EncryptionHeader(
                version = BackupCrypto.HEADER_VERSION,
                kdf = BackupCrypto.KDF,
                iterations = iterations,
                salt = encoder.encodeToString(salt),
                cipher = BackupCrypto.CIPHER,
                chunkSize = chunkSize,
                noncePrefix = encoder.encodeToString(prefix),
            ),
        )
        out.write(aad)
    }

    override fun write(b: Int) {
        write(byteArrayOf(b.toByte()), 0, 1)
    }

    override fun write(b: ByteArray, off: Int, len: Int) {
        check(!closed) { "closed" }
        var offset = off
        var remaining = len
        while (remaining > 0) {
            // A full buffer is only sealed once more data arrives: it might be the last chunk.
            if (filled == chunkSize) seal(last = false)
            val n = minOf(remaining, chunkSize - filled)
            System.arraycopy(b, offset, buffer, filled, n)
            filled += n
            offset += n
            remaining -= n
        }
    }

    override fun flush() = out.flush()

    override fun close() {
        if (closed) return
        try {
            seal(last = true)
        } finally {
            closed = true
            buffer.fill(0)
            out.close()
        }
    }

    private fun seal(last: Boolean) {
        if (counter == Int.MAX_VALUE) throw EOFException("Backup too large to encrypt")
        cipher.init(Cipher.ENCRYPT_MODE, key, GCMParameterSpec(BackupCrypto.TAG_BYTES * 8, BackupCrypto.nonce(prefix, counter, last)))
        cipher.updateAAD(aad)
        out.write(cipher.doFinal(buffer, 0, filled))
        counter++
        filled = 0
    }
}
