package pl.dakil.dakilio.core.backup

import java.io.ByteArrayOutputStream
import java.io.Closeable
import java.io.File
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.util.zip.CRC32
import java.util.zip.ZipEntry
import java.util.zip.ZipException
import java.util.zip.ZipFile
import java.util.zip.ZipOutputStream
import kotlinx.serialization.SerializationException

/**
 * A plain `.dakilio` file opened for reading: a ZIP with `dakilio.json` and the photos under
 * `photos/`.
 *
 * The file is untrusted. Only the expected members are read, every read is capped, and member names
 * are only ever looked up, never used as output paths.
 */
class DakilioArchive(file: File) : Closeable {
    private val zip: ZipFile = try {
        ZipFile(file)
    } catch (e: ZipException) {
        throw BackupException(BackupError.NotABackup, e)
    } catch (e: IOException) {
        throw BackupException(BackupError.NotABackup, e)
    }

    val backup: DakilioBackup

    init {
        try {
            backup = readBackup()
        } catch (e: Throwable) {
            zip.close()
            throw e
        }
    }

    fun hasPhoto(path: String): Boolean = isPhotoPath(path) && zip.getEntry(path) != null

    /** The photo's bytes, or null when it's missing from the archive or too large. */
    fun readPhoto(path: String): ByteArray? {
        if (!isPhotoPath(path)) return null
        val entry = zip.getEntry(path) ?: return null
        return runCatching { zip.getInputStream(entry).use { it.readCapped(MAX_PHOTO_BYTES) } }.getOrNull()
    }

    override fun close() = zip.close()

    private fun readBackup(): DakilioBackup {
        val entry = zip.getEntry(DakilioBackup.JSON_MEMBER) ?: throw BackupException(BackupError.NotABackup)
        val bytes = try {
            zip.getInputStream(entry).use { it.readCapped(MAX_JSON_BYTES) }
        } catch (e: IOException) {
            throw BackupException(BackupError.Malformed, e)
        } ?: throw BackupException(BackupError.TooLarge)
        return decode(bytes.toString(Charsets.UTF_8))
    }

    companion object {
        const val MAX_JSON_BYTES = 50L * 1024 * 1024
        const val MAX_PHOTO_BYTES = 30L * 1024 * 1024

        /** Parses and checks `dakilio.json`. */
        fun decode(text: String): DakilioBackup {
            val backup = try {
                DakilioBackup.parse(text)
            } catch (e: SerializationException) {
                throw BackupException(BackupError.Malformed, e)
            } catch (e: IllegalArgumentException) {
                throw BackupException(BackupError.Malformed, e)
            }
            if (backup.format != DakilioBackup.FORMAT) throw BackupException(BackupError.NotABackup)
            if (backup.version > DakilioBackup.FORMAT_VERSION) throw BackupException(BackupError.NewerVersion)
            if (backup.version < 1) throw BackupException(BackupError.Malformed)
            return backup
        }

        internal fun isPhotoPath(path: String): Boolean =
            path.startsWith(DakilioBackup.PHOTO_DIR) && path.length > DakilioBackup.PHOTO_DIR.length &&
                path.split('/', '\\').none { it == ".." }

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

/**
 * Writes a plain `.dakilio` archive to [out] (which it closes): `dakilio.json` first, so the file
 * can be recognised from its first bytes, then every photo the entries list, stored as they are
 * (they're JPEGs already). [readPhoto] gets an archive path (`photos/…`) and must return its
 * bytes; [onPhoto] is called after each one.
 */
fun writeBackup(out: OutputStream, backup: DakilioBackup, readPhoto: (path: String) -> ByteArray, onPhoto: () -> Unit = {}) {
    ZipOutputStream(out).use { zip ->
        zip.putNextEntry(ZipEntry(DakilioBackup.JSON_MEMBER))
        zip.write(backup.encode().toByteArray(Charsets.UTF_8))
        zip.closeEntry()
        backup.entries.flatMap { it.photos }.distinct().forEach { path ->
            val bytes = readPhoto(path)
            val entry = ZipEntry(path).apply {
                method = ZipEntry.STORED
                size = bytes.size.toLong()
                compressedSize = bytes.size.toLong()
                crc = CRC32().apply { update(bytes) }.value
            }
            zip.putNextEntry(entry)
            zip.write(bytes)
            zip.closeEntry()
            onPhoto()
        }
    }
}
