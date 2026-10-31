package pl.dakil.dakilio.feature.entry.impl

import androidx.annotation.StringRes
import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import pl.dakil.dakilio.core.designsystem.component.pressScale
import pl.dakil.dakilio.core.designsystem.component.rememberHaptics
import pl.dakil.dakilio.core.designsystem.theme.Spacing
import pl.dakil.dakilio.core.model.Mood
import pl.dakil.dakilio.core.ui.mood.MoodBadge
import pl.dakil.dakilio.core.ui.mood.moodColors

/**
 * The all-in-one editor for an existing entry: one row of mood faces, the tags, then the note, all
 * on a single page for quick fixes. New entries go through the step wizard instead.
 */
@Composable
internal fun EntryOverview(
    state: EditorUiState,
    title: TextFieldState,
    note: TextFieldState,
    bodyRequired: Boolean,
    onSelectMood: (Long) -> Unit,
    onToggleTag: (Long) -> Unit,
    onManageTags: () -> Unit,
    onAddPhotos: (List<android.net.Uri>) -> Unit,
    onRemovePhoto: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    var expanded by rememberSaveable { mutableStateOf(emptySet<Long>()) }
    LazyColumn(
        modifier = modifier.imePadding(),
        contentPadding = PaddingValues(top = Spacing.s, bottom = Spacing.xl),
        verticalArrangement = Arrangement.spacedBy(Spacing.s),
    ) {
        item(key = "mood-label") { SectionLabel(R.string.entry_step_mood) }
        item(key = "mood-row") {
            MoodRow(
                moods = state.moods,
                selectedId = state.moodId,
                onSelect = onSelectMood,
                modifier = Modifier.padding(horizontal = Spacing.gutter, vertical = Spacing.xs),
            )
        }
        item(key = "tags-label") { SectionLabel(R.string.entry_step_tags, Modifier.animateItem().padding(top = Spacing.l)) }
        tagGroupItems(
            suggested = state.suggestedGroups,
            others = state.otherGroups,
            selectedIds = state.tagIds,
            expanded = expanded,
            onExpandedChange = { id, open -> expanded = if (open) expanded + id else expanded - id },
            onToggle = onToggleTag,
            onManageTags = onManageTags,
            horizontalPadding = Spacing.gutter,
        )
        item(key = "note-label") { SectionLabel(R.string.entry_step_note, Modifier.animateItem().padding(top = Spacing.m)) }
        item(key = "note") {
            NoteFields(
                title = title,
                note = note,
                photos = state.photos,
                importing = state.importingPhotos,
                bodyRequired = bodyRequired,
                onAddPhotos = onAddPhotos,
                onRemovePhoto = onRemovePhoto,
                modifier = Modifier.animateItem(),
            )
        }
    }
}

@Composable
private fun SectionLabel(@StringRes text: Int, modifier: Modifier = Modifier) {
    Text(
        stringResource(text),
        style = MaterialTheme.typography.titleSmallEmphasized,
        color = MaterialTheme.colorScheme.primary,
        modifier = modifier.padding(horizontal = Spacing.gutter + Spacing.xs),
    )
}

/** Every mood face in one row, best first (matching the timeline's check-in card). Tap again to clear. */
@Composable
private fun MoodRow(moods: List<Mood>, selectedId: Long?, onSelect: (Long) -> Unit, modifier: Modifier = Modifier) {
    val haptics = rememberHaptics()
    Row(modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(Spacing.xs)) {
        moods.asReversed().forEach { mood ->
            val selected = mood.id == selectedId
            val dimmed = selectedId != null && !selected
            val interaction = remember { MutableInteractionSource() }
            val label by animateColorAsState(
                when {
                    selected -> moodColors(mood).accent
                    dimmed -> MaterialTheme.colorScheme.outline
                    else -> MaterialTheme.colorScheme.onSurfaceVariant
                },
                MaterialTheme.motionScheme.defaultEffectsSpec(),
                label = "moodLabel",
            )
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier
                    .weight(1f)
                    .pressScale(interaction, 0.88f)
                    .clickable(interactionSource = interaction, indication = null) {
                        haptics.confirm()
                        onSelect(mood.id)
                    }
                    .semantics {
                        role = Role.RadioButton
                        this.selected = selected
                    },
            ) {
                MoodBadge(mood, size = 52.dp, active = selected, dimmed = if (dimmed) 1f else 0f)
                Text(
                    mood.label,
                    style = if (selected) MaterialTheme.typography.labelLargeEmphasized else MaterialTheme.typography.labelMedium,
                    color = label,
                    textAlign = TextAlign.Center,
                    maxLines = 1,
                    modifier = Modifier.padding(top = Spacing.xs),
                )
            }
        }
    }
}
