package pl.dakil.dakilio.core.database

import androidx.room.Database
import androidx.room.RoomDatabase
import pl.dakil.dakilio.core.database.dao.DiaryDao
import pl.dakil.dakilio.core.database.dao.ImportDao
import pl.dakil.dakilio.core.database.dao.MoodDao
import pl.dakil.dakilio.core.database.dao.PostDao
import pl.dakil.dakilio.core.database.dao.TagDao
import pl.dakil.dakilio.core.database.entity.DiaryEntity
import pl.dakil.dakilio.core.database.entity.DiarySuggestedGroupCrossRef
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
    ],
    version = 1,
    exportSchema = true,
)
abstract class DakilioDatabase : RoomDatabase() {
    abstract fun moodDao(): MoodDao
    abstract fun diaryDao(): DiaryDao
    abstract fun tagDao(): TagDao
    abstract fun postDao(): PostDao
    abstract fun importDao(): ImportDao
}
