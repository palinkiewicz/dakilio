package pl.dakil.dakilio.core.ui

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.isSpecified
import pl.dakil.dakilio.core.model.TextScale

/**
 * The user's entry text sizes, provided at the app root from `UserSettings`. They scale only what
 * the user wrote (entry titles and notes), never moods, times, tags or the rest of the UI.
 */
@Immutable
data class EntryTextScale(
    val timeline: Float = TextScale.DEFAULT,
    val editor: Float = TextScale.DEFAULT,
)

val LocalEntryTextScale = compositionLocalOf { EntryTextScale() }

/** This style with its font size and line height multiplied by [scale]. */
fun TextStyle.scaled(scale: Float): TextStyle =
    if (scale == 1f) {
        this
    } else {
        copy(
            fontSize = if (fontSize.isSpecified) fontSize * scale else fontSize,
            lineHeight = if (lineHeight.isSpecified) lineHeight * scale else lineHeight,
        )
    }
