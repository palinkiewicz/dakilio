package pl.dakil.dakilio.core.backup

import java.time.Instant
import java.time.OffsetDateTime
import java.time.ZoneOffset
import java.time.format.DateTimeParseException
import kotlinx.serialization.EncodeDefault
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * `dakilio.json`, the heart of a `.dakilio` backup (see `.materials/dakilio_format.md`).
 *
 * Ids are local to the file: they only connect entries to their moods, tags and diaries. Fields
 * that are null or at their default are left out of the file, so it stays short and readable, and
 * unknown keys are ignored on reading, so later versions of the app can add fields to version 1.
 */
@OptIn(ExperimentalSerializationApi::class)
@Serializable
data class DakilioBackup(
    @EncodeDefault val format: String = "",
    @EncodeDefault val version: Int = 0,
    /** When the backup was made, with the device's offset. */
    val exportedAt: String? = null,
    val appVersion: String? = null,
    val settings: BackupSettings? = null,
    val moods: List<BackupMood> = emptyList(),
    val diaries: List<BackupDiary> = emptyList(),
    val tagGroups: List<BackupTagGroup> = emptyList(),
    val habits: List<BackupHabit> = emptyList(),
    val entries: List<BackupEntry> = emptyList(),
) {
    companion object {
        const val FORMAT = "dakilio"
        const val FORMAT_VERSION = 1

        /** The name of the JSON member, always the archive's first. */
        const val JSON_MEMBER = "dakilio.json"

        /** Where photos live inside the archive. */
        const val PHOTO_DIR = "photos/"

        val json = Json {
            prettyPrint = true
            explicitNulls = false
            encodeDefaults = false
            ignoreUnknownKeys = true
        }

        fun parse(text: String): DakilioBackup = json.decodeFromString(serializer(), text)
    }

    fun encode(): String = json.encodeToString(serializer(), this)
}

/** App settings. Every field is optional: whatever is missing or unknown keeps the device's value. */
@Serializable
data class BackupSettings(
    /** A `ThemeMode` name. */
    val theme: String? = null,
    /** A `TagStyle` name. */
    val tagStyle: String? = null,
    val showHints: Boolean? = null,
    val timelineTextScale: Float? = null,
    val editorTextScale: Float? = null,
    /** A `PreviewLength` name. */
    val previewLength: String? = null,
    val compactTags: Boolean? = null,
    val closeAfterSave: Boolean? = null,
    /** Keywords add their tag at once instead of suggesting it. */
    val keywordAutoAdd: Boolean? = null,
    /** The diary the editor picks by default. */
    val lastDiary: Long? = null,
    /** A `WeekStart` name: the first day of the week, or `Region` to follow the device's region. */
    val weekStart: String? = null,
    /** A `PhotoSize` name: the long edge photos are scaled down to (`Px1280`, …) or `Original`. */
    val photoSize: String? = null,
)

/** A mood: one primary per level (1 = worst … 5 = best), plus aliases. */
@Serializable
data class BackupMood(
    val id: Long,
    val level: Int,
    val name: String,
    val primary: Boolean = false,
    /** Order among the level's aliases. */
    val position: Int = 0,
    val archived: Boolean = false,
    /** A palette key; none means the level's (or its primary's) color. */
    val color: String? = null,
)

@Serializable
data class BackupDiary(
    val id: Long,
    val name: String,
    val icon: String = "",
    val position: Int = 0,
    val createdAt: String? = null,
    /** Ids of the tag groups the editor expands for this diary. */
    val suggestedGroups: List<Long> = emptyList(),
    /** The daily reminder to write; none means off. */
    val reminder: BackupReminder? = null,
)

/** A daily reminder. Its time is kept while it's off. */
@Serializable
data class BackupReminder(
    val enabled: Boolean = false,
    /** Local time of day, `HH:mm`. */
    val time: String = "20:00",
)

@Serializable
data class BackupTagGroup(
    val id: Long,
    val name: String,
    val position: Int = 0,
    val archived: Boolean = false,
    /** The default color of the group's tags (a palette key). */
    val color: String? = null,
    val tags: List<BackupTag> = emptyList(),
)

@Serializable
data class BackupTag(
    val id: Long,
    val name: String,
    val icon: String = "",
    val position: Int = 0,
    val archived: Boolean = false,
    /** The tag's own color (a palette key); none means its group's. */
    val color: String? = null,
    /** Words that suggest the tag in the editor (the Keywords extra). */
    val keywords: List<BackupKeyword> = emptyList(),
)

@Serializable
data class BackupKeyword(
    val word: String,
    /** The last word also matches longer words starting with it. */
    val approximate: Boolean = false,
)

@Serializable
data class BackupHabit(
    /** The id of the habit's tag. */
    val tag: Long,
    val daysPerWeek: Int,
    val position: Int = 0,
    val createdAt: String? = null,
    /** The daily reminder until the week's target is met; none means off. */
    val reminder: BackupReminder? = null,
)

@Serializable
data class BackupEntry(
    /** The moment the entry describes, with the UTC offset it was written in (ISO 8601). */
    val time: String,
    val diary: Long,
    val mood: Long? = null,
    val title: String? = null,
    val note: String? = null,
    val tags: List<Long> = emptyList(),
    /** Archive paths of the photos, in order (`photos/…`). */
    val photos: List<String> = emptyList(),
    val createdAt: String? = null,
    val updatedAt: String? = null,
)

/** How the backup writes and reads its times. */
object BackupTime {
    /** An ISO offset date-time like `2026-11-02T21:05:13.12+01:00`. */
    fun format(epochMillis: Long, utcOffsetMinutes: Int): String =
        OffsetDateTime.ofInstant(Instant.ofEpochMilli(epochMillis), ZoneOffset.ofTotalSeconds(utcOffsetMinutes * 60)).toString()

    /** An instant in UTC, like `2026-11-02T20:05:13.120Z`. */
    fun formatInstant(epochMillis: Long): String = Instant.ofEpochMilli(epochMillis).toString()

    fun parse(text: String): OffsetDateTime? = try {
        OffsetDateTime.parse(text.trim())
    } catch (_: DateTimeParseException) {
        null
    }

    /** Epoch millis of an instant (`…Z`) or an offset date-time, or null when it's neither. */
    fun parseMillis(text: String?): Long? {
        if (text == null) return null
        return try {
            Instant.parse(text.trim()).toEpochMilli()
        } catch (_: DateTimeParseException) {
            parse(text)?.toInstant()?.toEpochMilli()
        }
    }
}
