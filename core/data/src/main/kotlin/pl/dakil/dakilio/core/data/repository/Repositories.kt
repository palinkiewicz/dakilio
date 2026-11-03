package pl.dakil.dakilio.core.data.repository

import android.net.Uri
import java.time.LocalDate
import kotlinx.coroutines.flow.Flow
import pl.dakil.dakilio.core.model.ArchivedTags
import pl.dakil.dakilio.core.model.Diary
import pl.dakil.dakilio.core.model.GroupUsage
import pl.dakil.dakilio.core.model.Habit
import pl.dakil.dakilio.core.model.HabitStatus
import pl.dakil.dakilio.core.model.Mood
import pl.dakil.dakilio.core.model.Post
import pl.dakil.dakilio.core.model.PostDraft
import pl.dakil.dakilio.core.model.SuggestedGroup
import pl.dakil.dakilio.core.model.TagGroup
import pl.dakil.dakilio.core.model.PreviewLength
import pl.dakil.dakilio.core.model.TagStyle
import pl.dakil.dakilio.core.model.TagUsage
import pl.dakil.dakilio.core.model.ThemeMode
import pl.dakil.dakilio.core.model.UserSettings
import pl.dakil.dakilio.core.backup.RestoreStats
import pl.dakil.dakilio.core.daylio.ImportStats

interface MoodRepository {
    /** Every pickable (non-archived) mood: by level, worst first; primary before its aliases. */
    val moods: Flow<List<Mood>>

    /** Archived aliases, by level, worst first. */
    val archivedMoods: Flow<List<Mood>>

    suspend fun addAlias(level: Int, label: String)

    /** Renames a primary or an alias. */
    suspend fun rename(id: Long, label: String)

    /** Sets a mood's own color (a palette key), or `null` to use its primary's (or the level's). */
    suspend fun setColor(id: Long, colorKey: String?)

    /** How many entries use this exact mood. */
    suspend fun usageCount(id: Long): Int

    /** Hides an alias from pickers; its entries keep it. No-op for primaries. */
    suspend fun setAliasArchived(id: Long, archived: Boolean)

    /** Moves the alias's entries to [targetId], then deletes the alias. Returns how many moved. */
    suspend fun replaceAlias(id: Long, targetId: Long): Int

    /** Deletes an alias no entry uses. No-op for primaries or aliases still in use. */
    suspend fun deleteUnusedAlias(id: Long)
}

interface DiaryRepository {
    val diaries: Flow<List<Diary>>

    /** Number of entries per diary id (diaries without entries are absent). */
    val postCounts: Flow<Map<Long, Int>>

    suspend fun getDiary(id: Long): Diary?

    suspend fun saveDiary(id: Long?, name: String, iconKey: String, suggestedGroupIds: Set<Long>): Long

    /** How many entries are in this diary. */
    suspend fun postCount(diaryId: Long): Int

    /** Moves the diary's entries to [moveEntriesTo], or deletes them (and their photos) when null. */
    suspend fun deleteDiary(diaryId: Long, moveEntriesTo: Long?)
}

interface TagRepository {
    /** Active groups with their active tags, in display order. */
    val groups: Flow<List<TagGroup>>

    /** Archived groups and archived tags, for the Archived screen. */
    val archived: Flow<ArchivedTags>

    suspend fun createGroup(name: String): Long

    suspend fun renameGroup(id: Long, name: String)

    suspend fun setGroupArchived(id: Long, archived: Boolean)

    suspend fun groupUsage(id: Long): GroupUsage

    /** Deletes the group and its tags for good: they leave every entry, and entries left empty are deleted. */
    suspend fun deleteGroup(id: Long)

    /** @param colorKey the tag's own color; `null` makes it use its group's. */
    suspend fun saveTag(id: Long?, groupId: Long, label: String, iconKey: String, colorKey: String?): Long

    /** Restoring a tag whose group is archived restores the group too. */
    suspend fun setTagArchived(id: Long, archived: Boolean)

    suspend fun tagUsage(id: Long): TagUsage

    /** Deletes the tag for good: it disappears from every entry, and entries left empty are deleted. */
    suspend fun deleteTag(id: Long)

    /** Sets the default color of the group's tags; `null` clears it. */
    suspend fun setGroupColor(id: Long, colorKey: String?)
}

interface PostRepository {
    /** Every entry, newest first. */
    val posts: Flow<List<Post>>

    /** How many of the bundled sample entries are still around. */
    val samplePostCount: Flow<Int>

    suspend fun getPost(id: Long): Post?

    suspend fun savePost(draft: PostDraft): Long

    suspend fun deletePost(id: Long)

    suspend fun removeSamplePosts()
}

/** The Habits extra. Entries of every diary count; a day counts once per habit. */
interface HabitRepository {
    /** Every habit with this week's and last week's progress, in display order. */
    val habits: Flow<List<HabitStatus>>

    val habitCount: Flow<Int>

    /** Every habit, without progress. */
    suspend fun habitList(): List<Habit>

    /** Days with each habit's tag in the (Monday-based) week of [date], by habit id; habits at 0 are absent. */
    suspend fun weekCounts(date: LocalDate): Map<Long, Int>

    suspend fun addHabit(tagId: Long, daysPerWeek: Int): Long

    suspend fun setDaysPerWeek(id: Long, daysPerWeek: Int)

    suspend fun deleteHabit(id: Long)
}

interface PhotoRepository {
    /** Copies a picked image into app storage (downscaled, re-encoded, metadata stripped). */
    suspend fun import(uri: Uri): String?

