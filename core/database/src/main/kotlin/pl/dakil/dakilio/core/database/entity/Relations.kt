package pl.dakil.dakilio.core.database.entity

import androidx.room.Embedded
import androidx.room.Junction
import androidx.room.Relation

data class PopulatedPost(
    @Embedded val post: PostEntity,
    @Relation(parentColumn = "mood_id", entityColumn = "id")
    val mood: MoodEntity?,
    @Relation(
        parentColumn = "id",
        entityColumn = "id",
        associateBy = Junction(PostTagCrossRef::class, parentColumn = "post_id", entityColumn = "tag_id"),
    )
    val tags: List<TagEntity>,
    @Relation(parentColumn = "id", entityColumn = "post_id")
    val photos: List<PostPhotoEntity>,
)

data class PopulatedDiary(
    @Embedded val diary: DiaryEntity,
    @Relation(
        parentColumn = "id",
        entityColumn = "diary_id",
        entity = DiarySuggestedGroupCrossRef::class,
        projection = ["group_id"],
    )
    val suggestedGroupIds: List<Long>,
)

data class PopulatedTagGroup(
    @Embedded val group: TagGroupEntity,
    @Relation(parentColumn = "id", entityColumn = "group_id")
    val tags: List<TagEntity>,
)

data class DiaryPostCount(
    val diaryId: Long,
    val count: Int,
)

data class PopulatedHabit(
    @Embedded val habit: HabitEntity,
    @Relation(parentColumn = "tag_id", entityColumn = "id")
    val tag: TagEntity,
)

/** A day ([localDate], an epoch day) on which a habit's tag was on at least one entry. */
data class HabitDay(
    val habitId: Long,
    val localDate: Long,
)

/** A diary whose reminder is on. */
data class DiaryReminderRow(
    val diaryId: Long,
    val name: String,
    val minute: Int,
)

/** A habit whose reminder is on, with its tag's label. */
data class HabitReminderRow(
    val habitId: Long,
    val tagId: Long,
    val label: String,
    val daysPerWeek: Int,
    val minute: Int,
)
