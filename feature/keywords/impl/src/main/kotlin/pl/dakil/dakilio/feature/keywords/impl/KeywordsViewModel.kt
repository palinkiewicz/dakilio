package pl.dakil.dakilio.feature.keywords.impl

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import pl.dakil.dakilio.core.common.DakilioDispatchers
import pl.dakil.dakilio.core.common.Dispatcher
import pl.dakil.dakilio.core.data.repository.KeywordRepository
import pl.dakil.dakilio.core.data.repository.PostRepository
import pl.dakil.dakilio.core.data.repository.SettingsRepository
import pl.dakil.dakilio.core.data.repository.TagRepository
import pl.dakil.dakilio.core.model.Keyword
import pl.dakil.dakilio.core.model.KeywordDraft
import pl.dakil.dakilio.core.model.Keywords
import pl.dakil.dakilio.core.model.Tag
import pl.dakil.dakilio.core.model.TagGroup

/** An active tag with its keywords. */
data class TagKeywords(val tag: Tag, val keywords: List<Keyword>)

/**
 * @property tags tags with keywords, in group order.
 * @property groups active groups with their active tags, for picking a tag.
 */
data class KeywordsUiState(
    val tags: List<TagKeywords>,
    val groups: List<TagGroup>,
    val autoAdd: Boolean,
) {
    val taggedIds: Set<Long> = tags.map { it.tag.id }.toSet()
}

/** The words a sheet is checking older entries for. */
data class PastQuery(val tagId: Long, val drafts: List<KeywordDraft>)

/** Older entries that mention [query]'s words but don't carry its tag. */
data class PastPreview(val query: PastQuery, val postIds: List<Long>)

@HiltViewModel
class KeywordsViewModel @Inject constructor(
    private val keywordRepository: KeywordRepository,
    tagRepository: TagRepository,
    private val postRepository: PostRepository,
    private val settingsRepository: SettingsRepository,
    @Dispatcher(DakilioDispatchers.Default) private val default: CoroutineDispatcher,
) : ViewModel() {
    /** `null` while loading. Doesn't wait for the entries, so the screen shows at once. */
    val state: StateFlow<KeywordsUiState?> =
        combine(keywordRepository.keywords, tagRepository.groups, settingsRepository.settings) { keywords, groups, settings ->
            val byTag = keywords.groupBy { it.tagId }
            KeywordsUiState(
                tags = groups.flatMap { it.tags }.mapNotNull { tag -> byTag[tag.id]?.let { TagKeywords(tag, it) } },
                groups = groups,
                autoAdd = settings.keywordAutoAdd,
            )
        }
            .flowOn(default)
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    private val pastQuery = MutableStateFlow<PastQuery?>(null)

    /**
     * For the open sheet: older entries that mention the words being edited but lack the tag. Worked
     * out only while a sheet asks (never for the whole list), in the background, and again when the
     * entries change (so it drops to 0 once the tag is added).
     */
    @OptIn(FlowPreview::class, ExperimentalCoroutinesApi::class)
    val pastPreview: StateFlow<PastPreview?> = pastQuery
        .debounce(PAST_DEBOUNCE_MS)
        .flatMapLatest { query ->
            if (query == null) {
                flowOf(null)
            } else {
                val keywords = query.drafts.map { Keyword(id = 0, tagId = query.tagId, word = it.word, approximate = it.approximate) }
                postRepository.posts.map { posts ->
                    PastPreview(query, Keywords.pastMatches(posts, keywords, setOf(query.tagId))[query.tagId].orEmpty())
                }
            }
        }
        .flowOn(default)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    fun setAutoAdd(auto: Boolean) {
        viewModelScope.launch { settingsRepository.setKeywordAutoAdd(auto) }
    }

    fun save(tag: Tag, drafts: List<KeywordDraft>) {
        viewModelScope.launch { keywordRepository.setKeywords(tag.id, drafts) }
    }

    fun clear(tag: Tag) {
        viewModelScope.launch { keywordRepository.clear(tag.id) }
    }

    /** Restores keywords removed by [clear] (Undo). */
    fun restore(tag: Tag, keywords: List<Keyword>) {
        viewModelScope.launch { keywordRepository.setKeywords(tag.id, keywords.map { KeywordDraft(it.word, it.approximate) }) }
    }

    /** Asks for [pastPreview] of [tagId] with [drafts]; `null` when the sheet closes. */
    fun queryPast(tagId: Long?, drafts: List<KeywordDraft>) {
        pastQuery.value = tagId?.let { PastQuery(it, drafts) }
    }

    private companion object {
        const val PAST_DEBOUNCE_MS = 250L
    }
}
