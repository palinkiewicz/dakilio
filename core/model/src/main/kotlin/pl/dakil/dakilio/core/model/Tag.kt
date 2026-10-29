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
