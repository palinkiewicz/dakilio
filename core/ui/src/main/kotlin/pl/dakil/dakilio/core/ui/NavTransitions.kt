package pl.dakil.dakilio.core.ui

import androidx.compose.animation.ContentTransform
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.ui.unit.IntOffset
import androidx.navigation3.ui.NavDisplay

private val EmphasizedDecelerate = CubicBezierEasing(0.05f, 0.7f, 0.1f, 1f)
private val EmphasizedAccelerate = CubicBezierEasing(0.3f, 0f, 0.8f, 0.15f)

// Mirrors the expressive "default spatial" spring so navigation feels like the rest of the UI.
private val spatial = spring<IntOffset>(dampingRatio = 0.9f, stiffness = 700f)

/** M3 shared-axis X: the default for drilling into and out of screens. */
object NavTransitions {
    fun forward(): ContentTransform =
        (slideInHorizontally(spatial) { it / 5 } + fadeIn(tween(220, 60, EmphasizedDecelerate))) togetherWith
            (slideOutHorizontally(spatial) { -it / 10 } + fadeOut(tween(120, easing = EmphasizedAccelerate)))

    fun backward(): ContentTransform =
        (slideInHorizontally(spatial) { -it / 10 } + fadeIn(tween(220, 60, EmphasizedDecelerate))) togetherWith
            (slideOutHorizontally(spatial) { it / 5 } + fadeOut(tween(120, easing = EmphasizedAccelerate)))

    /** M3 fade-through: switching between peers (bottom-bar tabs). */
    fun fadeThrough(): ContentTransform =
        (fadeIn(tween(210, 90, EmphasizedDecelerate)) + scaleIn(tween(210, 90, EmphasizedDecelerate), initialScale = 0.94f)) togetherWith
            fadeOut(tween(90, easing = EmphasizedAccelerate))

    /**
     * Container transform: the outgoing screen holds still while the incoming one grows out of a
     * shared element (see [sharedContainer]) — the content swap itself is just a quick cross-fade.
     */
    fun containerTransform(): ContentTransform =
        fadeIn(tween(200, 50)) togetherWith fadeOut(tween(durationMillis = 1, delayMillis = 400))

    fun containerTransformReturn(): ContentTransform =
        EnterTransition.None togetherWith fadeOut(tween(250, 100))

    /** Entry metadata for bottom-bar destinations. */
    val topLevel: Map<String, Any> =
        NavDisplay.transitionSpec { fadeThrough() } +
            NavDisplay.popTransitionSpec { fadeThrough() } +
            NavDisplay.predictivePopTransitionSpec { fadeThrough() }

    /** Entry metadata for screens that grow out of a shared element (the entry editor). */
    val sharedContainer: Map<String, Any> =
        NavDisplay.transitionSpec { containerTransform() } +
            NavDisplay.popTransitionSpec { containerTransformReturn() } +
            NavDisplay.predictivePopTransitionSpec { containerTransformReturn() }
}
