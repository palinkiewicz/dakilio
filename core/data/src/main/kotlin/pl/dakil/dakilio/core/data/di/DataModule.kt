package pl.dakil.dakilio.core.data.di

import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton
import pl.dakil.dakilio.core.data.repository.BackupRepository
import pl.dakil.dakilio.core.data.repository.DaylioImportRepository
import pl.dakil.dakilio.core.data.repository.LocalBackupRepository
import pl.dakil.dakilio.core.data.repository.DiaryRepository
import pl.dakil.dakilio.core.data.repository.HabitRepository
import pl.dakil.dakilio.core.data.repository.KeywordRepository
import pl.dakil.dakilio.core.data.repository.LocalKeywordRepository
import pl.dakil.dakilio.core.data.repository.LocalHabitRepository
import pl.dakil.dakilio.core.data.repository.LocalDaylioImportRepository
import pl.dakil.dakilio.core.data.repository.LocalDiaryRepository
import pl.dakil.dakilio.core.data.repository.LocalMoodRepository
import pl.dakil.dakilio.core.data.repository.LocalOnboardingRepository
import pl.dakil.dakilio.core.data.repository.LocalPhotoRepository
import pl.dakil.dakilio.core.data.repository.LocalPostRepository
import pl.dakil.dakilio.core.data.repository.LocalSearchHistoryRepository
import pl.dakil.dakilio.core.data.repository.LocalSettingsRepository
import pl.dakil.dakilio.core.data.repository.LocalTagRepository
import pl.dakil.dakilio.core.data.repository.MoodRepository
import pl.dakil.dakilio.core.data.repository.OnboardingRepository
import pl.dakil.dakilio.core.data.repository.PhotoRepository
import pl.dakil.dakilio.core.data.repository.PostRepository
import pl.dakil.dakilio.core.data.repository.SearchHistoryRepository
import pl.dakil.dakilio.core.data.repository.SettingsRepository
import pl.dakil.dakilio.core.data.repository.TagRepository

@Module
@InstallIn(SingletonComponent::class)
internal interface DataModule {
    @Binds @Singleton fun moods(impl: LocalMoodRepository): MoodRepository

    @Binds @Singleton fun diaries(impl: LocalDiaryRepository): DiaryRepository

    @Binds @Singleton fun tags(impl: LocalTagRepository): TagRepository

    @Binds @Singleton fun posts(impl: LocalPostRepository): PostRepository

    @Binds @Singleton fun photos(impl: LocalPhotoRepository): PhotoRepository

    @Binds @Singleton fun settings(impl: LocalSettingsRepository): SettingsRepository

    @Binds @Singleton fun searchHistory(impl: LocalSearchHistoryRepository): SearchHistoryRepository

    @Binds @Singleton fun habits(impl: LocalHabitRepository): HabitRepository

    @Binds @Singleton fun keywords(impl: LocalKeywordRepository): KeywordRepository

    @Binds @Singleton fun onboarding(impl: LocalOnboardingRepository): OnboardingRepository

    /** Unscoped: it holds the opened file, so every screen gets its own. */
    @Binds fun daylioImport(impl: LocalDaylioImportRepository): DaylioImportRepository

    /** Unscoped for the same reason. */
    @Binds fun backup(impl: LocalBackupRepository): BackupRepository
}
