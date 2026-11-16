package pl.dakil.dakilio.feature.insights.impl.overview

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.LargeFlexibleTopAppBar
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation3.runtime.NavKey
import java.time.LocalDate
import pl.dakil.dakilio.feature.entry.api.EntryEditorKey
import pl.dakil.dakilio.feature.insights.api.InsightsEntriesKey
import pl.dakil.dakilio.feature.insights.api.InsightsHabitsKey
import pl.dakil.dakilio.feature.insights.api.InsightsMoodKey
import pl.dakil.dakilio.feature.insights.impl.habits.HabitsCard
import pl.dakil.dakilio.feature.insights.api.InsightsRhythmKey
import pl.dakil.dakilio.feature.insights.api.InsightsTagKey
import pl.dakil.dakilio.feature.insights.api.InsightsTagsKey
import pl.dakil.dakilio.feature.insights.impl.logic.TagSort
import pl.dakil.dakilio.feature.insights.impl.DaySheet
import pl.dakil.dakilio.core.designsystem.component.entrance
import pl.dakil.dakilio.core.designsystem.icon.DakilioSymbols
import pl.dakil.dakilio.core.designsystem.theme.Spacing
import pl.dakil.dakilio.core.ui.DakilioFormats
import pl.dakil.dakilio.core.ui.bottomBarPadding
import pl.dakil.dakilio.core.ui.component.DiaryFilterRow
import pl.dakil.dakilio.core.ui.component.EmptyState
import pl.dakil.dakilio.core.ui.hintOrNull
import pl.dakil.dakilio.core.ui.rememberFormats
import pl.dakil.dakilio.feature.insights.impl.InsightsUiState
import pl.dakil.dakilio.feature.insights.impl.InsightsViewModel
import pl.dakil.dakilio.feature.insights.impl.PeriodHeaderState
import pl.dakil.dakilio.feature.insights.impl.PeriodKindToggle
import pl.dakil.dakilio.feature.insights.impl.PeriodPicker
import pl.dakil.dakilio.feature.insights.impl.PeriodStepper
import pl.dakil.dakilio.feature.insights.impl.R
import pl.dakil.dakilio.feature.insights.impl.hasPicker
import pl.dakil.dakilio.feature.insights.impl.key
import pl.dakil.dakilio.feature.insights.impl.logic.comparisonPeriod
import pl.dakil.dakilio.feature.insights.impl.logic.calendarRange
import pl.dakil.dakilio.feature.insights.impl.periodLabel
import pl.dakil.dakilio.feature.insights.impl.rangeLabel
import pl.dakil.dakilio.core.model.DateRange

/** The Insights tab: one period at a glance, a card per topic. */
@Composable
internal fun InsightsScreen(viewModel: InsightsViewModel, onOpenPost: (Long) -> Unit, onOpen: (NavKey) -> Unit) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()
    Scaffold(
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        contentWindowInsets = WindowInsets(0),
        topBar = {
            LargeFlexibleTopAppBar(
                title = { Text(stringResource(R.string.insights_title)) },
                subtitle = { Text(stringResource(R.string.insights_subtitle)) },
                scrollBehavior = scrollBehavior,
            )
        },
    ) { padding ->
        when (val current = state) {
            InsightsUiState.Loading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { LoadingIndicator() }
            is InsightsUiState.Ready -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
                if (!current.hasAnyEntries) {
                    EmptyState(
                        symbol = DakilioSymbols.Insights,
                        title = stringResource(R.string.insights_empty_title),
                        body = stringResource(R.string.insights_empty_body),
                        modifier = Modifier.padding(padding).align(Alignment.Center),
                    )
                } else {
                    InsightsContent(current, padding, viewModel, onOpenPost, onOpen)
                }
            }
        }
    }
}

