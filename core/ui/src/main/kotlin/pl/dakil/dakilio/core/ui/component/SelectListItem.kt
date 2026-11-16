package pl.dakil.dakilio.core.ui.component

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.material3.DropdownMenuGroup
import androidx.compose.material3.DropdownMenuPopup
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MenuAnchorPosition
import androidx.compose.material3.MenuDefaults
import androidx.compose.material3.SegmentedListItem
import androidx.compose.material3.SelectableDropdownMenuItem
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.dp
import androidx.graphics.shapes.RoundedPolygon
import pl.dakil.dakilio.core.designsystem.component.ShapedIcon
import pl.dakil.dakilio.core.designsystem.component.rememberHaptics
import pl.dakil.dakilio.core.designsystem.component.segmentedItemColors
import pl.dakil.dakilio.core.designsystem.icon.DakilioIcon
import pl.dakil.dakilio.core.designsystem.icon.DakilioSymbol
import pl.dakil.dakilio.core.designsystem.icon.DakilioSymbols

/** One choice of a [SelectListItem]: its [value], [label] and an optional [note] after the label ("Default"). */
data class SelectOption<T>(val value: T, val label: String, val note: String? = null)

/**
 * A settings row whose value is picked from a menu: the current choice sits at the end with a
 * drop-down arrow, and a tap opens the options right there (no dialog). Picking one applies it.
 * The segmented counterpart of [SwitchListItem]; [index] and [count] place it in its group.
 */
@Composable
fun <T> SelectListItem(
    title: String,
    body: String?,
    symbol: DakilioSymbol,
    polygon: RoundedPolygon,
    selected: T,
    options: List<SelectOption<T>>,
    onSelect: (T) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    index: Int = 0,
    count: Int = 1,
) {
    var expanded by rememberSaveable { mutableStateOf(false) }
    val haptics = rememberHaptics()
    val current = options.firstOrNull { it.value == selected }
    SegmentedListItem(
        onClick = { expanded = true },
        enabled = enabled,
        shapes = ListItemDefaults.segmentedShapes(index, count),
        colors = segmentedItemColors(),
        modifier = modifier.semantics {
            role = Role.DropdownList
            current?.let { stateDescription = it.label }
        },
        leadingContent = { ShapedIcon(symbol, polygon = polygon) },
        supportingContent = body?.let { { Text(it) } },
        trailingContent = {
            // The menu hangs from this end box, so it opens under the value rather than the title.
            Box {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        current?.label.orEmpty(),
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.primary,
                    )
                    DakilioIcon(DakilioSymbols.ArrowDropDown, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                }
                DropdownMenuPopup(
                    expanded = expanded && enabled,
                    onDismissRequest = { expanded = false },
                    popupPositionProvider = MenuDefaults.rememberDropdownMenuPopupPositionProvider(
                        MenuAnchorPosition.Below,
                        DpOffset(0.dp, 0.dp),
                    ),
                ) {
                    DropdownMenuGroup(shapes = MenuDefaults.groupShape(0, 1)) {
                        options.forEach { option ->
                            SelectableDropdownMenuItem(
                                selected = option.value == selected,
                                onClick = {
                                    if (option.value != selected) haptics.tick()
                                    onSelect(option.value)
                                    expanded = false
                                },
                                text = { Text(option.label) },
                                trailingContent = option.note?.let {
                                    {
                                        Text(
                                            it,
                                            style = MaterialTheme.typography.labelMedium,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        )
                                    }
                                },
                                shapes = MenuDefaults.itemShapes(),
                                selectedLeadingIcon = {
                                    DakilioIcon(DakilioSymbols.Check, contentDescription = null, modifier = Modifier.size(24.dp))
                                },
                            )
                        }
                    }
                }
            }
        },
    ) { Text(title) }
}
