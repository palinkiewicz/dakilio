package pl.dakil.dakilio.core.datastore

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import javax.inject.Inject
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map

/**
 * Recent searches, newest first. Each one is an opaque string the search screen encodes itself, so
 * this layer never needs to know what a search consists of.
 */
class SearchHistoryDataSource @Inject constructor(
    private val dataStore: DataStore<Preferences>,
) {
    val recent: Flow<List<String>> = dataStore.data
        .map { prefs -> prefs[RECENT_SEARCHES]?.split(SEPARATOR)?.filter { it.isNotEmpty() }.orEmpty() }
        .distinctUntilChanged()

    /** Puts [entry] first (moving it up if it was already there), keeping at most [MAX] entries. */
    suspend fun push(entry: String) {
        if (entry.isEmpty() || SEPARATOR in entry) return
        dataStore.edit { prefs ->
            val current = prefs[RECENT_SEARCHES]?.split(SEPARATOR).orEmpty()
            prefs[RECENT_SEARCHES] = (listOf(entry) + (current - entry)).filter { it.isNotEmpty() }.take(MAX).joinToString(SEPARATOR)
        }
    }

    suspend fun remove(entry: String) {
        dataStore.edit { prefs ->
            val current = prefs[RECENT_SEARCHES]?.split(SEPARATOR).orEmpty()
            prefs[RECENT_SEARCHES] = (current - entry).joinToString(SEPARATOR)
        }
    }

    suspend fun clear() {
        dataStore.edit { it.remove(RECENT_SEARCHES) }
    }

    private companion object {
        const val MAX = 8

        /** ASCII record separator: never typed, and JSON escapes control characters anyway. */
        const val SEPARATOR = "\u001E"
        val RECENT_SEARCHES = stringPreferencesKey("recent_searches")
    }
}
