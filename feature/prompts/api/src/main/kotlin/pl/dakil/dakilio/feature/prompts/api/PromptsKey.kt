package pl.dakil.dakilio.feature.prompts.api

import androidx.navigation3.runtime.NavKey
import kotlinx.serialization.Serializable

/** More → Extras → Prompts: the questions the note step offers while a note is still empty. */
@Serializable
data object PromptsKey : NavKey
