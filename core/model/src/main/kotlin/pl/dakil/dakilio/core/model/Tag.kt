package pl.dakil.dakilio.core.model

data class TagGroup(
    val id: Long,
    val name: String,
    val position: Int,
    val tags: List<Tag>,
)

/** Archived tags are hidden from pickers but still shown on the entries that used them. */
data class Tag(
    val id: Long,
    val groupId: Long,
    val label: String,
    val iconKey: String,
    val position: Int,
    val isArchived: Boolean = false,
)

/**
 * How a tag is used, for choosing between archiving and deleting it.
 *
 * @property entries entries carrying the tag.
 * @property emptied those of them that would be left with nothing (no mood, text, photos or other
 *   tags) if the tag were deleted; deleting the tag deletes them too, as the editor never saves an
 *   empty entry.
 */
data class TagUsage(val entries: Int, val emptied: Int)
