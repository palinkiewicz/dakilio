package pl.dakil.dakilio.feature.entry.impl

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.key
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import pl.dakil.dakilio.core.designsystem.component.pressScale
import pl.dakil.dakilio.core.designsystem.component.rememberHaptics
import pl.dakil.dakilio.core.designsystem.icon.DakilioIcon
import pl.dakil.dakilio.core.designsystem.icon.DakilioSymbols
import pl.dakil.dakilio.core.designsystem.icon.symbolForKey
import pl.dakil.dakilio.core.designsystem.theme.Spacing
import pl.dakil.dakilio.core.ui.color.tagColors

/**
 * "From your note": tags whose keywords appear in the text (the Keywords extra). A suggestion is
 * added with a tap or dismissed with its ✕; an auto-added tag is undone with a tap. Hidden (taking
 * no space) when there's nothing to show.
 */
@Composable
internal fun KeywordSuggestions(
    chips: List<KeywordChip>,
    onAdd: (Long) -> Unit,
    onDismiss: (Long) -> Unit,
    onUndo: (Long) -> Unit,
    modifier: Modifier = Modifier,
) {
    // Keep the last chips while the row animates out, so it doesn't collapse empty.
    val last = remember { arrayOf(chips) }
    if (chips.isNotEmpty()) last[0] = chips
    val shown = last[0]
    AnimatedVisibility(
        visible = chips.isNotEmpty(),
        enter = expandVertically(MaterialTheme.motionScheme.fastSpatialSpec()) + fadeIn(MaterialTheme.motionScheme.fastEffectsSpec()),
        exit = shrinkVertically(MaterialTheme.motionScheme.fastSpatialSpec()) + fadeOut(MaterialTheme.motionScheme.fastEffectsSpec()),
        modifier = modifier,
    ) {
        Column(Modifier.fillMaxWidth().padding(horizontal = Spacing.gutter).padding(top = Spacing.s)) {
            Text(
                stringResource(R.string.entry_keywords_label),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(bottom = Spacing.xs),
            )
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                shown.forEach { chip ->
                    key(chip.tag.id) {
                        KeywordChipView(chip, onAdd = onAdd, onDismiss = onDismiss, onUndo = onUndo)
                    }
                }
            }
        }
    }
}

@Composable
private fun KeywordChipView(chip: KeywordChip, onAdd: (Long) -> Unit, onDismiss: (Long) -> Unit, onUndo: (Long) -> Unit) {
    val haptics = rememberHaptics()
    val tones = tagColors(chip.tag.color)
    val scheme = MaterialTheme.colorScheme
    // Added tags wear the tag's full fill, like a selected tag; suggestions its soft tone.
    val container = if (chip.added) tones?.container ?: scheme.secondaryContainer else tones?.soft ?: scheme.surfaceContainerHighest
    val content = if (chip.added) tones?.onContainer ?: scheme.onSecondaryContainer else tones?.accent ?: scheme.onSurfaceVariant
    val interaction = remember { MutableInteractionSource() }
    val tagName = chip.tag.label
    Surface(
        onClick = {
            if (chip.added) {
                haptics.toggle(false)
                onUndo(chip.tag.id)
            } else {
                haptics.toggle(true)
                onAdd(chip.tag.id)
            }
        },
        shape = CircleShape,
        color = container,
        contentColor = content,
        interactionSource = interaction,
        modifier = Modifier.pressScale(interaction),
    ) {
        Row(
            Modifier.padding(start = 10.dp, end = if (chip.added) 12.dp else 4.dp, top = 6.dp, bottom = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            DakilioIcon(
                if (chip.added) DakilioSymbols.Check else DakilioSymbols.Add,
                contentDescription = null,
                tint = content,
                modifier = Modifier.size(16.dp),
            )
            DakilioIcon(symbolForKey(chip.tag.iconKey), contentDescription = null, tint = content, modifier = Modifier.size(16.dp))
            Text(
                tagName,
                style = MaterialTheme.typography.labelLarge,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (!chip.added) {
                val dismiss = stringResource(R.string.entry_keywords_dismiss, tagName)
                Surface(
                    onClick = {
                        haptics.tick()
                        onDismiss(chip.tag.id)
                    },
                    shape = CircleShape,
                    color = Color.Transparent,
                    contentColor = content,
                    modifier = Modifier.size(24.dp),
                ) {
                    DakilioIcon(DakilioSymbols.Close, contentDescription = dismiss, tint = content, modifier = Modifier.padding(4.dp))
                }
            }
        }
    }
}
