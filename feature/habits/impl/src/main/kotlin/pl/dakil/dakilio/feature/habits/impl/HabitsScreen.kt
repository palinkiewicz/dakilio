package pl.dakil.dakilio.feature.habits.impl

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialShapes
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MediumFlexibleTopAppBar
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedListItem
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import java.time.LocalDate
import kotlinx.coroutines.launch
import pl.dakil.dakilio.core.designsystem.component.ShapedIcon
import pl.dakil.dakilio.core.designsystem.component.segmentedItemColors
import pl.dakil.dakilio.core.designsystem.icon.DakilioIcon
import pl.dakil.dakilio.core.designsystem.icon.DakilioSymbols
import pl.dakil.dakilio.core.designsystem.icon.symbolForKey
import pl.dakil.dakilio.core.designsystem.theme.Spacing
import pl.dakil.dakilio.core.model.HabitStatus
import pl.dakil.dakilio.core.model.Habits
import pl.dakil.dakilio.core.ui.Hint
import pl.dakil.dakilio.core.ui.color.tagColors
import pl.dakil.dakilio.core.ui.component.EmptyState
import pl.dakil.dakilio.core.ui.LocalFirstDayOfWeek
import pl.dakil.dakilio.core.ui.rememberFormats

/** What the habit sheet is open for. Editing goes by id, so the sheet follows live changes. */
private sealed interface SheetTarget {
    data object New : SheetTarget
    data class Edit(val habitId: Long) : SheetTarget
}

/**
 * More → Extras → Habits: tags the user wants on their entries a number of days a week, with this
 * week's days and last week's result.
 */
@Composable
internal fun HabitsScreen(viewModel: HabitsViewModel, onBack: () -> Unit) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()
    val listState = rememberLazyListState()
    val snackbar = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    var sheet by remember { mutableStateOf<SheetTarget?>(null) }

    val deletedMessage = stringResource(R.string.habits_deleted)
    val undoLabel = stringResource(R.string.habits_undo)

    Scaffold(
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        snackbarHost = { SnackbarHost(snackbar) },
        topBar = {
            MediumFlexibleTopAppBar(
                title = { Text(stringResource(R.string.habits_title)) },
                subtitle = { Text(stringResource(R.string.habits_subtitle)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        DakilioIcon(DakilioSymbols.ArrowBack, contentDescription = stringResource(R.string.habits_back))
                    }
                },
                scrollBehavior = scrollBehavior,
            )
        },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = { sheet = SheetTarget.New },
                expanded = !listState.canScrollBackward,
                icon = { DakilioIcon(DakilioSymbols.Add, contentDescription = null) },
                text = { Text(stringResource(R.string.habits_new)) },
            )
        },
    ) { padding ->
        val current = state
        Box(Modifier.fillMaxSize().padding(top = padding.calculateTopPadding()), contentAlignment = Alignment.TopCenter) {
            when {
                current == null -> LoadingIndicator(Modifier.align(Alignment.Center))
                current.habits.isEmpty() -> EmptyState(
                    symbol = DakilioSymbols.CheckCircle,
                    title = stringResource(R.string.habits_empty_title),
                    body = stringResource(R.string.habits_empty_body),
                    modifier = Modifier.align(Alignment.Center),
                )
                else -> LazyColumn(
                    state = listState,
                    modifier = Modifier.widthIn(max = Spacing.maxContentWidth).fillMaxSize(),
                    contentPadding = PaddingValues(
                        start = Spacing.gutter,
                        end = Spacing.gutter,
                        top = Spacing.s,
                        // Room for the FAB.
                        bottom = padding.calculateBottomPadding() + 96.dp,
                    ),
                    verticalArrangement = Arrangement.spacedBy(ListItemDefaults.SegmentedGap),
                ) {
                    item(key = "hint") {
                        Hint {
                            Text(
                                stringResource(R.string.habits_hint),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(start = Spacing.xs, end = Spacing.xs, bottom = Spacing.m),
                            )
                        }
                    }
                    itemsIndexed(current.habits, key = { _, it -> it.habit.id }) { index, status ->
                        HabitItem(
                            status = status,
                            today = current.today,
                            index = index,
                            count = current.habits.size,
                            onClick = { sheet = SheetTarget.Edit(status.habit.id) },
                            modifier = Modifier.animateItem(),
                        )
                    }
                }
            }
        }
    }

    val current = state ?: return
    when (val target = sheet) {
        null -> Unit
        SheetTarget.New -> HabitSheet(
            habit = null,
            groups = current.groups,
            trackedTagIds = current.trackedTagIds,
            onSave = { tagId, days ->
                viewModel.add(tagId, days)
                sheet = null
            },
            onDelete = {},
            onDismiss = { sheet = null },
        )
        is SheetTarget.Edit -> {
            val habit = current.habits.firstOrNull { it.habit.id == target.habitId }?.habit
            // Gone (its tag was deleted elsewhere): close.
            LaunchedEffect(habit == null) { if (habit == null) sheet = null }
            if (habit != null) {
                HabitSheet(
                    habit = habit,
                    groups = current.groups,
                    trackedTagIds = current.trackedTagIds,
                    onSave = { _, days ->
                        viewModel.update(habit, days)
                        sheet = null
                    },
                    onDelete = {
                        sheet = null
                        val deleted = viewModel.delete(habit)
                        scope.launch {
                            snackbar.currentSnackbarData?.dismiss()
                            val result = snackbar.showSnackbar(deletedMessage, undoLabel, withDismissAction = true)
                            if (result == SnackbarResult.ActionPerformed) viewModel.restore(deleted)
                        }
                    },
                    onDismiss = { sheet = null },
                )
            }
        }
    }
}

