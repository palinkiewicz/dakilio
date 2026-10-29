package pl.dakil.dakilio.core.navigation

import androidx.compose.runtime.Stable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.navigation3.runtime.NavKey

@Stable
class Navigator(private val state: NavigationState) {
    /** Top-level keys switch tabs; anything else is pushed onto the current tab's stack. */
    fun navigate(key: NavKey) {
        if (key in state.topLevelKeys) {
            state.select(key)
        } else {
            state.backStacks.getValue(state.topLevelKey).add(key)
        }
    }

    fun goBack() {
        val stack = state.backStacks.getValue(state.topLevelKey)
        when {
            stack.size > 1 -> stack.removeLastOrNull()
            state.topLevelKey != state.startKey -> state.select(state.startKey)
        }
    }
}

val LocalNavigator = staticCompositionLocalOf<Navigator> { error("No Navigator provided") }
