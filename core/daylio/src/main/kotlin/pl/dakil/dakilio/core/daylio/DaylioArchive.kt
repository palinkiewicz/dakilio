package pl.dakil.dakilio.core.daylio

import java.io.ByteArrayOutputStream
import java.io.Closeable
import java.io.File
import java.io.IOException
import java.io.InputStream
import java.util.Base64
import java.util.zip.ZipEntry
import java.util.zip.ZipException
import java.util.zip.ZipFile
import kotlinx.serialization.SerializationException

/** Why a file can't be imported. */
enum class DaylioError { NotAZip, MissingBackup, TooLarge, Malformed, UnsupportedVersion }

class DaylioImportException(val reason: DaylioError, cause: Throwable? = null) : Exception(reason.name, cause)

/**
 * A `.daylio` file opened for reading: a ZIP holding `backup.daylio` (MIME Base64 of the JSON) and
 * the media under `/assets/...`, named by their checksum.
 *
 * The file is untrusted. Only the expected members are read, every read is capped, and member names
 * are only ever looked up, never used as output paths.
 */
class DaylioArchive(file: File) : Closeable {
    private val zip: ZipFile = try {
        ZipFile(file)
    } catch (e: ZipException) {
        throw DaylioImportException(DaylioError.NotAZip, e)
    } catch (e: IOException) {
        throw DaylioImportException(DaylioError.NotAZip, e)
    }

    val backup: DaylioBackup

    /** Photo members by file name (= the asset's checksum). */
    private val photos: Map<String, ZipEntry>

    init {
        try {
            backup = readBackup()
            photos = zip.entries().asSequence()
                .filter { !it.isDirectory && it.name.trimStart('/').startsWith(PHOTO_DIR) }
                .associateBy { it.name.substringAfterLast('/') }
        } catch (e: Throwable) {
            zip.close()
            throw e
        }
    }

    /** True for a backup version the importer hasn't been checked against. */
    val isUntestedVersion: Boolean get() = backup.version != DaylioBackup.KNOWN_VERSION

    fun hasPhoto(checksum: String): Boolean = checksum in photos

    /** The photo's bytes, or null when it's missing from the archive or too large. */
    fun readPhoto(checksum: String): ByteArray? {
        val entry = photos[checksum] ?: return null
        return runCatching { zip.getInputStream(entry).use { it.readCapped(MAX_PHOTO_BYTES) } }.getOrNull()
    }

    override fun close() = zip.close()

    private fun readBackup(): DaylioBackup {
        val entry = zip.getEntry(BACKUP_MEMBER) ?: zip.getEntry("/$BACKUP_MEMBER")
            ?: throw DaylioImportException(DaylioError.MissingBackup)
        val encoded = try {
            zip.getInputStream(entry).use { it.readCapped(MAX_BACKUP_BYTES) }
        } catch (e: IOException) {
            throw DaylioImportException(DaylioError.Malformed, e)
        } ?: throw DaylioImportException(DaylioError.TooLarge)
        return decodeBackup(encoded)
    }

    companion object {
        private const val BACKUP_MEMBER = "backup.daylio"
        private const val PHOTO_DIR = "assets/photos/"
        const val MAX_BACKUP_BYTES = 50L * 1024 * 1024
        const val MAX_PHOTO_BYTES = 30L * 1024 * 1024

        /** Base64 (MIME, with line breaks) → UTF-8 JSON → model. */
        internal fun decodeBackup(encoded: ByteArray): DaylioBackup {
            val json = try {
                Base64.getMimeDecoder().decode(encoded)
            } catch (e: IllegalArgumentException) {
                throw DaylioImportException(DaylioError.Malformed, e)
            }
            val backup = try {
                DaylioBackup.parse(json.toString(Charsets.UTF_8))
            } catch (e: SerializationException) {
                throw DaylioImportException(DaylioError.Malformed, e)
            } catch (e: IllegalArgumentException) {
                throw DaylioImportException(DaylioError.Malformed, e)
            }
            if (backup.version <= 0) throw DaylioImportException(DaylioError.UnsupportedVersion)
            return backup
        }

        /** Reads the whole stream, or returns null once it exceeds [max] bytes. */
        internal fun InputStream.readCapped(max: Long): ByteArray? {
            val out = ByteArrayOutputStream()
            val buffer = ByteArray(64 * 1024)
            var total = 0L
            while (true) {
                val read = read(buffer)
                if (read < 0) break
                total += read
                if (total > max) return null
                out.write(buffer, 0, read)
            }
            return out.toByteArray()
        }
    }
}
