package pl.dakil.dakilio.core.designsystem.component

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.remember
import androidx.compose.ui.hapticfeedback.HapticFeedback
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback

/** The app's haptic vocabulary, so every screen "feels" the same for the same kind of action. */
@Stable
class DakilioHaptics(private val feedback: HapticFeedback) {
    /** A meaningful choice landed (mood picked, entry saved). */
    fun confirm() = feedback.performHapticFeedback(HapticFeedbackType.Confirm)

    /** The action isn't allowed (e.g. deselecting the last diary). */
    fun reject() = feedback.performHapticFeedback(HapticFeedbackType.Reject)

    /** Moving between discrete positions (wizard steps, segmented options). */
    fun tick() = feedback.performHapticFeedback(HapticFeedbackType.SegmentTick)

    fun toggle(on: Boolean) =
        feedback.performHapticFeedback(if (on) HapticFeedbackType.ToggleOn else HapticFeedbackType.ToggleOff)

    fun longPress() = feedback.performHapticFeedback(HapticFeedbackType.LongPress)
}

@Composable
fun rememberHaptics(): DakilioHaptics {
    val feedback = LocalHapticFeedback.current
    return remember(feedback) { DakilioHaptics(feedback) }
}
