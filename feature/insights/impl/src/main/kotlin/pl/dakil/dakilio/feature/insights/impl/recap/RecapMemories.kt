package pl.dakil.dakilio.feature.insights.impl.recap

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import java.io.File
import pl.dakil.dakilio.core.designsystem.theme.Spacing
import pl.dakil.dakilio.core.model.Tag
import pl.dakil.dakilio.core.ui.DakilioFormats
import pl.dakil.dakilio.core.ui.component.TagPill
import pl.dakil.dakilio.core.ui.mood.MoodBadge
import pl.dakil.dakilio.feature.insights.impl.R
import pl.dakil.dakilio.feature.insights.impl.logic.InsightsPeriod
import pl.dakil.dakilio.feature.insights.impl.logic.RecapPhoto

/** Each slot's tilt in the photo scatter, so the photos look laid down by hand. */
private val slotTurns = listOf(-5f, 3f, -2f, 4f, -4f, 2f, -3f, 5f, -1f)

/** Each slot's nudge up or down in the scatter. */
private val slotNudges = listOf(4, -6, 8, -4, 6, -8, 2, -2, 6)

/**
 * The brightest day: its date, the entry's first photo (when it has one), its face, title and tags.
 * The note isn't shown: a recap holds no note text.
 */
@Composable
internal fun BrightestDayPage(state: InsightsRecapUiState.Ready, formats: DakilioFormats, onOpenPost: (Long) -> Unit) {
    val post = state.recap.brightest!!
    PageColumn {
        Kicker(stringResource(R.string.insights_recap_brightest))
        Big(formats.fullDate(post.date, state.today))
        post.photos.firstOrNull()?.let { photo ->
            AsyncImage(
                model = File(photo.path),
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .recapPop(2, turn = -4f)
                    .fillMaxWidth()
                    .heightIn(max = 240.dp)
                    .aspectRatio(4f / 3f)
                    .clip(RoundedCornerShape(28.dp))
                    .background(MaterialTheme.colorScheme.surfaceContainerHighest),
            )
        }
        Surface(shape = RoundedCornerShape(28.dp), color = MaterialTheme.colorScheme.surface, modifier = Modifier.recapEntrance(3).fillMaxWidth()) {
            Column(Modifier.padding(Spacing.xl), verticalArrangement = Arrangement.spacedBy(Spacing.m)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    post.mood?.let { MoodBadge(it, size = 44.dp, active = true, modifier = Modifier.recapPop(4, turn = 40f)) }
                    Text(
                        post.title ?: post.mood?.label.orEmpty(),
                        style = MaterialTheme.typography.titleMediumEmphasized,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.padding(start = Spacing.m),
                    )
                }
                if (post.tags.isNotEmpty()) RecapTags(post.tags.take(6))
            }
        }
        Button(onClick = { onOpenPost(post.id) }, modifier = Modifier.recapEntrance(5)) { Text(stringResource(R.string.insights_recap_open_entry)) }
    }
}

/** The period's photos, spread through it, laid down one after another like prints on a table. */
@Composable
internal fun PhotosPage(state: InsightsRecapUiState.Ready) {
    val recap = state.recap
    val year = state.header.period is InsightsPeriod.Year
    PageColumn {
        Kicker(pluralStringResource(if (year) R.plurals.insights_recap_photos_year else R.plurals.insights_recap_photos_month, recap.photoCount, recap.photoCount))
        PhotoScatter(recap.photos)
    }
}

/** Up to nine photos in rows of three, each tilted and nudged a little, the last row centered. */
@Composable
private fun PhotoScatter(photos: List<RecapPhoto>) {
    BoxWithConstraints(Modifier.fillMaxWidth().padding(vertical = Spacing.m)) {
        val tile = (maxWidth - Spacing.s * 2) / 3
        Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(Spacing.s)) {
            photos.chunked(3).forEachIndexed { row, items ->
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(Spacing.s, Alignment.CenterHorizontally)) {
                    items.forEachIndexed { column, photo -> PhotoPrint(photo, row * 3 + column, Modifier.width(tile)) }
                }
            }
        }
    }
}

/** One photo as a print: a light frame, a soft shadow, a tilt; it drops in on its turn. */
@Composable
private fun PhotoPrint(photo: RecapPhoto, slot: Int, modifier: Modifier) {
    val turn = slotTurns[slot % slotTurns.size]
    Surface(
        color = MaterialTheme.colorScheme.surfaceBright,
        shape = RoundedCornerShape(10.dp),
        shadowElevation = 4.dp,
        modifier = modifier
            .recapPop(slot + 1, turn = turn * 3)
            .offset(y = slotNudges[slot % slotNudges.size].dp)
            .rotate(turn)
            .aspectRatio(1f),
    ) {
        AsyncImage(
            model = File(photo.photo.path),
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier
                .fillMaxSize()
                .padding(6.dp)
                .clip(RoundedCornerShape(6.dp))
                .background(MaterialTheme.colorScheme.surfaceContainerHighest),
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun RecapTags(tags: List<Tag>) {
    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) { tags.forEach { TagPill(it) } }
}
