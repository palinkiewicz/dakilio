package pl.dakil.dakilio.core.ui.component

import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.awaitLongPressOrCancellation
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlurEffect
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.TileMode
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.onPlaced
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.min
import androidx.compose.ui.util.lerp
import coil3.compose.AsyncImage
import java.io.File
import java.time.LocalDate
import kotlin.math.abs
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.withContext
import pl.dakil.dakilio.core.designsystem.component.rememberHaptics
import pl.dakil.dakilio.core.designsystem.icon.DakilioIcon
import pl.dakil.dakilio.core.designsystem.icon.DakilioSymbols
import pl.dakil.dakilio.core.designsystem.icon.symbolForKey
import pl.dakil.dakilio.core.designsystem.theme.Spacing
import pl.dakil.dakilio.core.model.Diary
import pl.dakil.dakilio.core.model.Post
import pl.dakil.dakilio.core.ui.LocalEntryDisplay
import pl.dakil.dakilio.core.ui.R
import pl.dakil.dakilio.core.ui.mood.MoodBadge
import pl.dakil.dakilio.core.ui.mood.NoMoodBadge
import pl.dakil.dakilio.core.ui.mood.moodColors
import pl.dakil.dakilio.core.ui.rememberFormats
import pl.dakil.dakilio.core.ui.scaled

/** The top and bottom part of the screen (as a fraction of its height) where the peek scrolls by itself. */
internal const val PEEK_EDGE_FRACTION = 0.12f

/** The most a finger movement is multiplied by, so a medium note fits one sweep without feeling slippery. */
internal const val PEEK_MAX_GAIN = 3f

/**
 * How far the content moves per pixel of finger travel. A note that overflows only a little scrolls
 * 1:1; a longer one scrolls faster, so one sweep from the touch-down point to the top edge zone
 * ([travel]) reaches its end. Past [PEEK_MAX_GAIN], the edge zones take over.
 */
internal fun peekScrollGain(maxScroll: Float, travel: Float): Float {
    if (maxScroll <= 0f) return 1f
    if (travel <= 0f) return PEEK_MAX_GAIN
    return (maxScroll / travel).coerceIn(1f, PEEK_MAX_GAIN)
}

/**
 * Auto-scroll speed while the finger rests in an edge zone, ramping with how deep into the zone it
 * is: positive (reading further) at the top, negative (back) at the bottom, zero in between.
 */
internal fun edgeScrollSpeed(y: Float, height: Float, maxSpeed: Float, edgeFraction: Float = PEEK_EDGE_FRACTION): Float {
    if (height <= 0f) return 0f
    val zone = height * edgeFraction
    return when {
        y < zone -> maxSpeed * ((zone - y) / zone).coerceIn(0f, 1f)
        y > height - zone -> -maxSpeed * ((y - (height - zone)) / zone).coerceIn(0f, 1f)
        else -> 0f
    }
}

/** One entry being peeked at; [origin] is the held card's bounds, which the large card grows out of. */
class EntryPeek internal constructor(
    val post: Post,
    val diary: Diary?,
    val highlight: String?,
    val origin: Rect,
) {
    internal val scroll = ScrollState(0)
}

/** The hold-to-peek overlay's state, owned by [EntryPeekHost]. */
@Stable
class EntryPeekState internal constructor() {
    /** What's shown; it outlives [active] until the closing animation is done. */
    var current by mutableStateOf<EntryPeek?>(null)
        private set

    /** True while the finger is down. */
    var active by mutableStateOf(false)
        private set

    internal var fingerY by mutableFloatStateOf(0f)
        private set

    /** Edge zones only start working once the finger has moved, so a card held near an edge doesn't run off. */
    internal var edgesArmed by mutableStateOf(false)
        private set

    internal var viewportHeight = 0f
    internal var armDistance = 0f
    private var downY = 0f

    /** The Share chip's root bounds (already grown by the touch slop), set while it's laid out. */
    internal var shareBounds: Rect? = null

    /** The finger rests on the Share chip: scrolling pauses, and letting go shares. */
    internal var overShare by mutableStateOf(false)
        private set

    /** Let go on the Share chip: the entry to share, until the host has handed it to the share sheet. */
    internal var shareRequest by mutableStateOf<EntryPeek?>(null)
        private set

    internal fun start(peek: EntryPeek, y: Float) {
        current = peek
        downY = y
        fingerY = y
        edgesArmed = false
        overShare = false
        active = true
    }

