package pl.dakil.dakilio.core.model

import java.time.YearMonth

/** A month at a glance, for the month pickers. [averageLevel] is `null` when no entry has a mood. */
data class MonthSummary(val entries: Int, val averageLevel: Int?)

/** Per-month entry counts and average mood level, for the posts shown under [selectedDiaryId] (`null` = all). */
fun monthSummaries(posts: List<Post>, selectedDiaryId: Long?): Map<YearMonth, MonthSummary> =
    posts.asSequence()
        .filter { selectedDiaryId == null || it.diaryId == selectedDiaryId }
        .groupBy { YearMonth.from(it.date) }
        .mapValues { (_, monthPosts) ->
            val levels = monthPosts.mapNotNull { it.mood?.level }
            MonthSummary(
                entries = monthPosts.size,
                averageLevel = if (levels.isEmpty()) null else Math.round(levels.average()).toInt(),
            )
        }
