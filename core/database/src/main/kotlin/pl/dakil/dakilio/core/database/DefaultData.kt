package pl.dakil.dakilio.core.database

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import androidx.annotation.StringRes
import androidx.sqlite.db.SupportSQLiteDatabase
import java.io.File
import java.time.Clock
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import pl.dakil.dakilio.core.model.Mood

/**
 * Everything a fresh install starts with: the 5-grade mood scale, the "Your Journey" diary, a set of
 * tag groups, and a handful of sample entries (with photos) so the app can be shown off right away.
 *
 * Runs inside Room's creation transaction, before the first query returns, with fixed ids so the
 * sample entries can reference tags and so [SAMPLE_POST_IDS] can find them again for removal.
 * SQLite AUTOINCREMENT never reuses ids, so the user's own entries can't collide with that range.
 */
object DefaultData {
    const val MOOD_SET_ID = 1L
    const val DEFAULT_DIARY_ID = 1L
    val SAMPLE_POST_IDS: LongRange = 1L..12L

    private const val SAMPLE_ASSET_DIR = "sample"

    private class SeedMood(val tier: Int, @StringRes val label: Int, val iconKey: String, val color: Int)

    // Diverging scale (warm = heavy, cool = light) with a calm periwinkle middle. Light-mode mark colors,
    // validated for CVD and normal-vision separation of neighbours; dark tones are derived at runtime.
    private val moods = listOf(
        SeedMood(1, R.string.seed_mood_awful, "mood_awful", 0xFFBD1F44.toInt()),
        SeedMood(2, R.string.seed_mood_bad, "mood_bad", 0xFFE98420.toInt()),
        SeedMood(3, R.string.seed_mood_okay, "mood_okay", 0xFF7A6BC1.toInt()),
        SeedMood(4, R.string.seed_mood_good, "mood_good", 0xFF6BC456.toInt()),
        SeedMood(5, R.string.seed_mood_great, "mood_great", 0xFF009298.toInt()),
    )

    private enum class Group(@StringRes val label: Int) {
        Emotions(R.string.seed_group_emotions),
        People(R.string.seed_group_people),
        Activities(R.string.seed_group_activities),
        Health(R.string.seed_group_health),
        Journey(R.string.seed_group_journey),
        Weather(R.string.seed_group_weather),
        ;

        val id: Long get() = ordinal + 1L
    }

    private enum class SeedTag(val group: Group, @StringRes val label: Int, val iconKey: String) {
        Happy(Group.Emotions, R.string.seed_tag_happy, "sentiment_very_satisfied"),
        Grateful(Group.Emotions, R.string.seed_tag_grateful, "volunteer_activism"),
        Calm(Group.Emotions, R.string.seed_tag_calm, "spa"),
        Excited(Group.Emotions, R.string.seed_tag_excited, "celebration"),
        Tired(Group.Emotions, R.string.seed_tag_tired, "bedtime"),
        Anxious(Group.Emotions, R.string.seed_tag_anxious, "sentiment_worried"),
        Stressed(Group.Emotions, R.string.seed_tag_stressed, "sentiment_stressed"),
        Sad(Group.Emotions, R.string.seed_tag_sad, "sentiment_sad"),
        Family(Group.People, R.string.seed_tag_family, "family_restroom"),
        Friends(Group.People, R.string.seed_tag_friends, "groups"),
        Partner(Group.People, R.string.seed_tag_partner, "favorite"),
        Colleagues(Group.People, R.string.seed_tag_colleagues, "handshake"),
        Pets(Group.People, R.string.seed_tag_pets, "pets"),
        Alone(Group.People, R.string.seed_tag_alone, "person"),
        Work(Group.Activities, R.string.seed_tag_work, "work"),
        Study(Group.Activities, R.string.seed_tag_study, "school"),
        Reading(Group.Activities, R.string.seed_tag_reading, "menu_book"),
        Gaming(Group.Activities, R.string.seed_tag_gaming, "sports_esports"),
        Movies(Group.Activities, R.string.seed_tag_movies, "movie"),
        Music(Group.Activities, R.string.seed_tag_music, "music_note"),
        Cooking(Group.Activities, R.string.seed_tag_cooking, "skillet"),
        Shopping(Group.Activities, R.string.seed_tag_shopping, "shopping_bag"),
        Cleaning(Group.Activities, R.string.seed_tag_cleaning, "cleaning_services"),
        Exercise(Group.Health, R.string.seed_tag_exercise, "fitness_center"),
        Walk(Group.Health, R.string.seed_tag_walk, "directions_walk"),
        AteHealthy(Group.Health, R.string.seed_tag_ate_healthy, "nutrition"),
        Water(Group.Health, R.string.seed_tag_water, "water_drop"),
        Meditation(Group.Health, R.string.seed_tag_meditation, "self_improvement"),
        GoodSleep(Group.Health, R.string.seed_tag_good_sleep, "hotel"),
        Sick(Group.Health, R.string.seed_tag_sick, "sick"),
        Nature(Group.Journey, R.string.seed_tag_nature, "forest"),
        Travel(Group.Journey, R.string.seed_tag_travel, "flight"),
        Hiking(Group.Journey, R.string.seed_tag_hiking, "hiking"),
        Beach(Group.Journey, R.string.seed_tag_beach, "beach_access"),
        RoadTrip(Group.Journey, R.string.seed_tag_road_trip, "directions_car"),
        NewPlace(Group.Journey, R.string.seed_tag_new_place, "location_on"),
        Sunny(Group.Weather, R.string.seed_tag_sunny, "sunny"),
        Cloudy(Group.Weather, R.string.seed_tag_cloudy, "cloud"),
        Rain(Group.Weather, R.string.seed_tag_rain, "rainy"),
        Snow(Group.Weather, R.string.seed_tag_snow, "weather_snowy"),
        Windy(Group.Weather, R.string.seed_tag_windy, "air"),
        ;