    internal fun move(position: Offset) {
        val y = position.y
        val dy = y - fingerY
        fingerY = y
        if (!edgesArmed && abs(y - downY) > armDistance) edgesArmed = true
        overShare = shareBounds?.contains(position) == true
        if (overShare) return
        val scroll = current?.scroll ?: return
        val gain = peekScrollGain(scroll.measuredMax().toFloat(), downY - viewportHeight * PEEK_EDGE_FRACTION)
        // The finger going up reads further, like scrolling a list.
        scroll.dispatchRawDelta(-dy * gain)
    }

    /** The finger lifted ([released]) or the gesture was cancelled. */
    internal fun end(released: Boolean) {
        if (released && overShare) shareRequest = current
        overShare = false
        active = false
    }

    internal fun shareHandled() {
        shareRequest = null
    }

    internal fun clear() {
        if (!active) current = null
    }
}

/** [ScrollState.maxValue] is Int.MAX_VALUE until the content has been measured. */
private fun ScrollState.measuredMax(): Int = if (maxValue == Int.MAX_VALUE) 0 else maxValue

val LocalEntryPeek = staticCompositionLocalOf<EntryPeekState?> { null }

/**
 * Hold to peek: after a long press, the entry shows large over a blurred background until the finger
 * lifts, and moving the finger scrolls it. Without an [EntryPeekHost] above, it does nothing.
 *
 * Once the peek starts, every pointer change is consumed on the Initial pass, before the card's own
 * clickable (inside) and the list (outside) see it: releasing doesn't open the entry and the list
 * doesn't scroll. Compose keeps sending the gesture here although the overlay draws on top, which is
 * why the overlay lives in the same window and isn't a dialog.
 */
@Composable
fun Modifier.entryPeek(post: Post, diary: Diary? = null, highlight: String? = null): Modifier {
    val state = LocalEntryPeek.current ?: return this
    val haptics = rememberHaptics()
    val latestPost by rememberUpdatedState(post)
    val latestDiary by rememberUpdatedState(diary)
    val latestHighlight by rememberUpdatedState(highlight)
    val holder = remember { arrayOfNulls<LayoutCoordinates>(1) }
    return this
        .onGloballyPositioned { holder[0] = it }
        .pointerInput(state) {
            awaitEachGesture {
                val down = awaitFirstDown(requireUnconsumed = false)
                val longPress = awaitLongPressOrCancellation(down.id) ?: return@awaitEachGesture
                val coords = holder[0]?.takeIf { it.isAttached } ?: return@awaitEachGesture
                haptics.longPress()
                longPress.consume()
                state.start(
                    EntryPeek(latestPost, latestDiary, latestHighlight, coords.boundsInRoot()),
                    coords.localToRoot(longPress.position).y,
                )
                var released = false
                try {
                    while (true) {
                        val event = awaitPointerEvent(PointerEventPass.Initial)
                        val change = event.changes.firstOrNull { it.id == longPress.id } ?: break
                        event.changes.forEach { it.consume() }
                        if (!change.pressed) {
                            released = true
                            break
                        }
                        if (coords.isAttached) state.move(coords.localToRoot(change.position))
                    }
                } finally {
                    state.end(released)
                }
            }
        }
}

private val MaxBlur = 24.dp
private val CardCorner = 32.dp
private const val SCRIM_WITH_BLUR = 0.32f
private const val SCRIM_WITHOUT_BLUR = 0.55f
private const val CARD_MAX_HEIGHT = 0.8f

