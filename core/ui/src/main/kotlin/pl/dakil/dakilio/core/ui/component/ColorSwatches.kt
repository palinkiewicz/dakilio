package pl.dakil.dakilio.core.ui.component

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialShapes
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
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
import pl.dakil.dakilio.core.ui.color.TagColor
import pl.dakil.dakilio.core.ui.color.tagColors

private val swatchMorph by lazy { Morph(MaterialShapes.Circle, MaterialShapes.Cookie9Sided) }

/**
 * A color choice in one scrolling row: a fallback chip ([fallbackLabel], showing [fallbackColor]),
 * then the palette. Used for tag and mood colors.
 *
 * @param selectedKey the chosen palette key, `null` for the fallback.
 * @param fallbackColor what the fallback looks like, or `null` for no color.
 */
@Composable
fun ColorRow(
    selectedKey: String?,
    fallbackLabel: String,
    fallbackColor: Color?,
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
            label = { Text(fallbackLabel) },
            leadingIcon = { ColorDot(fallbackColor, size = 18.dp) },
            shape = CircleShape,
        )
        TagColor.entries.forEach { color ->
            ColorSwatch(color, selected = color == selected, size = 40.dp, onClick = { select(color) })
        }
    }
}

/** A palette color; picking it morphs the circle into a cookie with a check. */
@Composable
fun ColorSwatch(color: TagColor, selected: Boolean, size: Dp, onClick: () -> Unit) {
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
fun ColorDot(color: Color?, size: Dp) {
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
        Box(Modifier.size(size).background(color, CircleShape))
    }
}
