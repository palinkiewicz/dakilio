package pl.dakil.dakilio.core.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.MapColumn
import androidx.room.Query
import androidx.room.Transaction
import kotlinx.coroutines.flow.Flow
import pl.dakil.dakilio.core.database.entity.HabitDay
import pl.dakil.dakilio.core.database.entity.HabitEntity
import pl.dakil.dakilio.core.database.entity.PopulatedHabit

@Dao
abstract class HabitDao {
    @Transaction
    @Query("SELECT * FROM habit ORDER BY position, id")
    abstract fun observeHabits(): Flow<List<PopulatedHabit>>

    @Query("SELECT COUNT(*) FROM habit")
    abstract fun observeCount(): Flow<Int>

    /** Every day from [from] (an epoch day) on which a habit's tag was on an entry, in any diary. */
    @Query(
        """
        SELECT DISTINCT h.id AS habitId, p.local_date AS localDate
        FROM habit h
        JOIN post_tag pt ON pt.tag_id = h.tag_id
        JOIN post p ON p.id = pt.post_id
        WHERE p.local_date >= :from
        """,
    )
    abstract fun observeHabitDays(from: Long): Flow<List<HabitDay>>

    /** Days with the habit's tag between [from] and [to] (epoch days, inclusive), by habit id. */
    @Query(
        """
        SELECT h.id AS habitId, COUNT(DISTINCT p.local_date) AS days
        FROM habit h
        JOIN post_tag pt ON pt.tag_id = h.tag_id
        JOIN post p ON p.id = pt.post_id
        WHERE p.local_date BETWEEN :from AND :to
        GROUP BY h.id
        """,
    )
    abstract suspend fun dayCounts(from: Long, to: Long): Map<@MapColumn("habitId") Long, @MapColumn("days") Int>

    @Transaction
    @Query("SELECT * FROM habit ORDER BY position, id")
    abstract suspend fun habits(): List<PopulatedHabit>

    @Query("SELECT COALESCE(MAX(position), -1) + 1 FROM habit")
    abstract suspend fun nextPosition(): Int

    @Insert
    abstract suspend fun insert(habit: HabitEntity): Long

    @Query("UPDATE habit SET days_per_week = :days WHERE id = :id")
    abstract suspend fun setDaysPerWeek(id: Long, days: Int)

    @Query("DELETE FROM habit WHERE id = :id")
    abstract suspend fun delete(id: Long)
}
