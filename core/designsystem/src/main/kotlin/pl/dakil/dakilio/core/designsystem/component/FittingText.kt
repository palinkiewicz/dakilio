package pl.dakil.dakilio.core.designsystem.component

import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextOverflow
import pl.dakil.dakilio.core.designsystem.theme.FULL_WIDTH
import pl.dakil.dakilio.core.designsystem.theme.NARROWEST_WIDTH
import pl.dakil.dakilio.core.designsystem.theme.narrowed

/** The widths tried, widest first: small steps, so a label narrows only as much as it must. */
private val WIDTH_STEPS = listOf(FULL_WIDTH, 92f, 84f, NARROWEST_WIDTH)

/**
 * One line of text that narrows its letters (the font's width axis, down to 75 %) when it doesn't
 * fit, before it's cut off with "…". For short labels in tight spaces, like connected toggles,
 * where a longer translation would otherwise be truncated.
 */
@Composable
fun FittingText(text: String, modifier: Modifier = Modifier, style: TextStyle = LocalTextStyle.current, color: Color = Color.Unspecified) {
    val measurer = rememberTextMeasurer(cacheSize = WIDTH_STEPS.size)
    BoxWithConstraints(modifier) {
        val available = constraints.maxWidth
        val fitted = remember(text, style, available) {
            WIDTH_STEPS.asSequence()
                .map { width -> style.copy(fontFamily = narrowed(style.fontFamily, width)) }
                .firstOrNull { candidate -> measurer.measure(text, candidate, softWrap = false, maxLines = 1).size.width <= available }
                ?: style.copy(fontFamily = narrowed(style.fontFamily, NARROWEST_WIDTH))
        }
        Text(text, style = fitted, color = color, maxLines = 1, softWrap = false, overflow = TextOverflow.Ellipsis)
    }
}
