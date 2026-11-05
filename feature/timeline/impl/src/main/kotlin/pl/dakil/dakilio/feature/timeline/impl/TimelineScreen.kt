package pl.dakil.dakilio.feature.timeline.impl

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animate
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.Button
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.IconButton
import androidx.compose.material3.LargeFlexibleTopAppBar
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MediumFloatingActionButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SmallFloatingActionButton
import androidx.compose.material3.TopAppBarScrollBehavior
import androidx.compose.material3.SelectableDates
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.animateFloatingActionButton
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import java.time.Instant
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneOffset
import kotlinx.coroutines.launch
import pl.dakil.dakilio.core.designsystem.component.pressScale
import pl.dakil.dakilio.core.designsystem.component.rememberHaptics
import pl.dakil.dakilio.core.designsystem.icon.DakilioIcon
import pl.dakil.dakilio.core.designsystem.icon.DakilioSymbols
import pl.dakil.dakilio.core.designsystem.icon.symbolForKey
import pl.dakil.dakilio.core.designsystem.theme.SegmentedShapes
import pl.dakil.dakilio.core.designsystem.theme.Spacing
import pl.dakil.dakilio.core.model.Diary
import pl.dakil.dakilio.core.model.Mood
import pl.dakil.dakilio.core.model.PostPhoto
import pl.dakil.dakilio.core.model.PreviewLength
import pl.dakil.dakilio.core.model.levelsBestFirst
import pl.dakil.dakilio.core.ui.DakilioFormats
import pl.dakil.dakilio.core.ui.Hint
import pl.dakil.dakilio.core.ui.bottomBarPadding
import pl.dakil.dakilio.core.ui.component.DiaryFilterRow
import pl.dakil.dakilio.core.ui.component.EmptyState
import pl.dakil.dakilio.core.ui.component.PhotoViewer
import pl.dakil.dakilio.core.ui.component.PostCard
import pl.dakil.dakilio.core.ui.dayTitle
import pl.dakil.dakilio.core.ui.mood.MoodAliasOverlay
import pl.dakil.dakilio.core.ui.mood.MoodBadge
import pl.dakil.dakilio.core.ui.mood.screenRect
import pl.dakil.dakilio.core.ui.relativeDayLabel
import pl.dakil.dakilio.core.ui.rememberFormats
import pl.dakil.dakilio.core.ui.sharedContainer
import pl.dakil.dakilio.feature.entry.api.EntryEditorKey
import pl.dakil.dakilio.feature.search.api.SearchKey

@Composable
internal fun TimelineRoute(
    onOpenEntry: (EntryEditorKey) -> Unit,
    onOpenSearch: () -> Unit,
    viewModel: TimelineViewModel,
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    LifecycleResumeEffect(Unit) {
        viewModel.refreshToday()
        onPauseOrDispose { }
    }
    TimelineScreen(
        state = state,
        onSelectDiary = viewModel::selectDiary,
        onToggleStretch = viewModel::toggleStretch,
        onExpandStretch = viewModel::expandStretch,
        onOpenEntry = onOpenEntry,
        onOpenSearch = onOpenSearch,
        onPreviewLength = viewModel::setPreviewLength,
        onCompactTags = viewModel::setCompactTags,
        onMonthNavigator = viewModel::setMonthNavigator,
        onTextScale = viewModel::setTimelineTextScale,
    )
}

