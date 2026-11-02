package pl.dakil.dakilio.core.model

import java.time.LocalDate
import java.time.LocalDateTime

/** A journal entry in exactly one diary. Every other part except the date is optional. */
data class Post(
    val id: Long,
    /** Wall-clock time where the entry was written; entries stay on "their" day across time zones. */
    val dateTime: LocalDateTime,
    val mood: Mood?,
    val title: String?,
    val note: String?,
    val tags: List<Tag>,
    val diaryId: Long,
    val photos: List<PostPhoto>,
) {
    val date: LocalDate get() = dateTime.toLocalDate()
}

data class PostPhoto(
    val id: Long,
    val fileName: String,
    /** Absolute path of the app-private copy. */
    val path: String,
)

/** What the entry editor saves. [photoFileNames] are files already copied into app storage. */
data class PostDraft(
    val id: Long?,
    val dateTime: LocalDateTime,
    val moodId: Long?,
    val title: String?,
    val note: String?,
    val tagIds: Set<Long>,
    val diaryId: Long,
    val photoFileNames: List<String>,
)
