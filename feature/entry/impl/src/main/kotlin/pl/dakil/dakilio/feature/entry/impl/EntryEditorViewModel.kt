package pl.dakil.dakilio.feature.entry.impl

import android.net.Uri
import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.foundation.text.input.setTextAndPlaceCursorAtEnd
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.assisted.Assisted
import dagger.assisted.AssistedFactory
import dagger.assisted.AssistedInject
import dagger.hilt.android.lifecycle.HiltViewModel
import java.time.Clock
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.temporal.ChronoUnit
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import pl.dakil.dakilio.core.common.ApplicationScope
import pl.dakil.dakilio.core.data.repository.DiaryRepository
import pl.dakil.dakilio.core.data.repository.HabitProgressEvents
import pl.dakil.dakilio.core.data.repository.HabitRepository
import pl.dakil.dakilio.core.data.repository.MoodRepository
import pl.dakil.dakilio.core.data.repository.PhotoRepository
import pl.dakil.dakilio.core.data.repository.PostRepository
import pl.dakil.dakilio.core.data.repository.SettingsRepository
import pl.dakil.dakilio.core.data.repository.TagRepository
import pl.dakil.dakilio.core.model.Diary
import pl.dakil.dakilio.core.model.Habits
import pl.dakil.dakilio.core.model.Mood
import pl.dakil.dakilio.core.model.MoodDisplayOrder
import pl.dakil.dakilio.core.model.PostDraft
import pl.dakil.dakilio.core.model.Tag
import pl.dakil.dakilio.core.model.TagGroup
import pl.dakil.dakilio.core.model.TagStyle
import pl.dakil.dakilio.feature.entry.api.EntryEditorKey

enum class EditorStep { Mood, Tags, Note }

data class EditorPhoto(val fileName: String, val path: String)

/** The parts of an entry the user edits (text lives in [EntryEditorViewModel.title]/[note]). */
internal data class EditorForm(
    val loaded: Boolean = false,
    val dateTime: LocalDateTime = LocalDateTime.MIN,
    val step: EditorStep = EditorStep.Mood,
    val moodId: Long? = null,
    /** The saved entry's own mood, kept pickable even if it has since been archived. */
    val ownMood: Mood? = null,
    /** Set once loaded: an entry is always in exactly one diary. */
    val diaryId: Long? = null,
    val tagIds: Set<Long> = emptySet(),
    /** The saved entry's own tags, so archived ones still show among the selected tags. */
    val ownTags: List<Tag> = emptyList(),
    val photos: List<EditorPhoto> = emptyList(),
    val importingPhotos: Int = 0,
    val dirty: Boolean = false,
    val finished: Boolean = false,
)

data class EditorUiState(
    val loaded: Boolean,
    val isNew: Boolean,
    val dateTime: LocalDateTime,
    val today: LocalDate,
    val step: EditorStep,
    val moods: List<Mood>,
    val moodId: Long?,
    val diaries: List<Diary>,
    val diaryId: Long?,
    /** Groups suggested by the entry's diary (all groups when none are suggested). */
    val suggestedGroups: List<TagGroup>,
    val otherGroups: List<TagGroup>,
    val tagIds: Set<Long>,
    /** The selected tags in group order, followed by archived ones the entry already had. */
    val selectedTags: List<Tag>,
    val tagStyle: TagStyle,
    val photos: List<EditorPhoto>,
    val importingPhotos: Int,
    val dirty: Boolean,
    val finished: Boolean,
)

