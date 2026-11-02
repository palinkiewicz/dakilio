package pl.dakil.dakilio.feature.extras.impl

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import pl.dakil.dakilio.core.data.repository.HabitRepository
import pl.dakil.dakilio.core.data.repository.TagRepository

/**
 * How each extra is used. Extras have no on/off switch: what they hold is their state, so an extra
 * with nothing set up is simply unused.
 *
 * @property coloredTags active tags showing a color (Tag colors).
 * @property habits habits set up (Habits).
 */
data class ExtrasUiState(val coloredTags: Int, val habits: Int)

@HiltViewModel
class ExtrasViewModel @Inject constructor(
    tagRepository: TagRepository,
    habitRepository: HabitRepository,
) : ViewModel() {
    /** `null` while loading. */
    val state: StateFlow<ExtrasUiState?> =
        combine(tagRepository.coloredTagCount, habitRepository.habitCount) { colored, habits ->
            ExtrasUiState(coloredTags = colored, habits = habits)
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)
}
