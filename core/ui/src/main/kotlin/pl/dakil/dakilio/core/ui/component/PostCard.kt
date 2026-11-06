package pl.dakil.dakilio.core.ui.component

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import java.io.File
import pl.dakil.dakilio.core.designsystem.component.pressScale
import pl.dakil.dakilio.core.designsystem.icon.DakilioIcon
import pl.dakil.dakilio.core.designsystem.icon.symbolForKey
import pl.dakil.dakilio.core.designsystem.theme.SegmentedShapes
import pl.dakil.dakilio.core.model.Diary
import pl.dakil.dakilio.core.model.Post
import pl.dakil.dakilio.core.model.PostPhoto
import pl.dakil.dakilio.core.model.PreviewLength
import pl.dakil.dakilio.core.model.noteExcerpt
import pl.dakil.dakilio.core.ui.DakilioFormats
import pl.dakil.dakilio.core.ui.LocalEntryDisplay
import pl.dakil.dakilio.core.ui.R
import pl.dakil.dakilio.core.ui.mood.MoodBadge
import pl.dakil.dakilio.core.ui.mood.NoMoodBadge
import pl.dakil.dakilio.core.ui.mood.moodColors
import pl.dakil.dakilio.core.ui.scaled

private val BadgeSize = 44.dp
private val CardPaddingH = 16.dp
private val CardPaddingV = 14.dp

/**
 * One journal entry. Cards of the same day are drawn as a segmented group ([index] of [count]) and
 * linked by a thin "journey thread" running through their mood badges.
 */
@Composable
fun PostCard(
    post: Post,
    formats: DakilioFormats,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    index: Int = 0,
    count: Int = 1,
    /** Diary to label the card with (only when the list mixes diaries). */
    diary: Diary? = null,
    highlight: String? = null,
    onPhotoClick: (Int) -> Unit = {},
) {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val motion = MaterialTheme.motionScheme
    // Expressive press feedback: the card's tight inner corners relax into soft ones.
    val inner by animateDpAsState(if (pressed) 20.dp else SegmentedShapes.inner, motion.fastSpatialSpec(), label = "cardCorner")
    val shape = SegmentedShapes.of(index, count, inner = inner)
    val threadColor = MaterialTheme.colorScheme.outlineVariant
    val display = LocalEntryDisplay.current
    val textScale = display.timelineTextScale
    val full = display.previewLength == PreviewLength.Full
    val maxTags = display.tagLimit.count ?: Int.MAX_VALUE
    val hasBody = post.title != null || post.note != null || post.tags.isNotEmpty() || post.photos.isNotEmpty()

    Surface(
        onClick = onClick,
        shape = shape,
        color = MaterialTheme.colorScheme.surfaceContainer,
        interactionSource = interaction,
        modifier = modifier.entryPeek(post, diary, highlight).fillMaxWidth().pressScale(interaction, pressedScale = 0.985f),
    ) {
        Row(
            modifier = Modifier
                .drawBehind { drawThread(threadColor, index, count) }
                .padding(horizontal = CardPaddingH, vertical = CardPaddingV),
            verticalAlignment = if (hasBody) Alignment.Top else Alignment.CenterVertically,
        ) {
            if (post.mood != null) MoodBadge(post.mood!!, size = BadgeSize) else NoMoodBadge(size = BadgeSize)
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                MetaRow(post, formats, diary)
                post.title?.let {
                    Text(
                        highlighted(it, highlight),
                        style = MaterialTheme.typography.titleMediumEmphasized.scaled(textScale),
                        maxLines = if (full) Int.MAX_VALUE else 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                post.note?.let {
                    // In search, a note whose match is past the preview starts just before it.
                    val shown = if (highlight.isNullOrBlank() || full) it else noteExcerpt(it, highlight)
                    Text(
                        highlighted(shown, highlight),
                        style = MaterialTheme.typography.bodyMedium.scaled(textScale),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = display.previewLength.noteLines ?: Int.MAX_VALUE,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                if (post.tags.isNotEmpty()) {
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp),
                        modifier = Modifier.padding(top = 2.dp),
                    ) {
                        post.tags.take(maxTags).forEach { TagPill(it, compact = display.compactTags) }
                        if (post.tags.size > maxTags) {
                            CountPill(stringResource(R.string.ui_more_tags, post.tags.size - maxTags))
                        }
                    }
                }
                if (post.photos.isNotEmpty()) {
                    PhotoStrip(post.photos, onPhotoClick, Modifier.padding(top = 4.dp))
                }
            }
        }
    }
}

@Composable
private fun MetaRow(post: Post, formats: DakilioFormats, diary: Diary?) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        val mood = post.mood
        Text(
            text = mood?.label ?: stringResource(if (post.title != null || post.note != null) R.string.ui_no_mood else R.string.ui_entry),
            style = MaterialTheme.typography.labelLargeEmphasized,
            color = if (mood != null) moodColors(mood).accent else MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            text = "  ·  " + formats.time(post.dateTime.toLocalTime()),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.weight(1f))
        if (diary != null) {
            DakilioIcon(
                symbolForKey(diary.iconKey),
                contentDescription = diary.name,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(start = 4.dp).size(16.dp),
            )
        }
    }
}

