package pl.dakil.dakilio.feature.insights.impl.recap

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.PagerState
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.IconButton
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialShapes
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.material3.LocalContentColor
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlin.math.roundToInt
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.launch
import pl.dakil.dakilio.core.designsystem.component.PolygonShape
import pl.dakil.dakilio.core.designsystem.component.RollingNumber
import pl.dakil.dakilio.core.designsystem.component.entrance
import pl.dakil.dakilio.core.designsystem.component.rememberHaptics
import pl.dakil.dakilio.core.designsystem.icon.DakilioIcon
import pl.dakil.dakilio.core.designsystem.icon.DakilioSymbols
import pl.dakil.dakilio.core.designsystem.theme.Spacing
import pl.dakil.dakilio.core.designsystem.theme.decorativeSpec
import pl.dakil.dakilio.core.designsystem.theme.decorativeTurn
import pl.dakil.dakilio.core.model.Mood
import pl.dakil.dakilio.core.model.primaryOf
import pl.dakil.dakilio.core.ui.DakilioFormats
import pl.dakil.dakilio.core.ui.component.TagPill
import pl.dakil.dakilio.core.ui.hintOrNull
import pl.dakil.dakilio.core.ui.mood.AverageMoodFace
import pl.dakil.dakilio.core.ui.mood.MoodBadge
import pl.dakil.dakilio.core.ui.mood.NoMoodBadge
import pl.dakil.dakilio.core.ui.mood.moodColors
import pl.dakil.dakilio.core.ui.rememberFormats
import pl.dakil.dakilio.feature.insights.impl.R
import pl.dakil.dakilio.feature.insights.impl.overview.TagIcon
import pl.dakil.dakilio.feature.insights.impl.chart.BarSegment
import pl.dakil.dakilio.feature.insights.impl.chart.MoodLineChart
import pl.dakil.dakilio.feature.insights.impl.chart.SegmentedBar
import pl.dakil.dakilio.feature.insights.impl.chart.WeekPills
import pl.dakil.dakilio.feature.insights.impl.habits.alignedWeeks
import pl.dakil.dakilio.feature.insights.impl.habits.habitColor
import pl.dakil.dakilio.feature.insights.impl.habits.onTargetLabel
import pl.dakil.dakilio.feature.insights.impl.habits.weekStarts
import pl.dakil.dakilio.feature.insights.impl.logic.Recap
import pl.dakil.dakilio.feature.insights.impl.logic.RecapPage
import pl.dakil.dakilio.feature.insights.impl.logic.brighter
import pl.dakil.dakilio.feature.insights.impl.logic.nearestMood
import pl.dakil.dakilio.feature.insights.impl.overview.RecordsCard
import pl.dakil.dakilio.feature.insights.impl.overview.dayPartLabel
import pl.dakil.dakilio.feature.insights.impl.overview.moodValueLabel
import pl.dakil.dakilio.feature.insights.impl.periodLabel

/** A page's frame: centered, readable width, cascading in. */
@Composable
private fun PageColumn(content: @Composable ColumnScope.() -> Unit) {
    Box(Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding().padding(top = 72.dp, bottom = Spacing.xl), contentAlignment = Alignment.Center) {
        Column(
            Modifier.widthIn(max = 480.dp).fillMaxWidth().padding(horizontal = Spacing.xl),
            verticalArrangement = Arrangement.spacedBy(Spacing.l),
            content = content,
        )
    }
}

@Composable
private fun Kicker(text: String, index: Int = 0) {
    Text(text, style = MaterialTheme.typography.titleMedium, modifier = Modifier.recapEntrance(index).semantics { heading() })
}

@Composable
private fun Big(text: String, index: Int = 1) {
    Text(text, style = MaterialTheme.typography.displaySmallEmphasized, modifier = Modifier.recapEntrance(index))
}

