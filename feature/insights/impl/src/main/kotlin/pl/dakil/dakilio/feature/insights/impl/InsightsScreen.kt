package pl.dakil.dakilio.feature.insights.impl

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.LargeFlexibleTopAppBar
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialShapes
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import java.time.DayOfWeek
import java.time.LocalDate
import kotlin.math.roundToInt
import pl.dakil.dakilio.core.designsystem.component.ConnectedToggleRow
import pl.dakil.dakilio.core.designsystem.component.PolygonShape
import pl.dakil.dakilio.core.designsystem.component.ToggleOption
import pl.dakil.dakilio.core.designsystem.icon.DakilioIcon
import pl.dakil.dakilio.core.designsystem.icon.DakilioSymbols
import pl.dakil.dakilio.core.designsystem.icon.symbolForKey
import pl.dakil.dakilio.core.designsystem.theme.Spacing
import pl.dakil.dakilio.core.model.Mood
import pl.dakil.dakilio.core.ui.DakilioFormats
import pl.dakil.dakilio.core.ui.bottomBarPadding
import pl.dakil.dakilio.core.ui.component.DiaryFilterRow
import pl.dakil.dakilio.core.ui.component.EmptyState
import pl.dakil.dakilio.core.ui.mood.MoodBadge
import pl.dakil.dakilio.core.ui.mood.moodColors
import pl.dakil.dakilio.core.ui.rememberFormats

private val CardShape = RoundedCornerShape(28.dp)

@Composable
internal fun InsightsScreen(viewModel: InsightsViewModel) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()
    Scaffold(
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        contentWindowInsets = WindowInsets(0),
        topBar = {
            LargeFlexibleTopAppBar(
                title = { Text(stringResource(R.string.insights_title)) },
                subtitle = { Text(stringResource(R.string.insights_subtitle)) },
                scrollBehavior = scrollBehavior,
            )
        },
    ) { padding ->
        when (val current = state) {
            InsightsUiState.Loading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { LoadingIndicator() }
            is InsightsUiState.Ready -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
                if (!current.hasAnyEntries) {
                    EmptyState(
                        symbol = DakilioSymbols.Insights,
                        title = stringResource(R.string.insights_empty_title),
                        body = stringResource(R.string.insights_empty_body),
                        modifier = Modifier.padding(padding).align(Alignment.Center),
                    )
                } else {
                    InsightsContent(current, padding, viewModel::selectRange, viewModel::selectDiary)
                }
            }
        }
    }
}

@Composable
private fun InsightsContent(
    state: InsightsUiState.Ready,
    padding: PaddingValues,
    onRange: (InsightsRange) -> Unit,
    onDiary: (Long?) -> Unit,
) {
    val formats = rememberFormats()
    val insights = state.insights
    val rangeLabel = when (state.range) {
        InsightsRange.Month -> stringResource(R.string.insights_range_month)
        InsightsRange.Quarter -> stringResource(R.string.insights_range_quarter)
        InsightsRange.All -> stringResource(R.string.insights_range_all)
    }
    LazyColumn(
        modifier = Modifier.widthIn(max = Spacing.maxContentWidth).fillMaxSize(),
        contentPadding = PaddingValues(
            top = padding.calculateTopPadding(),
            bottom = bottomBarPadding(extra = Spacing.xl),
            start = Spacing.gutter,
            end = Spacing.gutter,
        ),
        verticalArrangement = Arrangement.spacedBy(Spacing.m),
    ) {
        item(key = "filters") {
            Column(verticalArrangement = Arrangement.spacedBy(Spacing.s)) {
                ConnectedToggleRow(
                    options = listOf(
                        ToggleOption(InsightsRange.Month, stringResource(R.string.insights_range_month)),
                        ToggleOption(InsightsRange.Quarter, stringResource(R.string.insights_range_quarter)),
                        ToggleOption(InsightsRange.All, stringResource(R.string.insights_range_all)),
                    ),
                    selected = state.range,
                    onSelect = onRange,
                )
                if (state.diaries.size > 1) {
                    DiaryFilterRow(state.diaries, state.selectedDiaryId, onDiary, contentPadding = PaddingValues(0.dp))
                }
            }
        }
        item(key = "streak") { StreakCard(insights) }
        item(key = "average") { AverageMoodCard(insights, state.moods, rangeLabel, state.range != InsightsRange.All) }
        item(key = "distribution") { DistributionCard(insights) }
        item(key = "map") { JourneyMapCard(insights, state.moods, state.today, formats) }
        item(key = "tags") { TopTagsCard(insights) }
        item(key = "coming") {
            Text(
                stringResource(R.string.insights_coming),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.fillMaxWidth().padding(vertical = Spacing.s),
            )
        }
    }
}

