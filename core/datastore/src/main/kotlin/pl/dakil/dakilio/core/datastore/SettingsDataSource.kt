package pl.dakil.dakilio.core.datastore

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import javax.inject.Inject
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import pl.dakil.dakilio.core.model.TagStyle
import pl.dakil.dakilio.core.model.ThemeMode
import pl.dakil.dakilio.core.model.UserSettings

/** The user's own choices. Fetched/created content lives in Room; preferences live here. */
class SettingsDataSource @Inject constructor(
    private val dataStore: DataStore<Preferences>,
) {
    val settings: Flow<UserSettings> = dataStore.data
        .map { prefs ->
            UserSettings(
                themeMode = prefs[THEME_MODE]?.let { name -> ThemeMode.entries.firstOrNull { it.name == name } }
                    ?: ThemeMode.System,
                lastEntryDiaryIds = prefs[LAST_ENTRY_DIARIES].orEmpty().mapNotNull(String::toLongOrNull).toSet(),
                sampleHintDismissed = prefs[SAMPLE_HINT_DISMISSED] ?: false,
                tagStyle = prefs[TAG_STYLE]?.let { name -> TagStyle.entries.firstOrNull { it.name == name } }
                    ?: TagStyle.Icons,
                showHints = prefs[SHOW_HINTS] ?: true,
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

    suspend fun setLastEntryDiaryIds(ids: Set<Long>) {
        dataStore.edit { it[LAST_ENTRY_DIARIES] = ids.map(Long::toString).toSet() }
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

    private companion object {
        val THEME_MODE = stringPreferencesKey("theme_mode")
        val LAST_ENTRY_DIARIES = stringSetPreferencesKey("last_entry_diary_ids")
        val SAMPLE_HINT_DISMISSED = booleanPreferencesKey("sample_hint_dismissed")
        val TAG_STYLE = stringPreferencesKey("tag_style")
        val SHOW_HINTS = booleanPreferencesKey("show_hints")
        val INTRO_STARTED = booleanPreferencesKey("intro_started")
        val INTRO_DONE = booleanPreferencesKey("intro_done")
    }
}

data class IntroProgress(val started: Boolean, val done: Boolean)
