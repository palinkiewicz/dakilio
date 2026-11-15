package pl.dakil.dakilio.feature.insights.impl

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.widthIn
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
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.delay
import pl.dakil.dakilio.core.designsystem.component.rememberHaptics
import pl.dakil.dakilio.core.designsystem.theme.Spacing
import pl.dakil.dakilio.core.model.Milestone

/** Lets the editor's container transform settle before the banner drops in. */
private const val ENTER_DELAY_MS = 520L

/** Between the banner arriving and the stamp being pressed onto it. */
private const val PRESS_DELAY_MS = 260L

private const val SHOW_MS = 4_200L
private const val MAX_ROWS = 2

/**
 * The milestone banner: when saving an entry collects a stamp, a card drops in from the top, the
 * stamp is pressed onto it with a tick, and it leaves on its own after a few seconds (a tap sends it
 * off sooner). It comes from the top so it never covers the habit popup at the bottom.
 *
 * Placed once by the app shell, over every screen. [onOpen] opens the Milestones screen.
 */
@Composable
fun MilestoneCelebrationHost(modifier: Modifier = Modifier, onOpen: () -> Unit) {
    val viewModel: MilestoneCelebrationViewModel = hiltViewModel()
    val celebrating by viewModel.celebrating.collectAsStateWithLifecycle()
    var shown by remember { mutableStateOf(emptyList<Milestone>()) }
    var visible by remember { mutableStateOf(false) }

    LaunchedEffect(celebrating) {
        if (celebrating.isEmpty()) {
            visible = false
            return@LaunchedEffect
        }
        if (visible) {
            visible = false
            delay(200)
        }
        delay(ENTER_DELAY_MS)
        shown = celebrating
        visible = true
        delay(SHOW_MS)
        viewModel.consume()
    }

    val motion = MaterialTheme.motionScheme
    AnimatedVisibility(
        visible = visible,
        modifier = modifier.statusBarsPadding().padding(horizontal = Spacing.gutter, vertical = Spacing.s).widthIn(max = 480.dp),
        enter = slideInVertically(motion.defaultSpatialSpec()) { -it } + fadeIn(motion.defaultEffectsSpec()),
        exit = slideOutVertically(motion.fastSpatialSpec()) { -it } + fadeOut(motion.fastEffectsSpec()),
    ) {
        Surface(
            onClick = {
                viewModel.consume()
                onOpen()
            },
            shape = MaterialTheme.shapes.extraLarge,
            color = MaterialTheme.colorScheme.tertiaryContainer,
            contentColor = MaterialTheme.colorScheme.onTertiaryContainer,
            shadowElevation = 6.dp,
            modifier = Modifier.fillMaxWidth().semantics { liveRegion = LiveRegionMode.Polite },
        ) {
            Column(Modifier.padding(Spacing.l), verticalArrangement = Arrangement.spacedBy(Spacing.m)) {
                shown.take(MAX_ROWS).forEachIndexed { index, milestone -> CelebrationRow(milestone, index) }
            }
        }
    }
}

@Composable
private fun CelebrationRow(milestone: Milestone, index: Int) {
    val haptics = rememberHaptics()
    val settle = MaterialTheme.motionScheme.defaultSpatialSpec<Float>()
    // The stamp comes down from above the page: large and faint, then pressed to size.
    val press = remember(milestone) { Animatable(0f) }
    LaunchedEffect(milestone) {
        delay(PRESS_DELAY_MS + 140L * index)
        if (index == 0) haptics.confirm()
        press.animateTo(1f, settle)
    }
    Row(verticalAlignment = Alignment.CenterVertically) {
        Stamp(
            milestone,
            reached = true,
            emphasized = true,
            size = 56.dp,
            modifier = Modifier.graphicsLayer {
                val scale = 1.7f - 0.7f * press.value
                scaleX = scale
                scaleY = scale
                alpha = press.value.coerceIn(0f, 1f)
            },
        )
        Column(Modifier.padding(start = Spacing.l)) {
            Text(stringResource(R.string.milestones_new_stamp), style = MaterialTheme.typography.labelLarge)
            Text(milestone.label(), style = MaterialTheme.typography.titleMediumEmphasized)
        }
    }
}
