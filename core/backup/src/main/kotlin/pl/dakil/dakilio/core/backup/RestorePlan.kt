package pl.dakil.dakilio.core.backup

import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalTime
import java.util.Locale
import pl.dakil.dakilio.core.model.KeywordDraft
import pl.dakil.dakilio.core.model.Keywords
import pl.dakil.dakilio.core.model.MOOD_LEVELS
import pl.dakil.dakilio.core.model.PhotoSize
import pl.dakil.dakilio.core.model.PreviewLength
import pl.dakil.dakilio.core.model.Reminder
import pl.dakil.dakilio.core.model.ReminderDays
import pl.dakil.dakilio.core.model.TagLimit
import pl.dakil.dakilio.core.model.TagStyle
import pl.dakil.dakilio.core.model.TextScale
import pl.dakil.dakilio.core.model.ThemeMode
import pl.dakil.dakilio.core.model.WeekStart

/**
 * What a backup turns into. A restore replaces everything, so the plan doesn't look at existing
 * data: the file's ids become list indices, which the repository resolves to fresh database ids.
 */
data class RestorePlan(
    /** Level → the primary's name and color; a level missing here gets the default name. */
    val primaries: Map<Int, RestorePrimary>,
    val aliases: List<RestoreAlias>,
    /** Never empty. */
    val diaries: List<RestoreDiary>,
    val groups: List<RestoreGroup>,
    val habits: List<RestoreHabit>,
    val entries: List<RestoreEntry>,
    val settings: RestoreSettings,
    val stats: RestoreStats,
)

data class RestorePrimary(val name: String, val colorKey: String?)

data class RestoreAlias(val level: Int, val name: String, val position: Int, val archived: Boolean, val colorKey: String?)

data class RestoreDiary(
    val name: String,
    val iconKey: String,
    val position: Int,
    val createdAt: Long,
    /** Indices into [RestorePlan.groups]. */
    val suggestedGroups: List<Int>,
    val reminders: List<RestoreReminder> = emptyList(),
)

/** A reminder of a diary or habit. */
data class RestoreReminder(
    val time: LocalTime = Reminder.DEFAULT_TIME,
    val days: Set<DayOfWeek> = ReminderDays.EVERY_DAY,
    val enabled: Boolean = true,
)

data class RestoreGroup(val name: String, val position: Int, val archived: Boolean, val colorKey: String?, val tags: List<RestoreTag>)

data class RestoreTag(
    val name: String,
    val iconKey: String,
    val position: Int,
    val archived: Boolean,
    val colorKey: String?,
    val keywords: List<KeywordDraft> = emptyList(),
)

/** Indices into [RestorePlan.groups] and that group's tags. */
data class RestoreTagRef(val groupIndex: Int, val tagIndex: Int)

data class RestoreHabit(
    val tag: RestoreTagRef,
    val daysPerWeek: Int,
    val position: Int,
    val createdAt: Long,
    val reminders: List<RestoreReminder> = emptyList(),
)

sealed interface RestoreMoodRef {
    data class Primary(val level: Int) : RestoreMoodRef

    /** An index into [RestorePlan.aliases]. */
    data class Alias(val index: Int) : RestoreMoodRef
}

data class RestoreEntry(
    val timestamp: Long,
    val utcOffsetMinutes: Int,
    /** Epoch day of the entry's wall-clock date. */
    val localDate: Long,
    /** An index into [RestorePlan.diaries]. */
    val diaryIndex: Int,
    val mood: RestoreMoodRef?,
    val title: String?,
    val note: String?,
    val tags: List<RestoreTagRef>,
    /** Archive paths (`photos/…`). */
    val photos: List<String>,
    val createdAt: Long,
    val updatedAt: Long,
)

/** Settings to apply; null leaves the device's value alone. */
data class RestoreSettings(
    val themeMode: ThemeMode? = null,
    val tagStyle: TagStyle? = null,
    val showHints: Boolean? = null,
    val timelineTextScale: Float? = null,
    val editorTextScale: Float? = null,
    val previewLength: PreviewLength? = null,
    val compactTags: Boolean? = null,
    val tagLimit: TagLimit? = null,
    val monthNavigator: Boolean? = null,
    val monthNavigatorInAppBar: Boolean? = null,
    val closeAfterSave: Boolean? = null,
    val keywordAutoAdd: Boolean? = null,
    val weekStart: WeekStart? = null,
    val photoSize: PhotoSize? = null,
    /** An index into [RestorePlan.diaries]. */
    val lastDiaryIndex: Int? = null,
    /** An index into [RestorePlan.diaries]; `null` (also for an unknown diary) is "All diaries". */
    val timelineDiaryIndex: Int? = null,
)

