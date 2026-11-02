package pl.dakil.dakilio.feature.tags.impl

import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import pl.dakil.dakilio.core.designsystem.component.rememberHaptics
import pl.dakil.dakilio.core.designsystem.theme.Spacing
import pl.dakil.dakilio.core.model.TagGroup
import pl.dakil.dakilio.core.ui.color.TagColor
import pl.dakil.dakilio.core.ui.component.ColorDot
import pl.dakil.dakilio.core.ui.component.ColorSwatch
import pl.dakil.dakilio.core.ui.component.TagPill

private const val COLUMNS = 6

/**
 * Picks the default color of a group's tags. A pick applies at once, so the live tags at the top
 * show it right away; the sheet stays open for trying others.
 */
@Composable
internal fun GroupColorSheet(group: TagGroup, onSelect: (String?) -> Unit, onDismiss: () -> Unit) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val haptics = rememberHaptics()
    val selected = TagColor.fromKey(group.colorKey)
    fun select(color: TagColor?) {
        if (color == selected) return
        haptics.toggle(color != null)
        onSelect(color?.key)
    }

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState) {
        Column(Modifier.fillMaxWidth().navigationBarsPadding().padding(horizontal = Spacing.xl)) {
            Text(
                stringResource(R.string.tags_color_sheet_group),
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary,
            )
            Text(group.name, style = MaterialTheme.typography.headlineSmallEmphasized, maxLines = 1)
            if (group.tags.isNotEmpty()) {
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                    modifier = Modifier.padding(top = Spacing.m).animateContentSize(),
                ) {
                    group.tags.forEach { TagPill(it) }
                }
            }
            FilterChip(
                selected = selected == null,
                onClick = { select(null) },
                label = { Text(stringResource(R.string.tags_color_none)) },
                leadingIcon = { ColorDot(null, size = 18.dp) },
                shape = CircleShape,
                modifier = Modifier.padding(top = Spacing.l),
            )
            Column(
                Modifier.padding(top = Spacing.m),
                verticalArrangement = Arrangement.spacedBy(Spacing.m),
            ) {
                TagColor.entries.chunked(COLUMNS).forEach { row ->
                    Row(Modifier.fillMaxWidth()) {
                        row.forEach { color ->
                            Box(Modifier.weight(1f), contentAlignment = Alignment.Center) {
                                ColorSwatch(color, selected = color == selected, size = 48.dp, onClick = { select(color) })
                            }
                        }
                        repeat(COLUMNS - row.size) { Spacer(Modifier.weight(1f)) }
                    }
                }
            }
            Row(Modifier.fillMaxWidth().padding(vertical = Spacing.xl)) {
                Spacer(Modifier.weight(1f))
                Button(onClick = onDismiss) { Text(stringResource(R.string.tags_color_done)) }
            }
        }
    }
}