/** Hosts [entryPeek]: blurs [content] behind the large card while an entry is held. */
@Composable
fun EntryPeekHost(content: @Composable () -> Unit) {
    val state = remember { EntryPeekState() }
    val progress = remember { Animatable(0f) }
    val motion = MaterialTheme.motionScheme
    val haptics = rememberHaptics()
    val density = LocalDensity.current
    val blurs = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S
    val maxBlurPx = with(density) { MaxBlur.toPx() }
    val maxSpeed = with(density) { 1000.dp.toPx() }
    state.armDistance = with(density) { 24.dp.toPx() }
    val context = LocalContext.current
    // Through an ActivityResult launch, so the app lock treats the share sheet like a picker and
    // coming back doesn't lock.
    val shareLauncher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { }

    LaunchedEffect(state.active) {
        if (state.active) {
            progress.animateTo(1f, motion.defaultSpatialSpec())
        } else {
            progress.animateTo(0f, motion.fastSpatialSpec())
            state.clear()
        }
    }

    val peek = state.current
    // Edge auto-scroll: a frame loop only while the finger rests in an edge zone.
    LaunchedEffect(peek, state.active) {
        if (peek == null || !state.active) return@LaunchedEffect
        snapshotFlow { state.edgesArmed && !state.overShare && edgeScrollSpeed(state.fingerY, state.viewportHeight, maxSpeed) != 0f }
            .distinctUntilChanged()
            .collectLatest { scrolling ->
                if (!scrolling) return@collectLatest
                var last = 0L
                while (true) {
                    withFrameNanos { now ->
                        if (last != 0L) {
                            val seconds = (now - last) / 1_000_000_000f
                            peek.scroll.dispatchRawDelta(edgeScrollSpeed(state.fingerY, state.viewportHeight, maxSpeed) * seconds)
                        }
                        last = now
                    }
                }
            }
    }
    // One tick when the end of a scrolling entry is reached. It re-arms only after scrolling well back,
    // so dragging back against the edge auto-scroll (which jitters right at the end) doesn't buzz.
    val rearmPx = with(density) { 48.dp.toPx() }
    LaunchedEffect(peek) {
        val scroll = peek?.scroll ?: return@LaunchedEffect
        var armed = true
        snapshotFlow { scroll.measuredMax() to scroll.value }.collect { (max, value) ->
            if (max <= 0) return@collect
            if (armed && value >= max) {
                haptics.tick()
                armed = false
            } else if (!armed && value < max - rearmPx) {
                armed = true
            }
        }
    }

    // Sharing: the image is rendered as soon as the finger reaches the chip, so letting go feels instant.
    LaunchedEffect(state.overShare) { if (state.overShare) haptics.tick() }
    var rendered by remember { mutableStateOf<Pair<EntryPeek, ImageBitmap>?>(null) }
    val renderTarget = state.shareRequest ?: state.current?.takeIf { state.overShare }
    LaunchedEffect(state.shareRequest, rendered) {
        val request = state.shareRequest ?: return@LaunchedEffect
        val image = rendered?.takeIf { it.first === request }?.second ?: return@LaunchedEffect
        haptics.confirm()
        val written = withContext(Dispatchers.IO) {
            runCatching { writeShareImage(context, image, request.post.id, hasPhotos = request.post.photos.isNotEmpty()) }
        }
        state.shareHandled()
        rendered = null
        written.onSuccess { (uri, mime) -> shareLauncher.launch(shareImageIntent(uri, mime)) }
    }
    // A finished render is a big bitmap: let it go once the peek has moved on without sharing it.
    LaunchedEffect(state.current) {
        if (state.shareRequest == null && rendered?.first !== state.current) rendered = null
    }

    CompositionLocalProvider(LocalEntryPeek provides state) {
        Box(Modifier.fillMaxSize().onSizeChanged { state.viewportHeight = it.height.toFloat() }) {
            // Off-screen and invisible: it only records the entry into a bitmap.
            if (renderTarget != null && rendered?.first !== renderTarget) {
                key(renderTarget) { EntryShareRenderer(renderTarget) { rendered = renderTarget to it } }
            }
            Box(
                Modifier.fillMaxSize().graphicsLayer {
                    val radius = maxBlurPx * progress.value.coerceIn(0f, 1f)
                    renderEffect = if (blurs && radius > 0.5f) BlurEffect(radius, radius, TileMode.Clamp) else null
                },
            ) {
                content()
            }
            if (peek != null) {
                val scrim = MaterialTheme.colorScheme.scrim
                val scrimAlpha = if (blurs) SCRIM_WITH_BLUR else SCRIM_WITHOUT_BLUR
                Box(
                    Modifier.fillMaxSize().drawWithContent {
                        drawRect(scrim.copy(alpha = scrimAlpha * progress.value.coerceIn(0f, 1f)))
                    },
                )
                PeekLayer(peek, progress, state)
            }
        }
    }
}

