package pl.dakil.dakilio.core.ui.component

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.ToggleButton
import androidx.compose.material3.ToggleButtonDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import pl.dakil.dakilio.core.designsystem.component.pressScale
import pl.dakil.dakilio.core.designsystem.component.raisedToggleColors
import pl.dakil.dakilio.core.designsystem.component.rememberHaptics
import pl.dakil.dakilio.core.designsystem.icon.DakilioIcon
import pl.dakil.dakilio.core.designsystem.icon.DakilioSymbol
import pl.dakil.dakilio.core.designsystem.icon.DakilioSymbols
import pl.dakil.dakilio.core.designsystem.icon.symbolForKey
import pl.dakil.dakilio.core.model.Diary
import pl.dakil.dakilio.core.ui.R

/**
 * Single-select diary filter ("All" + every diary) as a scrollable row of expressive toggle buttons:
 * the checked one morphs from a pill into a rounded square and fills with the primary color.
 */
@Composable
fun DiaryFilterRow(
    diaries: List<Diary>,
    selectedDiaryId: Long?,
    onSelect: (Long?) -> Unit,
    modifier: Modifier = Modifier,
    contentPadding: PaddingValues = PaddingValues(horizontal = 16.dp),
) {
    val haptics = rememberHaptics()
    Row(
        modifier = modifier.horizontalScroll(rememberScrollState()).padding(contentPadding),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        DiaryToggle(
            label = stringResource(R.string.ui_all_diaries),
            symbol = DakilioSymbols.AutoStories,
            checked = selectedDiaryId == null,
            onClick = {
                haptics.tick()
                onSelect(null)
            },
        )
        diaries.forEach { diary ->
            DiaryToggle(
                label = diary.name,
                symbol = symbolForKey(diary.iconKey),
                checked = selectedDiaryId == diary.id,
                onClick = {
                    haptics.tick()
                    onSelect(diary.id)
                },
            )
        }
    }
}

/**
 * Single-select diary filter ("All diaries" + every diary) as a compact button that opens a menu,
 * small enough for an app bar's subtitle. It takes as much width as its label needs; a long name is
 * ellipsized when space is short.
 */
@Composable
fun DiaryFilterDropdown(
    diaries: List<Diary>,
    selectedDiaryId: Long?,
    onSelect: (Long?) -> Unit,
    modifier: Modifier = Modifier,
) {
    val haptics = rememberHaptics()
    var expanded by rememberSaveable { mutableStateOf(false) }
    val selected = diaries.firstOrNull { it.id == selectedDiaryId }
    val allLabel = stringResource(R.string.ui_all_diaries_long)
    val label = selected?.name ?: allLabel
    val description = stringResource(R.string.ui_diary_filter_cd, label)
    val rotation by animateFloatAsState(
        if (expanded) 180f else 0f,
        MaterialTheme.motionScheme.defaultSpatialSpec(),
        label = "chevron",
    )
    val interaction = remember { MutableInteractionSource() }
    // The menu anchors to this Box, so it opens right under the pill.
    Box(modifier) {
        Surface(
            onClick = {
                haptics.tick()
                expanded = true
            },
            shape = RoundedCornerShape(8.dp),
            color = MaterialTheme.colorScheme.surfaceContainerHighest,
            contentColor = MaterialTheme.colorScheme.onSurface,
            interactionSource = interaction,
            modifier = Modifier
                .pressScale(interaction, 0.94f)
                .semantics {
                    role = Role.DropdownList
                    contentDescription = description
                },
        ) {
            Row(
                modifier = Modifier.heightIn(min = 32.dp).padding(start = 8.dp, end = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                DakilioIcon(
                    selected?.let { symbolForKey(it.iconKey) } ?: DakilioSymbols.AutoStories,
                    contentDescription = null,
                    filled = true,
                    modifier = Modifier.size(16.dp),
                )
                Text(
                    label,
                    style = MaterialTheme.typography.labelLargeEmphasized,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false).padding(start = 6.dp, end = 2.dp),
                )
                DakilioIcon(
                    DakilioSymbols.ExpandMore,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp).graphicsLayer { rotationZ = rotation },
                )
            }
        }
        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
            shape = RoundedCornerShape(28.dp),
        ) {
            fun pick(id: Long?) {
                haptics.tick()
                expanded = false
                if (id != selectedDiaryId) onSelect(id)
            }
            DiaryMenuItem(allLabel, DakilioSymbols.AutoStories, selectedDiaryId == null) { pick(null) }
            diaries.forEach { diary ->
                DiaryMenuItem(diary.name, symbolForKey(diary.iconKey), diary.id == selectedDiaryId) { pick(diary.id) }
            }
        }
    }
}

@Composable
private fun DiaryMenuItem(label: String, symbol: DakilioSymbol, selected: Boolean, onClick: () -> Unit) {
    DropdownMenuItem(
        text = {
            Text(
                label,
                style = if (selected) MaterialTheme.typography.bodyLargeEmphasized else MaterialTheme.typography.bodyLarge,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        },
        onClick = onClick,
        leadingIcon = { DakilioIcon(symbol, contentDescription = null, filled = selected) },
        trailingIcon = if (selected) {
            { DakilioIcon(DakilioSymbols.Check, contentDescription = null) }
        } else {
            null
        },
        modifier = Modifier.semantics { this.selected = selected },
    )
}

/**
 * Single-select diary picker for the entry editor: an entry is in exactly one diary, so the checked
 * one stays checked when tapped again.
 */
@Composable
fun DiarySelector(
    diaries: List<Diary>,
    selectedId: Long?,
    onSelect: (Long) -> Unit,
    modifier: Modifier = Modifier,
    contentPadding: PaddingValues = PaddingValues(horizontal = 16.dp),
) {
    val haptics = rememberHaptics()
    Row(
        modifier = modifier.horizontalScroll(rememberScrollState()).padding(contentPadding),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        diaries.forEach { diary ->
            val checked = diary.id == selectedId
            DiaryToggle(
                label = diary.name,
                symbol = if (checked) DakilioSymbols.Check else symbolForKey(diary.iconKey),
                checked = checked,
                onClick = {
                    if (!checked) {
                        haptics.toggle(true)
                        onSelect(diary.id)
                    }
                },
            )
        }
    }
}

@Composable
private fun DiaryToggle(
    label: String,
    symbol: DakilioSymbol,
    checked: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    ToggleButton(
        checked = checked,
        onCheckedChange = { onClick() },
        modifier = modifier,
        colors = raisedToggleColors(),
    ) {
        DakilioIcon(symbol, contentDescription = null, filled = checked, modifier = Modifier.size(18.dp))
        Spacer(Modifier.width(ToggleButtonDefaults.IconSpacing))
        Text(label, maxLines = 1)
    }
}
