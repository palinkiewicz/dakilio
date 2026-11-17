package pl.dakil.dakilio.feature.insights.impl.tags

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.viewModelScope
import dagger.assisted.Assisted
import dagger.assisted.AssistedFactory
import dagger.assisted.AssistedInject
import dagger.hilt.android.lifecycle.HiltViewModel
import java.time.DayOfWeek
import java.time.LocalDate
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.stateIn
import pl.dakil.dakilio.core.data.repository.TagRepository
import pl.dakil.dakilio.core.model.Mood
import pl.dakil.dakilio.feature.insights.api.InsightsTagsKey
import pl.dakil.dakilio.feature.insights.impl.InsightsScopedViewModel
import pl.dakil.dakilio.feature.insights.impl.InsightsSource
import pl.dakil.dakilio.feature.insights.impl.PeriodHeaderState
import pl.dakil.dakilio.feature.insights.impl.logic.TagSort
import pl.dakil.dakilio.feature.insights.impl.logic.TagStat
import pl.dakil.dakilio.feature.insights.impl.logic.sortedFor
import pl.dakil.dakilio.feature.insights.impl.logic.tagStats

/** A tag group to filter the list by. */
data class GroupOption(val id: Long, val name: String)

sealed interface InsightsTagsUiState {
    data object Loading : InsightsTagsUiState

    /**
     * @property tags the list as shown: under the group filter, in the chosen order.
     * @property sorts the orders on offer ([TagSort.Change] only with a comparison period).
     * @property groups the groups of the period's tags; the filter shows with more than one.
     * @property withoutLink tags left out of a mood-link order because they have too few days.
     * @property reach the scale of the link bars: the largest lift on the list, at least one level.
     */
    data class Ready(
        val header: PeriodHeaderState,
        val tags: List<TagStat>,
        val sort: TagSort,
        val sorts: List<TagSort>,
        val groups: List<GroupOption>,
        val selectedGroupId: Long?,
        val withoutLink: Int,
        val reach: Float,
        val maxDays: Int,
        val moods: List<Mood>,
        val firstDay: DayOfWeek,
        val today: LocalDate,
    ) : InsightsTagsUiState
}

/** Group names by id, archived groups included: entries keep archived tags. */
internal fun TagRepository.groupNames(): Flow<Map<Long, String>> =
    combine(groups, archived) { active, archived -> (active + archived.groups + archived.tags).associate { it.id to it.name } }

@HiltViewModel(assistedFactory = InsightsTagsViewModel.Factory::class)
class InsightsTagsViewModel @AssistedInject constructor(
    source: InsightsSource,
    tagRepository: TagRepository,
    private val savedStateHandle: SavedStateHandle,
    @Assisted key: InsightsTagsKey,
) : InsightsScopedViewModel(source, savedStateHandle, key.scope) {
    private val sort = savedStateHandle.getStateFlow(KEY_SORT, key.sort)
    private val groupId = savedStateHandle.getStateFlow<Long?>(KEY_GROUP, null)

    val uiState: StateFlow<InsightsTagsUiState> =
        combine(frames, tagRepository.groupNames(), sort, groupId) { frame, groupNames, sortName, group ->
            val data = frame.data
            val all = tagStats(data.index.days(frame.range), frame.comparisonRange?.let(data.index::days)?.takeIf { it.isNotEmpty() })
            val sorts = TagSort.entries.filter { it != TagSort.Change || all.any { stat -> stat.change != null } }
            val chosen = sorts.firstOrNull { it.name == sortName } ?: TagSort.MostUsed
            val groups = all.map { it.tag.groupId }.distinct().mapNotNull { id -> groupNames[id]?.let { GroupOption(id, it) } }.sortedBy { it.name.lowercase() }
            val filter = group?.takeIf { id -> groups.any { it.id == id } }
            val inGroup = if (filter == null) all else all.filter { it.tag.groupId == filter }
            val shown = inGroup.sortedFor(chosen)
            InsightsTagsUiState.Ready(
                header = frame.header,
                tags = shown,
                sort = chosen,
                sorts = sorts,
                groups = groups,
                selectedGroupId = filter,
                withoutLink = if (chosen == TagSort.Brightest || chosen == TagSort.Heaviest) inGroup.size - shown.size else 0,
                reach = maxOf(1f, shown.maxOfOrNull { kotlin.math.abs(it.link?.lift ?: 0f) } ?: 0f),
                maxDays = shown.maxOfOrNull { it.days } ?: 0,
                moods = data.primaries,
                firstDay = data.firstDay,
                today = data.today,
            ) as InsightsTagsUiState
        }
            .flowOn(source.default)
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), InsightsTagsUiState.Loading)

    fun setSort(value: TagSort) {
        savedStateHandle[KEY_SORT] = value.name
    }

    fun selectGroup(id: Long?) {
        savedStateHandle[KEY_GROUP] = id
    }

    @AssistedFactory
    interface Factory {
        fun create(key: InsightsTagsKey): InsightsTagsViewModel
    }

    private companion object {
        const val KEY_SORT = "sort"
        const val KEY_GROUP = "group"
    }
}
