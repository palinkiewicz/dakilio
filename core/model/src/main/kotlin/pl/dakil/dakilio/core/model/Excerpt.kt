package pl.dakil.dakilio.core.model

/** A match that starts within this many characters is visible in a card preview anyway. */
private const val VISIBLE_LEAD = 80

/** How much text to keep before a later match, so it reads in context. */
private const val CONTEXT_BEFORE = 40

/**
 * The part of a note to preview for a search [query]: the whole [text] when its first match is
 * near the start (or there is none, e.g. the hit was in the title or a tag), otherwise the text
 * from shortly before the match, cut at a line start or word boundary and prefixed with "…".
 */
fun noteExcerpt(text: String, query: String): String {
    if (query.isBlank()) return text
    val match = text.indexOf(query, ignoreCase = true)
    if (match <= VISIBLE_LEAD) return text
    val windowStart = (match - CONTEXT_BEFORE).coerceAtLeast(0)
    val lineStart = text.lastIndexOf('\n', match - 1)
    val start = if (lineStart >= windowStart) {
        lineStart + 1
    } else {
        // The first word that starts inside the window; the match itself at worst.
        val space = text.indexOfAny(charArrayOf(' ', '\t'), windowStart)
        if (space in 0 until match) space + 1 else match
    }
    return "…" + text.substring(start).trimStart()
}
