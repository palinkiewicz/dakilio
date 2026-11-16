package pl.dakil.dakilio.core.designsystem.component

import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.LocalTextStyle
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.takeOrElse
import androidx.compose.ui.layout.IntrinsicMeasurable
import androidx.compose.ui.layout.IntrinsicMeasureScope
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.layout.Measurable
import androidx.compose.ui.layout.MeasurePolicy
import androidx.compose.ui.layout.MeasureResult
import androidx.compose.ui.layout.MeasureScope
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.text
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.constrainHeight
import androidx.compose.ui.unit.constrainWidth
import pl.dakil.dakilio.core.designsystem.theme.FULL_WIDTH
import pl.dakil.dakilio.core.designsystem.theme.NARROWEST_WIDTH
import pl.dakil.dakilio.core.designsystem.theme.narrowed

/** The widths tried, widest first: small steps, so a label narrows only as much as it must. */
private val WIDTH_STEPS = listOf(FULL_WIDTH, 92f, 84f, NARROWEST_WIDTH)

/**
 * One line of text that narrows its letters (the font's width axis, down to 75 %) when it doesn't
 * fit, before it's cut off with "…". For short labels in tight spaces, like connected toggles,
 * where a longer translation would otherwise be truncated.
 *
 * It measures and draws the text itself instead of using `BoxWithConstraints`, so it answers
 * intrinsic size queries: menus (`DropdownMenu`) ask for them, and a subcomposition can't answer.
 */
@Composable
fun FittingText(text: String, modifier: Modifier = Modifier, style: TextStyle = LocalTextStyle.current, color: Color = Color.Unspecified) {
    val measurer = rememberTextMeasurer(cacheSize = WIDTH_STEPS.size + 1)
    val textColor = color.takeOrElse { style.color.takeOrElse { LocalContentColor.current } }
    // Written while measuring, read while drawing: a new layout redraws even when the size stays.
    var laidOut by remember { mutableStateOf<TextLayoutResult?>(null) }
    val policy = remember(text, style, measurer) {
        FittingTextPolicy(text, WIDTH_STEPS.map { width -> style.copy(fontFamily = narrowed(style.fontFamily, width)) }, measurer) { laidOut = it }
    }
    Layout(
        modifier = modifier
            .semantics { this.text = AnnotatedString(text) }
            .drawBehind { laidOut?.let { drawText(it, color = textColor) } },
        measurePolicy = policy,
    )
}

private class FittingTextPolicy(
    private val text: String,
    /** The style at each of [WIDTH_STEPS], widest first. */
    private val styles: List<TextStyle>,
    private val measurer: TextMeasurer,
    private val onLaidOut: (TextLayoutResult) -> Unit,
) : MeasurePolicy {
    private fun measureLine(style: TextStyle, maxWidth: Int = Constraints.Infinity): TextLayoutResult =
        measurer.measure(
            text,
            style,
            overflow = TextOverflow.Ellipsis,
            softWrap = false,
            maxLines = 1,
            constraints = Constraints(maxWidth = maxWidth),
        )

    override fun MeasureScope.measure(measurables: List<Measurable>, constraints: Constraints): MeasureResult {
        val available = constraints.maxWidth
        // The widest style that fits whole; failing that, the narrowest one, cut off with "…".
        val result = styles.asSequence()
            .map { measureLine(it) }
            .firstOrNull { !constraints.hasBoundedWidth || it.size.width <= available }
            ?: measureLine(styles.last(), available)
        onLaidOut(result)
        return layout(constraints.constrainWidth(result.size.width), constraints.constrainHeight(result.size.height)) {}
    }

    // At its widest the text is set in full width; at its narrowest, in the narrowest style.
    override fun IntrinsicMeasureScope.maxIntrinsicWidth(measurables: List<IntrinsicMeasurable>, height: Int): Int =
        measureLine(styles.first()).size.width

    override fun IntrinsicMeasureScope.minIntrinsicWidth(measurables: List<IntrinsicMeasurable>, height: Int): Int =
        measureLine(styles.last()).size.width

    override fun IntrinsicMeasureScope.minIntrinsicHeight(measurables: List<IntrinsicMeasurable>, width: Int): Int =
        measureLine(styles.first()).size.height

    override fun IntrinsicMeasureScope.maxIntrinsicHeight(measurables: List<IntrinsicMeasurable>, width: Int): Int =
        measureLine(styles.first()).size.height
}
