package pl.dakil.dakilio.core.navigation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableIntState
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.lifecycle.viewmodel.navigation3.rememberViewModelStoreNavEntryDecorator
import androidx.navigation3.runtime.NavBackStack
import androidx.navigation3.runtime.NavEntry
import androidx.navigation3.runtime.NavKey
import androidx.navigation3.runtime.rememberDecoratedNavEntries
import androidx.navigation3.runtime.rememberNavBackStack
import androidx.navigation3.runtime.rememberSaveableStateHolderNavEntryDecorator

/**
 * One back stack per bottom-bar destination, so every tab keeps its own history, scroll position
 * and ViewModels while the user hops between tabs. The first tab is the "home" tab: back from any
 * other tab's root returns to it (the Navigation 3 "multiple back stacks" recipe).
 */
@Stable
class NavigationState internal constructor(
    val topLevelKeys: List<NavKey>,
    private val selectedIndex: MutableIntState,
    internal val backStacks: Map<NavKey, NavBackStack<NavKey>>,
) {
    val startKey: NavKey get() = topLevelKeys.first()

    val topLevelKey: NavKey get() = topLevelKeys[selectedIndex.intValue]

    /** The destination currently on screen. */
    val currentKey: NavKey get() = backStacks.getValue(topLevelKey).last()

    val isAtTopLevel: Boolean get() = currentKey in topLevelKeys

    internal fun select(key: NavKey) {
        selectedIndex.intValue = topLevelKeys.indexOf(key)
    }

    internal val stacksInUse: List<NavKey>
        get() = if (topLevelKey == startKey) listOf(startKey) else listOf(startKey, topLevelKey)
}

@Composable
fun rememberNavigationState(topLevelKeys: List<NavKey>): NavigationState {
    val selectedIndex = rememberSaveable { mutableIntStateOf(0) }
    val backStacks = topLevelKeys.associateWith { rememberNavBackStack(it) }
    return remember(topLevelKeys, selectedIndex, backStacks) {
        NavigationState(topLevelKeys, selectedIndex, backStacks)
    }
}

/**
 * Entries for NavDisplay: the home tab's stack, plus the selected tab's stack on top of it. Each stack
 * is decorated separately so its saved state and ViewModels survive while it is off screen.
 */
@Composable
fun NavigationState.rememberEntries(entryProvider: (NavKey) -> NavEntry<NavKey>): List<NavEntry<NavKey>> {
    val decorated = backStacks.mapValues { (_, stack) ->
        rememberDecoratedNavEntries(
            backStack = stack,
            entryDecorators = listOf(
                rememberSaveableStateHolderNavEntryDecorator(),
                rememberViewModelStoreNavEntryDecorator(),
            ),
            entryProvider = entryProvider,
        )
    }
    return stacksInUse.flatMap { decorated.getValue(it) }
}
