package pl.dakil.dakilio.core.daylio

import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class DaylioImportPlanTest {
    private val plan = planImport(Fixtures.backup, fallbackGroupName = "Daylio")

    private fun entryOn(day: Int, month: Int, hour: Int): PlannedEntry =
        plan.entries.single { LocalDate.ofEpochDay(it.localDate) == LocalDate.of(2026, month, day) && it.timestamp % 86_400_000 / 3_600_000 == (hour - 1).toLong() }

    @Test
    fun renamedBaseMoodNamesThePrimary() {
        // Daylio's group 2 ("good") is Dakilio's level 4; untouched base moods keep Dakilio's names.
        assertEquals(mapOf(4 to "gooooood"), plan.primaryNames)
    }

    @Test
    fun usedArchivedCustomMoodBecomesArchivedAlias() {
        assertEquals(listOf(PlannedMood(level = 5, label = "rad2", archived = true, position = 0)), plan.aliases)
        assertEquals(MoodRef.Alias(0), entryOn(28, 10, 9).mood)
        assertEquals(1, plan.stats.archivedAliases)
    }

    @Test
    fun levelsAreInverted() {
        assertEquals(MoodRef.Primary(5), entryOn(29, 10, 20).mood) // Daylio mood 1 = rad
        assertEquals(MoodRef.Primary(1), entryOn(31, 10, 21).mood) // Daylio mood 5 = awful
        assertEquals(MoodRef.Primary(3), entryOn(1, 11, 13).mood)
    }

    @Test
    fun groupsFollowDaylioOrderWithFallback() {
        assertEquals(listOf("Emotions", "Health", "Custom Group", "Daylio"), plan.groups.map { it.name })
        assertEquals(listOf(0, 1, 2, 3), plan.groups.map { it.position })
        assertEquals(listOf("excited", "happy"), plan.groups[0].tags.map { it.label })
        assertEquals(listOf("Orphan"), plan.groups[3].tags.map { it.label })
        assertEquals(7, plan.stats.tags)
    }

    @Test
    fun archivedTagStaysArchived() {
        val tag = plan.groups[2].tags.single()
        assertEquals("Custom activity #1", tag.label)
        assertTrue(tag.archived)
        assertEquals(listOf(TagRef(2, 0)), entryOn(28, 10, 9).tags)
    }

    @Test
    fun groupWithOnlyArchivedTagsIsArchived() {
        // "Custom Group" holds just the archived "Custom activity #1"; the others have active tags.
        assertEquals(listOf(false, false, true, false), plan.groups.map { it.archived })
    }

    @Test
    fun tagIconsComeFromDaylioIconsOrLabels() {
        val health = plan.groups[1].tags.associate { it.label to it.iconKey }
        assertEquals(mapOf("walk" to "directions_walk", "drink water" to "water_drop", "good sleep" to "hotel"), health)
        assertEquals(DEFAULT_TAG_ICON, plan.groups[2].tags.single().iconKey)
    }

    @Test
    fun timesKeepTheBackupOffsetAndZeroBasedMonths() {
        val entry = entryOn(1, 11, 13)
        assertEquals(1793536020000, entry.timestamp)
        assertEquals(60, entry.utcOffsetMinutes)
        assertEquals(LocalDate.of(2026, 11, 1).toEpochDay(), entry.localDate)
        assertEquals(LocalDate.of(2026, 10, 27), plan.stats.firstDate)
        assertEquals(LocalDate.of(2026, 11, 1), plan.stats.lastDate)
    }

    @Test
    fun photosOnlyVoiceNotesSkipped() {
        val photoEntry = entryOn(29, 10, 20)
        assertEquals(listOf(PlannedPhoto(Fixtures.PHOTO_CHECKSUM, 90)), photoEntry.photos)
        val voiceEntry = entryOn(31, 10, 21)
        assertTrue(voiceEntry.photos.isEmpty())
        assertEquals(listOf(TagRef(1, 0), TagRef(1, 2)), voiceEntry.tags)
        assertEquals(1, plan.stats.photos)
        assertEquals(1, plan.stats.voiceNotesSkipped)
        assertEquals(1, plan.stats.favoritesSkipped)
    }

    @Test
    fun textIsCleanedUp() {
        val entry = entryOn(28, 10, 9)
        assertNull(entry.title)
        assertEquals("Fish & chips <3", entry.note)
        assertEquals("Sample title 5", entryOn(31, 10, 21).title)
        assertNull(entryOn(31, 10, 21).note)
    }

    @Test
    fun brokenReferencesAreSkippedAndCounted() {
        // Tag 99 and asset 42 are undefined; the entry with mood 77 is dropped.
        assertEquals(5, plan.stats.entries)
        assertEquals(1, plan.stats.entriesSkipped)
        assertEquals(3, plan.stats.missingReferences)
        assertEquals(listOf(TagRef(2, 0), TagRef(3, 0)), entryOn(27, 10, 18).tags)
    }

    @Test
    fun backupWithoutGroupsPutsTagsInFallback() {
        val backup = DaylioBackup(
            version = 15,
            customMoods = listOf(DaylioMood(id = 1, moodGroupId = 3, predefinedNameId = 3)),
            tags = listOf(DaylioTag(id = 1, name = "reading")),
            dayEntries = listOf(DaylioEntry(id = 1, day = 1, month = 0, year = 2020, datetime = 1577872800000, mood = 1, tags = listOf(1))),
        )
        val plan = planImport(backup, "Daylio")
        assertEquals(listOf("Daylio"), plan.groups.map { it.name })
        assertEquals("menu_book", plan.groups.single().tags.single().iconKey)
        assertEquals(listOf(TagRef(0, 0)), plan.entries.single().tags)
        assertEquals(0, plan.entries.single().utcOffsetMinutes)
    }

    @Test
    fun invalidDateFieldsFallBackToTimestamp() {
        val backup = DaylioBackup(
            version = 15,
            customMoods = listOf(DaylioMood(id = 1, moodGroupId = 1, predefinedNameId = 1)),
            dayEntries = listOf(DaylioEntry(id = 1, datetime = 1793536020000, timeZoneOffset = 3_600_000, mood = 1)),
        )
        assertEquals(LocalDate.of(2026, 11, 1).toEpochDay(), planImport(backup, "D").entries.single().localDate)
    }
}
