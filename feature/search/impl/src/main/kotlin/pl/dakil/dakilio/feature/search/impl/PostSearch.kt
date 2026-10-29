package pl.dakil.dakilio.feature.search.impl

import java.text.Normalizer
import pl.dakil.dakilio.core.model.Post

/** Case- and accent-insensitive form used for matching ("Zażółć" matches "zazolc"). */
internal fun String.foldForSearch(): String =
    Normalizer.normalize(this, Normalizer.Form.NFD)
        .replace(COMBINING_MARKS, "")
        .replace('ł', 'l')
        .replace('Ł', 'L')
        .lowercase()

private val COMBINING_MARKS = "\\p{Mn}+".toRegex()

internal data class SearchFilters(
    val query: String = "",
    val moodIds: Set<Long> = emptySet(),
    val withPhotosOnly: Boolean = false,
) {
    val isActive: Boolean get() = query.isNotBlank() || moodIds.isNotEmpty() || withPhotosOnly
}

/** Every query word must appear somewhere in the entry (title, note or a tag label). */
internal fun List<Post>.search(filters: SearchFilters): List<Post> {
    if (!filters.isActive) return emptyList()
    val words = filters.query.foldForSearch().split(' ').filter { it.isNotBlank() }
    return filter { post ->
        (filters.moodIds.isEmpty() || post.mood?.id in filters.moodIds) &&
            (!filters.withPhotosOnly || post.photos.isNotEmpty()) &&
            (words.isEmpty() || post.haystack().let { text -> words.all { it in text } })
    }
}

private fun Post.haystack(): String = buildString {
    title?.let { append(it).append('\n') }
    note?.let { append(it).append('\n') }
    tags.forEach { append(it.label).append('\n') }
}.foldForSearch()
