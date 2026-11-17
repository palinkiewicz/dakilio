package pl.dakil.dakilio.feature.insights.impl.recap

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialShapes
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.graphics.shapes.Morph
import java.time.DayOfWeek
import pl.dakil.dakilio.core.designsystem.component.MorphShape
import pl.dakil.dakilio.core.designsystem.component.pressScale
import pl.dakil.dakilio.core.designsystem.component.rememberHaptics
import pl.dakil.dakilio.core.designsystem.theme.Spacing
import pl.dakil.dakilio.core.designsystem.theme.decorativeSpec
import pl.dakil.dakilio.core.ui.DakilioFormats
import pl.dakil.dakilio.core.ui.hintOrNull
import pl.dakil.dakilio.core.ui.mood.moodColors
import pl.dakil.dakilio.core.ui.weekdaysFrom
import pl.dakil.dakilio.feature.insights.impl.R
import pl.dakil.dakilio.feature.insights.impl.logic.DayPart
import pl.dakil.dakilio.feature.insights.impl.logic.InsightsPeriod
import pl.dakil.dakilio.feature.insights.impl.logic.RECAP_COUNTDOWN_FROM
import pl.dakil.dakilio.feature.insights.impl.logic.RecapPage
import pl.dakil.dakilio.feature.insights.impl.logic.TagStat
import pl.dakil.dakilio.feature.insights.impl.logic.brighter
import pl.dakil.dakilio.feature.insights.impl.logic.nearestMood
import pl.dakil.dakilio.feature.insights.impl.overview.TagIcon
import pl.dakil.dakilio.feature.insights.impl.overview.dayPartLabel

/** The cover over a reveal opens from a cookie into a circle as it goes. */
private val coverMorph by lazy { Morph(MaterialShapes.Cookie9Sided, MaterialShapes.Circle) }

/** 0→1 when [revealed] turns true (with a pop), already 1 when it was revealed before. */
@Composable
private fun rememberReveal(revealed: Boolean): () -> Float {
    val progress = remember { Animatable(if (revealed) 1f else 0f) }
    val spec = decorativeSpec(PopSpec)
    LaunchedEffect(revealed) { if (revealed) progress.animateTo(1f, spec) }
    return { progress.value }
}

/**
 * The most used tags. From [RECAP_COUNTDOWN_FROM] tags it's a countdown: #3 and #2 come in, and #1
 * waits under a cover until the next forward tap (or a tap on the cover).
 */
@Composable
internal fun TagsPage(state: InsightsRecapUiState.Ready, steps: RecapSteps, formats: DakilioFormats) {
    val tags = state.recap.overview.tags
    val countdown = tags.size >= RECAP_COUNTDOWN_FROM
    val revealed = !countdown || steps.isRevealed(RecapPage.Tags)
    val haptics = rememberHaptics()
    PageColumn {
        Kicker(stringResource(R.string.insights_recap_tags))
        tags.take(3).forEachIndexed { i, stat ->
            // Counted down: the third comes in first.
            val order = if (countdown) 3 - i else i + 1
            if (countdown && i == 0) {
                CoveredTagRow(stat, revealed, Modifier.recapEntrance(order)) {
                    haptics.confirm()
                    steps.reveal(RecapPage.Tags)
                }
            } else {
                TagRow(i + 1, stat, Modifier.recapEntrance(order))
            }
        }
        tags.brighter(1).firstOrNull()?.let { stat ->
            val shown = rememberReveal(revealed)
            Text(
                stringResource(R.string.insights_recap_tag_brighter, stat.tag.label, formats.signedDecimal(stat.link!!.lift)),
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.padding(top = Spacing.m).graphicsLayer { alpha = shown() },
            )
        }
    }
}

