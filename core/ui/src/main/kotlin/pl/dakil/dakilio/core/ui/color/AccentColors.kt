package pl.dakil.dakilio.core.ui.color

import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.Color
import kotlin.math.atan2
import kotlin.math.cbrt
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.sqrt

/** The tones of one hue in the current theme: used for moods and for tag colors. */
@Immutable
data class AccentColors(
    /** Fill (badges, selected tags) and chart mark color. */
    val container: Color,
    /** Glyph / content on [container] (near-white or near-black, by the fill's lightness). */
    val onContainer: Color,
    /** Hue-tinted text color with readable contrast on the app's surfaces. */
    val accent: Color,
    /** A whisper of the hue for large surfaces (selected rows). */
    val tint: Color,
    /** A light fill at the weight of `surfaceContainerHighest`, for small chips; [accent] reads on it. */
    val soft: Color,
)

/**
 * [seed] is a light-mode mark color. Dark mode remaps its OKLCH lightness into the dark-surface band
 * (lifting it only as far as needed to keep the hue saturated), so a set of seeds stays
 * distinguishable — including under color-vision deficiency, for the mood scale — in both themes.
 */
fun deriveAccentColors(seed: Color, dark: Boolean): AccentColors {
    val (l0, c, h) = seed.toOklch()
    val lightL = l0.coerceIn(LIGHT_BAND_MIN, LIGHT_BAND_MAX)
    val markL = if (!dark) {
        lightL
    } else {
        var l = DARK_BAND_MIN + (lightL - LIGHT_BAND_MIN) / (LIGHT_BAND_MAX - LIGHT_BAND_MIN) * (DARK_BAND_MAX - DARK_BAND_MIN)
        val wanted = min(c, 0.102f)
        while (maxChroma(l, h) < wanted && l < DARK_BAND_MAX) l += 0.005f
        l
    }
    val container = oklch(markL, c, h)
    return AccentColors(
        container = container,
        onContainer = if (markL < 0.62f) oklch(0.98f, 0.015f, h) else oklch(0.24f, min(c, 0.06f), h),
        accent = if (dark) oklch(max(markL, 0.8f), c * 0.8f, h) else oklch(min(markL, 0.5f), c, h),
        tint = if (dark) oklch(0.27f, c * 0.3f, h) else oklch(0.955f, c * 0.22f, h),
        soft = if (dark) oklch(0.33f, c * 0.4f, h) else oklch(0.91f, c * 0.35f, h),
    )
}

private const val LIGHT_BAND_MIN = 0.43f
private const val LIGHT_BAND_MAX = 0.77f
private const val DARK_BAND_MIN = 0.48f
private const val DARK_BAND_MAX = 0.67f

private fun Color.toOklch(): Triple<Float, Float, Float> {
    fun lin(c: Float) = if (c <= 0.04045f) c / 12.92f else ((c + 0.055f) / 1.055f).pow(2.4f)
    val r = lin(red)
    val g = lin(green)
    val b = lin(blue)
    val l = cbrt(0.4122214708f * r + 0.5363325363f * g + 0.0514459929f * b)
    val m = cbrt(0.2119034982f * r + 0.6806995451f * g + 0.1073969566f * b)
    val s = cbrt(0.0883024619f * r + 0.2817188376f * g + 0.6299787005f * b)
    val labL = 0.2104542553f * l + 0.7936177850f * m - 0.0040720468f * s
    val labA = 1.9779984951f * l - 2.4285922050f * m + 0.4505937099f * s
    val labB = 0.0259040371f * l + 0.7827717662f * m - 0.8086757660f * s
    val hue = Math.toDegrees(atan2(labB, labA).toDouble()).toFloat().let { if (it < 0) it + 360f else it }
    return Triple(labL, sqrt(labA * labA + labB * labB), hue)
}

private fun oklchToLinear(l: Float, c: Float, h: Float): FloatArray {
    val rad = Math.toRadians(h.toDouble())
    val a = (c * cos(rad)).toFloat()
    val b = (c * sin(rad)).toFloat()
    val l1 = (l + 0.3963377774f * a + 0.2158037573f * b).let { it * it * it }
    val m1 = (l - 0.1055613458f * a - 0.0638541728f * b).let { it * it * it }
    val s1 = (l - 0.0894841775f * a - 1.2914855480f * b).let { it * it * it }
    return floatArrayOf(
        4.0767416621f * l1 - 3.3077115913f * m1 + 0.2309699292f * s1,
        -1.2684380046f * l1 + 2.6097574011f * m1 - 0.3413193965f * s1,
        -0.0041960863f * l1 - 0.7034186147f * m1 + 1.7076147010f * s1,
    )
}

private fun inGamut(rgb: FloatArray) = rgb.all { it in -0.0005f..1.0005f }

private fun maxChroma(l: Float, h: Float): Float {
    var c = 0.4f
    while (c > 0f && !inGamut(oklchToLinear(l, c, h))) c -= 0.002f
    return max(c, 0f)
}

/** OKLCH to sRGB, reducing chroma until the color fits the gamut. */
internal fun oklch(l: Float, c: Float, h: Float): Color {
    var chroma = c
    var rgb = oklchToLinear(l, chroma, h)
    while (chroma > 0f && !inGamut(rgb)) {
        chroma -= 0.002f
        rgb = oklchToLinear(l, chroma, h)
    }
    fun srgb(x: Float): Float {
        val v = x.coerceIn(0f, 1f)
        return if (v <= 0.0031308f) 12.92f * v else 1.055f * v.pow(1f / 2.4f) - 0.055f
    }
    return Color(srgb(rgb[0]), srgb(rgb[1]), srgb(rgb[2]))
}