@Composable
internal fun TimelineScreen(
    state: TimelineUiState,
    onSelectDiary: (Long?) -> Unit,
    onToggleStretch: (Long) -> Unit,
    onExpandStretch: (Long) -> Unit,
    onOpenEntry: (EntryEditorKey) -> Unit,
    onOpenSearch: () -> Unit,
    onPreviewLength: (PreviewLength) -> Unit,
    onCompactTags: (Boolean) -> Unit,
    onMonthNavigator: (Boolean) -> Unit,
    onTextScale: (Float) -> Unit,
) {
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()
    val listState = rememberLazyListState()
    val formats = rememberFormats()
    val haptics = rememberHaptics()
    val scope = rememberCoroutineScope()
    val showScrollToTop by remember { derivedStateOf { listState.firstVisibleItemIndex >= SCROLL_TO_TOP_THRESHOLD } }
    var viewer by remember { mutableStateOf<Pair<List<PostPhoto>, Int>?>(null) }
    val ready = state as? TimelineUiState.Ready
    val selectedDiary = ready?.selectedDiaryId

    // Jumps to a day: from the date picker, or from an "Also this day" chip, which also switches
    // diaries. Without this, a diary switch keeps the list's position by the first visible item's
    // key, falling back to its index: often a different day.
    var pendingJump by remember { mutableStateOf<DayJump?>(null) }
    var pickDate by rememberSaveable { mutableStateOf(false) }
    var pickMonth by rememberSaveable { mutableStateOf(false) }
    var viewMenu by rememberSaveable { mutableStateOf(false) }
    val density = LocalDensity.current
    // The first row not covered by the month pill decides the month it shows.
    val coveredPx = with(density) { MonthNavigatorClearance.roundToPx() }
    val topIndex by remember(coveredPx) {
        derivedStateOf {
            listState.layoutInfo.visibleItemsInfo.firstOrNull { it.offset + it.size > coveredPx }?.index
                ?: listState.firstVisibleItemIndex
        }
    }
    // Jumps to the top of a month, just below the floating month pill (the newest month: the very
    // top). Unlike a day jump it never waits for a new list, so it scrolls at once.
    fun jumpToMonth(month: YearMonth) {
        val items = ready?.items ?: return
        when (val index = items.monthTopIndex(month)) {
            null -> Unit
            0 -> listState.requestScrollToItem(0)
            else -> listState.requestScrollToItem(index, -coveredPx)
        }
    }
    SideEffect {
        val jump = pendingJump ?: return@SideEffect
        if (ready == null || (jump.diaryId != null && ready.selectedDiaryId != jump.diaryId)) return@SideEffect
        when (val location = ready.items.locateDay(jump.date)) {
            // Applied on the next measure, i.e. the very frame that shows the new list.
            is DayLocation.At -> {
                listState.requestScrollToItem(location.index)
                pendingJump = null
            }
            // Open the stretch first; the next list has the day's own row.
            is DayLocation.InStretch -> if (!jump.expanding) {
                onExpandStretch(location.newestEpochDay)
                pendingJump = jump.copy(expanding = true)
            }
            DayLocation.Missing -> pendingJump = null
        }
    }

    Scaffold(
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        contentWindowInsets = WindowInsets(0),
        topBar = {
            val scrolled = scrollBehavior.state.collapsedFraction > 0.01f || listState.canScrollBackward
            val barColor by animateColorAsState(
                if (scrolled) MaterialTheme.colorScheme.surfaceContainer else MaterialTheme.colorScheme.surface,
                MaterialTheme.motionScheme.defaultEffectsSpec(),
                label = "barColor",
            )
            Column(Modifier.background(barColor)) {
                LargeFlexibleTopAppBar(
                    title = { Text(stringResource(greetingRes(ready?.greeting ?: Greeting.Morning))) },
                    subtitle = ready?.let { { Subtitle(it, formats) } },
                    actions = {
                        // The menu anchors to this Box, so it grows out of the button.
                        Box {
                            IconButton(onClick = {
                                haptics.tick()
                                viewMenu = true
                            }) {
                                DakilioIcon(DakilioSymbols.Tune, contentDescription = stringResource(R.string.timeline_view_options))
                            }
                            TimelineViewMenu(
                                expanded = viewMenu,
                                onDismiss = { viewMenu = false },
                                onPreviewLength = onPreviewLength,
                                onCompactTags = onCompactTags,
                                monthNavigator = ready?.monthNavigator ?: true,
                                onMonthNavigator = onMonthNavigator,
                                onTextScale = onTextScale,
                            )
                        }
                        if (ready != null) {
                            IconButton(onClick = {
                                haptics.tick()
                                pickDate = true
                            }) {
                                DakilioIcon(DakilioSymbols.CalendarMonth, contentDescription = stringResource(R.string.timeline_jump_to_date))
                            }
                        }
                        IconButton(
                            onClick = onOpenSearch,
                            modifier = Modifier.sharedContainer(SearchKey.SHARED_ELEMENT_KEY, CircleShape),
                        ) {
                            DakilioIcon(DakilioSymbols.Search, contentDescription = stringResource(R.string.timeline_search))
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Transparent, scrolledContainerColor = Color.Transparent),
                    scrollBehavior = scrollBehavior,
                )
                if (ready != null && ready.diaries.size > 1) {
                    DiaryFilterRow(
                        diaries = ready.diaries,
                        selectedDiaryId = ready.selectedDiaryId,
                        onSelect = onSelectDiary,
                        modifier = Modifier.padding(bottom = Spacing.s),
                    )
                }
            }
        },
        floatingActionButton = {
            Column(
                modifier = Modifier.padding(bottom = bottomBarPadding()),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(Spacing.m),
            ) {
                AnimatedVisibility(
                    visible = ready != null && showScrollToTop,
                    enter = scaleIn(MaterialTheme.motionScheme.fastSpatialSpec()) + fadeIn(MaterialTheme.motionScheme.fastEffectsSpec()),
                    exit = scaleOut(MaterialTheme.motionScheme.fastSpatialSpec()) + fadeOut(MaterialTheme.motionScheme.fastEffectsSpec()),
                ) {
                    SmallFloatingActionButton(
                        onClick = {
                            haptics.tick()
                            scope.launch { scrollToTop(listState, scrollBehavior) }
                        },
                        containerColor = MaterialTheme.colorScheme.secondaryContainer,
                        contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
                    ) {
                        DakilioIcon(DakilioSymbols.ArrowUpward, contentDescription = stringResource(R.string.timeline_scroll_to_top))
                    }
                }
                MediumFloatingActionButton(
                    onClick = {
                        haptics.tick()
                        onOpenEntry(EntryEditorKey(presetDiaryId = selectedDiary))
                    },
                    modifier = Modifier
                        .animateFloatingActionButton(visible = ready != null, alignment = Alignment.BottomEnd)
                        .sharedContainer(EntryEditorKey.NEW_ENTRY_SHARED_KEY, RoundedCornerShape(20.dp)),
                ) {
                    DakilioIcon(DakilioSymbols.Add, contentDescription = stringResource(R.string.timeline_new_entry), modifier = Modifier.size(28.dp))
                }
            }
        },
    ) { padding ->
        when (state) {
            TimelineUiState.Loading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { LoadingIndicator() }
            is TimelineUiState.Ready -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
                LazyColumn(
                    state = listState,
                    modifier = Modifier.widthIn(max = Spacing.maxContentWidth).fillMaxSize(),
                    contentPadding = PaddingValues(
                        top = padding.calculateTopPadding(),
                        bottom = bottomBarPadding(extra = 112.dp),
                        start = Spacing.gutter,
                        end = Spacing.gutter,
                    ),
                ) {
                    items(state.items, key = { it.key }, contentType = { it::class }) { item ->
                        val itemModifier = Modifier.animateItem()
                        when (item) {
                            is TimelineItem.MonthDivider -> MonthDivider(item, formats, itemModifier)
                            is TimelineItem.DayHeader -> DayHeader(item, state.today, formats, itemModifier)
                            is TimelineItem.TodayPrompt -> CheckInCard(
                                moods = state.moods,
                                onMood = { onOpenEntry(EntryEditorKey(presetDiaryId = selectedDiary, presetMoodId = it.id)) },
                                modifier = itemModifier,
                            )
                            is TimelineItem.PostItem -> PostCard(
                                post = item.post,
                                formats = formats,
                                onClick = { onOpenEntry(EntryEditorKey(postId = item.post.id)) },
                                index = item.index,
                                count = item.count,
                                diary = item.diary,
                                onPhotoClick = { viewer = item.post.photos to it },
                                modifier = itemModifier
                                    .padding(top = if (item.index > 0) SegmentedShapes.gap else 0.dp)
                                    .sharedContainer(EntryEditorKey.postSharedElementKey(item.post.id), SegmentedShapes.of(item.index, item.count)),
                            )
                            is TimelineItem.EmptyDay -> EmptyDayRow(
                                date = item.date,
                                otherDiaries = item.otherDiaries,
                                today = state.today,
                                formats = formats,
                                onAdd = { onOpenEntry(EntryEditorKey(presetDiaryId = selectedDiary, presetEpochDay = item.date.toEpochDay())) },
                                modifier = itemModifier,
                            )
                            is TimelineItem.QuietStretch -> QuietStretchRow(
                                stretch = item,
                                formats = formats,
                                onToggle = {
                                    haptics.tick()
                                    onToggleStretch(item.newest.toEpochDay())
                                },
                                modifier = itemModifier,
                            )
                            is TimelineItem.OtherDiaries -> OtherDiariesRow(
                                item = item,
                                onSelectDiary = { diaryId ->
                                    haptics.tick()
                                    pendingJump = DayJump(item.date, diaryId)
                                    onSelectDiary(diaryId)
                                },
                                modifier = itemModifier,
                            )
                        }
                    }
                    if (state.isEmpty) {
                        item(key = "empty-state", contentType = "empty") {
                            EmptyState(
                                symbol = DakilioSymbols.Route,
                                title = stringResource(R.string.timeline_empty_title),
                                body = stringResource(R.string.timeline_empty_body),
                                modifier = Modifier.fillMaxWidth().animateItem(),
                                action = {
                                    Button(onClick = { onOpenEntry(EntryEditorKey(presetDiaryId = selectedDiary)) }) {
                                        Text(stringResource(R.string.timeline_empty_action))
                                    }
                                },
                            )
                        }
                    }
                }
                val month = state.items.monthAt(topIndex)
                val sections = remember(state.items) { state.items.sectionMonths() }
                AnimatedVisibility(
                    visible = state.monthNavigator && month != null && listState.firstVisibleItemIndex >= 1,
                    enter = scaleIn(MaterialTheme.motionScheme.fastSpatialSpec()) + fadeIn(MaterialTheme.motionScheme.fastEffectsSpec()),
                    exit = scaleOut(MaterialTheme.motionScheme.fastSpatialSpec()) + fadeOut(MaterialTheme.motionScheme.fastEffectsSpec()),
                    modifier = Modifier.padding(top = padding.calculateTopPadding() + Spacing.s),
                ) {
                    // Keeps the last month while the pill animates out.
                    val shown = month ?: sections.firstOrNull() ?: YearMonth.from(state.today)
                    val position = sections.indexOf(shown)
                    MonthNavigator(
                        month = shown,
                        newer = sections.getOrNull(position - 1),
                        older = if (position >= 0) sections.getOrNull(position + 1) else null,
                        formats = formats,
                        onJump = ::jumpToMonth,
                        onPick = { pickMonth = true },
                    )
                }
            }
        }
    }

    viewer?.let { (photos, index) -> PhotoViewer(photos, index, onDismiss = { viewer = null }) }

    if (pickMonth && ready != null) {
        MonthPickerSheet(
            current = ready.items.monthAt(topIndex) ?: YearMonth.from(ready.today),
            firstDay = ready.firstDay,
            lastDay = ready.lastDay,
            months = ready.months,
            moods = ready.moods,
            formats = formats,
            onPick = { month ->
                pickMonth = false
                jumpToMonth(month)
            },
            onDismiss = { pickMonth = false },
        )
    }

    if (pickDate && ready != null) {
        val topDay = ready.items.getOrNull(listState.firstVisibleItemIndex)?.dayOrNull()
        JumpToDateDialog(
            initial = (topDay ?: ready.today).coerceIn(ready.firstDay, ready.lastDay),
            firstDay = ready.firstDay,
            lastDay = ready.lastDay,
            onDismiss = { pickDate = false },
            onPick = { date ->
                pickDate = false
                haptics.confirm()
                pendingJump = DayJump(date)
            },
        )
    }
}

