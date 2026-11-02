package pl.dakil.dakilio.feature.extras.impl

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import pl.dakil.dakilio.core.data.repository.TagRepository

/**
 * How each extra is used. Extras have no on/off switch: what they hold is their state, so an extra
 * with nothing set up is simply unused.
 *
 * @property coloredTags active tags showing a color (Tag colors).
 */
data class ExtrasUiState(val coloredTags: Int)

@HiltViewModel
class ExtrasViewModel @Inject constructor(tagRepository: TagRepository) : ViewModel() {
    /** `null` while loading. */
    val state: StateFlow<ExtrasUiState?> = tagRepository.coloredTagCount
        .map { ExtrasUiState(coloredTags = it) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)
}
