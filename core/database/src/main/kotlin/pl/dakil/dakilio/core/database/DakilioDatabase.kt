package pl.dakil.dakilio.core.database

import android.content.Context
import androidx.room.Database
import androidx.room.RoomDatabase
import pl.dakil.dakilio.core.database.dao.DiaryDao
import pl.dakil.dakilio.core.database.dao.HabitDao
import pl.dakil.dakilio.core.database.dao.ImportDao
import pl.dakil.dakilio.core.database.dao.KeywordDao
import pl.dakil.dakilio.core.database.dao.MoodDao
import pl.dakil.dakilio.core.database.dao.PhotoDao
import pl.dakil.dakilio.core.database.dao.PostDao
import pl.dakil.dakilio.core.database.dao.ReminderDao
import pl.dakil.dakilio.core.database.dao.TagDao
import pl.dakil.dakilio.core.database.entity.DiaryEntity
import pl.dakil.dakilio.core.database.entity.DiarySuggestedGroupCrossRef
import pl.dakil.dakilio.core.database.entity.HabitEntity
import pl.dakil.dakilio.core.database.entity.MoodEntity
import pl.dakil.dakilio.core.database.entity.PhotoFileEntity
import pl.dakil.dakilio.core.database.entity.PostEntity
import pl.dakil.dakilio.core.database.entity.PostPhotoEntity
import pl.dakil.dakilio.core.database.entity.PostTagCrossRef
import pl.dakil.dakilio.core.database.entity.TagEntity
import pl.dakil.dakilio.core.database.entity.TagGroupEntity
import pl.dakil.dakilio.core.database.entity.TagKeywordEntity

@Database(
    entities = [
        MoodEntity::class,
        DiaryEntity::class,
        TagGroupEntity::class,
        TagEntity::class,
        DiarySuggestedGroupCrossRef::class,
        PostEntity::class,
        PostTagCrossRef::class,
        PostPhotoEntity::class,
        PhotoFileEntity::class,
        HabitEntity::class,
        TagKeywordEntity::class,
    ],
    // No migrations before the first release: a schema change edits version 1 and needs a reinstall.
    version = 1,
    exportSchema = true,
)
abstract class DakilioDatabase : RoomDatabase() {
    abstract fun moodDao(): MoodDao
    abstract fun diaryDao(): DiaryDao
    abstract fun tagDao(): TagDao
    abstract fun postDao(): PostDao
    abstract fun importDao(): ImportDao
    abstract fun habitDao(): HabitDao
    abstract fun keywordDao(): KeywordDao
    abstract fun reminderDao(): ReminderDao
    abstract fun photoDao(): PhotoDao

    companion object {
        const val NAME = "dakilio.db"

        /** Whether the database was ever created (and seeded) on this device. Doesn't open it. */
        fun exists(context: Context): Boolean = context.getDatabasePath(NAME).exists()
    }
}
