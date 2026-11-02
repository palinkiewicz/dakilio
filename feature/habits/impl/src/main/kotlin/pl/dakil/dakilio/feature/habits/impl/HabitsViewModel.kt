package pl.dakil.dakilio.feature.habits.impl

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import java.time.Clock
import java.time.LocalDate
import javax.inject.Inject
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import pl.dakil.dakilio.core.data.repository.HabitProgressEvents
import pl.dakil.dakilio.core.data.repository.HabitRepository
import pl.dakil.dakilio.core.data.repository.TagRepository
import pl.dakil.dakilio.core.model.Habit
import pl.dakil.dakilio.core.model.HabitBump
import pl.dakil.dakilio.core.model.HabitStatus
import pl.dakil.dakilio.core.model.TagGroup

/**
 * @property groups active groups with their active tags, for picking a new habit's tag.
 */
data class HabitsUiState(
    val habits: List<HabitStatus>,
    val groups: List<TagGroup>,
    val today: LocalDate,
) {
    val trackedTagIds: Set<Long> = habits.map { it.habit.tag.id }.toSet()
}

@HiltViewModel
class HabitsViewModel @Inject constructor(
    private val habitRepository: HabitRepository,
    tagRepository: TagRepository,
    private val clock: Clock,
) : ViewModel() {
    /** `null` while loading. */
    val state: StateFlow<HabitsUiState?> = combine(habitRepository.habits, tagRepository.groups) { habits, groups ->
        HabitsUiState(habits = habits, groups = groups, today = LocalDate.now(clock))
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    fun add(tagId: Long, daysPerWeek: Int) {
        viewModelScope.launch { habitRepository.addHabit(tagId, daysPerWeek) }
    }

    fun setDaysPerWeek(id: Long, daysPerWeek: Int) {
        viewModelScope.launch { habitRepository.setDaysPerWeek(id, daysPerWeek) }
    }

    fun delete(habit: Habit) {
        viewModelScope.launch { habitRepository.deleteHabit(habit.id) }
    }
}

/** Feeds the app-wide habit progress popup ([HabitProgressHost]). */
@HiltViewModel
class HabitProgressViewModel @Inject constructor(private val events: HabitProgressEvents) : ViewModel() {
    val bumps: StateFlow<List<HabitBump>> = events.bumps

    fun consume() = events.consume()
}
