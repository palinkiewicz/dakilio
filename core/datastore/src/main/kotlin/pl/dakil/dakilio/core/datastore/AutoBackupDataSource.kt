package pl.dakil.dakilio.core.datastore

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.MutablePreferences
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import javax.inject.Inject
import javax.inject.Qualifier
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import pl.dakil.dakilio.core.model.AutoBackupConfig
import pl.dakil.dakilio.core.model.AutoBackupFailure
import pl.dakil.dakilio.core.model.AutoBackupFrequency
import pl.dakil.dakilio.core.model.AutoBackupResult
import pl.dakil.dakilio.core.model.AutoBackupStatus

/**
 * Automatic backups' own DataStore file. It's never part of a `.dakilio` backup, and it's left out of
 * device-to-device transfer: neither the folder grant nor the Keystore key would come along.
 */
@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class AutoBackupStore

/** The stored password, encrypted with a Keystore key (Base64). */
data class SealedPassword(val ciphertext: String, val iv: String)

/** Automatic backups: the folder and options, what the last run did, and the stored password. */
class AutoBackupDataSource @Inject constructor(
    @AutoBackupStore private val dataStore: DataStore<Preferences>,
) {
    val config: Flow<AutoBackupConfig> = dataStore.data
        .map { prefs ->
            AutoBackupConfig(
                folderUri = prefs[FOLDER_URI],
                folderName = prefs[FOLDER_NAME],
                frequency = prefs[FREQUENCY].toEnum(AutoBackupFrequency.Daily),
                onlyWhileCharging = prefs[ONLY_WHILE_CHARGING] ?: true,
                keep = prefs[KEEP] ?: AutoBackupConfig.DEFAULT_KEEP,
                hasPassword = prefs[PASSWORD] != null,
                enabledAt = prefs[ENABLED_AT],
            )
        }
        .distinctUntilChanged()

    val status: Flow<AutoBackupStatus> = dataStore.data
        .map { prefs ->
            AutoBackupStatus(
                lastSuccessAt = prefs[LAST_SUCCESS_AT],
                lastRunAt = prefs[LAST_RUN_AT],
                lastResult = when (prefs[LAST_RESULT]) {
                    RESULT_WRITTEN -> AutoBackupResult.Written(prefs[ENTRIES] ?: 0, prefs[PHOTOS] ?: 0, prefs[BYTES] ?: 0)
                    RESULT_UNCHANGED -> AutoBackupResult.Unchanged
                    RESULT_FAILED -> AutoBackupResult.Failed(prefs[FAILURE].toEnum(AutoBackupFailure.Other))
                    else -> null
                },
                entries = prefs[ENTRIES] ?: 0,
                photos = prefs[PHOTOS] ?: 0,
                bytes = prefs[BYTES] ?: 0,
            )
        }
        .distinctUntilChanged()

    suspend fun fingerprint(): String? = dataStore.data.first()[FINGERPRINT]

    suspend fun password(): SealedPassword? {
        val prefs = dataStore.data.first()
        return SealedPassword(prefs[PASSWORD] ?: return null, prefs[PASSWORD_IV] ?: return null)
    }

    /** A new folder: what's known about the last one (its status, its fingerprint) starts over. */
    suspend fun setFolder(uri: String, name: String?, at: Long) {
        dataStore.edit {
            it[FOLDER_URI] = uri
            if (name != null) it[FOLDER_NAME] = name else it.remove(FOLDER_NAME)
            it[ENABLED_AT] = at
            it.clearStatus()
        }
    }

    suspend fun setFrequency(frequency: AutoBackupFrequency) {
        dataStore.edit { it[FREQUENCY] = frequency.name }
    }

    suspend fun setOnlyWhileCharging(only: Boolean) {
        dataStore.edit { it[ONLY_WHILE_CHARGING] = only }
    }

    suspend fun setKeep(keep: Int) {
        dataStore.edit { it[KEEP] = keep }
    }

    /** A changed password means the next run writes a backup, even if nothing else changed. */
    suspend fun setPassword(sealed: SealedPassword?) {
        dataStore.edit {
            if (sealed != null) {
                it[PASSWORD] = sealed.ciphertext
                it[PASSWORD_IV] = sealed.iv
            } else {
                it.remove(PASSWORD)
                it.remove(PASSWORD_IV)
            }
            it.remove(FINGERPRINT)
        }
    }

    /** Turns automatic backups off. The options (how often, charging, how many) stay for next time. */
    suspend fun turnOff() {
        dataStore.edit {
            it.remove(FOLDER_URI)
            it.remove(FOLDER_NAME)
            it.remove(ENABLED_AT)
            it.remove(PASSWORD)
            it.remove(PASSWORD_IV)
            it.clearStatus()
        }
    }

    /** Records a run at [at]. A failure keeps the last success and its counts. */
    suspend fun recordResult(result: AutoBackupResult, at: Long, fingerprint: String? = null) {
        dataStore.edit {
            it[LAST_RUN_AT] = at
            when (result) {
                is AutoBackupResult.Written -> {
                    it[LAST_RESULT] = RESULT_WRITTEN
                    it[LAST_SUCCESS_AT] = at
                    it[ENTRIES] = result.entries
                    it[PHOTOS] = result.photos
                    it[BYTES] = result.bytes
                    if (fingerprint != null) it[FINGERPRINT] = fingerprint
                    it.remove(FAILURE)
                }
                AutoBackupResult.Unchanged -> {
                    it[LAST_RESULT] = RESULT_UNCHANGED
                    it[LAST_SUCCESS_AT] = at
                    it.remove(FAILURE)
                }
                is AutoBackupResult.Failed -> {
                    it[LAST_RESULT] = RESULT_FAILED
                    it[FAILURE] = result.reason.name
                }
            }
        }
    }

    /** Forgets the fingerprint, so the next run writes even if nothing changed (the files went missing). */
    suspend fun forgetFingerprint() {
        dataStore.edit { it.remove(FINGERPRINT) }
    }

    private fun MutablePreferences.clearStatus() {
        remove(LAST_SUCCESS_AT)
        remove(LAST_RUN_AT)
        remove(LAST_RESULT)
        remove(FAILURE)
        remove(ENTRIES)
        remove(PHOTOS)
        remove(BYTES)
        remove(FINGERPRINT)
    }

    private inline fun <reified T : Enum<T>> String?.toEnum(default: T): T =
        this?.let { name -> enumValues<T>().firstOrNull { it.name == name } } ?: default

    private companion object {
        val FOLDER_URI = stringPreferencesKey("folder_uri")
        val FOLDER_NAME = stringPreferencesKey("folder_name")
        val ENABLED_AT = longPreferencesKey("enabled_at")
        val FREQUENCY = stringPreferencesKey("frequency")
        val ONLY_WHILE_CHARGING = booleanPreferencesKey("only_while_charging")
        val KEEP = intPreferencesKey("keep")
        val PASSWORD = stringPreferencesKey("password")
        val PASSWORD_IV = stringPreferencesKey("password_iv")
        val FINGERPRINT = stringPreferencesKey("fingerprint")
        val LAST_SUCCESS_AT = longPreferencesKey("last_success_at")
        val LAST_RUN_AT = longPreferencesKey("last_run_at")
        val LAST_RESULT = stringPreferencesKey("last_result")
        val FAILURE = stringPreferencesKey("failure")
        val ENTRIES = intPreferencesKey("entries")
        val PHOTOS = intPreferencesKey("photos")
        val BYTES = longPreferencesKey("bytes")

        const val RESULT_WRITTEN = "written"
        const val RESULT_UNCHANGED = "unchanged"
        const val RESULT_FAILED = "failed"
    }
}
