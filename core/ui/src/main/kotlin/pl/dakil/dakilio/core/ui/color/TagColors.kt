package pl.dakil.dakilio.core.ui.color

import androidx.annotation.StringRes
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Color
import pl.dakil.dakilio.core.designsystem.theme.LocalDarkTheme
import pl.dakil.dakilio.core.ui.R

/**
 * The palette of tag colors, which moods can pick from too. Tags, groups and moods store a color's
 * [key], never its value, so the tones can be retuned without touching the database; never rename a key.
 *
 * Seeds are light-mode mark colors in OKLCH (lightness, chroma, hue), spread around the hue circle
 * at similar weight so no color dominates. Like the mood colors, the dark-theme tones are derived
 * from them ([deriveAccentColors]).
 */
enum class TagColor(val key: String, @StringRes val label: Int, l: Float, c: Float, h: Float) {
    Red("red", R.string.ui_tag_color_red, 0.56f, 0.19f, 25f),
    Orange("orange", R.string.ui_tag_color_orange, 0.67f, 0.17f, 50f),
    Amber("amber", R.string.ui_tag_color_amber, 0.76f, 0.15f, 80f),
    Lime("lime", R.string.ui_tag_color_lime, 0.70f, 0.17f, 128f),
    Green("green", R.string.ui_tag_color_green, 0.58f, 0.14f, 152f),
    Teal("teal", R.string.ui_tag_color_teal, 0.60f, 0.10f, 192f),
    Sky("sky", R.string.ui_tag_color_sky, 0.64f, 0.12f, 235f),
    Blue("blue", R.string.ui_tag_color_blue, 0.52f, 0.17f, 262f),
    Violet("violet", R.string.ui_tag_color_violet, 0.54f, 0.17f, 298f),
    Pink("pink", R.string.ui_tag_color_pink, 0.62f, 0.18f, 350f),
    Brown("brown", R.string.ui_tag_color_brown, 0.52f, 0.07f, 55f),
    Slate("slate", R.string.ui_tag_color_slate, 0.55f, 0.03f, 250f),
    ;

    val seed: Color = oklch(l, c, h)

    companion object {
        private val byKey = entries.associateBy { it.key }

        /** `null` for no color, and for a key this version doesn't know. */
        fun fromKey(key: String?): TagColor? = key?.let(byKey::get)
    }
}

/** The tones of [color] in the current theme. */
@Composable
fun tagColors(color: TagColor): AccentColors {
    val dark = LocalDarkTheme.current
    return remember(color, dark) { deriveAccentColors(color.seed, dark) }
}

/** The tones of the color stored under [key] in the current theme, or `null` when it has none. */
@Composable
fun tagColors(key: String?): AccentColors? = TagColor.fromKey(key)?.let { tagColors(it) }
