package pl.dakil.dakilio.feature.intro.api

import androidx.navigation3.runtime.NavKey
import kotlinx.serialization.Serializable

/** First step of the first-run intro: the app's language. */
@Serializable
data object IntroLanguageKey : NavKey

/** Bring a journal along (a Dakilio backup or a Daylio one), or start fresh. */
@Serializable
data object IntroImportKey : NavKey

/** Pick which ready-made tag groups to keep. */
@Serializable
data object IntroGroupsKey : NavKey
