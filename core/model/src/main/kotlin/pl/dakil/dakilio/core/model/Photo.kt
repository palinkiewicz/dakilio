package pl.dakil.dakilio.core.model

import kotlin.math.max
import kotlin.math.roundToInt
import kotlin.math.sqrt

/** The pixel size of a stored photo. */
data class PixelSize(val width: Int, val height: Int) {
    val longEdge: Int get() = max(width, height)
}

/** How a photo is scaled down when it's stored, following [PhotoSize]. */
object PhotoSizing {
    /**
     * The most pixels [PhotoSize.Original] keeps (4096², about 16.8 MP, which leaves standard 12 and
     * 16 MP phone photos untouched). Larger photos are scaled down to it, since decoding a 50–200 MP
     * photo in full would run out of memory.
     */
    const val MAX_ORIGINAL_PIXELS = 4096L * 4096

    /**
     * The size a [width] × [height] photo is stored at when its long edge may be at most [maxEdge]
     * (null: no limit but [MAX_ORIGINAL_PIXELS]), or null when it already fits. The aspect ratio is
     * kept and neither side drops below one pixel.
     */
    fun scaledSize(width: Int, height: Int, maxEdge: Int?): PixelSize? {
        if (width <= 0 || height <= 0) return null
        val longEdge = max(width, height)
        val scale = if (maxEdge != null) {
            if (longEdge <= maxEdge) return null
            maxEdge.toDouble() / longEdge
        } else {
            val pixels = width.toLong() * height
            if (pixels <= MAX_ORIGINAL_PIXELS) return null
            sqrt(MAX_ORIGINAL_PIXELS.toDouble() / pixels)
        }
        return PixelSize(
            width = (width * scale).roundToInt().coerceIn(1, width),
            height = (height * scale).roundToInt().coerceIn(1, height),
        )
    }

    /** Whether a photo of [size] is larger than [photoSize] allows. */
    fun exceeds(size: PixelSize, photoSize: PhotoSize): Boolean = scaledSize(size.width, size.height, photoSize.maxEdge) != null
}
