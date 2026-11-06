package pl.dakil.dakilio.feature.timeline.impl

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import java.time.YearMonth
import pl.dakil.dakilio.core.designsystem.theme.Spacing
import pl.dakil.dakilio.core.ui.DakilioFormats
import pl.dakil.dakilio.core.ui.component.DiaryFilterDropdown

/** Whether the app bar shows [TimelineHeaderControls] (the collapsed bar shows the date otherwise). */
internal val TimelineUiState.Ready.hasHeaderControls: Boolean
    get() = diaries.size > 1 || monthNavigator

/** Both pickers share the bar: they're compacted, and search moves out to a FAB. */
internal val TimelineUiState.Ready.hasBothPickers: Boolean
    get() = diaries.size > 1 && monthNavigator

/**
 * The timeline app bar's pickers, in its navigation slot so they stay in place whether the bar is
 * expanded or collapsed: the diary dropdown (only with more than one diary) followed by the month
 * navigator (when enabled). With both, give it a bounded width: the navigator fills what the
 * dropdown leaves.
 */
@Composable
internal fun TimelineHeaderControls(
    state: TimelineUiState.Ready,
    month: YearMonth,
    newer: YearMonth?,
    older: YearMonth?,
    formats: DakilioFormats,
    onSelectDiary: (Long?) -> Unit,
    onJump: (YearMonth) -> Unit,
    onPick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val both = state.hasBothPickers
    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(Spacing.xs),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (state.diaries.size > 1) {
            DiaryFilterDropdown(
                diaries = state.diaries,
                selectedDiaryId = state.selectedDiaryId,
                onSelect = onSelectDiary,
                modifier = Modifier.weight(1f, fill = false),
            )
        }
        if (state.monthNavigator) {
            MonthNavigator(
                month = month,
                newer = newer,
                older = older,
                formats = formats,
                onJump = onJump,
                onPick = onPick,
                compact = both,
                modifier = if (both) Modifier.weight(1f) else Modifier,
            )
        }
    }
}