data class RestoreStats(
    val entries: Int,
    val firstDate: LocalDate?,
    val lastDate: LocalDate?,
    val photos: Int,
    val diaries: Int,
    val groups: Int,
    val tags: Int,
    val aliases: Int,
    val habits: Int,
    /** Moods, tags, diaries or photos an entry or habit points at that the file doesn't define. */
    val missingReferences: Int,
    /** Entries dropped because their time can't be read. */
    val entriesSkipped: Int,
)

private const val MAX_OFFSET_MIN = 18 * 60

/**
 * Maps a parsed backup onto Dakilio's model, defensively: the file may have been edited by hand.
 * Dangling references are dropped and counted, an entry whose diary is missing goes into the first
 * one, and a backup without diaries gets one named [defaultDiaryName], created at [now].
 */
fun planRestore(backup: DakilioBackup, defaultDiaryName: String, now: Long): RestorePlan {
    var missing = 0
    var skipped = 0

    // Moods: the first primary of each level wins; extra primaries and the other moods are aliases.
    val primaries = mutableMapOf<Int, RestorePrimary>()
    val moodRefs = mutableMapOf<Long, RestoreMoodRef>()
    val aliasMoods = mutableListOf<BackupMood>()
    backup.moods.filter { it.level in 1..MOOD_LEVELS }.distinctBy { it.id }.forEach { mood ->
        val name = mood.name.trim()
        when {
            mood.primary && mood.level !in primaries && name.isNotEmpty() -> {
                primaries[mood.level] = RestorePrimary(name, mood.color)
                moodRefs[mood.id] = RestoreMoodRef.Primary(mood.level)
            }
            name.isNotEmpty() -> aliasMoods += mood
            // Nameless moods fall back to their level's primary.
            else -> moodRefs[mood.id] = RestoreMoodRef.Primary(mood.level)
        }
    }
    val aliases = mutableListOf<RestoreAlias>()
    aliasMoods.sortedWith(compareBy({ it.level }, { it.position })).groupBy { it.level }.forEach { (level, moods) ->
        moods.forEachIndexed { position, mood ->
            moodRefs[mood.id] = RestoreMoodRef.Alias(aliases.size)
            aliases += RestoreAlias(level, mood.name.trim(), position, mood.archived, mood.color)
        }
    }

    // Tag groups and their tags, in their saved order.
    val tagRefs = mutableMapOf<Long, RestoreTagRef>()
    val groupIndexById = mutableMapOf<Long, Int>()
    val groups = backup.tagGroups.distinctBy { it.id }.sortedBy { it.position }.mapIndexed { groupIndex, group ->
        groupIndexById[group.id] = groupIndex
        val tags = group.tags.filter { it.name.isNotBlank() && it.id !in tagRefs }.distinctBy { it.id }.sortedBy { it.position }
        RestoreGroup(
            name = group.name.trim(),
            position = groupIndex,
            archived = group.archived,
            colorKey = group.color,
            tags = tags.mapIndexed { tagIndex, tag ->
                tagRefs[tag.id] = RestoreTagRef(groupIndex, tagIndex)
                val keywords = tag.keywords
                    .map { KeywordDraft(it.word.trim(), it.approximate) }
                    .filter { Keywords.isUsable(it.word) }
                    .distinctBy { Keywords.key(it.word) }
                RestoreTag(tag.name.trim(), tag.icon, tagIndex, tag.archived, tag.color, keywords)
            },
        )
    }

    // Diaries.
    val sourceDiaries = backup.diaries.distinctBy { it.id }.sortedBy { it.position }
    val diaryIndexById = sourceDiaries.withIndex().associate { (index, diary) -> diary.id to index }
    val diaries = sourceDiaries.mapIndexed { index, diary ->
        RestoreDiary(
            name = diary.name.trim().ifEmpty { defaultDiaryName },
            iconKey = diary.icon,
            position = index,
            createdAt = BackupTime.parseMillis(diary.createdAt) ?: now,
            suggestedGroups = diary.suggestedGroups.distinct().mapNotNull { id -> groupIndexById[id].also { if (it == null) missing++ } },
            reminders = restoreReminders(diary.reminders, diary.reminder),
        )
    }.ifEmpty { listOf(RestoreDiary(defaultDiaryName, "", 0, now, emptyList())) }

    // Habits: one per tag.
    val habits = backup.habits.mapNotNull { habit -> tagRefs[habit.tag].also { if (it == null) missing++ }?.let { habit to it } }
        .distinctBy { (_, ref) -> ref }
        .sortedBy { (habit, _) -> habit.position }
        .mapIndexed { position, (habit, ref) ->
            RestoreHabit(
                ref,
                habit.daysPerWeek.coerceIn(1, 7),
                position,
                BackupTime.parseMillis(habit.createdAt) ?: now,
                restoreReminders(habit.reminders, habit.reminder),
            )
        }

    // Entries.
    val entries = backup.entries.mapNotNull { entry ->
        val time = BackupTime.parse(entry.time)
        val offsetMinutes = time?.offset?.totalSeconds?.div(60)
        if (time == null || offsetMinutes == null || offsetMinutes !in -MAX_OFFSET_MIN..MAX_OFFSET_MIN) {
            skipped++
            return@mapNotNull null
        }
        val timestamp = time.toInstant().toEpochMilli()
        val diaryIndex = diaryIndexById[entry.diary] ?: run { if (backup.diaries.isNotEmpty()) missing++; 0 }
        val mood = entry.mood?.let { id -> moodRefs[id].also { if (it == null) missing++ } }
        val createdAt = BackupTime.parseMillis(entry.createdAt) ?: timestamp
        RestoreEntry(
            timestamp = timestamp,
            utcOffsetMinutes = offsetMinutes,
            localDate = time.toLocalDate().toEpochDay(),
            diaryIndex = diaryIndex,
            mood = mood,
            title = entry.title?.takeIf { it.isNotBlank() },
            note = entry.note?.takeIf { it.isNotBlank() },
            tags = entry.tags.distinct().mapNotNull { id -> tagRefs[id].also { if (it == null) missing++ } },
            photos = entry.photos.distinct().filter { path -> DakilioArchive.isPhotoPath(path).also { if (!it) missing++ } },
            createdAt = createdAt,
            updatedAt = BackupTime.parseMillis(entry.updatedAt) ?: createdAt,
        )
    }

    val settings = backup.settings?.let { s ->
        RestoreSettings(
            themeMode = ThemeMode.entries.firstOrNull { it.name == s.theme },
            tagStyle = TagStyle.entries.firstOrNull { it.name == s.tagStyle },
            showHints = s.showHints,
            timelineTextScale = s.timelineTextScale?.takeIf { it.isFinite() }?.let(TextScale::coerce),
            editorTextScale = s.editorTextScale?.takeIf { it.isFinite() }?.let(TextScale::coerce),
            previewLength = PreviewLength.entries.firstOrNull { it.name == s.previewLength },
            compactTags = s.compactTags,
            tagLimit = s.tagLimit?.let(TagLimit::parse),
            monthNavigator = s.monthNavigator,
            monthNavigatorInAppBar = s.monthNavigatorInAppBar,
            closeAfterSave = s.closeAfterSave,
            keywordAutoAdd = s.keywordAutoAdd,
            weekStart = WeekStart.entries.firstOrNull { it.name == s.weekStart },
            photoSize = PhotoSize.entries.firstOrNull { it.name == s.photoSize },
            lastDiaryIndex = s.lastDiary?.let(diaryIndexById::get),
            timelineDiaryIndex = s.timelineDiary?.let(diaryIndexById::get),
        )
    } ?: RestoreSettings()

    val dates = entries.map { it.localDate }
    return RestorePlan(
        primaries = primaries,
        aliases = aliases,
        diaries = diaries,
        groups = groups,
        habits = habits,
        entries = entries,
        settings = settings,
        stats = RestoreStats(
            entries = entries.size,
            firstDate = dates.minOrNull()?.let(LocalDate::ofEpochDay),
            lastDate = dates.maxOrNull()?.let(LocalDate::ofEpochDay),
            photos = entries.sumOf { it.photos.size },
            diaries = diaries.size,
            groups = groups.size,
            tags = groups.sumOf { it.tags.size },
            aliases = aliases.size,
            habits = habits.size,
            missingReferences = missing,
            entriesSkipped = skipped,
        ),
    )
}

