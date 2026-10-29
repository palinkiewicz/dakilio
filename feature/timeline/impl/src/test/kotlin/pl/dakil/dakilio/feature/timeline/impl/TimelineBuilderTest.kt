package pl.dakil.dakilio.feature.timeline.impl

import java.time.LocalDate
import java.time.LocalTime
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import pl.dakil.dakilio.core.model.Diary
import pl.dakil.dakilio.core.model.Post
import pl.dakil.dakilio.feature.timeline.impl.TimelineItem.DayHeader
import pl.dakil.dakilio.feature.timeline.impl.TimelineItem.EmptyDay
import pl.dakil.dakilio.feature.timeline.impl.TimelineItem.NoEntries
import pl.dakil.dakilio.feature.timeline.impl.TimelineItem.OtherDiaries
import pl.dakil.dakilio.feature.timeline.impl.TimelineItem.PostItem
import pl.dakil.dakilio.feature.timeline.impl.TimelineItem.QuietStretch
import pl.dakil.dakilio.feature.timeline.impl.TimelineItem.TodayPrompt

class TimelineBuilderTest {
    private val today = LocalDate.of(2026, 10, 29)
    private val journey = Diary(1, "Your Journey", "explore", 0, emptySet())
    private val work = Diary(2, "Work", "work", 1, emptySet())
    private var nextId = 1L

    private fun post(daysAgo: Long, hour: Int = 12, vararg diaries: Long = longArrayOf(1)) = Post(
        id = nextId++,
        dateTime = today.minusDays(daysAgo).atTime(LocalTime.of(hour, 0)),
        mood = null,
        title = "t",
        note = null,
        tags = emptyList(),
        diaryIds = diaries.toSet(),
        photos = emptyList(),
    )

    @Test
    fun `empty journal shows today with the check-in prompt`() {
        val items = buildTimeline(emptyList(), listOf(journey), null, today)
        assertEquals(listOf(DayHeader(today, emptyList()), TodayPrompt(today)), items)
    }

    @Test
    fun `posts of a day are grouped newest first with segment positions`() {
        val morning = post(1, hour = 8)
        val evening = post(1, hour = 20)
        val items = buildTimeline(listOf(morning, evening), listOf(journey), null, today)
        val posts = items.filterIsInstance<PostItem>()
        assertEquals(listOf(evening.id, morning.id), posts.map { it.post.id })
        assertEquals(listOf(0 to 2, 1 to 2), posts.map { it.index to it.count })
    }

    @Test
    fun `short gaps list each empty day, long gaps collapse into a quiet stretch`() {
        val items = buildTimeline(listOf(post(1), post(3), post(8)), listOf(journey), null, today)
        assertEquals(1, items.filterIsInstance<EmptyDay>().size) // 2 days ago
        val stretch = items.filterIsInstance<QuietStretch>().single() // 4..7 days ago
        assertEquals(today.minusDays(4), stretch.newest)
        assertEquals(today.minusDays(7), stretch.oldest)
        assertEquals(4, stretch.days)
    }

    @Test
    fun `expanding a quiet stretch reveals its days`() {
        val expanded = setOf(today.minusDays(2).toEpochDay()) // the stretch covers 2..7 days ago
        val items = buildTimeline(listOf(post(1), post(8)), listOf(journey), null, today, expanded)
        assertTrue(items.filterIsInstance<QuietStretch>().single().expanded)
        assertEquals(6, items.filterIsInstance<EmptyDay>().size)
    }

    @Test
    fun `filtering by diary summarizes the other diaries per day`() {
        val inJourney = post(1, diaries = longArrayOf(1))
        val inWork = post(1, diaries = longArrayOf(2))
        val inBoth = post(1, diaries = longArrayOf(1, 2))
        val onlyWork = post(2, diaries = longArrayOf(2))
        val items = buildTimeline(listOf(inJourney, inWork, inBoth, onlyWork), listOf(journey, work), 1L, today)

        assertEquals(setOf(inJourney.id, inBoth.id), items.filterIsInstance<PostItem>().map { it.post.id }.toSet())
        val chips = items.filterIsInstance<OtherDiaries>()
        assertEquals(listOf(1, 1), chips.map { it.chips.single().count })
        // A day with entries only elsewhere still gets a header saying nothing is here.
        assertTrue(NoEntries(today.minusDays(2)) in items)
    }

    @Test
    fun `diaries are labelled on cards only when showing all of several diaries`() {
        val p = post(1, diaries = longArrayOf(1, 2))
        val all = buildTimeline(listOf(p), listOf(journey, work), null, today).filterIsInstance<PostItem>().single()
        val filtered = buildTimeline(listOf(p), listOf(journey, work), 1L, today).filterIsInstance<PostItem>().single()
        assertEquals(listOf(journey, work), all.diaries)
        assertEquals(emptyList<Diary>(), filtered.diaries)
    }
}