@Composable
private fun PhotoStrip(photos: List<PostPhoto>, onPhotoClick: (Int) -> Unit, modifier: Modifier = Modifier) {
    val shape = RoundedCornerShape(14.dp)
    val placeholder = MaterialTheme.colorScheme.surfaceContainerHighest
    if (photos.size == 1) {
        AsyncImage(
            model = File(photos.first().path),
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = modifier
                .fillMaxWidth()
                .aspectRatio(16f / 10f)
                .clip(shape)
                .background(placeholder)
                .clickable { onPhotoClick(0) },
        )
        return
    }
    Row(modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        val visible = photos.take(3)
        visible.forEachIndexed { i, photo ->
            Box(
                Modifier
                    .weight(1f)
                    .height(if (photos.size == 2) 132.dp else 104.dp)
                    .clip(shape)
                    .background(placeholder)
                    .clickable { onPhotoClick(i) },
            ) {
                AsyncImage(
                    model = File(photo.path),
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize(),
                )
                if (i == visible.lastIndex && photos.size > visible.size) {
                    Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.45f)), contentAlignment = Alignment.Center) {
                        Text(
                            stringResource(R.string.ui_more_photos, photos.size - visible.size),
                            style = MaterialTheme.typography.titleLargeEmphasized,
                            color = Color.White,
                        )
                    }
                }
            }
        }
    }
}

private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawThread(color: Color, index: Int, count: Int) {
    if (count < 2) return
    val x = CardPaddingH.toPx() + BadgeSize.toPx() / 2
    val badgeTop = CardPaddingV.toPx()
    val badgeBottom = badgeTop + BadgeSize.toPx()
    val stroke = 2.dp.toPx()
    if (index > 0) drawLine(color, Offset(x, 0f), Offset(x, badgeTop - 4.dp.toPx()), stroke, StrokeCap.Round)
    if (index < count - 1) drawLine(color, Offset(x, badgeBottom + 4.dp.toPx()), Offset(x, size.height), stroke, StrokeCap.Round)
}

@Composable
internal fun highlighted(text: String, query: String?): AnnotatedString {
    if (query.isNullOrBlank()) return AnnotatedString(text)
    val colors = MaterialTheme.colorScheme
    return remember(text, query, colors.tertiaryContainer) {
        buildAnnotatedString {
            append(text)
            var start = text.indexOf(query, ignoreCase = true)
            while (start >= 0) {
                addStyle(
                    SpanStyle(background = colors.tertiaryContainer, color = colors.onTertiaryContainer),
                    start,
                    start + query.length,
                )
                start = text.indexOf(query, start + query.length, ignoreCase = true)
            }
        }
    }
}
