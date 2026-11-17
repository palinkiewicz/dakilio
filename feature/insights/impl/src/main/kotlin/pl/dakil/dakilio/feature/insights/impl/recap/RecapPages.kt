package pl.dakil.dakilio.feature.insights.impl.recap

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import java.time.LocalDate
import kotlin.math.roundToInt
import pl.dakil.dakilio.core.designsystem.component.rememberHaptics
import pl.dakil.dakilio.core.designsystem.icon.DakilioIcon
import pl.dakil.dakilio.core.designsystem.icon.DakilioSymbols
import pl.dakil.dakilio.core.designsystem.theme.Spacing
import pl.dakil.dakilio.core.model.DateRange
import pl.dakil.dakilio.core.model.Mood
import pl.dakil.dakilio.core.model.primaryOf
import pl.dakil.dakilio.core.ui.DakilioFormats
import pl.dakil.dakilio.core.ui.hintOrNull
import pl.dakil.dakilio.core.ui.mood.AverageMoodFace
import pl.dakil.dakilio.core.ui.mood.MoodBadge
import pl.dakil.dakilio.core.ui.mood.NoMoodBadge
import pl.dakil.dakilio.core.ui.mood.moodColors
import pl.dakil.dakilio.feature.insights.impl.R
import pl.dakil.dakilio.feature.insights.impl.chart.BarSegment
import pl.dakil.dakilio.feature.insights.impl.chart.DaysGrid
import pl.dakil.dakilio.feature.insights.impl.chart.MoodLineChart
import pl.dakil.dakilio.feature.insights.impl.chart.SegmentedBar
import pl.dakil.dakilio.feature.insights.impl.chart.WeekPills
import pl.dakil.dakilio.feature.insights.impl.chart.waveAt
import pl.dakil.dakilio.feature.insights.impl.habits.alignedWeeks
import pl.dakil.dakilio.feature.insights.impl.habits.habitColor
import pl.dakil.dakilio.feature.insights.impl.habits.onTargetLabel
import pl.dakil.dakilio.feature.insights.impl.habits.weekStarts
import pl.dakil.dakilio.feature.insights.impl.logic.InsightsPeriod
import pl.dakil.dakilio.feature.insights.impl.logic.Recap
import pl.dakil.dakilio.feature.insights.impl.logic.RecapPage
import pl.dakil.dakilio.feature.insights.impl.logic.RecapTitle
import pl.dakil.dakilio.feature.insights.impl.logic.calendarRange
import pl.dakil.dakilio.feature.insights.impl.logic.nearestMood
import pl.dakil.dakilio.feature.insights.impl.overview.RecordsCard
import pl.dakil.dakilio.feature.insights.impl.overview.TagIcon
import pl.dakil.dakilio.feature.insights.impl.overview.dayCaption

/** A recap page's frame: centered, readable width. */
@Composable
internal fun PageColumn(content: @Composable ColumnScope.() -> Unit) {
    Box(Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding().padding(top = 72.dp, bottom = Spacing.xl), contentAlignment = Alignment.Center) {
        Column(
            Modifier.widthIn(max = 480.dp).fillMaxWidth().padding(horizontal = Spacing.xl),
            verticalArrangement = Arrangement.spacedBy(Spacing.l),
            content = content,
        )
    }
}

@Composable
internal fun Kicker(text: String, index: Int = 0) {
    Text(text, style = MaterialTheme.typography.titleMedium, modifier = Modifier.recapEntrance(index).semantics { heading() })
}

@Composable
internal fun Big(text: String, index: Int = 1) {
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
    when (page) {
        RecapPage.Title -> TitlePage(state, periodName)
        RecapPage.Numbers -> NumbersPage(state, formats)
        RecapPage.Mood -> MoodPage(state, formats)
        RecapPage.Days -> DaysPage(state, formats)
        RecapPage.Change -> ChangePage(state, formats)
        RecapPage.BrightestDay -> BrightestDayPage(state, formats, onOpenPost)
        RecapPage.Photos -> PhotosPage(state)
        RecapPage.Tags -> TagsPage(state, steps, formats)
        RecapPage.NewTags -> NewTagsPage(state)
        RecapPage.Rhythm -> RhythmPage(state, steps, formats)
        RecapPage.Habits -> HabitsPage(state)
        RecapPage.Records -> PageColumn { Box(Modifier.recapEntrance(0)) { RecordsCard(state.recap.overview.records!!, state.moods, state.today, formats) } }
        RecapPage.End -> FinalePage(state, periodName, formats, onShare, onDone)
    }
}

