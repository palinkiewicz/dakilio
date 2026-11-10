package pl.dakil.dakilio.feature.export.impl

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

class MarkdownExportTest {
    private val good = Mood(id = 4, level = 4, label = "Good", isPrimary = true, position = 0)
    private val family = Tag(id = 5, groupId = 1, label = "Family", iconKey = "family", position = 0)
    private val running = Tag(id = 6, groupId = 1, label = "Running", iconKey = "run", position = 1)

    private fun post(id: Long, at: LocalDateTime, diary: Long = 1, title: String? = null, note: String? = null, tags: List<Tag> = emptyList(), photos: List<String> = emptyList()) =
        Post(
            id = id,
            dateTime = at,
            mood = good,
            title = title,
            note = note,
            tags = tags,
            diaryId = diary,
            photos = photos.mapIndexed { i, name -> PostPhoto(i.toLong(), name, "/data/photos/$name") },
        )

    private val labels = ExportLabels(
        title = "Journal",
        subtitle = "All entries",
        day = { it.toString() },
        time = { "%02d:%02d".format(it.hour, it.minute) },
        diaryName = { mapOf(1L to "Life", 2L to "Work")[it] },
        photo = "Photo",
        footer = "Made with Dakilio",
    )

    @Test
    fun `selects by period and diary, oldest first`() {
        val today = LocalDate.of(2026, 11, 10)
        val posts = listOf(
            post(3, LocalDateTime.of(2026, 11, 9, 20, 0), diary = 2),
            post(1, LocalDateTime.of(2025, 12, 31, 23, 0)),
            post(2, LocalDateTime.of(2026, 1, 1, 8, 0)),
            post(4, LocalDateTime.of(2026, 11, 9, 9, 0)),
        )
        assertEquals(listOf(1L, 2L, 4L, 3L), selectEntries(posts, ExportOptions(), today).map { it.id })
        assertEquals(listOf(2L, 4L, 3L), selectEntries(posts, ExportOptions(period = ExportPeriod.ThisYear), today).map { it.id })
        assertEquals(listOf(2L, 4L), selectEntries(posts, ExportOptions(period = ExportPeriod.ThisYear, diaryIds = setOf(1)), today).map { it.id })
        val custom = ExportPeriod.Custom(LocalDate.of(2026, 1, 1), LocalDate.of(2025, 12, 31))
        assertEquals(listOf(1L, 2L), selectEntries(posts, ExportOptions(period = custom), today).map { it.id })
    }

    @Test
    fun `last 12 months includes the same day a year ago plus one`() {
        val today = LocalDate.of(2026, 11, 10)
        assertEquals(LocalDate.of(2025, 11, 11) to today, ExportPeriod.Last12Months.range(today))
    }

    @Test
    fun `renders days, entries, tags and photos`() {
        val entries = listOf(
            post(1, LocalDateTime.of(2026, 11, 9, 9, 5), title = "Walk", note = "Nice.", tags = listOf(family, running), photos = listOf("a.jpg")),
            post(2, LocalDateTime.of(2026, 11, 9, 21, 0)),
            post(3, LocalDateTime.of(2026, 11, 10, 7, 30)),
        )
        val md = renderMarkdown(entries, labels, withPhotos = true)
        assertEquals(
            """
            |# Journal
            |
            |*All entries*
            |
            |## 2026-11-09
            |
            |### 09:05 · Good
            |
            |**Walk**
            |
            |Nice.
            |
            |*Family · Running*
            |
            |![Photo](photos/a.jpg)
            |
            |### 21:00 · Good
            |
            |## 2026-11-10
            |
            |### 07:30 · Good
            |
            |---
            |
            |Made with Dakilio
            |
            """.trimMargin(),
            md,
        )
    }

    @Test
    fun `photos are left out without them, diaries shown only when mixed`() {
        val one = listOf(post(1, LocalDateTime.of(2026, 11, 9, 9, 0), photos = listOf("a.jpg")))
        val md = renderMarkdown(one, labels, withPhotos = false)
        assertFalse("photos/" in md)
        assertFalse("Life" in md)
        val mixed = one + post(2, LocalDateTime.of(2026, 11, 9, 10, 0), diary = 2)
        val mixedMd = renderMarkdown(mixed, labels, withPhotos = false)
        assertTrue("### 09:00 · Good · Life" in mixedMd)
        assertTrue("### 10:00 · Good · Work" in mixedMd)
    }

    @Test
    fun `notes keep their line breaks and lose their formatting`() {
        assertEquals("first  \nsecond\n\nthird", noteToMarkdown("first\nsecond\n\nthird"))
        assertEquals("\\# not a heading  \n\\- not a list  \n1\\. not ordered", noteToMarkdown("# not a heading\n- not a list\n1. not ordered"))
        assertEquals("a \\*b\\* \\_c\\_ \\[d\\](e) \\<f\\>", noteToMarkdown("a *b* _c_ [d](e) <f>"))
        assertEquals("2026 was good. -5 degrees", noteToMarkdown("2026 was good. -5 degrees"))
    }
}
