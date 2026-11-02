package pl.dakil.dakilio.feature.tags.impl

import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialShapes
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.graphics.shapes.Morph
import pl.dakil.dakilio.core.designsystem.component.MorphShape
import pl.dakil.dakilio.core.designsystem.component.pressScale
import pl.dakil.dakilio.core.designsystem.component.rememberHaptics
import pl.dakil.dakilio.core.designsystem.icon.DakilioIcon
import pl.dakil.dakilio.core.designsystem.icon.DakilioSymbols
import pl.dakil.dakilio.core.designsystem.theme.Spacing
import pl.dakil.dakilio.core.model.TagGroup
import pl.dakil.dakilio.core.ui.color.TagColor
import pl.dakil.dakilio.core.ui.color.tagColors
import pl.dakil.dakilio.core.ui.component.TagPill

private const val COLUMNS = 6
private val swatchMorph by lazy { Morph(MaterialShapes.Circle, MaterialShapes.Cookie9Sided) }

/**
 * Picks the default color of a group's tags. A pick applies at once, so the live tags at the top
 * show it right away; the sheet stays open for trying others.
 */
@Composable
internal fun GroupColorSheet(group: TagGroup, onSelect: (String?) -> Unit, onDismiss: () -> Unit) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val haptics = rememberHaptics()
    val selected = TagColor.fromKey(group.colorKey)
    fun select(color: TagColor?) {
        if (color == selected) return
        haptics.toggle(color != null)
        onSelect(color?.key)
    }

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState) {
        Column(Modifier.fillMaxWidth().navigationBarsPadding().padding(horizontal = Spacing.xl)) {
            Text(
                stringResource(R.string.tags_color_sheet_group),
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary,
            )
            Text(group.name, style = MaterialTheme.typography.headlineSmallEmphasized, maxLines = 1)
            if (group.tags.isNotEmpty()) {
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                    modifier = Modifier.padding(top = Spacing.m).animateContentSize(),
                ) {
                    group.tags.forEach { TagPill(it) }
                }
            }
            FilterChip(
                selected = selected == null,
                onClick = { select(null) },
                label = { Text(stringResource(R.string.tags_color_none)) },
                leadingIcon = { ColorDot(null, size = 18.dp) },
                shape = CircleShape,
                modifier = Modifier.padding(top = Spacing.l),
            )
            Column(
                Modifier.padding(top = Spacing.m),
                verticalArrangement = Arrangement.spacedBy(Spacing.m),
            ) {
                TagColor.entries.chunked(COLUMNS).forEach { row ->
                    Row(Modifier.fillMaxWidth()) {
                        row.forEach { color ->
                            Box(Modifier.weight(1f), contentAlignment = Alignment.Center) {
                                Swatch(color, selected = color == selected, size = 48.dp, onClick = { select(color) })
                            }
                        }
                        repeat(COLUMNS - row.size) { Spacer(Modifier.weight(1f)) }
                    }
                }
            }
            Row(Modifier.fillMaxWidth().padding(vertical = Spacing.xl)) {
                Spacer(Modifier.weight(1f))
                Button(onClick = onDismiss) { Text(stringResource(R.string.tags_color_done)) }
            }
        }
    }
}

/**
 * The tag editor's color choice: "Use group color" (showing [groupColorKey]), then the palette, in
 * one scrolling row.
 *
 * @param selectedKey the tag's own color, `null` for its group's.
 */
@Composable
internal fun TagColorRow(
    selectedKey: String?,
    groupColorKey: String?,
    onSelect: (String?) -> Unit,
    modifier: Modifier = Modifier,
    contentPadding: PaddingValues = PaddingValues(),
) {
    val haptics = rememberHaptics()
    val selected = TagColor.fromKey(selectedKey)
    fun select(color: TagColor?) {
        if (color == selected) return
        haptics.toggle(color != null)
        onSelect(color?.key)
    }
    Row(
        modifier.horizontalScroll(rememberScrollState()).padding(contentPadding),
        horizontalArrangement = Arrangement.spacedBy(Spacing.s),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        FilterChip(
            selected = selected == null,
            onClick = { select(null) },
            label = { Text(stringResource(R.string.tags_color_use_group)) },
            leadingIcon = { ColorDot(TagColor.fromKey(groupColorKey), size = 18.dp) },
            shape = CircleShape,
        )
        TagColor.entries.forEach { color ->
            Swatch(color, selected = color == selected, size = 40.dp, onClick = { select(color) })
        }
    }
}

/** A palette color; picking it morphs the circle into a cookie with a check. */
@Composable
private fun Swatch(color: TagColor, selected: Boolean, size: Dp, onClick: () -> Unit) {
    val tones = tagColors(color)
    val interaction = remember { MutableInteractionSource() }
    val progress by animateFloatAsState(if (selected) 1f else 0f, MaterialTheme.motionScheme.fastSpatialSpec(), label = "swatchMorph")
    val label = stringResource(color.label)
    Box(
        Modifier
            .size(size)
            .pressScale(interaction, 0.88f)
            .graphicsLayer { rotationZ = progress * 20f }
            .clip(MorphShape(swatchMorph, progress))
            .background(tones.container)
            .clickable(interactionSource = interaction, indication = null, onClick = onClick)
            .semantics {
                role = Role.RadioButton
                this.selected = selected
                contentDescription = label
            },
        contentAlignment = Alignment.Center,
    ) {
        if (progress > 0f) {
            DakilioIcon(
                DakilioSymbols.Check,
                contentDescription = null,
                tint = tones.onContainer,
                modifier = Modifier.size(size / 2).graphicsLayer {
                    rotationZ = -progress * 20f
                    alpha = progress
                    scaleX = progress
                    scaleY = progress
                },
            )
        }
    }
}

/** A small dot of [color], or a crossed-out outline for no color. */
@Composable
internal fun ColorDot(color: TagColor?, size: Dp) {
    if (color == null) {
        Box(
            Modifier.size(size).border(1.5.dp, MaterialTheme.colorScheme.outline, CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            DakilioIcon(
                DakilioSymbols.Block,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.outline,
                modifier = Modifier.size(size * 0.7f),
            )
        }
    } else {
        Box(Modifier.size(size).background(tagColors(color).container, CircleShape))
    }
}
