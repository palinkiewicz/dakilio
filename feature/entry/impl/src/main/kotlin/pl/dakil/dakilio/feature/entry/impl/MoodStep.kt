package pl.dakil.dakilio.feature.entry.impl

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import pl.dakil.dakilio.core.designsystem.component.pressScale
import pl.dakil.dakilio.core.designsystem.component.rememberHaptics
import pl.dakil.dakilio.core.designsystem.icon.DakilioIcon
import pl.dakil.dakilio.core.designsystem.icon.DakilioSymbols
import pl.dakil.dakilio.core.designsystem.theme.Spacing
import pl.dakil.dakilio.core.model.Mood
import pl.dakil.dakilio.core.model.levelsBestFirst
import pl.dakil.dakilio.core.ui.hintOrNull
import pl.dakil.dakilio.core.ui.mood.MoodAliasOverlay
import pl.dakil.dakilio.core.ui.mood.MoodBadge
import pl.dakil.dakilio.core.ui.mood.moodColors
import pl.dakil.dakilio.core.ui.mood.screenRect

/**
 * The hero step: big, tactile mood buttons, best mood on top. Same two-tap rule as the edit page: the
 * first tap picks a level's primary (and moves on, unless the level has aliases); tapping the selected
 * level again opens its aliases.
 */
@Composable
internal fun MoodStep(
    moods: List<Mood>,
    selectedId: Long?,
    onSelect: (id: Long, advance: Boolean) -> Unit,
    onClear: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val haptics = rememberHaptics()
    val selected = moods.firstOrNull { it.id == selectedId }
    val levels = moods.levelsBestFirst()
    val anchors = remember { mutableMapOf<Int, Rect>() }
    var openLevel by remember { mutableStateOf<Int?>(null) }
    Column(
        modifier = modifier.verticalScroll(rememberScrollState()).padding(horizontal = Spacing.gutter, vertical = Spacing.s),
        verticalArrangement = Arrangement.spacedBy(Spacing.s),
    ) {
        StepHeader(stringResource(R.string.entry_mood_title), hintOrNull(stringResource(R.string.entry_mood_body)))
        levels.forEach { levelMoods ->
            val primary = levelMoods.first()
            val isSelected = selected?.level == primary.level
            MoodOption(
                mood = if (isSelected) selected!! else primary,
                selected = isSelected,
                dimmed = selected != null && !isSelected,
                hasAliases = levelMoods.size > 1,
                modifier = Modifier.onGloballyPositioned { anchors[primary.level] = it.screenRect() },
                onClick = {
                    haptics.confirm()
                    when (val tap = moodTap(levelMoods, selected)) {
                        // With aliases, wait: the user may still want one of them.
                        is MoodTap.Select -> onSelect(tap.id, levelMoods.size == 1)
                        MoodTap.OpenAliases -> openLevel = primary.level
                        MoodTap.Clear -> onClear()
                    }
                },
            )
        }
    }
    val open = openLevel?.let { level -> levels.firstOrNull { it.first().level == level } }
    if (open != null) {
        MoodAliasOverlay(
            moods = open,
            selectedId = selectedId,
            anchor = anchors[open.first().level] ?: Rect.Zero,
            wide = true,
            faceSize = 56.dp,
            onPick = {
                onSelect(it.id, true)
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

@Composable
private fun MoodOption(
    mood: Mood,
    selected: Boolean,
    dimmed: Boolean,
    hasAliases: Boolean,
    modifier: Modifier,
    onClick: () -> Unit,
) {
    val colors = moodColors(mood)
    val motion = MaterialTheme.motionScheme
    val interaction = remember { MutableInteractionSource() }
    val corner by animateDpAsState(if (selected) 28.dp else 40.dp, motion.defaultSpatialSpec(), label = "moodCorner")
    val container by animateColorAsState(
        if (selected) colors.tint else MaterialTheme.colorScheme.surfaceContainerLow,
        motion.defaultEffectsSpec(),
        label = "moodContainer",
    )
    val scale by animateFloatAsState(if (selected) 1.02f else if (dimmed) 0.97f else 1f, motion.defaultSpatialSpec(), label = "moodScale")
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(corner),
        color = container,
        interactionSource = interaction,
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = 80.dp)
            .pressScale(interaction)
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            }
            .semantics {
                role = Role.RadioButton
                this.selected = selected
            },
    ) {
        Row(
            Modifier.padding(horizontal = Spacing.l, vertical = Spacing.m),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            MoodBadge(mood, size = 56.dp, active = selected, dimmed = if (dimmed) 1f else 0f, hasAliases = hasAliases)
            Text(
                mood.label,
                style = MaterialTheme.typography.titleLargeEmphasized,
                color = if (selected) colors.accent else MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.padding(start = Spacing.l).weight(1f),
            )
            AnimatedVisibility(selected, enter = scaleIn() + fadeIn(), exit = scaleOut() + fadeOut()) {
                DakilioIcon(DakilioSymbols.CheckCircle, contentDescription = null, filled = true, tint = colors.accent, modifier = Modifier.size(28.dp))
            }
        }
    }
}

@Composable
internal fun StepHeader(title: String, body: String?, modifier: Modifier = Modifier) {
    Column(modifier.padding(top = Spacing.m, bottom = Spacing.m, start = Spacing.xs)) {
        Text(title, style = MaterialTheme.typography.headlineMediumEmphasized)
        body?.let {
            Text(
                it,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = Spacing.xs),
            )
        }
    }
}
