package pl.dakil.dakilio.core.data.repository

import android.content.Context
import android.net.Uri
import android.os.SystemClock
import android.provider.DocumentsContract
import android.util.Log
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import java.io.IOException
import java.time.Clock
import java.util.UUID
import javax.inject.Inject
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.job
import kotlinx.coroutines.withContext
import pl.dakil.dakilio.core.backup.BACKUP_HEAD_BYTES
import pl.dakil.dakilio.core.backup.BackupCrypto
import pl.dakil.dakilio.core.backup.BackupError
import pl.dakil.dakilio.core.backup.BackupException
import pl.dakil.dakilio.core.backup.BackupKind
import pl.dakil.dakilio.core.backup.BackupTime
import pl.dakil.dakilio.core.backup.DakilioArchive
import pl.dakil.dakilio.core.backup.DakilioBackup
import pl.dakil.dakilio.core.backup.RestoreMoodRef
import pl.dakil.dakilio.core.backup.RestorePlan
import pl.dakil.dakilio.core.backup.detectBackupKind
import pl.dakil.dakilio.core.backup.planRestore
import pl.dakil.dakilio.core.backup.RestoreReminder
import pl.dakil.dakilio.core.common.DakilioDispatchers
import pl.dakil.dakilio.core.common.Dispatcher
import pl.dakil.dakilio.core.database.DefaultData
import pl.dakil.dakilio.core.database.dao.ImportDao
import pl.dakil.dakilio.core.database.dao.PostDao
import pl.dakil.dakilio.core.database.dao.ReplaceData
import pl.dakil.dakilio.core.database.dao.ReplaceDiary
import pl.dakil.dakilio.core.database.dao.ReplaceGroup
import pl.dakil.dakilio.core.database.dao.ReplaceHabit
import pl.dakil.dakilio.core.database.dao.ReplaceKeyword
import pl.dakil.dakilio.core.database.dao.ReplaceReminder
import pl.dakil.dakilio.core.database.dao.ReplacePost
import pl.dakil.dakilio.core.database.entity.DiaryEntity
import pl.dakil.dakilio.core.database.entity.HabitEntity
import pl.dakil.dakilio.core.database.entity.MoodEntity
import pl.dakil.dakilio.core.database.entity.PostEntity
import pl.dakil.dakilio.core.database.entity.ReminderEntity
import pl.dakil.dakilio.core.database.entity.TagEntity
import pl.dakil.dakilio.core.database.entity.TagGroupEntity
import pl.dakil.dakilio.core.database.entity.TagKeywordEntity
import pl.dakil.dakilio.core.model.MOOD_LEVELS
import pl.dakil.dakilio.core.model.ReminderDays
import pl.dakil.dakilio.core.model.minuteOfDay

