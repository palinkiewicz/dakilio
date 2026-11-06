package pl.dakil.dakilio.core.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow
import pl.dakil.dakilio.core.database.entity.PlannedReminderRow
import pl.dakil.dakilio.core.database.entity.ReminderEntity

/** The reminders (the Reminders extra), and what their schedule reads. */
@Dao
interface ReminderDao {
    @Query("SELECT * FROM reminder ORDER BY minute, id")
    fun observeAll(): Flow<List<ReminderEntity>>

    @Query("SELECT COUNT(*) FROM reminder")
    fun observeCount(): Flow<Int>

    @Query("SELECT * FROM reminder WHERE habit_id = :habitId ORDER BY minute, id")
    suspend fun forHabit(habitId: Long): List<ReminderEntity>

    @Insert
    suspend fun insert(reminder: ReminderEntity): Long

    @Insert
    suspend fun insertAll(reminders: List<ReminderEntity>)

    @Update
    suspend fun update(reminder: ReminderEntity)

    @Query("UPDATE reminder SET enabled = :enabled WHERE id = :id")
    suspend fun setEnabled(id: Long, enabled: Boolean)

    @Query("DELETE FROM reminder WHERE id = :id")
    suspend fun delete(id: Long)

    @Query("SELECT * FROM reminder WHERE id = :id")
    suspend fun get(id: Long): ReminderEntity?

    /** The reminders that are on, each with its diary, or its habit and the habit's tag. */
    @Query(
        """
        SELECT r.id, r.minute, r.days,
            d.id AS diaryId, d.name AS diaryName,
            h.id AS habitId, h.tag_id AS tagId, t.label AS label, h.days_per_week AS daysPerWeek
        FROM reminder r
        LEFT JOIN diary d ON d.id = r.diary_id
        LEFT JOIN habit h ON h.id = r.habit_id
        LEFT JOIN tag t ON t.id = h.tag_id
        WHERE r.enabled = 1
        """,
    )
    suspend fun planned(): List<PlannedReminderRow>

    /** Diaries with an entry on [day] (an epoch day). */
    @Query("SELECT DISTINCT diary_id FROM post WHERE local_date = :day")
    suspend fun diariesWithEntriesOn(day: Long): List<Long>
}
