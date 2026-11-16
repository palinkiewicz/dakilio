package pl.dakil.dakilio.core.designsystem.theme

import androidx.compose.animation.core.AnimationSpec
import androidx.compose.animation.core.snap
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.staticCompositionLocalOf

/**
 * The user's choices from Settings → Feel: whether changing numbers roll ([animatedNumbers]), whether
 * decorative motion plays ([decorative]: spins, shape morphs on selection, content cascading in) and
 * whether progress bars are wavy ([wavyProgress]). Screen transitions, press feedback, gestures and
 * values changing never depend on any of it. Haptics are switched in [DakilioTheme] instead.
 */
@Immutable
data class MotionPreferences(
    val animatedNumbers: Boolean = true,
    val decorative: Boolean = true,
    val wavyProgress: Boolean = true,
)

val LocalMotionPreferences = staticCompositionLocalOf { MotionPreferences() }

/**
 * Whether decorative motion plays. Where it's off, such places show their end state at once.
 */
val decorativeMotion: Boolean
    @Composable @ReadOnlyComposable get() = LocalMotionPreferences.current.decorative

/** [spec] for a decorative animation, or an instant jump when decorative motion is off. */
@Composable
@ReadOnlyComposable
fun <T> decorativeSpec(spec: AnimationSpec<T>): AnimationSpec<T> = if (decorativeMotion) spec else snap()

/**
 * How far (in degrees) a selected item turns as it morphs: [degrees], or none with decorative motion
 * off, so the item simply shows its selected shape.
 */
@Composable
@ReadOnlyComposable
fun decorativeTurn(degrees: Float): Float = if (decorativeMotion) degrees else 0f
