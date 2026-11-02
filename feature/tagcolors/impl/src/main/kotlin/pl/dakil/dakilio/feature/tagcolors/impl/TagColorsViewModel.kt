package pl.dakil.dakilio.feature.tagcolors.impl

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import pl.dakil.dakilio.core.data.repository.TagRepository
import pl.dakil.dakilio.core.model.TagGroup

@HiltViewModel
class TagColorsViewModel @Inject constructor(private val tagRepository: TagRepository) : ViewModel() {
    /** Active groups with their active tags; `null` while loading. */
    val groups: StateFlow<List<TagGroup>?> = tagRepository.groups
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    /** `null` clears the group's default color. */
    fun setGroupColor(groupId: Long, colorKey: String?) {
        viewModelScope.launch { tagRepository.setGroupColor(groupId, colorKey) }
    }

    /** `null` makes the tag use its group's color again. */
    fun setTagColor(tagId: Long, colorKey: String?) {
        viewModelScope.launch { tagRepository.setTagColor(tagId, colorKey) }
    }
}
