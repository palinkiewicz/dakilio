package pl.dakil.dakilio.core.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import kotlinx.coroutines.flow.Flow
import pl.dakil.dakilio.core.database.entity.PromptEntity

@Dao
interface PromptDao {
    @Query("SELECT * FROM prompt ORDER BY position, id")
    fun observeAll(): Flow<List<PromptEntity>>

    @Query("SELECT COUNT(*) FROM prompt")
    fun observeCount(): Flow<Int>

    @Query("SELECT COALESCE(MAX(position), -1) + 1 FROM prompt")
    suspend fun nextPosition(): Int

    @Insert
    suspend fun insert(prompt: PromptEntity): Long

    @Insert
    suspend fun insertAll(prompts: List<PromptEntity>)

    @Query("UPDATE prompt SET text = :text, levels = :levels WHERE id = :id")
    suspend fun update(id: Long, text: String, levels: Int)

    @Query("DELETE FROM prompt WHERE id = :id")
    suspend fun delete(id: Long)
}
