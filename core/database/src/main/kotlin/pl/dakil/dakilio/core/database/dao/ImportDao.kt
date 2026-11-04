package pl.dakil.dakilio.core.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Transaction
import pl.dakil.dakilio.core.database.entity.DiaryEntity
import pl.dakil.dakilio.core.database.entity.DiarySuggestedGroupCrossRef
import pl.dakil.dakilio.core.database.entity.HabitEntity
import pl.dakil.dakilio.core.database.entity.MoodEntity
import pl.dakil.dakilio.core.database.entity.PostEntity
import pl.dakil.dakilio.core.database.entity.PostPhotoEntity
import pl.dakil.dakilio.core.database.entity.PostTagCrossRef
import pl.dakil.dakilio.core.database.entity.TagEntity
import pl.dakil.dakilio.core.database.entity.TagGroupEntity
import pl.dakil.dakilio.core.database.entity.TagKeywordEntity

/** Everything that replaces the user's content. References between rows are list indices. */
class ReplaceData(
    /** The label of every level's primary, by level. */
    val primaryLabels: Map<Int, String>,
    /** The color of every level's primary, by level, or null to keep the current colors. */
    val primaryColors: Map<Int, String?>? = null,
    /** At least one. */
    val diaries: List<ReplaceDiary>,
    /** New aliases (ids are assigned on insert). */
    val aliases: List<MoodEntity>,
    val groups: List<ReplaceGroup>,
    val posts: List<ReplacePost>,
    val habits: List<ReplaceHabit> = emptyList(),
    val keywords: List<ReplaceKeyword> = emptyList(),
)

/** The diary's id is ignored; [suggestedGroups] are indices into [ReplaceData.groups]. */
class ReplaceDiary(val diary: DiaryEntity, val suggestedGroups: List<Int> = emptyList())

/** The habit's `id` and `tag_id` are ignored: the tag is (group index, tag index within the group). */
class ReplaceHabit(val habit: HabitEntity, val groupIndex: Int, val tagIndex: Int)

/** The keyword's `id` and `tag_id` are ignored: the tag is (group index, tag index within the group). */
class ReplaceKeyword(val keyword: TagKeywordEntity, val groupIndex: Int, val tagIndex: Int)

/** The group's and tags' ids and `group_id` are ignored. */
class ReplaceGroup(val group: TagGroupEntity, val tags: List<TagEntity>)

class ReplacePost(
    /**
     * `id`, `mood_id` and `diary_id` are ignored: the mood comes from [moodLevel] and [aliasIndex], and
     * the diary from [diaryIndex].
     */
    val post: PostEntity,
    /** The mood's level, or null for an entry without a mood. */
    val moodLevel: Int?,
    /** An index into [ReplaceData.aliases], or null for the level's primary. */
    val aliasIndex: Int?,
    /** An index into [ReplaceData.diaries]. */
    val diaryIndex: Int = 0,
    /** (group index, tag index within the group) pairs. */
    val tags: List<Pair<Int, Int>>,
    val photoFileNames: List<String>,
)

/** Every row of the user's content, read in one transaction (for a backup). */
class ContentSnapshot(
    val moods: List<MoodEntity>,
    val diaries: List<DiaryEntity>,
    val suggestedGroups: List<DiarySuggestedGroupCrossRef>,
    val groups: List<TagGroupEntity>,
    val tags: List<TagEntity>,
    val posts: List<PostEntity>,
    val postTags: List<PostTagCrossRef>,
    val photos: List<PostPhotoEntity>,
    val habits: List<HabitEntity>,
    val keywords: List<TagKeywordEntity>,
)

/** What the user would lose by replacing their content. */
data class ContentCounts(val posts: Int, val photos: Int, val diaries: Int, val tags: Int)

@Dao
abstract class ImportDao {
    @Query(
        """
        SELECT (SELECT COUNT(*) FROM post) AS posts,
               (SELECT COUNT(*) FROM post_photo) AS photos,
               (SELECT COUNT(*) FROM diary) AS diaries,
               (SELECT COUNT(*) FROM tag) AS tags
        """,
    )
    abstract suspend fun contentCounts(): ContentCounts

    @Query("SELECT * FROM mood ORDER BY level, is_primary DESC, position, id")
    protected abstract suspend fun allMoods(): List<MoodEntity>

    @Query("SELECT * FROM diary ORDER BY position, id")
    protected abstract suspend fun allDiaries(): List<DiaryEntity>

    @Query("SELECT * FROM diary_suggested_group")
    protected abstract suspend fun allSuggestedGroups(): List<DiarySuggestedGroupCrossRef>

    @Query("SELECT * FROM tag_group ORDER BY position, id")
    protected abstract suspend fun allGroups(): List<TagGroupEntity>

    @Query("SELECT * FROM tag ORDER BY position, id")
    protected abstract suspend fun allTags(): List<TagEntity>

    @Query("SELECT * FROM post ORDER BY timestamp, id")
    protected abstract suspend fun allPosts(): List<PostEntity>

    @Query("SELECT * FROM post_tag")
    protected abstract suspend fun allPostTags(): List<PostTagCrossRef>

    @Query("SELECT * FROM post_photo ORDER BY post_id, position, id")
    protected abstract suspend fun allPhotos(): List<PostPhotoEntity>

    @Query("SELECT * FROM habit ORDER BY position, id")
    protected abstract suspend fun allHabits(): List<HabitEntity>

