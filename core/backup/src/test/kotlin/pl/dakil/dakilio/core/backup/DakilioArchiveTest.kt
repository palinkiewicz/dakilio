package pl.dakil.dakilio.core.backup

import java.io.File
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class DakilioArchiveTest {
    @get:Rule val tmp = TemporaryFolder()

    private val photoA = ByteArray(5000) { (it % 251).toByte() }
    private val photoB = byteArrayOf(1, 2, 3)

    private val backup = DakilioBackup(
        format = DakilioBackup.FORMAT,
        version = DakilioBackup.FORMAT_VERSION,
        exportedAt = "2026-11-03T14:20:00+01:00",
        moods = listOf(BackupMood(id = 3, level = 3, name = "Okay", primary = true)),
        diaries = listOf(BackupDiary(id = 1, name = "Journey", icon = "explore")),
        entries = listOf(
            BackupEntry(time = "2026-11-02T21:05+01:00", diary = 1, mood = 3, photos = listOf("photos/a.jpg", "photos/b.jpg")),
            BackupEntry(time = "2026-11-01T08:00Z", diary = 1, photos = listOf("photos/a.jpg")),
        ),
    )

    private fun write(): File {
        val file = tmp.newFile("backup.dakilio")
        val photos = mapOf("photos/a.jpg" to photoA, "photos/b.jpg" to photoB)
        var written = 0
        writeBackup(file.outputStream(), backup, { photos.getValue(it) }) { written++ }
        assertEquals(2, written)
        return file
    }

    @Test
    fun roundTrip() {
        val file = write()
        assertEquals(BackupKind.DakilioPlain, detectBackupKind(file.readBytes().copyOf(BACKUP_HEAD_BYTES)))
        DakilioArchive(file).use { archive ->
            assertEquals(backup, archive.backup)
            assertTrue(archive.hasPhoto("photos/a.jpg"))
            assertArrayEquals(photoA, archive.readPhoto("photos/a.jpg"))
            assertArrayEquals(photoB, archive.readPhoto("photos/b.jpg"))
            assertNull(archive.readPhoto("photos/missing.jpg"))
            assertFalse(archive.hasPhoto("dakilio.json"))
            assertFalse(archive.hasPhoto("photos/../dakilio.json"))
        }
    }

    @Test
    fun jsonIsReadableAndSkipsDefaults() {
        val json = backup.encode()
        assertTrue(json.contains("\"format\": \"dakilio\""))
        assertTrue(json.contains("\"version\": 1"))
        assertTrue(json.contains("\n"))
        assertFalse(json.contains("archived"))
        assertFalse(json.contains("null"))
    }

    @Test
    fun unknownKeysAreIgnored() {
        val parsed = DakilioArchive.decode(
            """{"format":"dakilio","version":1,"future":{"x":1},"diaries":[{"id":1,"name":"D","sparkle":true}]}""",
        )
        assertEquals("D", parsed.diaries.single().name)
    }

    @Test
    fun rejectsBadJson() {
        fun assertReason(reason: BackupError, json: String) = try {
            DakilioArchive.decode(json)
            fail("Expected $reason")
        } catch (e: BackupException) {
            assertEquals(reason, e.reason)
        }
        assertReason(BackupError.NewerVersion, """{"format":"dakilio","version":2}""")
        assertReason(BackupError.NotABackup, """{"format":"other","version":1}""")
        assertReason(BackupError.Malformed, """{"format":"dakilio"}""")
        assertReason(BackupError.Malformed, """{"format":"dakilio","version":1,"entries":[{"diary":1}]}""")
        assertReason(BackupError.Malformed, "not json")
    }

    @Test
    fun otherZipsAreNotDakilio() {
        val file = tmp.newFile("other.zip")
        ZipOutputStream(file.outputStream()).use { zip ->
            zip.putNextEntry(ZipEntry("backup.daylio"))
            zip.write(ByteArray(10))
            zip.closeEntry()
        }
        assertEquals(BackupKind.Other, detectBackupKind(file.readBytes().copyOf(BACKUP_HEAD_BYTES)))
        assertEquals(BackupKind.Other, detectBackupKind(ByteArray(0)))
        try {
            DakilioArchive(file)
            fail()
        } catch (e: BackupException) {
            assertEquals(BackupError.NotABackup, e.reason)
        }
    }
}
