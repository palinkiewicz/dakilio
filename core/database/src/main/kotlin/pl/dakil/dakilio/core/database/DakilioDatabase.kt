package pl.dakil.dakilio.core.database

import android.content.Context
import androidx.room.AutoMigration
import androidx.room.Database
import androidx.room.RoomDatabase
import pl.dakil.dakilio.core.database.dao.DiaryDao
import pl.dakil.dakilio.core.database.dao.HabitDao
import pl.dakil.dakilio.core.database.dao.ImportDao
import pl.dakil.dakilio.core.database.dao.MoodDao
import pl.dakil.dakilio.core.database.dao.PostDao
import pl.dakil.dakilio.core.database.dao.TagDao
import pl.dakil.dakilio.core.database.entity.DiaryEntity
import pl.dakil.dakilio.core.database.entity.DiarySuggestedGroupCrossRef
import pl.dakil.dakilio.core.database.entity.HabitEntity
import pl.dakil.dakilio.core.database.entity.MoodEntity
import pl.dakil.dakilio.core.database.entity.PostDiaryCrossRef
import pl.dakil.dakilio.core.database.entity.PostEntity
import pl.dakil.dakilio.core.database.entity.PostPhotoEntity
import pl.dakil.dakilio.core.database.entity.PostTagCrossRef
import pl.dakil.dakilio.core.database.entity.TagEntity
import pl.dakil.dakilio.core.database.entity.TagGroupEntity

@Database(
    entities = [
        MoodEntity::class,
        DiaryEntity::class,
        TagGroupEntity::class,
        TagEntity::class,
        DiarySuggestedGroupCrossRef::class,
        PostEntity::class,
        PostDiaryCrossRef::class,
        PostTagCrossRef::class,
        PostPhotoEntity::class,
        HabitEntity::class,
    ],
    version = 3,
    exportSchema = true,
    autoMigrations = [
        // 2: tag and tag_group get a nullable color_key (the Tag colors extra).
        AutoMigration(from = 1, to = 2),
        // 3: the habit table (the Habits extra).
        AutoMigration(from = 2, to = 3),
    ],
)
abstract class DakilioDatabase : RoomDatabase() {
    abstract fun moodDao(): MoodDao
    abstract fun diaryDao(): DiaryDao
    abstract fun tagDao(): TagDao
    abstract fun postDao(): PostDao
    abstract fun importDao(): ImportDao
    abstract fun habitDao(): HabitDao

    companion object {
        const val NAME = "dakilio.db"

        /** Whether the database was ever created (and seeded) on this device. Doesn't open it. */
        fun exists(context: Context): Boolean = context.getDatabasePath(NAME).exists()
    }
}
