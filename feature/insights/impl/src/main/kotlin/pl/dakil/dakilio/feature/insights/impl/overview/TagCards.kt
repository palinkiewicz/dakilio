package pl.dakil.dakilio.feature.insights.impl.overview

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import kotlin.math.abs
import pl.dakil.dakilio.core.designsystem.theme.Spacing
import pl.dakil.dakilio.core.model.Mood
import pl.dakil.dakilio.core.model.primaryOf
import pl.dakil.dakilio.core.ui.DakilioFormats
import pl.dakil.dakilio.core.ui.mood.levelColors
import pl.dakil.dakilio.core.ui.mood.moodColors
import pl.dakil.dakilio.feature.insights.impl.R
import pl.dakil.dakilio.feature.insights.impl.chart.DivergingBar
import pl.dakil.dakilio.feature.insights.impl.chart.SingleBar
import pl.dakil.dakilio.feature.insights.impl.logic.TagStat
import pl.dakil.dakilio.feature.insights.impl.logic.brighter
import pl.dakil.dakilio.feature.insights.impl.logic.heavier

/** The colors of a tag link's two directions: the "good" level's for brighter days, the "bad" level's for heavier. */
@Composable
internal fun linkColors(moods: List<Mood>): Pair<Color, Color> {
    val above = moods.primaryOf(4)?.let { moodColors(it) } ?: levelColors(4)
    val below = moods.primaryOf(2)?.let { moodColors(it) } ?: levelColors(2)
    return above.container to below.container
}

/**
 * Which tags' days were brighter or heavier than the others. Shown only when some tag has enough
 * days on both sides (the "still gathering" card covers the rest).
 */
@Composable
internal fun TagLinksCard(
    tags: List<TagStat>,
    moods: List<Mood>,
    formats: DakilioFormats,
    onMore: (() -> Unit)? = null,
    onTag: ((Long) -> Unit)? = null,
) {
    val brighter = tags.brighter(3)
    val heavier = tags.heavier(3)
    InsightCard {
        CardTitle(stringResource(R.string.insights_links), stringResource(R.string.insights_links_body), onMore = onMore)
        if (brighter.isEmpty() && heavier.isEmpty()) {
            CardNote(stringResource(R.string.insights_links_none), Modifier.padding(top = Spacing.m))
            return@InsightCard
        }
        // One scale for the whole card, so bars compare across both lists.
        val reach = maxOf(1f, (brighter + heavier).maxOf { abs(it.link!!.lift) })
        if (brighter.isNotEmpty()) {
            LinkList(stringResource(R.string.insights_links_brighter), brighter, reach, moods, formats, onTag)
        }
        if (heavier.isNotEmpty()) {
            LinkList(stringResource(R.string.insights_links_heavier), heavier, reach, moods, formats, onTag)
        }
    }
}

@Composable
private fun LinkList(title: String, tags: List<TagStat>, reach: Float, moods: List<Mood>, formats: DakilioFormats, onTag: ((Long) -> Unit)?) {
    Text(
        title,
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(top = Spacing.l, bottom = Spacing.s),
    )
    Column(verticalArrangement = Arrangement.spacedBy(Spacing.m)) {
        tags.forEach { stat -> LinkRow(stat, reach, moods, formats, onTag?.let { open -> { open(stat.tag.id) } }) }
    }
}

/** A tag, how many levels its days sit above or below the others, and how many days that's from. */
@Composable
internal fun LinkRow(stat: TagStat, reach: Float, moods: List<Mood>, formats: DakilioFormats, onClick: (() -> Unit)?) {
    val link = stat.link ?: return
    val (above, below) = linkColors(moods)
    val figure = formats.signedDecimal(link.lift)
    val days = pluralStringResource(R.plurals.insights_days_count, link.daysWith, link.daysWith)
    val description = stringResource(R.string.insights_links_cd, stat.tag.label, figure, days)
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .cardRowClick(onClick)
            .semantics(mergeDescendants = true) { contentDescription = description },
    ) {
        TagIcon(stat.tag)
        Column(Modifier.padding(start = Spacing.m).weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(stat.tag.label, style = MaterialTheme.typography.labelLarge, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                Text(figure, style = MaterialTheme.typography.labelLargeEmphasized, modifier = Modifier.padding(start = Spacing.s))
            }
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = Spacing.xs)) {
                DivergingBar(link.lift / reach, above, below, Modifier.weight(1f))
                Text(
                    days,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.End,
                    modifier = Modifier.padding(start = Spacing.s).widthIn(min = 48.dp),
                )
            }
        }
    }
}

/** The tags on the most days, with how that changed against the comparison period. */
@Composable
internal fun TopTagsCard(tags: List<TagStat>, onMore: (() -> Unit)? = null, onTag: ((Long) -> Unit)? = null) {
    InsightCard {
        CardTitle(stringResource(R.string.insights_top_tags), onMore = onMore?.takeIf { tags.isNotEmpty() })
        if (tags.isEmpty()) {
            CardNote(stringResource(R.string.insights_no_tags), Modifier.padding(top = Spacing.s))
            return@InsightCard
        }
        val top = tags.take(5)
        val max = top.maxOf { it.days }.toFloat()
        Column(Modifier.padding(top = Spacing.l), verticalArrangement = Arrangement.spacedBy(Spacing.m)) {
            top.forEach { stat -> TagUseRow(stat, max, onTag?.let { open -> { open(stat.tag.id) } }) }
        }
    }
}

/** A tag with a bar of the days it was used on, the figure, and the change against the comparison period. */
@Composable
internal fun TagUseRow(stat: TagStat, max: Float, onClick: (() -> Unit)?) {
    val days = pluralStringResource(R.plurals.insights_days_count, stat.days, stat.days)
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .cardRowClick(onClick)
            .semantics(mergeDescendants = true) { contentDescription = "${stat.tag.label}, $days" },
    ) {
        TagIcon(stat.tag)
        Column(Modifier.padding(start = Spacing.m).weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(stat.tag.label, style = MaterialTheme.typography.labelLarge, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                stat.change?.takeIf { it != 0 }?.let { change ->
                    Text(
                        if (change > 0) "+$change" else "−${-change}",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(start = Spacing.s),
                    )
                }
            }
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = Spacing.xs)) {
                SingleBar(stat.days / max, Modifier.weight(1f))
                Text(
                    stat.days.toString(),
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.End,
                    modifier = Modifier.padding(start = Spacing.s).widthIn(min = 24.dp),
                )
            }
        }
    }
}
