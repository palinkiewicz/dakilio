package pl.dakil.dakilio.feature.insights.api

import androidx.navigation3.runtime.NavKey
import kotlinx.serialization.Serializable

/** Every milestone ("stamp") and how far the next ones are; opened from Insights. */
@Serializable
data object MilestonesKey : NavKey
