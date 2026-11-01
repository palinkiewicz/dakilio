package pl.dakil.dakilio.core.daylio

import java.time.DateTimeException
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import pl.dakil.dakilio.core.model.MOOD_LEVELS

/**
 * What a Daylio backup turns into in Dakilio. The import replaces everything, so the plan doesn't
 * look at existing data: it only maps Daylio's ids onto list indices that the repository resolves
 * to fresh database ids.
 */
data class ImportPlan(
    /** New names for the level primaries (level → name); levels absent here get Dakilio's default name. */
    val primaryNames: Map<Int, String>,
    val aliases: List<PlannedMood>,
    val groups: List<PlannedGroup>,
    val entries: List<PlannedEntry>,
    val stats: ImportStats,
)

data class PlannedMood(val level: Int, val label: String, val archived: Boolean, val position: Int)

data class PlannedGroup(val name: String, val position: Int, val tags: List<PlannedTag>)

data class PlannedTag(val label: String, val iconKey: String, val archived: Boolean, val position: Int)

sealed interface MoodRef {
    data class Primary(val level: Int) : MoodRef

    /** An index into [ImportPlan.aliases]. */
    data class Alias(val index: Int) : MoodRef
}

/** Indices into [ImportPlan.groups] and that group's tags. */
data class TagRef(val groupIndex: Int, val tagIndex: Int)

data class PlannedPhoto(val checksum: String, val rotationDegrees: Int)

data class PlannedEntry(
    val timestamp: Long,
    val utcOffsetMinutes: Int,
    /** Epoch day of the entry's wall-clock date. */
    val localDate: Long,
    val mood: MoodRef,
    val title: String?,
    val note: String?,
    val tags: List<TagRef>,
    val photos: List<PlannedPhoto>,
)

data class ImportStats(
    val entries: Int,
    val firstDate: LocalDate?,
    val lastDate: LocalDate?,
    val aliases: Int,
    val archivedAliases: Int,
    val groups: Int,
    val tags: Int,
    val photos: Int,
    val voiceNotesSkipped: Int,
    val favoritesSkipped: Int,
    /** Tag, mood or asset ids an entry points at that the backup doesn't define. */
    val missingReferences: Int,
    /** Entries dropped because their mood is unknown. */
    val entriesSkipped: Int,
)

/** Daylio's mood groups run 1 = best … 5 = worst; Dakilio's levels run the other way. */
internal fun daylioLevel(moodGroupId: Int): Int? =
    (MOOD_LEVELS + 1 - moodGroupId).takeIf { moodGroupId in 1..MOOD_LEVELS }

private const val MAX_OFFSET_MIN = 18 * 60

/**
 * Maps a parsed backup onto Dakilio's model. [fallbackGroupName] names the group that collects
 * activities without a (known) group, which older backups might have.
 */
