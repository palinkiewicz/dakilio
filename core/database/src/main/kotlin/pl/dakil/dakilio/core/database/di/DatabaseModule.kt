package pl.dakil.dakilio.core.database.di

import android.content.Context
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.sqlite.db.SupportSQLiteDatabase
import dagger.BindsOptionalOf
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import java.time.Clock
import java.util.Optional
import javax.inject.Singleton
import pl.dakil.dakilio.core.common.PhotoDirectory
import pl.dakil.dakilio.core.database.DakilioDatabase
import pl.dakil.dakilio.core.database.DefaultData
import pl.dakil.dakilio.core.database.SampleData
import pl.dakil.dakilio.core.database.dao.DiaryDao
import pl.dakil.dakilio.core.database.dao.HabitDao
import pl.dakil.dakilio.core.database.dao.ImportDao
import pl.dakil.dakilio.core.database.dao.KeywordDao
import pl.dakil.dakilio.core.database.dao.MoodDao
import pl.dakil.dakilio.core.database.dao.PostDao
import pl.dakil.dakilio.core.database.dao.TagDao

@Module
@InstallIn(SingletonComponent::class)
internal object DatabaseModule {
    @Provides
    @Singleton
    fun database(
        @ApplicationContext context: Context,
        photoDirectory: PhotoDirectory,
        clock: Clock,
        sampleData: Optional<SampleData>,
    ): DakilioDatabase = Room.databaseBuilder(context, DakilioDatabase::class.java, DakilioDatabase.NAME)
        .addCallback(
            object : RoomDatabase.Callback() {
                override fun onCreate(db: SupportSQLiteDatabase) {
                    val samples = sampleData.map { it.posts }.orElse(emptyList())
                    DefaultData.seed(db, context, photoDirectory.ensureExists(), clock, samples)
                }
            },
        )
        .build()

    @Provides fun moodDao(db: DakilioDatabase): MoodDao = db.moodDao()

    @Provides fun diaryDao(db: DakilioDatabase): DiaryDao = db.diaryDao()

    @Provides fun tagDao(db: DakilioDatabase): TagDao = db.tagDao()

    @Provides fun postDao(db: DakilioDatabase): PostDao = db.postDao()

    @Provides fun importDao(db: DakilioDatabase): ImportDao = db.importDao()

    @Provides fun habitDao(db: DakilioDatabase): HabitDao = db.habitDao()

    @Provides fun keywordDao(db: DakilioDatabase): KeywordDao = db.keywordDao()
}

/** Lets debug and preview builds bind [SampleData] while release builds leave it out. */
@Module
@InstallIn(SingletonComponent::class)
internal interface SampleDataModule {
    @BindsOptionalOf fun sampleData(): SampleData
}
