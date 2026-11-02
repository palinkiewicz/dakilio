package pl.dakil.dakilio.core.ui

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.isSpecified
import pl.dakil.dakilio.core.model.PreviewLength
import pl.dakil.dakilio.core.model.TextScale

/**
 * How entries are displayed, provided at the app root from `UserSettings`. The text sizes scale
 * only what the user wrote (entry titles and notes), never moods, times, tags or the rest of the UI.
 * [timelineTextScale], [previewLength] and [compactTags] apply to every `PostCard` (timeline and search).
 */
@Immutable
data class EntryDisplay(
    val timelineTextScale: Float = TextScale.DEFAULT,
    val editorTextScale: Float = TextScale.DEFAULT,
    val previewLength: PreviewLength = PreviewLength.Short,
    val compactTags: Boolean = false,
)

val LocalEntryDisplay = compositionLocalOf { EntryDisplay() }

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