@Composable
private fun InsightCard(modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    Surface(shape = CardShape, color = MaterialTheme.colorScheme.surfaceContainer, modifier = modifier.fillMaxWidth()) {
        Column(Modifier.padding(Spacing.xl)) { content() }
    }
}

@Composable
private fun CardTitle(title: String, subtitle: String? = null) {
    Text(title, style = MaterialTheme.typography.titleMediumEmphasized)
    subtitle?.let {
        Text(
            it,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = Spacing.xxs),
        )
    }
}

@Composable
private fun StreakCard(insights: Insights) {
    val spin by rememberInfiniteTransition(label = "streakSpin").animateFloat(
        0f,
        360f,
        infiniteRepeatable(tween(30_000, easing = LinearEasing), RepeatMode.Restart),
        label = "streakSpinValue",
    )
    val sun = remember { PolygonShape(MaterialShapes.Sunny) }
    val colors = MaterialTheme.colorScheme
    Column(verticalArrangement = Arrangement.spacedBy(Spacing.xxs)) {
        Surface(
            shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp, bottomStart = 6.dp, bottomEnd = 6.dp),
            color = colors.primaryContainer,
            contentColor = colors.onPrimaryContainer,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Row(Modifier.padding(Spacing.xl), verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(112.dp), contentAlignment = Alignment.Center) {
                    Box(Modifier.size(112.dp).graphicsLayer { rotationZ = spin }.background(colors.primary, sun))
                    AnimatedContent(insights.currentStreak, transitionSpec = { fadeIn() togetherWith fadeOut() }, label = "streak") {
                        Text(it.toString(), style = MaterialTheme.typography.displayMediumEmphasized, color = colors.onPrimary)
                    }
                }
                Column(Modifier.padding(start = Spacing.xl)) {
                    Text(stringResource(R.string.insights_current_streak), style = MaterialTheme.typography.titleMedium)
                    Text(
                        pluralStringResource(R.plurals.insights_days, insights.currentStreak),
                        style = MaterialTheme.typography.headlineSmallEmphasized,
                    )
                }
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(Spacing.xxs)) {
            StatTile(stringResource(R.string.insights_longest_streak), insights.longestStreak, RoundedCornerShape(6.dp, 6.dp, 6.dp, 28.dp), Modifier.weight(1f))
            StatTile(stringResource(R.string.insights_entries), insights.entryCount, RoundedCornerShape(6.dp), Modifier.weight(1f))
            StatTile(stringResource(R.string.insights_days_journaled), insights.daysJournaled, RoundedCornerShape(6.dp, 6.dp, 28.dp, 6.dp), Modifier.weight(1f))
        }
    }
}

@Composable
private fun StatTile(label: String, value: Int, shape: RoundedCornerShape, modifier: Modifier) {
    Surface(shape = shape, color = MaterialTheme.colorScheme.surfaceContainer, modifier = modifier.fillMaxHeight()) {
        Column(Modifier.padding(horizontal = Spacing.l, vertical = Spacing.l)) {
            Text(value.toString(), style = MaterialTheme.typography.headlineMediumEmphasized)
            Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 2)
        }
    }
}