    @Query("SELECT * FROM tag_keyword ORDER BY tag_id, position, id")
    protected abstract suspend fun allKeywords(): List<TagKeywordEntity>

    @Transaction
    open suspend fun snapshot(): ContentSnapshot = ContentSnapshot(
        moods = allMoods(),
        diaries = allDiaries(),
        suggestedGroups = allSuggestedGroups(),
        groups = allGroups(),
        tags = allTags(),
        posts = allPosts(),
        postTags = allPostTags(),
        photos = allPhotos(),
        habits = allHabits(),
        keywords = allKeywords(),
    )

    @Query("DELETE FROM post")
    protected abstract suspend fun deletePosts()

    @Query("DELETE FROM diary")
    protected abstract suspend fun deleteDiaries()

    @Query("DELETE FROM tag_group")
    protected abstract suspend fun deleteTagGroups()

    @Query("DELETE FROM tag")
    protected abstract suspend fun deleteTags()

    @Query("DELETE FROM mood WHERE is_primary = 0")
    protected abstract suspend fun deleteAliases()

    @Query("SELECT * FROM mood WHERE is_primary = 1")
    protected abstract suspend fun primaries(): List<MoodEntity>

    @Query("UPDATE mood SET label = :label, is_archived = 0 WHERE id = :id")
    protected abstract suspend fun updatePrimary(id: Long, label: String)

    @Query("UPDATE mood SET color_key = :colorKey WHERE id = :id")
    protected abstract suspend fun updatePrimaryColor(id: Long, colorKey: String?)

    @Insert
    protected abstract suspend fun insertSuggestedGroups(refs: List<DiarySuggestedGroupCrossRef>)

    @Insert
    protected abstract suspend fun insertHabit(habit: HabitEntity): Long

    @Insert
    protected abstract suspend fun insertKeywords(keywords: List<TagKeywordEntity>)

    @Insert
    protected abstract suspend fun insertMood(mood: MoodEntity): Long

    @Insert
    protected abstract suspend fun insertDiary(diary: DiaryEntity): Long

    @Insert
    protected abstract suspend fun insertGroup(group: TagGroupEntity): Long

    @Insert
    protected abstract suspend fun insertTag(tag: TagEntity): Long

    @Insert
    protected abstract suspend fun insertPost(post: PostEntity): Long

    @Insert
    protected abstract suspend fun insertPostTags(refs: List<PostTagCrossRef>)

    @Insert
    protected abstract suspend fun insertPhotos(photos: List<PostPhotoEntity>)

    /**
     * Replaces every entry, diary, tag group, tag and alias with [data], atomically. Primaries are
     * kept (they're never removed) and renamed. `sqlite_sequence` is left alone on purpose: ids keep
     * growing, so new entries can never take the reserved sample ids.
     *
     * Returns the new diaries' ids, in the order of [ReplaceData.diaries].
     */
    @Transaction
    open suspend fun replaceAll(data: ReplaceData): List<Long> {
        require(data.diaries.isNotEmpty()) { "At least one diary" }
        // Posts first: post.mood_id is RESTRICT, and the cross refs cascade with them.
        deletePosts()
        deleteDiaries()
        deleteTags()
        deleteTagGroups()
        deleteAliases()

        val primaryIds = primaries().associate { it.level to it.id }.toMutableMap()
        data.primaryLabels.forEach { (level, label) ->
            val id = primaryIds[level]
            if (id != null) {
                updatePrimary(id, label)
            } else {
                primaryIds[level] = insertMood(MoodEntity(level = level, label = label, isPrimary = true, position = 0))
            }
        }
        data.primaryColors?.forEach { (level, color) -> primaryIds[level]?.let { updatePrimaryColor(it, color) } }
        val aliasIds = data.aliases.map { insertMood(it.copy(id = 0, isPrimary = false)) }

        val groupIds = mutableListOf<Long>()
        val tagIds = data.groups.map { group ->
            val groupId = insertGroup(group.group.copy(id = 0)).also(groupIds::add)
            group.tags.map { insertTag(it.copy(id = 0, groupId = groupId)) }
        }

        val diaryIds = data.diaries.map { item ->
            val diaryId = insertDiary(item.diary.copy(id = 0))
            insertSuggestedGroups(item.suggestedGroups.distinct().map { DiarySuggestedGroupCrossRef(diaryId, groupIds[it]) })
            diaryId
        }

        data.habits.forEach { item -> insertHabit(item.habit.copy(id = 0, tagId = tagIds[item.groupIndex][item.tagIndex])) }

        insertKeywords(data.keywords.map { item -> item.keyword.copy(id = 0, tagId = tagIds[item.groupIndex][item.tagIndex]) })

        data.posts.forEach { item ->
            val moodId = item.aliasIndex?.let(aliasIds::get) ?: item.moodLevel?.let(primaryIds::getValue)
            val postId = insertPost(item.post.copy(id = 0, moodId = moodId, diaryId = diaryIds[item.diaryIndex]))
            insertPostTags(item.tags.map { (group, tag) -> tagIds[group][tag] }.distinct().map { PostTagCrossRef(postId, it) })
            insertPhotos(item.photoFileNames.mapIndexed { index, name -> PostPhotoEntity(postId = postId, fileName = name, position = index) })
        }
        return diaryIds
    }
}
