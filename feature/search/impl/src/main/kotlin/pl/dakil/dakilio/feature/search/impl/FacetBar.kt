package pl.dakil.dakilio.feature.search.impl

import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import java.time.LocalDate
import pl.dakil.dakilio.core.designsystem.component.rememberHaptics
import pl.dakil.dakilio.core.designsystem.icon.DakilioIcon
import pl.dakil.dakilio.core.designsystem.icon.DakilioSymbol
import pl.dakil.dakilio.core.designsystem.icon.DakilioSymbols
import pl.dakil.dakilio.core.designsystem.icon.symbolForKey
import pl.dakil.dakilio.core.designsystem.theme.Spacing
import pl.dakil.dakilio.core.ui.DakilioFormats
import pl.dakil.dakilio.core.ui.mood.MoodBadge
import pl.dakil.dakilio.core.ui.rememberFormats

/**
 * One chip per filter category. A chip in use shows its value ("Walk +2") and a clear button;
 * tapping a chip opens its sheet. The Diary chip only appears with more than one diary.
 */
@Composable
internal fun FacetBar(state: SearchUiState, onOpen: (Facet) -> Unit, onClear: (Facet) -> Unit, modifier: Modifier = Modifier) {
    val haptics = rememberHaptics()
    val formats = rememberFormats()
    val filters = state.filters
    Row(
        modifier.horizontalScroll(rememberScrollState()).padding(horizontal = Spacing.gutter),
        horizontalArrangement = Arrangement.spacedBy(Spacing.s),
    ) {
        Facet.entries.forEach { facet ->
            if (facet == Facet.Diary && state.diaries.size < 2 && filters.diaryIds.isEmpty()) return@forEach
            val values = facetValues(facet, state, formats)
            val active = values.isNotEmpty()
            val name = facetName(facet)
            val icon = facetIcon(facet, state)
            FilterChip(
                selected = active,
                onClick = {
                    haptics.tick()
                    onOpen(facet)
                },
                label = { Text(if (active) moreLabel(values) else name, maxLines = 1) },
                leadingIcon = if (icon != null) { { icon(active) } } else null,
                trailingIcon = {
                    if (active) {
                        DakilioIcon(
                            DakilioSymbols.Close,
                            contentDescription = stringResource(R.string.search_facet_clear, name),
                            modifier = Modifier
                                .size(FilterChipDefaults.IconSize)
                                .clip(CircleShape)
                                .clickable(role = Role.Button) {
                                    haptics.toggle(false)
                                    onClear(facet)
                                },
                        )
                    } else {
                        DakilioIcon(DakilioSymbols.ArrowDropDown, contentDescription = null, modifier = Modifier.size(FilterChipDefaults.IconSize))
                    }
                },
                shape = CircleShape,
            )
        }
    }
}

@Composable
internal fun facetName(facet: Facet): String = stringResource(
    when (facet) {
        Facet.Mood -> R.string.search_facet_mood
        Facet.Tags -> R.string.search_facet_tags
        Facet.Diary -> R.string.search_facet_diary
        Facet.When -> R.string.search_facet_when
        Facet.Has -> R.string.search_facet_has
    },
)

/** The chip's leading icon: the best selected mood's face, or the category symbol. */
private fun facetIcon(facet: Facet, state: SearchUiState): (@Composable (Boolean) -> Unit)? {
    if (facet == Facet.Mood) {
        val mood = state.moods.lastOrNull { it.level in state.filters.moodLevels } ?: return null
        return { active -> MoodBadge(mood, size = 20.dp, active = active) }
    }
    val symbol: DakilioSymbol = when (facet) {
        Facet.Tags -> state.filters.tagIds.firstNotNullOfOrNull { state.tags[it] }?.let { symbolForKey(it.iconKey) } ?: DakilioSymbols.Sell
        Facet.Diary -> state.diaries.firstOrNull { it.id in state.filters.diaryIds }?.let { symbolForKey(it.iconKey) } ?: DakilioSymbols.Book2
        Facet.When -> DakilioSymbols.CalendarToday
        Facet.Has -> DakilioSymbols.FilterList
        Facet.Mood -> error("handled above")
    }
    return { active -> DakilioIcon(symbol, contentDescription = null, filled = active, modifier = Modifier.size(FilterChipDefaults.IconSize)) }
}

@Composable
private fun moreLabel(values: List<String>): String =
    if (values.size == 1) values.single() else stringResource(R.string.search_facet_more, values.first(), values.size - 1)

/** What [facet] is set to, as labels (best moods and earliest weekdays first); empty when unused. */
@Composable
internal fun facetValues(facet: Facet, state: SearchUiState, formats: DakilioFormats): List<String> {
    val filters = state.filters
    return when (facet) {
        Facet.Mood -> state.moods.asReversed().filter { it.level in filters.moodLevels }.map { it.label }
        Facet.Tags -> filters.tagIds.mapNotNull { state.tags[it]?.label }
        Facet.Diary -> state.diaries.filter { it.id in filters.diaryIds }.map { it.name }
        Facet.When -> listOfNotNull(filters.period?.let { periodLabel(it, formats) }) +
            filters.weekdays.sorted().map { formats.weekdayShort(MONDAY.plusDays(it - 1L)) }
        Facet.Has -> Has.entries.filter { it in filters.has }.map { hasLabel(it) }
    }
}

/** A one-line description of a whole search, for the recents list ("“walk” · Great · Work"). */
@Composable
internal fun searchSummary(filters: SearchFilters, state: SearchUiState, formats: DakilioFormats): String {
    val scoped = state.copy(filters = filters)
    val parts = listOfNotNull(filters.query.trim().takeIf { it.isNotEmpty() }?.let { "“$it”" }) +
        Facet.entries.flatMap { facetValues(it, scoped, formats) }
    return parts.joinToString(" · ")
}

@Composable
internal fun periodLabel(period: Period, formats: DakilioFormats): String = when (period) {
    Period.Last7Days -> stringResource(R.string.search_period_7d)
    Period.Last30Days -> stringResource(R.string.search_period_30d)
    Period.ThisYear -> stringResource(R.string.search_period_year)
    Period.OnThisDay -> stringResource(R.string.search_period_on_this_day)
    is Period.Custom -> stringResource(R.string.search_period_range, formats.dayMonthYear(period.from), formats.dayMonthYear(period.to))
}

@Composable
internal fun hasLabel(kind: Has): String = stringResource(
    when (kind) {
        Has.Photos -> R.string.search_has_photos
        Has.Text -> R.string.search_has_text
        Has.LongNote -> R.string.search_has_long
    },
)

/** A Monday, for turning ISO day numbers into dates the formatter can name. */
private val MONDAY: LocalDate = LocalDate.of(2024, 1, 1)
