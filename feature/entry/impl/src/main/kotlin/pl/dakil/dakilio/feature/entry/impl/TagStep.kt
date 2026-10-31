package pl.dakil.dakilio.feature.entry.impl

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import pl.dakil.dakilio.core.designsystem.theme.Spacing
import pl.dakil.dakilio.core.model.TagGroup
import pl.dakil.dakilio.core.model.TagStyle
import pl.dakil.dakilio.core.ui.hintOrNull

/**
 * The wizard's tag step. Every group is a collapsible panel; the ones suggested by the selected
 * diaries start open, the rest wait under "More groups" (see [tagGroupItems]).
 */
@Composable
internal fun TagStep(
    suggested: List<TagGroup>,
    others: List<TagGroup>,
    selectedIds: Set<Long>,
    style: TagStyle,
    toggledGroups: Set<Long>,
    onToggleGroup: (Long) -> Unit,
    onToggle: (Long) -> Unit,
    onManageTags: () -> Unit,
    modifier: Modifier = Modifier,
) {
    LazyColumn(
        modifier = modifier,
        contentPadding = PaddingValues(horizontal = Spacing.gutter, vertical = Spacing.s),
        verticalArrangement = Arrangement.spacedBy(Spacing.s),
    ) {
        item(key = "header") {
            StepHeader(stringResource(R.string.entry_tags_title), hintOrNull(stringResource(R.string.entry_tags_body)))
        }
        tagGroupItems(
            suggested = suggested,
            others = others,
            selectedIds = selectedIds,
            style = style,
            toggledGroups = toggledGroups,
            onToggleGroup = onToggleGroup,
            onToggle = onToggle,
            onManageTags = onManageTags,
        )
    }
}
