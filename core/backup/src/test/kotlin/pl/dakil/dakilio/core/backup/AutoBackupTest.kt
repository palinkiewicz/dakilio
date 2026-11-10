package pl.dakil.dakilio.core.backup

import java.time.Instant
import java.time.LocalDateTime
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import pl.dakil.dakilio.core.model.AutoBackupFrequency

class AutoBackupTest {
    @Test
    fun `file names round-trip`() {
        val time = LocalDateTime.of(2026, 11, 10, 3, 12)
        val name = AutoBackup.fileName(time)
        assertEquals("dakilio-auto-2026-11-10-0312.dakilio", name)
        assertEquals(time, AutoBackup.parseFileName(name))
    }

    @Test
    fun `other files are not automatic backups`() {
        listOf(
            "dakilio-2026-11-10.dakilio",
            "dakilio-auto-2026-11-10-0312.dakilio (1)",
            "dakilio-auto-2026-11-10-0312 (1).dakilio",
            "dakilio-auto-2026-13-10-0312.dakilio",
            "dakilio-auto-2026-11-10-2560.dakilio",
            "notes.txt",
            "",
        ).forEach { assertNull(it, AutoBackup.parseFileName(it)) }
    }

    @Test
    fun `keeps the newest and never lists other files`() {
        val names = listOf(
            "dakilio-auto-2026-11-08-0300.dakilio",
            "my-own.dakilio",
            "dakilio-auto-2026-11-10-0300.dakilio",
            "dakilio-auto-2026-11-09-0300.dakilio",
            "dakilio-auto-2026-11-07-0300.dakilio",
            "dakilio-2026-11-01.dakilio",
        )
        assertEquals(
            listOf("dakilio-auto-2026-11-08-0300.dakilio", "dakilio-auto-2026-11-07-0300.dakilio"),
            AutoBackup.filesToDelete(names, keep = 2),
        )
        assertEquals(emptyList<String>(), AutoBackup.filesToDelete(names, keep = 10))
    }

    @Test
    fun `always keeps at least one`() {
        val names = listOf("dakilio-auto-2026-11-08-0300.dakilio", "dakilio-auto-2026-11-09-0300.dakilio")
        assertEquals(listOf("dakilio-auto-2026-11-08-0300.dakilio"), AutoBackup.filesToDelete(names, keep = 0))
    }

    private val backup = DakilioBackup(
        format = DakilioBackup.FORMAT,
        version = DakilioBackup.FORMAT_VERSION,
        exportedAt = "2026-11-03T14:20:00+01:00",
        appVersion = "1.2.0",
        diaries = listOf(BackupDiary(id = 1, name = "Journey", icon = "explore")),
        entries = listOf(BackupEntry(time = "2026-11-02T21:05+01:00", diary = 1, note = "Hi", photos = listOf("photos/a.jpg"))),
    )

    @Test
    fun `fingerprint ignores when and by which version`() {
        val photos = mapOf("a.jpg" to 1000L)
        val later = backup.copy(exportedAt = "2026-11-04T03:00:00+01:00", appVersion = "1.3.0")
        assertEquals(AutoBackup.fingerprint(backup, photos), AutoBackup.fingerprint(later, photos))
    }

    @Test
    fun `fingerprint changes with content and photo sizes`() {
        val photos = mapOf("a.jpg" to 1000L)
        val base = AutoBackup.fingerprint(backup, photos)
        val edited = backup.copy(entries = backup.entries.map { it.copy(note = "Hi!") })
        assertNotEquals(base, AutoBackup.fingerprint(edited, photos))
        assertNotEquals(base, AutoBackup.fingerprint(backup, mapOf("a.jpg" to 800L)))
        assertNotEquals(base, AutoBackup.fingerprint(backup, mapOf("a.jpg" to 1000L, "b.jpg" to 5L)))
    }

    @Test
    fun `fingerprint doesn't depend on photo order`() {
        val one = linkedMapOf("a.jpg" to 1L, "b.jpg" to 2L)
        val other = linkedMapOf("b.jpg" to 2L, "a.jpg" to 1L)
        assertEquals(AutoBackup.fingerprint(backup, one), AutoBackup.fingerprint(backup, other))
    }

    @Test
    fun `overdue after twice the interval plus a day`() {
        val last = Instant.parse("2026-11-01T03:00:00Z")
        val daily = AutoBackupFrequency.Daily
        assertFalse(AutoBackup.isOverdue(last, null, Instant.parse("2026-11-04T03:00:00Z"), daily))
        assertTrue(AutoBackup.isOverdue(last, null, Instant.parse("2026-11-04T03:01:00Z"), daily))
        val weekly = AutoBackupFrequency.Weekly
        assertFalse(AutoBackup.isOverdue(last, null, Instant.parse("2026-11-16T03:00:00Z"), weekly))
        assertTrue(AutoBackup.isOverdue(last, null, Instant.parse("2026-11-16T03:01:00Z"), weekly))
    }

    @Test
    fun `before the first backup it counts from turning on`() {
        val on = Instant.parse("2026-11-01T03:00:00Z")
        assertFalse(AutoBackup.isOverdue(null, null, Instant.parse("2027-01-01T00:00:00Z"), AutoBackupFrequency.Daily))
        assertTrue(AutoBackup.isOverdue(null, on, Instant.parse("2026-11-05T00:00:00Z"), AutoBackupFrequency.Daily))
    }
}
