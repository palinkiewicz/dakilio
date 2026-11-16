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
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.unit.dp
import pl.dakil.dakilio.core.designsystem.component.RollingNumber
import pl.dakil.dakilio.core.designsystem.icon.DakilioIcon
import pl.dakil.dakilio.core.designsystem.icon.DakilioSymbols
import pl.dakil.dakilio.core.designsystem.theme.Spacing
import pl.dakil.dakilio.core.model.Mood
import pl.dakil.dakilio.core.ui.DakilioFormats
import pl.dakil.dakilio.core.ui.mood.AverageMoodFace
import pl.dakil.dakilio.core.ui.mood.NoMoodBadge
import pl.dakil.dakilio.feature.insights.impl.ComparisonChip
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
 * @param onComparison changes what the period is measured against, from the chip at the card's foot.
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
                            // A change that rounds to nothing says nothing.
                            overview.moodChange?.takeIf { Math.round(it * 10) != 0 }?.let { ChangeChip(it, formats) }
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
                ComparisonChip(
                    header = header,
                    moods = moods,
                    firstDay = firstDay,
                    formats = formats,
                    onComparison = onComparison,
                    containerColor = colors.surface,
                    contentColor = colors.onSurface,
                    modifier = Modifier.padding(top = Spacing.l),
                )
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

/** A change in levels against the comparison period: an arrow and the signed figure. */
@Composable
internal fun ChangeChip(change: Float, formats: DakilioFormats, modifier: Modifier = Modifier) {
    val text = formats.signedDecimal(change)
    val tenths = Math.round(change * 10)
    val description = when {
        tenths > 0 -> stringResource(R.string.insights_change_up_cd, formats.decimal(tenths / 10f))
        tenths < 0 -> stringResource(R.string.insights_change_down_cd, formats.decimal(-tenths / 10f))
        else -> stringResource(R.string.insights_change_same_cd)
    }
    Surface(
        shape = CircleShape,
        color = MaterialTheme.colorScheme.surface,
        contentColor = MaterialTheme.colorScheme.onSurface,
        modifier = modifier.clearAndSetSemantics { contentDescription = description },
    ) {
        Row(Modifier.padding(start = Spacing.s, end = Spacing.m, top = Spacing.xs, bottom = Spacing.xs), verticalAlignment = Alignment.CenterVertically) {
            DakilioIcon(
                when {
                    tenths > 0 -> DakilioSymbols.TrendingUp
                    tenths < 0 -> DakilioSymbols.TrendingDown
                    else -> DakilioSymbols.TrendingFlat
                },
                contentDescription = null,
                modifier = Modifier.size(18.dp),
            )
            Text(text, style = MaterialTheme.typography.labelLargeEmphasized, modifier = Modifier.padding(start = Spacing.xs))
        }
    }
}

@Composable
private fun Pill(text: String) {
    Surface(shape = CircleShape, color = MaterialTheme.colorScheme.surface, contentColor = MaterialTheme.colorScheme.onSurface) {
        Text(text, style = MaterialTheme.typography.labelLarge, modifier = Modifier.padding(horizontal = Spacing.m, vertical = Spacing.xs))
    }
}
