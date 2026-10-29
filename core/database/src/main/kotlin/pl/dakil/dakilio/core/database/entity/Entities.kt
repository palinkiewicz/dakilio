package pl.dakil.dakilio.core.database.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/** A versioned mood scale. Only one is active; old entries keep pointing at the set they used. */
@Entity(tableName = "mood_set")
data class MoodSetEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    @ColumnInfo(name = "scale_size") val scaleSize: Int,
    @ColumnInfo(name = "created_at") val createdAt: Long,
    @ColumnInfo(name = "is_active") val isActive: Boolean,
)

@Entity(
    tableName = "mood",
    foreignKeys = [
        ForeignKey(MoodSetEntity::class, ["id"], ["mood_set_id"], onDelete = ForeignKey.CASCADE),
    ],
    indices = [Index(value = ["mood_set_id", "tier"], unique = true)],
)
data class MoodEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    @ColumnInfo(name = "mood_set_id") val moodSetId: Long,
    /** 1 = worst … scale size = best; also the display order. */
    val tier: Int,
    /** [tier] normalized to 0.0–1.0, stored so analytics can simply AVG() it. */
    val value: Float,
    val label: String,
    @ColumnInfo(name = "icon_key") val iconKey: String,
    @ColumnInfo(name = "color_argb") val colorArgb: Int,
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
        // RESTRICT: moods are never deleted under entries; scale changes create a new mood set.
        ForeignKey(MoodEntity::class, ["id"], ["mood_id"], onDelete = ForeignKey.RESTRICT),
    ],
    indices = [Index("local_date"), Index("mood_id"), Index("timestamp")],
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
    val title: String?,
    val note: String?,
    @ColumnInfo(name = "created_at") val createdAt: Long,
    @ColumnInfo(name = "updated_at") val updatedAt: Long,
)

@Entity(
    tableName = "post_diary",
    primaryKeys = ["post_id", "diary_id"],
    foreignKeys = [
        ForeignKey(PostEntity::class, ["id"], ["post_id"], onDelete = ForeignKey.CASCADE),
        ForeignKey(DiaryEntity::class, ["id"], ["diary_id"], onDelete = ForeignKey.CASCADE),
    ],
    indices = [Index("diary_id")],
)
data class PostDiaryCrossRef(
    @ColumnInfo(name = "post_id") val postId: Long,
    @ColumnInfo(name = "diary_id") val diaryId: Long,
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
