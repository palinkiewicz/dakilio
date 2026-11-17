package pl.dakil.dakilio.feature.insights.impl.recap

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.AnimationSpec
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Box
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp
import java.time.DayOfWeek
import kotlinx.coroutines.delay
import pl.dakil.dakilio.core.designsystem.theme.LocalMotionPreferences
import pl.dakil.dakilio.core.designsystem.theme.decorativeMotion
import pl.dakil.dakilio.feature.insights.impl.logic.RecapPage

/** Steps between items of a recap page cascading in: slower than a screen's, since a page is a moment. */
private const val STAGGER_MS = 110L

/** Items further down than this start with it. */
private const val MAX_STAGGERED = 8

/** How long a sweep (days filling in, a bar growing) takes. */
internal const val SWEEP_MS = 1_000

/**
 * Whether the recap page composing now is the settled current page. A neighbour composed while the
 * user swipes isn't, so its motion waits until it's actually shown.
 */
internal val LocalRecapPageActive = compositionLocalOf { true }

/**
 * Rises and fades an item in once its page is shown, [index] steps after the first. Decorative: with
 * decorative motion off it's simply there.
 */
@Composable
internal fun Modifier.recapEntrance(index: Int = 0): Modifier {
    if (!decorativeMotion) return this
    val progress = rememberRecapProgress(index.coerceIn(0, MAX_STAGGERED) * STAGGER_MS, MaterialTheme.motionScheme.slowSpatialSpec())
    return graphicsLayer {
        val p = progress()
        alpha = p.coerceIn(0f, 1f)
        translationY = (1f - p) * 48.dp.toPx()
    }
}

/** Pops an item in with a little overshoot once its page is shown: for faces, photos and reveals. */
@Composable
internal fun Modifier.recapPop(index: Int = 0, turn: Float = 0f): Modifier {
    if (!decorativeMotion) return this
    val progress = rememberRecapProgress(index.coerceIn(0, MAX_STAGGERED) * STAGGER_MS, PopSpec)
    return graphicsLayer {
        val p = progress()
        alpha = (p * 2f).coerceIn(0f, 1f)
        scaleX = 0.4f + 0.6f * p
        scaleY = 0.4f + 0.6f * p
        rotationZ = (1f - p) * turn
    }
}

/** A springy settle for things that pop in. */
internal val PopSpec: AnimationSpec<Float> = spring(dampingRatio = 0.55f, stiffness = Spring.StiffnessMediumLow)

/** An even sweep: days filling in, a bar growing. */
internal val SweepSpec: AnimationSpec<Float> = tween(SWEEP_MS, easing = FastOutSlowInEasing)

/**
 * 0→1 once the page is shown, after [delayMillis], along [spec]; read it in the draw phase. Stays at
 * 1 once played. With decorative motion off it's 1 from the start.
 */
@Composable
internal fun rememberRecapProgress(delayMillis: Long = 0, spec: AnimationSpec<Float> = SweepSpec, onDone: (() -> Unit)? = null): () -> Float {
    val decorative = decorativeMotion
    val active = LocalRecapPageActive.current
    val progress = remember { Animatable(if (decorative) 0f else 1f) }
    LaunchedEffect(active, decorative) {
        if (!decorative) {
            progress.snapTo(1f)
        } else if (active && progress.value < 1f) {
            delay(delayMillis)
            progress.animateTo(1f, spec)
            onDone?.invoke()
        }
    }
    return { progress.value }
}

/**
 * [value] counting up from [from] once the page is shown, formatted by [format]. Laid out at the
 * final value's width, so nothing around it moves. With animated numbers off it shows the value.
 */
@Composable
internal fun CountUp(
    value: Float,
    format: (Float) -> String,
    modifier: Modifier = Modifier,
    style: TextStyle = LocalTextStyle.current,
    from: Float = 0f,
    delayMillis: Long = 0,
) {
    val animated = LocalMotionPreferences.current.animatedNumbers
    val active = LocalRecapPageActive.current
    val current = remember { Animatable(if (animated) from else value) }
    LaunchedEffect(active, animated, value) {
        if (!animated) {
            current.snapTo(value)
        } else if (active && current.value != value) {
            delay(delayMillis)
            current.animateTo(value, tween(SWEEP_MS, easing = FastOutSlowInEasing))
        }
    }
    val tabular = style.copy(fontFeatureSettings = "tnum")
    Box(modifier) {
        Text(format(value), style = tabular, maxLines = 1, modifier = Modifier.alpha(0f))
        Text(format(current.value), style = tabular, maxLines = 1)
    }
}

/** [CountUp] for a whole number. */
@Composable
internal fun CountUp(value: Int, format: (Int) -> String, modifier: Modifier = Modifier, style: TextStyle = LocalTextStyle.current, delayMillis: Long = 0) =
    CountUp(value.toFloat(), { format(Math.round(it)) }, modifier, style, delayMillis = delayMillis)

/**
 * What the user has revealed on the recap's pages (the top tag, the best weekday) and their guess of
 * the best weekday. Saved, so a revealed page stays revealed.
 */
@Stable
internal class RecapSteps(revealed: Set<String>, guess: DayOfWeek?) {
    private var revealed by mutableStateOf(revealed)
    var guess by mutableStateOf(guess)
        private set

    fun isRevealed(page: RecapPage): Boolean = page.name in revealed

    fun reveal(page: RecapPage) {
        revealed = revealed + page.name
    }

    fun guess(day: DayOfWeek, page: RecapPage) {
        guess = day
        reveal(page)
    }

    companion object {
        val Saver = Saver<RecapSteps, List<String>>(
            save = { listOf(it.guess?.name.orEmpty()) + it.revealed },
            restore = { saved -> RecapSteps(saved.drop(1).toSet(), saved.first().takeIf { it.isNotEmpty() }?.let(DayOfWeek::valueOf)) },
        )
    }
}
