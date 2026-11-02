package pl.dakil.dakilio.core.model

/** @property colorKey the default color of the group's tags (the Tag colors extra), or `null`. */
data class TagGroup(
    val id: Long,
    val name: String,
    val position: Int,
    val tags: List<Tag>,
    val colorKey: String? = null,
)

/**
 * Archived tags are hidden from pickers but still shown on the entries that used them.
 *
 * @property colorKey the tag's own color (the Tag colors extra), or `null` to use its group's.
 * @property color the color the tag shows in: its own, else its group's, else `null`. Filled in by
 *   the data layer, so displays never need the group.
 */
data class Tag(
    val id: Long,
    val groupId: Long,
    val label: String,
    val iconKey: String,
    val position: Int,
    val isArchived: Boolean = false,
    val colorKey: String? = null,
    val color: String? = colorKey,
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
