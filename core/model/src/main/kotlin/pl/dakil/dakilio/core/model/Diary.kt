package pl.dakil.dakilio.core.model

/** A category of entries ("Your Journey", "Work", …). Each entry belongs to exactly one diary. */
data class Diary(
    val id: Long,
    val name: String,
    val iconKey: String,
    val position: Int,
    /** Tag groups shown expanded when writing in this diary; the rest start collapsed. */
    val suggestedGroupIds: Set<Long>,
    /**
     * Whether writing in this diary asks for a mood. Off (a dream journal, a project log), the editor
     * leaves the mood out; an entry that already has one keeps it, so moving entries loses nothing.
     */
    val moodTracking: Boolean = true,
)
