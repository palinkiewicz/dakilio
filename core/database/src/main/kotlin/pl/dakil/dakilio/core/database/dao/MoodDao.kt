package pl.dakil.dakilio.core.database.dao

import androidx.room.Dao
import androidx.room.Query
import kotlinx.coroutines.flow.Flow
import pl.dakil.dakilio.core.database.entity.MoodEntity

@Dao
interface MoodDao {
    @Query(
        """
        SELECT mood.* FROM mood
        JOIN mood_set ON mood.mood_set_id = mood_set.id
        WHERE mood_set.is_active = 1
        ORDER BY mood.tier
        """,
    )
    fun observeActiveMoods(): Flow<List<MoodEntity>>
}
