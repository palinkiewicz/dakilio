package pl.dakil.dakilio.feature.search.impl

import java.time.LocalDate
import java.time.LocalDateTime
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import pl.dakil.dakilio.core.model.Mood
import pl.dakil.dakilio.core.model.Post
import pl.dakil.dakilio.core.model.PostPhoto
import pl.dakil.dakilio.core.model.Tag

class PostSearchTest {
    private val today = LocalDate.of(2026, 10, 30)
    private val good = Mood(4, 4, "Good", isPrimary = true, position = 0)
    private val content = Mood(9, 4, "Content", isPrimary = false, position = 0)
    private val hiking = Tag(1, 1, "Hiking", "hiking", 0)
    private val friends = Tag(2, 1, "Friends", "group", 1)
    private val hike = Post(
        id = 1,
        dateTime = LocalDateTime.of(2026, 10, 28, 19, 0), // a Wednesday
        mood = good,
        title = "Wycieczka w góry",
        note = "Zachód słońca na grani",
        tags = listOf(hiking, friends),
        diaryId = 1,
        photos = listOf(PostPhoto(1, "a.jpg", "/a.jpg")),
    )
    private val work = hike.copy(id = 2, mood = null, title = "Meetings", note = null, tags = listOf(friends), photos = emptyList(), diaryId = 2)
    private val posts = listOf(hike, work)

    private fun search(filters: SearchFilters, from: List<Post> = posts) = from.search(filters, today)

    @Test fun `no filters means no results`() = assertEquals(emptyList<Post>(), search(SearchFilters()))

    @Test fun `matching ignores case and diacritics`() {
        assertEquals(listOf(hike), search(SearchFilters(query = "GORY")))
        assertEquals(listOf(hike), search(SearchFilters(query = "zachod slonca")))
    }

    @Test fun `tag names are not searched as text`() {
        assertEquals(emptyList<Post>(), search(SearchFilters(query = "hik")))
        assertEquals(emptyList<Post>(), search(SearchFilters(query = "friends")))
    }

    @Test fun `all words must match`() = assertEquals(emptyList<Post>(), search(SearchFilters(query = "meetings grani")))

    @Test fun `a hash word being typed is not searched as text`() {
        val filters = SearchFilters(query = "meetings #hik")
        assertEquals(listOf("meetings"), filters.words)
        assertEquals(listOf(work), search(filters))
        assertFalse(SearchFilters(query = "#hik").isActive)
    }

    @Test fun `a mood level matches its aliases`() {
        val aliased = work.copy(id = 3, mood = content)
        assertEquals(listOf(hike, aliased), search(SearchFilters(moodLevels = setOf(4)), posts + aliased))
    }

    @Test fun `mood and photo filters work without a query`() {
        assertEquals(listOf(hike), search(SearchFilters(moodLevels = setOf(4))))
        assertEquals(listOf(hike), search(SearchFilters(has = setOf(Has.Photos))))
    }

    @Test fun `tags match any or all`() {
        assertEquals(posts, search(SearchFilters(tagIds = setOf(1, 2))))
        assertEquals(listOf(hike), search(SearchFilters(tagIds = setOf(1, 2), tagMatch = TagMatch.All)))
    }

    @Test fun `diaries match any of them`() {
        assertEquals(listOf(work), search(SearchFilters(diaryIds = setOf(2))))
        assertEquals(posts, search(SearchFilters(diaryIds = setOf(1, 2))))
    }

    @Test fun `weekdays use ISO numbers`() {
        assertEquals(posts, search(SearchFilters(weekdays = setOf(3))))
        assertEquals(emptyList<Post>(), search(SearchFilters(weekdays = setOf(1, 7))))
    }

    @Test fun `text and long notes`() {
        val untitled = work.copy(id = 3, title = " ", note = null)
        val essay = work.copy(id = 4, note = List(LONG_NOTE_WORDS) { "word" }.joinToString(" "))
        val all = posts + untitled + essay
        assertEquals(listOf(hike, work, essay), search(SearchFilters(has = setOf(Has.Text)), all))
        assertEquals(listOf(essay), search(SearchFilters(has = setOf(Has.LongNote)), all))
    }

    @Test fun `relative periods`() {
        fun on(date: LocalDate) = hike.copy(id = date.toEpochDay(), dateTime = date.atTime(12, 0))
        val days = listOf(today, today.minusDays(6), today.minusDays(7), today.minusDays(29), today.minusDays(30), LocalDate.of(2025, 12, 31)).map(::on)
        assertEquals(days.take(2), search(SearchFilters(period = Period.Last7Days), days))
        assertEquals(days.take(4), search(SearchFilters(period = Period.Last30Days), days))
        assertEquals(days.take(5), search(SearchFilters(period = Period.ThisYear), days))
        val custom = Period.Custom.of(today.minusDays(7), today.minusDays(29)) // reversed on purpose
        assertEquals(listOf(days[2], days[3]), search(SearchFilters(period = custom), days))
    }

    @Test fun `on this day means earlier years only`() {
        val period = Period.OnThisDay
        assertTrue(period.contains(LocalDate.of(2024, 10, 30), today))
        assertFalse(period.contains(today, today))
        assertFalse(period.contains(LocalDate.of(2026, 9, 30), today))
        assertFalse(period.contains(LocalDate.of(2025, 10, 29), today))
    }

    @Test fun `on this day brings 29 February to 28 February of a common year`() {
        val period = Period.OnThisDay
        assertTrue(period.contains(LocalDate.of(2024, 2, 29), LocalDate.of(2026, 2, 28)))
        assertTrue(period.contains(LocalDate.of(2025, 2, 28), LocalDate.of(2026, 2, 28)))
        assertFalse(period.contains(LocalDate.of(2024, 2, 29), LocalDate.of(2028, 2, 28)))
        assertTrue(period.contains(LocalDate.of(2024, 2, 29), LocalDate.of(2028, 2, 29)))
    }

    @Test fun `facets are counted without the text`() {
        assertEquals(0, SearchFilters(query = "walk").facetCount)
        assertEquals(3, SearchFilters(moodLevels = setOf(1), period = Period.ThisYear, has = setOf(Has.Photos)).facetCount)
    }
}
