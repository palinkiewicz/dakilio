package pl.dakil.dakilio.feature.moods.impl

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import pl.dakil.dakilio.core.data.repository.MoodRepository
import pl.dakil.dakilio.core.model.Mood
import pl.dakil.dakilio.core.model.levelsBestFirst

@HiltViewModel
class MoodsViewModel @Inject constructor(private val moodRepository: MoodRepository) : ViewModel() {
    /** Each level's moods (primary first, then aliases), best level first. `null` while loading. */
    val levels: StateFlow<List<List<Mood>>?> = moodRepository.moods
        .map { it.levelsBestFirst() }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    fun rename(id: Long, label: String) {
        viewModelScope.launch { moodRepository.rename(id, label) }
    }

    fun addAlias(level: Int, label: String) {
        viewModelScope.launch { moodRepository.addAlias(level, label) }
    }

    /** Counts the alias's entries, so the delete dialog can offer the right choices. */
    fun usageCount(alias: Mood, onResult: (Int) -> Unit) {
        viewModelScope.launch { onResult(moodRepository.usageCount(alias.id)) }
    }

    fun deleteUnused(alias: Mood) {
        viewModelScope.launch { moodRepository.deleteUnusedAlias(alias.id) }
    }

    fun setArchived(alias: Mood, archived: Boolean) {
        viewModelScope.launch { moodRepository.setAliasArchived(alias.id, archived) }
    }

    fun replace(alias: Mood, target: Mood, onDone: (moved: Int) -> Unit) {
        viewModelScope.launch { onDone(moodRepository.replaceAlias(alias.id, target.id)) }
    }
}
