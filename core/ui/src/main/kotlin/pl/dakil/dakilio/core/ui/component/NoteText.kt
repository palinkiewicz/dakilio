package pl.dakil.dakilio.core.ui.component

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.em
import pl.dakil.dakilio.core.model.NoteMarkup
import pl.dakil.dakilio.core.model.NoteStyle

/** A heading in a note: bolder and a quarter larger than the text around it, whatever size that is. */
private val HeadingStyle = SpanStyle(fontWeight = FontWeight.Bold, fontSize = 1.25.em)
private val BoldStyle = SpanStyle(fontWeight = FontWeight.Bold)

// Google Sans Flex has no italic of its own in the bundled subset; Compose slants the upright one.
private val ItalicStyle = SpanStyle(fontStyle = FontStyle.Italic)

/**
 * A note as it's shown everywhere it's read (post cards, the peek, an entry's read mode, the shared
 * image): its markers (`**bold**`, `*italic*`, `# heading`) gone and their formatting applied, see
 * [NoteMarkup]. With [highlight] (a search), every match in the shown text is marked like
 * [highlighted] does.
 */
@Composable
fun formattedNote(note: String, highlight: String? = null): AnnotatedString {
    val colors = MaterialTheme.colorScheme
    return remember(note, highlight, colors.tertiaryContainer, colors.onTertiaryContainer) {
        val formatted = NoteMarkup.parse(note)
        if (formatted.isPlain && highlight.isNullOrBlank()) return@remember AnnotatedString(formatted.text)
        buildAnnotatedString {
            append(formatted.text)
            formatted.spans.forEach { span ->
                addStyle(
                    when (span.style) {
                        NoteStyle.Bold -> BoldStyle
                        NoteStyle.Italic -> ItalicStyle
                        NoteStyle.Heading -> HeadingStyle
                    },
                    span.start,
                    span.end,
                )
            }
            if (!highlight.isNullOrBlank()) {
                val mark = SpanStyle(background = colors.tertiaryContainer, color = colors.onTertiaryContainer)
                var start = formatted.text.indexOf(highlight, ignoreCase = true)
                while (start >= 0) {
                    addStyle(mark, start, start + highlight.length)
                    start = formatted.text.indexOf(highlight, start + highlight.length, ignoreCase = true)
                }
            }
        }
    }
}
