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
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.IconButton
import androidx.compose.material3.LargeFlexibleTopAppBar
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
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
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation3.runtime.NavKey
import java.time.LocalDate
import pl.dakil.dakilio.feature.entry.api.EntryEditorKey
import pl.dakil.dakilio.feature.insights.api.InsightsEntriesKey
import pl.dakil.dakilio.feature.insights.api.InsightsHabitsKey
import pl.dakil.dakilio.feature.insights.api.InsightsMoodKey
import pl.dakil.dakilio.feature.insights.api.InsightsRecapKey
import pl.dakil.dakilio.feature.insights.api.InsightsRecapsKey
import pl.dakil.dakilio.feature.insights.api.InsightsScope
import pl.dakil.dakilio.feature.insights.impl.logic.encode
import pl.dakil.dakilio.feature.insights.impl.habits.HabitsCard
import pl.dakil.dakilio.feature.insights.api.InsightsRhythmKey
import pl.dakil.dakilio.feature.insights.api.InsightsTagKey
import pl.dakil.dakilio.feature.insights.api.InsightsTagsKey
import pl.dakil.dakilio.feature.insights.impl.logic.Highlight
import pl.dakil.dakilio.feature.insights.impl.logic.InsightsPeriod
import pl.dakil.dakilio.feature.insights.impl.logic.TagSort
import pl.dakil.dakilio.feature.insights.impl.DaySheet
import pl.dakil.dakilio.core.designsystem.component.entrance
import pl.dakil.dakilio.core.designsystem.icon.DakilioIcon
import pl.dakil.dakilio.core.designsystem.icon.DakilioSymbols
import pl.dakil.dakilio.core.designsystem.theme.Spacing
import pl.dakil.dakilio.core.ui.DakilioFormats
import pl.dakil.dakilio.core.ui.bottomBarPadding
import pl.dakil.dakilio.core.ui.component.DiaryFilterDropdown
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
import pl.dakil.dakilio.feature.insights.impl.appBarSurface
import pl.dakil.dakilio.feature.insights.impl.hasPicker
import pl.dakil.dakilio.feature.insights.impl.key
import pl.dakil.dakilio.feature.insights.impl.logic.comparisonPeriod
import pl.dakil.dakilio.feature.insights.impl.logic.comparisonRange
import pl.dakil.dakilio.feature.insights.impl.logic.calendarRange
import pl.dakil.dakilio.feature.insights.impl.periodLabel
import pl.dakil.dakilio.feature.insights.impl.rangeLabel
import pl.dakil.dakilio.core.model.DateRange
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.graphics.lerp
import androidx.compose.material3.MaterialTheme
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.layout.RowScope
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.FastOutLinearInEasing

/**
 * The Insights tab: one period at a glance, a card per topic. Like the timeline, it has two app bars:
 * with one diary the large bar, whose title collapses as the list scrolls; with more, a pinned bar
 * holding the diary dropdown, and the title as the list's first row, scrolling away under it.
 */
@Composable
internal fun InsightsScreen(viewModel: InsightsViewModel, onOpenPost: (Long) -> Unit, onOpen: (NavKey) -> Unit) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val ready = state as? InsightsUiState.Ready
    val pinned = ready != null && ready.hasAnyEntries && ready.diaries.size > 1
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()
    val listState = rememberLazyListState()
    // The pinned bar turns surfaceContainer once anything is under it, like the timeline's.
    val scrolled by animateFloatAsState(
        if (pinned && listState.canScrollBackward) 1f else 0f,
        MaterialTheme.motionScheme.defaultEffectsSpec(),
        label = "barScrolled",
    )
    val barFraction: () -> Float = if (pinned) ({ scrolled }) else ({ scrollBehavior.state.collapsedFraction })
    val actions: @Composable RowScope.() -> Unit = {
        if (ready?.hasAnyEntries == true) {
            IconButton(onClick = { onOpen(InsightsRecapsKey) }) {
                DakilioIcon(DakilioSymbols.History, contentDescription = stringResource(R.string.insights_recaps_title))
            }
        }
    }
    Scaffold(
        // Only the large bar collapses; the pinned bar must not swallow the list's scrolling.
        modifier = if (pinned) Modifier else Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        contentWindowInsets = WindowInsets(0),
        topBar = {
            if (pinned) {
                val barColors = TopAppBarDefaults.topAppBarColors()
                TopAppBar(
                    title = { DiaryFilterDropdown(ready.diaries, ready.selectedDiaryId, viewModel::selectDiary) },
                    actions = actions,
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = lerp(barColors.containerColor, barColors.scrolledContainerColor, FastOutLinearInEasing.transform(scrolled)),
                    ),
                )
            } else {
                LargeFlexibleTopAppBar(
                    title = { Text(stringResource(R.string.insights_title)) },
                    subtitle = { Text(stringResource(R.string.insights_subtitle)) },
                    actions = actions,
                    scrollBehavior = scrollBehavior,
                )
            }
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
                    InsightsContent(current, padding, listState, pinned, viewModel, onOpenPost, onOpen, barFraction)
                }
            }
        }
    }
}

