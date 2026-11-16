package pl.dakil.dakilio.feature.insights.impl.overview

import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialShapes
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.layout
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.graphics.shapes.RoundedPolygon
import java.time.LocalDate
import pl.dakil.dakilio.core.designsystem.component.ShapedIcon
import pl.dakil.dakilio.core.designsystem.component.entrance
import pl.dakil.dakilio.core.designsystem.component.pressScale
import pl.dakil.dakilio.core.designsystem.icon.DakilioSymbol
import pl.dakil.dakilio.core.designsystem.icon.DakilioSymbols
import pl.dakil.dakilio.core.designsystem.icon.symbolForKey
import pl.dakil.dakilio.core.designsystem.theme.Spacing
import pl.dakil.dakilio.core.model.Mood
import pl.dakil.dakilio.core.ui.DakilioFormats
import pl.dakil.dakilio.core.ui.mood.MoodBadge
import pl.dakil.dakilio.feature.insights.impl.R
import pl.dakil.dakilio.feature.insights.impl.logic.Highlight
import pl.dakil.dakilio.feature.insights.impl.logic.nearestMood
import pl.dakil.dakilio.feature.insights.impl.rangeLabel

/** Lets a row reach the screen's edges past the list's side padding, so it scrolls edge to edge. */
internal fun Modifier.bleed(horizontal: Dp): Modifier = layout { measurable, constraints ->
    val extra = horizontal.roundToPx() * 2
    val placeable = measurable.measure(constraints.copy(minWidth = constraints.minWidth + extra, maxWidth = constraints.maxWidth + extra))
    layout(placeable.width - extra, placeable.height) { placeable.place(-extra / 2, 0) }
}

/**
 * The period's highlights as a row of cards: a big figure, one sentence, and a tap to the screen
 * behind it.
 *
 * @param comparisonLabel what a mood change is against ("October 2026").
 * @param onOpen opens a highlight's screen; `null` for one that has none.
 */
@Composable
internal fun HighlightsRow(
    highlights: List<Highlight>,
    moods: List<Mood>,
    today: LocalDate,
    formats: DakilioFormats,
    comparisonLabel: String?,
    onOpen: (Highlight) -> (() -> Unit)?,
) {
    Column {
        Text(
            stringResource(R.string.insights_highlights),
            style = MaterialTheme.typography.titleMediumEmphasized,
            modifier = Modifier.padding(start = Spacing.xs, bottom = Spacing.s),
        )
        LazyRow(
            modifier = Modifier.bleed(Spacing.gutter),
            contentPadding = PaddingValues(horizontal = Spacing.gutter),
            horizontalArrangement = Arrangement.spacedBy(Spacing.s),
        ) {
            itemsIndexed(highlights, key = { _, it -> it.subject }) { index, highlight ->
                HighlightCard(highlight, moods, today, formats, comparisonLabel, onOpen(highlight), Modifier.entrance(index).animateItem())
            }
        }
    }
}

/** How a highlight looks: its colors, badge, figure and sentence. */
private class HighlightLook(
    val container: Color,
    val content: Color,
    val figure: String,
    val text: String,
    val badge: @Composable () -> Unit,
)

@Composable
private fun shaped(symbol: DakilioSymbol, polygon: RoundedPolygon, tint: Color): @Composable () -> Unit = {
    ShapedIcon(symbol, polygon = polygon, size = 44.dp, containerColor = MaterialTheme.colorScheme.surface, contentColor = tint)
}

