package pl.dakil.dakilio.core.ui.mood

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
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.FilledTonalIconButton
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionOnScreen
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Dp
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
import pl.dakil.dakilio.core.designsystem.icon.DakilioIcon
import pl.dakil.dakilio.core.designsystem.icon.DakilioSymbols
import pl.dakil.dakilio.core.designsystem.theme.Spacing
import pl.dakil.dakilio.core.model.Mood
import pl.dakil.dakilio.core.ui.R

/** Where a level's face sits on screen, so the alias list can open right on top of it. */
fun LayoutCoordinates.screenRect(): Rect = Rect(positionOnScreen(), size.toSize())

/** Darker than a dialog's dim: the faces float on it without a panel behind them. */
private const val SCRIM_ALPHA = 0.65f
private val PanelPadding = Spacing.s
private val ItemPadding = Spacing.xs

/** Places the popup window at the origin; the content positions itself in screen coordinates. */
private object FullWindow : PopupPositionProvider {
    override fun calculatePosition(anchorBounds: IntRect, windowSize: IntSize, layoutDirection: LayoutDirection, popupContentSize: IntSize) =
        IntOffset.Zero
}

/**
 * A level's moods (the selected one first, then primary and aliases as usual) stacked vertically over a dimmed screen, starting at
 * the face that was tapped ([anchor], in screen pixels). Faces are drawn at [faceSize], the same as
 * the tapped one; the panel grows around them and may overlap neighbours. [wide] lays each mood out as a row (for the
 * wizard's full-width options) instead of a face over its label.
 *
 * Tapping the selected mood keeps it. When [onClear] is given and one of these moods is selected, a
 * clear button after the faces deselects it: clearer than a hidden "tap again" in a list of choices.
 */
