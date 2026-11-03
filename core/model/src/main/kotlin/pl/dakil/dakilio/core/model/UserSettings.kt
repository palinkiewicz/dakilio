package pl.dakil.dakilio.core.model

import kotlin.math.roundToInt

enum class ThemeMode { System, Light, Dark }

/** How tags are shown when picking them in the entry editor. Read-only tag displays always use chips. */
enum class TagStyle { Icons, Chips }

/** How many lines of an entry's note a post card shows; [Full] shows all of it (and the whole title). */
enum class PreviewLength(val noteLines: Int?) { Short(4), Medium(8), Long(16), Full(null) }

data class UserSettings(
    val themeMode: ThemeMode = ThemeMode.System,
    /** Diary of the last saved entry; the editor's default when the timeline shows "All". */
    val lastEntryDiaryId: Long? = null,
    val sampleHintDismissed: Boolean = false,
    val tagStyle: TagStyle = TagStyle.Icons,
    /** Beginner how-to lines across the app (see `Hint` in core:ui). */
    val showHints: Boolean = true,
    /** Size of entry titles and notes on the timeline (and in search results), as a factor of the default. */
    val timelineTextScale: Float = TextScale.DEFAULT,
    /** Size of the title and note fields in the entry editor, as a factor of the default. */
    val editorTextScale: Float = TextScale.DEFAULT,
    /** How much of each note post cards show (timeline and search). */
    val previewLength: PreviewLength = PreviewLength.Short,
    /** Post cards show tags as icons only, without their names. */
    val compactTags: Boolean = false,
    /** Saving edits to an existing entry closes it; otherwise the entry goes back to reading. */
    val closeAfterSave: Boolean = true,
)

/** The range of the entry text size settings. Only entry titles and notes scale, never the rest of the UI. */
object TextScale {
    const val DEFAULT = 1f
    const val MIN = 0.8f
    const val MAX = 1.6f
    const val STEP = 0.1f

    /** Snaps a stored or dragged value onto the slider's steps, within range. */
    fun coerce(value: Float): Float =
        (value.coerceIn(MIN, MAX) / STEP).roundToInt() * STEP
}
