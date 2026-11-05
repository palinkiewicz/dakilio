package pl.dakil.dakilio.core.model

import java.time.DayOfWeek
import java.time.temporal.WeekFields
import java.util.Locale
import kotlin.math.roundToInt

enum class ThemeMode { System, Light, Dark }

/** How tags are shown when picking them in the entry editor. Read-only tag displays always use chips. */
enum class TagStyle { Icons, Chips }

/** How many lines of an entry's note a post card shows; [Full] shows all of it (and the whole title). */
enum class PreviewLength(val noteLines: Int?) { Short(4), Medium(8), Long(16), Full(null) }

/** The first day of the week (habits, the Insights heatmap, weekday rows). [Region] follows the device region. */
enum class WeekStart(private val day: DayOfWeek?) {
    Region(null),
    Monday(DayOfWeek.MONDAY),
    Saturday(DayOfWeek.SATURDAY),
    Sunday(DayOfWeek.SUNDAY),
    ;

    /** The day this choice means, with [region] (the device's own locale) answering for [Region]. */
    fun resolve(region: Locale): DayOfWeek = day ?: WeekFields.of(region).firstDayOfWeek
}

/**
 * How large stored photos may be, as the length of their long edge in pixels. [Original] keeps the
 * picked size (up to [PhotoSizing.MAX_ORIGINAL_PIXELS], so a huge photo can't exhaust memory).
 */
enum class PhotoSize(val maxEdge: Int?) {
    Px640(640),
    Px960(960),
    Px1280(1280),
    Px1600(1600),
    Px2048(2048),
    Original(null),
}

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
    /** The timeline shows the floating month pill (▲ month ▼) once scrolled. */
    val monthNavigator: Boolean = true,
    /** Saving edits to an existing entry closes it; otherwise the entry goes back to reading. */
    val closeAfterSave: Boolean = true,
    /** Keywords (the extra) add their tag at once instead of suggesting it. */
    val keywordAutoAdd: Boolean = false,
    val weekStart: WeekStart = WeekStart.Region,
    /** How far new photos are scaled down when they're stored. */
    val photoSize: PhotoSize = PhotoSize.Px1280,
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
