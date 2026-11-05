package pl.dakil.dakilio.core.database.dao

import androidx.room.Dao
import androidx.room.Query
import pl.dakil.dakilio.core.database.entity.DiaryReminderRow
import pl.dakil.dakilio.core.database.entity.HabitReminderRow

/** What the reminder schedule reads: the reminders that are on, and today's entries. */
@Dao
interface ReminderDao {
    @Query("SELECT id AS diaryId, name, reminder_minute AS minute FROM diary WHERE reminder_on = 1")
    suspend fun diaryReminders(): List<DiaryReminderRow>

    @Query(
        """
        SELECT h.id AS habitId, h.tag_id AS tagId, t.label AS label, h.days_per_week AS daysPerWeek,
            h.reminder_minute AS minute
        FROM habit h JOIN tag t ON t.id = h.tag_id
        WHERE h.reminder_on = 1
        """,
    )
    suspend fun habitReminders(): List<HabitReminderRow>

    /** Diaries with an entry on [day] (an epoch day). */
    @Query("SELECT DISTINCT diary_id FROM post WHERE local_date = :day")
    suspend fun diariesWithEntriesOn(day: Long): List<Long>
}
