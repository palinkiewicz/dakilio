package pl.dakil.dakilio.core.model

/** A category of entries ("Your Journey", "Work", …). Each entry belongs to exactly one diary. */
data class Diary(
    val id: Long,
    val name: String,
    val iconKey: String,
    val position: Int,
    /** Tag groups shown expanded when writing in this diary; the rest start collapsed. */
    val suggestedGroupIds: Set<Long>,
)