@Composable
private fun AverageMoodCard(insights: Insights, moods: List<Mood>, rangeLabel: String, showDelta: Boolean) {
    InsightCard {
        CardTitle(stringResource(R.string.insights_average_mood))
        val average = insights.averageMood
        val mood = average?.let { nearestMood(moods, it) }
        if (average == null || mood == null) {
            Text(
                stringResource(R.string.insights_no_mood),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = Spacing.s),
            )
            return@InsightCard
        }
        val percent = (average * 100).roundToInt()
        Row(Modifier.padding(top = Spacing.l), verticalAlignment = Alignment.CenterVertically) {
            MoodBadge(mood, size = 64.dp, active = true)
            Column(Modifier.padding(start = Spacing.l)) {
                Text(stringResource(R.string.insights_mostly, mood.label), style = MaterialTheme.typography.headlineSmallEmphasized)
                Text(
                    stringResource(R.string.insights_score, percent),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        Meter(average, Modifier.padding(top = Spacing.l))
        val previous = insights.previousAverageMood
        if (showDelta && previous != null) {
            val delta = ((average - previous) * 100).roundToInt()
            Text(
                when {
                    delta > 0 -> stringResource(R.string.insights_delta_up, delta, rangeLabel)
                    delta < 0 -> stringResource(R.string.insights_delta_down, -delta, rangeLabel)
                    else -> stringResource(R.string.insights_delta_same, rangeLabel)
                },
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = Spacing.s),
            )
        }
    }
}

/** Same-ramp meter: primary fill on a primary-container track. */
@Composable
private fun Meter(fraction: Float, modifier: Modifier = Modifier) {
    val animated by animateFloatAsState(fraction.coerceIn(0f, 1f), MaterialTheme.motionScheme.slowSpatialSpec(), label = "meter")
    val track = MaterialTheme.colorScheme.primaryContainer
    val fill = MaterialTheme.colorScheme.primary
    Canvas(modifier.fillMaxWidth().height(10.dp)) {
        val r = CornerRadius(size.height / 2)
        drawRoundRect(track, cornerRadius = r)
        drawRoundRect(fill, size = Size(size.width * animated, size.height), cornerRadius = r)
    }
}

@Composable
private fun DistributionCard(insights: Insights) {
    InsightCard {
        CardTitle(
            stringResource(R.string.insights_distribution),
            pluralStringResource(R.plurals.insights_moods_logged, insights.moodEntryCount, insights.moodEntryCount),
        )
        val total = insights.moodEntryCount
        if (total == 0) {
            Text(
                stringResource(R.string.insights_no_mood),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = Spacing.s),
            )
            return@InsightCard
        }
        val segments = insights.distribution.filter { it.count > 0 }
        val segmentColors = segments.map { moodColors(it.mood).container }
        // Part-to-whole on an ordered scale: one stacked bar, worst → best, 2dp surface gaps between
        // segments and 4dp rounded outer ends. The legend below carries identity and exact values.
        Canvas(
            Modifier
                .padding(top = Spacing.l)
                .fillMaxWidth()
                .height(24.dp)
                .semantics { contentDescription = insights.distribution.joinToString { "${it.mood.label} ${it.count}" } },
        ) {
            val gap = 2.dp.toPx()
            val usable = (size.width - gap * (segments.size - 1)).coerceAtLeast(0f)
            val radius = 4.dp.toPx()
            var x = 0f
            segments.forEachIndexed { i, segment ->
                val w = usable * segment.count / total
                val path = Path().apply {
                    addRoundRect(
                        androidx.compose.ui.geometry.RoundRect(
                            left = x, top = 0f, right = x + w, bottom = size.height,
                            topLeftCornerRadius = CornerRadius(if (i == 0) radius else 0f),
                            bottomLeftCornerRadius = CornerRadius(if (i == 0) radius else 0f),
                            topRightCornerRadius = CornerRadius(if (i == segments.lastIndex) radius else 0f),
                            bottomRightCornerRadius = CornerRadius(if (i == segments.lastIndex) radius else 0f),
                        ),
                    )
                }
                drawPath(path, segmentColors[i])
                x += w + gap
            }
        }
        FlowRow(
            modifier = Modifier.padding(top = Spacing.l).fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(Spacing.l),
            verticalArrangement = Arrangement.spacedBy(Spacing.s),
        ) {
            insights.distribution.forEach { item ->
                Row(verticalAlignment = Alignment.CenterVertically) {
                    MoodBadge(item.mood, size = 22.dp)
                    Text(
                        "${item.mood.label}  ${item.count} · ${(item.count * 100f / total).roundToInt()}%",
                        style = MaterialTheme.typography.labelLarge,
                        color = if (item.count > 0) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(start = Spacing.s),
                    )
                }
            }
        }
    }
}

