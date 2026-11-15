package pl.dakil.dakilio.feature.insights.impl

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import java.time.LocalTime
import pl.dakil.dakilio.core.designsystem.component.rememberHaptics
import pl.dakil.dakilio.core.designsystem.icon.DakilioIcon
import pl.dakil.dakilio.core.designsystem.theme.Spacing
import pl.dakil.dakilio.core.model.MOOD_LEVELS
import pl.dakil.dakilio.core.model.Mood
import pl.dakil.dakilio.core.ui.DakilioFormats
import pl.dakil.dakilio.core.ui.hintOrNull
import pl.dakil.dakilio.core.ui.mood.MoodBadge

private val BarAreaHeight = 96.dp
private val BarWidth = 24.dp

/** How tall a bar for an average mood is, as a share of the plot: the worst level still shows as a stub. */
internal fun moodBarFraction(value: Float): Float = (value * (MOOD_LEVELS - 1) + 1) / MOOD_LEVELS

/**
 * The average mood of each weekday as seven thin columns on one baseline, in one hue. The picked
 * day (the brightest one at first) is the full-strength column and is read out underneath.
 */
@Composable
internal fun WeekdayCard(weekdays: List<WeekdayMood>, moods: List<Mood>, formats: DakilioFormats) {
    InsightCard {
        CardTitle(stringResource(R.string.insights_weekdays), hintOrNull(stringResource(R.string.insights_weekdays_body)))
        val brightest = weekdays.filter { it.averageMood != null }.maxByOrNull { it.averageMood!! }
        if (brightest == null) {
            CardEmptyText(stringResource(R.string.insights_no_mood))
            return@InsightCard
        }
        var selected by remember(weekdays) { mutableStateOf(brightest.day) }
        val haptics = rememberHaptics()
        val colors = MaterialTheme.colorScheme
        Row(Modifier.padding(top = Spacing.l).fillMaxWidth()) {
            weekdays.forEach { weekday ->
                val isSelected = weekday.day == selected
                val caption = periodCaption(formats.weekday(weekday.day), weekday.entries, weekday.averageMood?.let { nearestMood(moods, it) })
                val fraction by animateFloatAsState(
                    weekday.averageMood?.let(::moodBarFraction) ?: 0f,
                    MaterialTheme.motionScheme.slowSpatialSpec(),
                    label = "weekdayBar",
                )
                val fill by animateColorAsState(
                    if (isSelected) colors.primary else colors.primary.copy(alpha = 0.4f),
                    MaterialTheme.motionScheme.defaultEffectsSpec(),
                    label = "weekdayFill",
                )
                Column(
                    Modifier
                        .weight(1f)
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null,
                            role = Role.Tab,
                        ) {
                            if (!isSelected) haptics.tick()
                            selected = weekday.day
                        }
                        .semantics {
                            contentDescription = caption
                            this.selected = isSelected
                        },
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Box(Modifier.height(BarAreaHeight).fillMaxWidth(), contentAlignment = Alignment.BottomCenter) {
                        // Thin, square on the baseline, rounded at the data end.
                        Box(
                            Modifier
                                .widthIn(max = BarWidth)
                                .fillMaxWidth(0.62f)
                                .fillMaxHeight(fraction)
                                .background(fill, RoundedCornerShape(topStart = 4.dp, topEnd = 4.dp)),
                        )
                    }
                    Box(Modifier.fillMaxWidth().height(1.dp).background(colors.outlineVariant))
                    Text(
                        formats.weekdayShort(weekday.day),
                        style = MaterialTheme.typography.labelSmall,
                        color = if (isSelected) colors.onSurface else colors.onSurfaceVariant,
                        maxLines = 1,
                        modifier = Modifier.padding(top = Spacing.xs),
                    )
                }
            }
        }
        weekdays.firstOrNull { it.day == selected }?.let { weekday ->
            Text(
                periodCaption(formats.weekday(weekday.day), weekday.entries, weekday.averageMood?.let { nearestMood(moods, it) }),
                style = MaterialTheme.typography.labelLarge,
                modifier = Modifier.padding(top = Spacing.m),
            )
        }
    }
}

/**
 * How each part of the day tends to feel: a meter per part (the average mood on the scale), with
 * the matching face and the number of entries as its figure.
 */
@Composable
internal fun DayPartCard(dayParts: List<DayPartMood>, moods: List<Mood>, formats: DakilioFormats) {
    InsightCard {
        CardTitle(stringResource(R.string.insights_day_parts))
        if (dayParts.all { it.entries == 0 }) {
            CardEmptyText(stringResource(R.string.insights_no_entries_period))
            return@InsightCard
        }
        val colors = MaterialTheme.colorScheme
        Column(Modifier.padding(top = Spacing.l), verticalArrangement = Arrangement.spacedBy(Spacing.l)) {
            dayParts.forEach { item ->
                val mood = item.averageMood?.let { nearestMood(moods, it) }
                Column {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        DakilioIcon(item.part.symbol, contentDescription = null, tint = colors.onSurfaceVariant, modifier = Modifier.size(20.dp))
                        Column(Modifier.padding(start = Spacing.m).weight(1f)) {
                            Text(
                                stringResource(
                                    when (item.part) {
                                        DayPart.Morning -> R.string.insights_day_part_morning
                                        DayPart.Afternoon -> R.string.insights_day_part_afternoon
                                        DayPart.Evening -> R.string.insights_day_part_evening
                                        DayPart.Night -> R.string.insights_day_part_night
                                    },
                                ),
                                style = MaterialTheme.typography.labelLarge,
                            )
                            Text(
                                stringResource(R.string.insights_day_part_from, formats.time(LocalTime.of(item.part.fromHour, 0))),
                                style = MaterialTheme.typography.labelSmall,
                                color = colors.onSurfaceVariant,
                            )
                        }
                        if (mood != null) MoodBadge(mood, size = 20.dp)
                        Text(
                            pluralStringResource(R.plurals.insights_heat_entries, item.entries, item.entries),
                            style = MaterialTheme.typography.labelMedium,
                            color = colors.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.padding(start = Spacing.s),
                        )
                    }
                    Meter(item.averageMood?.let(::moodBarFraction) ?: 0f, Modifier.padding(top = Spacing.s))
                }
            }
        }
    }
}
