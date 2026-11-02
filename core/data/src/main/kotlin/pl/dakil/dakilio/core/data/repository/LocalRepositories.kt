package pl.dakil.dakilio.core.data.repository

import java.time.Clock
import javax.inject.Inject
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import pl.dakil.dakilio.core.common.DakilioDispatchers
import pl.dakil.dakilio.core.common.Dispatcher
import pl.dakil.dakilio.core.database.DefaultData
import pl.dakil.dakilio.core.database.dao.DiaryDao
import pl.dakil.dakilio.core.database.dao.MoodDao
import pl.dakil.dakilio.core.database.dao.PostDao
import pl.dakil.dakilio.core.database.dao.TagDao
import pl.dakil.dakilio.core.database.entity.DiaryEntity
import pl.dakil.dakilio.core.database.entity.MoodEntity
import pl.dakil.dakilio.core.database.entity.PostEntity
import pl.dakil.dakilio.core.database.entity.TagEntity
import pl.dakil.dakilio.core.database.entity.TagGroupEntity
import pl.dakil.dakilio.core.datastore.SettingsDataSource
import pl.dakil.dakilio.core.model.ArchivedTags
import pl.dakil.dakilio.core.model.Diary
import pl.dakil.dakilio.core.model.GroupUsage
import pl.dakil.dakilio.core.model.Mood
import pl.dakil.dakilio.core.model.MOOD_LEVELS
import pl.dakil.dakilio.core.model.Post
import pl.dakil.dakilio.core.model.PostDraft
import pl.dakil.dakilio.core.model.TagGroup
import pl.dakil.dakilio.core.model.TagStyle
import pl.dakil.dakilio.core.model.TagUsage
import pl.dakil.dakilio.core.model.ThemeMode
import pl.dakil.dakilio.core.model.UserSettings

internal class LocalMoodRepository @Inject constructor(private val dao: MoodDao) : MoodRepository {
    override val moods: Flow<List<Mood>> = dao.observeActiveMoods().map { list -> list.map { it.asModel() } }

    override val archivedMoods: Flow<List<Mood>> = dao.observeArchivedMoods().map { list -> list.map { it.asModel() } }

    override suspend fun addAlias(level: Int, label: String) {
        require(level in 1..MOOD_LEVELS)
        val name = label.trim().ifEmpty { return }
        dao.insert(MoodEntity(level = level, label = name, isPrimary = false, position = dao.maxAliasPosition(level) + 1))
    }

    override suspend fun rename(id: Long, label: String) {
        val name = label.trim().ifEmpty { return }
        dao.rename(id, name)
    }

    override suspend fun usageCount(id: Long): Int = dao.usageCount(id)

    override suspend fun setAliasArchived(id: Long, archived: Boolean) = dao.setArchived(id, archived)

    override suspend fun replaceAlias(id: Long, targetId: Long): Int {
        if (id == targetId) return 0
        val alias = dao.get(id)?.takeIf { !it.isPrimary } ?: return 0
        dao.get(targetId) ?: return 0
        return dao.replaceAlias(alias.id, targetId)
    }

    override suspend fun deleteUnusedAlias(id: Long) {
        if (dao.usageCount(id) == 0) dao.deleteAlias(id)
    }
}

internal class LocalDiaryRepository @Inject constructor(
    private val diaryDao: DiaryDao,
    private val postDao: PostDao,
    private val photos: PhotoRepository,
    private val clock: Clock,
) : DiaryRepository {
    override val diaries: Flow<List<Diary>> = diaryDao.observeDiaries().map { list -> list.map { it.asModel() } }

    override val postCounts: Flow<Map<Long, Int>> = diaryDao.observePostCounts()
        .map { counts -> counts.associate { it.diaryId to it.count } }

    override suspend fun getDiary(id: Long): Diary? = diaryDao.getDiary(id)?.asModel()

    override suspend fun saveDiary(id: Long?, name: String, iconKey: String, suggestedGroupIds: Set<Long>): Long {
        val existing = id?.let { diaryDao.getDiary(it)?.diary }
        val entity = existing?.copy(name = name, iconKey = iconKey)
            ?: DiaryEntity(name = name, iconKey = iconKey, position = diaryDao.nextPosition(), createdAt = clock.millis())
        return diaryDao.save(entity, suggestedGroupIds)
    }

    override suspend fun exclusivePostCount(diaryId: Long): Int = diaryDao.exclusivePostIds(diaryId).size

    override suspend fun deleteDiary(diaryId: Long, moveExclusiveTo: Long?) {
        if (moveExclusiveTo == null) {
            val orphans = diaryDao.exclusivePostIds(diaryId)
            if (orphans.isNotEmpty()) {
                val files = postDao.photoFileNames(orphans)
                postDao.delete(orphans)
                photos.delete(files)
            }
        }
        diaryDao.delete(diaryId, moveExclusiveTo)
    }
}

internal class LocalTagRepository @Inject constructor(private val dao: TagDao) : TagRepository {
    override val groups: Flow<List<TagGroup>> = dao.observeActiveGroups().map { list -> list.map { it.asModel() } }

    override val archived: Flow<ArchivedTags> = combine(
        dao.observeArchivedGroups(),
        dao.observeActiveGroupsWithArchivedTags(),
    ) { archivedGroups, activeGroups ->
        ArchivedTags(
            groups = archivedGroups.map { group -> group.asModel(keep = { true }) },
            tags = activeGroups.map { group -> group.asModel(keep = { it.isArchived }) },
        )
    }

