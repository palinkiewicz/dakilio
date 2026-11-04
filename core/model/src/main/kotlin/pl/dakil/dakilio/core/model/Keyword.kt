package pl.dakil.dakilio.core.model

import java.text.Normalizer

/**
 * Case- and accent-insensitive form used for matching ("Zażółć" matches "zazolc"): decomposed, combining
 * marks dropped, ł → l, lowercased. Folds character by character with a cache, since normalizing whole
 * notes is slow when thousands are searched at once.
 */
fun String.foldForSearch(): String {
    var plain = true
    for (c in this) if (c.code >= 0x80 || c in 'A'..'Z') { plain = false; break }
    if (plain) return this
    val out = StringBuilder(length)
    for (c in this) {
        when {
            c.code < 0x80 -> out.append(if (c in 'A'..'Z') c + 32 else c)
            else -> out.append(FOLDED.getOrPut(c) { foldSlow(c.toString()) })
        }
    }
    return out.toString()
}

private fun foldSlow(text: String): String =
    Normalizer.normalize(text, Normalizer.Form.NFD)
        .replace(COMBINING_MARKS, "")
        .replace('ł', 'l')
        .replace('Ł', 'L')
        .lowercase()

private val FOLDED = java.util.concurrent.ConcurrentHashMap<Char, String>()

private val COMBINING_MARKS = "\\p{Mn}+".toRegex()

/**
 * A word that suggests a tag (the Keywords extra). When it shows up in an entry's title or note,
 * the editor suggests (or adds) [tagId].
 *
 * @property word as the user typed it; may be a phrase of several words.
 * @property approximate when set, the last word of [word] also matches longer words starting with
 *   it ("rower" matches "rowerem"); otherwise words must match exactly. Both ignore case and accents.
 */
data class Keyword(
    val id: Long,
    val tagId: Long,
    val word: String,
    val approximate: Boolean,
)

/** A keyword as edited, before it's saved. */
data class KeywordDraft(val word: String, val approximate: Boolean)

object Keywords {
    /** The folded words of [text]: runs of letters and digits. */
    fun tokenize(text: String): List<String> {
        val folded = text.foldForSearch()
        val tokens = ArrayList<String>()
        var start = -1
        for (i in folded.indices) {
            if (folded[i].isLetterOrDigit()) {
                if (start < 0) start = i
            } else if (start >= 0) {
                tokens += folded.substring(start, i)
                start = -1
            }
        }
        if (start >= 0) tokens += folded.substring(start)
        return tokens
    }

    /** The ids of the tags whose keywords appear in [text]. */
    fun match(text: String, keywords: List<Keyword>): Set<Long> = KeywordMatcher(keywords).match(text)

    /**
     * Where [keywords] appear in [text], as sorted, non-overlapping ranges of the original text (for
     * highlighting). An approximate match covers the whole word ("rowerem" for "rower").
     */
    fun matchRanges(text: String, keywords: List<Keyword>): List<IntRange> = KeywordMatcher(keywords).ranges(text)

    /**
     * The words of [text] like [tokenize], each with where it sits in [text]. Folding may turn one
     * character into several, so every folded character remembers the character it came from.
     */
    internal fun spannedTokens(text: String): List<Token> {
        val folded = StringBuilder(text.length)
        var source = IntArray(text.length + 1)
        for (i in text.indices) {
            val c = text[i]
            val f = when {
                c.code < 0x80 -> if (c in 'A'..'Z') (c + 32).toString() else c.toString()
                else -> FOLDED.getOrPut(c) { foldSlow(c.toString()) }
            }
            if (folded.length + f.length >= source.size) source = source.copyOf(maxOf(source.size * 2, folded.length + f.length + 1))
            for (k in f.indices) source[folded.length + k] = i
            folded.append(f)
        }
        val tokens = ArrayList<Token>()
        var start = -1
        for (i in 0..folded.length) {
            val letter = i < folded.length && folded[i].isLetterOrDigit()
            if (letter) {
                if (start < 0) start = i
            } else if (start >= 0) {
                tokens += Token(folded.substring(start, i), source[start], source[i - 1] + 1)
                start = -1
            }
        }
        return tokens
    }

    /** Whether [keyword] appears in the already tokenized [tokens]. */
    fun matches(tokens: List<String>, keyword: Keyword): Boolean =
        KeywordMatcher(listOf(keyword)).matchTokens(TokenIndex(tokens)).isNotEmpty()

    /**
     * [text] without the word the cursor is still typing: the word ending at [cursor] when nothing but
     * more letters may follow. A word counts once a separator is typed after it, so a half-typed word
     * never flashes a suggestion.
     */
    fun settledText(text: String, cursor: Int): String {
        val at = cursor.coerceIn(0, text.length)
        if (at == 0 || !text[at - 1].isLetterOrDigit()) return text
        var start = at
        while (start > 0 && text[start - 1].isLetterOrDigit()) start--
        var end = at
        while (end < text.length && text[end].isLetterOrDigit()) end++
        return text.substring(0, start) + " " + text.substring(end)
    }

    /** Whether a draft's [word] has anything to match (at least one letter or digit). */
    fun isUsable(word: String): Boolean = tokenize(word).isNotEmpty()

