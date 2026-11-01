package pl.dakil.dakilio.core.daylio

import java.io.File
import java.util.Base64
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

internal object Fixtures {
    const val PHOTO_CHECKSUM = "3b14e2dacf698a7ac4bbc336d9c27410"
    const val AUDIO_CHECKSUM = "7ff6cf360f5ab334dfd7a0690d5063f3"
    val PHOTO_BYTES = byteArrayOf(0xFF.toByte(), 0xD8.toByte(), 1, 2, 3)

    val json: String by lazy {
        requireNotNull(Fixtures::class.java.getResource("/fixture_backup.json")).readText()
    }

    val backup: DaylioBackup by lazy { DaylioBackup.parse(json) }

    /** MIME Base64 with 76-char lines, like Daylio writes it. */
    fun encode(text: String): ByteArray = Base64.getMimeEncoder().encode(text.toByteArray())

    /** Writes a `.daylio`-like ZIP; member names keep Daylio's leading slash. */
    fun writeArchive(dir: File, members: Map<String, ByteArray>): File {
        val file = File.createTempFile("backup", ".daylio", dir)
        ZipOutputStream(file.outputStream()).use { zip ->
            members.forEach { (name, bytes) ->
                zip.putNextEntry(ZipEntry(name))
                zip.write(bytes)
                zip.closeEntry()
            }
        }
        return file
    }

    fun sampleArchive(dir: File): File = writeArchive(
        dir,
        mapOf(
            "backup.daylio" to encode(json),
            "/assets/photos/2026/11/$PHOTO_CHECKSUM" to PHOTO_BYTES,
            "/assets/audio/2026/11/$AUDIO_CHECKSUM" to byteArrayOf(9, 9, 9),
        ),
    )
}
