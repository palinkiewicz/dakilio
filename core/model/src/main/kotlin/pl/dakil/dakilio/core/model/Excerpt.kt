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

/** A short piece of a text with the [ranges] to highlight in it. */
data class Excerpt(val text: String, val ranges: List<IntRange>)

/**
 * A short one-paragraph preview of [text] around its first match: from shortly before it (at a line
 * start or word boundary, "…" when cut) for at most about [maxLength] characters (cut at a word,
 * "…" when cut). Line breaks and runs of spaces become single spaces. [ranges] (sorted, as from
 * [Keywords.matchRanges]) are clipped and moved into the excerpt.
 */
fun keywordExcerpt(text: String, ranges: List<IntRange>, before: Int = CONTEXT_BEFORE, maxLength: Int = 160): Excerpt {
    val firstRange = ranges.firstOrNull()
    val first = firstRange?.first?.coerceIn(0, text.length) ?: 0
    var start = if (first <= before) {
        0
    } else {
        val windowStart = first - before
        val lineStart = text.lastIndexOf('\n', first - 1)
        if (lineStart >= windowStart) {
            lineStart + 1
        } else {
            val space = text.indexOfAny(charArrayOf(' ', '\t'), windowStart)
            if (space in 0 until first) space + 1 else first
        }
    }
    while (start < first && text[start].isWhitespace()) start++

    var end = text.length
    if (end - start > maxLength) {
        val firstEnd = ((firstRange?.last ?: -1) + 1).coerceIn(start, text.length)
        val cut = maxOf(start + maxLength, firstEnd).coerceAtMost(text.length)
        var space = cut
        while (space > firstEnd && !text[space - 1].isWhitespace()) space--
        end = if (space > firstEnd) space - 1 else cut
    }

    val out = StringBuilder()
    if (start > 0) out.append('…')
    // Where each character of the window (and its end) lands in the excerpt.
    val at = IntArray(end - start + 1)
    for (i in start until end) {
        at[i - start] = out.length
        val c = text[i]
        if (!c.isWhitespace()) {
            out.append(c)
        } else if (out.isNotEmpty() && out.last() != ' ') {
            out.append(' ')
        }
    }
    at[end - start] = out.length
    while (out.endsWith(' ')) out.setLength(out.length - 1)
    val body = out.length
    if (end < text.length) out.append('…')

    val shifted = ranges.mapNotNull { range ->
        val s = maxOf(range.first, start)
        val e = minOf(range.last + 1, end)
        if (s >= e) return@mapNotNull null
        val from = at[s - start].coerceAtMost(body)
        val to = at[e - start].coerceAtMost(body)
        if (from >= to) null else from until to
    }
    return Excerpt(out.toString(), shifted)
}
