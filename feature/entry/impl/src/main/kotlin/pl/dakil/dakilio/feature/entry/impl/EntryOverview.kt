package pl.dakil.dakilio.feature.entry.impl

import androidx.annotation.StringRes
import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.heightIn
import androidx.compose.material3.Surface
import androidx.compose.material3.TextButton
import androidx.compose.runtime.saveable.rememberSaveable
import pl.dakil.dakilio.core.designsystem.icon.DakilioIcon
import pl.dakil.dakilio.core.designsystem.icon.DakilioSymbols
import pl.dakil.dakilio.core.model.Tag
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import coil3.compose.AsyncImage
import java.io.File
import pl.dakil.dakilio.core.model.PostPhoto
import pl.dakil.dakilio.core.ui.LocalEntryDisplay
import pl.dakil.dakilio.core.ui.component.PhotoViewer
import pl.dakil.dakilio.core.ui.scaled
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import pl.dakil.dakilio.core.ui.component.TagPill
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
 * The single page for an existing entry. It opens read-only for comfortable reading: the chosen
 * mood, the tags as chips and the note as plain text, with empty sections (and an empty title) left
 * out. When [editing], it becomes the all-in-one editor: one row of mood faces, the tags (folded
 * into chips until Expand is tapped), then the note fields. New entries go through the step wizard instead.
 */
@Composable
internal fun EntryOverview(
    state: EditorUiState,
    title: TextFieldState,
    note: TextFieldState,
    bodyRequired: Boolean,
    editing: Boolean,
    toggledGroups: Set<Long>,
    onToggleGroup: (Long) -> Unit,
    onSelectMood: (Long) -> Unit,
    onClearMood: () -> Unit,
    onToggleTag: (Long) -> Unit,
    onManageTags: () -> Unit,
    onAddPhotos: (List<android.net.Uri>) -> Unit,
    onRemovePhoto: (String) -> Unit,
    modifier: Modifier = Modifier,
    keywordChips: @Composable () -> Unit = {},
) {
    val mood = state.moods.firstOrNull { it.id == state.moodId }
    // Each time edit mode starts, the tags are folded into chips so the note is a short scroll away.
    var tagsExpanded by rememberSaveable(editing) { mutableStateOf(false) }
    LazyColumn(
        modifier = modifier.imePadding(),
        contentPadding = PaddingValues(top = Spacing.s, bottom = Spacing.xl),
        verticalArrangement = Arrangement.spacedBy(Spacing.s),
    ) {
        if (editing || mood != null) {
            item(key = "mood-label") { SectionLabel(R.string.entry_step_mood, Modifier.animateItem()) }
        }
        if (editing) {
            item(key = "mood-row") {
                MoodRow(
                    moods = state.moods,
                    selectedId = state.moodId,
                    onSelect = onSelectMood,
                    onClear = onClearMood,
                    modifier = Modifier.animateItem().padding(horizontal = Spacing.gutter, vertical = Spacing.xs),
                )
            }
        } else if (mood != null) {
            item(key = "mood-read") {
                MoodSummary(mood, Modifier.animateItem().padding(horizontal = Spacing.gutter + Spacing.xs, vertical = Spacing.xs))
            }
        }
        if (editing) {
            item(key = "tags-label") {
                TagsHeader(
                    expanded = tagsExpanded,
                    onExpandedChange = { tagsExpanded = it },
                    modifier = Modifier.animateItem().padding(top = Spacing.s),
                )
            }
        } else if (state.selectedTags.isNotEmpty()) {
            item(key = "tags-label-read") { SectionLabel(R.string.entry_step_tags, Modifier.animateItem().padding(top = Spacing.s)) }
        }
        if (editing && !tagsExpanded) {
            item(key = "tags-summary") {
                SelectedTagsSummary(
                    tags = state.selectedTags,
                    onClick = { tagsExpanded = true },
                    modifier = Modifier.animateItem().padding(horizontal = Spacing.gutter),
                )
            }
        } else if (editing) {
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
        } else if (state.selectedTags.isNotEmpty()) {
            item(key = "tags-read") {
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                    modifier = Modifier.animateItem().fillMaxWidth().padding(horizontal = Spacing.gutter + Spacing.xs),
                ) {
                    state.selectedTags.forEach { TagPill(it) }
                }
            }
        }
        val hasBody = title.text.isNotBlank() || note.text.isNotBlank() || state.photos.isNotEmpty()
        if (editing || hasBody) {
            item(key = "note-label") { SectionLabel(R.string.entry_step_note, Modifier.animateItem().padding(top = Spacing.m)) }
        }
        if (editing) {
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
                    belowNote = keywordChips,
                )
            }
        } else if (hasBody) {
            item(key = "note-read") {
                NoteReadView(
                    title = title.text.toString(),
                    note = note.text.toString(),
                    photos = state.photos,
                    modifier = Modifier.animateItem(),
                )
            }
        }
    }
}

