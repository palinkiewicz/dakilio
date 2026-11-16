package pl.dakil.dakilio.feature.insights.impl.chart

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialShapes
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.YearMonth
import java.time.temporal.TemporalAdjusters
import pl.dakil.dakilio.core.designsystem.component.PolygonShape
import pl.dakil.dakilio.core.designsystem.theme.Spacing
import pl.dakil.dakilio.core.model.DateRange
import pl.dakil.dakilio.core.model.MonthSummary
import pl.dakil.dakilio.core.model.Mood
import pl.dakil.dakilio.core.model.primaryOf
import pl.dakil.dakilio.core.ui.DakilioFormats
import pl.dakil.dakilio.core.ui.mood.MoodBadge
import pl.dakil.dakilio.core.ui.mood.NoMoodBadge
import pl.dakil.dakilio.core.ui.mood.moodColors
import pl.dakil.dakilio.core.ui.mood.moodGlyph
import pl.dakil.dakilio.core.ui.weekdaysFrom
import pl.dakil.dakilio.feature.insights.impl.logic.DayCell

/**
 * A few days as tiles (a week, or a short custom range): the weekday, the day's face and how many
 * entries it has.
 */
@Composable
internal fun DayStrip(
    range: DateRange,
    cells: Map<LocalDate, DayCell>,
    moods: List<Mood>,
    today: LocalDate,
    selected: LocalDate?,
    onSelect: (LocalDate) -> Unit,
    formats: DakilioFormats,
    describe: @Composable (LocalDate) -> String,
    modifier: Modifier = Modifier,
) {
    val colors = MaterialTheme.colorScheme
    Row(modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(Spacing.xs)) {
        range.dates().forEach { date ->
            val cell = cells[date]
            val isSelected = date == selected
            val future = date.isAfter(today)
            val description = describe(date)
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(Spacing.xs),
                modifier = Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(16.dp))
                    .background(if (isSelected) colors.surfaceContainerHighest else colors.surfaceContainer)
                    .clickable(enabled = !future) { onSelect(date) }
                    .alpha(if (future) 0.38f else 1f)
                    .padding(vertical = Spacing.s)
                    .clearAndSetSemantics {
                        contentDescription = description
                        this.selected = isSelected
                    },
            ) {
                Text(
                    formats.weekdayShort(date.dayOfWeek),
                    style = if (date == today) MaterialTheme.typography.labelSmallEmphasized else MaterialTheme.typography.labelSmall,
                    color = if (date == today) colors.primary else colors.onSurfaceVariant,
                    maxLines = 1,
                )
                val mood = cell?.level?.let(moods::primaryOf)
                when {
                    mood != null -> MoodBadge(mood, size = 32.dp, active = isSelected)
                    cell != null -> NoMoodBadge(size = 32.dp)
                    else -> Box(Modifier.size(32.dp).padding(4.dp).border(1.5.dp, colors.outlineVariant, CircleShape))
                }
                Text(
                    date.dayOfMonth.toString(),
                    style = MaterialTheme.typography.labelMedium,
                    color = colors.onSurface,
                )
            }
        }
    }
}

/**
 * A calendar of [range] (a month, or a few weeks): a row per week from [firstDay]. A day with a mood
 * is its level's shape in the level's color, so the shape tells levels apart where the color can't.
 */
