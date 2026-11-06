package pl.dakil.dakilio.core.data.repository

import java.time.Clock
import java.time.LocalDate
import javax.inject.Inject
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.flowOn
import pl.dakil.dakilio.core.common.DakilioDispatchers
import pl.dakil.dakilio.core.common.Dispatcher
import pl.dakil.dakilio.core.database.dao.HabitDao
import pl.dakil.dakilio.core.database.dao.TagDao
import pl.dakil.dakilio.core.database.entity.HabitEntity
import pl.dakil.dakilio.core.database.entity.PopulatedHabit
import pl.dakil.dakilio.core.model.Habit
import pl.dakil.dakilio.core.model.HabitStatus
import pl.dakil.dakilio.core.model.Habits

internal class LocalHabitRepository @Inject constructor(
    private val dao: HabitDao,
    private val tagDao: TagDao,
    private val settings: SettingsRepository,
    private val clock: Clock,
    @Dispatcher(DakilioDispatchers.Default) private val default: CoroutineDispatcher,
) : HabitRepository {
    @OptIn(ExperimentalCoroutinesApi::class)
    override val habits: Flow<List<HabitStatus>> =
        // "This week" is read whenever the flow starts (so a screen opened next week sees next week)
        // and whenever the first day of the week changes.
        settings.firstDayOfWeek.map { Habits.weekStart(LocalDate.now(clock), it) }.distinctUntilChanged().flatMapLatest { thisWeek ->
            val lastWeek = thisWeek.minusWeeks(1)
            combine(
                dao.observeHabits(),
                dao.observeHabitDays(lastWeek.toEpochDay()),
                tagDao.observeGroupColors(),
            ) { habits, days, groupColors ->
                val daysByHabit = days.groupBy({ it.habitId }, { LocalDate.ofEpochDay(it.localDate) })
                habits.map { populated ->
                    val habitDays = daysByHabit[populated.habit.id].orEmpty()
                    HabitStatus(
                        habit = populated.asModel(groupColors),
                        thisWeekDays = habitDays.filter { it >= thisWeek }.toSet(),
                        lastWeekCount = habitDays.count { it < thisWeek },
                    )
                }
            }
        }.flowOn(default)

    override val habitCount: Flow<Int> = dao.observeCount().distinctUntilChanged()

    override suspend fun habitList(): List<Habit> {
        val groupColors = tagDao.observeGroupColors().first()
        return dao.habits().map { it.asModel(groupColors) }
    }

    override suspend fun weekCounts(date: LocalDate): Map<Long, Int> {
        val start = Habits.weekStart(date, settings.firstDayOfWeek.first())
        return dao.dayCounts(start.toEpochDay(), start.plusDays(6).toEpochDay())
    }

    override suspend fun addHabit(tagId: Long, daysPerWeek: Int): Long = dao.insert(
        HabitEntity(
            tagId = tagId,
            daysPerWeek = daysPerWeek.coerceIn(Habits.DAYS_RANGE),
            position = dao.nextPosition(),
            createdAt = clock.millis(),
        ),
    )

    override suspend fun updateHabit(id: Long, daysPerWeek: Int) = dao.update(id, daysPerWeek.coerceIn(Habits.DAYS_RANGE))

    override suspend fun deleteHabit(id: Long) = dao.delete(id)
}

private fun PopulatedHabit.asModel(groupColors: Map<Long, String?>) = Habit(
    id = habit.id,
    tag = tag.asModel(groupColors[tag.groupId]),
    daysPerWeek = habit.daysPerWeek,
    position = habit.position,
)
