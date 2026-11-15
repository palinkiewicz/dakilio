package pl.dakil.dakilio.core.daylio

import pl.dakil.dakilio.core.model.NoteMarkup

private val TAG = Regex("<(/?)([a-zA-Z][a-zA-Z0-9]*)[^>]*?(/?)>")
private val ENTITY = Regex("&(#[0-9]+|#[xX][0-9a-fA-F]+|[a-zA-Z]+);")
private val EXTRA_NEWLINES = Regex("\n{3,}")
private val TRAILING_SPACES = Regex("[ \\t]+\n")

private val NAMED_ENTITIES = mapOf(
    "amp" to "&",
    "lt" to "<",
    "gt" to ">",
    "quot" to "\"",
    "apos" to "'",
    "nbsp" to " ",
)

private val BOLD_TAGS = setOf("b", "strong")
private val ITALIC_TAGS = setOf("i", "em")

private val BLOCKS = setOf("div", "p", "li", "ul", "ol", "h1", "h2", "h3", "h4", "h5", "h6", "blockquote")

/**
 * Turns a Daylio note (HTML) into a Dakilio note. Line breaks and block ends become newlines, list
 * items get `•` or `1.`, `2.`… markers, bold and italic become Dakilio's own markers (`**…**`,
 * `*…*`, see [NoteMarkup]), every other tag is dropped (underline and strike-through are lost) and
 * entities are decoded.
 */
fun daylioHtmlToText(html: String): String {
    if ('<' !in html && '&' !in html) return html.trim()
    val out = StringBuilder()
    // One counter per open list: null for <ul>, the next number for <ol>.
    val lists = ArrayDeque<IntArray?>()
    // Open bold and italic tags: the marker and where their text starts in [out].
    val marks = ArrayDeque<Pair<String, Int>>()
    var last = 0
    for (match in TAG.findAll(html)) {
        out.append(decodeEntities(html.substring(last, match.range.first)))
        last = match.range.last + 1
        val closing = match.groupValues[1] == "/"
        val name = match.groupValues[2].lowercase()
        val marker = when (name) {
            in BOLD_TAGS -> NoteMarkup.BOLD
            in ITALIC_TAGS -> NoteMarkup.ITALIC
            else -> null
        }
        when {
            marker != null && !closing -> marks.addLast(marker to out.length)
            marker != null -> {
                // The innermost open tag of this kind; a stray closing tag is ignored.
                val index = marks.indexOfLast { it.first == marker }
                if (index >= 0) {
                    val start = marks.removeAt(index).second.coerceAtMost(out.length)
                    val wrapped = NoteMarkup.wrap(out.toString(), start, out.length, marker)
                    out.setLength(0)
                    out.append(wrapped)
                }
            }
            name == "br" -> out.append('\n')
            !closing && name == "ul" -> lists.addLast(null)
            !closing && name == "ol" -> lists.addLast(intArrayOf(1))
            closing && (name == "ul" || name == "ol") -> {
                lists.removeLastOrNull()
                out.newlineIfNeeded()
            }
            !closing && name == "li" -> {
                out.newlineIfNeeded()
                val counter = lists.lastOrNull()
                if (counter == null) {
                    out.append("• ")
                } else {
                    out.append(counter[0]).append(". ")
                    counter[0]++
                }
            }
            closing && name in BLOCKS -> out.newlineIfNeeded()
            !closing && name in BLOCKS -> out.newlineIfNeeded()
        }
    }
    out.append(decodeEntities(html.substring(last)))
    return out.toString()
        .replace("\r\n", "\n")
        .replace(TRAILING_SPACES, "\n")
        .replace(EXTRA_NEWLINES, "\n\n")
        .trim()
}

/** Starts a new line unless the text is empty or already ends with one. */
private fun StringBuilder.newlineIfNeeded() {
    if (isNotEmpty() && last() != '\n') append('\n')
}

private fun decodeEntities(text: String): String {
    if ('&' !in text) return text
    return ENTITY.replace(text) { match ->
        val body = match.groupValues[1]
        when {
            body.startsWith("#x") || body.startsWith("#X") -> body.substring(2).toIntOrNull(16)?.let(::codePoint)
            body.startsWith("#") -> body.substring(1).toIntOrNull()?.let(::codePoint)
            else -> NAMED_ENTITIES[body]
        } ?: match.value
    }
}

private fun codePoint(value: Int): String? =
    if (Character.isValidCodePoint(value)) String(Character.toChars(value)) else null
