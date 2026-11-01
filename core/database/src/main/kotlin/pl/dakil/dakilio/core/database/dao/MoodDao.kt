package pl.dakil.dakilio.core.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Transaction
import kotlinx.coroutines.flow.Flow
import pl.dakil.dakilio.core.database.entity.MoodEntity

@Dao
abstract class MoodDao {
    @Query("SELECT * FROM mood WHERE is_archived = 0 ORDER BY level, is_primary DESC, position, id")
    abstract fun observeActiveMoods(): Flow<List<MoodEntity>>

    @Query("SELECT * FROM mood WHERE id = :id")
    abstract suspend fun get(id: Long): MoodEntity?

    @Query("SELECT COUNT(*) FROM post WHERE mood_id = :id")
    abstract suspend fun usageCount(id: Long): Int

    @Query("SELECT COALESCE(MAX(position), -1) FROM mood WHERE level = :level AND is_primary = 0")
    abstract suspend fun maxAliasPosition(level: Int): Int

    @Insert
    abstract suspend fun insert(mood: MoodEntity): Long

    @Query("UPDATE mood SET label = :label WHERE id = :id")
    abstract suspend fun rename(id: Long, label: String)

    @Query("UPDATE mood SET is_archived = :archived WHERE id = :id AND is_primary = 0")
    abstract suspend fun setArchived(id: Long, archived: Boolean)

    @Query("DELETE FROM mood WHERE id = :id AND is_primary = 0")
    abstract suspend fun deleteAlias(id: Long)

    @Query("UPDATE post SET mood_id = :to WHERE mood_id = :from")
    protected abstract suspend fun reassignPosts(from: Long, to: Long)

    /** Moves every entry of alias [from] to mood [to], then deletes the alias. Returns the moved count. */
    @Transaction
    open suspend fun replaceAlias(from: Long, to: Long): Int {
        val count = usageCount(from)
        reassignPosts(from, to)
        deleteAlias(from)
        return count
    }
}
