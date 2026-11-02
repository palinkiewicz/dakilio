package pl.dakil.dakilio.core.ui.mood

import androidx.compose.material3.MaterialShapes
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Color
import androidx.graphics.shapes.Morph
import androidx.graphics.shapes.RoundedPolygon
import pl.dakil.dakilio.core.designsystem.theme.LocalDarkTheme
import pl.dakil.dakilio.core.ui.color.AccentColors
import pl.dakil.dakilio.core.ui.color.TagColor
import pl.dakil.dakilio.core.ui.color.deriveAccentColors
import pl.dakil.dakilio.core.model.MOOD_LEVELS
import pl.dakil.dakilio.core.model.Mood

/**
 * How a mood looks: a resting MaterialShapes polygon that morphs into a livelier one when the mood is
 * picked, and how much its face smiles (-1 = deep frown … 1 = big grin). Tension reads as spikes,
 * contentment as soft petals and sun rays.
 */
@Immutable
class MoodGlyph(rest: RoundedPolygon, active: RoundedPolygon, val smile: Float) {
    val morph: Morph = Morph(rest, active)
}

/** One face per level, worst first. Aliases share their level's face; only the name differs. */
private val glyphsByLevel: List<MoodGlyph> by lazy {
    listOf(
        MoodGlyph(MaterialShapes.SoftBoom, MaterialShapes.Boom, smile = -1f),
        MoodGlyph(MaterialShapes.Cookie4Sided, MaterialShapes.Clover4Leaf, smile = -0.5f),
        MoodGlyph(MaterialShapes.Circle, MaterialShapes.Cookie12Sided, smile = 0f),
        MoodGlyph(MaterialShapes.Cookie9Sided, MaterialShapes.Flower, smile = 0.55f),
        MoodGlyph(MaterialShapes.Sunny, MaterialShapes.VerySunny, smile = 1f),
    )
}

/**
 * Light-mode mark color per level, worst first: a diverging scale (warm = heavy, cool = light) with a
 * calm periwinkle middle (crimson, orange, periwinkle, green, teal), validated for CVD and
 * normal-vision separation of neighbours. Dark tones are derived at runtime ([deriveAccentColors]).
 * A mood shows these unless it (or its level's primary) has a palette color of its own ([Mood.color]).
 */
private val seedColorsByLevel = listOf(0xFFBD1F44, 0xFFE98420, 0xFF7A6BC1, 0xFF6BC456, 0xFF009298).map { Color(it.toInt()) }

private fun levelIndex(level: Int) = (level - 1).coerceIn(0, MOOD_LEVELS - 1)

fun moodGlyph(mood: Mood): MoodGlyph = glyphsByLevel[levelIndex(mood.level)]

/** A mood's colors in the current theme: its own color, else its primary's, else its level's. */
typealias MoodColors = AccentColors

@Composable
fun moodColors(mood: Mood): MoodColors {
    val dark = LocalDarkTheme.current
    val seed = TagColor.fromKey(mood.color)?.seed ?: seedColorsByLevel[levelIndex(mood.level)]
    return remember(seed, dark) { deriveAccentColors(seed, dark) }
}

/** The built-in colors of [level] in the current theme, which moods without a color of their own show. */
@Composable
fun levelColors(level: Int): MoodColors {
    val dark = LocalDarkTheme.current
    return remember(level, dark) { deriveAccentColors(seedColorsByLevel[levelIndex(level)], dark) }
}
