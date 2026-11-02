package pl.dakil.dakilio.core.ui.component

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.Text
import androidx.compose.material3.ToggleButton
import androidx.compose.material3.ToggleButtonDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
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