@Composable
private fun TagRow(rank: Int, stat: TagStat, modifier: Modifier = Modifier, large: Boolean = false) {
    Row(verticalAlignment = Alignment.CenterVertically, modifier = modifier) {
        Text(
            "#$rank",
            style = if (large) MaterialTheme.typography.displaySmallEmphasized else MaterialTheme.typography.headlineMediumEmphasized,
            modifier = Modifier.widthIn(min = 64.dp),
        )
        TagIcon(stat.tag, size = if (large) 56.dp else 48.dp)
        Column(Modifier.padding(start = Spacing.m)) {
            Text(
                stat.tag.label,
                style = if (large) MaterialTheme.typography.headlineMediumEmphasized else MaterialTheme.typography.headlineSmallEmphasized,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(pluralStringResource(R.plurals.insights_days_count, stat.days, stat.days), style = MaterialTheme.typography.bodyMedium)
        }
    }
}

/** The top tag under a cover that opens (morphing round, growing and fading) to show it pop in. */
@Composable
private fun CoveredTagRow(stat: TagStat, revealed: Boolean, modifier: Modifier, onReveal: () -> Unit) {
    val progress = rememberReveal(revealed)
    val content = LocalContentColor.current
    val label = stringResource(R.string.insights_recap_reveal)
    Box(
        modifier
            .fillMaxWidth()
            .then(
                if (revealed) {
                    Modifier
                } else {
                    Modifier
                        .clip(RoundedCornerShape(24.dp))
                        .clickable(role = Role.Button, onClick = onReveal)
                        .semantics { contentDescription = label }
                },
            ),
    ) {
        TagRow(
            1,
            stat,
            Modifier.graphicsLayer {
                val p = progress()
                alpha = p
                scaleX = 0.6f + 0.4f * p
                scaleY = 0.6f + 0.4f * p
            },
            large = true,
        )
        if (!revealed || progress() < 1f) {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.align(Alignment.CenterStart)) {
                Text("#1", style = MaterialTheme.typography.displaySmallEmphasized, modifier = Modifier.widthIn(min = 64.dp).graphicsLayer { alpha = 1f - progress() })
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier
                        .size(56.dp)
                        .graphicsLayer {
                            val p = progress()
                            alpha = 1f - p
                            scaleX = 1f + 0.5f * p
                            scaleY = 1f + 0.5f * p
                        }
                        .background(content.copy(alpha = 0.16f), MorphShape(coverMorph, progress())),
                ) {
                    Text("?", style = MaterialTheme.typography.headlineSmallEmphasized)
                }
                hintOrNull(stringResource(R.string.insights_recap_tap_reveal))?.let {
                    Text(it, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.padding(start = Spacing.m).graphicsLayer { alpha = 1f - progress() })
                }
            }
        }
    }
}

/** Tags first used in this period, popping in one after another. */
@Composable
internal fun NewTagsPage(state: InsightsRecapUiState.Ready) {
    val year = state.header.period is InsightsPeriod.Year
    PageColumn {
        Kicker(stringResource(R.string.insights_recap_new_tags))
        Text(
            stringResource(if (year) R.string.insights_recap_new_tags_year else R.string.insights_recap_new_tags_month),
            style = MaterialTheme.typography.headlineSmall,
            modifier = Modifier.recapEntrance(1),
        )
        state.recap.newTags.forEachIndexed { i, stat ->
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.recapEntrance(i + 2)) {
                TagIcon(stat.tag, size = 48.dp, modifier = Modifier.recapPop(i + 2, turn = -60f))
                Column(Modifier.padding(start = Spacing.m)) {
                    Text(stat.tag.label, style = MaterialTheme.typography.titleLargeEmphasized, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(pluralStringResource(R.plurals.insights_days_count, stat.days, stat.days), style = MaterialTheme.typography.bodyMedium)
                }
            }
        }
    }
}

/**
 * The rhythm: a guess at the brightest weekday (tap a day, or tap forward to just see it), then
 * when the entries were mostly written.
 */
