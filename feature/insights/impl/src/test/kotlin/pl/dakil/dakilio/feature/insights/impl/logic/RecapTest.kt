package pl.dakil.dakilio.feature.insights.impl.logic

import java.time.DayOfWeek
import java.time.YearMonth
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import pl.dakil.dakilio.core.model.Post
import pl.dakil.dakilio.core.model.monthSummaries

class RecapTest {
    private fun recapFor(posts: List<Post>, period: InsightsPeriod): Recap {
        val index = InsightsIndex(posts)
        val bounds = Bounds(index.first ?: TODAY, TODAY)
        val overview = buildOverview(index, period, bounds, Comparison.Previous, MOODS, DayOfWeek.MONDAY, allPosts = posts)
        return recap(overview, index, period, monthSummaries(posts, null))
    }

    // 30..59 days before TODAY (29 October) are September 29 back to August 31; 30..58 stay in September.
    private val september = InsightsPeriod.Month(YearMonth.of(2026, 9))

    @Test
    fun `a thin month gets only the pages it can fill`() {
        // Five entries, two with a mood, no tags: title, numbers and the end.
        val posts = listOf(post(30, 4), post(31, 3), post(32), post(33), post(34))
        val recap = recapFor(posts, september)
        assertEquals(listOf(RecapPage.Title, RecapPage.Numbers, RecapPage.End), recap.pages)
        assertEquals(RecapTitle.Yours, recap.title)
    }

    @Test
    fun `a full month tells mood, its days, the change, its brightest day and tags`() {
        val august = (60L..70).map { post(it, 2) }
        val sept = (30L..44).map { post(it, if (it == 35L) 5 else 3, WALK, note = "x".repeat(it.toInt())) } + post(35, 5, note = "a much longer note with many words in it")
        val recap = recapFor(august + sept, september)
        assertEquals(
            listOf(
                RecapPage.Title, RecapPage.Numbers, RecapPage.Mood, RecapPage.Days, RecapPage.Change, RecapPage.BrightestDay,
                RecapPage.Tags, RecapPage.NewTags, RecapPage.Rhythm, RecapPage.End,
            ),
            recap.pages,
        )
        // Of the brightest day's two great entries, the one with more words.
        assertEquals("a much longer note with many words in it", recap.brightest!!.note)
        assertEquals(5f, recap.brightestMood!!, 0.0001f)
        assertEquals(emptyList<Any>(), recap.months)
        assertEquals(RecapTitle.Climbing, recap.title)
    }

    @Test
    fun `the brightest day prefers an entry with a photo over a longer one`() {
        val posts = listOf(post(30, 3), post(31, 3), post(32, 5, note = "many words in this one"), post(32, 5, photos = 1))
        val recap = recapFor(posts, september)
        assertEquals(1, recap.brightest!!.photos.size)
    }

    @Test
    fun `a year shows its months as days and adds its records`() {
        val posts = (0L..300 step 3).map { post(it, (it % 5 + 1).toInt()) }
        val recap = recapFor(posts, InsightsPeriod.Year(2026))
        assertEquals(12, recap.months.size)
        assertNull(recap.months.last().second) // December hasn't come yet
        assertTrue(RecapPage.Days in recap.pages && RecapPage.Records in recap.pages)
    }

    @Test
    fun `photos get a page from three and are spread through the period`() {
        val two = recapFor((30L..36).map { post(it, 3, photos = if (it < 32) 1 else 0) }, september)
        assertFalse(RecapPage.Photos in two.pages)

        // Twenty photos, one per day: nine of them, from the first day to the last.
        val posts = (30L..49).map { post(it, 3, photos = 1) }
        val recap = recapFor(posts, september)
        assertTrue(RecapPage.Photos in recap.pages)
        assertEquals(20, recap.photoCount)
        assertEquals(9, recap.photos.size)
        assertEquals(TODAY.minusDays(49), recap.photos.first().date)
        assertEquals(TODAY.minusDays(30), recap.photos.last().date)
        assertEquals(recap.photos.sortedBy { it.date }, recap.photos)
    }

    @Test
    fun `new tags are those first used in the period on two days or more`() {
        val august = listOf(post(65, 3, WORK), post(66, 3))
        val sept = listOf(post(30, 3, WORK, WALK), post(31, 3, WALK), post(32, 3, READ), post(33, 3), post(34, 3))
        val recap = recapFor(august + sept, september)
        // Work was used before, Read only once.
        assertEquals(listOf(WALK), recap.newTags.map { it.tag })
    }

    @Test
    fun `nothing is new in the first period`() {
        val posts = listOf(post(30, 3, WALK), post(31, 3, WALK), post(32, 3), post(33, 3), post(34, 3))
        val recap = recapFor(posts, september)
        assertEquals(emptyList<TagStat>(), recap.newTags)
        assertFalse(RecapPage.NewTags in recap.pages)
    }

    @Test
    fun `titles follow their rules in order`() {
        // Bright beats everything.
        assertEquals(RecapTitle.Bright, recapFor((30L..36).map { post(it, if (it % 2 == 0L) 4 else 5) }, september).title)
        // Faithful: 25 of 30 days, an average of 3 that doesn't change.
        assertEquals(RecapTitle.Faithful, recapFor((30L..54).map { post(it, 3) }, september).title)
        // Steady: few days, level all along.
        assertEquals(RecapTitle.Steady, recapFor((30L..36).map { post(it, 3) }, september).title)
        // Many shades: up and down.
        assertEquals(RecapTitle.ManyShades, recapFor((30L..36).map { post(it, if (it % 2 == 0L) 1 else 4) }, september).title)
    }

    @Test
    fun `a low and falling period still gets a neutral title`() {
        val august = (60L..66).map { post(it, 4) }
        val sept = (30L..36).map { post(it, 1) }
        val title = recapFor(august + sept, september).title
        assertTrue(title == RecapTitle.Steady || title == RecapTitle.Yours)
    }

    @Test
    fun `words become book pages`() {
        assertEquals(0, bookPages(100))
        assertEquals(1, bookPages(275))
        assertEquals(12, bookPages(3_300))
    }

    @Test
    fun `spreading keeps the ends and the order`() {
        assertEquals(listOf(1, 2, 3), spreadEvenly(listOf(1, 2, 3), 9))
        assertEquals(listOf(0, 5, 10), spreadEvenly((0..10).toList(), 3))
    }
}
