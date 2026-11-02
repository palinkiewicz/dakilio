package pl.dakil.dakilio.core.samples

import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import java.time.LocalTime
import javax.inject.Inject
import pl.dakil.dakilio.core.database.DefaultData.SamplePost
import pl.dakil.dakilio.core.database.DefaultData.SeedTag
import pl.dakil.dakilio.core.database.SampleData

/** Sample entries that show off every kind of entry and timeline row on a fresh install. */
internal class DakilioSampleData @Inject constructor() : SampleData {
    // Today is left empty on purpose (the timeline shows its check-in card), as are 3 days ago
    // (a single "No entries" day) and 7–10 days ago (a collapsed quiet stretch).
    override val posts: List<SamplePost> = listOf(
        SamplePost(
            1, LocalTime.of(19, 40), moodLevel = 5, R.string.sample_hike_title, R.string.sample_hike_note,
            listOf(SeedTag.Hiking, SeedTag.Nature, SeedTag.Friends, SeedTag.Grateful),
            photos = listOf("sample_ridge_sunset.jpg"),
        ),
        SamplePost(
            1, LocalTime.of(8, 10), moodLevel = 4, R.string.sample_morning_title, R.string.sample_morning_note,
            listOf(SeedTag.Reading, SeedTag.Calm),
        ),
        SamplePost(
            2, LocalTime.of(18, 25), moodLevel = 3, note = R.string.sample_meetings_note,
            tags = listOf(SeedTag.Work, SeedTag.Tired, SeedTag.Cloudy),
        ),
        SamplePost(
            2, LocalTime.of(7, 45), moodLevel = 4, R.string.sample_walk_title,
            tags = listOf(SeedTag.Walk, SeedTag.Nature, SeedTag.Sunny),
        ),
        SamplePost(
            4, LocalTime.of(20, 15), moodLevel = 4, R.string.sample_pierogi_title, R.string.sample_pierogi_note,
            listOf(SeedTag.Cooking, SeedTag.Family, SeedTag.Happy),
        ),
        // Tags only: no mood, no text.
        SamplePost(4, LocalTime.of(13, 5), moodLevel = null, tags = listOf(SeedTag.Friends, SeedTag.Gaming, SeedTag.Music)),
        SamplePost(
            5, LocalTime.of(17, 50), moodLevel = 2, R.string.sample_deadline_title, R.string.sample_deadline_note,
            listOf(SeedTag.Stressed, SeedTag.Work, SeedTag.Rain),
        ),
        // Text only: no mood, no tags.
        SamplePost(5, LocalTime.of(22, 5), moodLevel = null, R.string.sample_ideas_title, R.string.sample_ideas_note),
        SamplePost(
            6, LocalTime.of(16, 30), moodLevel = 5, R.string.sample_city_title, R.string.sample_city_note,
            listOf(SeedTag.Travel, SeedTag.NewPlace, SeedTag.Excited),
            photos = listOf("sample_old_town_evening.jpg", "sample_old_town_alley.jpg"),
        ),
        // Mood only.
        SamplePost(11, LocalTime.of(7, 30), moodLevel = 3),
        SamplePost(
            11, LocalTime.of(18, 40), moodLevel = 4, R.string.sample_run_title,
            tags = listOf(SeedTag.Exercise, SeedTag.Sunny),
        ),
        SamplePost(
            12, LocalTime.of(21, 0), moodLevel = 1, R.string.sample_cold_title, R.string.sample_cold_note,
            listOf(SeedTag.Sick, SeedTag.Alone),
        ),
    )
}

@Module
@InstallIn(SingletonComponent::class)
internal interface SamplesModule {
    @Binds fun sampleData(impl: DakilioSampleData): SampleData
}
