package pl.dakil.dakilio.feature.reminders.impl

import android.text.format.DateFormat
import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.TimePickerDialog
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import java.time.DayOfWeek
import java.time.LocalTime
import pl.dakil.dakilio.core.designsystem.component.ConnectedToggleRow
import pl.dakil.dakilio.core.designsystem.component.SectionHeader
import pl.dakil.dakilio.core.designsystem.component.ToggleOption
import pl.dakil.dakilio.core.designsystem.component.rememberHaptics
import pl.dakil.dakilio.core.designsystem.icon.DakilioIcon
import pl.dakil.dakilio.core.designsystem.icon.DakilioSymbol
import pl.dakil.dakilio.core.designsystem.icon.DakilioSymbols
import pl.dakil.dakilio.core.designsystem.icon.symbolForKey
import pl.dakil.dakilio.core.designsystem.theme.Spacing
import pl.dakil.dakilio.core.model.Diary
import pl.dakil.dakilio.core.model.Habit
import pl.dakil.dakilio.core.model.Reminder
import pl.dakil.dakilio.core.model.ReminderDays
import pl.dakil.dakilio.core.model.ReminderTarget
import pl.dakil.dakilio.core.model.minuteOfDay
import pl.dakil.dakilio.core.ui.component.WeekdayToggles
import pl.dakil.dakilio.core.ui.rememberFormats

private enum class Kind { Diary, Habit }

/**
 * Creates a reminder ([reminder] `null`) or edits one: what it's about (a diary or a habit), the
 * time, and the weekdays it comes on. A switched-off reminder stays off when edited.
 */
@Composable
internal fun ReminderSheet(
    reminder: Reminder?,
    diaries: List<Diary>,
    habits: List<Habit>,
    onSave: (Reminder) -> Unit,
    onDelete: () -> Unit,
    onDismiss: () -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val haptics = rememberHaptics()
    val formats = rememberFormats()
    val initial = reminder?.target
    var kind by rememberSaveable { mutableStateOf(if (initial is ReminderTarget.Habit) Kind.Habit else Kind.Diary) }
    // With a single diary there's nothing to choose.
    var diaryId by rememberSaveable { mutableStateOf((initial as? ReminderTarget.Diary)?.id ?: diaries.singleOrNull()?.id) }
    var habitId by rememberSaveable { mutableStateOf((initial as? ReminderTarget.Habit)?.id ?: habits.singleOrNull()?.id) }
    var minute by rememberSaveable { mutableIntStateOf((reminder?.time ?: Reminder.DEFAULT_TIME).minuteOfDay()) }
    var daysMask by rememberSaveable { mutableIntStateOf(ReminderDays.toMask(reminder?.days ?: ReminderDays.EVERY_DAY)) }
    var pickTime by rememberSaveable { mutableStateOf(false) }
    val time = Reminder.timeOf(minute)
    val days = ReminderDays.fromMask(daysMask)
    val diary = diaries.firstOrNull { it.id == diaryId }
    val habit = habits.firstOrNull { it.id == habitId }
    val target = when (kind) {
        Kind.Diary -> diary?.let { ReminderTarget.Diary(it.id) }
        Kind.Habit -> habit?.let { ReminderTarget.Habit(it.id) }
    }

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState) {
        Column(
            Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .navigationBarsPadding()
                .padding(horizontal = Spacing.xl)
                .padding(bottom = Spacing.l),
        ) {
            Text(
                stringResource(if (reminder == null) R.string.reminders_sheet_new else R.string.reminders_sheet_edit),
                style = MaterialTheme.typography.headlineSmallEmphasized,
            )

            SectionHeader(stringResource(R.string.reminders_sheet_about), Modifier.padding(top = Spacing.l))
            ConnectedToggleRow(
                options = listOf(
                    ToggleOption(Kind.Diary, stringResource(R.string.reminders_sheet_kind_diary), DakilioSymbols.Book2),
                    ToggleOption(Kind.Habit, stringResource(R.string.reminders_sheet_kind_habit), DakilioSymbols.CheckCircle),
                ),
                selected = kind,
                onSelect = { kind = it },
                modifier = Modifier.padding(top = Spacing.s),
            )
            when (kind) {
                Kind.Diary -> Choices(
                    items = diaries,
                    selectedId = diaryId,
                    id = { it.id },
                    label = { it.name },
                    symbol = { symbolForKey(it.iconKey) },
                    onSelect = { diaryId = it },
                )
                Kind.Habit -> if (habits.isEmpty()) {
                    Text(
                        stringResource(R.string.reminders_sheet_no_habits),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = Spacing.m),
                    )
                } else {
                    Choices(
                        items = habits,
                        selectedId = habitId,
                        id = { it.id },
                        label = { it.tag.label },
                        symbol = { symbolForKey(it.tag.iconKey) },
                        onSelect = { habitId = it },
                    )
                }
            }

            SectionHeader(stringResource(R.string.reminders_sheet_time), Modifier.padding(top = Spacing.l))
            Surface(
                onClick = { pickTime = true },
                shape = RoundedCornerShape(24.dp),
                color = MaterialTheme.colorScheme.surfaceContainerHighest,
                modifier = Modifier.padding(top = Spacing.s),
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(Spacing.m),
                    modifier = Modifier.padding(horizontal = Spacing.l, vertical = Spacing.s),
                ) {
                    DakilioIcon(DakilioSymbols.Schedule, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                    Text(formats.time(time), style = MaterialTheme.typography.displaySmallEmphasized)
                }
            }

            SectionHeader(stringResource(R.string.reminders_sheet_days), Modifier.padding(top = Spacing.l))
            WeekdayToggles(
                days = days,
                onToggle = { day ->
                    // At least one day stays on.
                    val next = if (day in days) days - day else days + day
                    if (next.isNotEmpty()) daysMask = ReminderDays.toMask(next)
                },
                modifier = Modifier.padding(top = Spacing.s),
            )

            Text(
                summary(kind, diary, habit, days, time),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = Spacing.m).animateContentSize(),
            )

            Row(
                Modifier.fillMaxWidth().padding(top = Spacing.l),
                horizontalArrangement = Arrangement.spacedBy(Spacing.s, Alignment.End),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (reminder != null) {
                    TextButton(
                        onClick = onDelete,
                        colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error),
                    ) { Text(stringResource(R.string.reminders_sheet_delete)) }
                    Spacer(Modifier.weight(1f))
                }
                Button(
                    onClick = {
                        val chosen = target ?: return@Button
                        haptics.confirm()
                        onSave(
                            Reminder(
                                id = reminder?.id ?: 0,
                                target = chosen,
                                time = time,
                                days = days,
                                enabled = reminder?.enabled ?: true,
                            ),
                        )
                    },
                    enabled = target != null,
                ) {
                    Text(stringResource(if (reminder == null) R.string.reminders_sheet_add else R.string.reminders_sheet_save))
                }
            }
        }
    }

    if (pickTime) {
        val pickerState = rememberTimePickerState(
            initialHour = time.hour,
            initialMinute = time.minute,
            is24Hour = DateFormat.is24HourFormat(LocalContext.current),
        )
        TimePickerDialog(
            onDismissRequest = { pickTime = false },
            title = { Text(stringResource(R.string.reminders_pick_time)) },
            confirmButton = {
                TextButton(onClick = {
                    minute = LocalTime.of(pickerState.hour, pickerState.minute).minuteOfDay()
                    pickTime = false
                }) { Text(stringResource(R.string.reminders_ok)) }
            },
            dismissButton = { TextButton(onClick = { pickTime = false }) { Text(stringResource(R.string.reminders_cancel)) } },
        ) { TimePicker(pickerState) }
    }
}