@Composable
internal fun DaysGrid(
    range: DateRange,
    cells: Map<LocalDate, DayCell>,
    moods: List<Mood>,
    firstDay: DayOfWeek,
    today: LocalDate,
    selected: LocalDate?,
    onSelect: (LocalDate) -> Unit,
    formats: DakilioFormats,
    describe: @Composable (LocalDate) -> String,
    modifier: Modifier = Modifier,
) {
    val colors = MaterialTheme.colorScheme
    val shapes = remember(moods) { moods.associate { it.level to PolygonShape(moodGlyph(it).rest) } }
    val neutral = remember { PolygonShape(MaterialShapes.Cookie4Sided) }
    val start = range.from.with(TemporalAdjusters.previousOrSame(firstDay))
    val weeks = generateSequence(start) { it.plusWeeks(1) }.takeWhile { !it.isAfter(range.to) }.toList()
    Column(modifier.fillMaxWidth().widthIn(max = 420.dp), verticalArrangement = Arrangement.spacedBy(Spacing.xxs)) {
        Row(Modifier.fillMaxWidth()) {
            weekdaysFrom(firstDay).forEach { weekday ->
                Text(
                    formats.weekdayNarrow(weekday),
                    style = MaterialTheme.typography.labelSmall,
                    color = colors.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.weight(1f).clearAndSetSemantics { },
                )
            }
        }
        weeks.forEach { weekStart ->
            Row(Modifier.fillMaxWidth()) {
                repeat(7) { offset ->
                    val date = weekStart.plusDays(offset.toLong())
                    if (date !in range) {
                        Spacer(Modifier.weight(1f))
                        return@repeat
                    }
                    val cell = cells[date]
                    val mood = cell?.level?.let(moods::primaryOf)
                    val future = date.isAfter(today)
                    val isSelected = date == selected
                    val description = describe(date)
                    Box(
                        contentAlignment = Alignment.Center,
                        modifier = Modifier
                            .weight(1f)
                            .aspectRatio(1f)
                            .clip(CircleShape)
                            .clickable(enabled = !future) { onSelect(date) }
                            .then(if (isSelected) Modifier.border(2.dp, colors.onSurface, CircleShape) else Modifier)
                            .alpha(if (future) 0.38f else 1f)
                            .clearAndSetSemantics {
                                contentDescription = description
                                role = Role.Button
                                this.selected = isSelected
                            },
                    ) {
                        when {
                            mood != null -> Box(Modifier.fillMaxSize(0.8f).background(moodColors(mood).container, shapes.getValue(mood.level)))
                            cell != null -> Box(Modifier.fillMaxSize(0.8f).background(colors.surfaceContainerHighest, neutral))
                        }
                        Text(
                            date.dayOfMonth.toString(),
                            style = if (date == today) MaterialTheme.typography.labelLargeEmphasized else MaterialTheme.typography.labelMedium,
                            color = when {
                                mood != null -> moodColors(mood).onContainer
                                cell != null -> colors.onSurface
                                date == today -> colors.primary
                                else -> colors.onSurfaceVariant
                            },
                        )
                    }
                }
            }
        }
    }
}

private val PixelRow = 10.dp
private val PixelGutter = 20.dp

/**
 * Up to a year at a glance: a column per month, a small cell per day, in the day's level color.
 * A tap or a drag across it picks the day under the finger. Days after [today] are only hinted at.
 */
