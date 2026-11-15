package pl.dakil.dakilio.core.model

/** The formatting a note can carry. */
enum class NoteStyle { Bold, Italic, Heading }

/** [style] over `text[start until end]` of a [FormattedNote]. */
data class NoteSpan(val start: Int, val end: Int, val style: NoteStyle)

/**
 * A note as it's shown: its [text] without the markers, and where the formatting goes.
 *
 * @property spans in no particular order; they may nest (italic inside bold, either inside a heading).
 */
data class FormattedNote(val text: String, val spans: List<NoteSpan>) {
    val isPlain: Boolean get() = spans.isEmpty()
}

/** What a formatting button does to the note field: the new text and what's selected afterwards. */
data class NoteEdit(val text: String, val selectionStart: Int, val selectionEnd: Int)

/**
 * Notes are stored as plain text with a few Markdown markers, so they stay readable anywhere
 * (a backup, an export, another app): `**bold**`, `*italic*` and a line starting with `# ` for a
 * heading. Nothing else of Markdown is read.
 *
 * The rules are strict on purpose, so ordinary text isn't taken for formatting:
 * - a pair must open and close **on the same line**, hugging its text (`** no **` stays as typed);
 * - a single `*` only opens at the start of a word and closes at the end of one, so `2*3*4` and
 *   `a * b` stay as typed;
 * - an unmatched marker stays as typed, and `\*` is always a plain asterisk.
 */
object NoteMarkup {
    const val BOLD = "**"
    const val ITALIC = "*"
    const val HEADING = "# "

    private val HEADING_LINE = Regex("^#{1,3} +(\\S.*)$")
    private val HEADING_PREFIX = Regex("^#{1,3} +")

    /** Whether [note] can hold formatting at all: a quick check that skips parsing plain notes. */
    private fun mayBeFormatted(note: String): Boolean = note.any { it == '*' || it == '#' || it == '\\' }

    fun parse(note: String): FormattedNote {
        if (!mayBeFormatted(note)) return FormattedNote(note, emptyList())
        val out = StringBuilder(note.length)
        val spans = ArrayList<NoteSpan>()
        var first = true
        for (line in note.split('\n')) {
            if (!first) out.append('\n')
            first = false
            val heading = HEADING_LINE.matchEntire(line)
            if (heading != null) {
                val start = out.length
                inline(heading.groupValues[1].trimEnd(), out, spans)
                spans += NoteSpan(start, out.length, NoteStyle.Heading)
            } else {
                inline(line, out, spans)
            }
        }
        return FormattedNote(out.toString(), spans)
    }

    /** [note] without its markers: what search, excerpts and plain exports read. */
    fun plain(note: String): String = if (mayBeFormatted(note)) parse(note).text else note

    /** Appends one line (or the inside of a pair) to [out], markers removed, and collects its spans. */
    private fun inline(s: String, out: StringBuilder, spans: MutableList<NoteSpan>) {
        var i = 0
        while (i < s.length) {
            val c = s[i]
            if (c == '\\' && i + 1 < s.length && (s[i + 1] == '*' || s[i + 1] == '#' || s[i + 1] == '\\')) {
                out.append(s[i + 1])
                i += 2
                continue
            }
            if (c != '*') {
                out.append(c)
                i++
                continue
            }
            val stars = starsAt(s, i)
            // ***both***
            if (stars >= 3) {
                val close = closing(s, i + 3, "***")
                if (close != null) {
                    val start = out.length
                    inline(s.substring(i + 3, close), out, spans)
                    spans += NoteSpan(start, out.length, NoteStyle.Bold)
                    spans += NoteSpan(start, out.length, NoteStyle.Italic)
                    i = close + 3
                    continue
                }
            }
            if (stars >= 2) {
                val close = closing(s, i + 2, BOLD)
                if (close != null) {
                    val start = out.length
                    inline(s.substring(i + 2, close), out, spans)
                    spans += NoteSpan(start, out.length, NoteStyle.Bold)
                    i = close + 2
                    continue
                }
            }
            if (stars == 1 && (i == 0 || !s[i - 1].isLetterOrDigit())) {
                val close = closingItalic(s, i + 1)
                if (close != null) {
                    val start = out.length
                    inline(s.substring(i + 1, close), out, spans)
                    spans += NoteSpan(start, out.length, NoteStyle.Italic)
                    i = close + 1
                    continue
                }
            }
            // Not a pair: the asterisks stay as typed.
            repeat(stars) { out.append('*') }
            i += stars
        }
    }

    private fun starsAt(s: String, index: Int): Int {
        var n = 0
        while (index + n < s.length && s[index + n] == '*') n++
        return n
    }

    /**
     * Where [marker] closes a run opened just before [from]: the text between must be non-empty and
     * must not start or end with whitespace. Null when it doesn't close.
     */
    private fun closing(s: String, from: Int, marker: String): Int? {
        if (from >= s.length || s[from].isWhitespace()) return null
        var k = from + 1
        while (k <= s.length - marker.length) {
            if (s[k] == '\\') {
                k += 2
                continue
            }
            if (s.startsWith(marker, k) && !s[k - 1].isWhitespace()) {
                // In a longer run of stars the pair closes at the run's end, leaving the first stars
                // to whatever is inside ("**bold *italic***").
                return k + starsAt(s, k) - marker.length
            }
            k++
        }
        return null
    }