/** The period's name, large, and its title. */
@Composable
private fun TitlePage(state: InsightsRecapUiState.Ready, periodName: String) {
    PageColumn {
        Kicker(stringResource(R.string.insights_recap_title_kicker))
        Text(periodName, style = MaterialTheme.typography.displayLargeEmphasized, modifier = Modifier.recapPop(1, turn = -8f))
        Text(
            recapTitleText(state.recap.title, state.header.period is InsightsPeriod.Year),
            style = MaterialTheme.typography.headlineMediumEmphasized,
            modifier = Modifier.recapEntrance(3),
        )
        hintOrNull(stringResource(R.string.insights_recap_tap))?.let {
            Text(it, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.recapEntrance(5).padding(top = Spacing.xl))
        }
    }
}

@Composable
private fun recapTitleText(title: RecapTitle, year: Boolean): String = stringResource(
    when (title) {
        RecapTitle.Bright -> if (year) R.string.insights_recap_title_bright_year else R.string.insights_recap_title_bright_month
        RecapTitle.Climbing -> R.string.insights_recap_title_climbing
        RecapTitle.Faithful -> R.string.insights_recap_title_faithful
        RecapTitle.Steady -> if (year) R.string.insights_recap_title_steady_year else R.string.insights_recap_title_steady_month
        RecapTitle.ManyShades -> if (year) R.string.insights_recap_title_shades_year else R.string.insights_recap_title_shades_month
        RecapTitle.Yours -> if (year) R.string.insights_recap_title_yours_year else R.string.insights_recap_title_yours_month
    },
)

/** Entries, days, words and streak, each counting up; a month's days fill in as a strip. */
@Composable
private fun NumbersPage(state: InsightsRecapUiState.Ready, formats: DakilioFormats) {
    val recap = state.recap
    val overview = recap.overview
    PageColumn {
        Kicker(stringResource(R.string.insights_recap_numbers))
        RecapStat(overview.entries, pluralStringResource(R.plurals.insights_recap_entries, overview.entries), formats, 1)
        Column(Modifier.recapEntrance(2), verticalArrangement = Arrangement.spacedBy(Spacing.s)) {
            RecapStat(overview.daysJournaled, pluralStringResource(R.plurals.insights_recap_days, overview.daysJournaled, overview.range.days), formats, delayMillis = 150)
            if (overview.range.days <= 31) DayStrip(overview.range, overview.calendar.cells.keys)
        }
        if (overview.writing.words > 0) {
            Column(Modifier.recapEntrance(3)) {
                RecapStat(overview.writing.words, pluralStringResource(R.plurals.insights_recap_words, overview.writing.words), formats, delayMillis = 300)
                if (recap.bookPages >= 1) {
                    Text(pluralStringResource(R.plurals.insights_recap_book_pages, recap.bookPages, recap.bookPages), style = MaterialTheme.typography.bodyLarge)
                }
            }
        }
        if (overview.streak > 1) {
            Box(Modifier.recapEntrance(4)) {
                RecapStat(
                    overview.streak,
                    pluralStringResource(if (overview.streakIsCurrent) R.plurals.insights_recap_streak_now else R.plurals.insights_recap_streak, overview.streak),
                    formats,
                    delayMillis = 450,
                )
            }
        }
    }
}

@Composable
private fun RecapStat(value: Int, label: String, formats: DakilioFormats, index: Int? = null, delayMillis: Long = 0) {
    Row(verticalAlignment = Alignment.Bottom, modifier = if (index != null) Modifier.recapEntrance(index) else Modifier) {
        CountUp(value, formats::count, style = MaterialTheme.typography.displayMediumEmphasized, delayMillis = delayMillis)
        Text(label, style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(start = Spacing.m, bottom = Spacing.s))
    }
}