/** The diaries or habits to pick from, as chips; a tap on the picked one keeps it. */
@Composable
private fun <T> Choices(
    items: List<T>,
    selectedId: Long?,
    id: (T) -> Long,
    label: (T) -> String,
    symbol: (T) -> DakilioSymbol,
    onSelect: (Long) -> Unit,
) {
    val haptics = rememberHaptics()
    FlowRow(
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        modifier = Modifier
            .padding(top = Spacing.s)
            .heightIn(max = 240.dp)
            .verticalScroll(rememberScrollState()),
    ) {
        items.forEach { item ->
            val selected = id(item) == selectedId
            FilterChip(
                selected = selected,
                onClick = {
                    if (!selected) haptics.toggle(true)
                    onSelect(id(item))
                },
                label = { Text(label(item)) },
                leadingIcon = {
                    DakilioIcon(symbol(item), contentDescription = null, filled = selected, modifier = Modifier.size(18.dp))
                },
                shape = CircleShape,
            )
        }
    }
}

/** What the reminder will do, in a sentence, e.g. "Every day at 20:00, unless Work already has an entry that day". */
@Composable
private fun summary(kind: Kind, diary: Diary?, habit: Habit?, days: Set<DayOfWeek>, time: LocalTime): String {
    val formats = rememberFormats()
    val whenText = when (ReminderDays.kind(days)) {
        ReminderDays.Kind.EveryDay -> stringResource(R.string.reminders_when_every_day, formats.time(time))
        ReminderDays.Kind.Workdays -> stringResource(R.string.reminders_when_workdays, formats.time(time))
        ReminderDays.Kind.Weekend -> stringResource(R.string.reminders_when_weekend, formats.time(time))
        ReminderDays.Kind.Custom -> stringResource(R.string.reminders_when_days, daysLabel(days, formats), formats.time(time))
    }
    return when (kind) {
        Kind.Diary -> if (diary == null) {
            stringResource(R.string.reminders_sheet_pick_diary)
        } else {
            stringResource(R.string.reminders_summary_diary, whenText, diary.name)
        }
        Kind.Habit -> if (habit == null) {
            stringResource(R.string.reminders_sheet_pick_habit)
        } else {
            pluralStringResource(R.plurals.reminders_summary_habit, habit.daysPerWeek, whenText, habit.tag.label, habit.daysPerWeek)
        }
    }
}