    /** The folded form a keyword is compared by, for spotting duplicates. */
    fun key(word: String): String = tokenize(word).joinToString(" ")

    /**
     * Posts whose title or note contain one of [keywords] but don't carry its tag yet, by tag id.
     * Tags not in [activeTagIds] (archived or gone) are skipped.
     */
    fun pastMatches(posts: List<Post>, keywords: List<Keyword>, activeTagIds: Set<Long>): Map<Long, List<Long>> {
        val matcher = KeywordMatcher(keywords.filter { it.tagId in activeTagIds })
        if (matcher.isEmpty) return emptyMap()
        val result = LinkedHashMap<Long, MutableList<Long>>()
        for (post in posts) {
            val title = post.title
            val note = post.note
            if (title.isNullOrBlank() && note.isNullOrBlank()) continue
            val text = if (title.isNullOrBlank()) note!! else if (note.isNullOrBlank()) title else "$title\n$note"
            val tagIds = matcher.match(text)
            if (tagIds.isEmpty()) continue
            for (tagId in tagIds) {
                if (post.tags.none { it.id == tagId }) result.getOrPut(tagId) { mutableListOf() } += post.id
            }
        }
        return result
    }

}

/** A folded word of a text, at [start] until [end] (exclusive) of the original text. */
internal class Token(val word: String, val start: Int, val end: Int)

/** The words of one text, indexed for fast lookups. */
internal class TokenIndex(val tokens: List<String>) {
    val set: HashSet<String> = tokens.toHashSet()

    /** The distinct words, sorted, for prefix lookups. */
    private val sorted: Array<String> by lazy { set.toTypedArray().also { it.sort() } }

    /** Whether some word starts with [prefix]. */
    fun hasPrefix(prefix: String): Boolean {
        if (prefix in set) return true
        val words = sorted
        // The first word >= prefix is the only candidate: words starting with it sort right after it.
        var low = 0
        var high = words.size
        while (low < high) {
            val mid = (low + high) ushr 1
            if (words[mid] < prefix) low = mid + 1 else high = mid
        }
        return low < words.size && words[low].startsWith(prefix)
    }
}

/**
 * [keywords] folded and split once, so one text can be checked against all of them quickly: single
 * words are hash lookups (or a binary search, when approximate), phrases scan only from words that
 * match their first part.
 */
class KeywordMatcher(keywords: List<Keyword>) {
    private class Compiled(val tagId: Long, val words: List<String>, val approximate: Boolean) {
        /** Whether [token] fits this keyword's word at [index]: as is, or as a prefix for the last approximate one. */
        fun accepts(token: String, index: Int): Boolean =
            if (approximate && index == words.lastIndex) token.startsWith(words[index]) else token == words[index]

        /** Whether the keyword starts at [start] of [tokens]. */
        fun startsAt(tokens: List<String>, start: Int): Boolean {
            if (start + words.size > tokens.size) return false
            for (i in words.indices) if (!accepts(tokens[start + i], i)) return false
            return true
        }
    }

    private val compiled = keywords.mapNotNull { keyword ->
        Keywords.tokenize(keyword.word).takeIf { it.isNotEmpty() }?.let { Compiled(keyword.tagId, it, keyword.approximate) }
    }

    val isEmpty: Boolean get() = compiled.isEmpty()

    /** The ids of the tags whose keywords appear in [text]. */
    fun match(text: String): Set<Long> {
        if (compiled.isEmpty()) return emptySet()
        val tokens = Keywords.tokenize(text)
        if (tokens.isEmpty()) return emptySet()
        return matchTokens(TokenIndex(tokens))
    }

    /** Where the keywords appear in [text]: sorted, merged ranges of the original text. */
    fun ranges(text: String): List<IntRange> {
        if (compiled.isEmpty()) return emptyList()
        val tokens = Keywords.spannedTokens(text)
        if (tokens.isEmpty()) return emptyList()
        val words = tokens.map { it.word }
        val found = ArrayList<IntRange>()
        for (keyword in compiled) {
            for (start in words.indices) {
                if (keyword.startsAt(words, start)) found += tokens[start].start until tokens[start + keyword.words.size - 1].end
            }
        }
        if (found.size < 2) return found
        found.sortBy { it.first }
        val merged = ArrayList<IntRange>(found.size)
        for (range in found) {
            val last = merged.lastOrNull()
            if (last != null && range.first <= last.last + 1) {
                merged[merged.lastIndex] = last.first..maxOf(last.last, range.last)
            } else {
                merged += range
            }
        }
        return merged
    }

    internal fun matchTokens(index: TokenIndex): Set<Long> {
        val result = LinkedHashSet<Long>()
        for (keyword in compiled) {
            if (keyword.tagId in result) continue
            if (matches(index, keyword)) result += keyword.tagId
        }
        return result
    }

    private fun matches(index: TokenIndex, keyword: Compiled): Boolean {
        val words = keyword.words
        val last = words.lastIndex
        if (words.size == 1) return if (keyword.approximate) index.hasPrefix(words[0]) else words[0] in index.set
        // Phrase: every word but the last must be present as is, else there's nothing to scan for.
        for (i in 0 until last) if (words[i] !in index.set) return false
        val tokens = index.tokens
        for (start in 0..tokens.size - words.size) if (keyword.startsAt(tokens, start)) return true
        return false
    }
}
