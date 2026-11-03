package pl.dakil.dakilio.core.backup

import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.security.SecureRandom
import kotlin.random.Random
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

class BackupCryptoTest {
    private val password = "zażółć gęślą jaźń".toCharArray()
    private val chunk = 1024

    private fun encrypt(plain: ByteArray, pass: CharArray = password): ByteArray {
        val out = ByteArrayOutputStream()
        // Few iterations keep the tests fast; the format stores the count.
        EncryptingOutputStream(out, pass, iterations = 1_000, chunkSize = chunk, random = SecureRandom()).use { it.write(plain) }
        return out.toByteArray()
    }

    private fun decrypt(file: ByteArray, pass: CharArray = password): ByteArray {
        val out = ByteArrayOutputStream()
        BackupCrypto.decrypt(ByteArrayInputStream(file), out, pass)
        return out.toByteArray()
    }

    private fun assertFails(reason: BackupError, block: () -> Unit) {
        try {
            block()
            fail("Expected $reason")
        } catch (e: BackupException) {
            assertEquals(reason, e.reason)
        }
    }

    private fun headerLength(file: ByteArray): Int = file.indexOfFirst { it == '\n'.code.toByte() }
        .let { magicEnd -> file.drop(magicEnd + 1).indexOfFirst { it == '\n'.code.toByte() } + magicEnd + 2 }

    @Test
    fun roundTripsAllSizes() {
        listOf(0, 1, chunk - 1, chunk, chunk + 1, 3 * chunk, 5 * chunk + 17).forEach { size ->
            val plain = Random(size).nextBytes(size)
            assertArrayEquals("size $size", plain, decrypt(encrypt(plain)))
        }
    }

    @Test
    fun byteByByteWritesMatch() {
        val plain = Random(1).nextBytes(2 * chunk + 5)
        val out = ByteArrayOutputStream()
        EncryptingOutputStream(out, password, iterations = 1_000, chunkSize = chunk).use { stream -> plain.forEach { stream.write(it.toInt()) } }
        assertArrayEquals(plain, decrypt(out.toByteArray()))
    }

    @Test
    fun headerIsReadable() {
        val file = encrypt(ByteArray(10))
        val text = file.copyOf(headerLength(file)).toString(Charsets.UTF_8)
        assertTrue(text.startsWith(BackupCrypto.MAGIC + "{"))
        assertTrue(text.contains("\"kdf\":\"PBKDF2-HMAC-SHA256\""))
        assertEquals(BackupKind.DakilioEncrypted, detectBackupKind(file.copyOf(BACKUP_HEAD_BYTES)))
    }

    @Test
    fun wrongPassword() {
        val file = encrypt(Random(2).nextBytes(3 * chunk))
        assertFails(BackupError.WrongPassword) { decrypt(file, "nope".toCharArray()) }
        val small = encrypt(ByteArray(5))
        assertFails(BackupError.WrongPassword) { decrypt(small, "".toCharArray()) }
    }

    @Test
    fun droppedLastChunkIsCorrupted() {
        val file = encrypt(Random(3).nextBytes(3 * chunk + 10))
        val block = chunk + BackupCrypto.TAG_BYTES
        val cut = file.copyOf(file.size - (10 + BackupCrypto.TAG_BYTES))
        assertEquals(headerLength(file) + 3 * block, cut.size)
        assertFails(BackupError.Corrupted) { decrypt(cut) }
    }

    @Test
    fun truncatedSingleChunkFileIsCorrupted() {
        // Only the first (non-last) chunk survives: the right key still opens it as "not last".
        val file = encrypt(Random(4).nextBytes(2 * chunk))
        val cut = file.copyOf(headerLength(file) + chunk + BackupCrypto.TAG_BYTES)
        assertFails(BackupError.Corrupted) { decrypt(cut) }
    }

    @Test
    fun cutMidChunkIsCorrupted() {
        val file = encrypt(Random(5).nextBytes(3 * chunk))
        assertFails(BackupError.Corrupted) { decrypt(file.copyOf(file.size - 100)) }
    }

    @Test
    fun swappedChunksAreCorrupted() {
        val file = encrypt(Random(6).nextBytes(4 * chunk))
        val start = headerLength(file)
        val block = chunk + BackupCrypto.TAG_BYTES
        val swapped = file.copyOf()
        System.arraycopy(file, start + block, swapped, start + 2 * block, block)
        System.arraycopy(file, start + 2 * block, swapped, start + block, block)
        assertFails(BackupError.Corrupted) { decrypt(swapped) }
    }

    @Test
    fun changedHeaderFails() {
        val file = encrypt(Random(7).nextBytes(100))
        val text = file.copyOf(headerLength(file)).toString(Charsets.UTF_8)
        val tampered = text.replace("\"chunkSize\":1024", "\"chunkSize\":1025").toByteArray() + file.copyOfRange(headerLength(file), file.size)
        try {
            decrypt(tampered)
            fail()
        } catch (e: BackupException) {
            assertTrue(e.reason == BackupError.Corrupted || e.reason == BackupError.WrongPassword)
        }
        // A flipped bit in a later chunk. (In the first one it's indistinguishable from a wrong password.)
        val long = encrypt(Random(8).nextBytes(3 * chunk))
        val flipped = long.copyOf().also { it[it.size - 1] = (it[it.size - 1].toInt() xor 1).toByte() }
        assertFails(BackupError.Corrupted) { decrypt(flipped) }
    }

    @Test
    fun foreignFiles() {
        assertFails(BackupError.NotABackup) { decrypt("PK\u0003\u0004 not encrypted".toByteArray()) }
        assertFails(BackupError.Corrupted) { decrypt((BackupCrypto.MAGIC + "{oops}\n").toByteArray()) }
        assertFails(BackupError.Corrupted) { decrypt((BackupCrypto.MAGIC + "no newline").toByteArray()) }
        val future = BackupCrypto.MAGIC + """{"version":2,"kdf":"x","iterations":1,"salt":"","cipher":"y","chunkSize":1,"noncePrefix":""}""" + "\n"
        assertFails(BackupError.NewerVersion) { decrypt(future.toByteArray()) }
        // A valid header without any chunk.
        val file = encrypt(ByteArray(0))
        assertFails(BackupError.Corrupted) { decrypt(file.copyOf(headerLength(file))) }
    }
}
