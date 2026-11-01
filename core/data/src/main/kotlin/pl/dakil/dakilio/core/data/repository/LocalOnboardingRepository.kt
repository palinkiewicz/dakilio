package pl.dakil.dakilio.core.data.repository

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import pl.dakil.dakilio.core.common.DakilioDispatchers
import pl.dakil.dakilio.core.common.Dispatcher
import pl.dakil.dakilio.core.database.DakilioDatabase
import pl.dakil.dakilio.core.database.DefaultData
import pl.dakil.dakilio.core.database.dao.TagDao
import pl.dakil.dakilio.core.datastore.SettingsDataSource
import pl.dakil.dakilio.core.model.SuggestedGroup

internal class LocalOnboardingRepository @Inject constructor(
    @ApplicationContext private val context: Context,
    // Injecting a DAO builds the Room object but doesn't open the database; only queries do.
    private val tagDao: TagDao,
    private val settings: SettingsDataSource,
    @Dispatcher(DakilioDispatchers.IO) private val io: CoroutineDispatcher,
) : OnboardingRepository {
    // Read on every access, so a language change is picked up.
    override val suggestedGroups: List<SuggestedGroup> get() = DefaultData.suggestedGroups(context)

    override suspend fun shouldShowIntro(): Boolean {
        val progress = settings.introProgress.first()
        if (progress.done) return false
        if (progress.started) return true
        // Never shown: a fresh install, unless the database already exists. Then this install
        // predates the intro, and the intro's group step would delete groups the user has.
        return withContext(io) { !DakilioDatabase.exists(context) }
    }

    override suspend fun markIntroStarted() = settings.setIntroStarted()

    override suspend fun finishIntro() = settings.setIntroDone()

    override suspend fun keepStarterGroups(keys: Set<String>) {
        val dropped = DefaultData.suggestedGroups(context)
            .filter { it.key !in keys }
            .mapNotNull { DefaultData.seedGroupId(it.key) }
        // Always touches the database, so it's created (and seeded) even when every group is kept.
        tagDao.deleteGroups(dropped)
    }

    override suspend fun addSuggestedGroup(key: String) {
        val group = DefaultData.suggestedGroups(context).firstOrNull { it.key == key } ?: return
        tagDao.insertGroupWithTags(group.name, group.tags.map { it.label to it.iconKey })
    }
}