@Composable
internal fun RhythmPage(state: InsightsRecapUiState.Ready, steps: RecapSteps, formats: DakilioFormats) {
    val rhythm = state.recap.overview.rhythm
    val best = rhythm.bestWeekday
    val revealed = steps.isRevealed(RecapPage.Rhythm)
    val haptics = rememberHaptics()
    PageColumn {
        Kicker(stringResource(R.string.insights_recap_rhythm))
        if (best != null) {
            Text(stringResource(R.string.insights_recap_guess), style = MaterialTheme.typography.headlineSmallEmphasized, modifier = Modifier.recapEntrance(1))
            val bestColors = rhythm.weekdays[best]?.average?.let { nearestMood(state.moods, it) }?.let { moodColors(it) }
            Row(Modifier.fillMaxWidth().recapEntrance(2), horizontalArrangement = Arrangement.spacedBy(Spacing.xs)) {
                weekdaysFrom(state.firstDay).forEach { day ->
                    WeekdayChoice(
                        day = day,
                        label = formats.weekdayShort(day).trimEnd('.'),
                        description = formats.weekday(day),
                        revealed = revealed,
                        best = day == best,
                        guessed = day == steps.guess,
                        bestColor = bestColors?.container,
                        bestContent = bestColors?.onContainer,
                        modifier = Modifier.weight(1f),
                    ) {
                        haptics.confirm()
                        steps.guess(day, RecapPage.Rhythm)
                    }
                }
            }
            val shown = rememberReveal(revealed)
            val answer = when (steps.guess) {
                null -> stringResource(R.string.insights_recap_guess_answer, formats.weekday(best))
                best -> stringResource(R.string.insights_recap_guess_right, formats.weekday(best))
                else -> stringResource(R.string.insights_recap_guess_wrong, formats.weekday(best))
            }
            Box {
                // Laid out from the start, so nothing moves when the answer shows.
                Text(
                    if (revealed) answer else hintOrNull(stringResource(R.string.insights_recap_guess_hint)).orEmpty(),
                    style = if (revealed) MaterialTheme.typography.headlineMediumEmphasized else MaterialTheme.typography.bodyLarge,
                    modifier = Modifier.graphicsLayer {
                        val p = if (revealed) shown() else 1f
                        alpha = p
                        translationY = (1f - p) * 24.dp.toPx()
                    },
                )
            }
        }
        rhythm.busiestDayPart?.let { part ->
            Text(
                when (part) {
                    DayPart.Morning -> stringResource(R.string.insights_recap_early_bird)
                    DayPart.Night -> stringResource(R.string.insights_recap_night_owl)
                    else -> stringResource(R.string.insights_recap_mostly_written, dayPartLabel(part).lowercase())
                },
                style = MaterialTheme.typography.titleLarge,
                modifier = Modifier.recapEntrance(4).padding(top = Spacing.l),
            )
        }
    }
}

/**
 * One weekday of the guess: a round button. Once revealed, the brightest grows into its mood's
 * color, the rest fade back, and a wrong guess keeps a ring.
 */
@Composable
private fun WeekdayChoice(
    day: DayOfWeek,
    label: String,
    description: String,
    revealed: Boolean,
    best: Boolean,
    guessed: Boolean,
    bestColor: Color?,
    bestContent: Color?,
    modifier: Modifier,
    onGuess: () -> Unit,
) {
    val content = LocalContentColor.current
    val interaction = remember(day) { MutableInteractionSource() }
    val spec = decorativeSpec(PopSpec)
    val scale by animateFloatAsState(if (!revealed) 1f else if (best) 1.2f else 0.9f, spec, label = "weekdayScale")
    val dim by animateFloatAsState(if (revealed && !best) 0.4f else 1f, MaterialTheme.motionScheme.defaultEffectsSpec(), label = "weekdayDim")
    val fill = if (revealed && best) bestColor ?: content else content.copy(alpha = 0.12f)
    Box(
        contentAlignment = Alignment.Center,
        modifier = modifier
            .aspectRatio(1f)
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
                alpha = dim
            }
            .pressScale(interaction)
            .then(if (revealed && guessed && !best) Modifier.border(2.dp, content, CircleShape) else Modifier)
            .clip(CircleShape)
            .background(fill)
            .clickable(interactionSource = interaction, indication = null, enabled = !revealed, role = Role.Button, onClick = onGuess)
            .semantics { contentDescription = description },
    ) {
        Text(
            label,
            style = MaterialTheme.typography.labelMedium,
            color = if (revealed && best) bestContent ?: MaterialTheme.colorScheme.surface else content,
            maxLines = 1,
            textAlign = TextAlign.Center,
        )
    }
}
