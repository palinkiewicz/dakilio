package pl.dakil.dakilio.feature.diaries.impl

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.input.TextFieldLineLimits
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialShapes
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedListItem
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.launch
import pl.dakil.dakilio.core.designsystem.component.SectionHeader
import pl.dakil.dakilio.core.designsystem.component.ShapedIcon
import pl.dakil.dakilio.core.designsystem.component.pressScale
import pl.dakil.dakilio.core.designsystem.component.rememberHaptics
import pl.dakil.dakilio.core.designsystem.component.segmentedItemColors
import pl.dakil.dakilio.core.designsystem.icon.DakilioIcon
import pl.dakil.dakilio.core.designsystem.icon.DakilioSymbols
import pl.dakil.dakilio.core.designsystem.icon.symbolForKey
import pl.dakil.dakilio.core.designsystem.theme.Spacing
import pl.dakil.dakilio.core.model.Diary
import pl.dakil.dakilio.core.ui.component.IconPicker

@Composable
internal fun DiaryEditorScreen(viewModel: DiaryEditorViewModel, onClose: () -> Unit) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val haptics = rememberHaptics()
    val scope = rememberCoroutineScope()
    var pickingIcon by rememberSaveable { mutableStateOf(false) }
    var deleteCount by rememberSaveable { mutableStateOf<Int?>(null) }
    var spins by rememberSaveable { mutableIntStateOf(0) }
    val rotation by animateFloatAsState(spins * 72f, MaterialTheme.motionScheme.slowSpatialSpec(), label = "diarySpin")

    LaunchedEffect(state?.finished) { if (state?.finished == true) onClose() }

    val current = state
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(if (current?.isNew != false) R.string.diaries_new else R.string.diaries_edit)) },
                navigationIcon = {
                    IconButton(onClick = onClose) {
                        DakilioIcon(DakilioSymbols.Close, contentDescription = stringResource(R.string.diaries_close))
                    }
                },
                actions = {
                    Button(
                        onClick = {
                            haptics.confirm()
                            viewModel.save()
                        },
                        enabled = current != null && viewModel.name.text.isNotBlank(),
                        modifier = Modifier.padding(end = Spacing.s),
                    ) { Text(stringResource(R.string.diaries_save)) }
                },
            )
        },
    ) { padding ->
        if (current == null) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { LoadingIndicator() }
            return@Scaffold
        }
        Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.TopCenter) {
            Column(
                Modifier
                    .widthIn(max = Spacing.maxContentWidth)
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = Spacing.gutter)
                    .navigationBarsPadding(),
            ) {
                // Icon preview: tap to choose; every change gives the cookie a playful turn.
                val interaction = remember { MutableInteractionSource() }
                Box(
                    Modifier
                        .align(Alignment.CenterHorizontally)
                        .padding(top = Spacing.l)
                        .pressScale(interaction, 0.9f)
                        .clickable(interactionSource = interaction, indication = null, role = Role.Button) { pickingIcon = true },
                ) {
                    AnimatedContent(current.iconKey, transitionSpec = { (scaleIn() + fadeIn()) togetherWith fadeOut() }, label = "diaryIcon") { key ->
                        ShapedIcon(
                            symbolForKey(key),
                            polygon = MaterialShapes.Cookie12Sided,
                            size = 112.dp,
                            iconSize = 52.dp,
                            containerColor = MaterialTheme.colorScheme.primaryContainer,
                            contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                            modifier = Modifier.graphicsLayer { rotationZ = rotation },
                        )
                    }
                    FilledTonalIconButton(
                        onClick = { pickingIcon = true },
                        modifier = Modifier.align(Alignment.BottomEnd).size(40.dp),
                    ) { DakilioIcon(DakilioSymbols.Edit, contentDescription = stringResource(R.string.diaries_change_icon), modifier = Modifier.size(20.dp)) }
                }
                OutlinedTextField(
                    state = viewModel.name,
                    label = { Text(stringResource(R.string.diaries_name)) },
                    placeholder = { Text(stringResource(R.string.diaries_name_hint)) },
                    lineLimits = TextFieldLineLimits.SingleLine,
                    textStyle = MaterialTheme.typography.titleLarge,
                    modifier = Modifier.fillMaxWidth().padding(top = Spacing.xl),
                )

                SectionHeader(stringResource(R.string.diaries_suggested_title), Modifier.padding(top = Spacing.m))
                Text(
                    stringResource(R.string.diaries_suggested_body),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(start = Spacing.xs, bottom = Spacing.m),
                )
                Column(verticalArrangement = Arrangement.spacedBy(ListItemDefaults.SegmentedGap)) {
                    current.groups.forEachIndexed { index, group ->
                        val checked = group.id in current.suggestedGroupIds
                        SegmentedListItem(
                            onClick = {
                                haptics.toggle(!checked)
                                viewModel.toggleGroup(group.id)
                            },
                            shapes = ListItemDefaults.segmentedShapes(index, current.groups.size),
                            colors = segmentedItemColors(),
                            supportingContent = {
                                Text(group.tags.take(4).joinToString { it.label } + if (group.tags.size > 4) "…" else "", maxLines = 1)
                            },
                            trailingContent = {
                                Switch(
                                    checked = checked,
                                    onCheckedChange = {
                                        haptics.toggle(it)
                                        viewModel.toggleGroup(group.id)
                                    },
                                    thumbContent = if (checked) {
                                        { DakilioIcon(DakilioSymbols.Check, contentDescription = null, modifier = Modifier.size(SwitchDefaults.IconSize)) }
                                    } else {
                                        null
                                    },
                                )
                            },
                        ) { Text(group.name) }
                    }
                }

                if (!current.isNew) {
                    if (current.otherDiaries.isEmpty()) {
                        Text(
                            stringResource(R.string.diaries_last_diary),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(start = Spacing.xs, top = Spacing.xxl, bottom = Spacing.xl),
                        )
                    } else {
                        OutlinedButton(
                            onClick = { scope.launch { deleteCount = viewModel.exclusiveEntryCount() } },
                            colors = androidx.compose.material3.ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error),
                            modifier = Modifier.padding(top = Spacing.xxl, bottom = Spacing.xl),
                        ) {
                            DakilioIcon(DakilioSymbols.Delete, contentDescription = null)
                            Text(stringResource(R.string.diaries_delete), modifier = Modifier.padding(start = Spacing.s))
                        }
                    }
                }
            }
        }
    }

    if (pickingIcon && current != null) {
        val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
        ModalBottomSheet(onDismissRequest = { pickingIcon = false }, sheetState = sheetState) {
            Column(Modifier.navigationBarsPadding()) {
                Row(Modifier.padding(horizontal = Spacing.xl), verticalAlignment = Alignment.CenterVertically) {
                    Text(stringResource(R.string.diaries_icon), style = MaterialTheme.typography.titleLargeEmphasized, modifier = Modifier.weight(1f))
                    TextButton(onClick = { pickingIcon = false }) { Text(stringResource(R.string.diaries_done)) }
                }
                IconPicker(
                    selectedKey = current.iconKey,
                    onSelect = {
                        if (it != current.iconKey) spins++
                        viewModel.setIcon(it)
                    },
                    contentPadding = PaddingValues(horizontal = Spacing.l, vertical = Spacing.s),
                    modifier = Modifier.fillMaxWidth().height(420.dp),
                )
            }
        }
    }

    val count = deleteCount
    if (count != null && current != null) {
        DeleteDiaryDialog(
            name = viewModel.name.text.toString(),
            exclusiveCount = count,
            otherDiaries = current.otherDiaries,
            onDismiss = { deleteCount = null },
            onConfirm = { moveTo ->
                deleteCount = null
                haptics.confirm()
                viewModel.delete(moveTo)
            },
        )
    }
}

