package pl.dakil.dakilio.core.database.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * A pickable mood on the fixed 5-level scale. Each level has exactly one primary (seeded, renamable,
 * never removed) plus optional aliases. Faces and colors belong to the level, so they live in code.
 */
@Entity(tableName = "mood", indices = [Index("level")])
data class MoodEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    /** 1 = worst … 5 = best. */
    val level: Int,
    val label: String,
    @ColumnInfo(name = "is_primary") val isPrimary: Boolean,
    /** Order among the level's aliases. */
    val position: Int,
    /** Archived aliases stay attached to old entries but are hidden from pickers. */
    @ColumnInfo(name = "is_archived") val isArchived: Boolean = false,
)

@Entity(tableName = "diary")
data class DiaryEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    @ColumnInfo(name = "icon_key") val iconKey: String,
    val position: Int,
    @ColumnInfo(name = "created_at") val createdAt: Long,
)

@Entity(tableName = "tag_group")
data class TagGroupEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val position: Int,
    @ColumnInfo(name = "is_archived") val isArchived: Boolean = false,
    /** Default color of the group's tags (a palette key), or `null`. */
    @ColumnInfo(name = "color_key") val colorKey: String? = null,
)

@Entity(
    tableName = "tag",
    foreignKeys = [
        ForeignKey(TagGroupEntity::class, ["id"], ["group_id"], onDelete = ForeignKey.CASCADE),
    ],
    indices = [Index("group_id")],
)
data class TagEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    @ColumnInfo(name = "group_id") val groupId: Long,
    val label: String,
    @ColumnInfo(name = "icon_key") val iconKey: String,
    val position: Int,
    @ColumnInfo(name = "is_archived") val isArchived: Boolean = false,
    /** The tag's own color (a palette key), or `null` to use its group's. */
    @ColumnInfo(name = "color_key") val colorKey: String? = null,
)

@Entity(
    tableName = "diary_suggested_group",
    primaryKeys = ["diary_id", "group_id"],
    foreignKeys = [
        ForeignKey(DiaryEntity::class, ["id"], ["diary_id"], onDelete = ForeignKey.CASCADE),
        ForeignKey(TagGroupEntity::class, ["id"], ["group_id"], onDelete = ForeignKey.CASCADE),
    ],
    indices = [Index("group_id")],
)
data class DiarySuggestedGroupCrossRef(
    @ColumnInfo(name = "diary_id") val diaryId: Long,
    @ColumnInfo(name = "group_id") val groupId: Long,
)

@Entity(
    tableName = "post",
    foreignKeys = [
        // RESTRICT: a mood is only deleted after its entries were moved to another one.
        ForeignKey(MoodEntity::class, ["id"], ["mood_id"], onDelete = ForeignKey.RESTRICT),
        // RESTRICT: a diary is only deleted after its entries were moved to another one or deleted.
        ForeignKey(DiaryEntity::class, ["id"], ["diary_id"], onDelete = ForeignKey.RESTRICT),
    ],
    indices = [Index("local_date"), Index("mood_id"), Index("diary_id"), Index("timestamp")],
)
data class PostEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    /** Epoch millis of the moment the entry describes. */
    val timestamp: Long,
    /** UTC offset at that moment, so the wall-clock time survives time-zone changes. */
    @ColumnInfo(name = "utc_offset_min") val utcOffsetMinutes: Int,
    /** Local calendar day (epoch day) for fast per-day grouping and streaks. */
    @ColumnInfo(name = "local_date") val localDate: Long,
    @ColumnInfo(name = "mood_id") val moodId: Long?,
    /** The one diary the entry belongs to. */
    @ColumnInfo(name = "diary_id") val diaryId: Long,
    val title: String?,
    val note: String?,
    @ColumnInfo(name = "created_at") val createdAt: Long,
    @ColumnInfo(name = "updated_at") val updatedAt: Long,
)

@Entity(
    tableName = "post_tag",
    primaryKeys = ["post_id", "tag_id"],
    foreignKeys = [
        ForeignKey(PostEntity::class, ["id"], ["post_id"], onDelete = ForeignKey.CASCADE),
        ForeignKey(TagEntity::class, ["id"], ["tag_id"], onDelete = ForeignKey.CASCADE),
    ],
    indices = [Index("tag_id")],
)
data class PostTagCrossRef(
    @ColumnInfo(name = "post_id") val postId: Long,
    @ColumnInfo(name = "tag_id") val tagId: Long,
)

@Entity(
    tableName = "post_photo",
    foreignKeys = [
        ForeignKey(PostEntity::class, ["id"], ["post_id"], onDelete = ForeignKey.CASCADE),
    ],
    indices = [Index("post_id")],
)
data class PostPhotoEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    @ColumnInfo(name = "post_id") val postId: Long,
    @ColumnInfo(name = "file_name") val fileName: String,
    val position: Int,
)

/**
 * A habit (the Habits extra): a tag the user wants on [daysPerWeek] days a week. One per tag; it goes
 * when its tag is deleted (which includes a Daylio import replacing every tag).
 */
@Entity(
    tableName = "habit",
    foreignKeys = [
        ForeignKey(TagEntity::class, ["id"], ["tag_id"], onDelete = ForeignKey.CASCADE),
    ],
    indices = [Index("tag_id", unique = true)],
)
data class HabitEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    @ColumnInfo(name = "tag_id") val tagId: Long,
    @ColumnInfo(name = "days_per_week") val daysPerWeek: Int,
    val position: Int,
    @ColumnInfo(name = "created_at") val createdAt: Long,
)