/** The title as the list's first row, when the diary dropdown takes the bar. */
@Composable
private fun InsightsTitle(barFraction: () -> Float) {
    Column(Modifier.appBarSurface(barFraction, extendBelow = Spacing.m).padding(start = Spacing.xs, top = Spacing.m)) {
        Text(stringResource(R.string.insights_title), style = MaterialTheme.typography.displaySmall, modifier = Modifier.semantics { heading() })
        Text(stringResource(R.string.insights_subtitle), style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

/** The days [header]'s period is compared with, whether or not they hold entries. */
internal fun comparisonRangeOf(header: PeriodHeaderState): DateRange? = comparisonRange(header.period, header.comparison, header.bounds)

/**
 * What a period is compared with, by name: "October 2026", or "October 2026, first 16 days" while
 * the period is still running; otherwise just the days.
 */
@Composable
internal fun comparisonLabel(header: PeriodHeaderState, range: DateRange?, formats: DakilioFormats): String? {
    range ?: return null
    val other = comparisonPeriod(header.period, header.comparison) ?: return rangeLabel(range, header.bounds.today, formats)
    val full = other.calendarRange(header.bounds)
    val name = periodLabel(other, header.bounds, formats)
    return when {
        full == range -> name
        full.from == range.from -> pluralStringResource(R.plurals.insights_first_days, range.days, name, range.days)
        else -> rangeLabel(range, header.bounds.today, formats)
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun InsightsContent(
    state: InsightsUiState.Ready,
    padding: PaddingValues,
    listState: LazyListState,
    pinned: Boolean,
    viewModel: InsightsViewModel,
    onOpenPost: (Long) -> Unit,
    onOpen: (NavKey) -> Unit,
    collapsedFraction: () -> Float,
) {
    val formats = rememberFormats()
    val overview = state.overview
    val header = state.header
    var picking by rememberSaveable { mutableStateOf(false) }
    var openDay by rememberSaveable { mutableStateOf<Long?>(null) }
    LazyColumn(
        state = listState,
        // The app bar's height is padding on the list itself, so the stepper sticks right under the bar.
        modifier = Modifier.padding(top = padding.calculateTopPadding()).widthIn(max = Spacing.maxContentWidth).fillMaxSize(),
        contentPadding = PaddingValues(bottom = bottomBarPadding(extra = Spacing.xl), start = Spacing.gutter, end = Spacing.gutter),
        verticalArrangement = Arrangement.spacedBy(Spacing.m),
    ) {
        if (pinned) item(key = "title") { InsightsTitle(collapsedFraction) }
        if (state.offers.isNotEmpty()) {
            item(key = "offers") {
                RecapOffersCard(
                    offers = state.offers,
                    moods = state.moods,
                    formats = formats,
                    onPlay = { onOpen(InsightsRecapKey(InsightsScope(period = it.period.encode()))) },
                    onDismiss = { viewModel.dismiss(it.offer) },
                    onArchive = { onOpen(InsightsRecapsKey) },
                )
            }
        }
        item(key = "filters") {
            PeriodKindToggle(header.period.kind, viewModel::setKind, Modifier.appBarSurface(collapsedFraction, extendBelow = Spacing.m))
        }
        stickyHeader(key = "period") {
            PeriodStepper(
                header,
                formats,
                viewModel::step,
                onPick = if (header.period.hasPicker) ({ picking = true }) else null,
                collapsedFraction = collapsedFraction,
            )
        }
        if (overview.isEmpty) {
            item(key = "empty") {
                val back = state.latestWithEntries
                EmptyPeriodCard(
                    hint = if (back == null) hintOrNull(stringResource(R.string.insights_period_empty_body)) else null,
                    action = back?.let { period ->
                        {
                            FilledTonalButton(onClick = { viewModel.setPeriod(period) }) {
                                Text(stringResource(R.string.insights_period_show, periodLabel(period, header.bounds, formats)))
                            }
                        }
                    },
                )
            }
            return@LazyColumn
        }
        // The first cards cascade in as the tab opens (decorative).
        item(key = "summary") {
            Box(Modifier.entrance(0)) {
                SummaryCard(
                    overview = overview,
                    moods = state.moods,
                    formats = formats,
                    header = header,
                    firstDay = state.firstDay,
                    onComparison = viewModel::setComparison,
                )
            }
        }
        if (state.highlights.isNotEmpty()) {
            item(key = "highlights") {
                HighlightsRow(
                    highlights = state.highlights,
                    moods = state.moods,
                    today = state.today,
                    formats = formats,
                    comparisonLabel = comparisonLabel(header, comparisonRangeOf(header), formats),
                    onOpen = { highlight ->
                        when (highlight) {
                            is Highlight.TagBrighter -> ({ onOpen(InsightsTagKey(viewModel.scope, highlight.tag.id)) })
                            is Highlight.TagHeavier -> ({ onOpen(InsightsTagKey(viewModel.scope, highlight.tag.id)) })
                            is Highlight.HabitRun -> ({ onOpen(InsightsTagKey(viewModel.scope, highlight.habit.tag.id)) })
                            is Highlight.BestWeekday -> ({ onOpen(InsightsRhythmKey(viewModel.scope)) })
                            is Highlight.MoodChange, is Highlight.BestMonthYet, is Highlight.BrightRun -> ({ onOpen(InsightsMoodKey(viewModel.scope)) })
                            is Highlight.StreakRecord, is Highlight.WordsWritten -> null
                        }
                    },
                )
            }
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
