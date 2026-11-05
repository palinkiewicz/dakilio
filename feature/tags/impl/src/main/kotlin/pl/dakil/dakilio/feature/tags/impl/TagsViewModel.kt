package pl.dakil.dakilio.feature.tags.impl

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import pl.dakil.dakilio.core.data.repository.OnboardingRepository
import pl.dakil.dakilio.core.data.repository.SettingsRepository
import pl.dakil.dakilio.core.data.repository.TagRepository
import pl.dakil.dakilio.core.model.GroupUsage
import pl.dakil.dakilio.core.model.SuggestedGroup
import pl.dakil.dakilio.core.model.TagGroup
import pl.dakil.dakilio.core.model.TagStyle
import pl.dakil.dakilio.core.model.TagUsage

@HiltViewModel
class TagsViewModel @Inject constructor(
    private val repository: TagRepository,
    private val onboarding: OnboardingRepository,
    settings: SettingsRepository,
) : ViewModel() {
    /** `null` while loading. */
    val groups: StateFlow<List<TagGroup>?> = repository.groups
        .map<List<TagGroup>, List<TagGroup>?> { it }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    /** The same style as the entry editor's tag picker. */
    val tagStyle: StateFlow<TagStyle> = settings.settings
        .map { it.tagStyle }
        .distinctUntilChanged()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), TagStyle.Icons)

    private val _reorderDraft = MutableStateFlow<List<TagGroup>?>(null)

    /**
     * The groups as being reordered, or `null` outside reorder mode. Drags change only this copy, so
     * they never fight the database's re-emissions; each drop saves the order.
     */
    val reorderDraft: StateFlow<List<TagGroup>?> = _reorderDraft.asStateFlow()

    fun startReorder() {
        _reorderDraft.value = groups.value ?: return
    }

    fun stopReorder() {
        _reorderDraft.value = null
    }

    fun moveGroup(from: Int, to: Int) = _reorderDraft.update { it?.moved(from, to) }

    fun moveTag(groupId: Long, from: Int, to: Int) = _reorderDraft.update { list ->
        list?.map { if (it.id == groupId) it.copy(tags = it.tags.moved(from, to)) else it }
    }

    fun saveGroupOrder() {
        val ids = _reorderDraft.value?.map { it.id } ?: return
        launch { repository.reorderGroups(ids) }
    }

    fun saveTagOrder(groupId: Long) {
        val ids = _reorderDraft.value?.firstOrNull { it.id == groupId }?.tags?.map { it.id } ?: return
        launch { repository.reorderTags(ids) }
    }

    /** The ready-made groups a fresh install starts with. */
    val suggestions: List<SuggestedGroup> get() = onboarding.suggestedGroups

    fun addSuggestedGroup(key: String) = launch { onboarding.addSuggestedGroup(key) }

    fun createGroup(name: String) = launch { repository.createGroup(name.trim()) }

    fun renameGroup(id: Long, name: String) = launch { repository.renameGroup(id, name.trim()) }

    /** Counts the entries using the group's tags, so the delete dialog can offer the right choices. */
    fun groupUsage(id: Long, onResult: (GroupUsage) -> Unit) {
        viewModelScope.launch { onResult(repository.groupUsage(id)) }
    }

    /** Hides the group from pickers; entries keep its tags, and it can be restored. */
    fun archiveGroup(id: Long) = launch { repository.setGroupArchived(id, true) }

    /** Deletes the group and its tags for good (and entries they leave empty). */
    fun deleteGroup(id: Long) = launch { repository.deleteGroup(id) }

    fun restoreGroup(id: Long) = launch { repository.setGroupArchived(id, false) }

    /** `null` clears the group's default color. */
    fun setGroupColor(id: Long, colorKey: String?) = launch { repository.setGroupColor(id, colorKey) }

    fun saveTag(id: Long?, groupId: Long, label: String, iconKey: String, colorKey: String?) =
        launch { repository.saveTag(id, groupId, label.trim(), iconKey, colorKey) }

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

private fun <T> List<T>.moved(from: Int, to: Int): List<T> =
    if (from !in indices || to !in indices) this else toMutableList().apply { add(to, removeAt(from)) }
