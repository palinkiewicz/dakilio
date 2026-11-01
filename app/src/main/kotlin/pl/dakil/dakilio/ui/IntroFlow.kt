package pl.dakil.dakilio.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Modifier
import androidx.lifecycle.viewmodel.navigation3.rememberViewModelStoreNavEntryDecorator
import androidx.navigation3.runtime.NavKey
import androidx.navigation3.runtime.entryProvider
import androidx.navigation3.runtime.rememberNavBackStack
import androidx.navigation3.runtime.rememberSaveableStateHolderNavEntryDecorator
import androidx.navigation3.ui.NavDisplay
import pl.dakil.dakilio.core.ui.NavTransitions
import pl.dakil.dakilio.feature.importer.api.DaylioImportKey
import pl.dakil.dakilio.feature.importer.impl.daylioImportEntry
import pl.dakil.dakilio.feature.intro.api.IntroLanguageKey
import pl.dakil.dakilio.feature.intro.impl.introEntries

/**
 * The first-run intro, shown instead of [DakilioApp] until it's finished. It has its own back stack,
 * which also holds the import screen, so a Daylio transfer happens right inside the intro.
 */
@Composable
fun IntroFlow(hasIncomingBackup: Boolean, onFinished: () -> Unit) {
    val backStack = rememberNavBackStack(IntroLanguageKey)
    val goBack: () -> Unit = { if (backStack.size > 1) backStack.removeLastOrNull() }

    // DakilioApp isn't composed yet, so a .daylio file shared or opened during the intro opens the
    // import screen here; its ViewModel then takes the file, as it does in the app.
    LaunchedEffect(hasIncomingBackup) {
        if (hasIncomingBackup && backStack.lastOrNull() != DaylioImportKey) backStack.add(DaylioImportKey)
    }

    NavDisplay(
        backStack = backStack,
        onBack = goBack,
        modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.surface),
        entryDecorators = listOf(
            rememberSaveableStateHolderNavEntryDecorator(),
            rememberViewModelStoreNavEntryDecorator(),
        ),
        transitionSpec = { NavTransitions.forward() },
        popTransitionSpec = { NavTransitions.backward() },
        predictivePopTransitionSpec = { NavTransitions.backward() },
        entryProvider = entryProvider<NavKey> {
            introEntries(onNavigate = { backStack.add(it) }, onBack = goBack, onFinished = onFinished)
            // A finished import replaces everything, so there's nothing left to set up.
            daylioImportEntry(onBack = goBack, onDone = onFinished)
        },
    )
}
