package pl.dakil.dakilio.feature.search.impl

import androidx.compose.foundation.text.input.TextFieldState
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
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import pl.dakil.dakilio.core.common.DakilioDispatchers
import pl.dakil.dakilio.core.common.Dispatcher
import pl.dakil.dakilio.core.data.repository.MoodRepository
import pl.dakil.dakilio.core.data.repository.PostRepository
import pl.dakil.dakilio.core.model.Mood
import pl.dakil.dakilio.core.model.primaries
import pl.dakil.dakilio.core.model.Post

data class SearchDay(val date: LocalDate, val posts: List<Post>)

data class SearchUiState(
    val moods: List<Mood> = emptyList(),
    val moodLevels: Set<Int> = emptySet(),
    val withPhotosOnly: Boolean = false,
    val active: Boolean = false,
    val query: String = "",
    val days: List<SearchDay> = emptyList(),
    val resultCount: Int = 0,
    val today: LocalDate = LocalDate.MIN,
)

@OptIn(FlowPreview::class)
@HiltViewModel
class SearchViewModel @Inject constructor(
    postRepository: PostRepository,
    moodRepository: MoodRepository,
    private val clock: Clock,
    @Dispatcher(DakilioDispatchers.Default) default: CoroutineDispatcher,
) : ViewModel() {
    val query = TextFieldState()

    private val toggles = MutableStateFlow(SearchFilters())
    private val queryText = snapshotFlow { query.text.toString() }.debounce(150)

    val uiState: StateFlow<SearchUiState> =
        combine(postRepository.posts, moodRepository.moods, toggles, queryText) { posts, moods, toggles, text ->
            val filters = toggles.copy(query = text)
            val results = posts.search(filters)
            SearchUiState(
                moods = moods.primaries(),
                moodLevels = filters.moodLevels,
                withPhotosOnly = filters.withPhotosOnly,
                active = filters.isActive,
                query = text.trim(),
                days = results.groupBy { it.date }.map { (date, dayPosts) -> SearchDay(date, dayPosts) },
                resultCount = results.size,
                today = LocalDate.now(clock),
            )
        }
            .flowOn(default)
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), SearchUiState())

    fun toggleMoodLevel(level: Int) = toggles.update { it.copy(moodLevels = if (level in it.moodLevels) it.moodLevels - level else it.moodLevels + level) }

    fun togglePhotosOnly() = toggles.update { it.copy(withPhotosOnly = !it.withPhotosOnly) }
}
