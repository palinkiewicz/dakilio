package pl.dakil.dakilio.feature.insights.impl.overview

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.unit.dp
import pl.dakil.dakilio.core.designsystem.component.RollingNumber
import pl.dakil.dakilio.core.designsystem.icon.DakilioIcon
import pl.dakil.dakilio.core.designsystem.icon.DakilioSymbols
import pl.dakil.dakilio.core.designsystem.theme.Spacing
import pl.dakil.dakilio.core.model.Mood
import pl.dakil.dakilio.core.ui.DakilioFormats
import pl.dakil.dakilio.core.ui.mood.AverageMoodFace
import pl.dakil.dakilio.core.ui.mood.NoMoodBadge
import pl.dakil.dakilio.feature.insights.impl.ComparisonPicker
import pl.dakil.dakilio.feature.insights.impl.PeriodHeaderState
import pl.dakil.dakilio.feature.insights.impl.R
import pl.dakil.dakilio.feature.insights.impl.logic.Comparison
import java.time.DayOfWeek
import pl.dakil.dakilio.feature.insights.impl.logic.Overview
import pl.dakil.dakilio.feature.insights.impl.logic.Steadiness
import pl.dakil.dakilio.feature.insights.impl.logic.nearestMood

/**
 * The period at a glance: its average as a large face that morphs as the period changes, the change
 * against the comparison period, and the four headline numbers.
 *
 * @param onComparison changes what the period is measured against, from the change chip.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun SummaryCard(
    overview: Overview,
    moods: List<Mood>,
    formats: DakilioFormats,
    header: PeriodHeaderState,
    firstDay: DayOfWeek,
    onComparison: (Comparison) -> Unit,
) {
    val colors = MaterialTheme.colorScheme
    val average = overview.mood.average
    val mood = average?.let { nearestMood(moods, it) }
    Column(verticalArrangement = Arrangement.spacedBy(Spacing.xxs)) {
        Surface(
            shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp, bottomStart = 6.dp, bottomEnd = 6.dp),
            color = colors.primaryContainer,
            contentColor = colors.onPrimaryContainer,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Column(Modifier.padding(Spacing.xl)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (average == null || mood == null) {
                        NoMoodBadge(size = 72.dp)
                        Text(
                            stringResource(R.string.insights_no_mood),
                            style = MaterialTheme.typography.bodyLarge,
                            modifier = Modifier.padding(start = Spacing.l),
                        )
                        return@Row
                    }
                    AverageMoodFace(mood, size = 96.dp)
                    Column(Modifier.padding(start = Spacing.l)) {
                        Text(stringResource(R.string.insights_mostly, mood.label), style = MaterialTheme.typography.titleMedium)
                        val averageText = formats.decimal(average)
                        val averageDescription = stringResource(R.string.insights_average_cd, averageText)
                        FlowRow(
                            horizontalArrangement = Arrangement.spacedBy(Spacing.s),
                            verticalArrangement = Arrangement.spacedBy(Spacing.xs),
                            itemVerticalAlignment = Alignment.CenterVertically,
                        ) {
                            RollingNumber(
                                averageText,
                                style = MaterialTheme.typography.displaySmallEmphasized,
                                modifier = Modifier.clearAndSetSemantics { contentDescription = averageDescription },
                            )
                            ComparisonChangeChip(overview.moodChange, header, moods, firstDay, formats, onComparison)
                            overview.steadiness?.let { steadiness ->
                                Pill(
                                    when (steadiness) {
                                        Steadiness.Steady -> stringResource(R.string.insights_steady)
                                        Steadiness.Varied -> stringResource(R.string.insights_varied)
                                        Steadiness.UpAndDown -> stringResource(R.string.insights_up_and_down)
                                    },
                                )
                            }
                        }
                    }
                }
            }
        }
        val square = RoundedCornerShape(6.dp)
        Row(Modifier.height(IntrinsicSize.Min), horizontalArrangement = Arrangement.spacedBy(Spacing.xxs)) {
            StatTile(stringResource(R.string.insights_entries), formats.count(overview.entries), square, Modifier.weight(1f))
            StatTile(
                stringResource(R.string.insights_days_journaled),
                formats.count(overview.daysJournaled),
                square,
                Modifier.weight(1f),
                detail = stringResource(R.string.insights_of_days, overview.range.days),
            )
        }
        Row(Modifier.height(IntrinsicSize.Min), horizontalArrangement = Arrangement.spacedBy(Spacing.xxs)) {
            StatTile(
                stringResource(if (overview.streakIsCurrent) R.string.insights_current_streak else R.string.insights_best_streak),
                formats.count(overview.streak),
                RoundedCornerShape(6.dp, 6.dp, 6.dp, 28.dp),
                Modifier.weight(1f),
            )
            StatTile(
                stringResource(R.string.insights_words),
                formats.count(overview.writing.words),
                RoundedCornerShape(6.dp, 6.dp, 28.dp, 6.dp),
                Modifier.weight(1f),
            )
        }
    }
}

/**
 * The change in levels against the comparison period, which is also how the comparison is changed:
 * a tap opens its choices. "+0.3", "No change", "–" when the other period has no mood, or "Compare"
 * without a comparison. All time has none, so it shows nothing.
 */
