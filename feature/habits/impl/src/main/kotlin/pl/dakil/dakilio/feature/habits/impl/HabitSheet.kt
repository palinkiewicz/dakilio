package pl.dakil.dakilio.feature.habits.impl

import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import pl.dakil.dakilio.core.designsystem.component.ConnectedToggleRow
import pl.dakil.dakilio.core.designsystem.component.SectionHeader
import pl.dakil.dakilio.core.designsystem.component.ToggleOption
import pl.dakil.dakilio.core.designsystem.component.rememberHaptics
import pl.dakil.dakilio.core.designsystem.icon.DakilioIcon
import pl.dakil.dakilio.core.designsystem.icon.symbolForKey
import pl.dakil.dakilio.core.designsystem.theme.Spacing
import pl.dakil.dakilio.core.model.Habit
import pl.dakil.dakilio.core.model.Habits
import pl.dakil.dakilio.core.model.TagGroup
import pl.dakil.dakilio.core.ui.component.TagPill

/**
 * Creates a habit ([habit] `null`: pick a tag, then a target) or edits one (the target; its tag is
 * fixed). Tags that already have a habit aren't offered.
 */
@Composable
internal fun HabitSheet(
    habit: Habit?,
    groups: List<TagGroup>,
    trackedTagIds: Set<Long>,
    onSave: (tagId: Long, daysPerWeek: Int) -> Unit,
    onDelete: () -> Unit,
    onDismiss: () -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val haptics = rememberHaptics()
    var tagId by rememberSaveable { mutableStateOf(habit?.tag?.id) }
    var days by rememberSaveable { mutableIntStateOf(habit?.daysPerWeek ?: Habits.DEFAULT_DAYS) }
    val pickable = groups
        .map { group -> group to group.tags.filter { it.id !in trackedTagIds } }
        .filter { (_, tags) -> tags.isNotEmpty() }
    val tag = habit?.tag ?: pickable.flatMap { it.second }.firstOrNull { it.id == tagId }

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
                stringResource(if (habit == null) R.string.habits_sheet_new else R.string.habits_sheet_edit),
                style = MaterialTheme.typography.headlineSmallEmphasized,
            )

            if (habit != null) {
                TagPill(habit.tag, modifier = Modifier.padding(top = Spacing.m))
            } else {
                SectionHeader(stringResource(R.string.habits_sheet_tag), Modifier.padding(top = Spacing.l))
                if (pickable.isEmpty()) {
                    Text(
                        stringResource(R.string.habits_sheet_no_tags),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = Spacing.s),
                    )
                } else {
                    Column(
                        Modifier
                            .heightIn(max = 320.dp)
                            .verticalScroll(rememberScrollState())
                            .padding(top = Spacing.xs),
                        verticalArrangement = Arrangement.spacedBy(Spacing.s),
                    ) {
                        pickable.forEach { (group, tags) ->
                            Text(
                                group.name,
                                style = MaterialTheme.typography.labelLarge,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                tags.forEach { candidate ->
                                    val selected = candidate.id == tagId
                                    FilterChip(
                                        selected = selected,
                                        onClick = {
                                            haptics.toggle(!selected)
                                            tagId = if (selected) null else candidate.id
                                        },
                                        label = { Text(candidate.label) },
                                        leadingIcon = {
                                            DakilioIcon(
                                                symbolForKey(candidate.iconKey),
                                                contentDescription = null,
                                                filled = selected,
                                                modifier = Modifier.size(18.dp),
                                            )
                                        },
                                        shape = CircleShape,
                                    )
                                }
                            }
                        }
                    }
                }
            }

            SectionHeader(stringResource(R.string.habits_sheet_days), Modifier.padding(top = Spacing.l))
            ConnectedToggleRow(
                options = Habits.DAYS_RANGE.map { ToggleOption(it, it.toString()) },
                selected = days,
                onSelect = { days = it },
                contentPadding = PaddingValues(horizontal = 2.dp),
                modifier = Modifier.padding(top = Spacing.s),
            )
            Text(
                if (tag == null) {
                    stringResource(R.string.habits_sheet_pick_tag)
                } else {
                    pluralStringResource(R.plurals.habits_sheet_summary, days, days, tag.label)
                },
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = Spacing.m).animateContentSize(),
            )

            Row(
                Modifier.fillMaxWidth().padding(top = Spacing.l),
                horizontalArrangement = Arrangement.spacedBy(Spacing.s, Alignment.End),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (habit != null) {
                    TextButton(
                        onClick = onDelete,
                        colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error),
                    ) { Text(stringResource(R.string.habits_sheet_delete)) }
                    Spacer(Modifier.weight(1f))
                }
                Button(
                    onClick = {
                        val id = tag?.id ?: return@Button
                        haptics.confirm()
                        onSave(id, days)
                    },
                    enabled = tag != null,
                ) {
                    Text(stringResource(if (habit == null) R.string.habits_sheet_add else R.string.habits_sheet_save))
                }
            }
        }
    }
}
