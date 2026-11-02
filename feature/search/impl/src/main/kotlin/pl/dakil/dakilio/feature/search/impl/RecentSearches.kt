package pl.dakil.dakilio.feature.search.impl

import kotlinx.serialization.json.Json

/** A recent search as stored ([raw], needed to remove it) and as decoded. */
internal data class RecentSearch(val raw: String, val filters: SearchFilters)

private val json = Json {
    ignoreUnknownKeys = true
    encodeDefaults = false
}

internal fun SearchFilters.encode(): String = json.encodeToString(SearchFilters.serializer(), copy(query = query.trim()))

/**
 * Decodes stored searches, dropping ones this version can't read. Tags and diaries that no longer
 * exist are left out of each search, and a search left with nothing to filter by is dropped.
 */
internal fun decodeRecents(raw: List<String>, tagIds: Set<Long>, diaryIds: Set<Long>): List<RecentSearch> =
    raw.mapNotNull { entry ->
        val filters = try {
            json.decodeFromString(SearchFilters.serializer(), entry)
        } catch (_: IllegalArgumentException) { // SerializationException is one too
            return@mapNotNull null
        }
        val kept = filters.copy(
            tagIds = filters.tagIds.filterTo(LinkedHashSet()) { it in tagIds },
            diaryIds = filters.diaryIds.filterTo(LinkedHashSet()) { it in diaryIds },
        )
        RecentSearch(entry, kept).takeIf { kept.isActive }
    }
