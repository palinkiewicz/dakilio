package pl.dakil.dakilio.core.designsystem.icon

import androidx.compose.runtime.Immutable

/**
 * A symbol that is just text (a letter, for now) drawn in the app's own font, so it adds nothing
 * to the app's size: there's no drawable behind it.
 *
 * Its key is [KEY_PREFIX] followed by the text itself (`glyph:A`, `glyph:ł`). Keys are resolved
 * by that rule rather than looked up in a list, so the letters the picker offers can change (per
 * language, or to add digits) without touching stored keys, and a key picked in one language
 * still draws in another.
 */
@Immutable
data class GlyphSymbol(val text: String) : DakilioSymbol {
    init {
        require(isValid(text)) { "Not a glyph: \"$text\"" }
    }

    override val key: String get() = KEY_PREFIX + text

    companion object {
        const val KEY_PREFIX = "glyph:"

        /** A glyph is short: one letter today, with room for a pair (initials, a two-digit number). */
        const val MAX_CODE_POINTS = 2

        /** The symbol [key] stands for, or `null` when it isn't a well-formed glyph key. */
        fun fromKey(key: String): GlyphSymbol? {
            if (!key.startsWith(KEY_PREFIX)) return null
            val text = key.substring(KEY_PREFIX.length)
            return if (isValid(text)) GlyphSymbol(text) else null
        }

        /**
         * One symbol per character of [characters], in order. Whitespace is skipped, so an alphabet
         * in a string resource may be spaced out, and so is anything listed twice.
         */
        fun each(characters: String): List<GlyphSymbol> {
            val symbols = LinkedHashSet<GlyphSymbol>()
            var index = 0
            while (index < characters.length) {
                val codePoint = characters.codePointAt(index)
                index += Character.charCount(codePoint)
                val text = String(Character.toChars(codePoint))
                if (isValid(text)) symbols += GlyphSymbol(text)
            }
            return symbols.toList()
        }

        // Keys also come from backups, so anything that wouldn't draw as a small mark is refused.
        private fun isValid(text: String): Boolean {
            if (text.isEmpty() || text.codePointCount(0, text.length) > MAX_CODE_POINTS) return false
            var index = 0
            while (index < text.length) {
                val codePoint = text.codePointAt(index)
                index += Character.charCount(codePoint)
                if (!Character.isValidCodePoint(codePoint)) return false
                when (Character.getType(codePoint).toByte()) {
                    Character.CONTROL, Character.FORMAT, Character.SURROGATE, Character.PRIVATE_USE, Character.UNASSIGNED,
                    Character.SPACE_SEPARATOR, Character.LINE_SEPARATOR, Character.PARAGRAPH_SEPARATOR,
                    -> return false
                }
            }
            return true
        }
    }
}
