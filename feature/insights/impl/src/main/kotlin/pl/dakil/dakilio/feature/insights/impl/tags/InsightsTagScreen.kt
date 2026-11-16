package pl.dakil.dakilio.feature.insights.impl.tags

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation3.runtime.NavKey
import java.time.DayOfWeek
import java.time.LocalDate
import pl.dakil.dakilio.core.designsystem.component.rememberHaptics
import pl.dakil.dakilio.core.designsystem.theme.Spacing
import pl.dakil.dakilio.core.model.MOOD_LEVELS
import pl.dakil.dakilio.core.model.Mood
import pl.dakil.dakilio.core.model.Tag
import pl.dakil.dakilio.core.ui.DakilioFormats
import pl.dakil.dakilio.core.ui.component.TagPill
import pl.dakil.dakilio.core.ui.mood.MoodBadge
import pl.dakil.dakilio.core.ui.rememberFormats
import pl.dakil.dakilio.core.ui.weekdaysFrom
import pl.dakil.dakilio.feature.insights.api.InsightsEntriesKey
import pl.dakil.dakilio.feature.insights.api.InsightsTagKey
import pl.dakil.dakilio.feature.insights.impl.InsightsDetailScaffold
import pl.dakil.dakilio.feature.insights.impl.R
import pl.dakil.dakilio.feature.insights.impl.chart.ChartColumn
import pl.dakil.dakilio.feature.insights.impl.chart.ColumnChart
import pl.dakil.dakilio.feature.insights.impl.chart.SingleBar
import pl.dakil.dakilio.feature.insights.impl.key
import pl.dakil.dakilio.feature.insights.impl.logic.TagCount
import pl.dakil.dakilio.feature.insights.impl.logic.TagDetail
import pl.dakil.dakilio.feature.insights.impl.logic.TagLink
import pl.dakil.dakilio.feature.insights.impl.logic.TagStat
import pl.dakil.dakilio.feature.insights.impl.logic.nearestMood
import pl.dakil.dakilio.feature.insights.impl.overview.CardNote
import pl.dakil.dakilio.feature.insights.impl.overview.CardTitle
import pl.dakil.dakilio.feature.insights.impl.overview.ChartCaption
import pl.dakil.dakilio.feature.insights.impl.overview.EmptyPeriodCard
import pl.dakil.dakilio.feature.insights.impl.overview.InsightCard
import pl.dakil.dakilio.feature.insights.impl.overview.MoodMixCard
import pl.dakil.dakilio.feature.insights.impl.overview.StatTile
import pl.dakil.dakilio.feature.insights.impl.overview.TagIcon
import pl.dakil.dakilio.feature.insights.impl.overview.axisLabel
import pl.dakil.dakilio.feature.insights.impl.overview.bucketLabel

/** One tag in a period: how often, when, with what, and how its days felt. */
@Composable
internal fun InsightsTagScreen(viewModel: InsightsTagViewModel, onBack: () -> Unit, onOpen: (NavKey) -> Unit) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val formats = rememberFormats()
    val ready = state as? InsightsTagUiState.Ready
    InsightsDetailScaffold(
        title = ready?.detail?.tag?.label ?: stringResource(R.string.insights_tag_title),
        subtitle = ready?.groupName,
        header = ready?.header,
        moods = ready?.moods.orEmpty(),
        firstDay = ready?.firstDay ?: DayOfWeek.MONDAY,
        viewModel = viewModel,
        onBack = onBack,
    ) {
        val current = ready ?: return@InsightsDetailScaffold
        val detail = current.detail
        val tag = detail.tag
        val stat = detail.stat
        if (tag == null || stat == null) {
            item(key = "empty") { EmptyPeriodCard(stringResource(R.string.insights_tag_unused), title = stringResource(R.string.insights_tag_unused_title)) }
            return@InsightsDetailScaffold
        }
        item(key = "summary") { TagSummaryCard(tag, stat, detail.journaledDays, current.today, formats) }
        item(key = "link") { TagMoodCard(tag, stat.link, current.moods, formats) }
        if (detail.use.size > 1) {
            item(key = "use") { TagUseCard(detail, current.today, formats, current.header.key) }
        }
        item(key = "weekdays") { TagWeekdaysCard(detail, current.firstDay, formats, current.header.key) }
        if (detail.together.isNotEmpty()) {
            item(key = "together") { TogetherCard(detail.together) { onOpen(InsightsTagKey(viewModel.scope, it)) } }
        }
        if (detail.mood.moodEntries > 0) {
            item(key = "mix") {
                MoodMixCard(detail.mood, onLevel = { onOpen(InsightsEntriesKey(viewModel.scope, moodLevel = it, tagId = viewModel.tagId)) })
            }
        }
        item(key = "entries") {
            FilledTonalButton(onClick = { onOpen(InsightsEntriesKey(viewModel.scope, tagId = viewModel.tagId)) }, modifier = Modifier.fillMaxWidth()) {
                Text(pluralStringResource(R.plurals.insights_see_entries, stat.entries, stat.entries))
            }
        }
    }
}

