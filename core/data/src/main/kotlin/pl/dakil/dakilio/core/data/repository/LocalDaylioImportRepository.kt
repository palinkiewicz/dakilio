package pl.dakil.dakilio.core.data.repository

import android.content.Context
import android.os.Build
import android.util.Log
import dalvik.system.ZipPathValidator
import android.net.Uri
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import java.time.Clock
import java.util.UUID
import java.util.zip.ZipException
import javax.inject.Inject
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import pl.dakil.dakilio.core.common.DakilioDispatchers
import pl.dakil.dakilio.core.common.Dispatcher
import pl.dakil.dakilio.core.database.DefaultData
import pl.dakil.dakilio.core.database.dao.ImportDao
import pl.dakil.dakilio.core.database.dao.PostDao
import pl.dakil.dakilio.core.database.dao.ReplaceData
import pl.dakil.dakilio.core.database.dao.ReplaceGroup
import pl.dakil.dakilio.core.database.dao.ReplacePost
import pl.dakil.dakilio.core.database.entity.MoodEntity
import pl.dakil.dakilio.core.database.entity.PostEntity
import pl.dakil.dakilio.core.database.entity.TagEntity
import pl.dakil.dakilio.core.database.entity.TagGroupEntity
import pl.dakil.dakilio.core.daylio.DaylioArchive
import pl.dakil.dakilio.core.daylio.DaylioError
import pl.dakil.dakilio.core.daylio.DaylioImportException
import pl.dakil.dakilio.core.daylio.ImportPlan
import pl.dakil.dakilio.core.daylio.MoodRef
import pl.dakil.dakilio.core.daylio.planImport
import pl.dakil.dakilio.core.model.MOOD_LEVELS

internal class LocalDaylioImportRepository @Inject constructor(
    @ApplicationContext private val context: Context,
    private val importDao: ImportDao,
    private val postDao: PostDao,
    private val photos: PhotoRepository,
    private val settings: SettingsRepository,
    private val clock: Clock,
    @Dispatcher(DakilioDispatchers.IO) private val io: CoroutineDispatcher,
) : DaylioImportRepository {
    private var copy: File? = null
    private var archive: DaylioArchive? = null
    private var plan: ImportPlan? = null

    override suspend fun open(uri: Uri): DaylioPreview = withContext(io) {
        close()
        val file = File(context.cacheDir, "daylio").apply { mkdirs() }.resolve("${UUID.randomUUID()}.daylio")
        copy = file
        val input = context.contentResolver.openInputStream(uri) ?: throw DaylioImportException(DaylioError.NotAZip)
        input.use { source ->
            file.outputStream().use { out ->
                val buffer = ByteArray(256 * 1024)
                var total = 0L
                while (true) {
                    ensureActive()
                    val read = source.read(buffer)
                    if (read < 0) break
                    total += read
                    if (total > MAX_FILE_BYTES) throw DaylioImportException(DaylioError.TooLarge)
                    out.write(buffer, 0, read)
                }
            }
        }
        allowRootedZipEntries()
        val opened = try {
            DaylioArchive(file)
        } catch (e: DaylioImportException) {
            Log.w(TAG, "Can't read the Daylio backup: ${e.reason}", e.cause ?: e)
            throw e
        }.also { archive = it }
        val planned = planImport(opened.backup, DefaultData.importFallbackGroupName(context)).also { plan = it }
        val current = importDao.contentCounts()
        DaylioPreview(
            stats = planned.stats,
            untestedVersion = opened.isUntestedVersion,
            missingPhotos = planned.entries.sumOf { entry -> entry.photos.count { !opened.hasPhoto(it.checksum) } },
            current = CurrentContent(current.posts, current.photos, current.diaries, current.tags),
        )
    }

    override suspend fun import(onProgress: (done: Int, total: Int) -> Unit): DaylioImportResult = withContext(io) {
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
            val posts = plan.entries.map { entry ->
                val names = entry.photos.mapNotNull { photo ->
                    ensureActive()
                    val name = archive.readPhoto(photo.checksum)?.let { photos.importBytes(it, photo.rotationDegrees) }
                    if (name == null) failed++ else newFiles += name
                    onProgress(++done, total)
                    name
                }
                val now = clock.millis()
                ReplacePost(
                    post = PostEntity(
                        timestamp = entry.timestamp,
                        utcOffsetMinutes = entry.utcOffsetMinutes,
                        localDate = entry.localDate,
                        moodId = null,
                        diaryId = 0,
                        title = entry.title,
                        note = entry.note,
                        createdAt = now,
                        updatedAt = now,
                    ),
                    moodLevel = when (val mood = entry.mood) {
                        is MoodRef.Primary -> mood.level
                        is MoodRef.Alias -> plan.aliases[mood.index].level
                    },
                    aliasIndex = (entry.mood as? MoodRef.Alias)?.index,
                    tags = entry.tags.map { it.groupIndex to it.tagIndex },
                    photoFileNames = names,
                )
            }
            ensureActive()
            val oldFiles = postDao.allPhotoFileNames()
            val diaryId = importDao.replaceAll(
                ReplaceData(
                    primaryLabels = (1..MOOD_LEVELS).associateWith { plan.primaryNames[it] ?: DefaultData.primaryMoodLabel(context, it) },
                    diary = DefaultData.defaultDiary(context, clock.millis()),
                    aliases = plan.aliases.map { MoodEntity(level = it.level, label = it.label, isPrimary = false, position = it.position, isArchived = it.archived) },
                    groups = plan.groups.map { group ->
                        ReplaceGroup(
                            TagGroupEntity(name = group.name, position = group.position, isArchived = group.archived),
                            group.tags.map { TagEntity(groupId = 0, label = it.label, iconKey = it.iconKey, position = it.position, isArchived = it.archived) },
                        )
                    },
                    posts = posts,
                ),
            )
            committed = true
            // Committed: from here on the old content is gone, so finish the cleanup regardless.
            // Best effort: a failure here must not report the (successful) import as failed.
            withContext(NonCancellable) {
                runCatching { photos.delete(oldFiles.toSet() - newFiles.toSet()) }
                runCatching {
                    settings.setLastEntryDiaryId(diaryId)
                    settings.setSampleHintDismissed(true)
                }
            }
            DaylioImportResult(entries = posts.size, photos = newFiles.size, photosFailed = failed)
        } catch (e: Throwable) {
            // Before the commit nothing changed, so drop the copies; after it they're the user's photos.
            if (!committed) withContext(NonCancellable) { photos.delete(newFiles) }
            throw e
        }
    }

    override fun close() {
        runCatching { archive?.close() }
        archive = null
        plan = null
        copy?.delete()
        copy = null
    }

    private companion object {
        const val TAG = "DaylioImport"
        const val MAX_FILE_BYTES = 2L * 1024 * 1024 * 1024
    }
}

/**
 * From Android 14, apps targeting SDK 34+ get a process-wide zip-path validator that makes
 * `ZipFile` reject any member name starting with `/`, and Daylio names every asset `/assets/...`.
 * This swaps in a validator that still rejects `..` path traversal but allows the leading slash.
 * It's safe for the whole app: nothing extracts ZIP members by name (the importer only looks them
 * up), and there's no other ZIP reading in the app.
 */
private fun allowRootedZipEntries() {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.UPSIDE_DOWN_CAKE) return
    ZipPathValidator.setCallback(
        object : ZipPathValidator.Callback {
            override fun onZipEntryAccess(path: String) {
                if (path.split('/', '\\').any { it == ".." }) throw ZipException("Invalid zip entry path: $path")
            }
        },
    )
}
