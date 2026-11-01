package pl.dakil.dakilio.feature.tags.impl

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import pl.dakil.dakilio.core.designsystem.component.rememberHaptics
import pl.dakil.dakilio.core.designsystem.icon.DakilioIcon
import pl.dakil.dakilio.core.designsystem.icon.DakilioSymbols
import pl.dakil.dakilio.core.designsystem.theme.SegmentedShapes
import pl.dakil.dakilio.core.designsystem.theme.Spacing
import pl.dakil.dakilio.core.model.SuggestedGroup
import pl.dakil.dakilio.core.model.TagGroup
import pl.dakil.dakilio.core.ui.Hint
import pl.dakil.dakilio.core.ui.component.SuggestedGroupContent

/**
 * The ready-made groups a fresh install starts with, to add back later. One counts as added while
 * an active group has its name.
 */
@Composable
internal fun SuggestedGroupsSheet(
    suggestions: List<SuggestedGroup>,
    groups: List<TagGroup>,
    onAdd: (SuggestedGroup) -> Unit,
    onDismiss: () -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val haptics = rememberHaptics()
    val existing = groups.map { it.name.trim().lowercase() }.toSet()
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState) {
        LazyColumn(
            modifier = Modifier.fillMaxWidth().navigationBarsPadding(),
            contentPadding = PaddingValues(start = Spacing.gutter, end = Spacing.gutter, bottom = Spacing.xl),
            verticalArrangement = Arrangement.spacedBy(SegmentedShapes.gap),
        ) {
            item(key = "header") {
                Text(
                    stringResource(R.string.tags_suggested_title),
                    style = MaterialTheme.typography.headlineSmallEmphasized,
                    modifier = Modifier.padding(horizontal = Spacing.s).padding(bottom = Spacing.xs),
                )
                Hint {
                    Text(
                        stringResource(R.string.tags_suggested_hint),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = Spacing.s),
                    )
                }
            }
            itemsIndexed(suggestions, key = { _, group -> group.key }) { index, group ->
                val added = group.name.trim().lowercase() in existing
                Surface(
                    shape = SegmentedShapes.of(index, suggestions.size),
                    color = MaterialTheme.colorScheme.surfaceContainer,
                    // The first card keeps its distance from the header, with or without the hint.
                    modifier = Modifier.fillMaxWidth().padding(top = if (index == 0) Spacing.l else 0.dp),
                ) {
                    SuggestedGroupContent(group) {
                        AnimatedContent(added, transitionSpec = { (scaleIn() + fadeIn()) togetherWith fadeOut() }, label = "added") { isAdded ->
                            if (isAdded) {
                                Row(
                                    Modifier.padding(horizontal = Spacing.m, vertical = Spacing.s),
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    DakilioIcon(
                                        DakilioSymbols.CheckCircle,
                                        contentDescription = null,
                                        filled = true,
                                        tint = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.size(20.dp),
                                    )
                                    Text(
                                        stringResource(R.string.tags_suggested_added),
                                        style = MaterialTheme.typography.labelLarge,
                                        color = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.padding(start = Spacing.xs),
                                    )
                                }
                            } else {
                                FilledTonalButton(onClick = {
                                    haptics.confirm()
                                    onAdd(group)
                                }) {
                                    DakilioIcon(DakilioSymbols.Add, contentDescription = null, modifier = Modifier.padding(end = Spacing.xs))
                                    Text(stringResource(R.string.tags_suggested_add))
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
