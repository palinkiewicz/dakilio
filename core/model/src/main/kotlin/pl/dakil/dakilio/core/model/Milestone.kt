package pl.dakil.dakilio.core.model

import java.time.LocalDate

/** What a milestone counts. Each kind has its own ladder of targets ([Milestones.targets]). */
enum class MilestoneKind {
    /** Days in a row with an entry. */
    Streak,

    /** Entries written. */
    Entries,

    /** Different days with an entry. */
    Days,

    /** Photos added to entries. */
    Photos,

    /** Words written in titles and notes. */
    Words,
}

/** A stamp to collect: [target] of whatever [kind] counts. */
data class Milestone(val kind: MilestoneKind, val target: Int)

/**
 * @property reachedOn the day of the entry that reached the milestone (by the entries' own dates,
 *   so it stays the same however often it's computed), or null while it's still ahead.
 */
data class MilestoneStatus(val milestone: Milestone, val reachedOn: LocalDate?) {
    val reached: Boolean get() = reachedOn != null
}

/**
 * One kind's ladder.
 *
 * @property current how far the user is towards the next stamp: the count so far, or for
 *   [MilestoneKind.Streak] the streak running right now (a stamp, once reached, stays reached).
 * @property statuses every stamp of the kind, lowest target first.
 */
data class MilestoneProgress(val kind: MilestoneKind, val current: Int, val statuses: List<MilestoneStatus>) {
    val reachedCount: Int get() = statuses.count { it.reached }

    /** The lowest stamp still ahead, or null when the whole ladder is collected. */
    val next: Milestone? get() = statuses.firstOrNull { !it.reached }?.milestone
}

/**
 * Milestones ("stamps") are derived from the entries alone: nothing is stored, so they survive a
 * backup, an import or a new phone, and deleting entries can take one back.
 */
object Milestones {
    val targets: Map<MilestoneKind, List<Int>> = mapOf(
        MilestoneKind.Streak to listOf(3, 7, 14, 30, 60, 100, 200, 365),
        MilestoneKind.Entries to listOf(1, 10, 25, 50, 100, 250, 500, 1000, 2000),
        MilestoneKind.Days to listOf(7, 30, 100, 200, 365, 730, 1000),
        MilestoneKind.Photos to listOf(1, 10, 50, 100, 500),
        MilestoneKind.Words to listOf(1_000, 5_000, 10_000, 25_000, 50_000, 100_000),
    )

    /** Every kind's ladder for [posts], in [MilestoneKind] order. */
    fun progress(posts: List<Post>, today: LocalDate): List<MilestoneProgress> {
        val ordered = posts.sortedWith(compareBy<Post> { it.dateTime }.thenBy { it.id })
        val days = ordered.map { it.date }.distinct()

        // Day by day: the run of consecutive days each day ends, and the first day each run length was seen.
        val streakReached = HashMap<Int, LocalDate>()
        var run = 0
        var previous: LocalDate? = null
        for (day in days) {
            run = if (previous != null && previous.plusDays(1) == day) run + 1 else 1
            streakReached.putIfAbsent(run, day)
            previous = day
        }

        fun ladder(kind: MilestoneKind, current: Int, reachedOn: (Int) -> LocalDate?) = MilestoneProgress(
            kind = kind,
            current = current,
            statuses = targets.getValue(kind).map { MilestoneStatus(Milestone(kind, it), reachedOn(it)) },
        )

        val photoDates = crossings(MilestoneKind.Photos, ordered) { it.photos.size }
        val wordDates = crossings(MilestoneKind.Words, ordered) { wordCount(it.title) + wordCount(it.note) }
        return listOf(
            ladder(MilestoneKind.Streak, Streaks.current(days.toSet(), today)) { streakReached[it] },
            ladder(MilestoneKind.Entries, ordered.size) { ordered.getOrNull(it - 1)?.date },
            ladder(MilestoneKind.Days, days.size) { days.getOrNull(it - 1) },
            ladder(MilestoneKind.Photos, ordered.sumOf { it.photos.size }, photoDates::get),
            ladder(MilestoneKind.Words, ordered.sumOf { wordCount(it.title) + wordCount(it.note) }, wordDates::get),
        )
    }

    /** The milestones [progress] has reached. */
    fun reached(progress: List<MilestoneProgress>): Set<Milestone> =
        progress.flatMap { ladder -> ladder.statuses.filter { it.reached }.map { it.milestone } }.toSet()

    /**
     * What to celebrate after a change: the milestones in [after] that weren't in [before], only the
     * highest of each kind (reaching 10 photos at once doesn't also announce the first one).
     */
    fun newlyReached(before: Set<Milestone>, after: Set<Milestone>): List<Milestone> =
        (after - before).groupBy { it.kind }.values.map { kind -> kind.maxBy { it.target } }.sortedBy { it.kind }

    /** Words in [text]: runs of non-whitespace. */
    fun wordCount(text: String?): Int {
        if (text.isNullOrBlank()) return 0
        var count = 0
        var inWord = false
        for (char in text) {
            if (char.isWhitespace()) {
                inWord = false
            } else if (!inWord) {
                inWord = true
                count++
            }
        }
        return count
    }

    /**
     * For the running total of [amount] over [ordered]: each of [kind]'s targets with the date of
     * the entry that took the total to it. Targets not reached yet are absent.
     */
    private fun crossings(kind: MilestoneKind, ordered: List<Post>, amount: (Post) -> Int): Map<Int, LocalDate> {
        val wanted = targets.getValue(kind)
        val dates = HashMap<Int, LocalDate>()
        var total = 0
        var index = 0
        for (post in ordered) {
            if (index == wanted.size) break
            total += amount(post)
            while (index < wanted.size && total >= wanted[index]) {
                dates[wanted[index]] = post.date
                index++
            }
        }
        return dates
    }
}