@Composable
private fun PeekLayer(peek: EntryPeek, progress: Animatable<Float, *>, state: EntryPeekState) {
    BoxWithConstraints(Modifier.fillMaxSize()) {
        val width = min(maxWidth - Spacing.gutter * 2, Spacing.maxContentWidth)
        var target by remember(peek) { mutableStateOf<Rect?>(null) }
        val cornerPx = with(LocalDensity.current) { CardCorner.toPx() }
        // The chip's touch target reaches a bit past it, so it's easy to land on while moving.
        val slopPx = with(LocalDensity.current) { 12.dp.toPx() }
        val cardMaxHeight = maxHeight * CARD_MAX_HEIGHT
        Column(Modifier.align(Alignment.Center), horizontalAlignment = Alignment.CenterHorizontally) {
            Box(
                Modifier
                    .width(width)
                    .heightIn(max = cardMaxHeight)
                    .onPlaced { target = it.boundsInRoot() }
                    .graphicsLayer {
                        val final = target
                        if (final == null || final.width <= 0f || final.height <= 0f) {
                            alpha = 0f
                            return@graphicsLayer
                        }
                        val raw = progress.value
                        val p = raw.coerceIn(0f, 1f)
                        val origin = peek.origin
                        // Grows out of the held card: its top and centre move into place while the visible
                        // part widens and lengthens from the card's size to the full card.
                        translationX = lerp(origin.center.x - final.center.x, 0f, p)
                        translationY = lerp(origin.top - final.top, 0f, p)
                        val overshoot = 1f + (raw - 1f).coerceAtLeast(0f) * 0.1f
                        scaleX = overshoot
                        scaleY = overshoot
                        transformOrigin = androidx.compose.ui.graphics.TransformOrigin(0.5f, 0f)
                        alpha = (p * 4f).coerceIn(0f, 1f)
                        shape = RevealShape(
                            width = lerp(origin.width.coerceAtMost(final.width), final.width, p),
                            height = lerp(origin.height.coerceAtMost(final.height), final.height, p),
                            corner = cornerPx,
                        )
                        clip = true
                        shadowElevation = 12.dp.toPx() * p
                    },
            ) {
                EntryPeekCard(peek)
            }
            PeekShareChip(
                hovered = state.overShare,
                progress = progress,
                modifier = Modifier
                    .padding(top = Spacing.m)
                    .onGloballyPositioned { state.shareBounds = it.boundsInRoot().inflate(slopPx) },
            )
        }
    }
}

/**
 * The Share chip under the peek card. It can't be tapped (the peek owns the gesture): sliding the
 * finger onto it raises and fills it, and letting go there shares the entry.
 */
