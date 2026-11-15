package pl.dakil.dakilio.feature.export.impl

import java.time.LocalDate
import java.time.LocalTime
import pl.dakil.dakilio.core.model.Post

/** The columns of Daylio's own CSV export, in its order; tools written for those files read ours. */
internal val CSV_COLUMNS = listOf("full_date", "date", "weekday", "time", "mood", "activities", "note_title", "note")

/** An extra last column, only when the export holds more than one diary. Daylio has no diaries. */
internal const val CSV_DIARY_COLUMN = "diary"

/** What the CSV needs that depends on the language and region. */
class CsvLabels(
    /** The day without its year ("1 November"); the year is in `full_date`. */
    val date: (LocalDate) -> String,
    val weekday: (LocalDate) -> String,
    val time: (LocalTime) -> String,
    val diaryName: (Long) -> String?,
)

/**
 * The journal as a table in the shape of Daylio's CSV export: one row per entry, **newest first**
 * like Daylio's, with the same eight columns. `full_date` is ISO (`2026-11-01`), tags go into
 * `activities` separated by ` | `, and line breaks in a note become `<br>`, as Daylio writes them.
 * Entries without a mood leave `mood` empty. Photos aren't part of a CSV.
 */
fun renderCsv(entries: List<Post>, labels: CsvLabels): String = buildString {
    val showDiary = entries.map { it.diaryId }.distinct().size > 1
    appendRow(if (showDiary) CSV_COLUMNS + CSV_DIARY_COLUMN else CSV_COLUMNS)
    entries.sortedWith(compareByDescending<Post> { it.dateTime }.thenByDescending { it.id }).forEach { post ->
        val row = listOf(
            post.date.toString(),
            labels.date(post.date),
            labels.weekday(post.date),
            labels.time(post.dateTime.toLocalTime()),
            post.mood?.label.orEmpty(),
            post.tags.joinToString(" | ") { it.label },
            post.title?.trim().orEmpty(),
            post.note?.trim().orEmpty().replace("\r\n", "\n").replace("\n", "<br>"),
        )
        appendRow(if (showDiary) row + labels.diaryName(post.diaryId).orEmpty() else row)
    }
}

private fun StringBuilder.appendRow(fields: List<String>) {
    fields.joinTo(this, ",", transform = ::csvField)
    append('\n')
}

/** A field as RFC 4180 wants it: quoted when it holds a comma, a quote, a line break or outer spaces. */
internal fun csvField(text: String): String {
    val needsQuotes = text.any { it == ',' || it == '"' || it == '\n' || it == '\r' } || text != text.trim()
    return if (needsQuotes) "\"" + text.replace("\"", "\"\"") + "\"" else text
}
