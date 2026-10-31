package pl.dakil.dakilio.core.model

enum class ThemeMode { System, Light, Dark }

/** How tags are shown when picking them in the entry editor. Read-only tag displays always use chips. */
enum class TagStyle { Icons, Chips }

data class UserSettings(
    val themeMode: ThemeMode = ThemeMode.System,
    /** Diaries of the last saved entry; the editor's default when the timeline shows "All". */
    val lastEntryDiaryIds: Set<Long> = emptySet(),
    val sampleHintDismissed: Boolean = false,
    val tagStyle: TagStyle = TagStyle.Icons,
)
