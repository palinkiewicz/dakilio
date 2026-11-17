package pl.dakil.dakilio.feature.insights.impl.habits

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import java.time.DayOfWeek
import java.time.LocalDate
import pl.dakil.dakilio.core.designsystem.icon.DakilioIcon
import pl.dakil.dakilio.core.designsystem.icon.DakilioSymbols
import pl.dakil.dakilio.core.designsystem.theme.Spacing
import pl.dakil.dakilio.core.model.DateRange
import pl.dakil.dakilio.core.model.HabitWeek
import pl.dakil.dakilio.core.model.Habits
import pl.dakil.dakilio.core.model.Tag
import pl.dakil.dakilio.core.ui.color.tagColors
import pl.dakil.dakilio.feature.insights.impl.R
import pl.dakil.dakilio.feature.insights.impl.chart.WeekPills
import pl.dakil.dakilio.feature.insights.impl.logic.HabitStat
import pl.dakil.dakilio.feature.insights.impl.overview.CardTitle
import pl.dakil.dakilio.feature.insights.impl.overview.InsightCard
import pl.dakil.dakilio.feature.insights.impl.overview.TagIcon
import pl.dakil.dakilio.feature.insights.impl.overview.cardRowClick

/** A habit's own color: its tag's, else the theme's accent. */
@Composable
internal fun habitColor(tag: Tag): Color = tagColors(tag.color)?.container ?: MaterialTheme.colorScheme.primary

/** The week starts of [range], in weeks from [firstDay]. */
internal fun weekStarts(range: DateRange, firstDay: DayOfWeek): List<LocalDate> =
    generateSequence(Habits.weekStart(range.from, firstDay)) { it.plusWeeks(1) }.takeWhile { !it.isAfter(range.to) }.toList()

/** [stat]'s weeks laid on the period's weeks, `null` where the habit hadn't started. */
internal fun alignedWeeks(stat: HabitStat, starts: List<LocalDate>): List<HabitWeek?> {
    val byStart = stat.weeks.associateBy { it.start }
    return starts.map { byStart[it] }
}

/**
 * "5 of 8 weeks on target". A period of one week counts days instead ("2 of 3 days so far").
 */
@Composable
internal fun onTargetLabel(stat: HabitStat): String {
    val single = stat.weeks.singleOrNull()
    return when {
        single != null -> pluralStringResource(
            if (single.inProgress) R.plurals.insights_habit_days_so_far else R.plurals.insights_habit_days_of,
            single.target,
            single.days,
            single.target,
        )
        else -> pluralStringResource(R.plurals.insights_habit_on_target, stat.counted, stat.met, stat.counted)
    }
}

/**
 * How the comparison period went: its weeks on target, or for a period of one week, whether that week
 * met the target.
 */
@Composable
internal fun habitBeforeNote(stat: HabitStat, met: Int): String = if (stat.weeks.size <= 1) {
    stringResource(if (met > 0) R.string.insights_habit_before_met else R.string.insights_habit_before_missed)
} else {
    pluralStringResource(R.plurals.insights_habit_before, met, met)
}

/** The run of weeks on target as a small flame badge; nothing under two weeks. */
@Composable
internal fun RunBadge(weeks: Int, modifier: Modifier = Modifier) {
    if (weeks < 2) return
    Surface(shape = CircleShape, color = MaterialTheme.colorScheme.tertiaryContainer, contentColor = MaterialTheme.colorScheme.onTertiaryContainer, modifier = modifier) {
        Row(Modifier.padding(start = Spacing.s, end = Spacing.m, top = Spacing.xxs, bottom = Spacing.xxs), verticalAlignment = Alignment.CenterVertically) {
            DakilioIcon(DakilioSymbols.LocalFireDepartment, contentDescription = null, filled = true, modifier = Modifier.size(16.dp))
            Text(weeks.toString(), style = MaterialTheme.typography.labelLargeEmphasized, modifier = Modifier.padding(start = Spacing.xxs))
        }
    }
}

/** Weeks shown per habit on the overview: enough to read a trend at a glance. */
private const val OVERVIEW_WEEKS = 10

/** The habits at a glance: per habit, how many weeks were on target and the last weeks as pills. */
@Composable
internal fun HabitsCard(stats: List<HabitStat>, range: DateRange, firstDay: DayOfWeek, onMore: () -> Unit, onHabit: (Long) -> Unit, allDiaries: Boolean) {
    val starts = weekStarts(range, firstDay).takeLast(OVERVIEW_WEEKS)
    InsightCard {
        CardTitle(
            stringResource(R.string.insights_habits),
            if (allDiaries) null else stringResource(R.string.insights_habits_all_diaries),
            onMore = onMore,
        )
        Column(Modifier.padding(top = Spacing.m), verticalArrangement = Arrangement.spacedBy(Spacing.s)) {
            stats.forEach { stat -> HabitRow(stat, starts) { onHabit(stat.habit.tag.id) } }
        }
    }
}

/** A habit's name, its weeks on target and a row of week pills; a tap opens its tag. */
@Composable
internal fun HabitRow(stat: HabitStat, starts: List<LocalDate>, onClick: (() -> Unit)?) {
    val label = onTargetLabel(stat)
    val target = pluralStringResource(R.plurals.insights_habit_target, stat.habit.daysPerWeek, stat.habit.daysPerWeek)
    Column(
        Modifier
            .fillMaxWidth()
            .cardRowClick(onClick)
            .semantics(mergeDescendants = true) { contentDescription = "${stat.habit.tag.label}, $target, $label" },
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            TagIcon(stat.habit.tag, size = 32.dp)
            Column(Modifier.padding(start = Spacing.m).weight(1f)) {
                Text(stat.habit.tag.label, style = MaterialTheme.typography.labelLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(label, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            RunBadge(stat.currentRun, Modifier.padding(start = Spacing.s))
        }
        // One week is said in days already; a lone pill adds nothing.
        if (starts.size > 1) {
            WeekPills(alignedWeeks(stat, starts), habitColor(stat.habit.tag), Modifier.padding(top = Spacing.s, start = 32.dp + Spacing.m))
        }
    }
}
