package pl.dakil.dakilio.feature.export.impl

import kotlin.math.min

/**
 * How many lines from [start] fit in [available] points. [lineBottoms] holds each line's bottom
 * measured from the top of the whole text, so lines of any height work. At least one line is
 * always taken when [available] covers it on an empty page ([force]), so a single line taller than a
 * page can't stall the layout.
 */
fun linesThatFit(lineBottoms: FloatArray, start: Int, available: Float, force: Boolean = false): Int {
    if (start >= lineBottoms.size) return 0
    val top = if (start == 0) 0f else lineBottoms[start - 1]
    var count = 0
    while (start + count < lineBottoms.size && lineBottoms[start + count] - top <= available) count++
    return if (count == 0 && force) 1 else count
}

/**
 * The size a photo of [width] × [height] is drawn at: as wide as [maxWidth] allows, no taller than
 * [maxHeight], never scaled up past its [maxScale] (points per pixel).
 */
fun photoBox(width: Int, height: Int, maxWidth: Float, maxHeight: Float, maxScale: Float = 1f): Pair<Float, Float> {
    if (width <= 0 || height <= 0) return 0f to 0f
    val scale = min(min(maxWidth / width, maxHeight / height), maxScale)
    return width * scale to height * scale
}

/** The JPEG decoder's power-of-two subsample that brings [longEdge] down to about [target] pixels. */
fun sampleSize(longEdge: Int, target: Int): Int {
    var sample = 1
    while (longEdge / (sample * 2) >= target) sample *= 2
    return sample
}
