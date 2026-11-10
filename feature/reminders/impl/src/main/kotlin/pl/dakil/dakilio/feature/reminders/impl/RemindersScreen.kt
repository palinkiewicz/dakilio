package pl.dakil.dakilio.feature.reminders.impl

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import java.time.DayOfWeek
import kotlinx.coroutines.launch
import pl.dakil.dakilio.core.designsystem.component.ShapedIcon
import pl.dakil.dakilio.core.designsystem.component.rememberHaptics
import pl.dakil.dakilio.core.designsystem.component.segmentedItemColors
import pl.dakil.dakilio.core.designsystem.icon.DakilioIcon
import pl.dakil.dakilio.core.designsystem.icon.DakilioSymbol
import pl.dakil.dakilio.core.designsystem.icon.DakilioSymbols
import pl.dakil.dakilio.core.designsystem.icon.symbolForKey
import pl.dakil.dakilio.core.designsystem.theme.Spacing
import pl.dakil.dakilio.core.model.ReminderDays
import pl.dakil.dakilio.core.ui.DakilioFormats
import pl.dakil.dakilio.core.ui.Hint
import pl.dakil.dakilio.core.ui.LocalFirstDayOfWeek
import pl.dakil.dakilio.core.ui.color.tagColors
import pl.dakil.dakilio.core.ui.component.EmptyState
import pl.dakil.dakilio.core.ui.rememberFormats
import pl.dakil.dakilio.core.ui.weekdaysFrom

/** The sheet open for a new reminder (-1) or for the reminder with this id; editing follows live changes. */
private const val NEW_SHEET = -1L

/**
 * More → Reminders: every reminder in one place, each about a diary or a habit, at a time and on
 * chosen weekdays, with a switch to pause it.
 */
@Composable
internal fun RemindersScreen(viewModel: RemindersViewModel, onBack: () -> Unit) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()
    val listState = rememberLazyListState()
    val snackbar = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val access = rememberReminderAccess()
    var sheet by rememberSaveable { mutableStateOf<Long?>(null) }

    val deletedMessage = stringResource(R.string.reminders_deleted)
    val undoLabel = stringResource(R.string.reminders_undo)

    Scaffold(
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        snackbarHost = { SnackbarHost(snackbar) },
        topBar = {
            MediumFlexibleTopAppBar(
                title = { Text(stringResource(R.string.reminders_title)) },
                subtitle = { Text(stringResource(R.string.reminders_subtitle)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        DakilioIcon(DakilioSymbols.ArrowBack, contentDescription = stringResource(R.string.reminders_back))
                    }
                },
                scrollBehavior = scrollBehavior,
            )
        },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = { sheet = NEW_SHEET },
                expanded = !listState.canScrollBackward,
                icon = { DakilioIcon(DakilioSymbols.Add, contentDescription = null) },
                text = { Text(stringResource(R.string.reminders_new)) },
            )
        },
    ) { padding ->
        val current = state
        Box(Modifier.fillMaxSize().padding(top = padding.calculateTopPadding()), contentAlignment = Alignment.TopCenter) {
            when {
                current == null -> LoadingIndicator(Modifier.align(Alignment.Center))
                current.items.isEmpty() -> EmptyState(
                    symbol = DakilioSymbols.NotificationsActive,
                    title = stringResource(R.string.reminders_empty_title),
                    body = stringResource(R.string.reminders_empty_body),
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
                    if (current.anyEnabled && !access.notificationsAllowed) {
                        item(key = "blocked") {
                            BlockedNotice(onOpenSettings = access::openNotificationSettings, modifier = Modifier.padding(bottom = Spacing.m))
                        }
                    }
                    item(key = "hint") {
                        Hint {
                            Text(
                                stringResource(R.string.reminders_hint),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(start = Spacing.xs, end = Spacing.xs, bottom = Spacing.m),
                            )
                        }
                    }
                    itemsIndexed(current.items, key = { _, it -> it.reminder.id }) { index, item ->
                        ReminderRow(
                            item = item,
                            index = index,
                            count = current.items.size,
                            onClick = { sheet = item.reminder.id },
                            onEnabledChange = { on ->
                                if (on) access.request()
                                viewModel.setEnabled(item.reminder, on)
                            },
                            modifier = Modifier.animateItem(),
                        )
                    }
                    if (current.anyEnabled && !access.exactTiming) {
                        item(key = "exact") {
                            AccessTip(
                                symbol = DakilioSymbols.Alarm,
                                title = stringResource(R.string.reminders_exact_title),
                                body = stringResource(R.string.reminders_exact_body),
                                action = stringResource(R.string.reminders_exact_action),
                                onAction = access::openExactTimingSettings,
                                modifier = Modifier.padding(top = Spacing.l).animateItem(),
                            )
                        }
                    }
                }
            }
        }
    }

    val current = state ?: return
    when (val target = sheet) {
        null -> Unit
        NEW_SHEET -> ReminderSheet(
            reminder = null,
            diaries = current.diaries,
            habits = current.habits,
            onSave = { reminder ->
                access.request()
                viewModel.save(reminder)
                sheet = null
            },
            onDelete = {},
            onDismiss = { sheet = null },
        )
        else -> {
            val item = current.items.firstOrNull { it.reminder.id == target }
            // Gone (its diary or habit was deleted elsewhere): close.
            LaunchedEffect(item == null) { if (item == null) sheet = null }
            if (item != null) {
                ReminderSheet(
                    reminder = item.reminder,
                    diaries = current.diaries,
                    habits = current.habits,
                    onSave = { reminder ->
                        if (reminder.enabled) access.request()
                        if (reminder != item.reminder) viewModel.save(reminder)
                        sheet = null
                    },
                    onDelete = {
                        sheet = null
                        viewModel.delete(item.reminder)
                        scope.launch {
                            snackbar.currentSnackbarData?.dismiss()
                            val result = snackbar.showSnackbar(deletedMessage, undoLabel, withDismissAction = true)
                            if (result == SnackbarResult.ActionPerformed) viewModel.restore(item.reminder)
                        }
                    },
                    onDismiss = { sheet = null },
                )
            }
        }
    }
}

