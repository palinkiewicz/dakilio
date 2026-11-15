package pl.dakil.dakilio.core.designsystem.component

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.remember
import androidx.compose.ui.hapticfeedback.HapticFeedback
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback

/** Whether the app's haptics are on; provided at the app root from `UserSettings.haptics`. */
val LocalHapticsEnabled = compositionLocalOf { true }

/**
 * The app's haptic vocabulary, so every screen "feels" the same for the same kind of action. Every
 * vibration the app makes goes through here, which is how the Haptic feedback setting covers them all.
 */
@Stable
class DakilioHaptics(private val feedback: HapticFeedback, private val enabled: Boolean = true) {
    /** A meaningful choice landed (mood picked, entry saved). */
    fun confirm() = perform(HapticFeedbackType.Confirm)

    /** The action isn't allowed (e.g. deselecting the last diary). */
    fun reject() = perform(HapticFeedbackType.Reject)

    /** Moving between discrete positions (wizard steps, segmented options). */
    fun tick() = perform(HapticFeedbackType.SegmentTick)

    fun toggle(on: Boolean) = perform(if (on) HapticFeedbackType.ToggleOn else HapticFeedbackType.ToggleOff)

    fun longPress() = perform(HapticFeedbackType.LongPress)

    private fun perform(type: HapticFeedbackType) {
        if (enabled) feedback.performHapticFeedback(type)
    }
}

@Composable
fun rememberHaptics(): DakilioHaptics {
    val feedback = LocalHapticFeedback.current
    val enabled = LocalHapticsEnabled.current
    return remember(feedback, enabled) { DakilioHaptics(feedback, enabled) }
}
