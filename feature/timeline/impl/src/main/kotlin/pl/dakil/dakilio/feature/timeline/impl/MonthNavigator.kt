package pl.dakil.dakilio.feature.timeline.impl

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.coerceAtLeast
import androidx.compose.ui.unit.dp
import java.time.YearMonth
import pl.dakil.dakilio.core.designsystem.component.pressScale
import pl.dakil.dakilio.core.designsystem.component.rememberHaptics
import pl.dakil.dakilio.core.designsystem.icon.DakilioIcon
import pl.dakil.dakilio.core.designsystem.icon.DakilioSymbols
import pl.dakil.dakilio.core.designsystem.theme.Spacing
import pl.dakil.dakilio.core.ui.DakilioFormats

/**
 * The month navigator: ▲ newer month · the current month (opens the picker) · ▼ older month, with no
 * background of its own (it blends into the app bar; [FloatingMonthNavigator] puts it on a pill).
 * The label is one line, "November 2026". [canCompact] (next to the diary dropdown) lets it stack the
 * short month name over its year when one line doesn't fit. Without [fill], the label is as wide as the
 * year's widest month, so the arrows sit right next to it and never shift; with [fill] the label takes
 * every bit of the width it's given, and the arrows sit at its ends. The arrows are disabled at the
 * timeline's ends.
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
    canCompact: Boolean = false,
    fill: Boolean = false,
) {
    val widths = rememberMonthLabelWidths(month.year, formats)
    if (fill) {
        BoxWithConstraints(modifier) {
            val room = maxWidth - ARROW_WIDTH * 2
            val compact = canCompact && widths.full > room
            MonthNavigatorRow(
                month, newer, older, formats, onJump, onPick,
                compact = compact,
                labelWidth = room.coerceAtLeast(if (compact) widths.compact else widths.full),
            )
        }
    } else {
        MonthNavigatorRow(
            month, newer, older, formats, onJump, onPick,
            compact = canCompact,
            labelWidth = if (canCompact) widths.compact else widths.full,
            modifier = modifier,
        )
    }
}

/** The widest month label of a year (padding included): one line ([full]) and stacked ([compact]). */
internal class MonthLabelWidths(val full: Dp, val compact: Dp)

/** The narrowest [MonthNavigator] that still fits its label, one line or (with [canCompact]) stacked. */
internal fun MonthLabelWidths.minNavigatorWidth(canCompact: Boolean): Dp =
    (if (canCompact) compact else full) + ARROW_WIDTH * 2

@Composable
internal fun rememberMonthLabelWidths(year: Int, formats: DakilioFormats): MonthLabelWidths {
    val fullStyle = MaterialTheme.typography.titleSmallEmphasized
    val compactStyle = MaterialTheme.typography.labelLargeEmphasized
    val measurer = rememberTextMeasurer()
    val density = LocalDensity.current
    return remember(year, fullStyle, compactStyle, formats, density) {
        fun widest(style: TextStyle, text: (YearMonth) -> String): Dp {
            val px = (1..12).maxOf { number ->
                measurer.measure(text(YearMonth.of(year, number)), style, maxLines = 1).size.width
            }
            return with(density) { px.toDp() } + LABEL_PADDING * 2
        }
        MonthLabelWidths(
            full = widest(fullStyle) { formats.monthYear(it.atDay(1)) },
            compact = widest(compactStyle) { formats.monthShort(it) },
        )
    }
}

@Composable
private fun MonthNavigatorRow(
    month: YearMonth,
    newer: YearMonth?,
    older: YearMonth?,
    formats: DakilioFormats,
    onJump: (YearMonth) -> Unit,
    onPick: () -> Unit,
    compact: Boolean,
    labelWidth: Dp,
    modifier: Modifier = Modifier,
) {
    val haptics = rememberHaptics()
    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        MonthArrow(
            up = true,
            enabled = newer != null,
            description = stringResource(R.string.timeline_month_newer),
            onClick = {
                haptics.tick()
                newer?.let(onJump)
            },
        )
        val interaction = remember { MutableInteractionSource() }
        val description = stringResource(R.string.timeline_month_choose, formats.monthYear(month.atDay(1)))
        val spatial = MaterialTheme.motionScheme.fastSpatialSpec<IntOffset>()
        val effects = MaterialTheme.motionScheme.fastEffectsSpec<Float>()
        val monthStyle = if (compact) MaterialTheme.typography.labelLargeEmphasized else MaterialTheme.typography.titleSmallEmphasized
        val yearStyle = MaterialTheme.typography.labelSmall
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
                .height(NAV_HEIGHT)
                .pressScale(interaction, 0.92f)
                .clip(CircleShape)
                .clickable(interactionSource = interaction, role = Role.Button) {
                    haptics.tick()
                    onPick()
                }
                .semantics { contentDescription = description },
        ) { shown ->
            Column(
                modifier = Modifier.fillMaxSize(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) {
                if (compact) {
                    Text(formats.monthShort(shown), style = monthStyle, maxLines = 1, softWrap = false, textAlign = TextAlign.Center)
                    Text(
                        shown.year.toString(),
                        style = yearStyle,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        softWrap = false,
                        textAlign = TextAlign.Center,
                    )
                } else {
                    Text(formats.monthYear(shown.atDay(1)), style = monthStyle, maxLines = 1, softWrap = false, textAlign = TextAlign.Center)
                }
            }
        }
        MonthArrow(
            up = false,
            enabled = older != null,
            description = stringResource(R.string.timeline_month_older),
            onClick = {
                haptics.tick()
                older?.let(onJump)
            },
        )
    }
}

private val NAV_HEIGHT = 40.dp
private val ARROW_WIDTH = 40.dp

/** [MonthNavigator] on a raised pill, floating over the timeline once it's scrolled past today. */
@Composable
internal fun FloatingMonthNavigator(
    month: YearMonth,
    newer: YearMonth?,
    older: YearMonth?,
    formats: DakilioFormats,
    onJump: (YearMonth) -> Unit,
    onPick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier,
        shape = CircleShape,
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        contentColor = MaterialTheme.colorScheme.onSurface,
        shadowElevation = 3.dp,
    ) {
        MonthNavigator(month, newer, older, formats, onJump, onPick, Modifier.padding(horizontal = 2.dp))
    }
}

/** The vertical room the floating pill takes over the list, so jumps land just below it. */
internal val MonthNavigatorClearance = NAV_HEIGHT + Spacing.s * 2

private val LABEL_PADDING = 4.dp

@Composable
private fun MonthArrow(up: Boolean, enabled: Boolean, description: String, onClick: () -> Unit) {
    val interaction = remember { MutableInteractionSource() }
    val tint = MaterialTheme.colorScheme.onSurfaceVariant
    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .size(width = ARROW_WIDTH, height = NAV_HEIGHT)
            .pressScale(interaction, 0.88f)
            .clip(CircleShape)
            .clickable(interactionSource = interaction, enabled = enabled, role = Role.Button, onClick = onClick)
            .semantics { contentDescription = description },
    ) {
        DakilioIcon(
            DakilioSymbols.ExpandMore,
            contentDescription = null,
            tint = if (enabled) tint else tint.copy(alpha = 0.38f),
            modifier = Modifier.size(26.dp).then(if (up) Modifier.rotate(180f) else Modifier),
        )
    }
}