        val id: Long get() = ordinal + 1L
    }

    private class SamplePost(
        val daysAgo: Long,
        val time: LocalTime,
        val moodTier: Int?,
        @StringRes val title: Int? = null,
        @StringRes val note: Int? = null,
        val tags: List<SeedTag> = emptyList(),
        val photos: List<String> = emptyList(),
    )

    // Today is left empty on purpose (the timeline shows its check-in card), as are 3 days ago
    // (a single "No entries" day) and 7–10 days ago (a collapsed quiet stretch).
    private val samplePosts = listOf(
        SamplePost(
            1, LocalTime.of(19, 40), moodTier = 5, R.string.sample_hike_title, R.string.sample_hike_note,
            listOf(SeedTag.Hiking, SeedTag.Nature, SeedTag.Friends, SeedTag.Grateful),
            photos = listOf("sample_ridge_sunset.jpg"),
        ),
        SamplePost(
            1, LocalTime.of(8, 10), moodTier = 4, R.string.sample_morning_title, R.string.sample_morning_note,
            listOf(SeedTag.Reading, SeedTag.Calm),
        ),
        SamplePost(
            2, LocalTime.of(18, 25), moodTier = 3, note = R.string.sample_meetings_note,
            tags = listOf(SeedTag.Work, SeedTag.Tired, SeedTag.Cloudy),
        ),
        SamplePost(
            2, LocalTime.of(7, 45), moodTier = 4, R.string.sample_walk_title,
            tags = listOf(SeedTag.Walk, SeedTag.Nature, SeedTag.Sunny),
        ),
        SamplePost(
            4, LocalTime.of(20, 15), moodTier = 4, R.string.sample_pierogi_title, R.string.sample_pierogi_note,
            listOf(SeedTag.Cooking, SeedTag.Family, SeedTag.Happy),
        ),
        // Tags only: no mood, no text.
        SamplePost(4, LocalTime.of(13, 5), moodTier = null, tags = listOf(SeedTag.Friends, SeedTag.Gaming, SeedTag.Music)),
        SamplePost(
            5, LocalTime.of(17, 50), moodTier = 2, R.string.sample_deadline_title, R.string.sample_deadline_note,
            listOf(SeedTag.Stressed, SeedTag.Work, SeedTag.Rain),
        ),
        // Text only: no mood, no tags.
        SamplePost(5, LocalTime.of(22, 5), moodTier = null, R.string.sample_ideas_title, R.string.sample_ideas_note),
        SamplePost(
            6, LocalTime.of(16, 30), moodTier = 5, R.string.sample_city_title, R.string.sample_city_note,
            listOf(SeedTag.Travel, SeedTag.NewPlace, SeedTag.Excited),
            photos = listOf("sample_old_town_evening.jpg", "sample_old_town_alley.jpg"),
        ),
        // Mood only.
        SamplePost(11, LocalTime.of(7, 30), moodTier = 3),
        SamplePost(
            11, LocalTime.of(18, 40), moodTier = 4, R.string.sample_run_title,
            tags = listOf(SeedTag.Exercise, SeedTag.Sunny),
        ),
        SamplePost(
            12, LocalTime.of(21, 0), moodTier = 1, R.string.sample_cold_title, R.string.sample_cold_note,
            listOf(SeedTag.Sick, SeedTag.Alone),
        ),
    )

