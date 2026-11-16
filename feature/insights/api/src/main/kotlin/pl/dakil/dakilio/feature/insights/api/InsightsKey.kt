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
