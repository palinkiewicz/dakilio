package pl.dakil.dakilio.core.data.repository

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import java.time.Clock
import java.time.LocalDate
import java.time.ZoneId
import javax.inject.Inject
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import pl.dakil.dakilio.core.database.DakilioDatabase
import pl.dakil.dakilio.core.database.TableChanges
import pl.dakil.dakilio.core.database.dao.HabitDao
import pl.dakil.dakilio.core.database.dao.ReminderDao
import pl.dakil.dakilio.core.database.entity.PlannedReminderRow
import pl.dakil.dakilio.core.database.entity.ReminderEntity
import pl.dakil.dakilio.core.model.Habits
import pl.dakil.dakilio.core.model.PlannedReminder
import pl.dakil.dakilio.core.model.Reminder
import pl.dakil.dakilio.core.model.ReminderDays
import pl.dakil.dakilio.core.model.ReminderSnapshot
import pl.dakil.dakilio.core.model.ReminderSubject
import pl.dakil.dakilio.core.model.ReminderTarget

internal class LocalReminderRepository @Inject constructor(
    @ApplicationContext private val context: Context,
    private val tableChanges: TableChanges,
    private val dao: ReminderDao,
    private val habitDao: HabitDao,
    private val settings: SettingsRepository,
    private val clock: Clock,
) : ReminderRepository {
    override val reminders: Flow<List<Reminder>> = dao.observeAll().map { list -> list.mapNotNull { it.asModel() } }

    override val count: Flow<Int> = dao.observeCount().distinctUntilChanged()

    override suspend fun add(reminder: Reminder): Long = dao.insert(reminder.asEntity(id = 0, createdAt = clock.millis()))

    override suspend fun update(reminder: Reminder) {
        val existing = dao.get(reminder.id) ?: return
        dao.update(reminder.asEntity(id = existing.id, createdAt = existing.createdAt))
    }

    override suspend fun setEnabled(id: Long, enabled: Boolean) = dao.setEnabled(id, enabled)

    override suspend fun delete(id: Long) = dao.delete(id)

    override suspend fun forHabit(habitId: Long): List<Reminder> = dao.forHabit(habitId).mapNotNull { it.asModel() }

    override suspend fun snapshot(date: LocalDate?): ReminderSnapshot? {
        if (!DakilioDatabase.exists(context)) return null
        // The zone is read each time: after a time zone change the injected clock keeps the old one.
        val today = date ?: LocalDate.now(clock.withZone(ZoneId.systemDefault()))
        val firstDay = settings.firstDayOfWeek.first()
        val reminders = dao.planned().mapNotNull { it.asModel() }
        val writtenToday = if (reminders.none { it.subject is ReminderSubject.Diary }) {
            emptySet()
        } else {
            dao.diariesWithEntriesOn(today.toEpochDay()).toSet()
        }
        val weekCounts = if (reminders.none { it.subject is ReminderSubject.Habit }) {
            emptyMap()
        } else {
            val start = Habits.weekStart(today, firstDay)
            habitDao.dayCounts(start.toEpochDay(), start.plusDays(6).toEpochDay())
        }
        return ReminderSnapshot(today, firstDay, reminders, writtenToday, weekCounts)
    }

    // Room's own change tracking: no query runs until something touches these tables.
    override val changes: Flow<Unit> = combine(
        tableChanges.observe("reminder", "diary", "habit", "tag", "post", "post_tag"),
        settings.firstDayOfWeek,
    ) { _, _ -> }
}

internal fun ReminderEntity.asModel(): Reminder? {
    val target = diaryId?.let(ReminderTarget::Diary) ?: habitId?.let(ReminderTarget::Habit) ?: return null
    return Reminder(id, target, Reminder.timeOf(minute), ReminderDays.fromMask(days), enabled)
}

internal fun Reminder.asEntity(id: Long = this.id, createdAt: Long) = ReminderEntity(
    id = id,
    diaryId = (target as? ReminderTarget.Diary)?.id,
    habitId = (target as? ReminderTarget.Habit)?.id,
    minute = minuteOfDay,
    days = ReminderDays.toMask(days),
    enabled = enabled,
    createdAt = createdAt,
)

private fun PlannedReminderRow.asModel(): PlannedReminder? {
    val diaryId = diaryId
    val habitId = habitId
    val tagId = tagId
    val subject = when {
        diaryId != null -> ReminderSubject.Diary(diaryId, diaryName.orEmpty())
        habitId != null && tagId != null -> ReminderSubject.Habit(habitId, tagId, label.orEmpty(), daysPerWeek ?: Habits.DEFAULT_DAYS)
        else -> return null
    }
    return PlannedReminder(id, subject, Reminder.timeOf(minute), ReminderDays.fromMask(days))
}