@Composable
private fun ComparisonChangeChip(
    change: Float?,
    header: PeriodHeaderState,
    moods: List<Mood>,
    firstDay: DayOfWeek,
    formats: DakilioFormats,
    onComparison: (Comparison) -> Unit,
) {
    val tenths = change?.let { Math.round(it * 10) }
    val compared = header.comparison != Comparison.None
    val text = when {
        !compared -> stringResource(R.string.insights_compare_short)
        tenths == null -> "–"
        tenths == 0 -> stringResource(R.string.insights_change_same_cd)
        else -> formats.signedDecimal(change!!)
    }
    val spoken = when {
        !compared -> null
        tenths == null -> stringResource(R.string.insights_change_none_cd)
        tenths > 0 -> stringResource(R.string.insights_change_up_cd, formats.decimal(tenths / 10f))
        tenths < 0 -> stringResource(R.string.insights_change_down_cd, formats.decimal(-tenths / 10f))
        else -> stringResource(R.string.insights_change_same_cd)
    }
    val against = comparisonLabel(header, comparisonRangeOf(header), formats)
    val description = when {
        spoken == null -> stringResource(R.string.insights_compare)
        against == null -> spoken
        else -> stringResource(R.string.insights_change_against_cd, spoken, against)
    }
    ComparisonPicker(header, moods, firstDay, formats, onComparison) { open ->
        Surface(
            onClick = open,
            shape = CircleShape,
            color = MaterialTheme.colorScheme.surface,
            contentColor = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.clearAndSetSemantics {
                contentDescription = description
                role = Role.Button
            },
        ) {
            Row(
                Modifier.padding(start = Spacing.s, end = Spacing.xs, top = Spacing.xs, bottom = Spacing.xs),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                DakilioIcon(DakilioSymbols.CompareArrows, contentDescription = null, modifier = Modifier.size(18.dp))
                Text(text, style = MaterialTheme.typography.labelLargeEmphasized, modifier = Modifier.padding(start = Spacing.xs))
                DakilioIcon(DakilioSymbols.ChevronRight, contentDescription = null, modifier = Modifier.size(18.dp))
            }
        }
    }
}

@Composable
private fun Pill(text: String) {
    Surface(shape = CircleShape, color = MaterialTheme.colorScheme.surface, contentColor = MaterialTheme.colorScheme.onSurface) {
        Text(text, style = MaterialTheme.typography.labelLarge, modifier = Modifier.padding(horizontal = Spacing.m, vertical = Spacing.xs))
    }
}
