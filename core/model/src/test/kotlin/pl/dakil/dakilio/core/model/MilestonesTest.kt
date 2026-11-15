package pl.dakil.dakilio.core.model

import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class MilestonesTest {
    private val today = LocalDate.of(2026, 10, 29)
    private var nextId = 1L

    private fun post(daysAgo: Long, note: String? = null, photos: Int = 0, hour: Int = 12) = Post(
        id = nextId++,
        dateTime = today.minusDays(daysAgo).atTime(hour, 0),
        mood = null,
        title = null,
        note = note,
        tags = emptyList(),
        diaryId = 1,
        photos = List(photos) { PostPhoto(it.toLong(), "p$it.jpg", "/p$it.jpg") },
    )

    private fun List<MilestoneProgress>.of(kind: MilestoneKind) = first { it.kind == kind }

    private fun MilestoneProgress.reachedOn(target: Int) = statuses.first { it.milestone.target == target }.reachedOn

    @Test
    fun `nothing is reached without entries`() {
        val progress = Milestones.progress(emptyList(), today)
        assertEquals(MilestoneKind.entries, progress.map { it.kind })
        assertTrue(progress.all { it.current == 0 && it.reachedCount == 0 })
        assertEquals(Milestone(MilestoneKind.Entries, 1), progress.of(MilestoneKind.Entries).next)
        assertTrue(Milestones.reached(progress).isEmpty())
    }

    @Test
    fun `entries and days are reached on the day of the entry that got there`() {
        // Twelve entries on eight days, given newest first like the repository does.
        val posts = (0L..7L).flatMap { day -> if (day < 4) listOf(post(day, hour = 9), post(day, hour = 20)) else listOf(post(day)) }
        val progress = Milestones.progress(posts, today)

        val entries = progress.of(MilestoneKind.Entries)
        assertEquals(12, entries.current)
        assertEquals(today.minusDays(7), entries.reachedOn(1))
        // Oldest first: days 7, 6, 5, 4 give four entries, then two a day: the tenth is on day 1.
        assertEquals(today.minusDays(1), entries.reachedOn(10))
        assertNull(entries.reachedOn(25))
        assertEquals(Milestone(MilestoneKind.Entries, 25), entries.next)

        val days = progress.of(MilestoneKind.Days)
        assertEquals(8, days.current)
        assertEquals(today.minusDays(1), days.reachedOn(7))
        assertNull(days.reachedOn(30))
    }

    @Test
    fun `a streak stamp stays after the streak breaks`() {
        // Seven days in a row long ago, then a gap, then three days up to yesterday.
        val posts = (20L..26L).map { post(it) } + (1L..3L).map { post(it) }
        val streak = Milestones.progress(posts, today).of(MilestoneKind.Streak)
        assertEquals(3, streak.current) // the streak running now, counted from yesterday
        assertEquals(today.minusDays(24), streak.reachedOn(3)) // first reached in the old run
        assertEquals(today.minusDays(20), streak.reachedOn(7))
        assertNull(streak.reachedOn(14))
        assertEquals(2, streak.reachedCount)
    }

    @Test
    fun `a broken streak counts nothing towards the next stamp`() {
        val streak = Milestones.progress((5L..9L).map { post(it) }, today).of(MilestoneKind.Streak)
        assertEquals(0, streak.current)
        assertTrue(streak.statuses.first().reached)
    }

    @Test
    fun `photos and words add up across entries`() {
        val long = List(600) { "word" }.joinToString(" ")
        val posts = listOf(post(3, note = long, photos = 4), post(2, photos = 5), post(1, note = long, photos = 1), post(0, note = "  one\ntwo  three "))
        val progress = Milestones.progress(posts, today)

        val photos = progress.of(MilestoneKind.Photos)
        assertEquals(10, photos.current)
        assertEquals(today.minusDays(3), photos.reachedOn(1))
        assertEquals(today.minusDays(1), photos.reachedOn(10))
        assertNull(photos.reachedOn(50))

        val words = progress.of(MilestoneKind.Words)
        assertEquals(1203, words.current)
        assertEquals(today.minusDays(1), words.reachedOn(1_000))
        assertNull(words.reachedOn(5_000))
    }

    @Test
    fun `one entry can reach several stamps at once`() {
        val photos = Milestones.progress(listOf(post(0, photos = 60)), today).of(MilestoneKind.Photos)
        assertEquals(listOf(today, today, today, null, null), photos.statuses.map { it.reachedOn })
    }

    @Test
    fun `words are runs of non-whitespace`() {
        assertEquals(0, Milestones.wordCount(null))
        assertEquals(0, Milestones.wordCount("  \n "))
        assertEquals(1, Milestones.wordCount("hello"))
        assertEquals(4, Milestones.wordCount(" a  b\tc\nd "))
    }

    @Test
    fun `only the highest new stamp of a kind is celebrated`() {
        val before = Milestones.reached(Milestones.progress(emptyList(), today))
        val after = Milestones.reached(Milestones.progress(listOf(post(0, photos = 12)), today))
        assertEquals(
            listOf(Milestone(MilestoneKind.Entries, 1), Milestone(MilestoneKind.Photos, 10)),
            Milestones.newlyReached(before, after),
        )
        assertTrue(Milestones.newlyReached(after, after).isEmpty())
        // Losing a stamp (an entry deleted) is nothing to announce.
        assertTrue(Milestones.newlyReached(after, before).isEmpty())
        assertFalse(Milestone(MilestoneKind.Photos, 50) in after)
    }

    @Test
    fun `every ladder climbs`() {
        assertEquals(MilestoneKind.entries.toSet(), Milestones.targets.keys)
        Milestones.targets.values.forEach { ladder -> assertEquals(ladder.sorted().distinct(), ladder) }
    }
}
