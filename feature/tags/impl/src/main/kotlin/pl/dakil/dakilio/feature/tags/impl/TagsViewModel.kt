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
import pl.dakil.dakilio.core.model.TagUsage

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

    /** Counts the tag's entries, so the delete dialog can offer the right choices. */
    fun tagUsage(id: Long, onResult: (TagUsage) -> Unit) {
        viewModelScope.launch { onResult(repository.tagUsage(id)) }
    }

    /** Hides the tag from pickers; entries keep it, and it can be restored. */
    fun archiveTag(id: Long) = launch { repository.setTagArchived(id, true) }

    fun restoreTag(id: Long) = launch { repository.setTagArchived(id, false) }

    /** Removes the tag from every entry for good (and entries it leaves empty). */
    fun deleteTag(id: Long) = launch { repository.deleteTag(id) }

    private fun launch(block: suspend () -> Unit) {
        viewModelScope.launch { block() }
    }
}
