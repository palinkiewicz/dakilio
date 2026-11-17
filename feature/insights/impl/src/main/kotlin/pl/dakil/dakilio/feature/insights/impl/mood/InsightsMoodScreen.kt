package pl.dakil.dakilio.feature.insights.impl.mood

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import pl.dakil.dakilio.feature.insights.impl.logic.InsightsPeriod
import pl.dakil.dakilio.feature.insights.impl.overview.comparisonRangeOf
import pl.dakil.dakilio.feature.insights.impl.overview.comparisonLabel
import pl.dakil.dakilio.feature.insights.impl.ComparisonChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.foundation.layout.Spacer
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
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
import pl.dakil.dakilio.core.designsystem.component.ConnectedToggleRow
import pl.dakil.dakilio.core.designsystem.component.ToggleOption
import pl.dakil.dakilio.core.designsystem.component.rememberHaptics
import pl.dakil.dakilio.core.designsystem.theme.Spacing
import pl.dakil.dakilio.core.model.Mood
import pl.dakil.dakilio.core.ui.DakilioFormats
import pl.dakil.dakilio.core.ui.mood.MoodBadge
import pl.dakil.dakilio.core.ui.mood.moodColors
import pl.dakil.dakilio.core.ui.rememberFormats
import pl.dakil.dakilio.feature.entry.api.EntryEditorKey
import pl.dakil.dakilio.feature.insights.api.InsightsEntriesKey
import pl.dakil.dakilio.feature.insights.impl.DaySheet
import pl.dakil.dakilio.feature.insights.impl.InsightsDetailScaffold
import pl.dakil.dakilio.feature.insights.impl.R
import pl.dakil.dakilio.feature.insights.impl.chart.SingleBar
import pl.dakil.dakilio.feature.insights.impl.chart.StackedColumns
import pl.dakil.dakilio.feature.insights.impl.key
import pl.dakil.dakilio.feature.insights.impl.logic.Granularity
import pl.dakil.dakilio.feature.insights.impl.logic.MoodCount
import pl.dakil.dakilio.feature.insights.impl.logic.MoodDay
import pl.dakil.dakilio.feature.insights.impl.logic.MoodFlow
import pl.dakil.dakilio.feature.insights.impl.logic.Steadiness
import pl.dakil.dakilio.feature.insights.impl.logic.nearestMood
import pl.dakil.dakilio.feature.insights.impl.overview.CardNote
import pl.dakil.dakilio.feature.insights.impl.overview.CardTitle
import pl.dakil.dakilio.feature.insights.impl.overview.ChartCaption
import pl.dakil.dakilio.feature.insights.impl.overview.EmptyPeriodCard
import pl.dakil.dakilio.feature.insights.impl.overview.InsightCard
import pl.dakil.dakilio.feature.insights.impl.overview.MoodFlowCard
import pl.dakil.dakilio.feature.insights.impl.overview.MoodLegend
import pl.dakil.dakilio.feature.insights.impl.overview.MoodMixCard
import pl.dakil.dakilio.feature.insights.impl.overview.axisLabel
import pl.dakil.dakilio.feature.insights.impl.overview.bucketLabel
import pl.dakil.dakilio.feature.insights.impl.overview.cardRowClick
import pl.dakil.dakilio.feature.insights.impl.overview.moodValueLabel

