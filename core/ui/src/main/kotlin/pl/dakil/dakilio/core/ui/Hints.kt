package pl.dakil.dakilio.core.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.compositionLocalOf

/**
 * Whether beginner hints are shown; provided at the app root from `UserSettings.showHints`.
 *
 * A hint is a line that teaches how to use the UI and stays true whatever the user's data is
 * ("Pick the closest mood, or skip this step"). Text that describes state (empty states, counts,
 * validation, warnings, setting descriptions, placeholders) is never a hint.
 */
val LocalShowHints = compositionLocalOf { true }

/** Renders beginner guidance only while the user keeps hints on. Wrap every how-to line in this. */
@Composable
fun Hint(content: @Composable () -> Unit) {
    if (LocalShowHints.current) content()
}

/** For components that take an optional text: the hint, or null when hints are off. */
@Composable
fun hintOrNull(text: String): String? = text.takeIf { LocalShowHints.current }