/** Rows scrolled past before the "scroll to top" button shows up. */
private const val SCROLL_TO_TOP_THRESHOLD = 4

/** Beyond this many rows, the list first jumps close to the top so the animated part stays short. */
private const val SCROLL_TO_TOP_JUMP = 12

/**
 * Smoothly scrolls the timeline back to today and re-expands the large app bar, which a
 * programmatic scroll doesn't do on its own (it bypasses nested scrolling).
 */
private suspend fun scrollToTop(listState: LazyListState, scrollBehavior: TopAppBarScrollBehavior) {
    if (listState.firstVisibleItemIndex > SCROLL_TO_TOP_JUMP) listState.scrollToItem(SCROLL_TO_TOP_JUMP)
    listState.animateScrollToItem(0)
    val barState = scrollBehavior.state
    animate(barState.heightOffset, 0f) { value, _ -> barState.heightOffset = value }
}

@Composable
private fun JumpToDateDialog(
    initial: LocalDate,
    firstDay: LocalDate,
    lastDay: LocalDate,
    onDismiss: () -> Unit,
    onPick: (LocalDate) -> Unit,
) {
    fun LocalDate.utcMillis() = atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()
    val first = firstDay.utcMillis()
    val last = lastDay.utcMillis()
    val pickerState = rememberDatePickerState(
        initialSelectedDateMillis = initial.utcMillis(),
        yearRange = firstDay.year..lastDay.year,
        selectableDates = object : SelectableDates {
            override fun isSelectableDate(utcTimeMillis: Long) = utcTimeMillis in first..last
            override fun isSelectableYear(year: Int) = year in firstDay.year..lastDay.year
        },
    )
    DatePickerDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(
                onClick = { pickerState.selectedDateMillis?.let { onPick(Instant.ofEpochMilli(it).atZone(ZoneOffset.UTC).toLocalDate()) } },
                enabled = pickerState.selectedDateMillis != null,
            ) { Text(stringResource(R.string.timeline_jump)) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.timeline_cancel)) } },
    ) { DatePicker(pickerState) }
}

