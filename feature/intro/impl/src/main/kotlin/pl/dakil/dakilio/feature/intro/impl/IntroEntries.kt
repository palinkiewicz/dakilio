package pl.dakil.dakilio.feature.intro.impl

import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.navigation3.runtime.EntryProviderScope
import androidx.navigation3.runtime.NavKey
import pl.dakil.dakilio.feature.intro.api.IntroImportKey
import pl.dakil.dakilio.feature.intro.api.IntroGroupsKey
import pl.dakil.dakilio.feature.intro.api.IntroLanguageKey

/**
 * The first-run intro: language → import (a Dakilio or Daylio backup) → tag groups. The app hosts it
 * in its own back stack, next to the Daylio import and backup screens, and swaps to the normal shell
 * once [onFinished] runs.
 */
fun EntryProviderScope<NavKey>.introEntries(
    onNavigate: (NavKey) -> Unit,
    onBack: () -> Unit,
    onFinished: () -> Unit,
) {
    entry<IntroLanguageKey> {
        LanguageStep(onContinue = { onNavigate(IntroImportKey) })
    }
    entry<IntroImportKey> {
        // A picked file is routed by its contents, like a shared one: the app's intro flow then opens
        // the backup or the Daylio import screen for it.
        ImportStep(viewModel = hiltViewModel(), onBack = onBack, onStartFresh = { onNavigate(IntroGroupsKey) })
    }
    entry<IntroGroupsKey> {
        GroupsStep(viewModel = hiltViewModel(), onBack = onBack, onFinished = onFinished)
    }
}
