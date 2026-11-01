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
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import pl.dakil.dakilio.core.designsystem.component.pressScale
import pl.dakil.dakilio.core.designsystem.component.rememberHaptics
import pl.dakil.dakilio.core.designsystem.theme.Spacing
import pl.dakil.dakilio.core.model.Mood
import pl.dakil.dakilio.core.model.levelsBestFirst
import pl.dakil.dakilio.core.ui.mood.MoodAliasOverlay
import pl.dakil.dakilio.core.ui.mood.MoodBadge
import pl.dakil.dakilio.core.ui.mood.moodColors
import pl.dakil.dakilio.core.ui.mood.screenRect

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
    toggledGroups: Set<Long>,
    onToggleGroup: (Long) -> Unit,
    onSelectMood: (Long) -> Unit,
    onClearMood: () -> Unit,
    onToggleTag: (Long) -> Unit,
    onManageTags: () -> Unit,
    onAddPhotos: (List<android.net.Uri>) -> Unit,
    onRemovePhoto: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
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
                onClear = onClearMood,
                modifier = Modifier.padding(horizontal = Spacing.gutter, vertical = Spacing.xs),
            )
        }
        item(key = "tags-label") { SectionLabel(R.string.entry_step_tags, Modifier.animateItem().padding(top = Spacing.l)) }
        tagGroupItems(
            suggested = state.suggestedGroups,
            others = state.otherGroups,
            selectedIds = state.tagIds,
            style = state.tagStyle,
            toggledGroups = toggledGroups,
            onToggleGroup = onToggleGroup,
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

/**
 * One face per level in a row, best first (matching the timeline's check-in card). The first tap
 * picks a level's primary; tapping the selected level again opens its aliases (marked with a dot),
 * or clears the mood when it has none.
 */
@Composable
private fun MoodRow(
    moods: List<Mood>,
    selectedId: Long?,
    onSelect: (Long) -> Unit,
    onClear: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val haptics = rememberHaptics()
    val selected = moods.firstOrNull { it.id == selectedId }
    val levels = moods.levelsBestFirst()
    val anchors = remember { mutableMapOf<Int, Rect>() }
    var openLevel by remember { mutableStateOf<Int?>(null) }
    Row(modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(Spacing.xs)) {
        levels.forEach { levelMoods ->
            val primary = levelMoods.first()
            val isSelected = selected?.level == primary.level
            val shown = if (isSelected) selected!! else primary
            val dimmed = selected != null && !isSelected
            val interaction = remember { MutableInteractionSource() }
            val label by animateColorAsState(
                when {
                    isSelected -> moodColors(shown).accent
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
                    .onGloballyPositioned { anchors[primary.level] = it.screenRect() }
                    .pressScale(interaction, 0.88f)
                    .clickable(interactionSource = interaction, indication = null) {
                        haptics.confirm()
                        when (val tap = moodTap(levelMoods, selected)) {
                            is MoodTap.Select -> onSelect(tap.id)
                            MoodTap.OpenAliases -> openLevel = primary.level
                            MoodTap.Clear -> onClear()
                        }
                    }
                    .semantics {
                        role = Role.RadioButton
                        this.selected = isSelected
                    },
            ) {
                MoodBadge(shown, size = 52.dp, active = isSelected, dimmed = if (dimmed) 1f else 0f, hasAliases = levelMoods.size > 1)
                Text(
                    shown.label,
                    style = if (isSelected) MaterialTheme.typography.labelLargeEmphasized else MaterialTheme.typography.labelMedium,
                    color = label,
                    textAlign = TextAlign.Center,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(top = Spacing.xs),
                )
            }
        }
    }
    val open = openLevel?.let { level -> levels.firstOrNull { it.first().level == level } }
    if (open != null) {
        MoodAliasOverlay(
            moods = open,
            selectedId = selectedId,
            anchor = anchors[open.first().level] ?: Rect.Zero,
            wide = false,
            faceSize = 52.dp,
            onPick = {
                onSelect(it.id)
                openLevel = null
            },
            onClear = {
                onClear()
                openLevel = null
            },
            onDismiss = { openLevel = null },
        )
    }
}
