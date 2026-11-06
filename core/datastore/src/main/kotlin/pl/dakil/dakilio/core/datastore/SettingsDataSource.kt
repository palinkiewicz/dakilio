package pl.dakil.dakilio.core.datastore

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import javax.inject.Inject
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import pl.dakil.dakilio.core.model.PhotoSize
import pl.dakil.dakilio.core.model.PreviewLength
import pl.dakil.dakilio.core.model.TagLimit
import pl.dakil.dakilio.core.model.TagStyle
import pl.dakil.dakilio.core.model.TextScale
import pl.dakil.dakilio.core.model.ThemeMode
import pl.dakil.dakilio.core.model.UserSettings
import pl.dakil.dakilio.core.model.WeekStart

/** The user's own choices. Fetched/created content lives in Room; preferences live here. */
class SettingsDataSource @Inject constructor(
    private val dataStore: DataStore<Preferences>,
) {
    val settings: Flow<UserSettings> = dataStore.data
        .map { prefs ->
            UserSettings(
                themeMode = prefs[THEME_MODE]?.let { name -> ThemeMode.entries.firstOrNull { it.name == name } }
                    ?: ThemeMode.System,
                lastEntryDiaryId = prefs[LAST_ENTRY_DIARY],
                timelineDiaryId = prefs[TIMELINE_DIARY],
                sampleHintDismissed = prefs[SAMPLE_HINT_DISMISSED] ?: false,
                tagStyle = prefs[TAG_STYLE]?.let { name -> TagStyle.entries.firstOrNull { it.name == name } }
                    ?: TagStyle.Icons,
                showHints = prefs[SHOW_HINTS] ?: true,
                timelineTextScale = prefs[TIMELINE_TEXT_SCALE]?.let(TextScale::coerce) ?: TextScale.DEFAULT,
                editorTextScale = prefs[EDITOR_TEXT_SCALE]?.let(TextScale::coerce) ?: TextScale.DEFAULT,
                previewLength = prefs[PREVIEW_LENGTH]?.let { name -> PreviewLength.entries.firstOrNull { it.name == name } }
                    ?: PreviewLength.Short,
                compactTags = prefs[COMPACT_TAGS] ?: false,
                // Never picked: what cards showed before the option existed (icons take less room).
                tagLimit = prefs[TAG_LIMIT]?.let(TagLimit::parse)
                    ?: if (prefs[COMPACT_TAGS] == true) TagLimit.of(TagLimit.MAX) else TagLimit.Default,
                monthNavigator = prefs[MONTH_NAVIGATOR] ?: true,
                monthNavigatorInAppBar = prefs[MONTH_NAVIGATOR_IN_APP_BAR] ?: false,
                closeAfterSave = prefs[CLOSE_AFTER_SAVE] ?: true,
                keywordAutoAdd = prefs[KEYWORD_AUTO_ADD] ?: false,
                weekStart = prefs[WEEK_START]?.let { name -> WeekStart.entries.firstOrNull { it.name == name } }
                    ?: WeekStart.Region,
                photoSize = prefs[PHOTO_SIZE]?.let { name -> PhotoSize.entries.firstOrNull { it.name == name } }
                    ?: PhotoSize.Px1280,
            )
        }
        .distinctUntilChanged()

    /**
     * Where the first-run intro stands: [IntroProgress.started] once it was shown, [IntroProgress.done]
     * once it was finished. Kept out of [UserSettings], which the whole UI observes.
     */
    val introProgress: Flow<IntroProgress> = dataStore.data
        .map { prefs -> IntroProgress(started = prefs[INTRO_STARTED] ?: false, done = prefs[INTRO_DONE] ?: false) }
        .distinctUntilChanged()

    suspend fun setIntroStarted() {
        dataStore.edit { it[INTRO_STARTED] = true }
    }

    suspend fun setIntroDone() {
        dataStore.edit { it[INTRO_DONE] = true }
    }

    suspend fun setThemeMode(mode: ThemeMode) {
        dataStore.edit { it[THEME_MODE] = mode.name }
    }

    suspend fun setLastEntryDiaryId(id: Long) {
        dataStore.edit { it[LAST_ENTRY_DIARY] = id }
    }

    /** `null` is "All diaries". */
    suspend fun setTimelineDiaryId(id: Long?) {
        dataStore.edit { if (id == null) it.remove(TIMELINE_DIARY) else it[TIMELINE_DIARY] = id }
    }

    suspend fun setSampleHintDismissed(dismissed: Boolean) {
        dataStore.edit { it[SAMPLE_HINT_DISMISSED] = dismissed }
    }

    suspend fun setTagStyle(style: TagStyle) {
        dataStore.edit { it[TAG_STYLE] = style.name }
    }

    suspend fun setShowHints(show: Boolean) {
        dataStore.edit { it[SHOW_HINTS] = show }
    }

    suspend fun setTimelineTextScale(scale: Float) {
        dataStore.edit { it[TIMELINE_TEXT_SCALE] = TextScale.coerce(scale) }
    }

    suspend fun setEditorTextScale(scale: Float) {
        dataStore.edit { it[EDITOR_TEXT_SCALE] = TextScale.coerce(scale) }
    }

    suspend fun setPreviewLength(length: PreviewLength) {
        dataStore.edit { it[PREVIEW_LENGTH] = length.name }
    }

    suspend fun setTagLimit(limit: TagLimit) {
        dataStore.edit { it[TAG_LIMIT] = limit.key }
    }

    suspend fun setCompactTags(compact: Boolean) {
        dataStore.edit { it[COMPACT_TAGS] = compact }
    }

    suspend fun setMonthNavigator(show: Boolean) {
        dataStore.edit { it[MONTH_NAVIGATOR] = show }
    }

    suspend fun setMonthNavigatorInAppBar(inAppBar: Boolean) {
        dataStore.edit { it[MONTH_NAVIGATOR_IN_APP_BAR] = inAppBar }
    }

    suspend fun setCloseAfterSave(close: Boolean) {
        dataStore.edit { it[CLOSE_AFTER_SAVE] = close }
    }

    suspend fun setKeywordAutoAdd(auto: Boolean) {
        dataStore.edit { it[KEYWORD_AUTO_ADD] = auto }
    }

    suspend fun setWeekStart(start: WeekStart) {
        dataStore.edit { it[WEEK_START] = start.name }
    }

    suspend fun setPhotoSize(size: PhotoSize) {
        dataStore.edit { it[PHOTO_SIZE] = size.name }
    }

    private companion object {
        val THEME_MODE = stringPreferencesKey("theme_mode")
        val LAST_ENTRY_DIARY = longPreferencesKey("last_entry_diary_id")
        val TIMELINE_DIARY = longPreferencesKey("timeline_diary_id")
        val SAMPLE_HINT_DISMISSED = booleanPreferencesKey("sample_hint_dismissed")
        val TAG_STYLE = stringPreferencesKey("tag_style")
        val SHOW_HINTS = booleanPreferencesKey("show_hints")
        val TIMELINE_TEXT_SCALE = floatPreferencesKey("timeline_text_scale")
        val EDITOR_TEXT_SCALE = floatPreferencesKey("editor_text_scale")
        val PREVIEW_LENGTH = stringPreferencesKey("preview_length")
        val COMPACT_TAGS = booleanPreferencesKey("compact_tags")
        val TAG_LIMIT = stringPreferencesKey("tag_limit")
        val MONTH_NAVIGATOR = booleanPreferencesKey("month_navigator")
        val MONTH_NAVIGATOR_IN_APP_BAR = booleanPreferencesKey("month_navigator_in_app_bar")
        val CLOSE_AFTER_SAVE = booleanPreferencesKey("close_after_save")
        val KEYWORD_AUTO_ADD = booleanPreferencesKey("keyword_auto_add")
        val WEEK_START = stringPreferencesKey("week_start")
        val PHOTO_SIZE = stringPreferencesKey("photo_size")
        val INTRO_STARTED = booleanPreferencesKey("intro_started")
        val INTRO_DONE = booleanPreferencesKey("intro_done")
    }
}

data class IntroProgress(val started: Boolean, val done: Boolean)