/**
 * A pending scroll to [date]. With [diaryId], it waits until the list shows that diary; [expanding]
 * marks that the quiet stretch hiding the day has already been asked to open.
 */
private data class DayJump(val date: LocalDate, val diaryId: Long? = null, val expanding: Boolean = false)

private fun greetingRes(greeting: Greeting) = when (greeting) {
    Greeting.Morning -> R.string.timeline_greeting_morning
    Greeting.Afternoon -> R.string.timeline_greeting_afternoon
    Greeting.Evening -> R.string.timeline_greeting_evening
    Greeting.Night -> R.string.timeline_greeting_night
}

@Composable
private fun Subtitle(state: TimelineUiState.Ready, formats: DakilioFormats) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(formats.fullDate(state.today, state.today))
        if (state.streak > 1) {
            Row(
                modifier = Modifier
                    .padding(start = Spacing.s)
                    .background(MaterialTheme.colorScheme.tertiaryContainer, CircleShape)
                    .padding(start = 6.dp, end = 10.dp, top = 2.dp, bottom = 2.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                DakilioIcon(
                    DakilioSymbols.LocalFireDepartment,
                    contentDescription = null,
                    filled = true,
                    tint = MaterialTheme.colorScheme.onTertiaryContainer,
                    modifier = Modifier.size(16.dp),
                )
                Text(
                    pluralStringResource(R.plurals.timeline_streak, state.streak, state.streak),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onTertiaryContainer,
                )
            }
        }
    }
}

