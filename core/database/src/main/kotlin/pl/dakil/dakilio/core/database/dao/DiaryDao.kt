package pl.dakil.dakilio.core.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import kotlinx.coroutines.flow.Flow
import pl.dakil.dakilio.core.database.entity.DiaryEntity
import pl.dakil.dakilio.core.database.entity.DiaryPostCount
import pl.dakil.dakilio.core.database.entity.DiarySuggestedGroupCrossRef
import pl.dakil.dakilio.core.database.entity.PopulatedDiary

@Dao
abstract class DiaryDao {
    @Transaction
    @Query("SELECT * FROM diary ORDER BY position, id")
    abstract fun observeDiaries(): Flow<List<PopulatedDiary>>

    @Transaction
    @Query("SELECT * FROM diary WHERE id = :id")
    abstract suspend fun getDiary(id: Long): PopulatedDiary?

    @Query("SELECT diary_id AS diaryId, COUNT(*) AS count FROM post GROUP BY diary_id")
    abstract fun observePostCounts(): Flow<List<DiaryPostCount>>

    @Query("SELECT COALESCE(MAX(position), -1) + 1 FROM diary")
    abstract suspend fun nextPosition(): Int

    @Insert
    protected abstract suspend fun insert(diary: DiaryEntity): Long

    @Update
    protected abstract suspend fun update(diary: DiaryEntity)

    @Query("DELETE FROM diary_suggested_group WHERE diary_id = :diaryId")
    protected abstract suspend fun clearSuggestedGroups(diaryId: Long)

    @Insert
    protected abstract suspend fun insertSuggestedGroups(refs: List<DiarySuggestedGroupCrossRef>)

    @Transaction
    open suspend fun save(diary: DiaryEntity, suggestedGroupIds: Set<Long>): Long {
        val id = if (diary.id == 0L) insert(diary) else diary.id.also { update(diary) }
        clearSuggestedGroups(id)
        insertSuggestedGroups(suggestedGroupIds.map { DiarySuggestedGroupCrossRef(id, it) })
        return id
    }

    @Query("SELECT id FROM post WHERE diary_id = :diaryId")
    abstract suspend fun postIds(diaryId: Long): List<Long>

    @Query("UPDATE post SET diary_id = :to WHERE diary_id = :from")
    protected abstract suspend fun movePosts(from: Long, to: Long)

    @Query("DELETE FROM diary WHERE id = :diaryId")
    protected abstract suspend fun deleteDiary(diaryId: Long)

    /**
     * Deletes the diary. Its entries move to [moveEntriesTo] when given; otherwise the caller is
     * expected to have deleted them (and their photos) first.
     */
    @Transaction
    open suspend fun delete(diaryId: Long, moveEntriesTo: Long?) {
        if (moveEntriesTo != null) movePosts(diaryId, moveEntriesTo)
        deleteDiary(diaryId)
    }
}
