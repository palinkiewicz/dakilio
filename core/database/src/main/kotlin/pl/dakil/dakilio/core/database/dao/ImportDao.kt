package pl.dakil.dakilio.core.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Transaction
import pl.dakil.dakilio.core.database.entity.DiaryEntity
import pl.dakil.dakilio.core.database.entity.MoodEntity
import pl.dakil.dakilio.core.database.entity.PostDiaryCrossRef
import pl.dakil.dakilio.core.database.entity.PostEntity
import pl.dakil.dakilio.core.database.entity.PostPhotoEntity
import pl.dakil.dakilio.core.database.entity.PostTagCrossRef
import pl.dakil.dakilio.core.database.entity.TagEntity
import pl.dakil.dakilio.core.database.entity.TagGroupEntity

/** Everything that replaces the user's content. References between rows are list indices. */
class ReplaceData(
    /** The label of every level's primary, by level. */
    val primaryLabels: Map<Int, String>,
    val diary: DiaryEntity,
    /** New aliases (ids are assigned on insert). */
    val aliases: List<MoodEntity>,
    val groups: List<ReplaceGroup>,
    val posts: List<ReplacePost>,
)

/** The group's and tags' ids and `group_id` are ignored. */
class ReplaceGroup(val group: TagGroupEntity, val tags: List<TagEntity>)

class ReplacePost(
    /** `id` and `mood_id` are ignored: the mood comes from [moodLevel] and [aliasIndex]. */
    val post: PostEntity,
    val moodLevel: Int,
    /** An index into [ReplaceData.aliases], or null for the level's primary. */
    val aliasIndex: Int?,
    /** (group index, tag index within the group) pairs. */
    val tags: List<Pair<Int, Int>>,
    val photoFileNames: List<String>,
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
    protected abstract suspend fun insertPostDiary(ref: PostDiaryCrossRef)

    @Insert
    protected abstract suspend fun insertPhotos(photos: List<PostPhotoEntity>)

    /**
     * Replaces every entry, diary, tag group, tag and alias with [data], atomically. Primaries are
     * kept (they're never removed) and renamed. `sqlite_sequence` is left alone on purpose: ids keep
     * growing, so new entries can never take the reserved sample ids.
     *
     * Returns the new diary's id.
     */
    @Transaction
    open suspend fun replaceAll(data: ReplaceData): Long {
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
        val aliasIds = data.aliases.map { insertMood(it.copy(id = 0, isPrimary = false)) }

        val diaryId = insertDiary(data.diary.copy(id = 0))

        val tagIds = data.groups.map { group ->
            val groupId = insertGroup(group.group.copy(id = 0))
            group.tags.map { insertTag(it.copy(id = 0, groupId = groupId)) }
        }

        data.posts.forEach { item ->
            val moodId = item.aliasIndex?.let(aliasIds::get) ?: primaryIds.getValue(item.moodLevel)
            val postId = insertPost(item.post.copy(id = 0, moodId = moodId))
            insertPostDiary(PostDiaryCrossRef(postId, diaryId))
            insertPostTags(item.tags.map { (group, tag) -> tagIds[group][tag] }.distinct().map { PostTagCrossRef(postId, it) })
            insertPhotos(item.photoFileNames.mapIndexed { index, name -> PostPhotoEntity(postId = postId, fileName = name, position = index) })
        }
        return diaryId
    }
}