@Composable
private fun MonthDivider(item: TimelineItem.MonthDivider, formats: DakilioFormats, modifier: Modifier) {
    Row(modifier.fillMaxWidth().padding(top = Spacing.xl, bottom = Spacing.xs), verticalAlignment = Alignment.CenterVertically) {
        Text(
            formats.monthYear(item.month.atDay(1)),
            style = MaterialTheme.typography.titleSmallEmphasized,
            color = MaterialTheme.colorScheme.primary,
        )
        HorizontalDivider(Modifier.padding(start = Spacing.m).weight(1f), color = MaterialTheme.colorScheme.outlineVariant)
    }
}

@Composable
private fun DayHeader(item: TimelineItem.DayHeader, today: LocalDate, formats: DakilioFormats, modifier: Modifier) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(start = Spacing.xs, top = Spacing.l, bottom = Spacing.s)
            .semantics(mergeDescendants = true) { heading() },
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            item.date.dayOfMonth.toString(),
            style = MaterialTheme.typography.headlineLargeEmphasized,
            color = MaterialTheme.colorScheme.onSurface,
        )
        Column(Modifier.padding(start = Spacing.m).weight(1f)) {
            Text(dayTitle(item.date, today, formats), style = MaterialTheme.typography.titleMediumEmphasized, maxLines = 1)
            Text(
                formats.monthYear(item.date),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        // The day at a glance: its moods as a little overlapping stack.
        Row(horizontalArrangement = Arrangement.spacedBy((-6).dp)) {
            item.moods.take(5).forEach { MoodBadge(it, size = 22.dp) }
        }
    }
}

