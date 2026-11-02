package pl.dakil.dakilio.feature.search.impl

import java.time.LocalDateTime
import org.junit.Assert.assertEquals
import org.junit.Test
import pl.dakil.dakilio.core.model.Mood
import pl.dakil.dakilio.core.model.Post
import pl.dakil.dakilio.core.model.PostPhoto
import pl.dakil.dakilio.core.model.Tag

class PostSearchTest {
    private val good = Mood(4, 4, "Good", isPrimary = true, position = 0)
    private val content = Mood(9, 4, "Content", isPrimary = false, position = 0)
    private val hike = Post(
        id = 1,
        dateTime = LocalDateTime.of(2026, 10, 28, 19, 0),
        mood = good,
        title = "Wycieczka w góry",
        note = "Zachód słońca na grani",
        tags = listOf(Tag(1, 1, "Hiking", "hiking", 0)),
        diaryIds = setOf(1),
        photos = listOf(PostPhoto(1, "a.jpg", "/a.jpg")),
    )
    private val work = hike.copy(id = 2, mood = null, title = "Meetings", note = null, tags = emptyList(), photos = emptyList())
    private val posts = listOf(hike, work)

    @Test fun `no filters means no results`() = assertEquals(emptyList<Post>(), posts.search(SearchFilters()))

    @Test fun `matching ignores case and diacritics`() {
        assertEquals(listOf(hike), posts.search(SearchFilters(query = "GORY")))
        assertEquals(listOf(hike), posts.search(SearchFilters(query = "zachod slonca")))
    }

    @Test fun `tags are searchable`() = assertEquals(listOf(hike), posts.search(SearchFilters(query = "hik")))

    @Test fun `all words must match`() = assertEquals(emptyList<Post>(), posts.search(SearchFilters(query = "meetings grani")))

    @Test fun `a mood level matches its aliases`() {
        val aliased = work.copy(id = 3, mood = content)
        assertEquals(listOf(hike, aliased), (posts + aliased).search(SearchFilters(moodLevels = setOf(4))))
    }

    @Test fun `mood and photo filters work without a query`() {
        assertEquals(listOf(hike), posts.search(SearchFilters(moodLevels = setOf(4))))
        assertEquals(listOf(hike), posts.search(SearchFilters(withPhotosOnly = true)))
    }
}
