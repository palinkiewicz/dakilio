package pl.dakil.dakilio.feature.export.impl

import java.time.LocalDate
import java.time.LocalTime
import pl.dakil.dakilio.core.model.Post

enum class ExportFormat { Pdf, Markdown }

/** Which entries go in. Relative periods keep counting from today. */
sealed interface ExportPeriod {
    data object All : ExportPeriod

    data object ThisYear : ExportPeriod

    data object Last12Months : ExportPeriod

    /** Both days included. */
    data class Custom(val from: LocalDate, val to: LocalDate) : ExportPeriod

    /** The first and last day it covers on [today]; null ends for no limit. */
    fun range(today: LocalDate): Pair<LocalDate?, LocalDate?> = when (this) {
        All -> null to null
        ThisYear -> today.withDayOfYear(1) to today
        Last12Months -> today.minusMonths(12).plusDays(1) to today
        is Custom -> minOf(from, to) to maxOf(from, to)
    }
}

data class ExportOptions(
    val format: ExportFormat = ExportFormat.Pdf,
    val period: ExportPeriod = ExportPeriod.All,
    /** Empty means every diary. */
    val diaryIds: Set<Long> = emptySet(),
    val photos: Boolean = true,
)

/** The entries [options] select, oldest first, like a book. */
fun selectEntries(posts: List<Post>, options: ExportOptions, today: LocalDate): List<Post> {
    val (from, to) = options.period.range(today)
    return posts
        .filter { post ->
            val date = post.date
            (from == null || !date.isBefore(from)) &&
                (to == null || !date.isAfter(to)) &&
                (options.diaryIds.isEmpty() || post.diaryId in options.diaryIds)
        }
        .sortedWith(compareBy<Post> { it.dateTime }.thenBy { it.id })
}

/**
 * Everything the Markdown needs that depends on the language. The UI builds it, so the renderer
 * stays plain Kotlin and testable.
 */
class ExportLabels(
    val title: String,
    /** "1 Jan 2026 – 10 Nov 2026 · 412 entries", under the title. */
    val subtitle: String,
    val day: (LocalDate) -> String,
    val time: (LocalTime) -> String,
    val diaryName: (Long) -> String?,
    val photo: String,
    /** "Made with Dakilio", at the end. */
    val footer: String,
)

/** Where a photo is written in a Markdown ZIP, relative to `journal.md`. */
fun markdownPhotoPath(fileName: String): String = "photos/$fileName"

/**
 * The journal as Markdown: a heading per day, then each entry with its time, mood and (when more
 * than one diary is in) diary, its title, the note, its tags and, with [withPhotos], its photos as
 * images under `photos/`.
 */
fun renderMarkdown(entries: List<Post>, labels: ExportLabels, withPhotos: Boolean): String = buildString {
    append("# ").append(escapeInline(labels.title)).append("\n\n")
    append('*').append(escapeInline(labels.subtitle)).append("*\n")
    val showDiary = entries.map { it.diaryId }.distinct().size > 1
    var currentDay: LocalDate? = null
    entries.forEach { post ->
        if (post.date != currentDay) {
            currentDay = post.date
            append("\n## ").append(escapeInline(labels.day(post.date))).append('\n')
        }
        val heading = buildList {
            add(labels.time(post.dateTime.toLocalTime()))
            post.mood?.let { add(it.label) }
            if (showDiary) labels.diaryName(post.diaryId)?.let(::add)
        }.joinToString(" · ")
        append("\n### ").append(escapeInline(heading)).append('\n')
        post.title?.takeIf { it.isNotBlank() }?.let { append("\n**").append(escapeInline(it.trim())).append("**\n") }
        post.note?.takeIf { it.isNotBlank() }?.let { append('\n').append(noteToMarkdown(it.trim())).append('\n') }
        if (post.tags.isNotEmpty()) {
            append("\n*").append(post.tags.joinToString(" · ") { escapeInline(it.label) }).append("*\n")
        }
        if (withPhotos && post.photos.isNotEmpty()) {
            append('\n')
            post.photos.forEach { append("![").append(escapeInline(labels.photo)).append("](").append(markdownPhotoPath(it.fileName)).append(")\n") }
        }
    }
    append("\n---\n\n").append(escapeInline(labels.footer)).append('\n')
}

/**
 * A plain-text note as Markdown that reads the same: paragraphs stay, single line breaks become hard
 * breaks (two trailing spaces), and anything Markdown would take as formatting is escaped.
 */
internal fun noteToMarkdown(note: String): String =
    note.replace("\r\n", "\n").split("\n\n").joinToString("\n\n") { paragraph ->
        paragraph.split('\n').joinToString("  \n") { line -> escapeLineStart(escapeInline(line.trimEnd())) }
    }

private val INLINE_SPECIAL = setOf('\\', '`', '*', '_', '[', ']', '<', '>', '#', '|', '~')

/** Escapes characters Markdown reads as inline formatting. */
internal fun escapeInline(text: String): String = buildString(text.length) {
    text.forEach { c ->
        if (c in INLINE_SPECIAL) append('\\')
        append(c)
    }
}

private val ORDERED_LIST = Regex("""^(\s*\d+)([.)])(\s)""")
private val BLOCK_START = Regex("""^(\s*)([-+=])(\s|$)""")

/** Escapes what would start a block: list markers ("- ", "1. ") and setext underlines. */
internal fun escapeLineStart(line: String): String = when {
    ORDERED_LIST.containsMatchIn(line) -> line.replaceFirst(ORDERED_LIST, "$1\\\\$2$3")
    BLOCK_START.containsMatchIn(line) -> line.replaceFirst(BLOCK_START, "$1\\\\$2$3")
    else -> line
}