@Composable
private fun CheckInCard(moods: List<Mood>, onMood: (Mood) -> Unit, modifier: Modifier) {
    val haptics = rememberHaptics()
    val levels = moods.levelsBestFirst()
    val anchors = remember { mutableMapOf<Int, Rect>() }
    var openLevel by remember { mutableStateOf<Int?>(null) }
    val open = openLevel?.let { level -> levels.firstOrNull { it.first().level == level } }
    if (open != null) {
        MoodAliasOverlay(
            moods = open,
            selectedId = null,
            anchor = anchors[open.first().level] ?: Rect.Zero,
            wide = false,
            faceSize = 52.dp,
            onPick = {
                openLevel = null
                onMood(it)
            },
            onDismiss = { openLevel = null },
        )
    }
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(SegmentedShapes.outer),
        color = MaterialTheme.colorScheme.primaryContainer,
        contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
    ) {
        Column(Modifier.padding(Spacing.xl)) {
            Text(stringResource(R.string.timeline_check_in_title), style = MaterialTheme.typography.headlineSmallEmphasized)
            Hint {
                Text(
                    stringResource(R.string.timeline_check_in_body),
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.padding(top = Spacing.xs),
                )
            }
            Spacer(Modifier.height(Spacing.l))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                levels.forEach { levelMoods ->
                    val primary = levelMoods.first()
                    val interaction = remember { MutableInteractionSource() }
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier
                            .onGloballyPositioned { anchors[primary.level] = it.screenRect() }
                            .pressScale(interaction, 0.88f)
                            .clickable(interactionSource = interaction, indication = null) {
                                haptics.confirm()
                                // Nothing is selected yet, so a level with aliases lists them right away.
                                if (levelMoods.size > 1) openLevel = primary.level else onMood(primary)
                            },
                    ) {
                        MoodBadge(primary, size = 52.dp, hasAliases = levelMoods.size > 1)
                        Text(primary.label, style = MaterialTheme.typography.labelMedium, modifier = Modifier.padding(top = Spacing.xs))
                    }
                }
            }
        }
    }
}

@Composable
private fun EmptyDayRow(
    date: LocalDate,
    otherDiaries: List<Diary>,
    today: LocalDate,
    formats: DakilioFormats,
    onAdd: () -> Unit,
    modifier: Modifier,
) {
    val label = relativeDayLabel(date, today, formats)
    Row(
        modifier = modifier.fillMaxWidth().padding(start = Spacing.xs, top = Spacing.xs),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            date.dayOfMonth.toString(),
            style = MaterialTheme.typography.titleLargeEmphasized,
            color = MaterialTheme.colorScheme.outline,
            modifier = Modifier.widthIn(min = 36.dp),
        )
        Column(Modifier.weight(1f).padding(start = Spacing.s)) {
            Text(label, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(stringResource(R.string.timeline_no_entries), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.outline)
                if (otherDiaries.isNotEmpty()) DiaryHint(otherDiaries, Modifier.padding(start = Spacing.s))
            }
        }
        IconButton(onClick = onAdd) {
            DakilioIcon(
                DakilioSymbols.Add,
                contentDescription = stringResource(R.string.timeline_add_for_day, label),
                tint = MaterialTheme.colorScheme.outline,
            )
        }
    }
}

