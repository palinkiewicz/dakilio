package pl.dakil.dakilio.feature.entry.impl

import androidx.compose.animation.core.Animatable
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionOnScreen
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntRect
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.toSize
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupPositionProvider
import androidx.compose.ui.window.PopupProperties
import kotlin.math.roundToInt
import pl.dakil.dakilio.core.designsystem.component.rememberHaptics
import pl.dakil.dakilio.core.designsystem.theme.Spacing
import pl.dakil.dakilio.core.model.Mood
import pl.dakil.dakilio.core.ui.mood.MoodBadge
import pl.dakil.dakilio.core.ui.mood.moodColors

/** Where a level's face sits on screen, so the alias list can open right on top of it. */
internal fun LayoutCoordinates.screenRect(): Rect = Rect(positionOnScreen(), size.toSize())

/** Places the popup window at the origin; the content positions itself in screen coordinates. */
private object FullWindow : PopupPositionProvider {
    override fun calculatePosition(anchorBounds: IntRect, windowSize: IntSize, layoutDirection: LayoutDirection, popupContentSize: IntSize) =
        IntOffset.Zero
}

/**
 * A level's moods (primary first, then aliases) stacked vertically over a dimmed screen, starting at
 * the face that was tapped ([anchor], in screen pixels). [wide] lays each mood out as a row (for the
 * wizard's full-width options) instead of a face over its label. Tapping the selected mood clears it.
 */
@Composable
internal fun MoodAliasOverlay(
    moods: List<Mood>,
    selectedId: Long?,
    anchor: Rect,
    wide: Boolean,
    onPick: (Mood) -> Unit,
    onClear: () -> Unit,
    onDismiss: () -> Unit,
) {
    val haptics = rememberHaptics()
    val density = LocalDensity.current
    val progress = remember { Animatable(0f) }
    val spatial = MaterialTheme.motionScheme.defaultSpatialSpec<Float>()
    LaunchedEffect(Unit) { progress.animateTo(1f, spatial) }
    var origin by remember { mutableStateOf(Offset.Zero) }
    val scrim = MaterialTheme.colorScheme.scrim

    Popup(popupPositionProvider = FullWindow, onDismissRequest = onDismiss, properties = PopupProperties(focusable = true, clippingEnabled = false)) {
        Box(
            Modifier
                .fillMaxSize()
                .onGloballyPositioned { origin = it.positionOnScreen() }
                .graphicsLayer { alpha = progress.value.coerceIn(0f, 1f) }
                .background(scrim.copy(alpha = 0.4f))
                .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null, onClick = onDismiss),
        ) {
            val margin = with(density) { Spacing.l.roundToPx() }
            Layout(
                content = {
                    Surface(
                        shape = RoundedCornerShape(28.dp),
                        color = MaterialTheme.colorScheme.surfaceContainerHigh,
                        shadowElevation = 6.dp,
                    ) {
                        Column(Modifier.padding(vertical = Spacing.s), verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
                            moods.forEachIndexed { index, mood ->
                                val selected = mood.id == selectedId
                                OverlayItem(
                                    mood = mood,
                                    selected = selected,
                                    wide = wide,
                                    modifier = Modifier.graphicsLayer {
                                        // Aliases slide out from under the primary as the list opens.
                                        val p = progress.value
                                        translationY = (1f - p) * -index * 24.dp.toPx()
                                        alpha = if (index == 0) 1f else p.coerceIn(0f, 1f)
                                    },
                                    onClick = {
                                        haptics.confirm()
                                        if (selected) onClear() else onPick(mood)
                                    },
                                )
                            }
                        }
                    }
                },
            ) { measurables, constraints ->
                val width = anchor.width.roundToInt().coerceAtMost(constraints.maxWidth)
                val panel = measurables.single().measure(Constraints(minWidth = width, maxWidth = width))
                val left = (anchor.left - origin.x).roundToInt().coerceIn(0, (constraints.maxWidth - panel.width).coerceAtLeast(0))
                // Line the primary up with the tapped face; move up if the list would run off screen.
                val wantedTop = (anchor.top - origin.y).roundToInt() - Spacing.s.roundToPx()
                val top = wantedTop.coerceAtMost(constraints.maxHeight - panel.height - margin).coerceAtLeast(margin)
                layout(constraints.maxWidth, constraints.maxHeight) { panel.place(left, top) }
            }
        }
    }
}

@Composable
private fun OverlayItem(mood: Mood, selected: Boolean, wide: Boolean, modifier: Modifier, onClick: () -> Unit) {
    val colors = moodColors(mood)
    val label = @Composable {
        Text(
            mood.label,
            style = if (selected) MaterialTheme.typography.labelLargeEmphasized else MaterialTheme.typography.labelLarge,
            color = if (selected) colors.accent else MaterialTheme.colorScheme.onSurface,
            textAlign = if (wide) TextAlign.Start else TextAlign.Center,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = if (wide) Modifier.padding(start = Spacing.l) else Modifier.padding(top = Spacing.xs),
        )
    }
    val itemModifier = modifier
        .fillMaxWidth()
        .clickable(onClick = onClick)
        .semantics {
            role = Role.RadioButton
            this.selected = selected
        }
        .padding(horizontal = Spacing.s, vertical = Spacing.xs)
    if (wide) {
        Row(itemModifier.padding(horizontal = Spacing.m), verticalAlignment = Alignment.CenterVertically) {
            MoodBadge(mood, size = 48.dp, active = selected)
            label()
        }
    } else {
        Column(itemModifier, horizontalAlignment = Alignment.CenterHorizontally) {
            MoodBadge(mood, size = 52.dp, active = selected)
            label()
        }
    }
}