@Composable
private fun PeekShareChip(hovered: Boolean, progress: Animatable<Float, *>, modifier: Modifier = Modifier) {
    val colors = MaterialTheme.colorScheme
    val motion = MaterialTheme.motionScheme
    val scale by animateFloatAsState(if (hovered) 1.15f else 1f, motion.fastSpatialSpec(), label = "shareScale")
    val container by animateColorAsState(if (hovered) colors.primary else colors.surfaceContainerHigh, motion.fastEffectsSpec(), label = "shareContainer")
    val content by animateColorAsState(if (hovered) colors.onPrimary else colors.onSurface, motion.fastEffectsSpec(), label = "shareContent")
    Row(
        modifier = modifier
            .graphicsLayer {
                val p = progress.value.coerceIn(0f, 1f)
                alpha = p
                translationY = (1f - p) * -16.dp.toPx()
                scaleX = scale
                scaleY = scale
            }
            .background(container, CircleShape)
            .padding(horizontal = 18.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        DakilioIcon(DakilioSymbols.Share, contentDescription = null, filled = hovered, tint = content, modifier = Modifier.size(20.dp))
        Spacer(Modifier.width(8.dp))
        Text(stringResource(R.string.ui_share), style = MaterialTheme.typography.labelLargeEmphasized, color = content)
    }
}

/** A rounded rectangle of the given size, centered horizontally at the top of the layer. */
private class RevealShape(private val width: Float, private val height: Float, private val corner: Float) : Shape {
    override fun createOutline(size: Size, layoutDirection: LayoutDirection, density: Density): Outline {
        val w = width.coerceAtMost(size.width)
        val h = height.coerceAtMost(size.height)
        val left = (size.width - w) / 2f
        return Outline.Rounded(RoundRect(Rect(Offset(left, 0f), Size(w, h)), CornerRadius(corner)))
    }
}

@Composable
private fun EntryPeekCard(peek: EntryPeek) {
    val post = peek.post
    val colors = MaterialTheme.colorScheme
    val container = colors.surfaceContainerHigh
    val scroll = peek.scroll
    val indicator = colors.onSurfaceVariant.copy(alpha = 0.4f)
    Box(
        Modifier
            .fillMaxWidth()
            .background(container)
            .drawWithContent {
                drawContent()
                val max = scroll.measuredMax().toFloat()
                if (max <= 0f) return@drawWithContent
                val fade = 28.dp.toPx()
                if (scroll.value > 0) {
                    drawRect(Brush.verticalGradient(listOf(container, Color.Transparent), endY = fade), size = Size(size.width, fade))
                }
                if (scroll.value < max) {
                    drawRect(
                        Brush.verticalGradient(listOf(Color.Transparent, container), startY = size.height - fade, endY = size.height),
                        topLeft = Offset(0f, size.height - fade),
                        size = Size(size.width, fade),
                    )
                }
                // Where in the entry the reader is, since there's no scrollbar to grab.
                val inset = CardCorner.toPx() / 2
                val track = size.height - inset * 2
                val thumb = (track * size.height / (size.height + max)).coerceAtLeast(24.dp.toPx())
                val top = inset + (track - thumb) * (scroll.value / max)
                val barWidth = 3.dp.toPx()
                drawRoundRect(
                    indicator,
                    topLeft = Offset(size.width - barWidth - 6.dp.toPx(), top),
                    size = Size(barWidth, thumb),
                    cornerRadius = CornerRadius(barWidth / 2),
                )
            },
    ) {
        // A plain Box isn't a Surface, so text would default to black without this.
        CompositionLocalProvider(LocalContentColor provides colors.onSurface) {
            EntryPeekContent(
                post = post,
                diary = peek.diary,
                highlight = peek.highlight,
                modifier = Modifier.verticalScroll(scroll, enabled = false),
            )
        }
    }
}

/**
 * The entry's content as the peek shows it: mood, date, diary, tags, title, note and photos. Shared
 * with the image made by the Share chip; [photo] draws each photo (by default loaded with Coil).
 */
@Composable
internal fun EntryPeekContent(
    post: Post,
    diary: Diary?,
    highlight: String?,
    modifier: Modifier = Modifier,
    photo: @Composable (index: Int, modifier: Modifier) -> Unit = { index, photoModifier ->
        AsyncImage(
            model = File(post.photos[index].path),
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = photoModifier,
        )
    },
) {
    val formats = rememberFormats()
    val colors = MaterialTheme.colorScheme
    val textScale = LocalEntryDisplay.current.editorTextScale
    Column(
        modifier.padding(horizontal = 20.dp, vertical = 20.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            val mood = post.mood
            if (mood != null) MoodBadge(mood, size = 56.dp) else NoMoodBadge(size = 56.dp)
            Spacer(Modifier.width(16.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    text = mood?.label ?: stringResource(if (post.title != null || post.note != null) R.string.ui_no_mood else R.string.ui_entry),
                    style = MaterialTheme.typography.titleMediumEmphasized,
                    color = if (mood != null) moodColors(mood).accent else colors.onSurfaceVariant,
                )
                Text(
                    text = formats.fullDate(post.date, LocalDate.now()) + "  ·  " + formats.time(post.dateTime.toLocalTime()),
                    style = MaterialTheme.typography.labelLarge,
                    color = colors.onSurfaceVariant,
                )
            }
            diary?.let { diary ->
                DakilioIcon(
                    symbolForKey(diary.iconKey),
                    contentDescription = diary.name,
                    tint = colors.onSurfaceVariant,
                    modifier = Modifier.padding(start = 8.dp).size(20.dp),
                )
            }
        }
        if (post.tags.isNotEmpty()) {
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                post.tags.forEach { TagPill(it) }
            }
        }
        post.title?.let {
            Text(highlighted(it, highlight), style = MaterialTheme.typography.titleLargeEmphasized.scaled(textScale))
        }
        post.note?.let {
            Text(
                highlighted(it, highlight),
                style = MaterialTheme.typography.bodyLarge.scaled(textScale),
                color = colors.onSurfaceVariant,
            )
        }
        if (post.photos.isNotEmpty()) {
            val shape = RoundedCornerShape(18.dp)
            post.photos.forEachIndexed { index, _ ->
                photo(
                    index,
                    Modifier
                        .fillMaxWidth()
                        .aspectRatio(4f / 3f)
                        .clip(shape)
                        .background(colors.surfaceContainerHighest),
                )
            }
        }
    }
}
