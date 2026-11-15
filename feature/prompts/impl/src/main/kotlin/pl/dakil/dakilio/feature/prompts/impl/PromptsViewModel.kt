package pl.dakil.dakilio.feature.prompts.impl

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
import pl.dakil.dakilio.core.data.repository.PromptRepository
import pl.dakil.dakilio.core.model.Mood
import pl.dakil.dakilio.core.model.Prompt
import pl.dakil.dakilio.core.model.PromptDraft
import pl.dakil.dakilio.core.model.Prompts
import pl.dakil.dakilio.core.model.primaries

/**
 * @property moods one mood per level, worst first: the faces a prompt is shown for.
 */
data class PromptsUiState(val prompts: List<Prompt>, val moods: List<Mood>) {
    /** The questions already there, for telling which starter prompts are still to add. */
    val keys: Set<String> = prompts.map { Prompts.key(it.text) }.toSet()
}

@HiltViewModel
class PromptsViewModel @Inject constructor(
    private val promptRepository: PromptRepository,
    moodRepository: MoodRepository,
) : ViewModel() {
    /** `null` while loading. */
    val state: StateFlow<PromptsUiState?> = combine(promptRepository.prompts, moodRepository.moods) { prompts, moods ->
        PromptsUiState(prompts, moods.primaries())
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    fun add(drafts: List<PromptDraft>) {
        viewModelScope.launch { promptRepository.add(drafts) }
    }

    fun update(id: Long, draft: PromptDraft) {
        viewModelScope.launch { promptRepository.update(id, draft) }
    }

    fun delete(prompt: Prompt) {
        viewModelScope.launch { promptRepository.delete(prompt.id) }
    }

    /** Undoes [delete]: the prompt comes back at the end of the list. */
    fun restore(prompt: Prompt) = add(listOf(PromptDraft(prompt.text, prompt.levels)))
}
