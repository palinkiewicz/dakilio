package pl.dakil.dakilio.core.model

/** A category of entries ("Your Journey", "Work", …). Entries can belong to several diaries. */
data class Diary(
    val id: Long,
    val name: String,
    val iconKey: String,
    val position: Int,
    /** Tag groups shown expanded when writing in this diary; the rest start collapsed. */
    val suggestedGroupIds: Set<Long>,
)