@Composable
internal fun RecapPageContent(
    page: RecapPage,
    state: InsightsRecapUiState.Ready,
    steps: RecapSteps,
    periodName: String,
    formats: DakilioFormats,
    onOpenPost: (Long) -> Unit,
    onShare: () -> Unit,
    onDone: () -> Unit,
) {
    val recap = state.recap
    val overview = recap.overview
    val moods = state.moods
    when (page) {
        RecapPage.Title -> PageColumn {
            Kicker(stringResource(R.string.insights_recap_title_kicker))
            Text(periodName, style = MaterialTheme.typography.displayMediumEmphasized, modifier = Modifier.recapEntrance(1))
            val mood = overview.mood.average?.let { nearestMood(moods, it) }
            Box(Modifier.recapEntrance(2).padding(vertical = Spacing.l)) {
                if (mood != null) AverageMoodFace(mood, size = 168.dp) else NoMoodBadge(size = 120.dp)
            }
            Text(
                mood?.let { stringResource(R.string.insights_mostly, it.label) } ?: pluralStringResource(R.plurals.insights_heat_entries, overview.entries, overview.entries),
                style = MaterialTheme.typography.headlineSmall,
                modifier = Modifier.recapEntrance(3),
            )
            hintOrNull(stringResource(R.string.insights_recap_tap))?.let { Text(it, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.recapEntrance(4)) }
        }

        RecapPage.Numbers -> PageColumn {
            Kicker(stringResource(R.string.insights_recap_numbers))
            RecapStat(formats.count(overview.entries), pluralStringResource(R.plurals.insights_recap_entries, overview.entries), 1)
            RecapStat(formats.count(overview.daysJournaled), pluralStringResource(R.plurals.insights_recap_days, overview.range.days, overview.range.days), 2)
            if (overview.writing.words > 0) RecapStat(formats.count(overview.writing.words), pluralStringResource(R.plurals.insights_recap_words, overview.writing.words), 3)
            if (overview.streak > 1) {
                RecapStat(
                    formats.count(overview.streak),
                    pluralStringResource(if (overview.streakIsCurrent) R.plurals.insights_recap_streak_now else R.plurals.insights_recap_streak, overview.streak),
                    4,
                )
            }
        }

        RecapPage.Mood -> PageColumn {
            Kicker(stringResource(R.string.insights_recap_mood))
            val average = overview.mood.average!!
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.recapEntrance(1)) {
                nearestMood(moods, average)?.let { MoodBadge(it, size = 64.dp, active = true) }
                Text(formats.decimal(average), style = MaterialTheme.typography.displayMediumEmphasized, modifier = Modifier.padding(start = Spacing.l))
            }
            val shown = overview.mood.distribution.filter { it.count > 0 }
            SegmentedBar(
                segments = shown.map { BarSegment(it.count, moodColors(it.mood).container) },
                description = overview.mood.distribution.joinToString { "${it.mood.label} ${it.count}" },
                height = 28.dp,
                modifier = Modifier.recapEntrance(2),
            )
            MoodShares(overview.mood.distribution.map { it.mood to it.count }, Modifier.recapEntrance(3))
            if (overview.flow.points >= 2) {
                MoodLineChart(
                    values = overview.flow.buckets.map { it.average },
                    moods = moods,
                    description = stringResource(R.string.insights_mood_flow),
                    height = 120.dp,
                    modifier = Modifier.recapEntrance(4),
                )
            }
        }

        RecapPage.BrightestDay -> PageColumn {
            val post = recap.brightest!!
            Kicker(stringResource(R.string.insights_recap_brightest))
            Big(formats.fullDate(post.date, state.today))
            Surface(shape = RoundedCornerShape(28.dp), color = MaterialTheme.colorScheme.surface, modifier = Modifier.recapEntrance(2).fillMaxWidth()) {
                Column(Modifier.padding(Spacing.xl), verticalArrangement = Arrangement.spacedBy(Spacing.m)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        post.mood?.let { MoodBadge(it, size = 44.dp, active = true) }
                        Text(
                            post.title ?: post.mood?.label.orEmpty(),
                            style = MaterialTheme.typography.titleMediumEmphasized,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.padding(start = Spacing.m),
                        )
                    }
                    post.note?.let {
                        Text(it, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 5, overflow = TextOverflow.Ellipsis)
                    }
                    if (post.tags.isNotEmpty()) RecapTags(post.tags.take(6))
                }
            }
            Button(onClick = { onOpenPost(post.id) }, modifier = Modifier.recapEntrance(3)) { Text(stringResource(R.string.insights_recap_open_entry)) }
        }

        RecapPage.Tags -> PageColumn {
            Kicker(stringResource(R.string.insights_recap_tags))
            overview.tags.take(3).forEachIndexed { i, stat ->
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.recapEntrance(i + 1)) {
                    Text("#${i + 1}", style = MaterialTheme.typography.headlineMediumEmphasized, modifier = Modifier.widthIn(min = 56.dp))
                    TagIcon(stat.tag, size = 48.dp)
                    Column(Modifier.padding(start = Spacing.m)) {
                        Text(stat.tag.label, style = MaterialTheme.typography.headlineSmallEmphasized, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text(pluralStringResource(R.plurals.insights_days_count, stat.days, stat.days), style = MaterialTheme.typography.bodyMedium)
                    }
                }
            }
            overview.tags.brighter(1).firstOrNull()?.let { stat ->
                Text(
                    stringResource(R.string.insights_recap_tag_brighter, stat.tag.label, formats.signedDecimal(stat.link!!.lift)),
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.recapEntrance(4).padding(top = Spacing.m),
                )
            }
        }

        RecapPage.Rhythm -> PageColumn {
            Kicker(stringResource(R.string.insights_recap_rhythm))
            overview.rhythm.bestWeekday?.let { day ->
                Big(formats.weekday(day))
                Text(stringResource(R.string.insights_hl_best_weekday), style = MaterialTheme.typography.titleMedium, modifier = Modifier.recapEntrance(2))
            }
            overview.rhythm.busiestDayPart?.let { part ->
                Text(
                    stringResource(R.string.insights_recap_mostly_written, dayPartLabel(part).lowercase()),
                    style = MaterialTheme.typography.headlineSmall,
                    modifier = Modifier.recapEntrance(3).padding(top = Spacing.l),
                )
            }
        }

        RecapPage.Habits -> PageColumn {
            Kicker(stringResource(R.string.insights_habits))
            val starts = weekStarts(overview.range, state.firstDay)
            overview.habits.forEachIndexed { i, stat ->
                Row(Modifier.recapEntrance(i + 1)) {
                    TagIcon(stat.habit.tag, size = 48.dp)
                    Column(Modifier.padding(start = Spacing.m)) {
                        Text(stat.habit.tag.label, style = MaterialTheme.typography.titleLargeEmphasized)
                        Text(onTargetLabel(stat), style = MaterialTheme.typography.bodyMedium)
                        if (starts.size > 1) {
                            WeekPills(alignedWeeks(stat, starts), habitColor(stat.habit.tag), Modifier.padding(top = Spacing.s))
                        }
                    }
                }
            }
        }

        RecapPage.Days -> PageColumn {
            Kicker(stringResource(R.string.insights_years))
            MonthFaces(recap, moods, formats, Modifier.recapEntrance(1))
        }

        RecapPage.Photos, RecapPage.NewTags -> PageColumn { }

        RecapPage.Records -> PageColumn {
            Box(Modifier.recapEntrance(0)) { RecordsCard(overview.records!!, moods, state.today, formats) }
        }

        RecapPage.Change -> PageColumn {
            val change = overview.moodChange!!
            val before = overview.comparison!!
            Kicker(stringResource(R.string.insights_recap_change))
            Big(formats.signedDecimal(change))
            Text(
                stringResource(if (Math.round(change * 10) >= 0) R.string.insights_recap_change_up else R.string.insights_recap_change_down),
                style = MaterialTheme.typography.headlineSmall,
                modifier = Modifier.recapEntrance(2),
            )
            Text(
                stringResource(R.string.insights_recap_change_detail, moodValueLabel(before.averageMood, moods, formats), moodValueLabel(overview.mood.average, moods, formats)),
                style = MaterialTheme.typography.bodyLarge,
                modifier = Modifier.recapEntrance(3),
            )
        }

        RecapPage.End -> PageColumn {
            Kicker(stringResource(R.string.insights_recap_end_kicker))
            Text(stringResource(R.string.insights_recap_end, periodName), style = MaterialTheme.typography.displaySmallEmphasized, modifier = Modifier.recapEntrance(1))
            Text(stringResource(R.string.insights_recap_end_body), style = MaterialTheme.typography.bodyLarge, modifier = Modifier.recapEntrance(2))
            Spacer(Modifier.height(Spacing.l))
            Row(horizontalArrangement = Arrangement.spacedBy(Spacing.s), modifier = Modifier.recapEntrance(3)) {
                onShare.let { share ->
                    OutlinedButton(onClick = share, colors = ButtonDefaults.outlinedButtonColors(contentColor = LocalContentColor.current)) {
                        DakilioIcon(DakilioSymbols.Share, contentDescription = null, modifier = Modifier.size(ButtonDefaults.IconSize))
                        Text(stringResource(R.string.insights_share), Modifier.padding(start = Spacing.s))
                    }
                }
                Button(onClick = onDone) { Text(stringResource(R.string.insights_recap_done)) }
            }
        }
    }
}

