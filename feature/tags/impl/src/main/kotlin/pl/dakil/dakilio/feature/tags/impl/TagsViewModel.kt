package pl.dakil.dakilio.feature.tags.impl

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import pl.dakil.dakilio.core.data.repository.TagRepository
import pl.dakil.dakilio.core.model.TagGroup

@HiltViewModel
class TagsViewModel @Inject constructor(private val repository: TagRepository) : ViewModel() {
    /** `null` while loading. */
    val groups: StateFlow<List<TagGroup>?> = repository.groups
        .map<List<TagGroup>, List<TagGroup>?> { it }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    fun createGroup(name: String) = launch { repository.createGroup(name.trim()) }

    fun renameGroup(id: Long, name: String) = launch { repository.renameGroup(id, name.trim()) }

    /** Deleting archives, so entries keep their tags and the action can be undone. */
    fun deleteGroup(id: Long) = launch { repository.setGroupArchived(id, true) }

    fun restoreGroup(id: Long) = launch { repository.setGroupArchived(id, false) }

    fun saveTag(id: Long?, groupId: Long, label: String, iconKey: String) =
        launch { repository.saveTag(id, groupId, label.trim(), iconKey) }

    fun deleteTag(id: Long) = launch { repository.setTagArchived(id, true) }

    fun restoreTag(id: Long) = launch { repository.setTagArchived(id, false) }

    private fun launch(block: suspend () -> Unit) {
        viewModelScope.launch { block() }
    }
}
