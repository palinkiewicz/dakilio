package pl.dakil.dakilio.feature.search.impl

import java.text.Normalizer
import java.time.LocalDate
import java.time.Month
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import pl.dakil.dakilio.core.model.Post

/** Case- and accent-insensitive form used for matching ("Zażółć" matches "zazolc"). */
internal fun String.foldForSearch(): String =
    Normalizer.normalize(this, Normalizer.Form.NFD)
        .replace(COMBINING_MARKS, "")
        .replace('ł', 'l')
        .replace('Ł', 'L')
        .lowercase()

private val COMBINING_MARKS = "\\p{Mn}+".toRegex()

/** A note at least this many words long counts as a long read. */
internal const val LONG_NOTE_WORDS = 120

@Serializable
internal enum class TagMatch { Any, All }

/** Kinds of content an entry must have. */
@Serializable
internal enum class Has { Photos, Text, LongNote }

/** A span of days. The presets stay relative to today, so a saved search keeps its meaning. */
@Serializable
internal sealed interface Period {
    fun contains(date: LocalDate, today: LocalDate): Boolean

    @Serializable @SerialName("7d")
    data object Last7Days : Period {
        override fun contains(date: LocalDate, today: LocalDate) = date in today.minusDays(6)..today
    }

    @Serializable @SerialName("30d")
    data object Last30Days : Period {
        override fun contains(date: LocalDate, today: LocalDate) = date in today.minusDays(29)..today
    }

    @Serializable @SerialName("year")
    data object ThisYear : Period {
        override fun contains(date: LocalDate, today: LocalDate) = date.year == today.year && date <= today
    }

    /** Today's month and day in earlier years; on 28 February of a common year, 29 February counts too. */
    @Serializable @SerialName("on_this_day")
    data object OnThisDay : Period {
        override fun contains(date: LocalDate, today: LocalDate): Boolean {
            if (date.year >= today.year || date.month != today.month) return false
            return date.dayOfMonth == today.dayOfMonth ||
                (today.month == Month.FEBRUARY && today.dayOfMonth == 28 && !today.isLeapYear && date.dayOfMonth == 29)
        }
    }

    @Serializable @SerialName("custom")
    data class Custom(val fromEpochDay: Long, val toEpochDay: Long) : Period {
        val from: LocalDate get() = LocalDate.ofEpochDay(fromEpochDay)
        val to: LocalDate get() = LocalDate.ofEpochDay(toEpochDay)

        override fun contains(date: LocalDate, today: LocalDate) = date.toEpochDay() in fromEpochDay..toEpochDay

        companion object {
            fun of(from: LocalDate, to: LocalDate) = Custom(minOf(from, to).toEpochDay(), maxOf(from, to).toEpochDay())
        }
    }
}

/** Everything a search can narrow by. Every facet left empty matches every entry. */
@Serializable
internal data class SearchFilters(
    val query: String = "",
    /** Mood levels (1..5); a level matches its primary and all its aliases. */
    val moodLevels: Set<Int> = emptySet(),
    val tagIds: Set<Long> = emptySet(),
    val tagMatch: TagMatch = TagMatch.Any,
    /** Entries in any of these diaries. */
    val diaryIds: Set<Long> = emptySet(),
    val period: Period? = null,
    /** ISO day-of-week numbers, Monday = 1. */
    val weekdays: Set<Int> = emptySet(),
    val has: Set<Has> = emptySet(),
) {
    /** The query's words, without a `#tag` still being typed (that one becomes a tag filter). */
    val words: List<String> get() = query.foldForSearch().split(' ').filter { it.isNotBlank() && !it.startsWith('#') }

    /** How many facets besides the text are in use. */
    val facetCount: Int
        get() = listOf(moodLevels.isNotEmpty(), tagIds.isNotEmpty(), diaryIds.isNotEmpty(), period != null, weekdays.isNotEmpty(), has.isNotEmpty())
            .count { it }

    val isActive: Boolean get() = words.isNotEmpty() || facetCount > 0
}

/** Every query word must appear in the entry's title or note. Tag names aren't searched as text; matching tags are offered as filters instead. */
internal fun List<Post>.search(filters: SearchFilters, today: LocalDate): List<Post> {
    if (!filters.isActive) return emptyList()
    val words = filters.words
    return filter { post ->
        (filters.moodLevels.isEmpty() || post.mood?.level in filters.moodLevels) &&
            post.matchesTags(filters) &&
            (filters.diaryIds.isEmpty() || post.diaryId in filters.diaryIds) &&
            (filters.period?.contains(post.date, today) ?: true) &&
            (filters.weekdays.isEmpty() || post.date.dayOfWeek.value in filters.weekdays) &&
            filters.has.all { post.has(it) } &&
            (words.isEmpty() || post.haystack().let { text -> words.all { it in text } })
    }
}

private fun Post.matchesTags(filters: SearchFilters): Boolean {
    if (filters.tagIds.isEmpty()) return true
    val ids = tags.mapTo(HashSet()) { it.id }
    return when (filters.tagMatch) {
        TagMatch.Any -> filters.tagIds.any { it in ids }
        TagMatch.All -> ids.containsAll(filters.tagIds)
    }
}

internal fun Post.has(kind: Has): Boolean = when (kind) {
    Has.Photos -> photos.isNotEmpty()
    Has.Text -> !title.isNullOrBlank() || !note.isNullOrBlank()
    Has.LongNote -> (note?.split(WHITESPACE)?.count { it.isNotEmpty() } ?: 0) >= LONG_NOTE_WORDS
}

private val WHITESPACE = "\\s+".toRegex()

private fun Post.haystack(): String = buildString {
    title?.let { append(it).append('\n') }
    note?.let { append(it).append('\n') }
}.foldForSearch()
