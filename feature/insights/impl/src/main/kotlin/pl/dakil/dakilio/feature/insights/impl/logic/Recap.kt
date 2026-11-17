package pl.dakil.dakilio.feature.insights.impl.logic

import java.time.LocalDate
import java.time.YearMonth
import kotlin.math.abs
import kotlin.math.roundToInt
import pl.dakil.dakilio.core.model.DateRange
import pl.dakil.dakilio.core.model.MonthSummary
import pl.dakil.dakilio.core.model.Post
import pl.dakil.dakilio.core.model.PostPhoto

/** The pages a recap can have, in the order they're shown. */
enum class RecapPage { Title, Numbers, Mood, Days, Change, BrightestDay, Photos, Tags, NewTags, Rhythm, Habits, Records, End }

/**
 * A recap's headline for its period, the first that holds. None is about a low or falling mood, so a
 * heavier period gets a neutral one.
 */
enum class RecapTitle { Bright, Climbing, Faithful, Steady, ManyShades, Yours }

/** A photo in a recap, with the entry it belongs to. */
data class RecapPhoto(val postId: Long, val date: LocalDate, val photo: PostPhoto)

/** The top tags are counted down, with the first revealed on a tap, from this many tags. */
const val RECAP_COUNTDOWN_FROM = 3

/** A period earns a recap from this many entries. */
const val RECAP_MIN_ENTRIES = 5

/** Pages that need a mood need this many days with one, so they aren't built on a day or two. */
private const val MIN_MOOD_DAYS = 3

/** The photos page needs this many photos. */
const val RECAP_MIN_PHOTOS = 3

/** At most this many photos are shown. */
const val RECAP_MAX_PHOTOS = 9

/** A tag counts as new only when used on this many days of the period. */
private const val NEW_TAG_MIN_DAYS = 2

/** An average from this level up makes a bright period. */
private const val BRIGHT_FROM = 4f

/** A rise of this many levels against the period before is a climb. */
private const val CLIMB_FROM = 0.3f

/** Journaling on this share of the period's days is faithful. */
private const val FAITHFUL_SHARE = 0.8f

/** Words on a printed book page, for "about N pages of a book". */
private const val WORDS_PER_PAGE = 275

/** At most this many new tags are shown. */
private const val MAX_NEW_TAGS = 6

/**
 * Everything a recap shows.
 *
 * @property brightest the brightest day's most telling entry (the one with the best mood, then a
 *   photo, then the most words), `null` without enough days with a mood.
 * @property months each month of a year with its summary (`null` without entries); empty otherwise.
 * @property photos up to [RECAP_MAX_PHOTOS] photos spread evenly through the period.
 * @property newTags tags first used in this period (never in the user's first period).
 * @property bookPages the words written, as pages of a book.
 */
data class Recap(
    val overview: Overview,
    val pages: List<RecapPage>,
    val title: RecapTitle,
    val brightest: Post?,
    val brightestMood: Float?,
    val months: List<Pair<YearMonth, MonthSummary?>>,
    val photos: List<RecapPhoto> = emptyList(),
    val photoCount: Int = 0,
    val newTags: List<TagStat> = emptyList(),
    val bookPages: Int = 0,
)

