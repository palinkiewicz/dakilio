package pl.dakil.dakilio.feature.entry.impl

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import pl.dakil.dakilio.core.designsystem.icon.DakilioIcon
import pl.dakil.dakilio.core.designsystem.icon.DakilioSymbols
import pl.dakil.dakilio.core.designsystem.icon.symbolForKey
import kotlinx.coroutines.delay
import pl.dakil.dakilio.core.designsystem.component.pressScale
import pl.dakil.dakilio.core.designsystem.component.rememberHaptics
import pl.dakil.dakilio.core.designsystem.theme.Spacing

/** How long the keyword toast stays up after its latest match. */
private const val KEYWORD_TOAST_MS = 2_500L

/** Chips shown in the toast; further tags are summed up as "+N". */
private const val MAX_TOAST_CHIPS = 2

/**
 * A brief pill at the bottom of the editor when a keyword matches while typing: a gentle tick, the
 * matched tag's chip and, for auto-added tags, an Undo. A tap on a suggestion adds it. It leaves on
 * its own after [KEYWORD_TOAST_MS]; a newer match restarts the timer.
 */
@Composable
internal fun KeywordToastHost(
    toast: KeywordToast?,
    onTimeout: (Long) -> Unit,
    onUndo: () -> Unit,
    onAdd: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val haptics = rememberHaptics()
    // Keep the last toast while it animates out.
    val last = remember { arrayOf(toast) }
    if (toast != null) last[0] = toast
    val shown = last[0]

    LaunchedEffect(toast?.id) {
        val id = toast?.id ?: return@LaunchedEffect
        haptics.tick()
        delay(KEYWORD_TOAST_MS)
        onTimeout(id)
    }

    val motion = MaterialTheme.motionScheme
    AnimatedVisibility(
        visible = toast != null,
        enter = slideInVertically(motion.fastSpatialSpec()) { it / 2 } + fadeIn(motion.fastEffectsSpec()),
        exit = slideOutVertically(motion.fastSpatialSpec()) { it / 2 } + fadeOut(motion.fastEffectsSpec()),
        modifier = modifier,
    ) {
        if (shown != null) {
            ToastPill(
                shown,
                onUndo = {
                    haptics.toggle(false)
                    onUndo()
                },
                onAdd = {
                    haptics.toggle(true)
                    onAdd()
                },
            )
        }
    }
}

/**
 * The toast is the tag chip itself, worn in the first tag's colors: the check or plus, each shown
 * tag's symbol and label, "+N" for the rest and, for auto-added tags, a compact Undo.
 */
@Composable
private fun ToastPill(toast: KeywordToast, onUndo: () -> Unit, onAdd: () -> Unit) {
    val first = toast.tags.first()
    val (container, content) = keywordChipColors(first)
    val names = toast.tags.joinToString { it.tag.label }
    val announcement = stringResource(
        if (toast.anyAdded) R.string.entry_keywords_toast_added else R.string.entry_keywords_toast_suggested,
        names,
    )
    val interaction = remember { MutableInteractionSource() }
    val body: @Composable () -> Unit = {
        Row(
            Modifier.padding(start = 10.dp, end = if (toast.anyAdded) 4.dp else 12.dp, top = 6.dp, bottom = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            DakilioIcon(
                if (toast.anyAdded) DakilioSymbols.Check else DakilioSymbols.Add,
                contentDescription = null,
                tint = content,
                modifier = Modifier.size(16.dp),
            )
            toast.tags.take(MAX_TOAST_CHIPS).forEachIndexed { i, chip ->
                if (i > 0) Spacer(Modifier.width(2.dp))
                DakilioIcon(symbolForKey(chip.tag.iconKey), contentDescription = null, tint = content, modifier = Modifier.size(16.dp))
                Text(
                    chip.tag.label,
                    style = MaterialTheme.typography.labelLarge,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.widthIn(max = 120.dp),
                )
            }
            val extra = toast.tags.size - MAX_TOAST_CHIPS
            if (extra > 0) Text(stringResource(R.string.entry_keywords_more, extra), style = MaterialTheme.typography.labelLarge)
            if (toast.anyAdded) {
                Text(
                    stringResource(R.string.entry_keywords_undo),
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier
                        .clip(CircleShape)
                        .clickable(role = Role.Button, onClick = onUndo)
                        .padding(horizontal = 8.dp, vertical = 2.dp),
                )
            }
        }
    }
    val modifier = Modifier
        .padding(horizontal = Spacing.gutter)
        .semantics {
            liveRegion = LiveRegionMode.Polite
            contentDescription = announcement
        }
    if (toast.anyAdded) {
        Surface(shape = CircleShape, color = container, contentColor = content, shadowElevation = 3.dp, modifier = modifier) { body() }
    } else {
        Surface(
            onClick = onAdd,
            shape = CircleShape,
            color = container,
            contentColor = content,
            shadowElevation = 3.dp,
            interactionSource = interaction,
            modifier = modifier.pressScale(interaction),
        ) { body() }
    }
}
