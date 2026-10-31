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
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
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
import pl.dakil.dakilio.core.ui.hintOrNull
import pl.dakil.dakilio.core.ui.mood.MoodBadge
import pl.dakil.dakilio.core.ui.mood.moodColors

/** The hero step: big, tactile mood buttons, best mood on top. */
@Composable
internal fun MoodStep(moods: List<Mood>, selectedId: Long?, onSelect: (Long) -> Unit, modifier: Modifier = Modifier) {
    val haptics = rememberHaptics()
    Column(
        modifier = modifier.verticalScroll(rememberScrollState()).padding(horizontal = Spacing.gutter, vertical = Spacing.s),
        verticalArrangement = Arrangement.spacedBy(Spacing.s),
    ) {
        StepHeader(stringResource(R.string.entry_mood_title), hintOrNull(stringResource(R.string.entry_mood_body)))
        moods.asReversed().forEach { mood ->
            MoodOption(
                mood = mood,
                selected = mood.id == selectedId,
                dimmed = selectedId != null && mood.id != selectedId,
                onClick = {
                    haptics.confirm()
                    onSelect(mood.id)
                },
            )
        }
    }
}

@Composable
private fun MoodOption(mood: Mood, selected: Boolean, dimmed: Boolean, onClick: () -> Unit) {
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
        modifier = Modifier
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
            MoodBadge(mood, size = 56.dp, active = selected, dimmed = if (dimmed) 1f else 0f)
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
