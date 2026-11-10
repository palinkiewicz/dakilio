package pl.dakil.dakilio.core.datastore.di

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.preferencesDataStoreFile
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import pl.dakil.dakilio.core.common.ApplicationScope
import pl.dakil.dakilio.core.common.DakilioDispatchers
import pl.dakil.dakilio.core.common.Dispatcher
import pl.dakil.dakilio.core.datastore.AutoBackupStore
import pl.dakil.dakilio.core.datastore.LockStore

@Module
@InstallIn(SingletonComponent::class)
internal object DataStoreModule {
    @Provides
    @Singleton
    fun preferences(
        @ApplicationContext context: Context,
        @ApplicationScope scope: CoroutineScope,
        @Dispatcher(DakilioDispatchers.IO) io: CoroutineDispatcher,
    ): DataStore<Preferences> = PreferenceDataStoreFactory.create(
        scope = CoroutineScope(scope.coroutineContext + io),
    ) { context.preferencesDataStoreFile("settings") }

    /** The app lock, in its own file so backups (which carry `settings`) never touch it. */
    @Provides
    @Singleton
    @LockStore
    fun lockPreferences(
        @ApplicationContext context: Context,
        @ApplicationScope scope: CoroutineScope,
        @Dispatcher(DakilioDispatchers.IO) io: CoroutineDispatcher,
    ): DataStore<Preferences> = PreferenceDataStoreFactory.create(
        scope = CoroutineScope(scope.coroutineContext + io),
    ) { context.preferencesDataStoreFile("lock") }

    /** Automatic backups, in their own file: not in backups, and not moved to a new phone. */
    @Provides
    @Singleton
    @AutoBackupStore
    fun autoBackupPreferences(
        @ApplicationContext context: Context,
        @ApplicationScope scope: CoroutineScope,
        @Dispatcher(DakilioDispatchers.IO) io: CoroutineDispatcher,
    ): DataStore<Preferences> = PreferenceDataStoreFactory.create(
        scope = CoroutineScope(scope.coroutineContext + io),
    ) { context.preferencesDataStoreFile(AUTO_BACKUP_FILE) }

    /** Also named in `data_extraction_rules.xml` (as `datastore/autobackup.preferences_pb`). */
    private const val AUTO_BACKUP_FILE = "autobackup"
}
