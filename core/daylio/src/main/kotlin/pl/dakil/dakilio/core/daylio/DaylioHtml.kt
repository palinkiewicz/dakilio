package pl.dakil.dakilio.core.daylio

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

private val BLOCKS = setOf("div", "p", "li", "ul", "ol", "h1", "h2", "h3", "h4", "h5", "h6", "blockquote")

/**
 * Turns a Daylio note (HTML) into Dakilio's plain text. Line breaks and block ends become newlines,
 * list items get `•` or `1.`, `2.`… markers, every other tag is dropped (bold, italic… are lost) and
 * entities are decoded.
 */
fun daylioHtmlToText(html: String): String {
    if ('<' !in html && '&' !in html) return html.trim()
    val out = StringBuilder()
    // One counter per open list: null for <ul>, the next number for <ol>.
    val lists = ArrayDeque<IntArray?>()
    var last = 0
    for (match in TAG.findAll(html)) {
        out.append(decodeEntities(html.substring(last, match.range.first)))
        last = match.range.last + 1
        val closing = match.groupValues[1] == "/"
        val name = match.groupValues[2].lowercase()
        when {
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
