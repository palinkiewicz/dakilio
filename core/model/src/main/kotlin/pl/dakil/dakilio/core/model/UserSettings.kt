package pl.dakil.dakilio.core.model

enum class ThemeMode { System, Light, Dark }

data class UserSettings(
    val themeMode: ThemeMode = ThemeMode.System,
    /** Diaries of the last saved entry; the editor's default when the timeline shows "All". */
    val lastEntryDiaryIds: Set<Long> = emptySet(),
    val sampleHintDismissed: Boolean = false,
)
