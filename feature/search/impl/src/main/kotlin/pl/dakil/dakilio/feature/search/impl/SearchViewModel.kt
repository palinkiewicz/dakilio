package pl.dakil.dakilio.feature.search.impl

import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.foundation.text.input.clearText
import androidx.compose.foundation.text.input.setTextAndPlaceCursorAtEnd
import androidx.compose.runtime.snapshotFlow
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import java.time.Clock
import java.time.LocalDate
import javax.inject.Inject
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import pl.dakil.dakilio.core.common.DakilioDispatchers
import pl.dakil.dakilio.core.common.Dispatcher
import pl.dakil.dakilio.core.data.repository.DiaryRepository
import pl.dakil.dakilio.core.data.repository.MoodRepository
import pl.dakil.dakilio.core.data.repository.PostRepository
import pl.dakil.dakilio.core.data.repository.SearchHistoryRepository
import pl.dakil.dakilio.core.data.repository.TagRepository
import pl.dakil.dakilio.core.model.Diary
import pl.dakil.dakilio.core.model.Mood
import pl.dakil.dakilio.core.model.Post
import pl.dakil.dakilio.core.model.Tag
import pl.dakil.dakilio.core.model.TagGroup
import pl.dakil.dakilio.core.model.primaries

internal data class SearchDay(val date: LocalDate, val posts: List<Post>)

/** The filter categories of the facet bar, each with its own sheet. */
internal enum class Facet { Mood, Tags, Diary, When, Has }

internal data class SearchUiState(
    val loaded: Boolean = false,
    val hasEntries: Boolean = false,
    /** One primary per level, worst first. */
    val moods: List<Mood> = emptyList(),
    val groups: List<TagGroup> = emptyList(),
    /** Active tags by id. */
    val tags: Map<Long, Tag> = emptyMap(),
    val diaries: List<Diary> = emptyList(),
    val filters: SearchFilters = SearchFilters(),
    /** The text to highlight in results. */
    val highlight: String = "",
    val days: List<SearchDay> = emptyList(),
    val resultCount: Int = 0,
    val today: LocalDate = LocalDate.MIN,
    val recents: List<RecentSearch> = emptyList(),
    val lenses: List<LensCount> = emptyList(),
    /** The newest photo, shown on the Photo memories lens. */
    val coverPhoto: String? = null,
    val topTags: List<Tag> = emptyList(),
    val forgottenTags: List<Tag> = emptyList(),
    val refine: List<Pair<Tag, Int>> = emptyList(),
    /** Tags matching a `#…` being typed, or `null` while none is. */
    val tagSuggestions: List<Tag>? = null,
    /** Tags matching the plain word being typed, offered as filters. */
    val wordTagSuggestions: List<Tag> = emptyList(),
)

private data class Catalog(val moods: List<Mood>, val groups: List<TagGroup>, val diaries: List<Diary>) {
    val tags: Map<Long, Tag> = groups.flatMap { it.tags }.associateBy { it.id }
}

/** What the idle screen offers; depends only on the entries, so typing never recomputes it. */
private data class Discover(
    val today: LocalDate,
    val lenses: List<LensCount>,
    val coverPhoto: String?,
    val topTags: List<Tag>,
    val forgottenTags: List<Tag>,
)