@Composable
private fun QuietStretchRow(stretch: TimelineItem.QuietStretch, formats: DakilioFormats, onToggle: () -> Unit, modifier: Modifier) {
    val rotation by animateFloatAsState(if (stretch.expanded) 180f else 0f, MaterialTheme.motionScheme.defaultSpatialSpec(), label = "chevron")
    Surface(
        onClick = onToggle,
        modifier = modifier.fillMaxWidth().padding(top = Spacing.m),
        shape = RoundedCornerShape(20.dp),
        color = MaterialTheme.colorScheme.surfaceContainerLow,
    ) {
        Row(Modifier.padding(horizontal = Spacing.l, vertical = Spacing.m), verticalAlignment = Alignment.CenterVertically) {
            DakilioIcon(DakilioSymbols.Bedtime, contentDescription = null, tint = MaterialTheme.colorScheme.tertiary)
            Column(Modifier.weight(1f).padding(start = Spacing.m)) {
                Text(
                    pluralStringResource(R.plurals.timeline_quiet_days, stretch.days, stretch.days),
                    style = MaterialTheme.typography.titleSmallEmphasized,
                )
                Text(
                    stringResource(R.string.timeline_quiet_range, formats.dayMonthShort(stretch.oldest), formats.dayMonthShort(stretch.newest)),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            if (stretch.otherDiaries.isNotEmpty()) DiaryHint(stretch.otherDiaries, Modifier.padding(end = Spacing.s))
            DakilioIcon(DakilioSymbols.ExpandMore, contentDescription = null, modifier = Modifier.graphicsLayer { rotationZ = rotation })
        }
    }
}

private const val DIARY_HINT_MAX = 3

/** A quiet, read-only hint of the diaries that have entries on an otherwise empty day. */
@Composable
private fun DiaryHint(diaries: List<Diary>, modifier: Modifier = Modifier) {
    val description = stringResource(R.string.timeline_entries_in, diaries.joinToString { it.name })
    val tint = MaterialTheme.colorScheme.outline
    Row(
        modifier = modifier.clearAndSetSemantics { contentDescription = description },
        horizontalArrangement = Arrangement.spacedBy(2.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        diaries.take(DIARY_HINT_MAX).forEach { diary ->
            DakilioIcon(symbolForKey(diary.iconKey), contentDescription = null, tint = tint, modifier = Modifier.size(16.dp))
        }
        if (diaries.size > DIARY_HINT_MAX) {
            Text("+${diaries.size - DIARY_HINT_MAX}", style = MaterialTheme.typography.labelSmall, color = tint)
        }
    }
}

@Composable
private fun OtherDiariesRow(item: TimelineItem.OtherDiaries, onSelectDiary: (Long) -> Unit, modifier: Modifier) {
    Column(modifier.fillMaxWidth().padding(top = Spacing.s)) {
        Text(
            stringResource(R.string.timeline_also_in),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(start = Spacing.xs),
        )
        FlowRow(horizontalArrangement = Arrangement.spacedBy(Spacing.s)) {
            item.chips.forEach { chip ->
                AssistChip(
                    onClick = { onSelectDiary(chip.diary.id) },
                    label = { Text(stringResource(R.string.timeline_other_diary, chip.diary.name, chip.count)) },
                    leadingIcon = {
                        DakilioIcon(symbolForKey(chip.diary.iconKey), contentDescription = null, modifier = Modifier.size(AssistChipDefaults.IconSize))
                    },
                    shape = CircleShape,
                )
            }
        }
        Spacer(Modifier.height(Spacing.xs))
    }
}
