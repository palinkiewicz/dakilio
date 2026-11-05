package pl.dakil.dakilio.core.data.repository

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import java.time.Clock
import java.time.LocalDate
import java.time.ZoneId
import javax.inject.Inject
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import pl.dakil.dakilio.core.database.DakilioDatabase
import pl.dakil.dakilio.core.database.TableChanges
import pl.dakil.dakilio.core.database.dao.HabitDao
import pl.dakil.dakilio.core.database.dao.ReminderDao
import pl.dakil.dakilio.core.model.DiaryReminder
import pl.dakil.dakilio.core.model.HabitReminder
import pl.dakil.dakilio.core.model.Habits
import pl.dakil.dakilio.core.model.Reminder
import pl.dakil.dakilio.core.model.ReminderSnapshot

internal class LocalReminderRepository @Inject constructor(
    @ApplicationContext private val context: Context,
    private val tableChanges: TableChanges,
    private val dao: ReminderDao,
    private val habitDao: HabitDao,
    private val settings: SettingsRepository,
    private val clock: Clock,
) : ReminderRepository {
    override suspend fun snapshot(): ReminderSnapshot? {
        if (!DakilioDatabase.exists(context)) return null
        // The zone is read each time: after a time zone change the injected clock keeps the old one.
        val today = LocalDate.now(clock.withZone(ZoneId.systemDefault()))
        val firstDay = settings.firstDayOfWeek.first()
        val diaries = dao.diaryReminders().map { DiaryReminder(it.diaryId, it.name, Reminder.ofMinute(true, it.minute).time) }
        val habits = dao.habitReminders().map {
            HabitReminder(it.habitId, it.tagId, it.label, it.daysPerWeek, Reminder.ofMinute(true, it.minute).time)
        }
        val writtenToday = if (diaries.isEmpty()) emptySet() else dao.diariesWithEntriesOn(today.toEpochDay()).toSet()
        val weekCounts = if (habits.isEmpty()) {
            emptyMap()
        } else {
            val start = Habits.weekStart(today, firstDay)
            habitDao.dayCounts(start.toEpochDay(), start.plusDays(6).toEpochDay())
        }
        return ReminderSnapshot(today, firstDay, diaries, habits, writtenToday, weekCounts)
    }

    // Room's own change tracking: no query runs until something touches these tables.
    override val changes: Flow<Unit> = combine(
        tableChanges.observe("diary", "habit", "tag", "post", "post_tag"),
        settings.firstDayOfWeek,
    ) { _, _ -> }
}
