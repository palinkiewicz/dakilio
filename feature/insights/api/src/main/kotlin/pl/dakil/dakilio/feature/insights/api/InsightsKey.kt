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