fun recap(overview: Overview, index: InsightsIndex, period: InsightsPeriod, months: Map<YearMonth, MonthSummary>): Recap {
    val days = index.days(overview.range)
    val moodDays = days.count { it.mood != null }
    val best = if (moodDays >= MIN_MOOD_DAYS) brightestDays(days, 1).firstOrNull() else null
    val bestPost = best?.let { day ->
        index.day(day.date)?.posts?.sortedWith(
            compareByDescending<Post> { it.mood?.level ?: 0 }.thenByDescending { it.photos.isNotEmpty() }.thenByDescending { it.wordCount() },
        )?.firstOrNull()
    }
    val yearMonths = (period as? InsightsPeriod.Year)?.let { year -> (1..12).map { YearMonth.of(year.year, it) to months[YearMonth.of(year.year, it)] } }.orEmpty()
    val allPhotos = days.flatMap { day -> day.posts.flatMap { post -> post.photos.map { RecapPhoto(post.id, post.date, it) } } }
    val photos = spreadEvenly(allPhotos, RECAP_MAX_PHOTOS)
    val newTags = newTags(index, overview.range, overview.tags)
    val showsDays = when (period) {
        is InsightsPeriod.Month -> moodDays >= MIN_MOOD_DAYS
        is InsightsPeriod.Year -> yearMonths.count { it.second != null } >= 2
        else -> false
    }
    val pages = buildList {
        add(RecapPage.Title)
        add(RecapPage.Numbers)
        if (moodDays >= MIN_MOOD_DAYS) add(RecapPage.Mood)
        if (showsDays) add(RecapPage.Days)
        // A shift of a tenth reads as "the same"; only one worth a page gets one.
        if (overview.moodChange?.let { abs(it) >= NOTABLE_LIFT } == true) add(RecapPage.Change)
        if (bestPost != null) add(RecapPage.BrightestDay)
        if (allPhotos.size >= RECAP_MIN_PHOTOS) add(RecapPage.Photos)
        if (overview.tags.isNotEmpty()) add(RecapPage.Tags)
        if (newTags.isNotEmpty()) add(RecapPage.NewTags)
        if (overview.rhythm.bestWeekday != null || (overview.entries >= 7 && overview.rhythm.busiestDayPart != null)) add(RecapPage.Rhythm)
        if (overview.habits.isNotEmpty()) add(RecapPage.Habits)
        if (period is InsightsPeriod.Year && overview.records != null) add(RecapPage.Records)
        add(RecapPage.End)
    }
    return Recap(
        overview = overview,
        pages = pages,
        title = recapTitle(overview),
        brightest = bestPost,
        brightestMood = best?.mood,
        months = yearMonths,
        photos = photos,
        photoCount = allPhotos.size,
        newTags = newTags,
        bookPages = bookPages(overview.writing.words),
    )
}

/** Whether [page] holds a reveal that the forward tap shows before the page moves on. */
fun Recap.hasReveal(page: RecapPage): Boolean = when (page) {
    RecapPage.Tags -> overview.tags.size >= RECAP_COUNTDOWN_FROM
    RecapPage.Rhythm -> overview.rhythm.bestWeekday != null
    else -> false
}

/** The period's headline: the first [RecapTitle] whose rule holds. */
fun recapTitle(overview: Overview): RecapTitle {
    val average = overview.mood.average
    val change = overview.moodChange
    return when {
        average != null && average >= BRIGHT_FROM -> RecapTitle.Bright
        change != null && change >= CLIMB_FROM -> RecapTitle.Climbing
        overview.range.days > 0 && overview.daysJournaled >= FAITHFUL_SHARE * overview.range.days -> RecapTitle.Faithful
        overview.steadiness == Steadiness.Steady -> RecapTitle.Steady
        overview.steadiness != null -> RecapTitle.ManyShades
        else -> RecapTitle.Yours
    }
}

/**
 * Of [tags] (the period's tag stats), those first used inside [range] and on at least
 * [NEW_TAG_MIN_DAYS] of its days, the most used first. Nothing when the period holds the very first
 * entry, since everything would be new.
 */
fun newTags(index: InsightsIndex, range: DateRange, tags: List<TagStat>): List<TagStat> {
    val first = index.first ?: return emptyList()
    if (first >= range.from) return emptyList()
    val seenBefore = HashSet<Long>()
    for (post in index.posts) {
        if (post.date >= range.from) break
        post.tags.forEach { seenBefore += it.id }
    }
    return tags
        .filter { it.tag.id !in seenBefore && it.days >= NEW_TAG_MIN_DAYS }
        .sortedByDescending { it.days }
        .take(MAX_NEW_TAGS)
}

/** [words] as pages of a printed book, rounded. */
fun bookPages(words: Int): Int = (words.toFloat() / WORDS_PER_PAGE).roundToInt()

/** At most [limit] of [items], evenly spaced from the first to the last. */
internal fun <T> spreadEvenly(items: List<T>, limit: Int): List<T> {
    if (items.size <= limit) return items
    if (limit <= 1) return items.take(limit)
    return (0 until limit).map { i -> items[(i * (items.size - 1).toFloat() / (limit - 1)).roundToInt()] }
}
