package pl.dakil.dakilio.feature.entry.impl

import android.net.Uri
import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.foundation.text.input.setTextAndPlaceCursorAtEnd
import androidx.compose.runtime.snapshotFlow
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
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.transformLatest
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.getAndUpdate
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import pl.dakil.dakilio.core.common.ApplicationScope
import pl.dakil.dakilio.core.common.DakilioDispatchers
import pl.dakil.dakilio.core.common.Dispatcher
import pl.dakil.dakilio.core.data.repository.DiaryRepository
import pl.dakil.dakilio.core.data.repository.HabitProgressEvents
import pl.dakil.dakilio.core.data.repository.HabitRepository
import pl.dakil.dakilio.core.data.repository.KeywordRepository
import pl.dakil.dakilio.core.data.repository.MoodRepository
import pl.dakil.dakilio.core.data.repository.PhotoRepository
import pl.dakil.dakilio.core.data.repository.PostRepository
import pl.dakil.dakilio.core.data.repository.SettingsRepository
import pl.dakil.dakilio.core.data.repository.TagRepository
import pl.dakil.dakilio.core.model.Diary
import pl.dakil.dakilio.core.model.Habits
import pl.dakil.dakilio.core.model.KeywordMatcher
import pl.dakil.dakilio.core.model.Keywords
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
    /** An existing entry opens read-only; this is set while its page is unlocked for editing. */
    val editing: Boolean = false,
    val saving: Boolean = false,
    val finished: Boolean = false,
    /** Keyword matches the user turned down this session (dismissed, undone or unticked). */
    val dismissedKeywordTags: Set<Long> = emptySet(),
    /** Tags keywords added on their own this session, shown so they can be undone. */
    val autoAddedTags: Set<Long> = emptySet(),
)

/**
 * A tag in the editor's "From your note" row.
 *
 * @property added the keyword already added it (auto-add mode); tapping removes it again.
 */
data class KeywordChip(val tag: Tag, val added: Boolean)

/**
 * The brief toast that announces fresh keyword matches while typing. Matches that arrive while one
 * is on screen join it, and [id] changes so its timer starts over.
 */
data class KeywordToast(val id: Long, val tags: List<KeywordChip>) {
    val anyAdded: Boolean get() = tags.any { it.added }
}

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
    val editing: Boolean,
    val finished: Boolean,
)