    /** Like [closing] for a single `*`, which must also end a word and may hold `**bold**` inside. */
    private fun closingItalic(s: String, from: Int): Int? {
        if (from >= s.length || s[from].isWhitespace() || s[from] == '*') return null
        var k = from + 1
        while (k < s.length) {
            when {
                s[k] == '\\' -> k += 2
                s[k] != '*' -> k++
                // A bold pair inside: step over it whole.
                starsAt(s, k) >= 2 -> {
                    val inner = closing(s, k + 2, BOLD)
                    k = if (inner != null) inner + 2 else k + starsAt(s, k)
                }
                !s[k - 1].isWhitespace() && (k + 1 == s.length || !s[k + 1].isLetterOrDigit()) -> return k
                else -> k++
            }
        }
        return null
    }

    /**
     * The Bold or Italic button: wraps the selection in [marker] ([BOLD] or [ITALIC]), or unwraps it
     * when it's already wrapped. The markers hug the text (spaces at the selection's ends stay
     * outside), and a selection over several lines wraps each line, since a pair can't cross lines.
     * With nothing selected, an empty pair goes in with the cursor inside it.
     */
    fun toggleInline(text: String, selectionStart: Int, selectionEnd: Int, marker: String): NoteEdit {
        val start = minOf(selectionStart, selectionEnd).coerceIn(0, text.length)
        val end = maxOf(selectionStart, selectionEnd).coerceIn(0, text.length)
        if (start == end) {
            return NoteEdit(text.substring(0, start) + marker + marker + text.substring(start), start + marker.length, start + marker.length)
        }
        if (isWrapped(text, start, end, marker)) {
            val unwrapped = text.substring(0, start - marker.length) + text.substring(start, end) + text.substring(end + marker.length)
            return NoteEdit(unwrapped, start - marker.length, end - marker.length)
        }
        val selected = text.substring(start, end)
        if ('\n' !in selected) {
            val content = selected.trim()
            if (content.isEmpty()) return NoteEdit(text, start, end)
            // The markers hug the text, and the text stays selected, so pressing again takes them off.
            val lead = selected.length - selected.trimStart().length
            val from = start + lead
            val wrapped = text.substring(0, from) + marker + content + marker + text.substring(from + content.length)
            return NoteEdit(wrapped, from + marker.length, from + marker.length + content.length)
        }
        // A pair can't cross a line break: every line with text gets its own.
        val wrapped = StringBuilder(text.substring(0, start))
        selected.split('\n').forEachIndexed { index, line ->
            if (index > 0) wrapped.append('\n')
            val content = line.trim()
            if (content.isEmpty()) {
                wrapped.append(line)
            } else {
                val lead = line.length - line.trimStart().length
                wrapped.append(line, 0, lead).append(marker).append(content).append(marker).append(line, lead + content.length, line.length)
            }
        }
        val newEnd = wrapped.length
        wrapped.append(text, end, text.length)
        return NoteEdit(wrapped.toString(), start, newEnd)
    }

    /** Whether `text[start until end]` sits right inside a [marker] pair (and not a longer run of stars). */
    private fun isWrapped(text: String, start: Int, end: Int, marker: String): Boolean {
        if (start < marker.length || end + marker.length > text.length) return false
        if (!text.startsWith(marker, start - marker.length) || !text.startsWith(marker, end)) return false
        // A lone "*" on both sides, or "***": italic is there. "**" on both sides is bold only.
        fun starsBefore(): Int = (start - 1 downTo 0).takeWhile { text[it] == '*' }.count()
        fun starsAfter(): Int = (end until text.length).takeWhile { text[it] == '*' }.count()
        val stars = minOf(starsBefore(), starsAfter())
        return if (marker == ITALIC) stars == 1 || stars >= 3 else stars >= 2
    }

    /**
     * The Heading button: makes the line the cursor is on a heading, or a plain line again. Only the
     * line where the selection starts changes.
     */
    fun toggleHeading(text: String, selectionStart: Int, selectionEnd: Int): NoteEdit {
        val start = minOf(selectionStart, selectionEnd).coerceIn(0, text.length)
        val end = maxOf(selectionStart, selectionEnd).coerceIn(0, text.length)
        val lineStart = text.lastIndexOf('\n', start - 1) + 1
        val lineEnd = text.indexOf('\n', lineStart).let { if (it < 0) text.length else it }
        val line = text.substring(lineStart, lineEnd)
        val prefix = HEADING_PREFIX.find(line)?.value
        return if (prefix != null) {
            val shift = prefix.length
            NoteEdit(
                text.substring(0, lineStart) + line.substring(shift) + text.substring(lineEnd),
                maxOf(start - shift, lineStart),
                maxOf(end - shift, lineStart),
            )
        } else {
            NoteEdit(text.substring(0, lineStart) + HEADING + text.substring(lineStart), start + HEADING.length, end + HEADING.length)
        }
    }
}
