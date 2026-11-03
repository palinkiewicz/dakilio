package pl.dakil.dakilio.core.daylio

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

/**
 * The parts of Daylio's backup JSON the importer reads (see `.materials/daylio_format.md`). Every
 * field has a default and unknown keys are ignored, so other backup versions parse as far as possible.
 */
@Serializable
data class DaylioBackup(
    val version: Int = 0,
    val customMoods: List<DaylioMood> = emptyList(),
    @SerialName("tag_groups") val tagGroups: List<DaylioTagGroup> = emptyList(),
    val tags: List<DaylioTag> = emptyList(),
    val dayEntries: List<DaylioEntry> = emptyList(),
    val assets: List<DaylioAsset> = emptyList(),
) {
    companion object {
        /** The only backup version seen so far. */
        const val KNOWN_VERSION = 15

        internal val json = Json {
            ignoreUnknownKeys = true
            coerceInputValues = true
            isLenient = true
        }

        fun parse(text: String): DaylioBackup = json.decodeFromString(serializer(), text)
    }
}

@Serializable
data class DaylioMood(
    val id: Int = 0,
    @SerialName("custom_name") val customName: String = "",
    /** The level, inverted: 1 = rad (best) … 5 = awful (worst). */
    @SerialName("mood_group_id") val moodGroupId: Int = 0,
    @SerialName("mood_group_order") val moodGroupOrder: Int = 0,
    /** 1..5 for the built-in moods, -1 for custom ones. */
    @SerialName("predefined_name_id") val predefinedNameId: Int = -1,
    /** 0 = active; anything else (2 seen) = archived. */
    val state: Int = 0,
)

@Serializable
data class DaylioTagGroup(
    val id: Int = 0,
    val name: String = "",
    val order: Int = 0,
)

@Serializable
data class DaylioTag(
    val id: Int = 0,
    val name: String = "",
    val order: Int = 0,
    /** Daylio's icon id; see `daylioTagIcon`. */
    val icon: Int = 0,
    /** 0 = active; anything else (1 seen) = archived. */
    val state: Int = 0,
    @SerialName("id_tag_group") val groupId: Int? = null,
)

@Serializable
data class DaylioEntry(
    val id: Int = 0,
    val minute: Int = 0,
    val hour: Int = 0,
    val day: Int = 0,
    /** 0-based. */
    val month: Int = 0,
    val year: Int = 0,
    /** Epoch millis (UTC). */
    val datetime: Long = 0,
    /** Millis. */
    val timeZoneOffset: Long = 0,
    val mood: Int = 0,
    val note: String = "",
    @SerialName("note_title") val noteTitle: String = "",
    val tags: List<Int> = emptyList(),
    val assets: List<Int> = emptyList(),
    val isFavorite: Boolean = false,
)

@Serializable
data class DaylioAsset(
    val id: Int = 0,
    /** The asset's file name inside the archive. Not a reliable content hash. */
    val checksum: String = "",
    /** 1 = photo, 2 = voice note. */
    val type: Int = 0,
    /** A JSON object encoded as a string. */
    @SerialName("android_metadata") val androidMetadata: String = "",
) {
    val isPhoto: Boolean get() = type == TYPE_PHOTO
    val isAudio: Boolean get() = type == TYPE_AUDIO

    /** Rotation in degrees from the nested metadata, 0 when absent or unreadable. */
    val orientation: Int
        get() = runCatching {
            DaylioBackup.json.parseToJsonElement(androidMetadata).jsonObject["Orientation"]?.jsonPrimitive?.intOrNull
        }.getOrNull()?.takeIf { it % 90 == 0 }?.mod(360) ?: 0

    companion object {
        const val TYPE_PHOTO = 1
        const val TYPE_AUDIO = 2
    }
}
