package pl.dakil.dakilio.feature.insights.impl

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import java.time.Clock
import java.time.LocalDate
import javax.inject.Inject
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import pl.dakil.dakilio.core.common.DakilioDispatchers
import pl.dakil.dakilio.core.common.Dispatcher
import pl.dakil.dakilio.core.data.repository.PostRepository
import pl.dakil.dakilio.core.data.repository.SettingsRepository
import pl.dakil.dakilio.core.model.Milestone
import pl.dakil.dakilio.core.model.MilestoneProgress
import pl.dakil.dakilio.core.model.Milestones

data class MilestonesUiState(val ladders: List<MilestoneProgress>, val today: LocalDate) {
    val reached: Int get() = ladders.sumOf { it.reachedCount }
    val total: Int get() = ladders.sumOf { it.statuses.size }
}

/** Milestones count the entries of every diary. */
@HiltViewModel
class MilestonesViewModel @Inject constructor(
    postRepository: PostRepository,
    private val clock: Clock,
    @Dispatcher(DakilioDispatchers.Default) default: CoroutineDispatcher,
) : ViewModel() {
    /** `null` while loading. */
    val state: StateFlow<MilestonesUiState?> = postRepository.posts
        .map { posts ->
            val today = LocalDate.now(clock)
            MilestonesUiState(Milestones.progress(posts, today), today)
        }
        .flowOn(default)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)
}

/**
 * Feeds the app-wide milestone popup ([MilestoneCelebrationHost]): watches the entries and holds the
 * stamps that were just collected until the popup has shown them.
 *
 * Nothing is stored. The first look at the entries is the baseline; after that, a stamp that
 * appears when entries change one at a time (a save, not an import or a restore) is celebrated.
 */
@HiltViewModel
class MilestoneCelebrationViewModel @Inject constructor(
    postRepository: PostRepository,
    settingsRepository: SettingsRepository,
    private val clock: Clock,
    @Dispatcher(DakilioDispatchers.Default) default: CoroutineDispatcher,
) : ViewModel() {
    private val _celebrating = MutableStateFlow<List<Milestone>>(emptyList())
    val celebrating: StateFlow<List<Milestone>> = _celebrating.asStateFlow()

    init {
        viewModelScope.launch {
            var before: Set<Milestone>? = null
            var entriesBefore = 0
            combine(postRepository.posts, settingsRepository.settings.map { it.celebrateMilestones }) { posts, celebrate ->
                Triple(posts.size, Milestones.reached(Milestones.progress(posts, LocalDate.now(clock))), celebrate)
            }
                .flowOn(default)
                .collect { (entries, reached, celebrate) ->
                    val known = before
                    // A jump of several entries is an import, a restore or a clean-up, not a moment to mark.
                    if (celebrate && known != null && entries - entriesBefore in 0..1) {
                        val fresh = Milestones.newlyReached(known, reached)
                        if (fresh.isNotEmpty()) _celebrating.value = fresh
                    }
                    before = reached
                    entriesBefore = entries
                }
        }
    }

    fun consume() {
        _celebrating.value = emptyList()
    }
}
