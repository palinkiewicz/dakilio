package pl.dakil.dakilio.core.model

/**
 * A ready-made tag group the user can start with (first-run intro) or add later (Tags screen).
 * [key] is stable across languages; names and labels are already localized.
 */
data class SuggestedGroup(
    val key: String,
    val name: String,
    val tags: List<SuggestedTag>,
)

data class SuggestedTag(val label: String, val iconKey: String)
