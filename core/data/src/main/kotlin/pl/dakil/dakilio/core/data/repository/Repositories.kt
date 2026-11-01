package pl.dakil.dakilio.core.data.repository

import android.net.Uri
import kotlinx.coroutines.flow.Flow
import pl.dakil.dakilio.core.model.Diary
import pl.dakil.dakilio.core.model.Mood
import pl.dakil.dakilio.core.model.Post
import pl.dakil.dakilio.core.model.PostDraft
import pl.dakil.dakilio.core.model.TagGroup
import pl.dakil.dakilio.core.model.TagStyle
import pl.dakil.dakilio.core.model.TagUsage
import pl.dakil.dakilio.core.model.ThemeMode
import pl.dakil.dakilio.core.model.UserSettings

interface MoodRepository {
    /** Every pickable (non-archived) mood: by level, worst first; primary before its aliases. */
    val moods: Flow<List<Mood>>

    suspend fun addAlias(level: Int, label: String)

    /** Renames a primary or an alias. */
    suspend fun rename(id: Long, label: String)

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

    /** Entries that belong to this diary only, i.e. would be orphaned by deleting it. */
    suspend fun exclusivePostCount(diaryId: Long): Int

    /** Moves exclusive entries to [moveExclusiveTo], or deletes them (and their photos) when null. */
    suspend fun deleteDiary(diaryId: Long, moveExclusiveTo: Long?)
}

interface TagRepository {
    /** Active groups with their active tags, in display order. */
    val groups: Flow<List<TagGroup>>

    suspend fun createGroup(name: String): Long

    suspend fun renameGroup(id: Long, name: String)

    suspend fun setGroupArchived(id: Long, archived: Boolean)

    suspend fun saveTag(id: Long?, groupId: Long, label: String, iconKey: String): Long

    suspend fun setTagArchived(id: Long, archived: Boolean)

    suspend fun tagUsage(id: Long): TagUsage

    /** Deletes the tag for good: it disappears from every entry, and entries left empty are deleted. */
    suspend fun deleteTag(id: Long)
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

interface PhotoRepository {
    /** Copies a picked image into app storage (downscaled, re-encoded, metadata stripped). */
    suspend fun import(uri: Uri): String?

    fun pathOf(fileName: String): String

    suspend fun delete(fileNames: Collection<String>)

    /** Removes photo files no entry references (e.g. from an abandoned draft). */
    suspend fun sweepOrphans()
}

interface SettingsRepository {
    val settings: Flow<UserSettings>

    suspend fun setThemeMode(mode: ThemeMode)

    suspend fun setLastEntryDiaryIds(ids: Set<Long>)

    suspend fun setSampleHintDismissed(dismissed: Boolean)

    suspend fun setTagStyle(style: TagStyle)

    suspend fun setShowHints(show: Boolean)
}
