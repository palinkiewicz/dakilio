package pl.dakil.dakilio.feature.timeline.impl

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import java.time.LocalDate
import java.time.YearMonth
import pl.dakil.dakilio.core.designsystem.component.pressScale
import pl.dakil.dakilio.core.designsystem.component.rememberHaptics
import pl.dakil.dakilio.core.designsystem.icon.DakilioIcon
import pl.dakil.dakilio.core.designsystem.icon.DakilioSymbols
import pl.dakil.dakilio.core.designsystem.theme.Spacing
import pl.dakil.dakilio.core.model.Mood
import pl.dakil.dakilio.core.model.primaryOf
import pl.dakil.dakilio.core.ui.DakilioFormats
import pl.dakil.dakilio.core.ui.mood.moodColors

/**
 * The floating month pill over the timeline: ▲ newer month · the current month (opens the picker) ·
 * ▼ older month. The arrows are disabled at the timeline's ends.
 */
@Composable
internal fun MonthNavigator(
    month: YearMonth,
    /** The adjacent sections' months; `null` at the timeline's ends disables the arrow. */
    newer: YearMonth?,
    older: YearMonth?,
    formats: DakilioFormats,
    onJump: (YearMonth) -> Unit,
    onPick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val haptics = rememberHaptics()
    val label = formats.monthYear(month.atDay(1))
    Surface(
        modifier = modifier,
        shape = CircleShape,
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        contentColor = MaterialTheme.colorScheme.onSurface,
        shadowElevation = 3.dp,
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(
                onClick = {
                    haptics.tick()
                    newer?.let(onJump)
                },
                enabled = newer != null,
            ) {
                DakilioIcon(
                    DakilioSymbols.ExpandMore,
                    contentDescription = stringResource(R.string.timeline_month_newer),
                    modifier = Modifier.rotate(180f),
                )
            }
            val interaction = remember { MutableInteractionSource() }
            val description = stringResource(R.string.timeline_month_choose, label)
            val spatial = MaterialTheme.motionScheme.fastSpatialSpec<IntOffset>()
            val effects = MaterialTheme.motionScheme.fastEffectsSpec<Float>()
            // As wide as the year's longest month name, so the arrows never shift between months.
            val labelStyle = MaterialTheme.typography.titleSmallEmphasized
            val measurer = rememberTextMeasurer()
            val density = LocalDensity.current
            val labelWidth = remember(month.year, labelStyle, formats, density) {
                val widest = (1..12).maxOf { number ->
                    measurer.measure(formats.monthYear(LocalDate.of(month.year, number, 1)), labelStyle, maxLines = 1).size.width
                }
                with(density) { widest.toDp() } + Spacing.xs * 2
            }
            AnimatedContent(
                targetState = month,
                transitionSpec = {
                    // Newer months come from above, like the list they mirror.
                    val newer = targetState > initialState
                    (slideInVertically(spatial) { if (newer) -it else it } + fadeIn(effects))
                        .togetherWith(slideOutVertically(spatial) { if (newer) it else -it } + fadeOut(effects))
                        .using(SizeTransform(clip = true))
                },
                contentAlignment = Alignment.Center,
                label = "month",
                modifier = Modifier
                    .width(labelWidth)
                    .pressScale(interaction, 0.92f)
                    .clip(CircleShape)
                    .clickable(interactionSource = interaction, indication = null, role = Role.Button) {
                        haptics.tick()
                        onPick()
                    }
                    .semantics { contentDescription = description },
            ) { shown ->
                Text(
                    formats.monthYear(shown.atDay(1)),
                    style = labelStyle,
                    maxLines = 1,
                    softWrap = false,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth().padding(vertical = Spacing.s),
                )
            }
            IconButton(
                onClick = {
                    haptics.tick()
                    older?.let(onJump)
                },
                enabled = older != null,
            ) {
                DakilioIcon(DakilioSymbols.ExpandMore, contentDescription = stringResource(R.string.timeline_month_older))
            }
        }
    }
}

/**
 * Picks a month to jump to: a year switcher over a 4×3 grid. Each month shows a dot in its average
 * mood's color; months without entries are dimmed, and months outside the timeline are disabled.
 */
