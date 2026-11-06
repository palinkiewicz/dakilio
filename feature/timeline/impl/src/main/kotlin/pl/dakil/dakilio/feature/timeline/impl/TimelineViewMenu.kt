package pl.dakil.dakilio.feature.timeline.impl

import androidx.compose.runtime.mutableStateOf
import kotlin.math.roundToInt
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.Slider
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.fadeOut
import androidx.compose.animation.fadeIn
import androidx.compose.animation.expandVertically
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import pl.dakil.dakilio.core.designsystem.component.ConnectedToggleRow
import pl.dakil.dakilio.core.designsystem.component.DimBehind
import pl.dakil.dakilio.core.designsystem.component.ToggleOption
import pl.dakil.dakilio.core.designsystem.component.rememberHaptics
import pl.dakil.dakilio.core.designsystem.icon.DakilioIcon
import pl.dakil.dakilio.core.designsystem.icon.DakilioSymbols
import pl.dakil.dakilio.core.designsystem.theme.Spacing
import pl.dakil.dakilio.core.model.PreviewLength
import pl.dakil.dakilio.core.model.TagLimit
import pl.dakil.dakilio.core.ui.LocalEntryDisplay
import pl.dakil.dakilio.core.ui.component.TextScaleSlider

/**
 * How post cards look, opened from the timeline's app bar and growing out of its button. The screen
 * behind is dimmed like a dialog's, but stays visible, so every change shows on the cards right
 * away. The values are the app-wide `EntryDisplay`; the text size is the same one as in Settings.
 */
@Composable
internal fun TimelineViewMenu(
    expanded: Boolean,
    onDismiss: () -> Unit,
    onPreviewLength: (PreviewLength) -> Unit,
    onCompactTags: (Boolean) -> Unit,
    onTagLimit: (TagLimit) -> Unit,
    monthNavigator: Boolean,
    onMonthNavigator: (Boolean) -> Unit,
    monthNavigatorInAppBar: Boolean,
    onMonthNavigatorInAppBar: (Boolean) -> Unit,
    onTextScale: (Float) -> Unit,
) {
    val display = LocalEntryDisplay.current
    DropdownMenu(
        expanded = expanded,
        onDismissRequest = onDismiss,
        shape = RoundedCornerShape(28.dp),
    ) {
        DimBehind()
        Column(Modifier.width(336.dp).padding(horizontal = Spacing.l, vertical = Spacing.s)) {
            MenuTitle(stringResource(R.string.timeline_view_preview))
            ConnectedToggleRow(
                options = listOf(
                    ToggleOption(PreviewLength.Short, stringResource(R.string.timeline_view_preview_short)),
                    ToggleOption(PreviewLength.Medium, stringResource(R.string.timeline_view_preview_medium)),
                    ToggleOption(PreviewLength.Long, stringResource(R.string.timeline_view_preview_long)),
                    ToggleOption(PreviewLength.Full, stringResource(R.string.timeline_view_preview_full)),
                ),
                selected = display.previewLength,
                onSelect = onPreviewLength,
                modifier = Modifier.padding(top = Spacing.s),
                contentPadding = PaddingValues(horizontal = 6.dp),
            )

            MenuSwitch(
                title = stringResource(R.string.timeline_view_compact_tags),
                body = stringResource(R.string.timeline_view_compact_tags_body),
                checked = display.compactTags,
                onCheckedChange = onCompactTags,
            )

            MenuTitle(stringResource(R.string.timeline_view_tag_limit), Modifier.padding(top = Spacing.l))
            // Local so the thumb follows the finger; each step is saved, so the cards follow too.
            var tagLimit by remember(display.tagLimit) { mutableStateOf(display.tagLimit) }
            TagLimitSlider(
                value = tagLimit,
                onValueChange = {
                    tagLimit = it
                    onTagLimit(it)
                },
            )
            MenuSwitch(
                title = stringResource(R.string.timeline_view_month_navigator),
                body = stringResource(R.string.timeline_view_month_navigator_body),
                checked = monthNavigator,
                onCheckedChange = onMonthNavigator,
            )
            AnimatedVisibility(
                visible = monthNavigator,
                enter = expandVertically(MaterialTheme.motionScheme.fastSpatialSpec()) + fadeIn(MaterialTheme.motionScheme.fastEffectsSpec()),
                exit = shrinkVertically(MaterialTheme.motionScheme.fastSpatialSpec()) + fadeOut(MaterialTheme.motionScheme.fastEffectsSpec()),
            ) {
                MenuSwitch(
                    title = stringResource(R.string.timeline_view_month_in_app_bar),
                    body = stringResource(R.string.timeline_view_month_in_app_bar_body),
                    checked = monthNavigatorInAppBar,
                    onCheckedChange = onMonthNavigatorInAppBar,
                )
            }

            MenuTitle(stringResource(R.string.timeline_view_text_size), Modifier.padding(top = Spacing.l))
            // Local so the thumb follows the finger; each step is saved, so the cards follow too.
            var scale by remember(display.timelineTextScale) { mutableFloatStateOf(display.timelineTextScale) }
            TextScaleSlider(
                value = scale,
                onValueChange = {
                    scale = it
                    onTextScale(it)
                },
            )
        }
    }
}

@Composable
private fun MenuSwitch(title: String, body: String, checked: Boolean, onCheckedChange: (Boolean) -> Unit) {
    val haptics = rememberHaptics()
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = Spacing.l)) {
        Column(Modifier.weight(1f)) {
            MenuTitle(title)
            Text(body, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Switch(
            checked = checked,
            onCheckedChange = {
                haptics.toggle(it)
                onCheckedChange(it)
            },
            thumbContent = if (checked) {
                { DakilioIcon(DakilioSymbols.Check, contentDescription = null, modifier = Modifier.size(SwitchDefaults.IconSize)) }
            } else {
                null
            },
        )
    }
}

@Composable
private fun MenuTitle(text: String, modifier: Modifier = Modifier) {
    Text(text, style = MaterialTheme.typography.titleSmallEmphasized, modifier = modifier)
}

/** 0 to [TagLimit.MAX] tags, then "All" as the last stop. */
@Composable
private fun TagLimitSlider(value: TagLimit, onValueChange: (TagLimit) -> Unit) {
    val haptics = rememberHaptics()
    val allStop = TagLimit.MAX + 1
    Row(verticalAlignment = Alignment.CenterVertically) {
        Slider(
            value = (value.count ?: allStop).toFloat(),
            onValueChange = {
                val stop = it.roundToInt()
                val picked = if (stop >= allStop) TagLimit.All else TagLimit.of(stop)
                if (picked != value) {
                    haptics.tick()
                    onValueChange(picked)
                }
            },
            valueRange = 0f..allStop.toFloat(),
            steps = allStop - 1,
            modifier = Modifier.weight(1f),
        )
        Text(
            value.count?.toString() ?: stringResource(R.string.timeline_view_tag_limit_all),
            style = MaterialTheme.typography.labelLarge,
            textAlign = TextAlign.End,
            maxLines = 1,
            modifier = Modifier.padding(start = Spacing.s).widthIn(min = 48.dp),
        )
    }
}