    fun seed(db: SupportSQLiteDatabase, context: Context, photoDirectory: File, clock: Clock) {
        val now = clock.millis()
        db.insertRow(
            "mood_set",
            "id" to MOOD_SET_ID,
            "scale_size" to moods.size,
            "created_at" to now,
            "is_active" to 1,
        )
        moods.forEach { mood ->
            db.insertRow(
                "mood",
                "id" to mood.tier.toLong(),
                "mood_set_id" to MOOD_SET_ID,
                "tier" to mood.tier,
                "value" to Mood.normalize(mood.tier, moods.size),
                "label" to context.getString(mood.label),
                "icon_key" to mood.iconKey,
                "color_argb" to mood.color,
            )
        }

        db.insertRow(
            "diary",
            "id" to DEFAULT_DIARY_ID,
            "name" to context.getString(R.string.seed_diary_journey),
            "icon_key" to "explore",
            "position" to 0,
            "created_at" to now,
        )
        Group.entries.forEach { group ->
            db.insertRow("tag_group", "id" to group.id, "name" to context.getString(group.label), "position" to group.ordinal, "is_archived" to 0)
            db.insertRow("diary_suggested_group", "diary_id" to DEFAULT_DIARY_ID, "group_id" to group.id)
        }
        SeedTag.entries.groupBy { it.group }.forEach { (_, tags) ->
            tags.forEachIndexed { position, tag ->
                db.insertRow(
                    "tag",
                    "id" to tag.id,
                    "group_id" to tag.group.id,
                    "label" to context.getString(tag.label),
                    "icon_key" to tag.iconKey,
                    "position" to position,
                    "is_archived" to 0,
                )
            }
        }

        seedSamplePosts(db, context, photoDirectory, clock, now)
    }

    private fun seedSamplePosts(db: SupportSQLiteDatabase, context: Context, photoDirectory: File, clock: Clock, now: Long) {
        val zone: ZoneId = clock.zone
        val today = LocalDate.now(clock)
        photoDirectory.mkdirs()
        samplePosts.forEachIndexed { index, sample ->
            val postId = SAMPLE_POST_IDS.first + index
            val dateTime = today.minusDays(sample.daysAgo).atTime(sample.time)
            val offset = zone.rules.getOffset(dateTime)
            db.insertRow(
                "post",
                "id" to postId,
                "timestamp" to dateTime.toInstant(offset).toEpochMilli(),
                "utc_offset_min" to offset.totalSeconds / 60,
                "local_date" to dateTime.toLocalDate().toEpochDay(),
                "mood_id" to sample.moodTier?.toLong(),
                "title" to sample.title?.let(context::getString),
                "note" to sample.note?.let(context::getString),
                "created_at" to now,
                "updated_at" to now,
            )
            db.insertRow("post_diary", "post_id" to postId, "diary_id" to DEFAULT_DIARY_ID)
            sample.tags.forEach { tag -> db.insertRow("post_tag", "post_id" to postId, "tag_id" to tag.id) }
            sample.photos.forEachIndexed { position, fileName ->
                val copied = runCatching {
                    context.assets.open("$SAMPLE_ASSET_DIR/$fileName").use { input ->
                        File(photoDirectory, fileName).outputStream().use { input.copyTo(it) }
                    }
                }.isSuccess
                if (copied) {
                    db.insertRow("post_photo", "post_id" to postId, "file_name" to fileName, "position" to position)
                }
            }
        }
    }

    private fun SupportSQLiteDatabase.insertRow(table: String, vararg values: Pair<String, Any?>) {
        val content = ContentValues(values.size)
        values.forEach { (column, value) ->
            when (value) {
                null -> content.putNull(column)
                is Long -> content.put(column, value)
                is Int -> content.put(column, value)
                is Float -> content.put(column, value)
                is String -> content.put(column, value)
                else -> error("Unsupported seed value for $table.$column: $value")
            }
        }
        insert(table, SQLiteDatabase.CONFLICT_ABORT, content)
    }
}
