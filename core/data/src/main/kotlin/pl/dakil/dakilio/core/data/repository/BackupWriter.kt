package pl.dakil.dakilio.core.data.repository

import android.content.Context
import android.os.SystemClock
import android.util.Log
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import java.io.OutputStream
import java.time.Clock
import java.time.ZoneId
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.first
import pl.dakil.dakilio.core.backup.BackupDiary
import pl.dakil.dakilio.core.backup.BackupEntry
import pl.dakil.dakilio.core.backup.BackupHabit
import pl.dakil.dakilio.core.backup.BackupKeyword
import pl.dakil.dakilio.core.backup.BackupMood
import pl.dakil.dakilio.core.backup.BackupSettings
import pl.dakil.dakilio.core.backup.BackupTag
import pl.dakil.dakilio.core.backup.BackupTagGroup
import pl.dakil.dakilio.core.backup.BackupTime
import pl.dakil.dakilio.core.backup.DakilioBackup
import pl.dakil.dakilio.core.backup.EncryptingOutputStream
import pl.dakil.dakilio.core.backup.toBackup
import pl.dakil.dakilio.core.backup.writeBackup
import pl.dakil.dakilio.core.database.dao.ContentSnapshot
import pl.dakil.dakilio.core.database.dao.ImportDao
import pl.dakil.dakilio.core.model.UserSettings

/**
 * A backup ready to write: [backup] plus the files of the photos it lists ([photoFiles]: file name →
 * file). [missingPhotos] were listed by entries but are gone from storage, so they're left out.
 */
internal class PreparedBackup(
    val backup: DakilioBackup,
    val photoFiles: Map<String, File>,
    val missingPhotos: Int,
) {
    /** Photo references in the entries (a photo listed twice counts twice, like the progress). */
    val photoCount: Int get() = backup.entries.sumOf { it.photos.size }

    /** File name → size, for `AutoBackup.fingerprint`. */
    fun photoSizes(): Map<String, Long> = photoFiles.mapValues { it.value.length() }
}