/** The Tags section label with an Expand/Done button that unfolds the tag groups. */
@Composable
private fun TagsHeader(expanded: Boolean, onExpandedChange: (Boolean) -> Unit, modifier: Modifier = Modifier) {
    val haptics = rememberHaptics()
    Row(
        modifier.fillMaxWidth().padding(end = Spacing.gutter - Spacing.s),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        SectionLabel(R.string.entry_step_tags, Modifier.weight(1f))
        TextButton(onClick = {
            haptics.tick()
            onExpandedChange(!expanded)
        }) {
            DakilioIcon(
                if (expanded) DakilioSymbols.Check else DakilioSymbols.ExpandMore,
                contentDescription = null,
                modifier = Modifier.size(18.dp),
            )
            Text(
                stringResource(if (expanded) R.string.entry_tags_done else R.string.entry_tags_expand),
                modifier = Modifier.padding(start = Spacing.s),
            )
        }
    }
}

/** The entry's tags as chips (all of them, unlike the timeline card); tapping unfolds the groups. */
@Composable
private fun SelectedTagsSummary(tags: List<Tag>, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val haptics = rememberHaptics()
    Surface(
        onClick = {
            haptics.tick()
            onClick()
        },
        shape = RoundedCornerShape(24.dp),
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        modifier = modifier.fillMaxWidth(),
    ) {
        Row(
            Modifier.heightIn(min = 56.dp).padding(horizontal = Spacing.l, vertical = Spacing.m),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (tags.isEmpty()) {
                Text(
                    stringResource(R.string.entry_tags_none_selected),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.weight(1f),
                )
            } else {
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                    modifier = Modifier.weight(1f),
                ) {
                    tags.forEach { TagPill(it) }
                }
            }
        }
    }
}

/** The chosen mood, read-only: its face and its name in the mood's color. */
@Composable
private fun MoodSummary(mood: Mood, modifier: Modifier = Modifier) {
    Row(modifier, verticalAlignment = Alignment.CenterVertically) {
        MoodBadge(mood, size = 52.dp, active = true)
        Text(
            mood.label,
            style = MaterialTheme.typography.titleLargeEmphasized,
            color = moodColors(mood).accent,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(start = Spacing.m),
        )
    }
}

/** Title, note and photos as plain, selectable text; a blank title or note takes no space at all. */
@Composable
private fun NoteReadView(title: String, note: String, photos: List<EditorPhoto>, modifier: Modifier = Modifier) {
    val textScale = LocalEntryDisplay.current.editorTextScale
    var viewing by remember { mutableStateOf<Int?>(null) }
    Column(modifier.fillMaxWidth()) {
        SelectionContainer {
            Column(
                Modifier.padding(horizontal = Spacing.gutter + Spacing.xs),
                verticalArrangement = Arrangement.spacedBy(Spacing.s),
            ) {
                if (title.isNotBlank()) {
                    Text(title.trim(), style = MaterialTheme.typography.headlineSmall.scaled(textScale))
                }
                if (note.isNotBlank()) {
                    Text(note.trim(), style = MaterialTheme.typography.bodyLarge.scaled(textScale))
                }
            }
        }
        if (photos.isNotEmpty()) {
            LazyRow(
                contentPadding = PaddingValues(horizontal = Spacing.gutter),
                horizontalArrangement = Arrangement.spacedBy(Spacing.s),
                modifier = Modifier.padding(top = Spacing.l),
            ) {
                itemsIndexed(photos, key = { _, photo -> photo.fileName }) { index, photo ->
                    AsyncImage(
                        model = File(photo.path),
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier
                            .size(104.dp)
                            .clip(RoundedCornerShape(20.dp))
                            .background(MaterialTheme.colorScheme.surfaceContainerHighest)
                            .clickable { viewing = index },
                    )
                }
            }
        }
    }
    viewing?.let { index ->
        PhotoViewer(
            photos = photos.map { PostPhoto(id = 0, fileName = it.fileName, path = it.path) },
            initialIndex = index,
            onDismiss = { viewing = null },
        )
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