    /** Like [import], for an image held in memory; [rotationDegrees] applies when it has no EXIF orientation. */
    suspend fun importBytes(bytes: ByteArray, rotationDegrees: Int = 0): String?

    fun pathOf(fileName: String): String

    suspend fun delete(fileNames: Collection<String>)

    /** Removes photo files no entry references (e.g. from an abandoned draft). */
    suspend fun sweepOrphans()
}

interface SettingsRepository {
    val settings: Flow<UserSettings>

    suspend fun setThemeMode(mode: ThemeMode)

    suspend fun setLastEntryDiaryId(id: Long)

    suspend fun setSampleHintDismissed(dismissed: Boolean)

    suspend fun setTagStyle(style: TagStyle)

    suspend fun setShowHints(show: Boolean)

    suspend fun setTimelineTextScale(scale: Float)

    suspend fun setEditorTextScale(scale: Float)

    suspend fun setPreviewLength(length: PreviewLength)

    suspend fun setCompactTags(compact: Boolean)
}

/** Recent searches, newest first, kept on the device. Entries are opaque strings encoded by the search screen. */
interface SearchHistoryRepository {
    val recent: Flow<List<String>>

    /** Records [entry] as the newest search; a repeat moves up instead of appearing twice. */
    suspend fun push(entry: String)

    suspend fun remove(entry: String)

    suspend fun clear()
}

/**
 * The first-run intro and the tag group suggestions it shares with the Tags screen.
 *
 * Until the intro is over, nothing here opens the database: Room seeds it on first open with
 * localized names, so it must not happen before the user has picked a language.
 */
interface OnboardingRepository {
    /** The ready-made tag groups (the ones a fresh install is seeded with). No database access. */
    val suggestedGroups: List<SuggestedGroup>

    /**
     * Whether to show the intro on this launch. True for a fresh install and for an intro that was
     * left halfway; false once finished, and for installs from before the intro existed.
     */
    suspend fun shouldShowIntro(): Boolean

    /** Remembers that the intro was shown, so leaving it halfway brings it back next time. */
    suspend fun markIntroStarted()

    suspend fun finishIntro()

    /**
     * Keeps only the seed groups with these keys: the others are deleted for good (their tags leave
     * the sample entries, and samples left empty go too). Opens, and so seeds, the database.
     */
    suspend fun keepStarterGroups(keys: Set<String>)

    /** Adds the suggestion as a new group at the end, with all its tags. */
    suspend fun addSuggestedGroup(key: String)
}

/** What's in Dakilio right now, i.e. what a Daylio import would delete. */
data class CurrentContent(val entries: Int, val photos: Int, val diaries: Int, val tags: Int)

data class DaylioPreview(
    val stats: ImportStats,
    /** The backup's version differs from the one the importer was checked against. */
    val untestedVersion: Boolean,
    /** Photos the entries reference that aren't in the archive. */
    val missingPhotos: Int,
    val current: CurrentContent,
)

data class DaylioImportResult(val entries: Int, val photos: Int, val photosFailed: Int)

/**
 * Imports a `.daylio` backup, **replacing** every entry, photo, diary, tag group, tag and mood alias.
 * Stateful: [open] a file, then [import] it; [close] releases the temporary copy. Errors in [open]
 * are [pl.dakil.dakilio.core.daylio.DaylioImportException]s where the file is the problem.
 */
interface DaylioImportRepository {
    suspend fun open(uri: Uri): DaylioPreview

    /** Copies the photos (reporting progress), then swaps the content in one transaction. */
    suspend fun import(onProgress: (done: Int, total: Int) -> Unit): DaylioImportResult

    fun close()
}

data class BackupExportResult(val entries: Int, val photos: Int, val missingPhotos: Int)

sealed interface BackupOpenResult {
    /** An encrypted backup: [BackupRepository.unlock] it with the password. */
    data object NeedsPassword : BackupOpenResult

    data class Ready(val preview: BackupPreview) : BackupOpenResult
}

data class BackupPreview(
    val stats: RestoreStats,
    /** Epoch millis of when the backup was made, if the file says. */
    val exportedAt: Long?,
    val encrypted: Boolean,
    /** Photos the entries reference that aren't in the archive. */
    val missingPhotos: Int,
    val current: CurrentContent,
)

data class BackupRestoreResult(val entries: Int, val photos: Int, val photosFailed: Int)

/**
 * Dakilio's own `.dakilio` backups (see `.materials/dakilio_format.md`).
 *
 * [export] writes everything (entries, photos, moods, tags, diaries, habits and settings) to a
 * file, optionally encrypted with a password. Restoring is stateful, like the Daylio import: [open]
 * a file, [unlock] it if it's encrypted, then [restore] it, which **replaces** all content; [close]
 * releases the temporary copies. Errors where the file is the problem are
 * [pl.dakil.dakilio.core.backup.BackupException]s.
 */
interface BackupRepository {
    /** [password] null writes a plain, readable backup. The caller clears the password afterwards. */
    suspend fun export(uri: Uri, password: CharArray?, onProgress: (done: Int, total: Int) -> Unit): BackupExportResult

    suspend fun open(uri: Uri): BackupOpenResult

    /** Decrypts the opened file. Throws `BackupException(WrongPassword)` and leaves it open for another try. */
    suspend fun unlock(password: CharArray): BackupPreview

    /** Copies the photos (reporting progress), then swaps the content in one transaction. */
    suspend fun restore(onProgress: (done: Int, total: Int) -> Unit): BackupRestoreResult

    fun close()
}