@Composable
fun MoodAliasOverlay(
    moods: List<Mood>,
    selectedId: Long?,
    anchor: Rect,
    wide: Boolean,
    faceSize: Dp,
    onPick: (Mood) -> Unit,
    onDismiss: () -> Unit,
    onClear: (() -> Unit)? = null,
) {
    val haptics = rememberHaptics()
    val density = LocalDensity.current
    val progress = remember { Animatable(0f) }
    val spatial = MaterialTheme.motionScheme.defaultSpatialSpec<Float>()
    LaunchedEffect(Unit) { progress.animateTo(1f, spatial) }
    var origin by remember { mutableStateOf(Offset.Zero) }
    val scrim = MaterialTheme.colorScheme.scrim
    // The current mood leads (it sits right over the tapped face, which shows it); the rest keep
    // their usual order. sortedBy is stable, so that order is untouched.
    val ordered = remember(moods, selectedId) { moods.sortedBy { it.id != selectedId } }

    Popup(popupPositionProvider = FullWindow, onDismissRequest = onDismiss, properties = PopupProperties(focusable = true, clippingEnabled = false)) {
        Box(
            Modifier
                .fillMaxSize()
                .onGloballyPositioned { origin = it.positionOnScreen() }
                .graphicsLayer { alpha = progress.value.coerceIn(0f, 1f) }
                .background(scrim.copy(alpha = SCRIM_ALPHA))
                .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null, onClick = onDismiss),
        ) {
            val margin = with(density) { Spacing.l.roundToPx() }
            Layout(
                content = {
                    // No panel background: over the dimmed screen the faces and labels stand on their own.
                    // The shape still clips the items' press ripples to a rounded area.
                    Surface(shape = RoundedCornerShape(28.dp), color = Color.Transparent) {
                        Column(Modifier.padding(horizontal = PanelPadding, vertical = Spacing.s), verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
                            ordered.forEachIndexed { index, mood ->
                                val selected = mood.id == selectedId
                                OverlayItem(
                                    mood = mood,
                                    selected = selected,
                                    wide = wide,
                                    faceSize = faceSize,
                                    modifier = Modifier.graphicsLayer {
                                        // Aliases slide out from under the primary as the list opens.
                                        val p = progress.value
                                        translationY = (1f - p) * -index * 24.dp.toPx()
                                        alpha = if (index == 0) 1f else p.coerceIn(0f, 1f)
                                    },
                                    onClick = {
                                        haptics.confirm()
                                        onPick(mood)
                                    },
                                )
                            }
                            if (onClear != null && moods.any { it.id == selectedId }) {
                                FilledTonalIconButton(
                                    onClick = {
                                        haptics.tick()
                                        onClear()
                                    },
                                    modifier = Modifier
                                        .align(Alignment.CenterHorizontally)
                                        .graphicsLayer { alpha = progress.value.coerceIn(0f, 1f) },
                                ) {
                                    DakilioIcon(DakilioSymbols.Block, contentDescription = stringResource(R.string.mood_clear))
                                }
                            }
                        }
                    }
                },
            ) { measurables, constraints ->
                // The faces keep their real size: the panel is the anchor (at least one face wide) plus
                // its own padding, centred on the tapped face, and may cover a bit of the neighbours.
                // Narrow (a face over its label): leave the alias names some room beyond the face.
                val minInner = if (wide) faceSize else faceSize * 1.5f
                val inner = maxOf(anchor.width.roundToInt(), minInner.roundToPx())
                val width = (inner + 2 * PanelPadding.roundToPx()).coerceAtMost(constraints.maxWidth)
                val panel = measurables.single().measure(Constraints(minWidth = width, maxWidth = width))
                val centerX = (anchor.left + anchor.width / 2 - origin.x).roundToInt()
                val left = (centerX - panel.width / 2).coerceIn(0, (constraints.maxWidth - panel.width).coerceAtLeast(0))
                // Line the primary up with the tapped face; move up if the list would run off screen.
                val wantedTop = (anchor.top - origin.y).roundToInt() - (Spacing.s + ItemPadding).roundToPx()
                val top = wantedTop.coerceAtMost(constraints.maxHeight - panel.height - margin).coerceAtLeast(margin)
                layout(constraints.maxWidth, constraints.maxHeight) { panel.place(left, top) }
            }
        }
    }
}

@Composable
private fun OverlayItem(mood: Mood, selected: Boolean, wide: Boolean, faceSize: Dp, modifier: Modifier, onClick: () -> Unit) {
    val colors = moodColors(mood)
    val label = @Composable {
        Text(
            mood.label,
            style = if (selected) MaterialTheme.typography.labelLargeEmphasized else MaterialTheme.typography.labelLarge,
            color = if (selected) colors.accent else MaterialTheme.colorScheme.onSurface,
            textAlign = if (wide) TextAlign.Start else TextAlign.Center,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = (if (wide) Modifier.padding(start = Spacing.m) else Modifier.padding(top = Spacing.xs))
                // Each label gets its own pill so it reads on the scrim in both themes.
                .background(if (selected) colors.tint else MaterialTheme.colorScheme.surfaceContainerHigh, CircleShape)
                .padding(horizontal = Spacing.s, vertical = 2.dp),
        )
    }
    val itemModifier = modifier
        .fillMaxWidth()
        .clickable(onClick = onClick)
        .semantics {
            role = Role.RadioButton
            this.selected = selected
        }
        .padding(vertical = ItemPadding)
    if (wide) {
        // Lines the faces up with the wizard row's own face (its content starts at Spacing.l).
        Row(itemModifier.padding(horizontal = Spacing.l - PanelPadding), verticalAlignment = Alignment.CenterVertically) {
            MoodBadge(mood, Modifier.requiredSize(faceSize), size = faceSize, active = selected)
            label()
        }
    } else {
        Column(itemModifier, horizontalAlignment = Alignment.CenterHorizontally) {
            MoodBadge(mood, Modifier.requiredSize(faceSize), size = faceSize, active = selected)
            label()
        }
    }
}