@OptIn(FlowPreview::class)
@HiltViewModel
class SearchViewModel @Inject constructor(
    postRepository: PostRepository,
    moodRepository: MoodRepository,
    tagRepository: TagRepository,
    diaryRepository: DiaryRepository,
    private val history: SearchHistoryRepository,
    private val clock: Clock,
    @Dispatcher(DakilioDispatchers.Default) default: CoroutineDispatcher,
) : ViewModel() {
    val query = TextFieldState()

    /** Every filter but the text, which lives in [query]. */
    private val toggles = MutableStateFlow(SearchFilters())
    private val queryText = snapshotFlow { query.text.toString() }.debounce(150)
    /** `null` until loaded, so the screen never flashes the empty-journal state. */
    private val posts = postRepository.posts.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)
    private val loadedPosts = posts.filterNotNull()

    private val catalog = combine(moodRepository.moods, tagRepository.groups, diaryRepository.diaries) { moods, groups, diaries ->
        Catalog(moods.primaries(), groups, diaries)
    }

    private val discover = combine(loadedPosts, catalog) { posts, catalog ->
        val today = LocalDate.now(clock)
        Discover(
            today = today,
            lenses = posts.lensCounts(today),
            coverPhoto = posts.firstOrNull { it.photos.isNotEmpty() }?.photos?.first()?.path,
            topTags = posts.topTags(today, catalog.tags),
            forgottenTags = posts.forgottenTags(today, catalog.tags),
        )
    }.flowOn(default)

    private val filters = combine(toggles, queryText) { toggles, text -> toggles.copy(query = text) }

    internal val uiState: StateFlow<SearchUiState> =
        combine(loadedPosts, catalog, discover, filters, history.recent) { posts, catalog, discover, filters, recent ->
            val results = posts.search(filters, discover.today)
            SearchUiState(
                loaded = true,
                hasEntries = posts.isNotEmpty(),
                moods = catalog.moods,
                groups = catalog.groups,
                tags = catalog.tags,
                diaries = catalog.diaries,
                filters = filters,
                highlight = withoutHashFragment(filters.query).trim(),
                days = results.groupBy { it.date }.map { (date, dayPosts) -> SearchDay(date, dayPosts) },
                resultCount = results.size,
                today = discover.today,
                recents = decodeRecents(recent, catalog.tags.keys, catalog.diaries.mapTo(HashSet()) { it.id }),
                lenses = discover.lenses,
                coverPhoto = discover.coverPhoto,
                topTags = discover.topTags,
                forgottenTags = discover.forgottenTags,
                refine = refineTags(results, filters.tagIds, catalog.tags),
                tagSuggestions = hashFragment(filters.query)?.let { fragment ->
                    tagSuggestions(fragment, catalog.tags.values.toList(), filters.tagIds)
                },
                wordTagSuggestions = typedWord(filters.query)?.let { word ->
                    tagSuggestions(word, catalog.tags.values.toList(), filters.tagIds)
                }.orEmpty(),
            )
        }
            .flowOn(default)
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), SearchUiState())

    fun toggleMoodLevel(level: Int) = toggles.update { it.copy(moodLevels = it.moodLevels.toggle(level)) }

    fun toggleTag(id: Long) = toggles.update { it.copy(tagIds = it.tagIds.toggle(id)) }

    internal fun setTagMatch(match: TagMatch) = toggles.update { it.copy(tagMatch = match) }

    fun toggleDiary(id: Long) = toggles.update { it.copy(diaryIds = it.diaryIds.toggle(id)) }

    internal fun setPeriod(period: Period?) = toggles.update { it.copy(period = period) }

    /** [day] is an ISO day-of-week number, Monday = 1. */
    fun toggleWeekday(day: Int) = toggles.update { it.copy(weekdays = it.weekdays.toggle(day)) }

    internal fun toggleHas(kind: Has) = toggles.update { it.copy(has = it.has.toggle(kind)) }

    internal fun clearFacet(facet: Facet) = toggles.update {
        when (facet) {
            Facet.Mood -> it.copy(moodLevels = emptySet())
            Facet.Tags -> it.copy(tagIds = emptySet(), tagMatch = TagMatch.Any)
            Facet.Diary -> it.copy(diaryIds = emptySet())
            Facet.When -> it.copy(period = null, weekdays = emptySet())
            Facet.Has -> it.copy(has = emptySet())
        }
    }

    fun clearAll() {
        toggles.value = SearchFilters()
        query.clearText()
    }

    internal fun applyLens(lens: Lens) {
        val preset = lens.filters ?: return
        toggles.value = preset
        query.clearText()
    }

    internal fun applyRecent(recent: RecentSearch) {
        toggles.value = recent.filters.copy(query = "")
        query.setTextAndPlaceCursorAtEnd(recent.filters.query)
        recordSearch()
    }

    internal fun removeRecent(recent: RecentSearch) {
        viewModelScope.launch { history.remove(recent.raw) }
    }

    fun clearRecents() {
        viewModelScope.launch { history.clear() }
    }

    /** Turns the `#…` being typed into a tag filter. */
    fun pickTagSuggestion(tag: Tag) {
        query.setTextAndPlaceCursorAtEnd(withoutHashFragment(query.text.toString()))
        toggles.update { it.copy(tagIds = it.tagIds + tag.id) }
    }

    /** Turns the plain word being typed into a tag filter. */
    fun pickWordTagSuggestion(tag: Tag) {
        query.setTextAndPlaceCursorAtEnd(withoutTypedWord(query.text.toString()))
        toggles.update { it.copy(tagIds = it.tagIds + tag.id) }
    }

    /** A random older entry's id, for the Shuffle lens. */
    fun shuffle(): Long? = posts.value.orEmpty().shuffleCandidates(LocalDate.now(clock)).randomOrNull()?.id

    /** Remembers the current search, unless it's empty or just a lens as offered. */
    fun recordSearch() {
        val current = toggles.value.copy(query = withoutHashFragment(query.text.toString()).trim())
        if (!current.isActive || Lens.matching(current) != null) return
        viewModelScope.launch { history.push(current.encode()) }
    }
}

private fun <T> Set<T>.toggle(value: T): Set<T> = if (value in this) this - value else this + value