/** A dot per day of [range], solid where there are entries, filling in from the first day. */
@Composable
private fun DayStrip(range: DateRange, journaled: Set<LocalDate>) {
    val content = LocalContentColor.current
    val appear = rememberRecapProgress(400)
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(3.dp)) {
        repeat(range.days) { i ->
            val written = range.from.plusDays(i.toLong()) in journaled
            Box(
                Modifier
                    .weight(1f)
                    .aspectRatio(1f)
                    .graphicsLayer {
                        val p = waveAt(appear(), i, range.days)
                        alpha = p
                        scaleX = 0.2f + 0.8f * p
                        scaleY = 0.2f + 0.8f * p
                    }
                    .background(content.copy(alpha = if (written) 0.9f else 0.16f), CircleShape),
            )
        }
    }
}

/** The average face pops in, the average counts up, the mix grows and the mood line draws itself. */
@Composable
private fun MoodPage(state: InsightsRecapUiState.Ready, formats: DakilioFormats) {
    val overview = state.recap.overview
    val moods = state.moods
    val average = overview.mood.average!!
    val mood = nearestMood(moods, average)
    PageColumn {
        Kicker(stringResource(R.string.insights_recap_mood))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.recapPop(1, turn = -40f)) {
                if (mood != null) AverageMoodFace(mood, size = 120.dp) else NoMoodBadge(size = 96.dp)
            }
            Column(Modifier.padding(start = Spacing.l).recapEntrance(2)) {
                mood?.let { Text(stringResource(R.string.insights_mostly, it.label), style = MaterialTheme.typography.titleLarge) }
                CountUp(average, formats::decimal, style = MaterialTheme.typography.displayMediumEmphasized, from = 1f, delayMillis = 200)
            }
        }
        val grow = rememberRecapProgress(500)
        SegmentedBar(
            segments = overview.mood.distribution.filter { it.count > 0 }.map { BarSegment(it.count, moodColors(it.mood).container) },
            description = overview.mood.distribution.joinToString { "${it.mood.label} ${it.count}" },
            height = 28.dp,
            modifier = Modifier.drawWithContent { clipRect(right = size.width * grow()) { this@drawWithContent.drawContent() } },
        )
        MoodShares(overview.mood.distribution.map { it.mood to it.count }, Modifier.recapEntrance(4))
        if (overview.flow.points >= 2) {
            // Composed once the page is shown, so the line draws itself in front of the user.
            Box(Modifier.fillMaxWidth().height(120.dp)) {
                if (rememberShown()) {
                    MoodLineChart(
                        values = overview.flow.buckets.map { it.average },
                        moods = moods,
                        description = stringResource(R.string.insights_mood_flow),
                        height = 120.dp,
                    )
                }
            }
        }
    }
}

/** Whether the page has been shown; stays true once it has. */
@Composable
private fun rememberShown(): Boolean {
    var shown by remember { mutableStateOf(false) }
    if (LocalRecapPageActive.current) shown = true
    return shown
}

/** A month's days filling in with their moods, or a year's months popping in. */
@Composable
private fun DaysPage(state: InsightsRecapUiState.Ready, formats: DakilioFormats) {
    val overview = state.recap.overview
    val haptics = rememberHaptics()
    val appear = rememberRecapProgress(300, onDone = { haptics.tick() })
    PageColumn {
        when (val period = state.header.period) {
            is InsightsPeriod.Year -> {
                Kicker(stringResource(R.string.insights_years))
                MonthFaces(state.recap, state.moods, formats, appear = appear)
            }
            else -> {
                Kicker(stringResource(R.string.insights_recap_days_kicker))
                val range = (period as? InsightsPeriod.Month)?.calendarRange(state.header.bounds) ?: overview.range
                Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                    DaysGrid(
                        range = range,
                        cells = overview.calendar.cells,
                        moods = state.moods,
                        firstDay = state.firstDay,
                        today = state.today,
                        selected = null,
                        onSelect = {},
                        formats = formats,
                        describe = { dayCaption(it, overview.calendar.cells[it], state.moods, state.today, formats) },
                        selectable = false,
                        appear = appear,
                    )
                }
            }
        }
    }
}

