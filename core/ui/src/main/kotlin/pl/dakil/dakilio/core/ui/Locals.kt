package pl.dakil.dakilio.core.ui

import androidx.compose.animation.SharedTransitionScope
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.navigation3.ui.LocalNavAnimatedContentScope

/** The app-wide shared transition scope (set up by the app shell around NavDisplay). */
val LocalSharedTransitionScope = staticCompositionLocalOf<SharedTransitionScope?> { null }

/** Height the bottom navigation bar covers, so top-level screens can pad their content above it. */
val LocalBottomBarPadding = compositionLocalOf { 0.dp }

/**
 * Container transform between two destinations: apply with the same [key] on the element that
 * launches a screen (FAB, card) and on that screen's root.
 */
@Composable
fun Modifier.sharedContainer(key: Any, shape: Shape = RoundedCornerShape(28.dp)): Modifier {
    val shared = LocalSharedTransitionScope.current ?: return this
    val animatedScope = LocalNavAnimatedContentScope.current
    return with(shared) {
        this@sharedContainer.sharedBounds(
            sharedContentState = rememberSharedContentState(key),
            animatedVisibilityScope = animatedScope,
            resizeMode = SharedTransitionScope.ResizeMode.RemeasureToBounds,
            clipInOverlayDuringTransition = OverlayClip(shape),
        )
    }
}

/** Bottom padding that keeps the last item of a top-level screen clear of the nav bar (+ [extra]). */
@Composable
fun bottomBarPadding(extra: Dp = 0.dp): Dp = LocalBottomBarPadding.current + extra
