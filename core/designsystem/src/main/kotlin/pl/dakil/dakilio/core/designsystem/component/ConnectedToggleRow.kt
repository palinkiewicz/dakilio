package pl.dakil.dakilio.core.designsystem.component

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.ButtonGroupDefaults
import androidx.compose.material3.ToggleButton
import androidx.compose.material3.ToggleButtonDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import pl.dakil.dakilio.core.designsystem.icon.DakilioIcon
import pl.dakil.dakilio.core.designsystem.icon.DakilioSymbol

@Immutable
data class ToggleOption<T>(val value: T, val label: String, val symbol: DakilioSymbol? = null)

/**
 * M3 Expressive connected button group used as a single-select segmented control: the checked
 * button morphs to a fully round shape and the neighbours keep their connected inner corners.
 */
@Composable
fun <T> ConnectedToggleRow(
    options: List<ToggleOption<T>>,
    selected: T,
    onSelect: (T) -> Unit,
    modifier: Modifier = Modifier,
    /** Narrower than the default for rows of many options in tight spaces (menus); null keeps the default. */
    contentPadding: PaddingValues? = null,
) {
    val haptics = rememberHaptics()
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(ButtonGroupDefaults.ConnectedSpaceBetween),
    ) {
        options.forEachIndexed { index, option ->
            val checked = option.value == selected
            val onCheckedChange = { _: Boolean ->
                if (!checked) {
                    haptics.tick()
                    onSelect(option.value)
                }
            }
            val buttonModifier = Modifier.weight(1f).semantics { role = Role.RadioButton }
            val shapes = when (index) {
                0 -> ButtonGroupDefaults.connectedLeadingButtonShapes()
                options.lastIndex -> ButtonGroupDefaults.connectedTrailingButtonShapes()
                else -> ButtonGroupDefaults.connectedMiddleButtonShapes()
            }
            val content: @Composable RowScope.() -> Unit = {
                option.symbol?.let {
                    DakilioIcon(it, contentDescription = null, filled = checked, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(ToggleButtonDefaults.IconSpacing))
                }
                FittingText(option.label)
            }
            // ToggleButton's default padding is private, so it can only be kept by not passing one.
            if (contentPadding == null) {
                ToggleButton(checked, onCheckedChange, buttonModifier, shapes = shapes, colors = raisedToggleColors(), content = content)
            } else {
                ToggleButton(
                    checked,
                    onCheckedChange,
                    buttonModifier,
                    shapes = shapes,
                    colors = raisedToggleColors(),
                    contentPadding = contentPadding,
                    content = content,
                )
            }
        }
    }
}
