package pl.dakil.dakilio.core.ui.component

import android.content.ClipData
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.net.Uri
import android.os.Build
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.graphics.rememberGraphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.layout
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import coil3.compose.asPainter
import coil3.imageLoader
import coil3.request.ImageRequest
import coil3.request.SuccessResult
import java.io.File
import kotlinx.coroutines.flow.first
import pl.dakil.dakilio.core.ui.R

/** The shared image's width in pixels, for an entry laid out [SHARE_LAYOUT_WIDTH_DP] wide. */
internal const val SHARE_WIDTH_PX = 1080

/** How wide the entry is laid out in the image, like a phone screen. */
internal const val SHARE_LAYOUT_WIDTH_DP = 360f

/** Taller entries are drawn smaller, so the bitmap stays a sane size (1080 × 8000 × 4 bytes ≈ 35 MB). */
internal const val MAX_SHARE_HEIGHT_PX = 8_000

/**
 * The pixel density to draw the shared image at: [widthPx] wide for a [layoutWidthDp] layout, scaled
 * down when the entry, measured [measuredHeightPx] tall at that density, would pass [maxHeightPx].
 */
internal fun shareDensity(widthPx: Int, layoutWidthDp: Float, measuredHeightPx: Int, maxHeightPx: Int): Float {
    val base = widthPx / layoutWidthDp
    if (measuredHeightPx <= maxHeightPx || measuredHeightPx <= 0) return base
    return base * maxHeightPx / measuredHeightPx
}

/**
 * Draws [peek]'s whole entry off-screen into a bitmap and hands it to [onRendered] once. Nothing shows:
 * the card is laid out at full height (unbounded) and recorded into a `GraphicsLayer` that's never
 * drawn. Photos are loaded first, so the capture never catches them half loaded.
 */
@Composable
internal fun EntryShareRenderer(peek: EntryPeek, onRendered: (ImageBitmap) -> Unit) {
    val context = LocalContext.current
    val fontScale = LocalDensity.current.fontScale
    val layer = rememberGraphicsLayer()
    val latest by rememberUpdatedState(onRendered)
    var painters by remember(peek) { mutableStateOf<List<Painter?>?>(null) }
    LaunchedEffect(peek) {
        painters = peek.post.photos.map { photo ->
            val request = ImageRequest.Builder(context).data(File(photo.path)).size(SHARE_WIDTH_PX).build()
            (context.imageLoader.execute(request) as? SuccessResult)?.image?.asPainter(context)
        }
    }
    val loaded = painters ?: return
    // Measured once at the full density; a too tall entry is then drawn again, smaller.
    var baseHeight by remember(peek) { mutableIntStateOf(0) }
    val density = shareDensity(SHARE_WIDTH_PX, SHARE_LAYOUT_WIDTH_DP, baseHeight, MAX_SHARE_HEIGHT_PX)
    LaunchedEffect(peek, density) {
        snapshotFlow { baseHeight }.first { it > 0 }
        // A smaller density is on its way, and this effect restarts with it.
        if (shareDensity(SHARE_WIDTH_PX, SHARE_LAYOUT_WIDTH_DP, baseHeight, MAX_SHARE_HEIGHT_PX) != density) return@LaunchedEffect
        // Two frames: a layout and a draw with this density have run, so the layer holds them.
        withFrameNanos { }
        withFrameNanos { }
        latest(layer.toImageBitmap())
    }
    Box(Modifier.size(0.dp)) {
        CompositionLocalProvider(LocalDensity provides Density(density, fontScale)) {
            Box(
                Modifier
                    .layout { measurable, _ ->
                        val placeable = measurable.measure(Constraints.fixedWidth(SHARE_LAYOUT_WIDTH_DP.dp.roundToPx()))
                        layout(0, 0) { placeable.place(0, 0) }
                    }
                    .onSizeChanged { if (baseHeight == 0) baseHeight = it.height }
                    // Recorded, never drawn: the layer is only read back as a bitmap.
                    .drawWithContent { layer.record { this@drawWithContent.drawContent() } },
            ) {
                EntryShareCard(peek, loaded)
            }
        }
    }
}

/** The entry as the peek shows it, whole and without the search highlight, with a small footer. */
@Composable
private fun EntryShareCard(peek: EntryPeek, painters: List<Painter?>) {
    val colors = MaterialTheme.colorScheme
    // An opaque background all around, so the rounded corners don't turn into transparent pixels.
    Column(Modifier.fillMaxWidth().background(colors.surface).padding(16.dp)) {
        Box(
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(32.dp))
                .background(colors.surfaceContainerHigh),
        ) {
            CompositionLocalProvider(LocalContentColor provides colors.onSurface) {
                EntryPeekContent(
                    post = peek.post,
                    diary = peek.diary,
                    highlight = null,
                    photo = { index, modifier ->
                        val painter = painters.getOrNull(index)
                        if (painter != null) {
                            Image(painter, contentDescription = null, contentScale = ContentScale.Crop, modifier = modifier)
                        } else {
                            Box(modifier)
                        }
                    },
                )
            }
        }
        Text(
            stringResource(R.string.ui_share_footer),
            style = MaterialTheme.typography.labelMedium,
            color = colors.onSurfaceVariant,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth().padding(top = 12.dp).align(Alignment.CenterHorizontally),
        )
    }
}

/**
 * Writes [image] to `cacheDir/shared/` (clearing older ones) and returns its content URI from the
 * app's `.share` FileProvider. JPEG when there are photos (a PNG of them gets huge), PNG otherwise,
 * which keeps text crisp. Call off the main thread.
 */
internal fun writeShareImage(context: Context, image: ImageBitmap, postId: Long, hasPhotos: Boolean): Pair<Uri, String> {
    val dir = File(context.cacheDir, "shared").apply {
        deleteRecursively()
        mkdirs()
    }
    val source = image.asAndroidBitmap()
    // Compress needs readable pixels; a capture can come back as a hardware bitmap.
    val bitmap = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && source.config == Bitmap.Config.HARDWARE) {
        source.copy(Bitmap.Config.ARGB_8888, false)
    } else {
        source
    }
    val (format, extension, mime) = if (hasPhotos) {
        Triple(Bitmap.CompressFormat.JPEG, "jpg", "image/jpeg")
    } else {
        Triple(Bitmap.CompressFormat.PNG, "png", "image/png")
    }
    val file = File(dir, "entry-$postId.$extension")
    file.outputStream().use { bitmap.compress(format, 92, it) }
    return FileProvider.getUriForFile(context, "${context.packageName}.share", file) to mime
}

/** The system share sheet for an image written by [writeShareImage]. */
internal fun shareImageIntent(uri: Uri, mime: String): Intent {
    val send = Intent(Intent.ACTION_SEND).apply {
        type = mime
        putExtra(Intent.EXTRA_STREAM, uri)
        // ClipData gives the share sheet its preview, and carries the read grant.
        clipData = ClipData.newRawUri(null, uri)
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }
    return Intent.createChooser(send, null)
}
