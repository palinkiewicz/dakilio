package pl.dakil.dakilio.feature.insights.impl.logic

import java.time.YearMonth
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RecapArchiveTest {
    @Test
    fun `the archive runs from the first entry's year, newest first`() {
        // TODAY is 29 October 2026. Five entries in September, two in October, five a year ago.
        val posts = (30L..34).map { post(it, 4) } + listOf(post(0, 3), post(1, 5)) + (365L..369).map { post(it, 2) }
        val archive = recapArchive(posts, TODAY)
        assertEquals(listOf(2026, 2025), archive.map { it.year })
        val months = archive.first().months.toMap()
        assertTrue(months.getValue(YearMonth.of(2026, 9)).playable)
        assertFalse(months.getValue(YearMonth.of(2026, 10)).playable) // not finished, and too few
        assertEquals(4f, months.getValue(YearMonth.of(2026, 9)).average!!, 0.0001f)
        assertFalse(archive.first().summary.playable) // 2026 isn't over
        assertTrue(archive.last().summary.playable)
        assertEquals(emptyList<ArchivedYear>(), recapArchive(emptyList(), TODAY))
        // A year with nothing to play yet isn't listed.
        assertEquals(emptyList<ArchivedYear>(), recapArchive(listOf(post(0, 3), post(1, 3)), TODAY))
    }
}
