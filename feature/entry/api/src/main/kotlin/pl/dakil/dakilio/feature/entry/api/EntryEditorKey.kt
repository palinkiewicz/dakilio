package pl.dakil.dakilio.feature.entry.api

import androidx.navigation3.runtime.NavKey
import kotlinx.serialization.Serializable

/**
 * The full-screen entry editor. Without [postId] it runs the mood → tags → note wizard for a new
 * entry; the presets let callers start it pre-filled (the timeline's diary filter, an empty day's
 * date, a mood tapped on the check-in card). With [postId] it opens the all-in-one page for quick
 * edits of an existing entry.
 */
@Serializable
data class EntryEditorKey(
    val postId: Long? = null,
    val presetDiaryId: Long? = null,
    val presetEpochDay: Long? = null,
    val presetMoodId: Long? = null,
) : NavKey {
    /** Shared-element key linking the editor to whatever it grew out of (FAB or entry card). */
    val sharedElementKey: String get() = postId?.let { postSharedElementKey(it) } ?: NEW_ENTRY_SHARED_KEY

    companion object {
        const val NEW_ENTRY_SHARED_KEY = "entry-new"

        fun postSharedElementKey(postId: Long) = "entry-$postId"
    }
}