@Composable
private fun HabitItem(
    status: HabitStatus,
    today: LocalDate,
    index: Int,
    count: Int,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val habit = status.habit
    val target = habit.daysPerWeek
    val reached = status.thisWeekCount >= target
    val lastMet = status.lastWeekCount >= target
    val colors = tagColors(habit.tag.color)
    SegmentedListItem(
        onClick = onClick,
        shapes = ListItemDefaults.segmentedShapes(index, count),
        colors = segmentedItemColors(),
        modifier = modifier,
        leadingContent = {
            ShapedIcon(
                symbolForKey(habit.tag.iconKey),
                polygon = if (reached) MaterialShapes.Cookie9Sided else MaterialShapes.Circle,
                size = 48.dp,
                containerColor = colors?.container ?: MaterialTheme.colorScheme.secondaryContainer,
                contentColor = colors?.onContainer ?: MaterialTheme.colorScheme.onSecondaryContainer,
            )
        },
        supportingContent = {
            Column(verticalArrangement = Arrangement.spacedBy(Spacing.xs), modifier = Modifier.padding(top = Spacing.xs)) {
                WeekDots(days = status.thisWeekDays, today = today)
                Text(
                    when {
                        habit.tag.isArchived -> stringResource(R.string.habits_archived_tag)
                        reached -> stringResource(R.string.habits_goal_reached)
                        else -> stringResource(R.string.habits_this_week, status.thisWeekCount, target)
                    },
                    style = MaterialTheme.typography.labelMedium,
                    color = if (reached && !habit.tag.isArchived) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        },
        trailingContent = {
            Column(horizontalAlignment = Alignment.End) {
                Text(
                    stringResource(R.string.habits_last_week),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                    if (lastMet) {
                        DakilioIcon(
                            DakilioSymbols.CheckCircle,
                            contentDescription = stringResource(R.string.habits_last_week_met),
                            filled = true,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(16.dp),
                        )
                    }
                    Text(
                        stringResource(R.string.habits_last_week_count, status.lastWeekCount, target),
                        style = MaterialTheme.typography.titleSmall,
                        color = if (lastMet) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                    )
                }
            }
        },
    ) { Text(habit.tag.label) }
}

/** The days of [today]'s week: filled on days with the tag, today ringed, days to come faded. */
@Composable
private fun WeekDots(days: Set<LocalDate>, today: LocalDate) {
    val formats = rememberFormats()
    val start = Habits.weekStart(today, LocalFirstDayOfWeek.current)
    val colors = MaterialTheme.colorScheme
    Row(horizontalArrangement = Arrangement.spacedBy(Spacing.xs)) {
        repeat(7) { offset ->
            val date = start.plusDays(offset.toLong())
            val done = date in days
            val letter = formats.weekdayNarrow(date.dayOfWeek)
            val description = stringResource(if (done) R.string.habits_day_done else R.string.habits_day_not_done, formats.weekday(date))
            Box(
                modifier = Modifier
                    .size(22.dp)
                    .alpha(if (date > today) 0.5f else 1f)
                    .background(if (done) colors.primary else colors.surfaceContainerHighest, CircleShape)
                    .then(if (date == today) Modifier.border(1.5.dp, colors.primary, CircleShape) else Modifier)
                    .clearAndSetSemantics { contentDescription = description },
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    letter,
                    style = MaterialTheme.typography.labelSmall,
                    color = if (done) colors.onPrimary else colors.onSurfaceVariant,
                )
            }
        }
    }
}