@Composable
private fun DeleteDiaryDialog(
    name: String,
    exclusiveCount: Int,
    otherDiaries: List<Diary>,
    onDismiss: () -> Unit,
    onConfirm: (moveTo: Long?) -> Unit,
) {
    // null = delete the exclusive entries; otherwise the diary to move them into.
    var target by rememberSaveable { mutableStateOf(otherDiaries.firstOrNull()?.id) }
    AlertDialog(
        onDismissRequest = onDismiss,
        icon = { DakilioIcon(DakilioSymbols.Delete, contentDescription = null) },
        title = { Text(stringResource(R.string.diaries_delete_title, name)) },
        text = {
            Column {
                if (exclusiveCount == 0) {
                    Text(stringResource(R.string.diaries_delete_body_none))
                } else {
                    Text(pluralStringResource(R.plurals.diaries_delete_body, exclusiveCount, exclusiveCount))
                    otherDiaries.forEach { diary ->
                        ChoiceRow(stringResource(R.string.diaries_move_to, diary.name), selected = target == diary.id) { target = diary.id }
                    }
                    ChoiceRow(stringResource(R.string.diaries_delete_entries), selected = target == null) { target = null }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(if (exclusiveCount == 0) otherDiaries.firstOrNull()?.id else target) }) {
                Text(stringResource(R.string.diaries_delete_confirm), color = MaterialTheme.colorScheme.error)
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.diaries_cancel)) } },
    )
}

@Composable
private fun ChoiceRow(label: String, selected: Boolean, onSelect: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .padding(top = Spacing.s)
            .selectable(selected = selected, onClick = onSelect, role = Role.RadioButton),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        RadioButton(selected = selected, onClick = null)
        Text(label, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.padding(start = Spacing.m))
    }
}
