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

// A spring 1.5× slower than the expressive "default spatial" one (a spring's duration scales with
// 1/√stiffness, so 700 / 1.5² ≈ 311): at full speed screen changes went by too fast to see.
private val spatial = spring<IntOffset>(dampingRatio = 0.9f, stiffness = 311f)

// The fades are 1.5× the M3 shared-axis and fade-through timings, to match the spring.
private const val ENTER_FADE_MS = 330
private const val ENTER_FADE_DELAY_MS = 90
private const val EXIT_FADE_MS = 180
private const val THROUGH_FADE_MS = 315
private const val THROUGH_FADE_DELAY_MS = 135
private const val THROUGH_EXIT_MS = 135

/** M3 shared-axis X: the default for drilling into and out of screens. */
object NavTransitions {
    fun forward(): ContentTransform =
        (slideInHorizontally(spatial) { it / 5 } + fadeIn(tween(ENTER_FADE_MS, ENTER_FADE_DELAY_MS, EmphasizedDecelerate))) togetherWith
            (slideOutHorizontally(spatial) { -it / 10 } + fadeOut(tween(EXIT_FADE_MS, easing = EmphasizedAccelerate)))

    fun backward(): ContentTransform =
        (slideInHorizontally(spatial) { -it / 10 } + fadeIn(tween(ENTER_FADE_MS, ENTER_FADE_DELAY_MS, EmphasizedDecelerate))) togetherWith
            (slideOutHorizontally(spatial) { it / 5 } + fadeOut(tween(EXIT_FADE_MS, easing = EmphasizedAccelerate)))

    /** M3 fade-through: switching between peers (bottom-bar tabs). */
    fun fadeThrough(): ContentTransform =
        (
            fadeIn(tween(THROUGH_FADE_MS, THROUGH_FADE_DELAY_MS, EmphasizedDecelerate)) +
                scaleIn(tween(THROUGH_FADE_MS, THROUGH_FADE_DELAY_MS, EmphasizedDecelerate), initialScale = 0.94f)
            ) togetherWith
            fadeOut(tween(THROUGH_EXIT_MS, easing = EmphasizedAccelerate))

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