/** Mood in depth for one period. */
@Composable
internal fun InsightsMoodScreen(viewModel: InsightsMoodViewModel, onBack: () -> Unit, onOpenPost: (Long) -> Unit, onOpen: (NavKey) -> Unit) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val formats = rememberFormats()
    val ready = state as? InsightsMoodUiState.Ready
    var openDay by rememberSaveable { mutableStateOf<Long?>(null) }
    val compareLabel = ready?.let { comparisonLabel(it.header, comparisonRangeOf(it.header), formats) }
    InsightsDetailScaffold(
        title = stringResource(R.string.insights_mood_title),
        header = ready?.header,
        moods = ready?.moods.orEmpty(),
        firstDay = ready?.firstDay ?: DayOfWeek.MONDAY,
        viewModel = viewModel,
        onBack = onBack,
    ) {
        val current = ready ?: return@InsightsDetailScaffold
        if (current.summary.moodEntries == 0) {
            item(key = "empty") { EmptyPeriodCard(stringResource(R.string.insights_no_mood)) }
            return@InsightsDetailScaffold
        }
        if (current.granularities.size > 1) {
            item(key = "slices") { GranularityToggle(current.granularities, current.flow.granularity, viewModel::setGranularity) }
        }
        if (current.flow.points >= 2) {
            item(key = "flow") {
                MoodFlowCard(
                    flow = current.flow,
                    moods = current.moods,
                    today = current.today,
                    formats = formats,
                    periodKey = current.header.key,
                    comparison = current.comparisonFlow,
                    comparisonLabel = compareLabel,
                )
            }
        }
        if (current.header.period != InsightsPeriod.AllTime) {
            item(key = "compare") {
                NowThenCard(current.now, current.then, compareLabel, current.moods, formats) {
                    ComparisonChip(current.header, current.moods, current.firstDay, formats, viewModel::setComparison)
                }
            }
        }
        item(key = "mix") { MoodMixCard(current.summary, onLevel = { onOpen(InsightsEntriesKey(viewModel.scope, moodLevel = it)) }) }
        if (current.flow.buckets.size > 1) {
            item(key = "mix-time") { MixOverTimeCard(current.flow, current.moods, current.today, formats, current.header.key) }
        }
        current.steadiness?.let { steadiness -> item(key = "steadiness") { SteadinessCard(steadiness) } }
        item(key = "names") { MoodNamesCard(current.names) }
        if (current.brightest.isNotEmpty()) {
            item(key = "brightest") {
                StandoutDaysCard(stringResource(R.string.insights_brightest), current.brightest, current.moods, current.today, formats) { openDay = it.toEpochDay() }
            }
            item(key = "toughest") {
                StandoutDaysCard(stringResource(R.string.insights_toughest), current.toughest, current.moods, current.today, formats) { openDay = it.toEpochDay() }
            }
        }
    }
    val current = ready
    val date = openDay?.let(LocalDate::ofEpochDay)
    if (current != null && date != null) {
        DaySheet(
            date = date,
            posts = remember(date, current) { viewModel.postsOn(date) },
            moods = current.moods,
            diaries = current.diaries,
            today = current.today,
            formats = formats,
            onOpenPost = onOpenPost,
            onAddEntry = { onOpen(EntryEditorKey(presetDiaryId = current.selectedDiaryId, presetEpochDay = it.toEpochDay())) },
            onDismiss = { openDay = null },
        )
    }
}

@Composable
internal fun granularityLabel(granularity: Granularity): String = stringResource(
    when (granularity) {
        Granularity.Days -> R.string.insights_by_days
        Granularity.Weeks -> R.string.insights_by_weeks
        Granularity.Months -> R.string.insights_by_months
        Granularity.Years -> R.string.insights_by_years
    },
)

/** Days · Weeks · Months: how finely the period's charts are sliced. */
@Composable
internal fun GranularityToggle(options: List<Granularity>, selected: Granularity, onSelect: (Granularity) -> Unit) {
    // `map` is inline, so each label can be composed inside it.
    ConnectedToggleRow(options = options.map { ToggleOption(it, granularityLabel(it)) }, selected = selected, onSelect = onSelect)
}

/** The mood mix slice after slice: every column a whole, split by level. */
@Composable
private fun MixOverTimeCard(flow: MoodFlow, moods: List<Mood>, today: LocalDate, formats: DakilioFormats, periodKey: String) {
    val haptics = rememberHaptics()
    var selected by rememberSaveable(periodKey, flow.granularity) { mutableStateOf<Int?>(null) }
    val first = flow.buckets.first()
    val last = flow.buckets.last()
    InsightCard {
        CardTitle(stringResource(R.string.insights_mix_over_time))
        StackedColumns(
            columns = flow.buckets.map { it.levelCounts },
            colors = moods.sortedBy { it.level }.map { moodColors(it).container },
            description = stringResource(R.string.insights_mix_cd),
            selected = selected,
            onSelect = { index ->
                if (index != selected) {
                    haptics.tick()
                    selected = index
                }
            },
            startLabel = axisLabel(first.range, flow.granularity, formats),
            endLabel = axisLabel(last.range, flow.granularity, formats),
            modifier = Modifier.padding(top = Spacing.l),
        )
        selected?.let(flow.buckets::getOrNull)?.let { bucket ->
            ChartCaption(
                stringResource(
                    R.string.insights_point,
                    bucketLabel(bucket.range, flow.granularity, today, formats),
                    pluralStringResource(R.plurals.insights_moods_logged, bucket.moodEntries, bucket.moodEntries),
                ),
                Modifier.padding(top = Spacing.m),
            )
        }
        MoodLegend(moods, Modifier.padding(top = Spacing.m), withNoEntries = false)
    }
}

/** How much the mood moved between days, as a word and a sentence. */
@Composable
private fun SteadinessCard(steadiness: Steadiness) {
    InsightCard {
        CardTitle(stringResource(R.string.insights_steadiness))
        Text(
            stringResource(
                when (steadiness) {
                    Steadiness.Steady -> R.string.insights_steady
                    Steadiness.Varied -> R.string.insights_varied
                    Steadiness.UpAndDown -> R.string.insights_up_and_down
                },
            ),
            style = MaterialTheme.typography.headlineSmallEmphasized,
            modifier = Modifier.padding(top = Spacing.s),
        )
        CardNote(
            stringResource(
                when (steadiness) {
                    Steadiness.Steady -> R.string.insights_steady_body
                    Steadiness.Varied -> R.string.insights_varied_body
                    Steadiness.UpAndDown -> R.string.insights_up_and_down_body
                },
            ),
            Modifier.padding(top = Spacing.xs),
        )
    }
}