@HiltViewModel(assistedFactory = EntryEditorViewModel.Factory::class)
class EntryEditorViewModel @AssistedInject constructor(
    @Assisted private val key: EntryEditorKey,
    private val postRepository: PostRepository,
    private val diaryRepository: DiaryRepository,
    tagRepository: TagRepository,
    moodRepository: MoodRepository,
    private val photoRepository: PhotoRepository,
    private val settingsRepository: SettingsRepository,
    private val habitRepository: HabitRepository,
    private val habitProgressEvents: HabitProgressEvents,
    private val clock: Clock,
    @ApplicationScope private val applicationScope: CoroutineScope,
) : ViewModel() {

    @AssistedFactory
    interface Factory {
        fun create(key: EntryEditorKey): EntryEditorViewModel
    }

    val title = TextFieldState()
    val note = TextFieldState()
    private var originalTitle = ""
    private var originalNote = ""

    private val form = MutableStateFlow(EditorForm())

    /** Files imported during this session; deleted again unless the entry gets saved. */
    private val sessionFiles = mutableSetOf<String>()

    val uiState: StateFlow<EditorUiState?> =
        combine(
            form,
            moodRepository.moods,
            diaryRepository.diaries,
            tagRepository.groups,
            settingsRepository.settings,
        ) { form, moods, diaries, groups, settings ->
            if (!form.loaded) return@combine null
            val suggestedIds = diaries.firstOrNull { it.id == form.diaryId }?.suggestedGroupIds.orEmpty()
            val (suggested, other) = if (suggestedIds.isEmpty()) groups to emptyList() else groups.partition { it.id in suggestedIds }
            EditorUiState(
                loaded = true,
                isNew = key.postId == null,
                dateTime = form.dateTime,
                today = LocalDate.now(clock),
                step = form.step,
                moods = form.ownMood?.takeIf { own -> moods.none { it.id == own.id } }
                    ?.let { (moods + it).sortedWith(MoodDisplayOrder) }
                    ?: moods,
                moodId = form.moodId,
                diaries = diaries,
                diaryId = form.diaryId,
                suggestedGroups = suggested,
                otherGroups = other,
                tagIds = form.tagIds,
                selectedTags = selectedTags(groups, form),
                tagStyle = settings.tagStyle,
                photos = form.photos,
                importingPhotos = form.importingPhotos,
                dirty = form.dirty,
                finished = form.finished,
            )
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    init {
        viewModelScope.launch { load() }
    }

    private suspend fun load() {
        val diaries = diaryRepository.diaries.first()
        val existing = key.postId?.let { postRepository.getPost(it) }
        if (existing != null) {
            originalTitle = existing.title.orEmpty()
            originalNote = existing.note.orEmpty()
            title.setTextAndPlaceCursorAtEnd(originalTitle)
            note.setTextAndPlaceCursorAtEnd(originalNote)
            form.value = EditorForm(
                loaded = true,
                dateTime = existing.dateTime,
                moodId = existing.mood?.id,
                ownMood = existing.mood,
                diaryId = existing.diaryId,
                tagIds = existing.tags.map { it.id }.toSet(),
                ownTags = existing.tags,
                photos = existing.photos.map { EditorPhoto(it.fileName, it.path) },
            )
            return
        }
        val now = LocalDateTime.now(clock).truncatedTo(ChronoUnit.MINUTES)
        val known = diaries.map { it.id }.toSet()
        val diaryId = key.presetDiaryId?.takeIf { it in known }
            ?: settingsRepository.settings.first().lastEntryDiaryId?.takeIf { it in known }
            ?: diaries.firstOrNull()?.id
        form.value = EditorForm(
            loaded = true,
            dateTime = key.presetEpochDay?.let { LocalDate.ofEpochDay(it).atTime(now.toLocalTime()) } ?: now,
            step = if (key.presetMoodId != null) EditorStep.Tags else EditorStep.Mood,
            moodId = key.presetMoodId,
            diaryId = diaryId,
        )
    }

    fun goTo(step: EditorStep) = form.update { it.copy(step = step) }

    /**
     * @param advance whether a new entry may glide on to the tag step. The wizard passes false for a
     *   first tap on a level with aliases, so the user can still open them with a second tap.
     */
    fun selectMood(id: Long, advance: Boolean = true) {
        // Re-picking the current mood (from the alias list) keeps it, but may still move on.
        if (form.value.moodId != id) form.update { it.copy(moodId = id, dirty = true) }
        // New entries glide on to the next step once a mood lands; edits stay put.
        if (advance && key.postId == null) {
            viewModelScope.launch {
                delay(AUTO_ADVANCE_MS)
                form.update { if (it.moodId == id && it.step == EditorStep.Mood) it.copy(step = EditorStep.Tags) else it }
            }
        }
    }

    fun clearMood() = form.update { it.copy(moodId = null, dirty = true) }

    fun selectDiary(id: Long) = form.update { if (it.diaryId == id) it else it.copy(diaryId = id, dirty = true) }

    fun toggleTag(id: Long) = form.update {
        it.copy(tagIds = if (id in it.tagIds) it.tagIds - id else it.tagIds + id, dirty = true)
    }

    fun setDate(date: LocalDate) = form.update { it.copy(dateTime = date.atTime(it.dateTime.toLocalTime()), dirty = true) }

    fun setTime(hour: Int, minute: Int) = form.update { it.copy(dateTime = it.dateTime.withHour(hour).withMinute(minute), dirty = true) }

    fun addPhotos(uris: List<Uri>) {
        if (uris.isEmpty()) return
        form.update { it.copy(importingPhotos = it.importingPhotos + uris.size, dirty = true) }
        viewModelScope.launch {
            uris.forEach { uri ->
                val fileName = photoRepository.import(uri)
                if (fileName != null) sessionFiles += fileName
                form.update {
                    it.copy(
                        importingPhotos = it.importingPhotos - 1,
                        photos = if (fileName == null) it.photos else it.photos + EditorPhoto(fileName, photoRepository.pathOf(fileName)),
                    )
                }
            }
        }
    }

    fun removePhoto(fileName: String) {
        form.update { it.copy(photos = it.photos.filterNot { photo -> photo.fileName == fileName }, dirty = true) }
        // Photos imported in this session can go right away; saved ones are removed on save.
        if (sessionFiles.remove(fileName)) viewModelScope.launch { photoRepository.delete(listOf(fileName)) }
    }

    fun hasUnsavedChanges(): Boolean =
        form.value.dirty || title.text.toString() != originalTitle || note.text.toString() != originalNote

    fun save() {
        val current = form.value
        val diaryId = current.diaryId
        if (current.finished || current.importingPhotos > 0 || diaryId == null) return
        viewModelScope.launch {
            // Habits: compare the week's counts around the save, so only a newly counted day bumps.
            val date = current.dateTime.toLocalDate()
            val countsBefore = habitRepository.weekCounts(date)
            postRepository.savePost(
                PostDraft(
                    id = key.postId,
                    dateTime = current.dateTime,
                    moodId = current.moodId,
                    title = title.text.toString(),
                    note = note.text.toString(),
                    tagIds = current.tagIds,
                    diaryId = diaryId,
                    photoFileNames = current.photos.map { it.fileName },
                ),
            )
            sessionFiles.clear()
            if (current.tagIds.isNotEmpty()) {
                val bumps = Habits.bumps(habitRepository.habitList(), countsBefore, habitRepository.weekCounts(date), Habits.weekStart(date))
                habitProgressEvents.post(bumps)
            }
            settingsRepository.setLastEntryDiaryId(diaryId)
            form.update { it.copy(finished = true) }
        }
    }

    fun delete() {
        val id = key.postId ?: return
        viewModelScope.launch {
            postRepository.deletePost(id)
            form.update { it.copy(finished = true) }
        }
    }

    override fun onCleared() {
        // Abandoned draft: don't leave its photos behind.
        val leftovers = sessionFiles.toList()
        if (leftovers.isNotEmpty()) applicationScope.launch { photoRepository.delete(leftovers) }
    }

    private companion object {
        const val AUTO_ADVANCE_MS = 420L
    }
}

private fun selectedTags(groups: List<TagGroup>, form: EditorForm): List<Tag> {
    val shown = groups.flatMap { it.tags }.filter { it.id in form.tagIds }
    val shownIds = shown.map { it.id }.toSet()
    return shown + form.ownTags.filter { it.id in form.tagIds && it.id !in shownIds }
}