@Composable
private fun JourneyMapCard(insights: Insights, moods: List<Mood>, today: LocalDate, formats: DakilioFormats) {
    var selected by rememberSaveable { mutableStateOf(today.toEpochDay()) }
    val colors = MaterialTheme.colorScheme
    val moodFill = moods.associate { it.id to moodColors(it).container }
    val noMoodFill = colors.outlineVariant
    val emptyFill = colors.surfaceContainerHighest
    InsightCard {
        CardTitle(stringResource(R.string.insights_journey_map), stringResource(R.string.insights_journey_map_body))
        val weeks = insights.heatmap.chunked(7)
        BoxWithConstraints(Modifier.padding(top = Spacing.l).fillMaxWidth()) {
            val gap = 4.dp
            val labelWidth = 16.dp
            val cell: Dp = minOf((maxWidth - labelWidth - gap * HEATMAP_WEEKS) / HEATMAP_WEEKS, 32.dp)
            Row(horizontalArrangement = Arrangement.spacedBy(gap), modifier = Modifier.align(Alignment.Center)) {
                // Weekday initials, Monday first, aligned with the rows.
                Column(verticalArrangement = Arrangement.spacedBy(gap)) {
                    DayOfWeek.entries.forEach { weekday ->
                        Box(Modifier.size(labelWidth, cell), contentAlignment = Alignment.CenterStart) {
                            Text(formats.weekdayNarrow(weekday), style = MaterialTheme.typography.labelSmall, color = colors.onSurfaceVariant)
                        }
                    }
                }
                weeks.forEach { week ->
                    Column(verticalArrangement = Arrangement.spacedBy(gap)) {
                        week.forEach { day ->
                            val mood = day.averageMood?.let { nearestMood(moods, it) }
                            val fill = when {
                                day.entries == 0 -> emptyFill
                                mood == null -> noMoodFill
                                else -> moodFill[mood.id] ?: noMoodFill
                            }
                            val isSelected = day.date.toEpochDay() == selected
                            Box(
                                Modifier
                                    .size(cell)
                                    .background(fill, RoundedCornerShape(cell * 0.28f))
                                    .then(if (isSelected) Modifier.border(2.dp, colors.onSurface, RoundedCornerShape(cell * 0.28f)) else Modifier)
                                    .clickable { selected = day.date.toEpochDay() }
                                    .semantics { contentDescription = heatDescription(day, mood, formats) },
                            )
                        }
                    }
                }
            }
        }
        val day = insights.heatmap.firstOrNull { it.date.toEpochDay() == selected }
        if (day != null) {
            val mood = day.averageMood?.let { nearestMood(moods, it) }
            Text(
                heatCaption(day, mood, formats, today),
                style = MaterialTheme.typography.labelLarge,
                modifier = Modifier.padding(top = Spacing.m),
            )
        }
        // Legend: mood faces carry identity alongside color.
        FlowRow(
            Modifier.padding(top = Spacing.m),
            horizontalArrangement = Arrangement.spacedBy(Spacing.m),
            verticalArrangement = Arrangement.spacedBy(Spacing.xs),
        ) {
            moods.sortedBy { it.tier }.forEach { mood ->
                Row(verticalAlignment = Alignment.CenterVertically) {
                    MoodBadge(mood, size = 16.dp)
                    Text(mood.label, style = MaterialTheme.typography.labelSmall, color = colors.onSurfaceVariant, modifier = Modifier.padding(start = Spacing.xs))
                }
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(14.dp).background(emptyFill, RoundedCornerShape(4.dp)))
                Text(stringResource(R.string.insights_no_entries_day), style = MaterialTheme.typography.labelSmall, color = colors.onSurfaceVariant, modifier = Modifier.padding(start = Spacing.xs))
            }
        }
    }
}

@Composable
private fun heatCaption(day: HeatDay, mood: Mood?, formats: DakilioFormats, today: LocalDate): String {
    val date = formats.fullDate(day.date, today)
    if (day.entries == 0) return stringResource(R.string.insights_heat_empty, date)
    val entries = pluralStringResource(R.plurals.insights_heat_entries, day.entries, day.entries)
    return stringResource(R.string.insights_heat_day, date, "${mood?.label ?: stringResource(R.string.insights_heat_no_mood)} · $entries")
}

private fun heatDescription(day: HeatDay, mood: Mood?, formats: DakilioFormats): String =
    listOfNotNull(formats.dayMonth(day.date), mood?.label, day.entries.takeIf { it > 0 }?.toString()).joinToString(", ")

@Composable
private fun TopTagsCard(insights: Insights) {
    InsightCard {
        CardTitle(stringResource(R.string.insights_top_tags))
        if (insights.topTags.isEmpty()) {
            Text(
                stringResource(R.string.insights_no_tags),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = Spacing.s),
            )
            return@InsightCard
        }
        val max = insights.topTags.maxOf { it.count }.toFloat()
        val bar = MaterialTheme.colorScheme.primary
        Column(Modifier.padding(top = Spacing.l), verticalArrangement = Arrangement.spacedBy(Spacing.m)) {
            insights.topTags.forEach { item ->
                val fraction by animateFloatAsState(item.count / max, MaterialTheme.motionScheme.slowSpatialSpec(), label = "tagBar")
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        Modifier.size(36.dp).background(MaterialTheme.colorScheme.secondaryContainer, CircleShape),
                        contentAlignment = Alignment.Center,
                    ) {
                        DakilioIcon(symbolForKey(item.tag.iconKey), contentDescription = null, tint = MaterialTheme.colorScheme.onSecondaryContainer, modifier = Modifier.size(20.dp))
                    }
                    Column(Modifier.padding(start = Spacing.m).weight(1f)) {
                        Text(item.tag.label, style = MaterialTheme.typography.labelLarge)
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            // Single-series bar: 10dp thick, square at the baseline, 4dp rounded data end.
                            Canvas(Modifier.weight(1f).height(10.dp).padding(top = 2.dp)) {
                                val w = size.width * fraction
                                val r = 4.dp.toPx()
                                drawPath(
                                    Path().apply {
                                        addRoundRect(
                                            androidx.compose.ui.geometry.RoundRect(
                                                0f, 0f, w, size.height,
                                                topRightCornerRadius = CornerRadius(r),
                                                bottomRightCornerRadius = CornerRadius(r),
                                            ),
                                        )
                                    },
                                    bar,
                                )
                            }
                            Text(
                                item.count.toString(),
                                style = MaterialTheme.typography.labelLarge,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(start = Spacing.s).widthIn(min = 24.dp),
                            )
                        }
                    }
                }
            }
        }
    }
}
