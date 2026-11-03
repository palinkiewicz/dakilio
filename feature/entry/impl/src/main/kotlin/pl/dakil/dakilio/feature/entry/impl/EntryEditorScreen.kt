package pl.dakil.dakilio.feature.entry.impl

import android.text.format.DateFormat
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ButtonGroupDefaults
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.IconButton
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SelectableDates
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.TimePickerDialog
import androidx.compose.material3.ToggleButton
import androidx.compose.material3.ToggleButtonDefaults
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import pl.dakil.dakilio.core.designsystem.component.rememberHaptics
import pl.dakil.dakilio.core.designsystem.icon.DakilioIcon
import pl.dakil.dakilio.core.designsystem.icon.DakilioSymbol
import pl.dakil.dakilio.core.designsystem.icon.DakilioSymbols
import pl.dakil.dakilio.core.designsystem.icon.symbolForKey
import pl.dakil.dakilio.core.designsystem.theme.Spacing
import pl.dakil.dakilio.core.model.EntryValidation
import pl.dakil.dakilio.core.ui.component.DiarySelector
import pl.dakil.dakilio.core.ui.rememberFormats
import pl.dakil.dakilio.core.ui.relativeDayLabel
import pl.dakil.dakilio.core.ui.sharedContainer

@Composable
internal fun EntryEditorScreen(
    viewModel: EntryEditorViewModel,
    sharedElementKey: String,
    onClose: () -> Unit,
    onManageTags: () -> Unit,
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val haptics = rememberHaptics()
    var confirmDiscard by rememberSaveable { mutableStateOf(false) }
    var confirmDelete by rememberSaveable { mutableStateOf(false) }
    // Tag groups the user folded open or shut, against their default. Lives for this editor only.
    var toggledGroups by rememberSaveable { mutableStateOf(emptySet<Long>()) }
    val toggleGroup = { id: Long -> toggledGroups = if (id in toggledGroups) toggledGroups - id else toggledGroups + id }

    val requestClose = {
        if (viewModel.hasUnsavedChanges()) confirmDiscard = true else onClose()
    }
    BackHandler(enabled = state?.finished != true, onBack = requestClose)
    LaunchedEffect(state?.finished) { if (state?.finished == true) onClose() }

    Surface(
        modifier = Modifier.fillMaxSize().sharedContainer(sharedElementKey),
        color = MaterialTheme.colorScheme.surface,
    ) {
        val current = state
        if (current == null) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { LoadingIndicator() }
            return@Surface
        }
        val canSave = EntryValidation.canSave(
            hasMood = current.moodId != null,
            tagCount = current.tagIds.size,
            title = viewModel.title.text.toString(),
            note = viewModel.note.text.toString(),
            photoCount = current.photos.size,
            hasDiary = current.diaryId != null,
        ) && current.importingPhotos == 0
        val bodyRequired = current.moodId == null && current.tagIds.isEmpty() && !canSave
        val save = {
            haptics.confirm()
            viewModel.save()
        }
        val readOnly = !current.isNew && !current.editing

        Scaffold(
            containerColor = MaterialTheme.colorScheme.surface,
            topBar = {
                TopAppBar(
                    title = {
                        Text(
                            stringResource(
                                when {
                                    current.isNew -> R.string.entry_new
                                    readOnly -> R.string.entry_view
                                    else -> R.string.entry_edit
                                },
                            ),
                        )
                    },
                    navigationIcon = {
                        IconButton(onClick = requestClose) {
                            DakilioIcon(DakilioSymbols.Close, contentDescription = stringResource(R.string.entry_close))
                        }
                    },
                    actions = {
                        if (!current.isNew) OverflowMenu(onDelete = { confirmDelete = true })
                        val fade = MaterialTheme.motionScheme.fastEffectsSpec<Float>()
                        AnimatedContent(
                            targetState = readOnly,
                            transitionSpec = { fadeIn(fade) togetherWith fadeOut(fade) },
                            label = "editOrSave",
                            modifier = Modifier.padding(end = Spacing.s),
                        ) { reading ->
                            if (reading) {
                                FilledIconButton(onClick = {
                                    haptics.tick()
                                    viewModel.startEditing()
                                }) {
                                    DakilioIcon(DakilioSymbols.Edit, contentDescription = stringResource(R.string.entry_start_editing))
                                }
                            } else {
                                FilledIconButton(onClick = save, enabled = canSave) {
                                    DakilioIcon(DakilioSymbols.Check, contentDescription = stringResource(R.string.entry_save))
                                }
                            }
                        }
                    },
                )
            },
            bottomBar = {
                if (current.isNew) BottomActions(
                    step = current.step,
                    canAdvance = when (current.step) {
                        EditorStep.Mood -> current.moodId != null
                        EditorStep.Tags -> current.tagIds.isNotEmpty()
                        EditorStep.Note -> true
                    },
                    canSave = canSave,
                    onBack = {
                        haptics.tick()
                        viewModel.goTo(EditorStep.entries[current.step.ordinal - 1])
                    },
                    onNext = {
                        haptics.tick()
                        viewModel.goTo(EditorStep.entries[current.step.ordinal + 1])
                    },
                    onSave = save,
                )
            },
        ) { padding ->
            Column(
                Modifier.padding(padding).consumeWindowInsets(padding).fillMaxSize(),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Column(Modifier.widthIn(max = Spacing.maxContentWidth).fillMaxSize()) {
                    DateTimeRow(
                        state = current,
                        readOnly = readOnly,
                        onDate = viewModel::setDate,
                        onTime = viewModel::setTime,
                    )
                    if (!readOnly && current.diaries.size > 1) {
                        DiarySelector(
                            diaries = current.diaries,
                            selectedId = current.diaryId,
                            onSelect = viewModel::selectDiary,
                            modifier = Modifier.padding(top = Spacing.s),
                        )
                    }
                    if (!current.isNew) {
                        EntryOverview(
                            state = current,
                            title = viewModel.title,
                            note = viewModel.note,
                            bodyRequired = bodyRequired,
                            editing = current.editing,
                            toggledGroups = toggledGroups,
                            onToggleGroup = toggleGroup,
                            onSelectMood = { viewModel.selectMood(it) },
                            onClearMood = viewModel::clearMood,
                            onToggleTag = viewModel::toggleTag,
                            onManageTags = onManageTags,
                            onAddPhotos = viewModel::addPhotos,
                            onRemovePhoto = viewModel::removePhoto,
                            modifier = Modifier.weight(1f).padding(top = Spacing.s),
                        )
                    } else {
                        Stepper(
                            state = current,
                            bodyFilled = viewModel.title.text.isNotBlank() || viewModel.note.text.isNotBlank() || current.photos.isNotEmpty(),
                            onStep = {
                                haptics.tick()
                                viewModel.goTo(it)
                            },
                            modifier = Modifier.padding(horizontal = Spacing.gutter, vertical = Spacing.m),
                        )
                        AnimatedContent(
                            targetState = current.step,
                            transitionSpec = {
                                val forward = targetState.ordinal > initialState.ordinal
                                val spatial = MotionSpatial
                                (slideInHorizontally(spatial) { if (forward) it / 4 else -it / 4 } + fadeIn()) togetherWith
                                    (slideOutHorizontally(spatial) { if (forward) -it / 4 else it / 4 } + fadeOut())
                            },
                            label = "step",
                            modifier = Modifier.weight(1f),
                        ) { step ->
                            when (step) {
                                EditorStep.Mood -> MoodStep(
                                    moods = current.moods,
                                    selectedId = current.moodId,
                                    onSelect = viewModel::selectMood,
                                    onClear = viewModel::clearMood,
                                    modifier = Modifier.fillMaxSize(),
                                )
                                EditorStep.Tags -> TagStep(
                                    suggested = current.suggestedGroups,
                                    others = current.otherGroups,
                                    selectedIds = current.tagIds,
                                    style = current.tagStyle,
                                    toggledGroups = toggledGroups,
                                    onToggleGroup = toggleGroup,
                                    onToggle = viewModel::toggleTag,
                                    onManageTags = onManageTags,
                                    modifier = Modifier.fillMaxSize(),
                                )
                                EditorStep.Note -> NoteStep(
                                    title = viewModel.title,
                                    note = viewModel.note,
                                    photos = current.photos,
                                    importing = current.importingPhotos,
                                    bodyRequired = bodyRequired,
                                    onAddPhotos = viewModel::addPhotos,
                                    onRemovePhoto = viewModel::removePhoto,
                                    modifier = Modifier.fillMaxSize(),
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    if (confirmDiscard) {
        AlertDialog(
            onDismissRequest = { confirmDiscard = false },
            icon = { DakilioIcon(DakilioSymbols.DeleteSweep, contentDescription = null) },
            title = { Text(stringResource(R.string.entry_discard_title)) },
            text = { Text(stringResource(R.string.entry_discard_body)) },
            confirmButton = {
                TextButton(onClick = {
                    confirmDiscard = false
                    onClose()
                }) { Text(stringResource(R.string.entry_discard)) }
            },
            dismissButton = { TextButton(onClick = { confirmDiscard = false }) { Text(stringResource(R.string.entry_keep_editing)) } },
        )
    }
    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            icon = { DakilioIcon(DakilioSymbols.Delete, contentDescription = null) },
            title = { Text(stringResource(R.string.entry_delete_title)) },
            text = { Text(stringResource(R.string.entry_delete_body)) },
            confirmButton = {
                TextButton(onClick = {
                    confirmDelete = false
                    haptics.confirm()
                    viewModel.delete()
                }) { Text(stringResource(R.string.entry_delete_confirm)) }
            },
            dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text(stringResource(R.string.entry_cancel)) } },
        )
    }
}

private val MotionSpatial = androidx.compose.animation.core.spring<androidx.compose.ui.unit.IntOffset>(dampingRatio = 0.85f, stiffness = 600f)

@Composable
private fun OverflowMenu(onDelete: () -> Unit) {
    var open by remember { mutableStateOf(false) }
    Box {
        IconButton(onClick = { open = true }) {
            DakilioIcon(DakilioSymbols.MoreVert, contentDescription = stringResource(R.string.entry_more))
        }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }, shape = RoundedCornerShape(20.dp)) {
            DropdownMenuItem(
                text = { Text(stringResource(R.string.entry_delete)) },
                leadingIcon = { DakilioIcon(DakilioSymbols.Delete, contentDescription = null) },
                onClick = {
                    open = false
                    onDelete()
                },
            )
        }
    }
}

@Composable
private fun DateTimeRow(state: EditorUiState, readOnly: Boolean, onDate: (LocalDate) -> Unit, onTime: (Int, Int) -> Unit) {
    val formats = rememberFormats()
    var pickDate by rememberSaveable { mutableStateOf(false) }
    var pickTime by rememberSaveable { mutableStateOf(false) }
    val date = state.dateTime.toLocalDate()
    if (readOnly) {
        val diary = state.diaries.firstOrNull { it.id == state.diaryId }.takeIf { state.diaries.size > 1 }
        Row(
            Modifier.padding(horizontal = Spacing.gutter + Spacing.xs, vertical = Spacing.s),
            horizontalArrangement = Arrangement.spacedBy(Spacing.l),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            InfoItem(DakilioSymbols.CalendarToday, relativeDayLabel(date, state.today, formats))
            InfoItem(DakilioSymbols.Schedule, formats.time(state.dateTime.toLocalTime()))
            if (diary != null) InfoItem(symbolForKey(diary.iconKey), diary.name, Modifier.weight(1f, fill = false))
        }
        return
    }
    Row(Modifier.padding(horizontal = Spacing.gutter), horizontalArrangement = Arrangement.spacedBy(Spacing.s)) {
        AssistChip(
            onClick = { pickDate = true },
            label = { Text(relativeDayLabel(date, state.today, formats)) },
            leadingIcon = { DakilioIcon(DakilioSymbols.CalendarToday, contentDescription = stringResource(R.string.entry_date), modifier = Modifier.size(AssistChipDefaults.IconSize)) },
            shape = RoundedCornerShape(12.dp),
        )
        AssistChip(
            onClick = { pickTime = true },
            label = { Text(formats.time(state.dateTime.toLocalTime())) },
            leadingIcon = { DakilioIcon(DakilioSymbols.Schedule, contentDescription = stringResource(R.string.entry_time), modifier = Modifier.size(AssistChipDefaults.IconSize)) },
            shape = RoundedCornerShape(12.dp),
        )
    }

    if (pickDate) {
        val todayUtc = state.today.atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()
        val pickerState = rememberDatePickerState(
            initialSelectedDateMillis = date.atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli(),
            selectableDates = object : SelectableDates {
                override fun isSelectableDate(utcTimeMillis: Long) = utcTimeMillis <= todayUtc
                override fun isSelectableYear(year: Int) = year <= state.today.year
            },
        )
        DatePickerDialog(
            onDismissRequest = { pickDate = false },
            confirmButton = {
                TextButton(onClick = {
                    pickerState.selectedDateMillis?.let { onDate(Instant.ofEpochMilli(it).atZone(ZoneOffset.UTC).toLocalDate()) }
                    pickDate = false
                }) { Text(stringResource(R.string.entry_ok)) }
            },
            dismissButton = { TextButton(onClick = { pickDate = false }) { Text(stringResource(R.string.entry_cancel)) } },
        ) { DatePicker(pickerState) }
    }
    if (pickTime) {
        val context = LocalContext.current
        val pickerState = rememberTimePickerState(
            initialHour = state.dateTime.hour,
            initialMinute = state.dateTime.minute,
            is24Hour = DateFormat.is24HourFormat(context),
        )
        TimePickerDialog(
            onDismissRequest = { pickTime = false },
            title = { Text(stringResource(R.string.entry_pick_time)) },
            confirmButton = {
                TextButton(onClick = {
                    onTime(pickerState.hour, pickerState.minute)
                    pickTime = false
                }) { Text(stringResource(R.string.entry_ok)) }
            },
            dismissButton = { TextButton(onClick = { pickTime = false }) { Text(stringResource(R.string.entry_cancel)) } },
        ) { TimePicker(pickerState) }
    }
}

/** An icon and a label of read-only entry info (date, time, diary). */
@Composable
private fun InfoItem(symbol: DakilioSymbol, text: String, modifier: Modifier = Modifier) {
    Row(modifier, verticalAlignment = Alignment.CenterVertically) {
        DakilioIcon(symbol, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(18.dp))
        Text(
            text,
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(start = Spacing.s),
        )
    }
}

/** Connected button group as a stepper: the current step widens, finished steps show a check. */
@Composable
private fun Stepper(state: EditorUiState, bodyFilled: Boolean, onStep: (EditorStep) -> Unit, modifier: Modifier = Modifier) {
    Row(modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(ButtonGroupDefaults.ConnectedSpaceBetween)) {
        EditorStep.entries.forEach { step ->
            val done = when (step) {
                EditorStep.Mood -> state.moodId != null
                EditorStep.Tags -> state.tagIds.isNotEmpty()
                EditorStep.Note -> bodyFilled
            }
            val current = step == state.step
            val weight by animateFloatAsState(if (current) 1.5f else 1f, MaterialTheme.motionScheme.defaultSpatialSpec(), label = "stepWeight")
            ToggleButton(
                checked = current,
                onCheckedChange = { if (!current) onStep(step) },
                modifier = Modifier.weight(weight).semantics { role = Role.Tab },
                shapes = when (step.ordinal) {
                    0 -> ButtonGroupDefaults.connectedLeadingButtonShapes()
                    EditorStep.entries.lastIndex -> ButtonGroupDefaults.connectedTrailingButtonShapes()
                    else -> ButtonGroupDefaults.connectedMiddleButtonShapes()
                },
                colors = ToggleButtonDefaults.colors(),
            ) {
                DakilioIcon(
                    if (done && !current) DakilioSymbols.CheckCircle else step.symbol,
                    contentDescription = null,
                    filled = current || done,
                    modifier = Modifier.size(18.dp),
                )
                AnimatedVisibility(current || done) {
                    Row {
                        Spacer(Modifier.width(ToggleButtonDefaults.IconSpacing))
                        Text(stringResource(step.label), maxLines = 1)
                    }
                }
            }
        }
    }
}

private val EditorStep.symbol: DakilioSymbol
    get() = when (this) {
        EditorStep.Mood -> DakilioSymbols.SentimentSatisfied
        EditorStep.Tags -> DakilioSymbols.Sell
        EditorStep.Note -> DakilioSymbols.EditNote
    }

private val EditorStep.label: Int
    get() = when (this) {
        EditorStep.Mood -> R.string.entry_step_mood
        EditorStep.Tags -> R.string.entry_step_tags
        EditorStep.Note -> R.string.entry_step_note
    }

/** Back + one primary button that morphs between Skip (tonal), Next (filled) and Save. */
@Composable
private fun BottomActions(
    step: EditorStep,
    canAdvance: Boolean,
    canSave: Boolean,
    onBack: () -> Unit,
    onNext: () -> Unit,
    onSave: () -> Unit,
) {
    val colors = MaterialTheme.colorScheme
    val isLast = step == EditorStep.Note
    val emphasized = isLast || canAdvance
    val container by animateColorAsState(if (emphasized) colors.primary else colors.secondaryContainer, MaterialTheme.motionScheme.defaultEffectsSpec(), label = "primaryBtn")
    val content by animateColorAsState(if (emphasized) colors.onPrimary else colors.onSecondaryContainer, MaterialTheme.motionScheme.defaultEffectsSpec(), label = "primaryBtnContent")
    val height = ButtonDefaults.MediumContainerHeight
    Surface(color = colors.surfaceContainer) {
        Row(
            Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .imePadding()
                .padding(horizontal = Spacing.gutter, vertical = Spacing.m),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            AnimatedVisibility(step != EditorStep.Mood) {
                OutlinedButton(
                    onClick = onBack,
                    modifier = Modifier.heightIn(min = height),
                    contentPadding = ButtonDefaults.contentPaddingFor(height),
                ) { Text(stringResource(R.string.entry_back), style = ButtonDefaults.textStyleFor(height)) }
            }
            Spacer(Modifier.weight(1f))
            Button(
                onClick = if (isLast) onSave else onNext,
                enabled = !isLast || canSave,
                shapes = ButtonDefaults.shapesFor(height),
                colors = ButtonDefaults.buttonColors(containerColor = container, contentColor = content),
                contentPadding = ButtonDefaults.contentPaddingFor(height),
                modifier = Modifier.heightIn(min = height).animateContentSize(MaterialTheme.motionScheme.defaultSpatialSpec()),
            ) {
                AnimatedContent(
                    targetState = when {
                        isLast -> R.string.entry_save
                        canAdvance -> R.string.entry_next
                        else -> R.string.entry_skip
                    },
                    transitionSpec = { fadeIn() togetherWith fadeOut() },
                    label = "primaryLabel",
                ) { label ->
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        if (label == R.string.entry_save) {
                            DakilioIcon(DakilioSymbols.Check, contentDescription = null, modifier = Modifier.size(22.dp))
                            Spacer(Modifier.width(Spacing.s))
                        }
                        Text(stringResource(label), style = ButtonDefaults.textStyleFor(height))
                    }
                }
            }
        }
    }
}