    override suspend fun createGroup(name: String): Long =
        dao.insertGroup(TagGroupEntity(name = name, position = dao.nextGroupPosition()))

    override suspend fun renameGroup(id: Long, name: String) = dao.renameGroup(id, name)

    override suspend fun setGroupArchived(id: Long, archived: Boolean) = dao.setGroupArchived(id, archived)

    override suspend fun groupUsage(id: Long) = GroupUsage(
        tags = dao.groupTagCount(id),
        entries = dao.groupUsageCount(id),
        emptied = dao.groupEmptiedCount(id),
    )

    override suspend fun deleteGroup(id: Long) = dao.deleteGroup(id)

    override suspend fun saveTag(id: Long?, groupId: Long, label: String, iconKey: String): Long {
        val existing = id?.let { dao.getTag(it) }
        return if (existing == null) {
            dao.insertTag(TagEntity(groupId = groupId, label = label, iconKey = iconKey, position = dao.nextTagPosition(groupId)))
        } else {
            val moved = existing.groupId != groupId
            dao.updateTag(
                existing.copy(
                    groupId = groupId,
                    label = label,
                    iconKey = iconKey,
                    position = if (moved) dao.nextTagPosition(groupId) else existing.position,
                ),
            )
            existing.id
        }
    }

    override suspend fun setTagArchived(id: Long, archived: Boolean) =
        if (archived) dao.setTagArchived(id, true) else dao.restoreTag(id)

    override suspend fun tagUsage(id: Long) = TagUsage(entries = dao.usageCount(id), emptied = dao.emptiedCount(id))

    override suspend fun deleteTag(id: Long) = dao.deleteTag(id)

    override val coloredTagCount: Flow<Int> = dao.observeColoredTagCount().distinctUntilChanged()

    override suspend fun setGroupColor(id: Long, colorKey: String?) = dao.setGroupColor(id, colorKey)

    override suspend fun setTagColor(id: Long, colorKey: String?) = dao.setTagColor(id, colorKey)
}

internal class LocalPostRepository @Inject constructor(
    private val dao: PostDao,
    private val tagDao: TagDao,
    private val photos: PhotoRepository,
    private val clock: Clock,
    @Dispatcher(DakilioDispatchers.Default) private val default: CoroutineDispatcher,
) : PostRepository {
    override val posts: Flow<List<Post>> = combine(dao.observePosts(), tagDao.observeGroupColors()) { list, groupColors ->
        list.map { it.asModel(photos::pathOf, groupColors) }
    }.flowOn(default)

    override val samplePostCount: Flow<Int> = dao
        .observeCountInRange(DefaultData.SAMPLE_POST_IDS.first, DefaultData.SAMPLE_POST_IDS.last)
        .distinctUntilChanged()

    override suspend fun getPost(id: Long): Post? = dao.getPost(id)?.asModel(photos::pathOf, tagDao.observeGroupColors().first())

    override suspend fun savePost(draft: PostDraft): Long {
        val offset = clock.zone.rules.getOffset(draft.dateTime)
        val now = clock.millis()
        val previous = draft.id?.let { dao.getPost(it) }
        val entity = PostEntity(
            id = draft.id ?: 0L,
            timestamp = draft.dateTime.toInstant(offset).toEpochMilli(),
            utcOffsetMinutes = offset.totalSeconds / 60,
            localDate = draft.dateTime.toLocalDate().toEpochDay(),
            moodId = draft.moodId,
            title = draft.title?.trim()?.takeIf { it.isNotEmpty() },
            note = draft.note?.trim()?.takeIf { it.isNotEmpty() },
            createdAt = previous?.post?.createdAt ?: now,
            updatedAt = now,
        )
        val id = dao.save(entity, draft.tagIds, draft.diaryIds, draft.photoFileNames)
        previous?.photos?.map { it.fileName }?.minus(draft.photoFileNames.toSet())?.let { photos.delete(it) }
        return id
    }

    override suspend fun deletePost(id: Long) {
        val files = dao.photoFileNames(listOf(id))
        dao.delete(listOf(id))
        photos.delete(files)
    }

    override suspend fun removeSamplePosts() {
        val ids = dao.idsInRange(DefaultData.SAMPLE_POST_IDS.first, DefaultData.SAMPLE_POST_IDS.last)
        if (ids.isEmpty()) return
        val files = dao.photoFileNames(ids)
        dao.delete(ids)
        photos.delete(files)
    }
}

internal class LocalSettingsRepository @Inject constructor(
    private val dataSource: SettingsDataSource,
) : SettingsRepository {
    override val settings: Flow<UserSettings> = dataSource.settings

    override suspend fun setThemeMode(mode: ThemeMode) = dataSource.setThemeMode(mode)

    override suspend fun setLastEntryDiaryIds(ids: Set<Long>) = dataSource.setLastEntryDiaryIds(ids)

    override suspend fun setSampleHintDismissed(dismissed: Boolean) = dataSource.setSampleHintDismissed(dismissed)

    override suspend fun setTagStyle(style: TagStyle) = dataSource.setTagStyle(style)

    override suspend fun setShowHints(show: Boolean) = dataSource.setShowHints(show)
}