/** The tag and its three headline numbers: days (of the days journaled), entries, last use. */
@Composable
private fun TagSummaryCard(tag: Tag, stat: TagStat, journaledDays: Int, today: LocalDate, formats: DakilioFormats) {
    InsightCard {
        Row(verticalAlignment = Alignment.CenterVertically) {
            TagIcon(tag, size = 56.dp)
            Column(Modifier.padding(start = Spacing.l)) {
                Text(tag.label, style = MaterialTheme.typography.headlineSmallEmphasized, maxLines = 2, overflow = TextOverflow.Ellipsis)
                Text(
                    stringResource(R.string.insights_last_used, formats.fullDate(stat.lastUsed, today)),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        val tile = RoundedCornerShape(16.dp)
        val tileColor = MaterialTheme.colorScheme.surfaceContainerHigh
        Row(Modifier.padding(top = Spacing.l).height(IntrinsicSize.Min), horizontalArrangement = Arrangement.spacedBy(Spacing.xs)) {
            StatTile(
                stringResource(R.string.insights_tag_days),
                formats.count(stat.days),
                tile,
                Modifier.weight(1f),
                detail = stringResource(R.string.insights_of_days, journaledDays),
                color = tileColor,
            )
            StatTile(stringResource(R.string.insights_entries), formats.count(stat.entries), tile, Modifier.weight(1f), color = tileColor)
        }
    }
}

/**
 * The average mood of the days with the tag next to the other days: two bars on the 1–5 scale, each
 * with its face, its figure and how many days it's from.
 */
@Composable
private fun TagMoodCard(tag: Tag, link: TagLink?, moods: List<Mood>, formats: DakilioFormats) {
    InsightCard {
        CardTitle(stringResource(R.string.insights_tag_mood), stringResource(R.string.insights_links_body))
        if (link == null) {
            CardNote(stringResource(R.string.insights_links_need), Modifier.padding(top = Spacing.m))
            return@InsightCard
        }
        Column(Modifier.padding(top = Spacing.l), verticalArrangement = Arrangement.spacedBy(Spacing.m)) {
            MoodSideRow(stringResource(R.string.insights_tag_with, tag.label), link.averageWith, link.daysWith, moods, formats)
            MoodSideRow(stringResource(R.string.insights_tag_without), link.averageWithout, link.daysWithout, moods, formats)
        }
        ChartCaption(stringResource(R.string.insights_tag_difference, formats.signedDecimal(link.lift)), Modifier.padding(top = Spacing.m))
    }
}

@Composable
private fun MoodSideRow(label: String, average: Float, days: Int, moods: List<Mood>, formats: DakilioFormats) {
    val daysText = pluralStringResource(R.plurals.insights_days_count, days, days)
    val figure = formats.decimal(average)
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.fillMaxWidth().semantics(mergeDescendants = true) { contentDescription = "$label: $figure, $daysText" },
    ) {
        nearestMood(moods, average)?.let { MoodBadge(it, size = 36.dp) }
        Column(Modifier.padding(start = Spacing.m).weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(label, style = MaterialTheme.typography.labelLarge, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                Text(figure, style = MaterialTheme.typography.labelLargeEmphasized, modifier = Modifier.padding(start = Spacing.s))
            }
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = Spacing.xs)) {
                SingleBar((average - 1f) / (MOOD_LEVELS - 1), Modifier.weight(1f))
                Text(
                    daysText,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.End,
                    modifier = Modifier.padding(start = Spacing.s).widthIn(min = 48.dp),
                )
            }
        }
    }
}

