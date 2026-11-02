package pl.dakil.dakilio.feature.intro.impl

import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.navigation3.runtime.EntryProviderScope
import androidx.navigation3.runtime.NavKey
import pl.dakil.dakilio.feature.importer.api.DaylioImportKey
import pl.dakil.dakilio.feature.intro.api.IntroDaylioKey
import pl.dakil.dakilio.feature.intro.api.IntroGroupsKey
import pl.dakil.dakilio.feature.intro.api.IntroLanguageKey

/**
 * The first-run intro: language → Daylio (only when installed) → tag groups. The app hosts it in
 * its own back stack, next to the import screen ([DaylioImportKey]), and swaps to the normal shell
 * once [onFinished] runs.
 */
fun EntryProviderScope<NavKey>.introEntries(
    onNavigate: (NavKey) -> Unit,
    onBack: () -> Unit,
    onFinished: () -> Unit,
) {
    entry<IntroLanguageKey> {
        LanguageStep(onContinue = { daylioInstalled -> onNavigate(if (daylioInstalled) IntroDaylioKey else IntroGroupsKey) })
    }
    entry<IntroDaylioKey> {
        DaylioStep(onBack = onBack, onPickFile = { onNavigate(DaylioImportKey) }, onStartFresh = { onNavigate(IntroGroupsKey) })
    }
    entry<IntroGroupsKey> {
        GroupsStep(viewModel = hiltViewModel(), onBack = onBack, onFinished = onFinished)
    }
}