/** The exact moods picked, aliases apart: which names were reached for within the levels. */
@Composable
private fun MoodNamesCard(names: List<MoodCount>) {
    val max = names.maxOf { it.count }.toFloat()
    InsightCard {
        CardTitle(stringResource(R.string.insights_mood_names))
        Column(Modifier.padding(top = Spacing.l), verticalArrangement = Arrangement.spacedBy(Spacing.m)) {
            names.forEach { item ->
                val entries = pluralStringResource(R.plurals.insights_heat_entries, item.count, item.count)
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth().semantics(mergeDescendants = true) { contentDescription = "${item.mood.label}, $entries" },
                ) {
                    MoodBadge(item.mood, size = 36.dp)
                    Column(Modifier.padding(start = Spacing.m).weight(1f)) {
                        Text(item.mood.label, style = MaterialTheme.typography.labelLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = Spacing.xs)) {
                            SingleBar(item.count / max, Modifier.weight(1f))
                            Text(
                                item.count.toString(),
                                style = MaterialTheme.typography.labelLarge,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                textAlign = TextAlign.End,
                                modifier = Modifier.padding(start = Spacing.s).widthIn(min = 24.dp),
                            )
                        }
                    }
                }
            }
        }
    }
}

/** A few standout days; each opens its day. */
@Composable
private fun StandoutDaysCard(title: String, days: List<MoodDay>, moods: List<Mood>, today: LocalDate, formats: DakilioFormats, onDay: (LocalDate) -> Unit) {
    InsightCard {
        CardTitle(title)
        Column(Modifier.padding(top = Spacing.m), verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
            days.forEach { day ->
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth().cardRowClick { onDay(day.date) }) {
                    nearestMood(moods, day.mood)?.let { MoodBadge(it, size = 36.dp) }
                    Column(Modifier.padding(start = Spacing.m).weight(1f)) {
                        Text(formats.fullDate(day.date, today), style = MaterialTheme.typography.labelLarge)
                        Text(
                            stringResource(
                                R.string.insights_point,
                                moodValueLabel(day.mood, moods, formats),
                                pluralStringResource(R.plurals.insights_heat_entries, day.entries, day.entries),
                            ),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        }
    }
}

/**
 * This period's numbers next to the comparison period's, with the chip that picks the comparison
 * on top. Without one, only the chip shows.
 */
@Composable
private fun NowThenCard(now: PeriodNumbers, then: PeriodNumbers?, thenLabel: String?, moods: List<Mood>, formats: DakilioFormats, chip: @Composable () -> Unit) {
    InsightCard {
        chip()
        if (then == null || thenLabel == null) return@InsightCard
        Row(Modifier.fillMaxWidth().padding(top = Spacing.l)) {
            Spacer(Modifier.weight(1.3f))
            Text(stringResource(R.string.insights_now), style = MaterialTheme.typography.labelLarge, textAlign = TextAlign.End, modifier = Modifier.weight(1f))
            Text(stringResource(R.string.insights_then), style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.End, modifier = Modifier.weight(1f))
        }
        HorizontalDivider(Modifier.padding(vertical = Spacing.s), color = MaterialTheme.colorScheme.outlineVariant)
        NowThenRow(stringResource(R.string.insights_average_mood), now.average?.let(formats::decimal) ?: "–", then.average?.let(formats::decimal) ?: "–") {
            now.average?.let { nearestMood(moods, it) }?.let { MoodBadge(it, size = 20.dp) }
        }
        NowThenRow(stringResource(R.string.insights_entries), formats.count(now.entries), formats.count(then.entries))
        NowThenRow(stringResource(R.string.insights_days_journaled), formats.count(now.days), formats.count(then.days))
    }
}

@Composable
private fun NowThenRow(label: String, now: String, then: String, badge: (@Composable () -> Unit)? = null) {
    Row(
        Modifier.fillMaxWidth().padding(vertical = Spacing.xs).semantics(mergeDescendants = true) { },
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1.3f))
        Row(Modifier.weight(1f), horizontalArrangement = Arrangement.End, verticalAlignment = Alignment.CenterVertically) {
            badge?.let {
                it()
                Spacer(Modifier.width(Spacing.xs))
            }
            Text(now, style = MaterialTheme.typography.titleMedium)
        }
        Text(then, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.End, modifier = Modifier.weight(1f))
    }
}
