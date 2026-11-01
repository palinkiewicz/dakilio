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

/**
 * How a tag group is used, for choosing between archiving and deleting it.
 *
 * @property tags tags in the group, archived ones included (deleting the group deletes them all).
 * @property entries entries carrying at least one of those tags.
 * @property emptied those of them whose only content is tags from this group; deleting the group
 *   deletes them too.
 */
data class GroupUsage(val tags: Int, val entries: Int, val emptied: Int)

/**
 * Everything tag-related that's archived.
 *
 * @property groups archived groups, each with all its tags (archived or not).
 * @property tags active groups that hold archived tags, each with only those tags.
 */
data class ArchivedTags(val groups: List<TagGroup>, val tags: List<TagGroup>)
