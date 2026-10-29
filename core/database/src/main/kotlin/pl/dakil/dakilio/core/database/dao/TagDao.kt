package pl.dakil.dakilio.core.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import kotlinx.coroutines.flow.Flow
import pl.dakil.dakilio.core.database.entity.PopulatedTagGroup
import pl.dakil.dakilio.core.database.entity.TagEntity
import pl.dakil.dakilio.core.database.entity.TagGroupEntity

@Dao
interface TagDao {
    /** Active groups with all their tags (archived tags are filtered by the repository). */
    @Transaction
    @Query("SELECT * FROM tag_group WHERE is_archived = 0 ORDER BY position, id")
    fun observeActiveGroups(): Flow<List<PopulatedTagGroup>>

    @Query("SELECT COALESCE(MAX(position), -1) + 1 FROM tag_group")
    suspend fun nextGroupPosition(): Int

    @Query("SELECT COALESCE(MAX(position), -1) + 1 FROM tag WHERE group_id = :groupId")
    suspend fun nextTagPosition(groupId: Long): Int

    @Insert
    suspend fun insertGroup(group: TagGroupEntity): Long

    @Query("UPDATE tag_group SET name = :name WHERE id = :id")
    suspend fun renameGroup(id: Long, name: String)

    @Query("UPDATE tag_group SET is_archived = :archived WHERE id = :id")
    suspend fun setGroupArchived(id: Long, archived: Boolean)

    @Insert
    suspend fun insertTag(tag: TagEntity): Long

    @Update
    suspend fun updateTag(tag: TagEntity)

    @Query("SELECT * FROM tag WHERE id = :id")
    suspend fun getTag(id: Long): TagEntity?

    @Query("UPDATE tag SET is_archived = :archived WHERE id = :id")
    suspend fun setTagArchived(id: Long, archived: Boolean)
}