/** The days with the tag, slice after slice. */
@Composable
private fun TagUseCard(detail: TagDetail, today: LocalDate, formats: DakilioFormats, periodKey: String) {
    val haptics = rememberHaptics()
    var selected by rememberSaveable(periodKey) { mutableStateOf<Int?>(null) }
    val max = detail.use.maxOf { it.days }.coerceAtLeast(1)
    InsightCard {
        CardTitle(stringResource(R.string.insights_tag_use))
        ColumnChart(
            columns = detail.use.map { ChartColumn(it.days.toFloat() / max) },
            description = stringResource(R.string.insights_tag_use),
            selected = selected,
            onSelect = { index ->
                if (index != selected) {
                    haptics.tick()
                    selected = index
                }
            },
            modifier = Modifier.padding(top = Spacing.l),
        )
        Row(Modifier.fillMaxWidth().padding(top = Spacing.xs), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(axisLabel(detail.use.first().range, detail.granularity, formats), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(axisLabel(detail.use.last().range, detail.granularity, formats), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        selected?.let(detail.use::getOrNull)?.let { use ->
            ChartCaption(
                stringResource(
                    R.string.insights_point,
                    bucketLabel(use.range, detail.granularity, today, formats),
                    pluralStringResource(R.plurals.insights_days_count, use.days, use.days),
                ),
                Modifier.padding(top = Spacing.m),
            )
        }
    }
}

/** Which weekdays the tag falls on, the most common standing out. */
@Composable
private fun TagWeekdaysCard(detail: TagDetail, firstDay: DayOfWeek, formats: DakilioFormats, periodKey: String) {
    val haptics = rememberHaptics()
    var selected by rememberSaveable(periodKey) { mutableStateOf<Int?>(null) }
    val weekdays = weekdaysFrom(firstDay)
    val max = detail.weekdays.values.max().coerceAtLeast(1)
    val top = detail.weekdays.entries.maxByOrNull { it.value }?.takeIf { best -> detail.weekdays.values.count { it == best.value } == 1 }?.key
    InsightCard {
        CardTitle(stringResource(R.string.insights_tag_weekdays))
        ColumnChart(
            columns = weekdays.map { ChartColumn(detail.weekdays.getValue(it).toFloat() / max, formats.weekdayShort(it)) },
            description = stringResource(R.string.insights_tag_weekdays),
            emphasized = top?.let(weekdays::indexOf),
            selected = selected,
            onSelect = { index ->
                if (index != selected) {
                    haptics.tick()
                    selected = index
                }
            },
            modifier = Modifier.padding(top = Spacing.l),
        )
        selected?.let(weekdays::getOrNull)?.let { day ->
            val days = detail.weekdays.getValue(day)
            ChartCaption(
                stringResource(R.string.insights_point, formats.weekday(day), pluralStringResource(R.plurals.insights_days_count, days, days)),
                Modifier.padding(top = Spacing.m),
            )
        }
    }
}

/** Tags that share entries with this one; each opens its own screen. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun TogetherCard(together: List<TagCount>, onTag: (Long) -> Unit) {
    InsightCard {
        CardTitle(stringResource(R.string.insights_tag_together))
        FlowRow(
            Modifier.padding(top = Spacing.m),
            horizontalArrangement = Arrangement.spacedBy(Spacing.s),
            verticalArrangement = Arrangement.spacedBy(Spacing.s),
        ) {
            together.forEach { item ->
                val entries = pluralStringResource(R.plurals.insights_heat_entries, item.count, item.count)
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .clip(CircleShape)
                        .clickable { onTag(item.tag.id) }
                        .semantics(mergeDescendants = true) { contentDescription = "${item.tag.label}, $entries" },
                ) {
                    TagPill(item.tag)
                    Text(
                        item.count.toString(),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(start = Spacing.xs, end = Spacing.s),
                    )
                }
            }
        }
    }
}
