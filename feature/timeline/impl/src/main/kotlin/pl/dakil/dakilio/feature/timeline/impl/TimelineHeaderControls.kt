package pl.dakil.dakilio.feature.timeline.impl

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.Layout
import java.time.YearMonth
import pl.dakil.dakilio.core.designsystem.theme.Spacing
import pl.dakil.dakilio.core.ui.DakilioFormats
import pl.dakil.dakilio.core.ui.component.DiaryFilterDropdown

/** Whether the app bar shows [TimelineHeaderControls] (the collapsed bar shows the date otherwise). */
internal val TimelineUiState.Ready.hasHeaderControls: Boolean
    get() = diaries.size > 1 || monthInAppBar

/** Both pickers share the bar: they're compacted, and search moves out to a FAB. */
internal val TimelineUiState.Ready.hasBothPickers: Boolean
    get() = diaries.size > 1 && monthInAppBar

/** The month navigator shows, in the app bar rather than floating over the list. */
internal val TimelineUiState.Ready.monthInAppBar: Boolean
    get() = monthNavigator && monthNavigatorInAppBar

/** The month navigator shows, floating over the list once it's scrolled past today. */
internal val TimelineUiState.Ready.monthFloating: Boolean
    get() = monthNavigator && !monthNavigatorInAppBar

/**
 * The timeline app bar's pickers, in its title slot so they stay in place whether the bar is expanded
 * or collapsed: the diary dropdown (only with more than one diary) followed by the month navigator
 * (when enabled). The dropdown is as wide as its label, up to what the narrowest navigator leaves;
 * the navigator then takes all the rest. Next to the dropdown it stacks the short month over its year
 * only when the one-line label doesn't fit.
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
    val navigatorMin = rememberMonthLabelWidths(month.year, formats).minNavigatorWidth(canCompact = both)
    val showDropdown = state.diaries.size > 1
    val showMonth = state.monthInAppBar
    Layout(
        modifier = modifier,
        content = {
            if (showDropdown) {
                DiaryFilterDropdown(
                    diaries = state.diaries,
                    selectedDiaryId = state.selectedDiaryId,
                    onSelect = onSelectDiary,
                )
            }
            if (showMonth) {
                MonthNavigator(
                    month = month,
                    newer = newer,
                    older = older,
                    formats = formats,
                    onJump = onJump,
                    onPick = onPick,
                    canCompact = both,
                    fill = true,
                )
            }
        },
    ) { measurables, constraints ->
        val loose = constraints.copy(minWidth = 0, minHeight = 0)
        val gap = Spacing.xs.roundToPx()
        val reserved = if (showMonth && showDropdown) navigatorMin.roundToPx() + gap else 0
        val dropdown = if (showDropdown) {
            measurables[0].measure(loose.copy(maxWidth = (loose.maxWidth - reserved).coerceAtLeast(0)))
        } else {
            null
        }
        val used = dropdown?.let { it.width + gap } ?: 0
        val navigator = if (showMonth) {
            val width = (loose.maxWidth - used).coerceAtLeast(0)
            measurables.last().measure(loose.copy(minWidth = width, maxWidth = width))
        } else {
            null
        }
        val width = if (navigator != null) loose.maxWidth else dropdown?.width ?: 0
        val height = maxOf(dropdown?.height ?: 0, navigator?.height ?: 0).coerceIn(constraints.minHeight, constraints.maxHeight)
        layout(width.coerceIn(constraints.minWidth, constraints.maxWidth), height) {
            dropdown?.placeRelative(0, (height - dropdown.height) / 2)
            navigator?.placeRelative(used, (height - navigator.height) / 2)
        }
    }
}