@Composable
private fun RecapStat(value: String, label: String, index: Int) {
    Row(verticalAlignment = Alignment.Bottom, modifier = Modifier.recapEntrance(index)) {
        RollingNumber(value, style = MaterialTheme.typography.displayMediumEmphasized)
        Text(label, style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(start = Spacing.m, bottom = Spacing.s))
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun MoodShares(counts: List<Pair<Mood, Int>>, modifier: Modifier = Modifier) {
    val total = counts.sumOf { it.second }.coerceAtLeast(1)
    FlowRow(modifier, horizontalArrangement = Arrangement.spacedBy(Spacing.l), verticalArrangement = Arrangement.spacedBy(Spacing.s)) {
        counts.filter { it.second > 0 }.forEach { (mood, count) ->
            Row(verticalAlignment = Alignment.CenterVertically) {
                MoodBadge(mood, size = 22.dp)
                Text("${(count * 100f / total).roundToInt()}%", style = MaterialTheme.typography.labelLarge, modifier = Modifier.padding(start = Spacing.xs))
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun RecapTags(tags: List<pl.dakil.dakilio.core.model.Tag>) {
    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) { tags.forEach { TagPill(it) } }
}

/** A year's months as a 4×3 grid: each month's short name over its average face, or a faint ring without entries. */
@Composable
internal fun MonthFaces(recap: Recap, moods: List<Mood>, formats: DakilioFormats, modifier: Modifier = Modifier) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(Spacing.l)) {
        recap.months.chunked(4).forEach { row ->
            Row(Modifier.fillMaxWidth()) {
                row.forEach { (month, summary) ->
                    Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                        val mood = summary?.averageLevel?.let(moods::primaryOf)
                        if (mood != null) {
                            MoodBadge(mood, size = 48.dp)
                        } else {
                            Box(Modifier.size(48.dp).padding(8.dp).background(LocalContentColor.current.copy(alpha = 0.12f), CircleShape))
                        }
                        Text(formats.monthShort(month), style = MaterialTheme.typography.labelLarge, textAlign = TextAlign.Center, modifier = Modifier.padding(top = Spacing.xs))
                    }
                }
            }
        }
    }
}
