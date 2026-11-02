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
import pl.dakil.dakilio.core.database.entity.DiaryEntity
import pl.dakil.dakilio.core.model.SuggestedGroup
import pl.dakil.dakilio.core.model.SuggestedTag

/**
 * Everything a fresh install starts with: the 5 primary moods, the "Your Journey" diary and a set of
 * tag groups. Debug and preview builds add a handful of sample entries (with photos) from
 * core:samples, so the app can be shown off right away; release builds ship without them.
 *
 * The tag groups double as the suggestions of [suggestedGroups]: the first-run intro deletes the
 * ones the user didn't pick (by their fixed ids, [seedGroupId]), and the Tags screen can add any of
 * them again later. Nothing here may need the database before seeding, so the intro can show the
 * suggestions while the database doesn't exist yet.
 *
 * Runs inside Room's creation transaction, before the first query returns, with fixed ids so the
 * sample entries can reference tags and so [SAMPLE_POST_IDS] can find them again for removal.
 * SQLite AUTOINCREMENT never reuses ids, and the range is reserved even without samples, so the
 * user's own entries can't collide with it.
 */
object DefaultData {
    const val DEFAULT_DIARY_ID = 1L
    val SAMPLE_POST_IDS: LongRange = 1L..12L

    /** Where core:samples ships the photos of [SamplePost.photos]. */
    private const val SAMPLE_ASSET_DIR = "sample"
    private const val DEFAULT_DIARY_ICON = "explore"

    /** The default name of the [level]'s primary mood. */
    fun primaryMoodLabel(context: Context, level: Int): String = context.getString(primaryMoods[level - 1])

    /** The group for imported activities that come without one. */
    fun importFallbackGroupName(context: Context): String = context.getString(R.string.import_group_daylio)

    /** The "Your Journey" diary a fresh install (and a Daylio import) starts with. */
    fun defaultDiary(context: Context, now: Long): DiaryEntity = DiaryEntity(
        name = context.getString(R.string.seed_diary_journey),
        iconKey = DEFAULT_DIARY_ICON,
        position = 0,
        createdAt = now,
    )

    /** Every seed tag group, localized, in seed order. Pure resource lookups: no database access. */
    fun suggestedGroups(context: Context): List<SuggestedGroup> = Group.entries.map { group ->
        SuggestedGroup(
            key = group.name,
            name = context.getString(group.label),
            tags = SeedTag.entries.filter { it.group == group }.map { SuggestedTag(context.getString(it.label), it.iconKey) },
            colorKey = group.colorKey,
        )
    }

    /** The fixed id the seed gives the group with this [SuggestedGroup.key], or null for an unknown key. */
    fun seedGroupId(key: String): Long? = Group.entries.firstOrNull { it.name == key }?.id

    /** The primary mood of each level, worst first. Its id equals its level ([SamplePost.moodLevel] relies on it). */
    private val primaryMoods = listOf(
        R.string.seed_mood_awful,
        R.string.seed_mood_bad,
        R.string.seed_mood_okay,
        R.string.seed_mood_good,
        R.string.seed_mood_great,
    )

    /**
     * @property colorKey the default color of the group's tags, a `TagColor` key (core:ui). Each fits
     *   the group's meaning, and together they're the most distinct six of the palette.
     */
    enum class Group(@StringRes val label: Int, val colorKey: String) {
        Emotions(R.string.seed_group_emotions, "pink"), // the heart
        People(R.string.seed_group_people, "orange"), // warmth of company
        Activities(R.string.seed_group_activities, "violet"), // play and creativity
        Health(R.string.seed_group_health, "green"), // wellness
        Journey(R.string.seed_group_journey, "brown"), // earth, trails and roads
        Weather(R.string.seed_group_weather, "sky"), // the sky
        ;

        val id: Long get() = ordinal + 1L
    }

    enum class SeedTag(val group: Group, @StringRes val label: Int, val iconKey: String) {
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

    /**
     * A sample entry of debug and preview builds, [daysAgo] days before the install.
     *
     * @property title,note string resources of the module that provides the samples.
     * @property photos file names under the `sample` assets directory.
     */
    class SamplePost(
        val daysAgo: Long,
        val time: LocalTime,
        val moodLevel: Int?,
        @StringRes val title: Int? = null,
        @StringRes val note: Int? = null,
        val tags: List<SeedTag> = emptyList(),
        val photos: List<String> = emptyList(),
    )

    fun seed(
        db: SupportSQLiteDatabase,
        context: Context,
        photoDirectory: File,
        clock: Clock,
        samples: List<SamplePost>,
    ) {
        val now = clock.millis()
        primaryMoods.forEachIndexed { index, label ->
            val level = index + 1
            db.insertRow(
                "mood",
                "id" to level.toLong(),
                "level" to level,
                "label" to context.getString(label),
                "is_primary" to 1,
                "position" to 0,
                "is_archived" to 0,
            )
        }

        db.insertRow(
            "diary",
            "id" to DEFAULT_DIARY_ID,
            "name" to context.getString(R.string.seed_diary_journey),
            "icon_key" to DEFAULT_DIARY_ICON,
            "position" to 0,
            "created_at" to now,
        )
        Group.entries.forEach { group ->
            db.insertRow(
                "tag_group",
                "id" to group.id,
                "name" to context.getString(group.label),
                "position" to group.ordinal,
                "is_archived" to 0,
                "color_key" to group.colorKey,
            )
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

        seedSamplePosts(db, context, photoDirectory, clock, now, samples)
        reserveSamplePostIds(db)
    }

    private fun seedSamplePosts(
        db: SupportSQLiteDatabase,
        context: Context,
        photoDirectory: File,
        clock: Clock,
        now: Long,
        samples: List<SamplePost>,
    ) {
        require(samples.size <= SAMPLE_POST_IDS.count()) { "More samples than reserved ids" }
        if (samples.isEmpty()) return
        val zone: ZoneId = clock.zone
        val today = LocalDate.now(clock)
        photoDirectory.mkdirs()
        samples.forEachIndexed { index, sample ->
            val postId = SAMPLE_POST_IDS.first + index
            val dateTime = today.minusDays(sample.daysAgo).atTime(sample.time)
            val offset = zone.rules.getOffset(dateTime)
            db.insertRow(
                "post",
                "id" to postId,
                "timestamp" to dateTime.toInstant(offset).toEpochMilli(),
                "utc_offset_min" to offset.totalSeconds / 60,
                "local_date" to dateTime.toLocalDate().toEpochDay(),
                "mood_id" to sample.moodLevel?.toLong(),
                "diary_id" to DEFAULT_DIARY_ID,
                "title" to sample.title?.let(context::getString),
                "note" to sample.note?.let(context::getString),
                "created_at" to now,
                "updated_at" to now,
            )
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

    /**
     * Moves the post id sequence past [SAMPLE_POST_IDS] when fewer samples were seeded (none at all in
     * release builds), so the user's first entries aren't counted as samples.
     */
    private fun reserveSamplePostIds(db: SupportSQLiteDatabase) {
        val last = SAMPLE_POST_IDS.last
        val updated = db.compileStatement("UPDATE sqlite_sequence SET seq = max(seq, $last) WHERE name = 'post'")
            .use { it.executeUpdateDelete() }
        if (updated == 0) db.insertRow("sqlite_sequence", "name" to "post", "seq" to last)
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
