package pl.dakil.dakilio.core.daylio

import java.io.File
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class DaylioArchiveTest {
    @get:Rule val tmp = TemporaryFolder()

    private fun dir(): File = tmp.root

    @Test
    fun readsBackupAndPhotos() {
        DaylioArchive(Fixtures.sampleArchive(dir())).use { archive ->
            assertEquals(15, archive.backup.version)
            assertFalse(archive.isUntestedVersion)
            assertEquals(6, archive.backup.dayEntries.size)
            assertTrue(archive.hasPhoto(Fixtures.PHOTO_CHECKSUM))
            assertArrayEquals(Fixtures.PHOTO_BYTES, archive.readPhoto(Fixtures.PHOTO_CHECKSUM))
            // Audio isn't indexed as a photo.
            assertFalse(archive.hasPhoto(Fixtures.AUDIO_CHECKSUM))
            assertNull(archive.readPhoto("nope"))
        }
    }

    @Test
    fun parsesNestedMetadata() {
        val assets = Fixtures.backup.assets.associateBy { it.id }
        assertEquals(90, assets.getValue(1).orientation)
        assertEquals(0, assets.getValue(2).orientation)
        assertTrue(assets.getValue(2).isAudio)
    }

    @Test
    fun otherVersionIsFlagged() {
        val file = Fixtures.writeArchive(dir(), mapOf("backup.daylio" to Fixtures.encode("""{"version":16}""")))
        DaylioArchive(file).use { assertTrue(it.isUntestedVersion) }
    }

    @Test
    fun notAZip() = assertFails(DaylioError.NotAZip) { File(dir(), "x.daylio").apply { writeText("hello") } }

    @Test
    fun missingBackupMember() = assertFails(DaylioError.MissingBackup) {
        Fixtures.writeArchive(dir(), mapOf("other.txt" to byteArrayOf(1)))
    }

    @Test
    fun garbageJson() = assertFails(DaylioError.Malformed) {
        Fixtures.writeArchive(dir(), mapOf("backup.daylio" to Fixtures.encode("{not json")))
    }

    @Test
    fun missingVersion() = assertFails(DaylioError.UnsupportedVersion) {
        Fixtures.writeArchive(dir(), mapOf("backup.daylio" to Fixtures.encode("""{"dayEntries":[]}""")))
    }

    @Test
    fun oversizedBackup() = assertFails(DaylioError.TooLarge) {
        // Highly compressible, so the ZIP stays tiny while the member exceeds the cap.
        val huge = ByteArray((DaylioArchive.MAX_BACKUP_BYTES + 1).toInt()) { 'A'.code.toByte() }
        Fixtures.writeArchive(dir(), mapOf("backup.daylio" to huge))
    }

    private fun assertFails(reason: DaylioError, file: () -> File) {
        val f = file()
        try {
            DaylioArchive(f).close()
            fail("expected $reason")
        } catch (e: DaylioImportException) {
            assertEquals(reason, e.reason)
        }
    }
}
