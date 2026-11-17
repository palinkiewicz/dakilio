package pl.dakil.dakilio.feature.insights.impl.habits

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation3.runtime.NavKey
import java.time.DayOfWeek
import java.time.LocalDate
import pl.dakil.dakilio.feature.insights.impl.chart.WeekPills
import pl.dakil.dakilio.core.designsystem.theme.Spacing
import pl.dakil.dakilio.core.model.Mood
import pl.dakil.dakilio.core.ui.DakilioFormats
import pl.dakil.dakilio.core.ui.rememberFormats
import pl.dakil.dakilio.feature.habits.api.HabitsKey
import pl.dakil.dakilio.feature.insights.api.InsightsTagKey
import pl.dakil.dakilio.feature.insights.impl.InsightsDetailScaffold
import pl.dakil.dakilio.feature.insights.impl.R
import pl.dakil.dakilio.feature.insights.impl.logic.HabitStat
import pl.dakil.dakilio.feature.insights.impl.overview.CardNote
import pl.dakil.dakilio.feature.insights.impl.overview.EmptyPeriodCard
import pl.dakil.dakilio.feature.insights.impl.overview.InsightCard
import pl.dakil.dakilio.feature.insights.impl.overview.StatTile
import pl.dakil.dakilio.feature.insights.impl.overview.TagIcon
import pl.dakil.dakilio.feature.insights.impl.overview.cardRowClick
import pl.dakil.dakilio.feature.insights.impl.overview.moodValueLabel

/** The habits in a period: all of them week by week, then each habit's own numbers. */
@Composable
internal fun InsightsHabitsScreen(viewModel: InsightsHabitsViewModel, onBack: () -> Unit, onOpen: (NavKey) -> Unit) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val formats = rememberFormats()
    val ready = state as? InsightsHabitsUiState.Ready
    InsightsDetailScaffold(
        title = stringResource(R.string.insights_habits),
        subtitle = stringResource(R.string.insights_habits_all_diaries),
        header = ready?.header,
        moods = ready?.moods.orEmpty(),
        firstDay = ready?.firstDay ?: DayOfWeek.MONDAY,
        viewModel = viewModel,
        onBack = onBack,
    ) {
        val current = ready ?: return@InsightsDetailScaffold
        if (current.stats.isEmpty()) {
            item(key = "empty") {
                EmptyPeriodCard(
                    stringResource(if (current.hasHabits) R.string.insights_habits_not_begun else R.string.insights_habits_none),
                    title = stringResource(R.string.insights_habits_empty_title),
                )
            }
        } else {
            current.stats.forEach { stat ->
                item(key = "habit-${stat.habit.id}") {
                    HabitDetailCard(
                        stat = stat,
                        hasComparison = current.hasComparison,
                        moods = current.moods,
                        formats = formats,
                        weeks = weekStarts(current.range, current.firstDay),
                        onClick = { onOpen(InsightsTagKey(viewModel.scope, stat.habit.tag.id)) },
                    )
                }
            }
        }
        item(key = "manage") {
            FilledTonalButton(onClick = { onOpen(HabitsKey) }, modifier = Modifier.fillMaxWidth()) {
                Text(stringResource(R.string.insights_habits_manage))
            }
        }
    }
}

/** One habit's numbers: weeks on target, runs and days, and how its weeks felt. A tap opens its tag. */
@Composable
internal fun HabitDetailCard(
    stat: HabitStat,
    hasComparison: Boolean,
    moods: List<Mood>,
    formats: DakilioFormats,
    weeks: List<LocalDate>,
    onClick: (() -> Unit)?,
) {
    InsightCard {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth().cardRowClick(onClick)) {
            TagIcon(stat.habit.tag, size = 44.dp)
            Column(Modifier.padding(start = Spacing.m).weight(1f)) {
                Text(stat.habit.tag.label, style = MaterialTheme.typography.titleMediumEmphasized, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(
                    pluralStringResource(R.plurals.insights_habit_target, stat.habit.daysPerWeek, stat.habit.daysPerWeek),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            RunBadge(stat.currentRun)
        }
        if (weeks.size > 1) {
            WeekPills(alignedWeeks(stat, weeks), habitColor(stat.habit.tag), Modifier.padding(top = Spacing.l))
            Row(Modifier.fillMaxWidth().padding(top = Spacing.xs), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(formats.dayMonthShort(weeks.first()), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(onTargetLabel(stat), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        HabitTiles(stat, formats, Modifier.padding(top = Spacing.m))
        val notes = listOfNotNull(
            stat.metBefore?.takeIf { hasComparison }?.let { habitBeforeNote(stat, it) },
            if (stat.moodOnTarget != null && stat.moodOffTarget != null) {
                stringResource(R.string.insights_habit_mood, moodValueLabel(stat.moodOnTarget, moods, formats), moodValueLabel(stat.moodOffTarget, moods, formats))
            } else {
                null
            },
        )
        if (notes.isNotEmpty()) {
            Column(Modifier.padding(top = Spacing.m), verticalArrangement = Arrangement.spacedBy(Spacing.xxs)) { notes.forEach { CardNote(it) } }
        }
    }
}

/**
 * Weeks on target, the longest run and the days, as tiles. A period of one week has no weeks to count,
 * so it shows only its days against the target.
 */
@Composable
internal fun HabitTiles(stat: HabitStat, formats: DakilioFormats, modifier: Modifier = Modifier) {
    val tile = RoundedCornerShape(16.dp)
    val color = MaterialTheme.colorScheme.surfaceContainerHigh
    stat.weeks.singleOrNull()?.let { week ->
        StatTile(
            stringResource(R.string.insights_tag_days),
            formats.count(week.days),
            tile,
            modifier.fillMaxWidth(),
            detail = stringResource(R.string.insights_of_days, week.target),
            color = color,
        )
        return
    }
    Row(modifier.height(IntrinsicSize.Min), horizontalArrangement = Arrangement.spacedBy(Spacing.xs)) {
        StatTile(
            stringResource(R.string.insights_habit_weeks_on_target),
            formats.count(stat.met),
            tile,
            Modifier.weight(1f),
            detail = stringResource(R.string.insights_of_days, stat.counted),
            color = color,
        )
        StatTile(
            stringResource(R.string.insights_habit_best_run),
            formats.count(stat.longestRun),
            tile,
            Modifier.weight(1f),
            detail = pluralStringResource(R.plurals.insights_weeks_unit, stat.longestRun),
            color = color,
        )
        StatTile(stringResource(R.string.insights_tag_days), formats.count(stat.days), tile, Modifier.weight(1f), color = color)
    }
}
