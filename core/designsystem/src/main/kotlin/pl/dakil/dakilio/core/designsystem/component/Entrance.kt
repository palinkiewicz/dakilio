package pl.dakil.dakilio.core.designsystem.component

import android.os.SystemClock
import androidx.compose.animation.core.Animatable
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import pl.dakil.dakilio.core.designsystem.theme.decorativeMotion

private const val STAGGER_MS = 45L

/** Items further down than this start together with it, so a long page doesn't keep the user waiting. */
const val MAX_STAGGERED = 6

/** How long after its first entrance a screen may still cascade items in (its first screenful). */
private const val WINDOW_MS = 1_200L

/**
 * Decides which entrances of one screen may play: those composed within [WINDOW_MS] of the screen's
 * first entrance. Saved with the screen, so items built again later (scrolled back into view, the
 * screen coming back from one pushed over it or from another tab) show at once.
 */
@Stable
class EntranceGate internal constructor(private var played: Boolean) {
    private var openUntil = 0L

    internal fun admit(): Boolean {
        val now = SystemClock.uptimeMillis()
        if (!played) {
            played = true
            openUntil = now + WINDOW_MS
        }
        return now < openUntil
    }

    internal companion object {
        val Saver = Saver<EntranceGate, Boolean>({ it.played }, ::EntranceGate)
    }
}

private val LocalEntranceGate = staticCompositionLocalOf<EntranceGate?> { null }

/**
 * The standard for a screen whose content cascades in: wrap the screen in it, and its [entrance]s
 * play once, when the screen first opens. Without it an entrance plays every time it composes,
 * which suits content that is new each time it shows (a recap's pages).
 */
@Composable
fun EntranceScope(content: @Composable () -> Unit) {
    val gate = rememberSaveable(saver = EntranceGate.Saver) { EntranceGate(played = false) }
    CompositionLocalProvider(LocalEntranceGate provides gate, content)
}

/** Whether a one-shot entrance composing now should play, by the enclosing [EntranceScope]. */
@Composable
fun rememberEntrance(): Boolean {
    val gate = LocalEntranceGate.current
    return remember { gate?.admit() ?: true }
}

/**
 * Rises and fades an item in when it first composes, [index] steps after the previous one, so a page
 * of cards cascades in rather than popping. Draw-phase only: layout never moves. Decorative, so it
 * does nothing with decorative motion off. Inside an [EntranceScope] it plays only as the screen
 * first opens.
 */
@Composable
fun Modifier.entrance(index: Int = 0): Modifier {
    if (!decorativeMotion || !rememberEntrance()) return this
    val progress = remember { Animatable(0f) }
    val spec = MaterialTheme.motionScheme.slowSpatialSpec<Float>()
    LaunchedEffect(Unit) {
        delay(index.coerceIn(0, MAX_STAGGERED) * STAGGER_MS)
        progress.animateTo(1f, spec)
    }
    return graphicsLayer {
        val p = progress.value
        alpha = p.coerceIn(0f, 1f)
        translationY = (1f - p) * 40.dp.toPx()
    }
}
