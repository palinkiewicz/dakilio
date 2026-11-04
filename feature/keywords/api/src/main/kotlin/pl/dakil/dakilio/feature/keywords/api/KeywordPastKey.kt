package pl.dakil.dakilio.feature.keywords.api

import androidx.navigation3.runtime.NavKey
import kotlinx.serialization.Serializable

/** A keyword as edited in the keyword sheet (possibly not saved yet). */
@Serializable
data class KeywordWord(val word: String, val approximate: Boolean)

/**
 * Reviews older entries that mention [words] but lack the tag [tagId], and adds the tag to the ones
 * the user ticks. Carries the words as edited, so unsaved changes in the sheet count.
 */
@Serializable
data class KeywordPastKey(val tagId: Long, val words: List<KeywordWord>) : NavKey