/** What a period is compared with, by name: a whole period's own name, otherwise the days. */
@Composable
internal fun comparisonLabel(header: PeriodHeaderState, range: DateRange?, formats: DakilioFormats): String? {
    range ?: return null
    val other = comparisonPeriod(header.period, header.comparison)
    return if (other != null && other.calendarRange(header.bounds) == range) {
        periodLabel(other, header.bounds, formats)
    } else {
        rangeLabel(range, header.bounds.today, formats)
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun InsightsContent(
    state: InsightsUiState.Ready,
    padding: PaddingValues,
    viewModel: InsightsViewModel,
    onOpenPost: (Long) -> Unit,
    onOpen: (NavKey) -> Unit,
) {
    val formats = rememberFormats()
    val overview = state.overview
    val header = state.header
    var picking by rememberSaveable { mutableStateOf(false) }
    var openDay by rememberSaveable { mutableStateOf<Long?>(null) }
    LazyColumn(
        // The app bar's height is padding on the list itself, so the stepper sticks right under the bar.
        modifier = Modifier.padding(top = padding.calculateTopPadding()).widthIn(max = Spacing.maxContentWidth).fillMaxSize(),
        contentPadding = PaddingValues(bottom = bottomBarPadding(extra = Spacing.xl), start = Spacing.gutter, end = Spacing.gutter),
        verticalArrangement = Arrangement.spacedBy(Spacing.m),
    ) {
        item(key = "filters") {
            Column(verticalArrangement = Arrangement.spacedBy(Spacing.s)) {
                PeriodKindToggle(header.period.kind, viewModel::setKind)
                if (state.diaries.size > 1) {
                    DiaryFilterRow(state.diaries, state.selectedDiaryId, viewModel::selectDiary, contentPadding = PaddingValues(0.dp))
                }
            }
        }
        stickyHeader(key = "period") {
            PeriodStepper(header, formats, viewModel::step, onPick = if (header.period.hasPicker) ({ picking = true }) else null)
        }
        if (overview.isEmpty) {
            item(key = "empty") { EmptyPeriodCard(hintOrNull(stringResource(R.string.insights_period_empty_body))) }
            return@LazyColumn
        }
        // The first cards cascade in as the tab opens (decorative).
        item(key = "summary") {
            Box(Modifier.entrance(0)) { SummaryCard(overview, state.moods, formats, comparisonLabel(header, overview.comparison?.range, formats)) }
        }
        if (overview.flow.points >= 2) {
            item(key = "flow") { Box(Modifier.entrance(1)) { MoodFlowCard(overview.flow, state.moods, state.today, formats, header.key, onMore = { onOpen(InsightsMoodKey(viewModel.scope)) }) } }
        }
        item(key = "calendar") {
            Box(Modifier.entrance(2)) {
                CalendarCard(
                    calendar = overview.calendar,
                    months = header.months,
                    moods = state.moods,
                    firstDay = state.firstDay,
                    today = state.today,
                    formats = formats,
                    periodKey = header.key,
                    onPeriod = viewModel::setPeriod,
                    onDay = { openDay = it.toEpochDay() },
                )
            }
        }
        if (overview.mood.moodEntries > 0) {
            item(key = "mix") { MoodMixCard(overview.mood, onMore = { onOpen(InsightsMoodKey(viewModel.scope)) }, onLevel = { onOpen(InsightsEntriesKey(viewModel.scope, moodLevel = it)) }) }
        }
        if (overview.tags.any { it.link != null }) {
            item(key = "links") {
                TagLinksCard(
                    tags = overview.tags,
                    moods = state.moods,
                    formats = formats,
                    onMore = { onOpen(InsightsTagsKey(viewModel.scope, sort = TagSort.Brightest.name)) },
                    onTag = { onOpen(InsightsTagKey(viewModel.scope, it)) },
                )
            }
        }
        item(key = "tags") {
            TopTagsCard(overview.tags, onMore = { onOpen(InsightsTagsKey(viewModel.scope)) }, onTag = { onOpen(InsightsTagKey(viewModel.scope, it)) })
        }
        item(key = "rhythm") {
            RhythmCard(overview.rhythm, state.moods, state.firstDay, formats, header.key, onMore = { onOpen(InsightsRhythmKey(viewModel.scope)) })
        }
        if (overview.habits.isNotEmpty()) {
            item(key = "habits") {
                HabitsCard(
                    stats = overview.habits,
                    range = overview.range,
                    firstDay = state.firstDay,
                    onMore = { onOpen(InsightsHabitsKey(viewModel.scope)) },
                    onHabit = { onOpen(InsightsTagKey(viewModel.scope, it)) },
                    allDiaries = state.selectedDiaryId == null,
                )
            }
        }
        if (overview.writing.words > 0 || overview.writing.photos > 0) {
            item(key = "writing") { WritingCard(overview.writing, state.today, formats, onOpenPost) }
        }
        overview.diaries?.takeIf { it.size > 1 }?.let { diaries ->
            item(key = "diaries") { DiariesCard(diaries, state.moods, viewModel::selectDiary) }
        }
        overview.records?.let { records ->
            item(key = "records") { RecordsCard(records, state.moods, state.today, formats) }
        }
        if (overview.gathering.isNotEmpty()) {
            item(key = "gathering") { GatheringCard(overview.gathering) }
        }
    }
    openDay?.let(LocalDate::ofEpochDay)?.let { date ->
        DaySheet(
            date = date,
            // Read again whenever the state changes, so an entry edited meanwhile shows as saved.
            posts = remember(date, state) { viewModel.postsOn(date) },
            moods = state.moods,
            diaries = if (state.selectedDiaryId == null && state.diaries.size > 1) state.diaries.associateBy { it.id } else emptyMap(),
            today = state.today,
            formats = formats,
            onOpenPost = onOpenPost,
            onAddEntry = { onOpen(EntryEditorKey(presetDiaryId = state.selectedDiaryId, presetEpochDay = it.toEpochDay())) },
            onDismiss = { openDay = null },
        )
    }
    if (picking) {
        PeriodPicker(
            state = header,
            moods = state.moods,
            firstDay = state.firstDay,
            formats = formats,
            onPick = {
                picking = false
                viewModel.setPeriod(it)
            },
            onDismiss = { picking = false },
        )
    }
}
