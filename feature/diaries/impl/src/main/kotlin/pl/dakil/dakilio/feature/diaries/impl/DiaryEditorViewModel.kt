package pl.dakil.dakilio.feature.diaries.impl

import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.foundation.text.input.setTextAndPlaceCursorAtEnd
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.assisted.Assisted
import dagger.assisted.AssistedFactory
import dagger.assisted.AssistedInject
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import pl.dakil.dakilio.core.data.repository.DiaryRepository
import pl.dakil.dakilio.core.data.repository.TagRepository
import pl.dakil.dakilio.core.model.Diary
import pl.dakil.dakilio.core.model.Reminder
import pl.dakil.dakilio.core.model.TagGroup
import pl.dakil.dakilio.feature.diaries.api.DiaryEditorKey

internal data class DiaryForm(
    val loaded: Boolean = false,
    val iconKey: String = DEFAULT_DIARY_ICON,
    val suggestedGroupIds: Set<Long> = emptySet(),
    val reminder: Reminder = Reminder(),
    val finished: Boolean = false,
)

data class DiaryEditorUiState(
    val isNew: Boolean,
    val iconKey: String,
    val groups: List<TagGroup>,
    val suggestedGroupIds: Set<Long>,
    val reminder: Reminder,
    /** Diaries entries could be moved to when this one is deleted. */
    val otherDiaries: List<Diary>,
    val finished: Boolean,
)

internal const val DEFAULT_DIARY_ICON = "book"

@HiltViewModel(assistedFactory = DiaryEditorViewModel.Factory::class)
class DiaryEditorViewModel @AssistedInject constructor(
    @Assisted private val key: DiaryEditorKey,
    private val diaryRepository: DiaryRepository,
    tagRepository: TagRepository,
) : ViewModel() {

    @AssistedFactory
    interface Factory {
        fun create(key: DiaryEditorKey): DiaryEditorViewModel
    }

    val name = TextFieldState()
    private val form = MutableStateFlow(DiaryForm())

    val uiState: StateFlow<DiaryEditorUiState?> =
        combine(form, tagRepository.groups, diaryRepository.diaries) { form, groups, diaries ->
            if (!form.loaded) return@combine null
            DiaryEditorUiState(
                isNew = key.diaryId == null,
                iconKey = form.iconKey,
                groups = groups,
                suggestedGroupIds = form.suggestedGroupIds,
                reminder = form.reminder,
                otherDiaries = diaries.filter { it.id != key.diaryId },
                finished = form.finished,
            )
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    init {
        viewModelScope.launch {
            val existing = key.diaryId?.let { diaryRepository.getDiary(it) }
            if (existing != null) {
                name.setTextAndPlaceCursorAtEnd(existing.name)
                form.value = DiaryForm(
                    loaded = true,
                    iconKey = existing.iconKey,
                    suggestedGroupIds = existing.suggestedGroupIds,
                    reminder = existing.reminder,
                )
            } else {
                // A new diary suggests nothing by default: every group stays collapsed until chosen.
                form.value = DiaryForm(loaded = true)
            }
        }
    }

    fun setIcon(iconKey: String) = form.update { it.copy(iconKey = iconKey) }

    fun setReminder(reminder: Reminder) = form.update { it.copy(reminder = reminder) }

    fun toggleGroup(id: Long) = form.update {
        it.copy(suggestedGroupIds = if (id in it.suggestedGroupIds) it.suggestedGroupIds - id else it.suggestedGroupIds + id)
    }

    fun save() {
        val current = form.value
        val title = name.text.toString().trim()
        if (title.isEmpty() || current.finished) return
        viewModelScope.launch {
            diaryRepository.saveDiary(key.diaryId, title, current.iconKey, current.suggestedGroupIds, current.reminder)
            form.update { it.copy(finished = true) }
        }
    }

    suspend fun entryCount(): Int = key.diaryId?.let { diaryRepository.postCount(it) } ?: 0

    /** [moveTo] null deletes the diary's entries along with it. */
    fun delete(moveTo: Long?) {
        val id = key.diaryId ?: return
        viewModelScope.launch {
            if (diaryRepository.diaries.first().size <= 1) return@launch
            diaryRepository.deleteDiary(id, moveTo)
            form.update { it.copy(finished = true) }
        }
    }
}