@HiltViewModel(assistedFactory = EntryEditorViewModel.Factory::class)
class EntryEditorViewModel @AssistedInject constructor(
    @Assisted private val key: EntryEditorKey,
    private val postRepository: PostRepository,
    private val diaryRepository: DiaryRepository,
    private val tagRepository: TagRepository,
    moodRepository: MoodRepository,
    private val photoRepository: PhotoRepository,
    private val settingsRepository: SettingsRepository,
    private val habitRepository: HabitRepository,
    private val habitProgressEvents: HabitProgressEvents,
    keywordRepository: KeywordRepository,
    private val clock: Clock,
    @ApplicationScope private val applicationScope: CoroutineScope,
    @Dispatcher(DakilioDispatchers.Default) default: CoroutineDispatcher,
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
                editing = form.editing,
                finished = form.finished,
            )
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    /**
     * The title and note as far as keywords may look: a word still being typed (no separator after it
     * yet) is left out, until typing pauses for [KEYWORD_SETTLE_MS]. So a suggestion never flickers in
     * for a half-typed word.
     */
    @OptIn(ExperimentalCoroutinesApi::class)
    private val settledText = snapshotFlow { TextSnapshot(title.text.toString(), title.selection.end, note.text.toString(), note.selection.end) }
        .distinctUntilChanged()
        .transformLatest { s ->
            emit(Keywords.settledText(s.title, s.titleCursor) + "\n" + Keywords.settledText(s.note, s.noteCursor))
            delay(KEYWORD_SETTLE_MS)
            emit(s.title + "\n" + s.note)
        }
        .distinctUntilChanged()

    private val keywords = keywordRepository.keywords.stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    /** Active tags whose keywords appear in the (settled) text. */
    private val matchedTags: StateFlow<List<Tag>> =
        combine(settledText, keywords.map(::KeywordMatcher), tagRepository.groups) { text, matcher, groups ->
            if (matcher.isEmpty) return@combine emptyList()
            val ids = matcher.match(text)
            groups.flatMap { it.tags }.filter { it.id in ids }
        }
            .flowOn(default)
            .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    /** The "From your note" row: suggestions, and in auto-add mode what was added. Empty in read mode. */
    val keywordChips: StateFlow<List<KeywordChip>> =
        combine(matchedTags, form, tagRepository.groups) { matched, form, groups ->
            if (!form.loaded || (key.postId != null && !form.editing)) return@combine emptyList()
            val added = groups.flatMap { it.tags }.filter { it.id in form.autoAddedTags && it.id in form.tagIds }
            val suggested = matched.filter { it.id !in form.tagIds && it.id !in form.dismissedKeywordTags }
            added.map { KeywordChip(it, added = true) } + suggested.map { KeywordChip(it, added = false) }
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    private val _keywordToast = MutableStateFlow<KeywordToast?>(null)
    val keywordToast: StateFlow<KeywordToast?> = _keywordToast
    private var keywordToastId = 0L

    /** Tags already announced this session, so retyping a word doesn't buzz again. */
    private val announcedKeywordTags = mutableSetOf<Long>()

    init {
        viewModelScope.launch { load() }
        viewModelScope.launch { watchKeywordMatches() }
    }

    /**
     * Announces tags whose keyword newly shows up, and in auto-add mode ticks them at once. Words
     * already in the saved text don't count (opening an old entry changes nothing), and a
     * turned-down tag stays off.
     */
    private suspend fun watchKeywordMatches() {
        combine(matchedTags, settingsRepository.settings) { matched, settings -> matched to settings.keywordAutoAdd }
            .collect { (matched, autoAdd) ->
                if (matched.isEmpty()) return@collect
                val current = form.value
                if (!current.loaded || (key.postId != null && !current.editing)) return@collect
                val baseline = Keywords.match(originalTitle + "\n" + originalNote, keywords.value)
                val fresh = matched.filter {
                    it.id !in baseline && it.id !in current.tagIds && it.id !in current.dismissedKeywordTags &&
                        it.id !in current.autoAddedTags && it.id !in announcedKeywordTags
                }
                if (fresh.isEmpty()) return@collect
                val ids = fresh.map { it.id }
                announcedKeywordTags += ids
                if (autoAdd) {
                    form.update { it.copy(tagIds = it.tagIds + ids, autoAddedTags = it.autoAddedTags + ids, dirty = true) }
                }
                _keywordToast.update { shown ->
                    val chips = fresh.map { KeywordChip(it, added = autoAdd) }
                    KeywordToast(++keywordToastId, (shown?.tags.orEmpty().filter { old -> old.tag.id !in ids }) + chips)
                }
            }
    }

    /** The toast's time is up (unless a newer match has replaced it since). */
    fun consumeKeywordToast(id: Long) = _keywordToast.update { if (it?.id == id) null else it }

    /** Undoes the toast's auto-added tags that are still on, leaving any the user changed by hand. */
    fun undoKeywordToast() {
        val toast = _keywordToast.getAndUpdate { null } ?: return
        val ids = toast.tags.filter { it.added }.map { it.tag.id }.toSet()
        form.update {
            val undo = ids.filter { id -> id in it.tagIds && id in it.autoAddedTags }
            if (undo.isEmpty()) return@update it
            it.copy(
                tagIds = it.tagIds - undo,
                autoAddedTags = it.autoAddedTags - undo,
                dismissedKeywordTags = it.dismissedKeywordTags + undo,
                dirty = true,
            )
        }
    }

    /** Ticks the toast's suggested tags, unless the user dismissed or ticked them meanwhile. */
    fun addFromKeywordToast() {
        val toast = _keywordToast.getAndUpdate { null } ?: return
        val ids = toast.tags.filterNot { it.added }.map { it.tag.id }
        form.update {
            val add = ids.filter { id -> id !in it.tagIds && id !in it.dismissedKeywordTags }
            if (add.isEmpty()) it else it.copy(tagIds = it.tagIds + add, dirty = true)
        }
    }

    /** Ticks a suggested tag. */
    fun addKeywordTag(id: Long) = form.update { it.copy(tagIds = it.tagIds + id, dirty = true) }

    /** Hides a suggestion for the rest of the session. */
    fun dismissKeywordTag(id: Long) = form.update { it.copy(dismissedKeywordTags = it.dismissedKeywordTags + id) }

    /** Undoes an auto-added tag; it won't be added again this session. */
    fun undoKeywordTag(id: Long) = form.update {
        it.copy(
            tagIds = it.tagIds - id,
            autoAddedTags = it.autoAddedTags - id,
            dismissedKeywordTags = it.dismissedKeywordTags + id,
            dirty = true,
        )
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

    fun startEditing() = form.update { it.copy(editing = true) }

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
        if (id in it.tagIds) {
            // Unticking a tag the note suggests means "not this one": don't offer it straight back.
            val turnedDown = matchedTags.value.any { tag -> tag.id == id }
            it.copy(
                tagIds = it.tagIds - id,
                autoAddedTags = it.autoAddedTags - id,
                dismissedKeywordTags = if (turnedDown) it.dismissedKeywordTags + id else it.dismissedKeywordTags,
                dirty = true,
            )
        } else {
            it.copy(tagIds = it.tagIds + id, dirty = true)
        }
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
        if (current.finished || current.saving || current.importingPhotos > 0 || diaryId == null) return
        form.update { it.copy(saving = true) }
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
                val bumps = Habits.bumps(habitRepository.habitList(), countsBefore, habitRepository.weekCounts(date), Habits.weekStart(date, settingsRepository.firstDayOfWeek.first()))
                habitProgressEvents.post(bumps)
            }
            settingsRepository.setLastEntryDiaryId(diaryId)
            if (key.postId == null || settingsRepository.settings.first().closeAfterSave) {
                form.update { it.copy(finished = true) }
            } else {
                // Stay on the entry and go back to reading; what was just saved is the new baseline.
                val shown = uiState.value
                originalTitle = title.text.toString()
                originalNote = note.text.toString()
                form.update {
                    it.copy(
                        ownMood = shown?.moods?.firstOrNull { mood -> mood.id == it.moodId },
                        ownTags = shown?.selectedTags ?: it.ownTags,
                        dirty = false,
                        editing = false,
                        saving = false,
                        dismissedKeywordTags = emptySet(),
                        autoAddedTags = emptySet(),
                    )
                }
                _keywordToast.value = null
            }
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
        const val KEYWORD_SETTLE_MS = 800L
    }
}

private data class TextSnapshot(val title: String, val titleCursor: Int, val note: String, val noteCursor: Int)

private fun selectedTags(groups: List<TagGroup>, form: EditorForm): List<Tag> {
    val shown = groups.flatMap { it.tags }.filter { it.id in form.tagIds }
    val shownIds = shown.map { it.id }.toSet()
    return shown + form.ownTags.filter { it.id in form.tagIds && it.id !in shownIds }
}
