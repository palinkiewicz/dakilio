package pl.dakil.dakilio.feature.insights.impl.logic

import java.time.LocalDate
import java.time.LocalDateTime
import pl.dakil.dakilio.core.model.Mood
import pl.dakil.dakilio.core.model.Post
import pl.dakil.dakilio.core.model.PostPhoto
import pl.dakil.dakilio.core.model.Tag

internal val TODAY: LocalDate = LocalDate.of(2026, 10, 29) // a Thursday

internal val MOODS = (1..5).map { Mood(it.toLong(), it, "m$it", isPrimary = true, position = 0) }
internal val SAD = Mood(9, 2, "Sad", isPrimary = false, position = 0)
internal val WALK = Tag(1, 1, "Walk", "directions_walk", 0)
internal val WORK = Tag(2, 1, "Work", "work", 1)
internal val READ = Tag(3, 1, "Read", "book", 2)

private var nextId = 1L

internal fun post(
    daysAgo: Long,
    level: Int? = null,
    vararg tags: Tag,
    hour: Int = 12,
    note: String? = null,
    title: String? = null,
    photos: Int = 0,
    mood: Mood? = level?.let { MOODS[it - 1] },
    diaryId: Long = 1,
): Post = postAt(TODAY.minusDays(daysAgo).atTime(hour, 0), mood, tags.toList(), note, title, photos, diaryId)

internal fun postAt(
    dateTime: LocalDateTime,
    mood: Mood? = null,
    tags: List<Tag> = emptyList(),
    note: String? = null,
    title: String? = null,
    photos: Int = 0,
    diaryId: Long = 1,
): Post = Post(
    id = nextId++,
    dateTime = dateTime,
    mood = mood,
    title = title,
    note = note,
    tags = tags,
    diaryId = diaryId,
    photos = List(photos) { PostPhoto(it.toLong(), "p$it.jpg", "/p$it.jpg") },
)
