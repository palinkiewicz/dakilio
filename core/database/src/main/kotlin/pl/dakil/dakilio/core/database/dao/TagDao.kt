package pl.dakil.dakilio.core.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.MapColumn
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import kotlinx.coroutines.flow.Flow
import pl.dakil.dakilio.core.database.entity.PopulatedTagGroup
import pl.dakil.dakilio.core.database.entity.TagEntity
import pl.dakil.dakilio.core.database.entity.TagGroupEntity

@Dao
abstract class TagDao {
    /** Active groups with all their tags (archived tags are filtered by the repository). */
    @Transaction
    @Query("SELECT * FROM tag_group WHERE is_archived = 0 ORDER BY position, id")
    abstract fun observeActiveGroups(): Flow<List<PopulatedTagGroup>>

    /** Archived groups with all their tags. */
    @Transaction
    @Query("SELECT * FROM tag_group WHERE is_archived = 1 ORDER BY position, id")
    abstract fun observeArchivedGroups(): Flow<List<PopulatedTagGroup>>

    /** Active groups that hold at least one archived tag, with all their tags. */
    @Transaction
    @Query(
        """
        SELECT * FROM tag_group g
        WHERE g.is_archived = 0 AND EXISTS (SELECT 1 FROM tag t WHERE t.group_id = g.id AND t.is_archived = 1)
        ORDER BY g.position, g.id
        """,
    )
    abstract fun observeActiveGroupsWithArchivedTags(): Flow<List<PopulatedTagGroup>>

    /** Every group's default tag color (archived groups too), for resolving the color of entries' tags. */
    @Query("SELECT id, color_key FROM tag_group")
    abstract fun observeGroupColors(): Flow<Map<@MapColumn("id") Long, @MapColumn("color_key") String?>>

    @Query("UPDATE tag_group SET color_key = :colorKey WHERE id = :id")
    abstract suspend fun setGroupColor(id: Long, colorKey: String?)

    @Query("SELECT COALESCE(MAX(position), -1) + 1 FROM tag_group")
    abstract suspend fun nextGroupPosition(): Int

    @Query("SELECT COALESCE(MAX(position), -1) + 1 FROM tag WHERE group_id = :groupId")
    abstract suspend fun nextTagPosition(groupId: Long): Int

    @Insert
    abstract suspend fun insertGroup(group: TagGroupEntity): Long

    /** Adds a group at the end, with [tags] in the given order. Returns the group's id. */
    @Transaction
    open suspend fun insertGroupWithTags(name: String, colorKey: String?, tags: List<Pair<String, String>>): Long {
        val groupId = insertGroup(TagGroupEntity(name = name, position = nextGroupPosition(), colorKey = colorKey))
        tags.forEachIndexed { position, (label, iconKey) ->
            insertTag(TagEntity(groupId = groupId, label = label, iconKey = iconKey, position = position))
        }
        return groupId
    }

    @Query("SELECT EXISTS (SELECT 1 FROM tag_group WHERE id = :id)")
    abstract suspend fun groupExists(id: Long): Boolean

    /** Deletes these groups for good, like [deleteGroup], in one transaction; missing ids are skipped. */
    @Transaction
    open suspend fun deleteGroups(ids: Collection<Long>) {
        ids.filter { groupExists(it) }.forEach { deleteGroup(it) }
    }

    @Query("UPDATE tag_group SET name = :name WHERE id = :id")
    abstract suspend fun renameGroup(id: Long, name: String)

    @Query("UPDATE tag_group SET is_archived = :archived WHERE id = :id")
    abstract suspend fun setGroupArchived(id: Long, archived: Boolean)

    @Insert
    abstract suspend fun insertTag(tag: TagEntity): Long

    @Update
    abstract suspend fun updateTag(tag: TagEntity)

    @Query("SELECT * FROM tag WHERE id = :id")
    abstract suspend fun getTag(id: Long): TagEntity?

    @Query("UPDATE tag SET is_archived = :archived WHERE id = :id")
    abstract suspend fun setTagArchived(id: Long, archived: Boolean)

    @Query("UPDATE tag_group SET is_archived = 0 WHERE id = (SELECT group_id FROM tag WHERE id = :tagId)")
    protected abstract suspend fun unarchiveGroupOfTag(tagId: Long)

    /** Un-archives the tag, and its group too if that's archived, so the tag shows up again. */
    @Transaction
    open suspend fun restoreTag(tagId: Long) {
        setTagArchived(tagId, false)
        unarchiveGroupOfTag(tagId)
    }

