package pl.dakil.dakilio.core.ui.component

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import kotlin.math.roundToInt
import pl.dakil.dakilio.core.designsystem.component.rememberHaptics
import pl.dakil.dakilio.core.model.TextScale
import pl.dakil.dakilio.core.ui.R

private val Steps = ((TextScale.MAX - TextScale.MIN) / TextScale.STEP).roundToInt() - 1

/**
 * An entry text size: a stepped slider over the [TextScale] range with its percentage, ticking on
 * every step. [onValueChange] gets snapped values while dragging; [onValueChangeFinished] fires
 * when the drag ends, for callers that save only then.
 */
@Composable
fun TextScaleSlider(
    value: Float,
    onValueChange: (Float) -> Unit,
    modifier: Modifier = Modifier,
    onValueChangeFinished: (() -> Unit)? = null,
) {
    val haptics = rememberHaptics()
    Row(modifier, verticalAlignment = Alignment.CenterVertically) {
        Slider(
            value = value,
            onValueChange = {
                val snapped = TextScale.coerce(it)
                if (snapped != value) {
                    haptics.tick()
                    onValueChange(snapped)
                }
            },
            onValueChangeFinished = onValueChangeFinished,
            valueRange = TextScale.MIN..TextScale.MAX,
            steps = Steps,
            modifier = Modifier.weight(1f),
        )
        Text(
            stringResource(R.string.ui_percent, (value * 100).roundToInt()),
            style = MaterialTheme.typography.labelLarge,
            textAlign = TextAlign.End,
            modifier = Modifier.widthIn(min = 48.dp),
        )
    }
}