/** Builds and writes `.dakilio` backups, for the manual export and automatic backups alike. */
@Singleton
internal class BackupWriter @Inject constructor(
    @ApplicationContext private val context: Context,
    private val importDao: ImportDao,
    private val photos: PhotoRepository,
    private val settings: SettingsRepository,
    private val clock: Clock,
) {
    /** Reads everything into a backup. Call on an IO thread. */
    suspend fun prepare(): PreparedBackup {
        val snapshot = importDao.snapshot()
        val all = snapshot.photos.map { it.fileName }.distinct().associateWith { File(photos.pathOf(it)) }
        val available = all.filterValues { it.isFile }
        val backup = toBackup(snapshot, settings.settings.first(), available.keys)
        return PreparedBackup(backup, available, missingPhotos = snapshot.photos.count { it.fileName !in available })
    }

    /**
     * Writes [prepared] to [raw], which it closes; [password] (not cleared here) encrypts it.
     * [onPhoto] runs after each photo and may throw to stop.
     */
    fun write(raw: OutputStream, prepared: PreparedBackup, password: CharArray?, onPhoto: () -> Unit) {
        val started = SystemClock.elapsedRealtime()
        val sink = if (password != null) EncryptingOutputStream(raw.buffered(), password) else raw.buffered()
        if (password != null) Log.i(TAG, "Key derived in ${SystemClock.elapsedRealtime() - started} ms")
        writeBackup(
            sink,
            prepared.backup,
            readPhoto = { path -> prepared.photoFiles.getValue(path.removePrefix(DakilioBackup.PHOTO_DIR)).readBytes() },
            onPhoto = onPhoto,
        )
    }

    private fun toBackup(snapshot: ContentSnapshot, userSettings: UserSettings, photoNames: Set<String>): DakilioBackup {
        val now = clock.millis()
        val offsetMinutes = ZoneId.systemDefault().rules.getOffset(clock.instant()).totalSeconds / 60
        val suggestions = snapshot.suggestedGroups.groupBy({ it.diaryId }, { it.groupId })
        val tagsByGroup = snapshot.tags.groupBy { it.groupId }
        val keywordsByTag = snapshot.keywords.groupBy { it.tagId }
        val reminders = snapshot.reminders.mapNotNull { entity -> entity.asModel()?.let { entity to it.toBackup() } }
        val remindersByDiary = reminders.filter { it.first.diaryId != null }.groupBy({ it.first.diaryId }, { it.second })
        val remindersByHabit = reminders.filter { it.first.habitId != null }.groupBy({ it.first.habitId }, { it.second })
        val tagsByPost = snapshot.postTags.groupBy({ it.postId }, { it.tagId })
        val photosByPost = snapshot.photos.groupBy({ it.postId }, { it.fileName })
        return DakilioBackup(
            format = DakilioBackup.FORMAT,
            version = DakilioBackup.FORMAT_VERSION,
            exportedAt = BackupTime.format(now, offsetMinutes),
            appVersion = runCatching { context.packageManager.getPackageInfo(context.packageName, 0).versionName }.getOrNull(),
            settings = BackupSettings(
                theme = userSettings.themeMode.name,
                tagStyle = userSettings.tagStyle.name,
                showHints = userSettings.showHints,
                timelineTextScale = userSettings.timelineTextScale,
                editorTextScale = userSettings.editorTextScale,
                previewLength = userSettings.previewLength.name,
                compactTags = userSettings.compactTags,
                tagLimit = userSettings.tagLimit.key,
                monthNavigator = userSettings.monthNavigator,
                monthNavigatorInAppBar = userSettings.monthNavigatorInAppBar,
                closeAfterSave = userSettings.closeAfterSave,
                keywordAutoAdd = userSettings.keywordAutoAdd,
                lastDiary = userSettings.lastEntryDiaryId,
                timelineDiary = userSettings.timelineDiaryId,
                weekStart = userSettings.weekStart.name,
                photoSize = userSettings.photoSize.name,
            ),
            moods = snapshot.moods.map { BackupMood(it.id, it.level, it.label, it.isPrimary, it.position, it.isArchived, it.colorKey) },
            diaries = snapshot.diaries.map {
                BackupDiary(
                    id = it.id,
                    name = it.name,
                    icon = it.iconKey,
                    position = it.position,
                    createdAt = BackupTime.formatInstant(it.createdAt),
                    suggestedGroups = suggestions[it.id].orEmpty().sorted(),
                    reminders = remindersByDiary[it.id].orEmpty(),
                )
            },
            tagGroups = snapshot.groups.map { group ->
                BackupTagGroup(
                    id = group.id,
                    name = group.name,
                    position = group.position,
                    archived = group.isArchived,
                    color = group.colorKey,
                    tags = tagsByGroup[group.id].orEmpty().map { tag ->
                        BackupTag(
                            tag.id, tag.label, tag.iconKey, tag.position, tag.isArchived, tag.colorKey,
                            keywordsByTag[tag.id].orEmpty().map { BackupKeyword(it.word, it.approximate) },
                        )
                    },
                )
            },
            habits = snapshot.habits.map {
                BackupHabit(
                    tag = it.tagId,
                    daysPerWeek = it.daysPerWeek,
                    position = it.position,
                    createdAt = BackupTime.formatInstant(it.createdAt),
                    reminders = remindersByHabit[it.id].orEmpty(),
                )
            },
            entries = snapshot.posts.map { post ->
                BackupEntry(
                    time = BackupTime.format(post.timestamp, post.utcOffsetMinutes),
                    diary = post.diaryId,
                    mood = post.moodId,
                    title = post.title,
                    note = post.note,
                    tags = tagsByPost[post.id].orEmpty().sorted(),
                    photos = photosByPost[post.id].orEmpty().filter { it in photoNames }.map { DakilioBackup.PHOTO_DIR + it },
                    createdAt = BackupTime.formatInstant(post.createdAt),
                    updatedAt = BackupTime.formatInstant(post.updatedAt),
                )
            },
        )
    }

    private companion object {
        const val TAG = "DakilioBackup"
    }
}
