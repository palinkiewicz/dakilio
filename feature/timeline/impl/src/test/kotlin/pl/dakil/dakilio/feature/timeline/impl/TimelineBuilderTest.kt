package pl.dakil.dakilio.feature.timeline.impl

import java.time.LocalDate
import java.time.LocalTime
import java.time.YearMonth
import org.junit.Assert.assertNull
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import pl.dakil.dakilio.core.model.Diary
import pl.dakil.dakilio.core.model.Mood
import pl.dakil.dakilio.core.model.Post
import pl.dakil.dakilio.feature.timeline.impl.TimelineItem.DayHeader
import pl.dakil.dakilio.feature.timeline.impl.TimelineItem.EmptyDay
import pl.dakil.dakilio.feature.timeline.impl.TimelineItem.MonthDivider
import pl.dakil.dakilio.feature.timeline.impl.TimelineItem.OtherDiaries
import pl.dakil.dakilio.feature.timeline.impl.TimelineItem.PostItem
import pl.dakil.dakilio.feature.timeline.impl.TimelineItem.QuietStretch
import pl.dakil.dakilio.feature.timeline.impl.TimelineItem.TodayPrompt

class TimelineBuilderTest {
    private val today = LocalDate.of(2026, 10, 29)
    private val journey = Diary(1, "Your Journey", "explore", 0, emptySet())
    private val work = Diary(2, "Work", "work", 1, emptySet())
    private var nextId = 1L

    private fun post(daysAgo: Long, hour: Int = 12, diary: Long = 1, mood: Mood? = null) = Post(
        id = nextId++,
        dateTime = today.minusDays(daysAgo).atTime(LocalTime.of(hour, 0)),
        mood = mood,
        title = "t",
        note = null,
        tags = emptyList(),
        diaryId = diary,
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
        val inJourney = post(1, diary = 1)
        val inWork = post(1, diary = 2)
        val alsoInWork = post(1, diary = 2)
        val onlyWork = post(2, diary = 2)
        val items = buildTimeline(listOf(inJourney, inWork, alsoInWork, onlyWork), listOf(journey, work), 1L, today)

        assertEquals(listOf(inJourney.id), items.filterIsInstance<PostItem>().map { it.post.id })
        // "Also this day" only sits under days with entries in the filter.
        val chips = items.filterIsInstance<OtherDiaries>().single()
        assertEquals(today.minusDays(1), chips.date)
        assertEquals(2, chips.chips.single().count)
        // A day with entries only elsewhere is a compact empty row hinting at those diaries.
        assertTrue(EmptyDay(today.minusDays(2), listOf(work)) in items)
        assertFalse(items.any { it is DayHeader && it.date == today.minusDays(2) })
    }

    @Test
    fun `days with entries only in other diaries join quiet stretches and surface their diaries`() {
        val posts = listOf(post(1), post(3, diary = 2), post(6))
        val collapsed = buildTimeline(posts, listOf(journey, work), 1L, today)
        val stretch = collapsed.filterIsInstance<QuietStretch>().single() // 2..5 days ago
        assertEquals(today.minusDays(2), stretch.newest)
        assertEquals(today.minusDays(5), stretch.oldest)
        assertEquals(listOf(work), stretch.otherDiaries)

        val expanded = buildTimeline(posts, listOf(journey, work), 1L, today, setOf(today.minusDays(2).toEpochDay()))
        assertTrue(EmptyDay(today.minusDays(3), listOf(work)) in expanded)
        assertTrue(EmptyDay(today.minusDays(4)) in expanded)
    }

    @Test
    fun `today keeps its check-in card and other-diary chips when its entries are elsewhere`() {
        val items = buildTimeline(listOf(post(0, diary = 2)), listOf(journey, work), 1L, today)
        assertEquals(DayHeader(today, emptyList()), items[0])
        assertEquals(TodayPrompt(today), items[1])
        assertEquals(work, (items[2] as OtherDiaries).chips.single().diary)
    }

