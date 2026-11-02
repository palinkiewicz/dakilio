package pl.dakil.dakilio.core.designsystem.component

import android.view.WindowManager
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.platform.LocalView

/** The M3 scrim opacity. */
private const val ScrimAlpha = 0.32f

/**
 * Dims everything behind the popup window this is composed in (a `DropdownMenu`'s content, a
 * `Popup`), the app bars and system bars included, like a dialog's scrim. Outside a popup window
 * it does nothing.
 */
@Composable
fun DimBehind(amount: Float = ScrimAlpha) {
    val view = LocalView.current
    DisposableEffect(view, amount) {
        val root = view.rootView
        val params = root.layoutParams as? WindowManager.LayoutParams
        val windowManager = view.context.getSystemService(WindowManager::class.java)
        if (params != null && windowManager != null && root.isAttachedToWindow) {
            params.flags = params.flags or WindowManager.LayoutParams.FLAG_DIM_BEHIND
            params.dimAmount = amount
            windowManager.updateViewLayout(root, params)
        }
        onDispose { }
    }
}