    @Query("SELECT COUNT(*) FROM post_tag WHERE tag_id = :tagId")
    abstract suspend fun usageCount(tagId: Long): Int

    /** Entries that only exist because of this tag: no mood, text, photos or other tags. */
    @Query(
        """
        SELECT COUNT(*)
        FROM post p
        WHERE p.id IN (SELECT post_id FROM post_tag WHERE tag_id = :tagId)
          AND p.mood_id IS NULL
          AND TRIM(COALESCE(p.title, '')) = ''
          AND TRIM(COALESCE(p.note, '')) = ''
          AND NOT EXISTS (SELECT 1 FROM post_photo ph WHERE ph.post_id = p.id)
          AND NOT EXISTS (SELECT 1 FROM post_tag pt WHERE pt.post_id = p.id AND pt.tag_id != :tagId)
        """,
    )
    abstract suspend fun emptiedCount(tagId: Long): Int

    @Query(
        """
        DELETE FROM post WHERE id IN (
        SELECT p.id
        FROM post p
        WHERE p.id IN (SELECT post_id FROM post_tag WHERE tag_id = :tagId)
          AND p.mood_id IS NULL
          AND TRIM(COALESCE(p.title, '')) = ''
          AND TRIM(COALESCE(p.note, '')) = ''
          AND NOT EXISTS (SELECT 1 FROM post_photo ph WHERE ph.post_id = p.id)
          AND NOT EXISTS (SELECT 1 FROM post_tag pt WHERE pt.post_id = p.id AND pt.tag_id != :tagId)
        )
        """,
    )
    protected abstract suspend fun deleteEmptiedPosts(tagId: Long)

    @Query("SELECT COUNT(*) FROM tag WHERE group_id = :groupId")
    abstract suspend fun groupTagCount(groupId: Long): Int

    @Query("SELECT COUNT(DISTINCT pt.post_id) FROM post_tag pt JOIN tag t ON t.id = pt.tag_id WHERE t.group_id = :groupId")
    abstract suspend fun groupUsageCount(groupId: Long): Int

    /** Entries whose only content is tags from this group: no mood, text, photos or tags from elsewhere. */
    @Query(
        """
        SELECT COUNT(*)
        FROM post p
        WHERE p.id IN (SELECT pt.post_id FROM post_tag pt JOIN tag t ON t.id = pt.tag_id WHERE t.group_id = :groupId)
          AND p.mood_id IS NULL
          AND TRIM(COALESCE(p.title, '')) = ''
          AND TRIM(COALESCE(p.note, '')) = ''
          AND NOT EXISTS (SELECT 1 FROM post_photo ph WHERE ph.post_id = p.id)
          AND NOT EXISTS (
            SELECT 1 FROM post_tag pt JOIN tag t ON t.id = pt.tag_id WHERE pt.post_id = p.id AND t.group_id != :groupId
          )
        """,
    )
    abstract suspend fun groupEmptiedCount(groupId: Long): Int

    @Query(
        """
        DELETE FROM post WHERE id IN (
        SELECT p.id
        FROM post p
        WHERE p.id IN (SELECT pt.post_id FROM post_tag pt JOIN tag t ON t.id = pt.tag_id WHERE t.group_id = :groupId)
          AND p.mood_id IS NULL
          AND TRIM(COALESCE(p.title, '')) = ''
          AND TRIM(COALESCE(p.note, '')) = ''
          AND NOT EXISTS (SELECT 1 FROM post_photo ph WHERE ph.post_id = p.id)
          AND NOT EXISTS (
            SELECT 1 FROM post_tag pt JOIN tag t ON t.id = pt.tag_id WHERE pt.post_id = p.id AND t.group_id != :groupId
          )
        )
        """,
    )
    protected abstract suspend fun deleteGroupEmptiedPosts(groupId: Long)

    @Query("DELETE FROM tag_group WHERE id = :groupId")
    protected abstract suspend fun deleteGroupRow(groupId: Long)

    /**
     * Deletes the group and all its tags for good. The tags leave every entry (tag and post_tag
     * cascade, as do diary suggestions), and entries left empty are deleted.
     */
    @Transaction
    open suspend fun deleteGroup(groupId: Long) {
        deleteGroupEmptiedPosts(groupId)
        deleteGroupRow(groupId)
    }

    @Query("DELETE FROM tag WHERE id = :tagId")
    protected abstract suspend fun deleteTagRow(tagId: Long)

    /** Removes the tag from every entry (post_tag cascades), deleting entries it leaves empty. */
    @Transaction
    open suspend fun deleteTag(tagId: Long) {
        deleteEmptiedPosts(tagId)
        deleteTagRow(tagId)
    }
}
