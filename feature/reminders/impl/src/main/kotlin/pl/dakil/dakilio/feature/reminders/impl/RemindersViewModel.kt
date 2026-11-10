package pl.dakil.dakilio.feature.reminders.impl

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import pl.dakil.dakilio.core.data.repository.DiaryRepository
import pl.dakil.dakilio.core.data.repository.HabitRepository
import pl.dakil.dakilio.core.data.repository.ReminderChannels
import pl.dakil.dakilio.core.data.repository.ReminderRepository
import pl.dakil.dakilio.core.model.Diary
import pl.dakil.dakilio.core.model.Habit
import pl.dakil.dakilio.core.model.Reminder
import pl.dakil.dakilio.core.model.ReminderTarget

/** A reminder with its diary or habit, for showing it. Exactly one of them is set. */
data class ReminderItem(val reminder: Reminder, val diary: Diary? = null, val habit: Habit? = null)

/**
 * @property diaries and [habits] what a reminder can be about.
 */
data class RemindersUiState(
    val items: List<ReminderItem>,
    val diaries: List<Diary>,
    val habits: List<Habit>,
) {
    val anyEnabled: Boolean get() = items.any { it.reminder.enabled }

    /** Kinds of reminders that are on, as their notification channels (`ReminderChannels`). */
    val enabledChannels: Set<String>
        get() = items.filter { it.reminder.enabled }.mapTo(mutableSetOf()) { if (it.habit != null) ReminderChannels.HABIT else ReminderChannels.DIARY }
}

@HiltViewModel
class RemindersViewModel @Inject constructor(
    private val reminderRepository: ReminderRepository,
    diaryRepository: DiaryRepository,
    habitRepository: HabitRepository,
) : ViewModel() {
    /** `null` while loading. */
    val state: StateFlow<RemindersUiState?> = combine(
        reminderRepository.reminders,
        diaryRepository.diaries,
        habitRepository.habits,
    ) { reminders, diaries, statuses ->
        val habits = statuses.map { it.habit }
        val diaryById = diaries.associateBy { it.id }
        val habitById = habits.associateBy { it.id }
        RemindersUiState(
            // A reminder whose diary or habit is going (the cascade is a moment behind) is left out.
            items = reminders.mapNotNull { reminder ->
                when (val target = reminder.target) {
                    is ReminderTarget.Diary -> diaryById[target.id]?.let { ReminderItem(reminder, diary = it) }
                    is ReminderTarget.Habit -> habitById[target.id]?.let { ReminderItem(reminder, habit = it) }
                }
            },
            diaries = diaries,
            habits = habits,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    /** Adds [reminder] when it's new (id 0), otherwise saves the changes. */
    fun save(reminder: Reminder) {
        viewModelScope.launch {
            if (reminder.id == 0L) reminderRepository.add(reminder) else reminderRepository.update(reminder)
        }
    }

    fun setEnabled(reminder: Reminder, enabled: Boolean) {
        viewModelScope.launch { reminderRepository.setEnabled(reminder.id, enabled) }
    }

    fun delete(reminder: Reminder) {
        viewModelScope.launch { reminderRepository.delete(reminder.id) }
    }

    /** Undoes [delete]: the reminder comes back under a new id. */
    fun restore(reminder: Reminder) {
        viewModelScope.launch { reminderRepository.add(reminder) }
    }
}
