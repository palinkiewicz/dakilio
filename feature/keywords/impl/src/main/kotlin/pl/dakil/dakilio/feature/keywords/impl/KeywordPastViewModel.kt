package pl.dakil.dakilio.feature.keywords.impl

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.assisted.Assisted
import dagger.assisted.AssistedFactory
import dagger.assisted.AssistedInject
import dagger.hilt.android.lifecycle.HiltViewModel
import java.time.LocalDate
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import pl.dakil.dakilio.core.common.DakilioDispatchers
import pl.dakil.dakilio.core.common.Dispatcher
import pl.dakil.dakilio.core.data.repository.KeywordRepository
import pl.dakil.dakilio.core.data.repository.PostRepository
import pl.dakil.dakilio.core.data.repository.TagRepository
import pl.dakil.dakilio.core.model.Excerpt
import pl.dakil.dakilio.core.model.Keyword
import pl.dakil.dakilio.core.model.Keywords
import pl.dakil.dakilio.core.model.Mood
import pl.dakil.dakilio.core.model.Post
import pl.dakil.dakilio.core.model.Tag
import pl.dakil.dakilio.core.model.keywordExcerpt
import pl.dakil.dakilio.feature.keywords.api.KeywordPastKey

/**
 * One older entry that mentions the words.
 *
 * @property tagged it got the tag since the screen opened (e.g. in the entry's own page).
 */
data class PastRow(
    val postId: Long,
    val date: LocalDate,
    val mood: Mood?,
    val excerpt: Excerpt,
    val tagged: Boolean,
)

/**
 * @property tag `null` once the tag is gone or archived.
 * @property selected ticked entries that can still get the tag.
 */
data class KeywordPastUiState(
    val tag: Tag?,
    val rows: List<PastRow>,
    val selected: Set<Long>,
) {
    val selectable: List<Long> = rows.filter { !it.tagged }.map { it.postId }
    val allSelected: Boolean = selectable.isNotEmpty() && selected.size == selectable.size
}

@HiltViewModel(assistedFactory = KeywordPastViewModel.Factory::class)
class KeywordPastViewModel @AssistedInject constructor(
    @Assisted private val key: KeywordPastKey,
    private val savedStateHandle: SavedStateHandle,
    private val postRepository: PostRepository,
    tagRepository: TagRepository,
    private val keywordRepository: KeywordRepository,
    @Dispatcher(DakilioDispatchers.Default) private val default: CoroutineDispatcher,
) : ViewModel() {

    @AssistedFactory
    interface Factory {
        fun create(key: KeywordPastKey): KeywordPastViewModel
    }

    private val keywords = key.words.map { Keyword(id = 0, tagId = key.tagId, word = it.word, approximate = it.approximate) }

    /**
     * The matching entries, newest first, worked out once when the screen opens (and kept across
     * process death), so rows don't jump when an entry gets the tag elsewhere.
     */
    private val matchedIds = MutableStateFlow(savedStateHandle.get<LongArray>(KEY_MATCHED)?.toList())

    private val selectedIds = savedStateHandle.getStateFlow(KEY_SELECTED, LongArray(0)).map { it.toSet() }

    private val _finished = MutableStateFlow(false)

    /** Set once the tag was added, so the screen can close. */
    val finished: StateFlow<Boolean> = _finished.asStateFlow()

    private val rows = combine(matchedIds.filterNotNull(), postRepository.posts) { ids, posts ->
        val byId = posts.associateBy { it.id }
        ids.mapNotNull { byId[it] }.map(::row)
    }.flowOn(default)

    private val tag = tagRepository.groups.map { groups -> groups.firstNotNullOfOrNull { group -> group.tags.firstOrNull { it.id == key.tagId } } }

    /** `null` while loading. */
    val state: StateFlow<KeywordPastUiState?> =
        combine(rows, tag, selectedIds) { rows, tag, selected ->
            val open = rows.filter { !it.tagged }.map { it.postId }.toSet()
            KeywordPastUiState(tag = tag, rows = rows, selected = selected intersect open)
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    init {
        if (matchedIds.value == null) {
            viewModelScope.launch {
                val posts = postRepository.posts.first()
                val ids = withContext(default) {
                    val matched = Keywords.pastMatches(posts, keywords, setOf(key.tagId))[key.tagId].orEmpty().toSet()
                    posts.filter { it.id in matched }.sortedByDescending { it.dateTime }.map { it.id }
                }
                savedStateHandle[KEY_MATCHED] = ids.toLongArray()
                matchedIds.value = ids
            }
        }
    }

    private fun row(post: Post): PastRow {
        val note = post.note.orEmpty()
        val title = post.title.orEmpty()
        val noteRanges = Keywords.matchRanges(note, keywords)
        val excerpt = when {
            noteRanges.isNotEmpty() -> keywordExcerpt(note, noteRanges)
            title.isNotBlank() -> keywordExcerpt(title, Keywords.matchRanges(title, keywords))
            // Edited since and no longer mentions the words: still show what it says.
            else -> keywordExcerpt(note, emptyList())
        }
        return PastRow(
            postId = post.id,
            date = post.date,
            mood = post.mood,
            excerpt = excerpt,
            tagged = post.tags.any { it.id == key.tagId },
        )
    }

    fun toggle(postId: Long) {
        val current = savedStateHandle.get<LongArray>(KEY_SELECTED)?.toSet().orEmpty()
        savedStateHandle[KEY_SELECTED] = (if (postId in current) current - postId else current + postId).toLongArray()
    }

    /** Ticks every entry that can get the tag, or none when all are ticked already. */
    fun toggleAll() {
        val current = state.value ?: return
        savedStateHandle[KEY_SELECTED] = if (current.allSelected) LongArray(0) else current.selectable.toLongArray()
    }

    fun addTag() {
        val ids = state.value?.selected?.toList().orEmpty()
        if (ids.isEmpty()) return
        viewModelScope.launch {
            keywordRepository.addTagToPosts(key.tagId, ids)
            _finished.value = true
        }
    }

    private companion object {
        const val KEY_MATCHED = "matched"
        const val KEY_SELECTED = "selected"
    }
}
