package pl.dakil.dakilio.core.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Transaction
import kotlinx.coroutines.flow.Flow
import pl.dakil.dakilio.core.database.entity.TagKeywordEntity

@Dao
abstract class KeywordDao {
    @Query("SELECT * FROM tag_keyword ORDER BY tag_id, position, id")
    abstract fun observeAll(): Flow<List<TagKeywordEntity>>

    @Insert
    protected abstract suspend fun insertAll(keywords: List<TagKeywordEntity>)

    @Query("DELETE FROM tag_keyword WHERE tag_id = :tagId")
    abstract suspend fun deleteForTag(tagId: Long)

    /** Replaces every keyword of [tagId] with [keywords] (in order). */
    @Transaction
    open suspend fun replaceForTag(tagId: Long, keywords: List<TagKeywordEntity>) {
        deleteForTag(tagId)
        insertAll(keywords.mapIndexed { i, it -> it.copy(id = 0, tagId = tagId, position = i) })
    }
}
