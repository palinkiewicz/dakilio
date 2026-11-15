package pl.dakil.dakilio.feature.entry.impl

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.IconButton
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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import pl.dakil.dakilio.core.designsystem.component.rememberHaptics
import pl.dakil.dakilio.core.designsystem.icon.DakilioIcon
import pl.dakil.dakilio.core.designsystem.icon.DakilioSymbols
import pl.dakil.dakilio.core.designsystem.theme.Spacing
import pl.dakil.dakilio.core.ui.Hint

/**
 * A writing prompt above the title (the Prompts extra), shown only while the note is still blank:
 * tapping it starts the entry with the question, the shuffle button brings another. It folds away
 * as soon as [offer] goes, i.e. once anything is typed.
 */
@Composable
internal fun PromptCard(offer: PromptOffer?, onUse: () -> Unit, onAnother: () -> Unit, modifier: Modifier = Modifier) {
    val haptics = rememberHaptics()
    // Keeps the last question on screen while the card folds away.
    var shown by remember { mutableStateOf(offer) }
    if (offer != null) shown = offer
    AnimatedVisibility(
        visible = offer != null,
        enter = expandVertically() + fadeIn(),
        exit = shrinkVertically() + fadeOut(),
        modifier = modifier,
    ) {
        val current = shown ?: return@AnimatedVisibility
        Surface(
            onClick = {
                haptics.confirm()
                onUse()
            },
            shape = RoundedCornerShape(20.dp),
            color = MaterialTheme.colorScheme.secondaryContainer,
            contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
            modifier = Modifier.fillMaxWidth().padding(horizontal = Spacing.gutter, vertical = Spacing.xs),
        ) {
            Row(Modifier.padding(start = Spacing.l, top = Spacing.s, bottom = Spacing.s, end = Spacing.xs), verticalAlignment = Alignment.CenterVertically) {
                DakilioIcon(DakilioSymbols.Lightbulb, contentDescription = null, filled = true)
                Column(Modifier.weight(1f).padding(horizontal = Spacing.m, vertical = Spacing.xs)) {
                    AnimatedContent(current.prompt.text, transitionSpec = { fadeIn() togetherWith fadeOut() }, label = "prompt") { text ->
                        Text(text, style = MaterialTheme.typography.bodyLarge)
                    }
                    Hint {
                        Text(
                            stringResource(R.string.entry_prompt_hint),
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.75f),
                        )
                    }
                }
                if (current.hasMore) {
                    IconButton(onClick = {
                        haptics.tick()
                        onAnother()
                    }) {
                        DakilioIcon(DakilioSymbols.Shuffle, contentDescription = stringResource(R.string.entry_prompt_another))
                    }
                }
            }
        }
    }
}
