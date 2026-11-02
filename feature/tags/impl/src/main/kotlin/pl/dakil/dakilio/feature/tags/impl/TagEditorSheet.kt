package pl.dakil.dakilio.feature.tags.impl

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.input.TextFieldLineLimits
import androidx.compose.foundation.text.input.rememberTextFieldState
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialShapes
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import pl.dakil.dakilio.core.designsystem.component.ShapedIcon
import pl.dakil.dakilio.core.designsystem.icon.DakilioIcon
import pl.dakil.dakilio.core.designsystem.icon.DakilioSymbols
import pl.dakil.dakilio.core.designsystem.icon.symbolForKey
import pl.dakil.dakilio.core.designsystem.theme.Spacing
import pl.dakil.dakilio.core.model.TagGroup
import pl.dakil.dakilio.core.ui.color.tagColors
import pl.dakil.dakilio.core.ui.component.ColorRow
import pl.dakil.dakilio.core.ui.component.IconPicker

private const val DEFAULT_TAG_ICON = "label"

@Composable
internal fun TagEditorSheet(
    edit: TagEdit,
    groups: List<TagGroup>,
    onDismiss: () -> Unit,
    onSave: (groupId: Long, label: String, iconKey: String, colorKey: String?) -> Unit,
    onDelete: (() -> Unit)?,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val label = rememberTextFieldState(edit.tag?.label.orEmpty())
    var iconKey by rememberSaveable { mutableStateOf(edit.tag?.iconKey ?: DEFAULT_TAG_ICON) }
    var groupId by rememberSaveable { mutableLongStateOf(edit.groupId) }
    // The tag's own color; `null` uses its group's.
    var colorKey by rememberSaveable { mutableStateOf(edit.tag?.colorKey) }
    val groupColorKey = groups.firstOrNull { it.id == groupId }?.colorKey
    // Every icon or color change gives the preview a little spin.
    var spins by rememberSaveable { mutableStateOf(0) }
    val rotation by animateFloatAsState(spins * 80f, MaterialTheme.motionScheme.slowSpatialSpec(), label = "previewSpin")
    val tones = tagColors(colorKey ?: groupColorKey)
    val previewContainer by animateColorAsState(
        tones?.container ?: MaterialTheme.colorScheme.primaryContainer,
        MaterialTheme.motionScheme.defaultEffectsSpec(),
        label = "previewContainer",
    )
    val previewContent by animateColorAsState(
        tones?.onContainer ?: MaterialTheme.colorScheme.onPrimaryContainer,
        MaterialTheme.motionScheme.defaultEffectsSpec(),
        label = "previewContent",
    )

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState) {
        Column(Modifier.fillMaxWidth().navigationBarsPadding()) {
            Row(Modifier.padding(horizontal = Spacing.xl), verticalAlignment = Alignment.CenterVertically) {
                AnimatedContent(iconKey, transitionSpec = { (scaleIn() + fadeIn()) togetherWith fadeOut() }, label = "preview") { key ->
                    ShapedIcon(
                        symbolForKey(key),
                        polygon = MaterialShapes.Cookie9Sided,
                        size = 64.dp,
                        iconSize = 32.dp,
                        containerColor = previewContainer,
                        contentColor = previewContent,
                        containerRotation = rotation,
                    )
                }
                Column(Modifier.padding(start = Spacing.l)) {
                    Text(
                        stringResource(if (edit.tag == null) R.string.tags_new_tag else R.string.tags_edit_tag),
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.primary,
                    )
                    Text(
                        label.text.toString().ifBlank { "…" },
                        style = MaterialTheme.typography.headlineSmallEmphasized,
                        maxLines = 1,
                    )
                }
            }
            OutlinedTextField(
                state = label,
                label = { Text(stringResource(R.string.tags_label)) },
                lineLimits = TextFieldLineLimits.SingleLine,
                modifier = Modifier.fillMaxWidth().padding(horizontal = Spacing.xl, vertical = Spacing.l),
            )
            if (groups.size > 1) {
                Text(
                    stringResource(R.string.tags_group),
                    style = MaterialTheme.typography.labelLargeEmphasized,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(horizontal = Spacing.xl),
                )
                Row(
                    Modifier.horizontalScroll(rememberScrollState()).padding(horizontal = Spacing.xl, vertical = Spacing.s),
                    horizontalArrangement = Arrangement.spacedBy(Spacing.s),
                ) {
                    groups.forEach { group ->
                        FilterChip(
                            selected = group.id == groupId,
                            onClick = { groupId = group.id },
                            label = { Text(group.name) },
                            shape = CircleShape,
                        )
                    }
                }
            }
            Text(
                stringResource(R.string.tags_color),
                style = MaterialTheme.typography.labelLargeEmphasized,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(horizontal = Spacing.xl),
            )
            ColorRow(
                selectedKey = colorKey,
                fallbackLabel = stringResource(R.string.tags_color_use_group),
                fallbackColor = tagColors(groupColorKey)?.container,
                onSelect = {
                    spins++
                    colorKey = it
                },
                contentPadding = PaddingValues(horizontal = Spacing.xl, vertical = Spacing.s),
            )
            Text(
                stringResource(R.string.tags_icon),
                style = MaterialTheme.typography.labelLargeEmphasized,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(horizontal = Spacing.xl, vertical = Spacing.xs),
            )
            IconPicker(
                selectedKey = iconKey,
                onSelect = {
                    if (it != iconKey) spins++
                    iconKey = it
                },
                contentPadding = PaddingValues(horizontal = Spacing.l, vertical = Spacing.xs),
                modifier = Modifier.fillMaxWidth().height(300.dp),
            )
            Row(Modifier.padding(Spacing.xl), verticalAlignment = Alignment.CenterVertically) {
                if (onDelete != null) {
                    OutlinedButton(
                        onClick = onDelete,
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error),
                    ) {
                        DakilioIcon(DakilioSymbols.Delete, contentDescription = null)
                        Text(stringResource(R.string.tags_delete), modifier = Modifier.padding(start = Spacing.s))
                    }
                }
                Spacer(Modifier.weight(1f))
                Button(onClick = { onSave(groupId, label.text.toString(), iconKey, colorKey) }, enabled = label.text.isNotBlank()) {
                    Text(stringResource(R.string.tags_save))
                }
            }
        }
    }
}
