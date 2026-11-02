package pl.dakil.dakilio.feature.search.impl

import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import pl.dakil.dakilio.core.model.Mood
import pl.dakil.dakilio.core.model.Post
import pl.dakil.dakilio.core.model.PostPhoto
import pl.dakil.dakilio.core.model.Tag

class LensesTest {
    private val today = LocalDate.of(2026, 10, 30)
    private val walk = Tag(1, 1, "Walk", "directions_walk", 0)
    private val work = Tag(2, 1, "Work", "work", 1)
    private val rain = Tag(3, 2, "Rain", "rainy", 0)
    private val wandering = Tag(4, 1, "Wandering", "explore", 2)
    private val active = listOf(walk, work, rain, wandering).associateBy { it.id }

    private fun post(id: Long, daysAgo: Long, vararg tags: Tag, level: Int? = null, photo: Boolean = false, note: String? = null) = Post(
        id = id,
        dateTime = today.minusDays(daysAgo).atTime(12, 0),
        mood = level?.let { Mood(it.toLong(), it, "L$it", isPrimary = true, position = 0) },
        title = null,
        note = note,
        tags = tags.toList(),
        diaryId = 1,
        photos = if (photo) listOf(PostPhoto(id, "$id.jpg", "/$id.jpg")) else emptyList(),
    )

    @Test fun `lens counts leave out lenses that find nothing`() {
        val posts = listOf(
            post(1, 0, level = 5, note = "Great"),
            post(2, 365, level = 1, photo = true),
            post(3, 40, level = 2),
        )
        assertEquals(
            listOf(
                LensCount(Lens.OnThisDay, 1),
                LensCount(Lens.BrightDays, 1),
                LensCount(Lens.ToughDays, 2),
                LensCount(Lens.PhotoMemories, 1),
                LensCount(Lens.Shuffle, 2),
            ),
            posts.lensCounts(today),
        )
    }

    @Test fun `shuffle falls back to all entries while none is a month old`() {
        val fresh = listOf(post(1, 0), post(2, 3))
        assertEquals(fresh, fresh.shuffleCandidates(today))
        assertEquals(1, (fresh + post(3, 30)).shuffleCandidates(today).size)
    }

    @Test fun `a lens preset is recognized`() {
        assertEquals(Lens.ToughDays, Lens.matching(SearchFilters(moodLevels = setOf(2, 1))))
        assertNull(Lens.matching(SearchFilters(moodLevels = setOf(1))))
    }

    @Test fun `refine skips selected tags and tags on every result`() {
        val results = listOf(post(1, 0, walk, work), post(2, 1, walk, rain), post(3, 2, walk, rain))
        assertEquals(listOf(rain to 2, work to 1), refineTags(results, selected = emptySet(), active = active))
        assertEquals(listOf(work to 1), refineTags(results, selected = setOf(rain.id), active = active))
        assertEquals(emptyList<Pair<Tag, Int>>(), refineTags(results.take(1), emptySet(), active))
    }

    @Test fun `top tags prefer the last 30 days`() {
        val posts = listOf(post(1, 1, work), post(2, 2, work, walk), post(3, 90, rain), post(4, 91, rain), post(5, 92, rain))
        assertEquals(listOf(work, walk), posts.topTags(today, active))
        assertEquals(listOf(rain), posts.drop(2).topTags(today, active))
    }

    @Test fun `forgotten tags were used before but not lately`() {
        val posts = listOf(post(1, 1, work), post(2, 70, work, rain), post(3, 80, rain, walk), post(4, 90, rain))
        assertEquals(listOf(rain, walk), posts.forgottenTags(today, active))
        assertEquals(listOf(rain), posts.forgottenTags(today, active - walk.id))
    }

    @Test fun `hash fragments`() {
        assertEquals("wa", hashFragment("rainy #wa"))
        assertEquals("", hashFragment("#"))
        assertNull(hashFragment("#walk then"))
        assertEquals("rainy ", withoutHashFragment("rainy #wa"))
        assertEquals("", withoutHashFragment("#wa"))
        assertEquals("rainy", withoutHashFragment("rainy"))
    }

    @Test fun `suggestions put prefix matches first and skip picked tags`() {
        val tags = active.values.toList()
        assertEquals(listOf(walk, wandering), tagSuggestions("wa", tags, exclude = emptySet()))
        assertEquals(listOf(wandering), tagSuggestions("WA", tags, exclude = setOf(walk.id)))
        assertEquals(listOf(rain), tagSuggestions("ai", tags, exclude = emptySet()))
    }

    @Test fun `recents round-trip and drop what no longer exists`() {
        val filters = SearchFilters(
            query = " walk ",
            moodLevels = setOf(5),
            tagIds = setOf(1, 99),
            diaryIds = setOf(7),
            period = Period.Custom.of(today.minusDays(3), today),
            weekdays = setOf(6, 7),
            has = setOf(Has.Photos),
        )
        val raw = listOf(filters.encode(), SearchFilters(tagIds = setOf(99)).encode(), "{not json", """{"period":{"type":"next_week"}}""")
        val decoded = decodeRecents(raw, tagIds = setOf(1), diaryIds = emptySet())
        assertEquals(listOf(filters.copy(query = "walk", tagIds = setOf(1), diaryIds = emptySet())), decoded.map { it.filters })
        assertEquals(raw.first(), decoded.single().raw)
    }

    @Test fun `relative periods stay relative when saved`() {
        val raw = SearchFilters(period = Period.Last7Days).encode()
        assertEquals(Period.Last7Days, decodeRecents(listOf(raw), emptySet(), emptySet()).single().filters.period)
    }
}