@Composable
private fun look(highlight: Highlight, moods: List<Mood>, today: LocalDate, formats: DakilioFormats, comparisonLabel: String?): HighlightLook {
    val colors = MaterialTheme.colorScheme
    return when (highlight) {
        is Highlight.TagBrighter -> HighlightLook(
            colors.secondaryContainer, colors.onSecondaryContainer,
            formats.signedDecimal(highlight.lift),
            stringResource(R.string.insights_hl_tag_brighter, highlight.tag.label),
            shaped(symbolForKey(highlight.tag.iconKey), MaterialShapes.Sunny, colors.onSecondaryContainer),
        )
        is Highlight.TagHeavier -> HighlightLook(
            colors.secondaryContainer, colors.onSecondaryContainer,
            formats.signedDecimal(highlight.lift),
            stringResource(R.string.insights_hl_tag_heavier, highlight.tag.label),
            shaped(symbolForKey(highlight.tag.iconKey), MaterialShapes.Cookie4Sided, colors.onSecondaryContainer),
        )
        is Highlight.BestWeekday -> HighlightLook(
            colors.primaryContainer, colors.onPrimaryContainer,
            formats.weekday(highlight.day),
            stringResource(R.string.insights_hl_best_weekday),
        ) { nearestMood(moods, highlight.average)?.let { MoodBadge(it, size = 44.dp, active = true) } }
        is Highlight.MoodChange -> HighlightLook(
            colors.primaryContainer, colors.onPrimaryContainer,
            formats.signedDecimal(highlight.change),
            stringResource(if (highlight.change > 0) R.string.insights_hl_brighter_than else R.string.insights_hl_heavier_than, comparisonLabel.orEmpty()),
            shaped(if (highlight.change > 0) DakilioSymbols.TrendingUp else DakilioSymbols.TrendingDown, MaterialShapes.Cookie9Sided, colors.onPrimaryContainer),
        )
        is Highlight.BestMonthYet -> HighlightLook(
            colors.primaryContainer, colors.onPrimaryContainer,
            formats.decimal(highlight.average),
            stringResource(R.string.insights_hl_best_month),
        ) { nearestMood(moods, highlight.average)?.let { MoodBadge(it, size = 44.dp, active = true) } }
        is Highlight.StreakRecord -> HighlightLook(
            colors.tertiaryContainer, colors.onTertiaryContainer,
            formats.count(highlight.days),
            pluralStringResource(R.plurals.insights_hl_streak, highlight.days),
            shaped(DakilioSymbols.LocalFireDepartment, MaterialShapes.Cookie12Sided, colors.onTertiaryContainer),
        )
        is Highlight.BrightRun -> HighlightLook(
            colors.tertiaryContainer, colors.onTertiaryContainer,
            formats.count(highlight.run.days),
            pluralStringResource(R.plurals.insights_hl_bright_run, highlight.run.days, rangeLabel(highlight.run, today, formats)),
            shaped(DakilioSymbols.AutoAwesome, MaterialShapes.Flower, colors.onTertiaryContainer),
        )
        is Highlight.HabitRun -> HighlightLook(
            colors.tertiaryContainer, colors.onTertiaryContainer,
            formats.count(highlight.weeks),
            pluralStringResource(R.plurals.insights_hl_habit_run, highlight.weeks, highlight.habit.tag.label),
            shaped(symbolForKey(highlight.habit.tag.iconKey), MaterialShapes.Clover4Leaf, colors.onTertiaryContainer),
        )
        is Highlight.WordsWritten -> HighlightLook(
            colors.secondaryContainer, colors.onSecondaryContainer,
            formats.count(highlight.words),
            pluralStringResource(R.plurals.insights_hl_words, highlight.words),
            shaped(DakilioSymbols.EditNote, MaterialShapes.Cookie6Sided, colors.onSecondaryContainer),
        )
    }
}

private val CardWidth = 196.dp
private val CardHeight = 176.dp

@Composable
private fun HighlightCard(
    highlight: Highlight,
    moods: List<Mood>,
    today: LocalDate,
    formats: DakilioFormats,
    comparisonLabel: String?,
    onClick: (() -> Unit)?,
    modifier: Modifier,
) {
    val look = look(highlight, moods, today, formats, comparisonLabel)
    val interaction = remember { MutableInteractionSource() }
    val description = look.figure + " " + look.text
    val content: @Composable () -> Unit = {
        Column(Modifier.padding(Spacing.l).fillMaxHeight()) {
            look.badge()
            Spacer(Modifier.weight(1f))
            Text(look.figure, style = MaterialTheme.typography.headlineSmallEmphasized, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(look.text, style = MaterialTheme.typography.bodyMedium, maxLines = 3, overflow = TextOverflow.Ellipsis)
        }
    }
    val size = modifier.width(CardWidth).height(CardHeight)
    val shape = RoundedCornerShape(28.dp)
    if (onClick == null) {
        Surface(
            shape = shape,
            color = look.container,
            contentColor = look.content,
            modifier = size.semantics(mergeDescendants = true) { contentDescription = description },
            content = content,
        )
    } else {
        Surface(
            onClick = onClick,
            shape = shape,
            color = look.container,
            contentColor = look.content,
            interactionSource = interaction,
            modifier = size.pressScale(interaction).semantics(mergeDescendants = true) {
                contentDescription = description
                role = Role.Button
            },
            content = content,
        )
    }
}
