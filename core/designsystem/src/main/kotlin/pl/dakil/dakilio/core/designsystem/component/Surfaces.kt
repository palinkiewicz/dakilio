package pl.dakil.dakilio.core.designsystem.component

import androidx.compose.material3.ListItemColors
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ToggleButtonColors
import androidx.compose.material3.ToggleButtonDefaults
import androidx.compose.runtime.Composable

/** Segmented list items as visible tonal tiles on the app's surface. */
@Composable
fun segmentedItemColors(): ListItemColors =
    ListItemDefaults.segmentedColors(containerColor = MaterialTheme.colorScheme.surfaceContainer)

/** Toggle buttons that stay visible on container-colored backgrounds (cards, scrolled app bars). */
@Composable
fun raisedToggleColors(): ToggleButtonColors =
    ToggleButtonDefaults.colors(containerColor = MaterialTheme.colorScheme.surfaceContainerHighest)
