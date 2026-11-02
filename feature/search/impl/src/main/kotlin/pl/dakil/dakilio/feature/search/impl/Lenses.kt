package pl.dakil.dakilio.feature.search.impl

import java.time.LocalDate
import pl.dakil.dakilio.core.model.Post
import pl.dakil.dakilio.core.model.Tag

/**
 * Ready-made questions offered before anything is typed. Each is just a preset of [SearchFilters],
 * so once applied it shows up in the facet bar like any other filter; [Shuffle] instead opens a
 * random entry.
 */
internal enum class Lens(val filters: SearchFilters?) {
    OnThisDay(SearchFilters(period = Period.OnThisDay)),
    BrightDays(SearchFilters(moodLevels = setOf(4, 5), has = setOf(Has.Text))),
    ToughDays(SearchFilters(moodLevels = setOf(1, 2))),
    PhotoMemories(SearchFilters(has = setOf(Has.Photos))),
    LongReads(SearchFilters(has = setOf(Has.LongNote))),
    Shuffle(null),
    ;

    companion object {
        fun matching(filters: SearchFilters): Lens? = entries.firstOrNull { it.filters == filters }
    }
}

internal data class LensCount(val lens: Lens, val count: Int)

/** Entries [Lens.Shuffle] draws from: older than a month, or any when there are none that old yet. */
internal fun List<Post>.shuffleCandidates(today: LocalDate): List<Post> =
    filter { it.date <= today.minusDays(SHUFFLE_MIN_AGE_DAYS) }.ifEmpty { this }

/** The lenses that would find something, with how many entries each finds. */
internal fun List<Post>.lensCounts(today: LocalDate): List<LensCount> =
    Lens.entries.mapNotNull { lens ->
        val count = lens.filters?.let { search(it, today).size } ?: shuffleCandidates(today).size
        LensCount(lens, count).takeIf { count > 0 }
    }

/** Tags on the most entries of the last 30 days (all time while that's empty), among [active] tags. */
internal fun List<Post>.topTags(today: LocalDate, active: Map<Long, Tag>, limit: Int = 8): List<Tag> {
    val recent = filter { it.date > today.minusDays(TOP_TAG_DAYS) }
    return tagCounts(recent.ifEmpty { this }, active).take(limit).map { it.first }
}

/** Tags once used but not in the last 60 days, the most used before first. */
internal fun List<Post>.forgottenTags(today: LocalDate, active: Map<Long, Tag>, limit: Int = 8): List<Tag> {
    val cutoff = today.minusDays(FORGOTTEN_DAYS)
    val lastUsed = HashMap<Long, LocalDate>()
    forEach { post -> post.tags.forEach { tag -> lastUsed.merge(tag.id, post.date, ::maxOf) } }
    return tagCounts(this, active)
        .filter { (tag, _) -> lastUsed.getValue(tag.id) < cutoff }
        .take(limit)
        .map { it.first }
}

/**
 * Tags that would narrow [results] down, with how many results carry each: the most common first,
 * leaving out [selected] tags and tags every result has (picking those changes nothing).
 */
internal fun refineTags(results: List<Post>, selected: Set<Long>, active: Map<Long, Tag>, limit: Int = 6): List<Pair<Tag, Int>> {
    if (results.size < 2) return emptyList()
    return tagCounts(results, active)
        .filter { (tag, count) -> tag.id !in selected && count < results.size }
        .take(limit)
}

/** Counts per active tag (each entry counts once), the most common first, then by name. */
private fun tagCounts(posts: List<Post>, active: Map<Long, Tag>): List<Pair<Tag, Int>> {
    val counts = HashMap<Long, Int>()
    posts.forEach { post -> post.tags.mapTo(HashSet()) { it.id }.forEach { counts.merge(it, 1, Int::plus) } }
    return counts.mapNotNull { (id, count) -> active[id]?.let { it to count } }
        .sortedWith(compareByDescending<Pair<Tag, Int>> { it.second }.thenBy { it.first.label.lowercase() })
}

/** The `#…` word being typed at the end of [query] (without the `#`), or `null` when there's none. */
internal fun hashFragment(query: String): String? {
    val last = query.substringAfterLast(' ')
    return if (last.startsWith('#')) last.drop(1) else null
}

/** [query] with the trailing `#…` word removed. */
internal fun withoutHashFragment(query: String): String {
    val fragment = hashFragment(query) ?: return query
    return query.dropLast(fragment.length + 1).trimEnd().let { if (it.isEmpty()) it else "$it " }
}

/** Tags whose name contains [fragment] (accent-insensitive), names starting with it first. */
internal fun tagSuggestions(fragment: String, tags: List<Tag>, exclude: Set<Long>, limit: Int = 8): List<Tag> {
    val needle = fragment.foldForSearch()
    return tags.asSequence()
        .filter { it.id !in exclude }
        .map { it to it.label.foldForSearch() }
        .filter { (_, label) -> needle in label }
        .sortedBy { (_, label) -> if (label.startsWith(needle)) 0 else 1 }
        .take(limit)
        .map { it.first }
        .toList()
}

private const val SHUFFLE_MIN_AGE_DAYS = 30L
private const val TOP_TAG_DAYS = 30L
private const val FORGOTTEN_DAYS = 60L
