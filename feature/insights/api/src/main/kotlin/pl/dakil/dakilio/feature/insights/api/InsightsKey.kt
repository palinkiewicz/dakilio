package pl.dakil.dakilio.feature.insights.api

import androidx.navigation3.runtime.NavKey
import kotlinx.serialization.Serializable

@Serializable
data object InsightsKey : NavKey

/**
 * What an Insights screen looks at, handed from one screen to the next. The period and the
 * comparison are short strings written and read by the feature itself (`month:20758`, `prev`);
 * `null` means its default (this month, the period before).
 */
@Serializable
data class InsightsScope(
    val period: String? = null,
    val diaryId: Long? = null,
    val comparison: String? = null,
)

/**
 * The entries behind a number: those of [scope]'s period (and diary) that match every filter given.
 *
 * @property weekday ISO day of the week, Monday = 1.
 * @property dayPart the name of a part of the day, as the feature writes it.
 */
@Serializable
data class InsightsEntriesKey(
    val scope: InsightsScope = InsightsScope(),
    val moodLevel: Int? = null,
    val tagId: Long? = null,
    val weekday: Int? = null,
    val dayPart: String? = null,
) : NavKey

/** Mood in depth: its flow in finer or coarser slices, the mix over time, steadiness and standout days. */
@Serializable
data class InsightsMoodKey(val scope: InsightsScope = InsightsScope()) : NavKey

/**
 * Every tag used in the period, to sort and filter.
 *
 * @property sort the name of the order to open with, as the feature writes it; `null` is most used.
 */
@Serializable
data class InsightsTagsKey(val scope: InsightsScope = InsightsScope(), val sort: String? = null) : NavKey

/** One tag in depth: how often, when, with what, and how its days felt. */
@Serializable
data class InsightsTagKey(val scope: InsightsScope = InsightsScope(), val tagId: Long) : NavKey

/** When entries are written and how they feel: weekdays against parts of the day, and the hours. */
@Serializable
data class InsightsRhythmKey(val scope: InsightsScope = InsightsScope()) : NavKey