    @Test
    fun `locateDay finds headers, empty rows and collapsed stretches`() {
        val posts = listOf(post(1), post(3), post(8)) // 2 is a lone empty day, 4..7 a quiet stretch
        val items = buildTimeline(posts, listOf(journey), null, today)

        val withEntries = items.locateDay(today.minusDays(1)) as DayLocation.At
        assertEquals(DayHeader(today.minusDays(1), emptyList()), items[withEntries.index])
        val lone = items.locateDay(today.minusDays(2)) as DayLocation.At
        assertEquals(EmptyDay(today.minusDays(2)), items[lone.index])
        assertEquals(DayLocation.InStretch(today.minusDays(4).toEpochDay()), items.locateDay(today.minusDays(6)))
        assertEquals(DayLocation.Missing, items.locateDay(today.minusDays(9)))

        val expanded = buildTimeline(posts, listOf(journey), null, today, setOf(today.minusDays(4).toEpochDay()))
        val inside = expanded.locateDay(today.minusDays(6)) as DayLocation.At
        assertEquals(EmptyDay(today.minusDays(6)), expanded[inside.index])
    }

    @Test
    fun `locateDay lands a diary switch on the day header`() {
        val posts = listOf(post(1, diary = 1), post(2, diary = 2))
        val items = buildTimeline(posts, listOf(journey, work), 2L, today)
        val at = items.locateDay(today.minusDays(2)) as DayLocation.At
        assertEquals(DayHeader(today.minusDays(2), emptyList()), items[at.index])
    }

    @Test
    fun `timeline bounds span the oldest entry to today or a future entry`() {
        assertEquals(today..today, timelineBounds(emptyList(), today))
        assertEquals(today.minusDays(5)..today.plusDays(2), timelineBounds(listOf(post(5), post(-2)), today))
    }

    @Test
    fun `diaries are labelled on cards only when showing all of several diaries`() {
        val p = post(1, diary = 2)
        val all = buildTimeline(listOf(p), listOf(journey, work), null, today).filterIsInstance<PostItem>().single()
        val filtered = buildTimeline(listOf(p), listOf(journey, work), 2L, today).filterIsInstance<PostItem>().single()
        assertEquals(work, all.diary)
        assertEquals(null, filtered.diary)
    }

    private fun mood(level: Int) = Mood(level.toLong(), level, "m$level", true, 0)

    private val oct = YearMonth.of(2026, 10)
    private val sep = YearMonth.of(2026, 9)
    private val aug = YearMonth.of(2026, 8)
    private val jul = YearMonth.of(2026, 7)

    @Test
    fun `monthAt reads dividers, days and stretches`() {
        // Sep 2 and Aug 20: the stretch Sep 1..Aug 21 sits under September.
        val items = buildTimeline(listOf(post(1), post(57), post(70)), listOf(journey), null, today)
        assertEquals(oct, items.monthAt(0))
        val sepDivider = items.indexOf(MonthDivider(sep))
        assertEquals(sep, items.monthAt(sepDivider))
        val stretch = items.indexOfFirst { it is QuietStretch && it.newest == today.minusDays(58) }
        assertEquals(sep, items.monthAt(stretch))
        assertEquals(aug, items.monthAt(items.lastIndex))
        assertNull(emptyList<TimelineItem>().monthAt(0))
    }

    @Test
    fun `monthTopIndex lands on the top of a month`() {
        val items = buildTimeline(listOf(post(1), post(57), post(70)), listOf(journey), null, today)
        assertEquals(0, items.monthTopIndex(oct))
        assertEquals(items.indexOf(MonthDivider(sep)), items.monthTopIndex(sep))
        assertEquals(items.indexOf(MonthDivider(aug)), items.monthTopIndex(aug))
        assertNull(items.monthTopIndex(jul))
        assertEquals(listOf(oct, sep, aug), items.sectionMonths())
    }

    @Test
    fun `a month swallowed by a stretch lands on that stretch`() {
        // Oct 28, then nothing until Aug 20: one stretch from Oct 27 covers all of September.
        val items = buildTimeline(listOf(post(1), post(70)), listOf(journey), null, today)
        val stretch = items.indexOfFirst { it is QuietStretch }
        assertEquals(stretch, items.monthTopIndex(sep))
        assertEquals(listOf(oct, aug), items.sectionMonths())
    }

    @Test
    fun `month summaries count entries and average the mood level in the filter`() {
        val posts = listOf(
            post(1, mood = mood(5)),
            post(2, mood = mood(4)),
            post(3),
            post(4, diary = 2, mood = mood(1)),
            post(40),
        )
        val all = monthSummaries(posts, null)
        assertEquals(MonthSummary(4, 3), all[oct]) // (5 + 4 + 1) / 3 = 3.3
        assertEquals(MonthSummary(1, null), all[sep])
        assertEquals(MonthSummary(3, 5), monthSummaries(posts, 1)[oct]) // 4.5 rounds up
        assertNull(monthSummaries(posts, 2)[sep])
    }
}
