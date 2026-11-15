package pl.dakil.dakilio.feature.export.impl

import java.time.LocalDateTime
import org.junit.Assert.assertEquals
import org.junit.Test
import pl.dakil.dakilio.core.model.Mood
import pl.dakil.dakilio.core.model.Post
import pl.dakil.dakilio.core.model.Tag

class CsvExportTest {
    private val good = Mood(id = 4, level = 4, label = "Good", isPrimary = true, position = 0)
    private val family = Tag(id = 5, groupId = 1, label = "Family", iconKey = "family", position = 0)
    private val running = Tag(id = 6, groupId = 1, label = "Running, slow", iconKey = "run", position = 1)

    private val labels = CsvLabels(
        date = { "${it.dayOfMonth} ${it.month.name.lowercase().replaceFirstChar(Char::titlecase)}" },
        weekday = { it.dayOfWeek.name.lowercase().replaceFirstChar(Char::titlecase) },
        time = { "%02d:%02d".format(it.hour, it.minute) },
        diaryName = { mapOf(1L to "Life", 2L to "Work")[it] },
    )

    private fun post(id: Long, at: LocalDateTime, mood: Mood? = good, diary: Long = 1, title: String? = null, note: String? = null, tags: List<Tag> = emptyList()) =
        Post(id = id, dateTime = at, mood = mood, title = title, note = note, tags = tags, diaryId = diary, photos = emptyList())

    @Test
    fun `rows follow Daylio's columns, newest first`() {
        val csv = renderCsv(
            listOf(
                post(1, LocalDateTime.of(2026, 10, 31, 8, 5), title = "Morning", note = "Up early", tags = listOf(family)),
                post(2, LocalDateTime.of(2026, 11, 1, 13, 27), mood = null),
            ),
            labels,
        )
        assertEquals(
            """
            full_date,date,weekday,time,mood,activities,note_title,note
            2026-11-01,1 November,Sunday,13:27,,,,
            2026-10-31,31 October,Saturday,08:05,Good,Family,Morning,Up early
            """.trimIndent() + "\n",
            csv,
        )
    }

    @Test
    fun `tags are joined like Daylio's activities and risky fields are quoted`() {
        val csv = renderCsv(
            listOf(post(1, LocalDateTime.of(2026, 11, 1, 9, 0), title = "She said \"hi\"", note = "First line\nSecond, with a comma\r\nThird", tags = listOf(family, running))),
            labels,
        )
        assertEquals(
            "2026-11-01,1 November,Sunday,09:00,Good,\"Family | Running, slow\",\"She said \"\"hi\"\"\",\"First line<br>Second, with a comma<br>Third\"",
            csv.lines()[1],
        )
    }

    @Test
    fun `a diary column appears only with more than one diary`() {
        val one = renderCsv(listOf(post(1, LocalDateTime.of(2026, 11, 1, 9, 0))), labels)
        assertEquals(CSV_COLUMNS.joinToString(","), one.lines().first())

        val two = renderCsv(
            listOf(post(1, LocalDateTime.of(2026, 11, 1, 9, 0)), post(2, LocalDateTime.of(2026, 11, 1, 10, 0), diary = 2), post(3, LocalDateTime.of(2026, 11, 1, 11, 0), diary = 9)),
            labels,
        )
        val lines = two.lines()
        assertEquals(CSV_COLUMNS.joinToString(",") + ",diary", lines[0])
        assertEquals("2026-11-01,1 November,Sunday,11:00,Good,,,,", lines[1]) // an unknown diary leaves it empty
        assertEquals("2026-11-01,1 November,Sunday,10:00,Good,,,,Work", lines[2])
        assertEquals("2026-11-01,1 November,Sunday,09:00,Good,,,,Life", lines[3])
    }

    @Test
    fun `no entries is just the header`() {
        assertEquals(CSV_COLUMNS.joinToString(",") + "\n", renderCsv(emptyList(), labels))
    }

    @Test
    fun `fields are quoted only when they need it`() {
        assertEquals("plain", csvField("plain"))
        assertEquals("", csvField(""))
        assertEquals("\"a,b\"", csvField("a,b"))
        assertEquals("\"a \"\"b\"\"\"", csvField("a \"b\""))
        assertEquals("\" padded\"", csvField(" padded"))
        assertEquals("\"two\nlines\"", csvField("two\nlines"))
    }
}