/**
 * A diary's or habit's reminders: the list, or else 1.2.0's single reminder when it was on (one that
 * was off was only a remembered time). An unreadable time keeps the default; unknown day names are
 * dropped, and no day left means every day.
 */
private fun restoreReminders(reminders: List<BackupReminder>, legacy: BackupReminder?): List<RestoreReminder> {
    val source = reminders.ifEmpty { listOfNotNull(legacy?.takeIf { it.enabled }) }
    return source.map { reminder ->
        RestoreReminder(
            time = runCatching { LocalTime.parse(reminder.time) }.getOrNull()?.withSecond(0)?.withNano(0) ?: Reminder.DEFAULT_TIME,
            days = reminder.days.orEmpty().mapNotNull { name -> DAY_NAMES.entries.firstOrNull { it.value == name.lowercase(Locale.ROOT) }?.key }
                .toSet()
                .ifEmpty { ReminderDays.EVERY_DAY },
            enabled = reminder.enabled,
        )
    }
}

/** How a reminder is written into a backup; every day writes no days. */
fun Reminder.toBackup(): BackupReminder = BackupReminder(
    enabled = enabled,
    time = String.format(Locale.ROOT, "%02d:%02d", time.hour, time.minute),
    days = days.takeIf { it != ReminderDays.EVERY_DAY }?.sorted()?.map { DAY_NAMES.getValue(it) },
)

private val DAY_NAMES: Map<DayOfWeek, String> = DayOfWeek.entries.associateWith { it.name.take(3).lowercase(Locale.ROOT) }
