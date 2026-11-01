package pl.dakil.dakilio.feature.archive.impl

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import pl.dakil.dakilio.core.data.repository.MoodRepository
import pl.dakil.dakilio.core.data.repository.TagRepository
import pl.dakil.dakilio.core.model.GroupUsage
import pl.dakil.dakilio.core.model.Mood
import pl.dakil.dakilio.core.model.TagGroup
import pl.dakil.dakilio.core.model.TagUsage
import pl.dakil.dakilio.core.model.levelsBestFirst

/**
 * @property moods archived mood aliases, worst level first.
 * @property groups archived tag groups with all their tags.
 * @property tags active groups holding archived tags, each with only those tags.
 * @property activeMoods pickable moods, best level first: where an archived alias's entries can go.
 */
data class ArchiveUiState(
    val moods: List<Mood>,
    val groups: List<TagGroup>,
    val tags: List<TagGroup>,
    val activeMoods: List<Mood>,
) {
    val isEmpty get() = moods.isEmpty() && groups.isEmpty() && tags.isEmpty()
}

@HiltViewModel
class ArchiveViewModel @Inject constructor(
    private val moodRepository: MoodRepository,
    private val tagRepository: TagRepository,
) : ViewModel() {
    /** `null` while loading. */
    val state: StateFlow<ArchiveUiState?> = combine(
        moodRepository.archivedMoods,
        moodRepository.moods,
        tagRepository.archived,
    ) { archivedMoods, moods, archivedTags ->
        ArchiveUiState(
            moods = archivedMoods,
            groups = archivedTags.groups,
            tags = archivedTags.tags,
            activeMoods = moods.levelsBestFirst().flatten(),
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    fun setMoodArchived(mood: Mood, archived: Boolean) = launch { moodRepository.setAliasArchived(mood.id, archived) }

    fun moodUsage(mood: Mood, onResult: (Int) -> Unit) {
        viewModelScope.launch { onResult(moodRepository.usageCount(mood.id)) }
    }

    fun deleteUnusedMood(mood: Mood) = launch { moodRepository.deleteUnusedAlias(mood.id) }

    fun replaceMood(alias: Mood, target: Mood, onDone: (moved: Int) -> Unit) {
        viewModelScope.launch { onDone(moodRepository.replaceAlias(alias.id, target.id)) }
    }

    fun setGroupArchived(id: Long, archived: Boolean) = launch { tagRepository.setGroupArchived(id, archived) }

    fun groupUsage(id: Long, onResult: (GroupUsage) -> Unit) {
        viewModelScope.launch { onResult(tagRepository.groupUsage(id)) }
    }

    fun deleteGroup(id: Long) = launch { tagRepository.deleteGroup(id) }

    fun setTagArchived(id: Long, archived: Boolean) = launch { tagRepository.setTagArchived(id, archived) }

    fun tagUsage(id: Long, onResult: (TagUsage) -> Unit) {
        viewModelScope.launch { onResult(tagRepository.tagUsage(id)) }
    }

    fun deleteTag(id: Long) = launch { tagRepository.deleteTag(id) }

    private fun launch(block: suspend () -> Unit) {
        viewModelScope.launch { block() }
    }
}