@Composable
internal fun MonthPickerSheet(
    current: YearMonth,
    firstDay: LocalDate,
    lastDay: LocalDate,
    months: Map<YearMonth, MonthSummary>,
    moods: List<Mood>,
    formats: DakilioFormats,
    onPick: (YearMonth) -> Unit,
    onDismiss: () -> Unit,
) {
    val haptics = rememberHaptics()
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val firstMonth = YearMonth.from(firstDay)
    val lastMonth = YearMonth.from(lastDay)
    var year by rememberSaveable { mutableIntStateOf(current.year) }
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState) {
        Column(Modifier.fillMaxWidth().padding(horizontal = Spacing.gutter).padding(bottom = Spacing.xl)) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                IconButton(
                    onClick = {
                        haptics.tick()
                        year--
                    },
                    enabled = year > firstMonth.year,
                ) {
                    DakilioIcon(
                        DakilioSymbols.ChevronRight,
                        contentDescription = stringResource(R.string.timeline_month_prev_year),
                        modifier = Modifier.rotate(180f),
                    )
                }
                val spatial = MaterialTheme.motionScheme.fastSpatialSpec<IntOffset>()
                val effects = MaterialTheme.motionScheme.fastEffectsSpec<Float>()
                AnimatedContent(
                    targetState = year,
                    transitionSpec = {
                        val forward = targetState > initialState
                        (slideInHorizontally(spatial) { if (forward) it / 2 else -it / 2 } + fadeIn(effects))
                            .togetherWith(slideOutHorizontally(spatial) { if (forward) -it / 2 else it / 2 } + fadeOut(effects))
                    },
                    contentAlignment = Alignment.Center,
                    label = "year",
                    modifier = Modifier.weight(1f),
                ) { shown ->
                    Text(
                        shown.toString(),
                        style = MaterialTheme.typography.titleLargeEmphasized,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
                IconButton(
                    onClick = {
                        haptics.tick()
                        year++
                    },
                    enabled = year < lastMonth.year,
                ) {
                    DakilioIcon(DakilioSymbols.ChevronRight, contentDescription = stringResource(R.string.timeline_month_next_year))
                }
            }
            Column(
                modifier = Modifier.padding(top = Spacing.m),
                verticalArrangement = Arrangement.spacedBy(Spacing.s),
            ) {
                (1..12).chunked(4).forEach { row ->
                    Row(horizontalArrangement = Arrangement.spacedBy(Spacing.s)) {
                        row.forEach { number ->
                            val month = YearMonth.of(year, number)
                            MonthCell(
                                month = month,
                                summary = months[month],
                                moods = moods,
                                selected = month == current,
                                enabled = month in firstMonth..lastMonth,
                                formats = formats,
                                onClick = {
                                    haptics.confirm()
                                    onPick(month)
                                },
                                modifier = Modifier.weight(1f),
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun MonthCell(
    month: YearMonth,
    summary: MonthSummary?,
    moods: List<Mood>,
    selected: Boolean,
    enabled: Boolean,
    formats: DakilioFormats,
    onClick: () -> Unit,
    modifier: Modifier,
) {
    val interaction = remember { MutableInteractionSource() }
    val hasEntries = (summary?.entries ?: 0) > 0
    val colors = MaterialTheme.colorScheme
    val container = if (selected) colors.primary else colors.surfaceContainerHigh
    val content = when {
        selected -> colors.onPrimary
        hasEntries -> colors.onSurface
        else -> colors.onSurfaceVariant
    }
    val name = formats.monthYear(month.atDay(1))
    val description = if (hasEntries || !enabled) name else stringResource(R.string.timeline_month_no_entries, name)
    Surface(
        onClick = onClick,
        enabled = enabled,
        shape = RoundedCornerShape(16.dp),
        color = container,
        contentColor = content,
        interactionSource = interaction,
        modifier = modifier
            .pressScale(interaction, 0.92f)
            .alpha(if (enabled) 1f else 0.38f)
            .semantics {
                contentDescription = description
                this.selected = selected
            },
    ) {
        Column(
            modifier = Modifier.padding(vertical = Spacing.m),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                formats.monthShort(month),
                style = if (selected) MaterialTheme.typography.labelLargeEmphasized else MaterialTheme.typography.labelLarge,
                maxLines = 1,
            )
            Box(Modifier.padding(top = Spacing.xs).size(6.dp)) {
                val level = summary?.averageLevel
                val mood = level?.let(moods::primaryOf)
                if (mood != null) {
                    val dot = moodColors(mood).container
                    Box(
                        Modifier
                            .size(6.dp)
                            .background(if (selected) colors.onPrimary else dot, CircleShape),
                    )
                } else if (hasEntries) {
                    Box(Modifier.size(6.dp).background(content.copy(alpha = 0.6f), CircleShape))
                }
            }
        }
    }
}

/** The vertical room the pill takes over the list, so jumps land just below it. */
internal val MonthNavigatorClearance = 48.dp + Spacing.s