@Composable
private fun ReminderRow(
    item: ReminderItem,
    index: Int,
    count: Int,
    onClick: () -> Unit,
    onEnabledChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    val haptics = rememberHaptics()
    val formats = rememberFormats()
    val reminder = item.reminder
    val on = reminder.enabled
    val tagColors = item.habit?.let { tagColors(it.tag.color) }
    val iconKey = item.diary?.iconKey ?: item.habit?.tag?.iconKey.orEmpty()
    val name = item.diary?.name ?: item.habit?.tag?.label.orEmpty()
    val toggle = { value: Boolean ->
        haptics.toggle(value)
        onEnabledChange(value)
    }
    SegmentedListItem(
        onClick = onClick,
        shapes = ListItemDefaults.segmentedShapes(index, count),
        colors = segmentedItemColors(),
        modifier = modifier,
        leadingContent = {
            ShapedIcon(
                symbolForKey(iconKey),
                polygon = if (on) MaterialShapes.Cookie9Sided else MaterialShapes.Circle,
                size = 48.dp,
                containerColor = when {
                    !on -> MaterialTheme.colorScheme.surfaceContainerHighest
                    tagColors != null -> tagColors.container
                    else -> MaterialTheme.colorScheme.secondaryContainer
                },
                contentColor = when {
                    !on -> MaterialTheme.colorScheme.onSurfaceVariant
                    tagColors != null -> tagColors.onContainer
                    else -> MaterialTheme.colorScheme.onSecondaryContainer
                },
            )
        },
        supportingContent = {
            Column(Modifier.alpha(if (on) 1f else DISABLED_ALPHA)) {
                Text(
                    stringResource(if (item.habit != null) R.string.reminders_about_habit else R.string.reminders_about_diary, name),
                    maxLines = 1,
                )
                Text(
                    daysLabel(reminder.days, formats),
                    style = MaterialTheme.typography.labelMedium,
                    color = if (on) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = Spacing.xxs),
                )
            }
        },
        trailingContent = {
            Switch(
                checked = on,
                onCheckedChange = toggle,
                thumbContent = if (on) {
                    { DakilioIcon(DakilioSymbols.Check, contentDescription = null, modifier = Modifier.size(SwitchDefaults.IconSize)) }
                } else {
                    null
                },
            )
        },
    ) {
        Text(
            formats.time(reminder.time),
            style = MaterialTheme.typography.headlineMediumEmphasized,
            modifier = Modifier.alpha(if (on) 1f else DISABLED_ALPHA),
        )
    }
}

/** Some reminder is on, but notifications can't show (denied, or off in the system). */
@Composable
private fun BlockedNotice(onOpenSettings: () -> Unit, modifier: Modifier = Modifier) {
    Surface(
        shape = RoundedCornerShape(20.dp),
        color = MaterialTheme.colorScheme.errorContainer,
        contentColor = MaterialTheme.colorScheme.onErrorContainer,
        modifier = modifier.fillMaxWidth(),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(Spacing.m),
            modifier = Modifier.padding(start = Spacing.l, end = Spacing.s, top = Spacing.s, bottom = Spacing.s),
        ) {
            DakilioIcon(DakilioSymbols.Notifications, contentDescription = null)
            Text(stringResource(R.string.reminders_blocked), style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
            TextButton(onClick = onOpenSettings) { Text(stringResource(R.string.reminders_open_settings)) }
        }
    }
}

/** Something that makes reminders come late or not at all, with the system setting that fixes it. */
@Composable
private fun AccessTip(
    symbol: DakilioSymbol,
    title: String,
    body: String,
    action: String,
    onAction: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        shape = RoundedCornerShape(20.dp),
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        modifier = modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(start = Spacing.l, end = Spacing.s, top = Spacing.l, bottom = Spacing.xs)) {
            Row(horizontalArrangement = Arrangement.spacedBy(Spacing.m), modifier = Modifier.padding(end = Spacing.s)) {
                DakilioIcon(symbol, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                Column(Modifier.weight(1f)) {
                    Text(title, style = MaterialTheme.typography.titleSmall)
                    Text(
                        body,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = Spacing.xxs),
                    )
                }
            }
            TextButton(onClick = onAction, modifier = Modifier.align(Alignment.End)) { Text(action) }
        }
    }
}

private const val DISABLED_ALPHA = 0.6f

/** "Every day", "Weekdays", "Weekends", or the days themselves from the user's first day of the week. */
@Composable
internal fun daysLabel(days: Set<DayOfWeek>, formats: DakilioFormats): String = when (ReminderDays.kind(days)) {
    ReminderDays.Kind.EveryDay -> stringResource(R.string.reminders_days_every_day)
    ReminderDays.Kind.Workdays -> stringResource(R.string.reminders_days_workdays)
    ReminderDays.Kind.Weekend -> stringResource(R.string.reminders_days_weekend)
    ReminderDays.Kind.Custom -> weekdaysFrom(LocalFirstDayOfWeek.current).filter { it in days }.joinToString(", ") { formats.weekdayShort(it) }
}
