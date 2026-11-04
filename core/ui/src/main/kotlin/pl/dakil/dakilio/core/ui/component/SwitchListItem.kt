package pl.dakil.dakilio.core.ui.component

import androidx.compose.foundation.layout.size
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.SegmentedListItem
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.graphics.shapes.RoundedPolygon
import pl.dakil.dakilio.core.designsystem.component.ShapedIcon
import pl.dakil.dakilio.core.designsystem.component.rememberHaptics
import pl.dakil.dakilio.core.designsystem.component.segmentedItemColors
import pl.dakil.dakilio.core.designsystem.icon.DakilioIcon
import pl.dakil.dakilio.core.designsystem.icon.DakilioSymbol
import pl.dakil.dakilio.core.designsystem.icon.DakilioSymbols

/**
 * A setting with a switch; the whole row toggles it. [index] and [count] place it in a segmented
 * group (a lone row by default).
 */
@Composable
fun SwitchListItem(
    title: String,
    body: String,
    symbol: DakilioSymbol,
    polygon: RoundedPolygon,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    index: Int = 0,
    count: Int = 1,
) {
    val haptics = rememberHaptics()
    val toggle = { value: Boolean ->
        haptics.toggle(value)
        onCheckedChange(value)
    }
    SegmentedListItem(
        onClick = { toggle(!checked) },
        enabled = enabled,
        shapes = ListItemDefaults.segmentedShapes(index, count),
        colors = segmentedItemColors(),
        modifier = modifier,
        leadingContent = { ShapedIcon(symbol, polygon = polygon) },
        supportingContent = { Text(body) },
        trailingContent = {
            Switch(
                checked = checked,
                onCheckedChange = toggle,
                enabled = enabled,
                thumbContent = if (checked) {
                    { DakilioIcon(DakilioSymbols.Check, contentDescription = null, modifier = Modifier.size(SwitchDefaults.IconSize)) }
                } else {
                    null
                },
            )
        },
    ) { Text(title) }
}
