package pl.dakil.dakilio.feature.prompts.impl

import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.input.TextFieldLineLimits
import androidx.compose.foundation.text.input.rememberTextFieldState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SegmentedListItem
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringArrayResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import pl.dakil.dakilio.core.designsystem.component.SectionHeader
import pl.dakil.dakilio.core.designsystem.component.pressScale
import pl.dakil.dakilio.core.designsystem.component.rememberHaptics
import pl.dakil.dakilio.core.designsystem.component.segmentedItemColors
import pl.dakil.dakilio.core.designsystem.theme.Spacing
import pl.dakil.dakilio.core.model.Mood
import pl.dakil.dakilio.core.model.Prompt
import pl.dakil.dakilio.core.model.PromptDraft
import pl.dakil.dakilio.core.model.Prompts
import pl.dakil.dakilio.core.ui.Hint
import pl.dakil.dakilio.core.ui.mood.MoodBadge

/**
 * Adds a prompt or edits [prompt]: the question, and the moods it's offered for. Every face is on
 * at first (any mood); tapping one turns it off, and at least one stays on.
 */
@Composable
internal fun PromptSheet(
    prompt: Prompt?,
    moods: List<Mood>,
    onSave: (PromptDraft) -> Unit,
    onDelete: () -> Unit,
    onDismiss: () -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val haptics = rememberHaptics()
    val text = rememberTextFieldState(prompt?.text.orEmpty())
    var levels by rememberSaveable { mutableStateOf((prompt?.levels ?: Prompts.ALL_LEVELS).toList()) }
    val cleaned = Prompts.clean(text.text.toString())

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState) {
        Column(
            Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .navigationBarsPadding()
                .imePadding()
                .padding(horizontal = Spacing.xl)
                .padding(bottom = Spacing.l),
        ) {
            Text(
                stringResource(if (prompt == null) R.string.prompts_sheet_new else R.string.prompts_sheet_edit),
                style = MaterialTheme.typography.headlineSmallEmphasized,
            )
            OutlinedTextField(
                state = text,
                label = { Text(stringResource(R.string.prompts_sheet_question)) },
                placeholder = { Text(stringResource(R.string.prompts_sheet_question_hint)) },
                lineLimits = TextFieldLineLimits.MultiLine(maxHeightInLines = 4),
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
                modifier = Modifier.fillMaxWidth().padding(top = Spacing.l),
            )

            SectionHeader(stringResource(R.string.prompts_sheet_moods), Modifier.padding(top = Spacing.s))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                // Best first, like every mood picker.
                moods.sortedByDescending { it.level }.forEach { mood ->
                    val on = mood.level in levels
                    val interaction = remember { MutableInteractionSource() }
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier
                            .pressScale(interaction, 0.88f)
                            .clickable(interactionSource = interaction, indication = null, role = Role.Checkbox) {
                                if (on && levels.size == 1) {
                                    // The last one stays: a prompt for no mood at all would never show.
                                    haptics.reject()
                                } else {
                                    haptics.toggle(!on)
                                    levels = if (on) levels - mood.level else levels + mood.level
                                }
                            }
                            .semantics { selected = on },
                    ) {
                        MoodBadge(mood, size = 48.dp, active = on, dimmed = if (on) 0f else 1f)
                        Text(
                            mood.label,
                            style = MaterialTheme.typography.labelSmall,
                            color = if (on) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(top = Spacing.xs),
                        )
                    }
                }
            }
            Text(
                stringResource(if (levels.size == moods.size) R.string.prompts_sheet_any_mood else R.string.prompts_sheet_some_moods),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = Spacing.m).animateContentSize(),
            )

            Row(
                Modifier.fillMaxWidth().padding(top = Spacing.l),
                horizontalArrangement = Arrangement.spacedBy(Spacing.s, Alignment.End),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (prompt != null) {
                    TextButton(
                        onClick = onDelete,
                        colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error),
                    ) { Text(stringResource(R.string.prompts_sheet_delete)) }
                    Spacer(Modifier.weight(1f))
                }
                Button(
                    onClick = {
                        haptics.confirm()
                        onSave(PromptDraft(cleaned, levels.toSet()))
                    },
                    enabled = cleaned.isNotEmpty(),
                ) {
                    Text(stringResource(if (prompt == null) R.string.prompts_sheet_add else R.string.prompts_sheet_save))
                }
            }
        }
    }
}

/** A ready-made prompt and the mood levels it's for. */
private data class Starter(val text: String, val levels: Set<Int>)

private val HEAVY_LEVELS = setOf(1, 2)
private val BRIGHT_LEVELS = setOf(4, 5)

/**
 * Ready-made prompts to tick and add: for heavier days, for brighter days and for any day. Each
 * language writes its own lines (`prompts_starter_*`). Ones the user already has show as added.
 */
@Composable
internal fun StarterPromptsSheet(
    existing: Set<String>,
    moods: List<Mood>,
    onAdd: (List<PromptDraft>) -> Unit,
    onDismiss: () -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val haptics = rememberHaptics()
    val sections = listOf(
        R.string.prompts_starter_any_title to stringArrayResource(R.array.prompts_starter_any).map { Starter(it, Prompts.ALL_LEVELS) },
        R.string.prompts_starter_heavy_title to stringArrayResource(R.array.prompts_starter_heavy).map { Starter(it, HEAVY_LEVELS) },
        R.string.prompts_starter_bright_title to stringArrayResource(R.array.prompts_starter_bright).map { Starter(it, BRIGHT_LEVELS) },
    )
    var picked by rememberSaveable { mutableStateOf(emptyList<String>()) }

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState) {
        Column(
            Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .navigationBarsPadding()
                .padding(horizontal = Spacing.xl)
                .padding(bottom = Spacing.l),
        ) {
            Text(stringResource(R.string.prompts_starters), style = MaterialTheme.typography.headlineSmallEmphasized)
            Hint {
                Text(
                    stringResource(R.string.prompts_starters_hint),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = Spacing.xs),
                )
            }
            sections.forEach { (title, starters) ->
                SectionHeader(stringResource(title))
                Column(verticalArrangement = Arrangement.spacedBy(ListItemDefaults.SegmentedGap)) {
                    starters.forEachIndexed { index, starter ->
                        val added = Prompts.key(starter.text) in existing
                        val checked = added || starter.text in picked
                        val toggle = {
                            haptics.toggle(!checked)
                            picked = if (checked) picked - starter.text else picked + starter.text
                        }
                        SegmentedListItem(
                            onClick = toggle,
                            enabled = !added,
                            shapes = ListItemDefaults.segmentedShapes(index, starters.size),
                            colors = segmentedItemColors(),
                            supportingContent = if (added) {
                                { Text(stringResource(R.string.prompts_starter_added)) }
                            } else {
                                null
                            },
                            trailingContent = {
                                Checkbox(checked = checked, onCheckedChange = { toggle() }, enabled = !added, modifier = Modifier.size(40.dp))
                            },
                        ) { Text(starter.text) }
                    }
                }
            }
            val count = picked.size
            Button(
                onClick = {
                    haptics.confirm()
                    onAdd(sections.flatMap { it.second }.filter { it.text in picked }.map { PromptDraft(it.text, it.levels) })
                },
                enabled = count > 0,
                modifier = Modifier.align(Alignment.End).padding(top = Spacing.l),
            ) {
                Text(if (count > 0) pluralStringResource(R.plurals.prompts_starters_add, count, count) else stringResource(R.string.prompts_starters_add_none))
            }
        }
    }
}
