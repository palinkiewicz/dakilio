package pl.dakil.dakilio.core.designsystem.component

import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.LinearWavyProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import pl.dakil.dakilio.core.designsystem.theme.LocalMotionPreferences

/**
 * A linear progress bar in the style chosen in Settings → Feel, wavy or straight: determinate with a
 * [progress], indeterminate without one.
 *
 * @param still for a determinate bar that shows a state rather than work going on: the wave stays
 *   put instead of travelling for as long as the bar is on screen.
 */
@Composable
fun DakilioLinearProgress(modifier: Modifier = Modifier, progress: (() -> Float)? = null, still: Boolean = false) {
    val wavy = LocalMotionPreferences.current.wavyProgress
    when {
        !wavy && progress != null -> LinearProgressIndicator(progress = progress, modifier = modifier)
        !wavy -> LinearProgressIndicator(modifier = modifier)
        progress != null && still -> LinearWavyProgressIndicator(progress = progress, modifier = modifier, waveSpeed = 0.dp)
        progress != null -> LinearWavyProgressIndicator(progress = progress, modifier = modifier)
        else -> LinearWavyProgressIndicator(modifier = modifier)
    }
}
