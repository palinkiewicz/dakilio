package pl.dakil.dakilio.core.ui.component

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.ButtonGroupDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.ToggleButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import java.time.DayOfWeek
import pl.dakil.dakilio.core.designsystem.component.raisedToggleColors
import pl.dakil.dakilio.core.designsystem.component.rememberHaptics
import pl.dakil.dakilio.core.ui.LocalFirstDayOfWeek
import pl.dakil.dakilio.core.ui.rememberFormats
import pl.dakil.dakilio.core.ui.weekdaysFrom

/** Seven connected toggles from the user's first day of the week; unlike `ConnectedToggleRow` any number can be on. */
@Composable
fun WeekdayToggles(days: Set<DayOfWeek>, onToggle: (DayOfWeek) -> Unit, modifier: Modifier = Modifier) {
    val haptics = rememberHaptics()
    val formats = rememberFormats()
    val weekdays = weekdaysFrom(LocalFirstDayOfWeek.current)
    Row(modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(ButtonGroupDefaults.ConnectedSpaceBetween)) {
        weekdays.forEachIndexed { index, day ->
            val name = formats.weekday(day)
            ToggleButton(
                checked = day in days,
                onCheckedChange = {
                    haptics.toggle(it)
                    onToggle(day)
                },
                shapes = when (index) {
                    0 -> ButtonGroupDefaults.connectedLeadingButtonShapes()
                    weekdays.lastIndex -> ButtonGroupDefaults.connectedTrailingButtonShapes()
                    else -> ButtonGroupDefaults.connectedMiddleButtonShapes()
                },
                colors = raisedToggleColors(),
                contentPadding = PaddingValues(0.dp),
                modifier = Modifier.weight(1f).semantics {
                    role = Role.Checkbox
                    contentDescription = name
                },
            ) { Text(formats.weekdayNarrow(day), maxLines = 1) }
        }
    }
}
