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

internal fun MoodEntity.asModel() = Mood(
    id = id,
    level = level,
    label = label,
    isPrimary = isPrimary,
    position = position,
)

internal fun TagEntity.asModel() = Tag(
    id = id,
    groupId = groupId,
    label = label,
    iconKey = iconKey,
    position = position,
    isArchived = isArchived,
)

internal fun PopulatedDiary.asModel() = Diary(
    id = diary.id,
    name = diary.name,
    iconKey = diary.iconKey,
    position = diary.position,
    suggestedGroupIds = suggestedGroupIds.toSet(),
)

internal fun PopulatedTagGroup.asModel() = TagGroup(
    id = group.id,
    name = group.name,
    position = group.position,
    tags = tags.asSequence()
        .filterNot { it.isArchived }
        .sortedWith(compareBy({ it.position }, { it.id }))
        .map { it.asModel() }
        .toList(),
)

internal fun PopulatedPost.asModel(pathOf: (String) -> String) = Post(
    id = post.id,
    dateTime = Instant.ofEpochMilli(post.timestamp)
        .atOffset(ZoneOffset.ofTotalSeconds(post.utcOffsetMinutes * 60))
        .toLocalDateTime(),
    mood = mood?.asModel(),
    title = post.title,
    note = post.note,
    tags = tags.sortedWith(compareBy({ it.groupId }, { it.position })).map { it.asModel() },
    diaryIds = diaryIds.toSet(),
    photos = photos.sortedBy { it.position }.map { PostPhoto(it.id, it.fileName, pathOf(it.fileName)) },
)