internal class LocalBackupRepository @Inject constructor(
    @ApplicationContext private val context: Context,
    private val importDao: ImportDao,
    private val postDao: PostDao,
    private val photos: PhotoRepository,
    private val settings: SettingsRepository,
    private val clock: Clock,
    private val writer: BackupWriter,
    @Dispatcher(DakilioDispatchers.IO) private val io: CoroutineDispatcher,
) : BackupRepository {
    private var source: File? = null
    private var decrypted: File? = null
    private var encrypted = false
    private var archive: DakilioArchive? = null
    private var plan: RestorePlan? = null

    // Export.

    override suspend fun export(uri: Uri, password: CharArray?, onProgress: (done: Int, total: Int) -> Unit): BackupExportResult = withContext(io) {
        val job = coroutineContext.job
        val prepared = writer.prepare()
        var done = 0
        onProgress(0, prepared.photoCount)
        try {
            val raw = context.contentResolver.openOutputStream(uri, "wt") ?: throw IOException("Can't write $uri")
            writer.write(raw, prepared, password) {
                job.ensureActive()
                onProgress(++done, prepared.photoCount)
            }
        } catch (e: Throwable) {
            // Don't leave a half-written file behind that looks like a backup.
            withContext(NonCancellable) { runCatching { DocumentsContract.deleteDocument(context.contentResolver, uri) } }
            throw e
        }
        BackupExportResult(entries = prepared.backup.entries.size, photos = prepared.photoCount, missingPhotos = prepared.missingPhotos)
    }

    // Restore.

    override suspend fun open(uri: Uri): BackupOpenResult = withContext(io) {
        close()
        val file = File(context.cacheDir, CACHE_DIR).apply { mkdirs() }.resolve("${UUID.randomUUID()}.dakilio")
        source = file
        val input = context.contentResolver.openInputStream(uri) ?: throw BackupException(BackupError.NotABackup)
        input.use { from ->
            file.outputStream().use { out ->
                val buffer = ByteArray(256 * 1024)
                var total = 0L
                while (true) {
                    ensureActive()
                    val read = from.read(buffer)
                    if (read < 0) break
                    total += read
                    if (total > MAX_FILE_BYTES) throw BackupException(BackupError.TooLarge)
                    out.write(buffer, 0, read)
                }
            }
        }
        val head = file.inputStream().use { input ->
            val bytes = ByteArray(BACKUP_HEAD_BYTES)
            bytes.copyOf(input.read(bytes).coerceAtLeast(0))
        }
        when (detectBackupKind(head)) {
            BackupKind.DakilioEncrypted -> {
                encrypted = true
                BackupOpenResult.NeedsPassword
            }
            BackupKind.DakilioPlain -> BackupOpenResult.Ready(load(file))
            BackupKind.Other -> throw BackupException(BackupError.NotABackup)
        }
    }

    override suspend fun unlock(password: CharArray): BackupPreview = withContext(io) {
        val file = checkNotNull(source) { "open() first" }
        val target = file.resolveSibling("${UUID.randomUUID()}.zip")
        val started = SystemClock.elapsedRealtime()
        try {
            file.inputStream().buffered().use { input -> target.outputStream().buffered().use { BackupCrypto.decrypt(input, it, password) } }
        } catch (e: Throwable) {
            target.delete()
            throw e
        }
        Log.i(TAG, "Decrypted in ${SystemClock.elapsedRealtime() - started} ms")
        decrypted = target
        load(target)
    }

    private suspend fun load(file: File): BackupPreview {
        val opened = try {
            DakilioArchive(file)
        } catch (e: BackupException) {
            Log.w(TAG, "Can't read the backup: ${e.reason}", e.cause ?: e)
            throw e
        }.also { archive = it }
        val now = clock.millis()
        val planned = planRestore(opened.backup, DefaultData.defaultDiary(context, now).name, now).also { plan = it }
        val current = importDao.contentCounts()
        return BackupPreview(
            stats = planned.stats,
            exportedAt = BackupTime.parseMillis(opened.backup.exportedAt),
            encrypted = encrypted,
            missingPhotos = planned.entries.sumOf { entry -> entry.photos.count { !opened.hasPhoto(it) } },
            current = CurrentContent(current.posts, current.photos, current.diaries, current.tags),
        )
    }

    override suspend fun restore(onProgress: (done: Int, total: Int) -> Unit): BackupRestoreResult = withContext(io) {
        val archive = checkNotNull(archive) { "open() first" }
        val plan = checkNotNull(plan)
        val total = plan.entries.sumOf { it.photos.size }
        val newFiles = mutableListOf<String>()
        var done = 0
        var failed = 0
        var committed = false
        try {
            onProgress(0, total)
            // Photos first, one at a time; the database is only touched once they're all in place.
            // They're re-encoded like any other photo: the file is untrusted. The backup's own photo
            // size applies, so a restore doesn't shrink photos the backed-up app kept larger.
            val posts = plan.entries.map { entry ->
                val names = entry.photos.mapNotNull { path ->
                    ensureActive()
                    val name = archive.readPhoto(path)?.let { photos.importBytes(it, size = plan.settings.photoSize) }
                    if (name == null) failed++ else newFiles += name
                    onProgress(++done, total)
                    name
                }
                ReplacePost(
                    post = PostEntity(
                        timestamp = entry.timestamp,
                        utcOffsetMinutes = entry.utcOffsetMinutes,
                        localDate = entry.localDate,
                        moodId = null,
                        diaryId = 0,
                        title = entry.title,
                        note = entry.note,
                        createdAt = entry.createdAt,
                        updatedAt = entry.updatedAt,
                    ),
                    moodLevel = when (val mood = entry.mood) {
                        null -> null
                        is RestoreMoodRef.Primary -> mood.level
                        is RestoreMoodRef.Alias -> plan.aliases[mood.index].level
                    },
                    aliasIndex = (entry.mood as? RestoreMoodRef.Alias)?.index,
                    diaryIndex = entry.diaryIndex,
                    tags = entry.tags.map { it.groupIndex to it.tagIndex },
                    photoFileNames = names,
                )
            }
            ensureActive()
            val defaultDiary = DefaultData.defaultDiary(context, clock.millis())
            val oldFiles = postDao.allPhotoFileNames()
            val diaryIds = importDao.replaceAll(
                ReplaceData(
                    primaryLabels = (1..MOOD_LEVELS).associateWith { plan.primaries[it]?.name ?: DefaultData.primaryMoodLabel(context, it) },
                    primaryColors = (1..MOOD_LEVELS).associateWith { plan.primaries[it]?.colorKey },
                    diaries = plan.diaries.map {
                        ReplaceDiary(
                            DiaryEntity(
                                name = it.name,
                                iconKey = it.iconKey.ifBlank { defaultDiary.iconKey },
                                position = it.position,
                                createdAt = it.createdAt,
                            ),
                            it.suggestedGroups,
                        )
                    },
                    aliases = plan.aliases.map {
                        MoodEntity(level = it.level, label = it.name, isPrimary = false, position = it.position, isArchived = it.archived, colorKey = it.colorKey)
                    },
                    groups = plan.groups.map { group ->
                        ReplaceGroup(
                            TagGroupEntity(name = group.name, position = group.position, isArchived = group.archived, colorKey = group.colorKey),
                            group.tags.map {
                                TagEntity(groupId = 0, label = it.name, iconKey = it.iconKey, position = it.position, isArchived = it.archived, colorKey = it.colorKey)
                            },
                        )
                    },
                    posts = posts,
                    habits = plan.habits.map {
                        ReplaceHabit(
                            HabitEntity(
                                tagId = 0,
                                daysPerWeek = it.daysPerWeek,
                                position = it.position,
                                createdAt = it.createdAt,
                            ),
                            it.tag.groupIndex,
                            it.tag.tagIndex,
                        )
                    },
                    reminders = plan.diaries.flatMapIndexed { index, diary -> diary.reminders.map { it.toReplace(clock.millis(), diaryIndex = index) } } +
                        plan.habits.flatMapIndexed { index, habit -> habit.reminders.map { it.toReplace(clock.millis(), habitIndex = index) } },
                    keywords = plan.groups.flatMapIndexed { groupIndex, group ->
                        group.tags.flatMapIndexed { tagIndex, tag ->
                            tag.keywords.mapIndexed { position, keyword ->
                                ReplaceKeyword(TagKeywordEntity(tagId = 0, word = keyword.word, approximate = keyword.approximate, position = position), groupIndex, tagIndex)
                            }
                        }
                    },
                ),
            )
            committed = true
            // Committed: from here on the old content is gone, so finish the cleanup regardless.
            // Best effort: a failure here must not report the (successful) restore as failed.
            withContext(NonCancellable) {
                runCatching { photos.delete(oldFiles.toSet() - newFiles.toSet()) }
                runCatching { applySettings(plan, diaryIds) }.onFailure { Log.w(TAG, "Restoring settings failed", it) }
            }
            BackupRestoreResult(entries = posts.size, photos = newFiles.size, photosFailed = failed)
        } catch (e: Throwable) {
            // Before the commit nothing changed, so drop the copies; after it they're the user's photos.
            if (!committed) withContext(NonCancellable) { photos.delete(newFiles) }
            throw e
        }
    }

    private suspend fun applySettings(plan: RestorePlan, diaryIds: List<Long>) {
        val s = plan.settings
        s.themeMode?.let { settings.setThemeMode(it) }
        s.tagStyle?.let { settings.setTagStyle(it) }
        s.showHints?.let { settings.setShowHints(it) }
        s.timelineTextScale?.let { settings.setTimelineTextScale(it) }
        s.editorTextScale?.let { settings.setEditorTextScale(it) }
        s.previewLength?.let { settings.setPreviewLength(it) }
        s.compactTags?.let { settings.setCompactTags(it) }
        s.tagLimit?.let { settings.setTagLimit(it) }
        s.monthNavigator?.let { settings.setMonthNavigator(it) }
        s.monthNavigatorInAppBar?.let { settings.setMonthNavigatorInAppBar(it) }
        s.closeAfterSave?.let { settings.setCloseAfterSave(it) }
        s.keywordAutoAdd?.let { settings.setKeywordAutoAdd(it) }
        s.weekStart?.let { settings.setWeekStart(it) }
        s.photoSize?.let { settings.setPhotoSize(it) }
        settings.setLastEntryDiaryId(diaryIds[s.lastDiaryIndex ?: 0])
        settings.setTimelineDiaryId(s.timelineDiaryIndex?.let(diaryIds::getOrNull))
        settings.setSampleHintDismissed(true)
    }

    override fun close() {
        runCatching { archive?.close() }
        archive = null
        plan = null
        encrypted = false
        source?.delete()
        source = null
        decrypted?.delete()
        decrypted = null
    }

    private companion object {
        const val TAG = "DakilioBackup"
        const val CACHE_DIR = "backup"
        const val MAX_FILE_BYTES = 2L * 1024 * 1024 * 1024
    }
}

private fun RestoreReminder.toReplace(createdAt: Long, diaryIndex: Int? = null, habitIndex: Int? = null) = ReplaceReminder(
    ReminderEntity(
        minute = time.minuteOfDay(),
        days = ReminderDays.toMask(days),
        enabled = enabled,
        createdAt = createdAt,
    ),
    diaryIndex = diaryIndex,
    habitIndex = habitIndex,
)
