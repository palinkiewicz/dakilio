package pl.dakil.dakilio.core.data.repository

import java.time.Instant
import java.time.ZoneOffset
import pl.dakil.dakilio.core.database.entity.MoodEntity
import pl.dakil.dakilio.core.database.entity.PopulatedDiary
import pl.dakil.dakilio.core.database.entity.PopulatedPost
import pl.dakil.dakilio.core.database.entity.PopulatedTagGroup
import pl.dakil.dakilio.core.database.entity.TagEntity
import pl.dakil.dakilio.core.model.Diary
import pl.dakil.dakilio.core.model.Mood
import pl.dakil.dakilio.core.model.Post
import pl.dakil.dakilio.core.model.PostPhoto
import pl.dakil.dakilio.core.model.Tag
import pl.dakil.dakilio.core.model.TagGroup

/** [primaryColor] is the color of the level's primary, which the mood shows unless it has its own. */
internal fun MoodEntity.asModel(primaryColor: String?) = Mood(
    id = id,
    level = level,
    label = label,
    isPrimary = isPrimary,
    position = position,
    colorKey = colorKey,
    color = colorKey ?: primaryColor,
)

/** [groupColor] is the group's default color, which the tag shows unless it has its own. */
internal fun TagEntity.asModel(groupColor: String?) = Tag(
    id = id,
    groupId = groupId,
    label = label,
    iconKey = iconKey,
    position = position,
    isArchived = isArchived,
    colorKey = colorKey,
    color = colorKey ?: groupColor,
)

internal fun PopulatedDiary.asModel() = Diary(
    id = diary.id,
    name = diary.name,
    iconKey = diary.iconKey,
    position = diary.position,
    suggestedGroupIds = suggestedGroupIds.toSet(),
)

/** [keep] picks which tags to include; by default only active ones. */
internal fun PopulatedTagGroup.asModel(keep: (TagEntity) -> Boolean = { !it.isArchived }) = TagGroup(
    id = group.id,
    name = group.name,
    position = group.position,
    tags = tags.asSequence()
        .filter(keep)
        .sortedWith(compareBy({ it.position }, { it.id }))
        .map { it.asModel(group.colorKey) }
        .toList(),
    colorKey = group.colorKey,
)

/**
 * [groupColors] maps each tag group's id to its default tag color; [primaryColors] maps each mood
 * level to its primary's color.
 */
internal fun PopulatedPost.asModel(
    pathOf: (String) -> String,
    groupColors: Map<Long, String?> = emptyMap(),
    primaryColors: Map<Int, String?> = emptyMap(),
) = Post(
    id = post.id,
    dateTime = Instant.ofEpochMilli(post.timestamp)
        .atOffset(ZoneOffset.ofTotalSeconds(post.utcOffsetMinutes * 60))
        .toLocalDateTime(),
    mood = mood?.let { it.asModel(primaryColors[it.level]) },
    title = post.title,
    note = post.note,
    tags = tags.sortedWith(compareBy({ it.groupId }, { it.position })).map { it.asModel(groupColors[it.groupId]) },
    diaryId = post.diaryId,
    photos = photos.sortedBy { it.position }.map { PostPhoto(it.id, it.fileName, pathOf(it.fileName)) },
)
