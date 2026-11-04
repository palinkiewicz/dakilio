package pl.dakil.dakilio.core.backup

import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import pl.dakil.dakilio.core.model.KeywordDraft
import pl.dakil.dakilio.core.model.PreviewLength
import pl.dakil.dakilio.core.model.ThemeMode

class RestorePlanTest {
    private val now = 1_000L

    private fun backup(
        moods: List<BackupMood> = emptyList(),
        diaries: List<BackupDiary> = listOf(BackupDiary(id = 10, name = "Main")),
        groups: List<BackupTagGroup> = emptyList(),
        habits: List<BackupHabit> = emptyList(),
        entries: List<BackupEntry> = emptyList(),
        settings: BackupSettings? = null,
    ) = DakilioBackup(DakilioBackup.FORMAT, 1, moods = moods, diaries = diaries, tagGroups = groups, habits = habits, entries = entries, settings = settings)

    @Test
    fun mapsEverything() {
        val plan = planRestore(
            backup(
                moods = listOf(
                    BackupMood(id = 1, level = 1, name = "Awful", primary = true, color = "crimson"),
                    BackupMood(id = 7, level = 2, name = "Sad", position = 1, archived = true),
                    BackupMood(id = 8, level = 2, name = "Down", position = 0),
                ),
                diaries = listOf(
                    BackupDiary(id = 20, name = "Work", icon = "work", position = 1, createdAt = "1970-01-01T00:00:05Z"),
                    BackupDiary(id = 10, name = "Main", position = 0, suggestedGroups = listOf(2, 99)),
                ),
                groups = listOf(
                    BackupTagGroup(id = 2, name = "People", position = 0, color = "orange", tags = listOf(BackupTag(5, "Family", "family"), BackupTag(6, "Friends", "group", position = 1, archived = true))),
                ),
                habits = listOf(BackupHabit(tag = 5, daysPerWeek = 9), BackupHabit(tag = 5, daysPerWeek = 2), BackupHabit(tag = 404, daysPerWeek = 1)),
                entries = listOf(
                    BackupEntry(time = "2026-11-02T23:30+01:00", diary = 20, mood = 7, title = " ", note = "Hi", tags = listOf(6, 5, 5, 404), photos = listOf("photos/a.jpg", "../x")),
                    BackupEntry(time = "2026-11-01T08:00Z", diary = 99, mood = 1),
                    BackupEntry(time = "yesterday", diary = 10),
                ),
                settings = BackupSettings(theme = "Dark", tagStyle = "Sparkles", timelineTextScale = 9f, previewLength = "Full", lastDiary = 20),
            ),
            defaultDiaryName = "Journey",
            now = now,
        )

        assertEquals(mapOf(1 to RestorePrimary("Awful", "crimson")), plan.primaries)
        assertEquals(listOf(RestoreAlias(2, "Down", 0, false, null), RestoreAlias(2, "Sad", 1, true, null)), plan.aliases)
        assertEquals(listOf("Main", "Work"), plan.diaries.map { it.name })
        assertEquals(listOf(0), plan.diaries[0].suggestedGroups)
        assertEquals(5_000L, plan.diaries[1].createdAt)
        assertEquals(now, plan.diaries[0].createdAt)
        assertEquals(listOf("Family", "Friends"), plan.groups.single().tags.map { it.name })
        assertEquals(listOf(RestoreHabit(RestoreTagRef(0, 0), 7, 0, now)), plan.habits)

        assertEquals(2, plan.entries.size)
        val first = plan.entries[0]
        assertEquals(1, first.diaryIndex)
        assertEquals(RestoreMoodRef.Alias(1), first.mood)
        assertEquals(60, first.utcOffsetMinutes)
        assertEquals(LocalDate.of(2026, 11, 2).toEpochDay(), first.localDate)
        assertNull(first.title)
        assertEquals(listOf(RestoreTagRef(0, 1), RestoreTagRef(0, 0)), first.tags)
        assertEquals(listOf("photos/a.jpg"), first.photos)
        assertEquals(first.timestamp, first.createdAt)
        // An unknown diary falls back to the first one.
        assertEquals(0, plan.entries[1].diaryIndex)
        assertEquals(RestoreMoodRef.Primary(1), plan.entries[1].mood)

        // Habit 404, suggested group 99, tag 404, photo "../x" and diary 99.
        assertEquals(5, plan.stats.missingReferences)
        assertEquals(1, plan.stats.entriesSkipped)
        assertEquals(LocalDate.of(2026, 11, 1), plan.stats.firstDate)
        assertEquals(LocalDate.of(2026, 11, 2), plan.stats.lastDate)

        assertEquals(ThemeMode.Dark, plan.settings.themeMode)
        assertNull(plan.settings.tagStyle)
        assertEquals(1.6f, plan.settings.timelineTextScale!!, 0.001f)
        assertEquals(PreviewLength.Full, plan.settings.previewLength)
        assertEquals(1, plan.settings.lastDiaryIndex)
    }

    @Test
    fun duplicatePrimariesBecomeAliases() {
        val plan = planRestore(
            backup(
                moods = listOf(
                    BackupMood(id = 1, level = 5, name = "Rad", primary = true),
                    BackupMood(id = 2, level = 5, name = "Great", primary = true),
                    BackupMood(id = 3, level = 9, name = "Off the scale", primary = true),
                    BackupMood(id = 4, level = 4, name = "  ", primary = true),
                ),
                entries = listOf(BackupEntry(time = "2026-01-01T10:00Z", diary = 10, mood = 4), BackupEntry(time = "2026-01-01T11:00Z", diary = 10, mood = 3)),
            ),
            "Journey",
            now,
        )
        assertEquals(setOf(5), plan.primaries.keys)
        assertEquals(listOf("Great"), plan.aliases.map { it.name })
        // A nameless mood uses its level's primary (which gets the default name).
        assertEquals(RestoreMoodRef.Primary(4), plan.entries[0].mood)
        assertNull(plan.entries[1].mood)
        assertEquals(1, plan.stats.missingReferences)
    }

    @Test
    fun noDiariesGetsADefaultOne() {
        val plan = planRestore(backup(diaries = emptyList(), entries = listOf(BackupEntry(time = "2026-01-01T10:00Z", diary = 3))), "Journey", now)
        assertEquals(listOf(RestoreDiary("Journey", "", 0, now, emptyList())), plan.diaries)
        assertEquals(0, plan.entries.single().diaryIndex)
        assertEquals(0, plan.stats.missingReferences)
        assertEquals(RestoreSettings(), plan.settings)
    }

    @Test
    fun keepsUsableKeywordsWithoutDuplicates() {
        val tag = BackupTag(
            5, "Cycling", "bike",
            keywords = listOf(BackupKeyword(" bike "), BackupKeyword("BIKE"), BackupKeyword("rower", approximate = true), BackupKeyword(" !! ")),
        )
        val plan = planRestore(backup(groups = listOf(BackupTagGroup(id = 2, name = "Activities", tags = listOf(tag)))), "Journey", now)
        assertEquals(
            listOf(KeywordDraft("bike", false), KeywordDraft("rower", true)),
            plan.groups.single().tags.single().keywords,
        )
    }

    @Test
    fun keywordsSurviveJson() {
        val original = backup(groups = listOf(BackupTagGroup(id = 2, name = "A", tags = listOf(BackupTag(5, "C", "x", keywords = listOf(BackupKeyword("ice cream", true)))))))
        assertEquals(original, DakilioBackup.parse(original.encode()))
    }
}