fun planImport(backup: DaylioBackup, fallbackGroupName: String): ImportPlan {
    var missingReferences = 0
    var entriesSkipped = 0

    // Moods: one base mood per level becomes (the name of) the primary, custom ones become aliases.
    val moodsById = backup.customMoods.associateBy { it.id }
    val primaryNames = mutableMapOf<Int, String>()
    val baseIds = mutableSetOf<Int>()
    backup.customMoods.filter { daylioLevel(it.moodGroupId) != null }.groupBy { it.moodGroupId }.forEach { (groupId, moods) ->
        val base = moods.filter { it.predefinedNameId != -1 }.minByOrNull { it.moodGroupOrder }
            ?: moods.firstOrNull { it.moodGroupOrder == 0 }
            ?: return@forEach
        baseIds += base.id
        base.customName.trim().takeIf { it.isNotEmpty() }?.let { primaryNames[daylioLevel(groupId)!!] = it }
    }
    val usedMoodIds = backup.dayEntries.mapTo(mutableSetOf()) { it.mood }
    val aliasCandidates = backup.customMoods
        .filter { it.id !in baseIds && daylioLevel(it.moodGroupId) != null && it.customName.isNotBlank() }
        // An archived mood is only worth keeping for the entries that still use it.
        .filter { it.state == 0 || it.id in usedMoodIds }
        .sortedWith(compareBy({ daylioLevel(it.moodGroupId) }, { it.moodGroupOrder }, { it.id }))
    val aliases = mutableListOf<PlannedMood>()
    val moodRefs = mutableMapOf<Int, MoodRef>()
    aliasCandidates.groupBy { daylioLevel(it.moodGroupId)!! }.forEach { (level, moods) ->
        moods.forEachIndexed { position, mood ->
            moodRefs[mood.id] = MoodRef.Alias(aliases.size)
            aliases += PlannedMood(level, mood.customName.trim(), archived = mood.state != 0, position = position)
        }
    }
    // Base moods, and custom ones without a usable name, fall back to the level's primary.
    moodsById.values.forEach { mood ->
        if (mood.id !in moodRefs) daylioLevel(mood.moodGroupId)?.let { moodRefs[mood.id] = MoodRef.Primary(it) }
    }

    // Activities: groups in Daylio's order, unknown or missing group ids collected in a fallback group.
    val sortedGroups = backup.tagGroups.sortedWith(compareBy({ it.order }, { it.id }))
    val groupIndexById = sortedGroups.withIndex().associate { (index, group) -> group.id to index }
    val validTags = backup.tags.filter { it.name.isNotBlank() }
    val needsFallback = validTags.any { it.groupId == null || it.groupId !in groupIndexById }
    val groupNames = sortedGroups.map { it.name.trim().ifEmpty { fallbackGroupName } } +
        if (needsFallback) listOf(fallbackGroupName) else emptyList()
    val fallbackIndex = sortedGroups.size
    val tagsByGroup = validTags.groupBy { groupIndexById[it.groupId] ?: fallbackIndex }
    val tagRefs = mutableMapOf<Int, TagRef>()
    val groups = groupNames.mapIndexed { groupIndex, name ->
        val tags = tagsByGroup[groupIndex].orEmpty().sortedWith(compareBy({ it.order }, { it.id }))
        PlannedGroup(
            name = name,
            position = groupIndex,
            tags = tags.mapIndexed { tagIndex, tag ->
                tagRefs[tag.id] = TagRef(groupIndex, tagIndex)
                val label = tag.name.trim()
                PlannedTag(label, daylioTagIcon(label), archived = tag.state != 0, position = tagIndex)
            },
        )
    }

    // Entries.
    val assetsById = backup.assets.associateBy { it.id }
    var voiceNotes = 0
    var favorites = 0
    val entries = backup.dayEntries.mapNotNull { entry ->
        val mood = moodRefs[entry.mood]
        if (mood == null) {
            missingReferences++
            entriesSkipped++
            return@mapNotNull null
        }
        val tags = entry.tags.distinct().mapNotNull { id -> tagRefs[id].also { if (it == null) missingReferences++ } }
        val photos = entry.assets.distinct().mapNotNull { id ->
            val asset = assetsById[id]
            when {
                asset == null -> { missingReferences++; null }
                asset.isPhoto && asset.checksum.isNotBlank() -> PlannedPhoto(asset.checksum, asset.orientation)
                asset.isAudio -> { voiceNotes++; null }
                else -> { missingReferences++; null }
            }
        }
        if (entry.isFavorite) favorites++
        val offset = offsetMinutes(entry)
        PlannedEntry(
            timestamp = entry.datetime,
            utcOffsetMinutes = offset,
            localDate = localDate(entry, offset).toEpochDay(),
            mood = mood,
            title = entry.noteTitle.trim().ifEmpty { null },
            note = daylioHtmlToText(entry.note).ifEmpty { null },
            tags = tags,
            photos = photos,
        )
    }

    val dates = entries.map { it.localDate }
    return ImportPlan(
        primaryNames = primaryNames,
        aliases = aliases,
        groups = groups,
        entries = entries,
        stats = ImportStats(
            entries = entries.size,
            firstDate = dates.minOrNull()?.let(LocalDate::ofEpochDay),
            lastDate = dates.maxOrNull()?.let(LocalDate::ofEpochDay),
            aliases = aliases.size,
            archivedAliases = aliases.count { it.archived },
            groups = groups.size,
            tags = groups.sumOf { it.tags.size },
            photos = entries.sumOf { it.photos.size },
            voiceNotesSkipped = voiceNotes,
            favoritesSkipped = favorites,
            missingReferences = missingReferences,
            entriesSkipped = entriesSkipped,
        ),
    )
}

/** The backup's own offset, kept as it is (never the phone's current zone). */
private fun offsetMinutes(entry: DaylioEntry): Int {
    val minutes = entry.timeZoneOffset / 60_000
    if (minutes in -MAX_OFFSET_MIN..MAX_OFFSET_MIN) return minutes.toInt()
    // An unusable offset: recover it from the wall-clock fields, or give up and use UTC.
    val wallClock = wallClockDate(entry)?.atTime(entry.hour.coerceIn(0, 23), entry.minute.coerceIn(0, 59))
        ?: return 0
    val derived = (wallClock.toEpochSecond(ZoneOffset.UTC) * 1000 - entry.datetime) / 60_000
    return derived.takeIf { it in -MAX_OFFSET_MIN..MAX_OFFSET_MIN }?.toInt() ?: 0
}

/** The wall-clock date from the fields (0-based month), or from `datetime + offset` if they're invalid. */
private fun localDate(entry: DaylioEntry, offsetMinutes: Int): LocalDate =
    wallClockDate(entry) ?: Instant.ofEpochMilli(entry.datetime).atOffset(ZoneOffset.ofTotalSeconds(offsetMinutes * 60)).toLocalDate()

private fun wallClockDate(entry: DaylioEntry): LocalDate? = try {
    LocalDate.of(entry.year, entry.month + 1, entry.day)
} catch (_: DateTimeException) {
    null
}
