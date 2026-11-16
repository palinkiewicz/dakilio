package pl.dakil.dakilio.core.designsystem.component

import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.LinearWavyProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import pl.dakil.dakilio.core.designsystem.theme.LocalMotionPreferences

/**
 * A linear progress bar in the style chosen in Settings → Feel, wavy or straight: determinate with a
 * [progress], indeterminate without one.
 */
@Composable
fun DakilioLinearProgress(modifier: Modifier = Modifier, progress: (() -> Float)? = null) {
    val wavy = LocalMotionPreferences.current.wavyProgress
    when {
        !wavy && progress != null -> LinearProgressIndicator(progress = progress, modifier = modifier)
        !wavy -> LinearProgressIndicator(modifier = modifier)
        progress != null -> LinearWavyProgressIndicator(progress = progress, modifier = modifier)
        else -> LinearWavyProgressIndicator(modifier = modifier)
    }
}
