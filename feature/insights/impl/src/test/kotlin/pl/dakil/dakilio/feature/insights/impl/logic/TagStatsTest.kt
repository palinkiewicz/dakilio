package pl.dakil.dakilio.feature.insights.impl.logic

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import pl.dakil.dakilio.core.model.DateRange
import pl.dakil.dakilio.core.model.Post

class TagStatsTest {
    private val range = DateRange(TODAY.minusDays(60), TODAY)
    private fun days(posts: List<Post>) = InsightsIndex(posts).days(range)

    @Test
    fun `tags count entries and days apart`() {
        val stats = tagStats(days(listOf(post(0, 5, WALK), post(0, 4, WALK, WORK), post(2, 3, WORK), post(3, null, READ))))
        assertEquals(listOf("Walk" to 1, "Work" to 2, "Read" to 1).toMap(), stats.associate { it.tag.label to it.days })
        assertEquals(2, stats.first { it.tag == WALK }.entries)
        assertEquals("Work", stats.first().tag.label) // most days first
        assertEquals(TODAY, stats.first { it.tag == WORK }.lastUsed)
    }

    @Test
    fun `a link needs enough days on both sides`() {
        // 5 bright walk days and 5 plain days: +2 levels.
        val posts = (0L..4).map { post(it, 5, WALK) } + (5L..9).map { post(it, 3) }
        val walk = tagStats(days(posts)).single()
        assertEquals(TagLink(5, 5, 5f, 3f), walk.link)
        assertEquals(2f, walk.link!!.lift, 0.0001f)
        // One day short on either side: no link.
        assertNull(tagStats(days(posts.drop(1))).single().link)
        assertNull(tagStats(days(posts.dropLast(1))).single().link)
    }

    @Test
    fun `days without a mood don't count towards a link`() {
        val posts = (0L..4).map { post(it, 4, WALK) } + (5L..9).map { post(it, 2) } + (10L..14).map { post(it, null, WALK) }
        val walk = tagStats(days(posts)).single()
        assertEquals(10, walk.days)
        assertEquals(5, walk.link!!.daysWith)
    }

    @Test
    fun `links are ranked by how well they're backed`() {
        // Walk: +1 over 20 days. Read: +1.2 over 5 days. Work: -1 over 8 days.
        val posts = (0L..19).map { post(it, 4, WALK) } +
            (20L..24).map { post(it, 4, READ) } + // 5 days at 4
            (25L..32).map { post(it, 2, WORK) } +
            (33L..45).map { post(it, 3) }
        val stats = tagStats(days(posts))
        assertEquals(listOf("Walk", "Read"), stats.brighter().map { it.tag.label })
        assertEquals(listOf("Work"), stats.heavier().map { it.tag.label })
        assertTrue(stats.first { it.tag == WALK }.link!!.score > stats.first { it.tag == READ }.link!!.score)
    }

    @Test
    fun `a small lift isn't called out`() {
        val posts = (0L..5).map { post(it, if (it < 1) 5 else 4, WALK) } + (6L..11).map { post(it, 4) }
        val stats = tagStats(days(posts))
        assertTrue(stats.single().link!!.lift > 0)
        assertEquals(emptyList<TagStat>(), stats.brighter())
    }

    @Test
    fun `the comparison period fills the change`() {
        val now = days(listOf(post(0, 4, WALK), post(1, 4, WALK), post(2, 4, WORK)))
        val before = InsightsIndex(listOf(post(70, 4, WALK), post(71, 4, READ))).days(DateRange(TODAY.minusDays(90), TODAY.minusDays(61)))
        val stats = tagStats(now, before).associateBy { it.tag.label }
        assertEquals(1, stats.getValue("Walk").change)
        assertEquals(1, stats.getValue("Work").change)
        assertNull(tagStats(now).first().change)
    }

    @Test
    fun `tags that go together are counted per entry`() {
        val posts = listOf(post(0, 4, WALK, WORK), post(1, 4, WALK, WORK, READ), post(2, 4, WALK), post(3, 4, WORK, READ))
        assertEquals(listOf("Work" to 2, "Read" to 1), coOccurring(posts, WALK.id).map { it.tag.label to it.count })
    }
}

class EntryFilterTest {
    @Test
    fun `entries are narrowed by every part of the filter`() {
        // Today is a Thursday.
        val morningWalk = post(0, 5, WALK, hour = 8)
        val eveningWalk = post(0, 3, WALK, hour = 20)
        val wednesday = post(1, 5, WORK, hour = 8)
        val posts = listOf(morningWalk, eveningWalk, wednesday, post(2))
        assertEquals(listOf(morningWalk, wednesday), posts.filter(EntryFilter(moodLevel = 5)::matches))
        assertEquals(listOf(morningWalk, eveningWalk), posts.filter(EntryFilter(tagId = WALK.id)::matches))
        assertEquals(listOf(wednesday), posts.filter(EntryFilter(weekday = java.time.DayOfWeek.WEDNESDAY)::matches))
        assertEquals(listOf(morningWalk), posts.filter(EntryFilter(tagId = WALK.id, dayPart = DayPart.Morning)::matches))
        assertEquals(4, posts.count(EntryFilter()::matches))
    }

    @Test
    fun `matching entries are listed by day, newest day first`() {
        val late = post(0, 3, hour = 20)
        val early = post(0, 5, hour = 8)
        val before = post(3, 4)
        val days = entryDays(listOf(late, before, early))
        assertEquals(listOf(TODAY, TODAY.minusDays(3)), days.map { it.date })
        assertEquals(listOf(early, late), days.first().posts)
        assertEquals(emptyList<EntryDay>(), entryDays(listOf(late), EntryFilter(moodLevel = 1)))
    }
}