@Composable
internal fun PixelGrid(
    months: List<YearMonth>,
    range: DateRange,
    cells: Map<LocalDate, DayCell>,
    moods: List<Mood>,
    today: LocalDate,
    selected: LocalDate?,
    onSelect: (LocalDate) -> Unit,
    formats: DakilioFormats,
    description: String,
    modifier: Modifier = Modifier,
) {
    val colors = MaterialTheme.colorScheme
    val levelFill = moods.associate { it.level to moodColors(it).container }
    val noMood = colors.outlineVariant
    val empty = colors.surfaceContainerHighest
    // Days still to come are only hinted at.
    val upcoming = colors.surfaceContainerHighest.copy(alpha = 0.38f)
    val ring = colors.onSurface
    Column(modifier.fillMaxWidth().clearAndSetSemantics { contentDescription = description }) {
        Row(Modifier.fillMaxWidth().padding(start = PixelGutter)) {
            months.forEach { month ->
                Text(
                    formats.monthShort(month).take(1),
                    style = MaterialTheme.typography.labelSmall,
                    color = colors.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.weight(1f),
                )
            }
        }
        Row(Modifier.fillMaxWidth().padding(top = Spacing.xs)) {
            Box(Modifier.width(PixelGutter).height(PixelRow * 31)) {
                listOf(1, 10, 20, 31).forEach { day ->
                    Text(
                        day.toString(),
                        style = MaterialTheme.typography.labelSmall,
                        color = colors.onSurfaceVariant,
                        modifier = Modifier.offset(y = PixelRow * (day - 1) - 3.dp),
                    )
                }
            }
            Canvas(
                Modifier.weight(1f).height(PixelRow * 31).chartScrub(anyDirection = true) { position, size ->
                    val (column, row) = cellAt(position.x, position.y, size.width.toFloat(), size.height.toFloat(), months.size, 31)
                    val month = months[column]
                    val date = month.atDay((row + 1).coerceAtMost(month.lengthOfMonth()))
                    if (date in range && !date.isAfter(today)) onSelect(date)
                },
            ) {
                val columnWidth = size.width / months.size
                val rowHeight = size.height / 31
                val gap = 2.dp.toPx()
                val cellSize = Size(columnWidth - gap, rowHeight - gap)
                val radius = CornerRadius(2.5.dp.toPx())
                months.forEachIndexed { column, month ->
                    for (day in 1..month.lengthOfMonth()) {
                        val date = month.atDay(day)
                        if (date !in range) continue
                        val cell = cells[date]
                        val fill = when {
                            date.isAfter(today) -> upcoming
                            cell == null -> empty
                            cell.level == null -> noMood
                            else -> levelFill[cell.level] ?: noMood
                        }
                        val topLeft = Offset(column * columnWidth + gap / 2, (day - 1) * rowHeight + gap / 2)
                        drawRoundRect(fill, topLeft, cellSize, radius)
                        if (date == selected) drawRoundRect(ring, topLeft, cellSize, radius, style = Stroke(2.dp.toPx()))
                    }
                }
            }
        }
    }
}

/**
 * Many years at a glance: a row per year, a cell per month in its average level's color. A cell
 * opens that month, the year's label that year.
 */
@Composable
internal fun YearsGrid(
    range: DateRange,
    months: Map<YearMonth, MonthSummary>,
    moods: List<Mood>,
    onMonth: (YearMonth) -> Unit,
    onYear: (Int) -> Unit,
    formats: DakilioFormats,
    describe: @Composable (YearMonth, MonthSummary?) -> String,
    modifier: Modifier = Modifier,
) {
    val colors = MaterialTheme.colorScheme
    val first = YearMonth.from(range.from)
    val last = YearMonth.from(range.to)
    val yearWidth = 44.dp
    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
        Row(Modifier.fillMaxWidth().padding(start = yearWidth)) {
            (1..12).forEach { number ->
                Text(
                    formats.monthShort(YearMonth.of(last.year, number)).take(1),
                    style = MaterialTheme.typography.labelSmall,
                    color = colors.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.weight(1f).clearAndSetSemantics { },
                )
            }
        }
        // Newest year first, like the timeline.
        (last.year downTo first.year).forEach { year ->
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text(
                    year.toString(),
                    style = MaterialTheme.typography.labelLarge,
                    color = colors.onSurface,
                    modifier = Modifier.width(yearWidth).clip(RoundedCornerShape(8.dp)).clickable { onYear(year) }.padding(vertical = Spacing.xs),
                )
                (1..12).forEach { number ->
                    val month = YearMonth.of(year, number)
                    val inRange = month in first..last
                    val summary = months[month]
                    val mood = summary?.averageLevel?.let(moods::primaryOf)
                    val fill = when {
                        !inRange -> colors.surfaceContainer
                        mood != null -> moodColors(mood).container
                        summary != null -> colors.outlineVariant
                        else -> colors.surfaceContainerHighest
                    }
                    val description = describe(month, summary)
                    Box(
                        Modifier
                            .weight(1f)
                            .padding(1.dp)
                            .aspectRatio(1f)
                            .clip(RoundedCornerShape(6.dp))
                            .background(fill)
                            .clickable(enabled = inRange) { onMonth(month) }
                            .semantics { contentDescription = description },
                    )
                }
            }
        }
    }
}