/** The average before and now, as two faces, with the change counting up. */
@Composable
private fun ChangePage(state: InsightsRecapUiState.Ready, formats: DakilioFormats) {
    val overview = state.recap.overview
    val change = overview.moodChange!!
    val before = overview.comparison!!.averageMood!!
    val now = overview.mood.average!!
    PageColumn {
        Kicker(stringResource(R.string.insights_recap_change))
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Spacing.l)) {
            nearestMood(state.moods, before)?.let { MoodBadge(it, size = 56.dp, modifier = Modifier.recapEntrance(1)) }
            DakilioIcon(DakilioSymbols.ArrowBack, contentDescription = null, modifier = Modifier.recapEntrance(2).graphicsLayer { rotationZ = 180f })
            nearestMood(state.moods, now)?.let { MoodBadge(it, size = 80.dp, active = true, modifier = Modifier.recapPop(3, turn = 30f)) }
        }
        CountUp(change, formats::signedDecimal, style = MaterialTheme.typography.displayLargeEmphasized, modifier = Modifier.recapEntrance(3), delayMillis = 300)
        Text(
            stringResource(if (Math.round(change * 10) >= 0) R.string.insights_recap_change_up else R.string.insights_recap_change_down),
            style = MaterialTheme.typography.headlineSmall,
            modifier = Modifier.recapEntrance(4),
        )
        Text(
            stringResource(R.string.insights_recap_change_detail, formats.decimal(before), formats.decimal(now)),
            style = MaterialTheme.typography.bodyLarge,
            modifier = Modifier.recapEntrance(5),
        )
    }
}

@Composable
private fun HabitsPage(state: InsightsRecapUiState.Ready) {
    val overview = state.recap.overview
    PageColumn {
        Kicker(stringResource(R.string.insights_habits))
        val starts = weekStarts(overview.range, state.firstDay)
        overview.habits.forEachIndexed { i, stat ->
            Row(Modifier.recapEntrance(i + 1)) {
                TagIcon(stat.habit.tag, size = 48.dp, modifier = Modifier.recapPop(i + 1))
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

/**
 * A year's months as a 4×3 grid: each month's short name over its average face, or a faint ring
 * without entries. With [appear], the months pop in one after another.
 */
@Composable
internal fun MonthFaces(recap: Recap, moods: List<Mood>, formats: DakilioFormats, modifier: Modifier = Modifier, appear: (() -> Float)? = null) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(Spacing.l)) {
        recap.months.chunked(4).forEachIndexed { rowIndex, row ->
            Row(Modifier.fillMaxWidth()) {
                row.forEachIndexed { columnIndex, (month, summary) ->
                    val order = rowIndex * 4 + columnIndex
                    Column(
                        Modifier
                            .weight(1f)
                            .then(
                                if (appear == null) {
                                    Modifier
                                } else {
                                    Modifier.graphicsLayer {
                                        val p = waveAt(appear(), order, recap.months.size, spread = 2f)
                                        alpha = p
                                        scaleX = 0.4f + 0.6f * p
                                        scaleY = 0.4f + 0.6f * p
                                    }
                                },
                            ),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
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

/** The end: the recap's card, as it would be shared, with Share and Done. */
@Composable
private fun FinalePage(state: InsightsRecapUiState.Ready, periodName: String, formats: DakilioFormats, onShare: () -> Unit, onDone: () -> Unit) {
    PageColumn {
        Kicker(stringResource(R.string.insights_recap_end_kicker))
        Text(stringResource(R.string.insights_recap_end, periodName), style = MaterialTheme.typography.displaySmallEmphasized, modifier = Modifier.recapEntrance(1))
        Text(stringResource(R.string.insights_recap_end_body), style = MaterialTheme.typography.bodyLarge, modifier = Modifier.recapEntrance(2))
        Spacer(Modifier.height(Spacing.l))
        FinaleButtons(onShare, onDone, Modifier.recapEntrance(3))
    }
}

@Composable
internal fun FinaleButtons(onShare: () -> Unit, onDone: () -> Unit, modifier: Modifier = Modifier) {
    Row(modifier, horizontalArrangement = Arrangement.spacedBy(Spacing.s)) {
        OutlinedButton(onClick = onShare, colors = ButtonDefaults.outlinedButtonColors(contentColor = LocalContentColor.current)) {
            DakilioIcon(DakilioSymbols.Share, contentDescription = null, modifier = Modifier.size(ButtonDefaults.IconSize))
            Text(stringResource(R.string.insights_share), Modifier.padding(start = Spacing.s))
        }
        Button(onClick = onDone) { Text(stringResource(R.string.insights_recap_done)) }
    }
}
