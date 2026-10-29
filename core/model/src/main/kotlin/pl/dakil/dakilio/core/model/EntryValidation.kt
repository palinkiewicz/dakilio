package pl.dakil.dakilio.core.model

/**
 * An entry needs *something*: a mood, a tag, or some body content. Skipping both the mood and the
 * tags steps makes the body step mandatory.
 */
object EntryValidation {
    fun canSave(
        hasMood: Boolean,
        tagCount: Int,
        title: String,
        note: String,
        photoCount: Int,
        diaryCount: Int,
    ): Boolean {
        if (diaryCount == 0) return false
        if (hasMood || tagCount > 0) return true
        return title.isNotBlank() || note.isNotBlank() || photoCount > 0
    }
}
