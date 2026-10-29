package pl.dakil.dakilio.feature.search.api

import androidx.navigation3.runtime.NavKey
import kotlinx.serialization.Serializable

@Serializable
data object SearchKey : NavKey {
    /** Shared-element key: the search screen grows out of the timeline's search button. */
    const val SHARED_ELEMENT_KEY = "search"
}
